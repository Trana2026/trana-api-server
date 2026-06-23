-- V10: trust_score 도메인 (events + 면제 티켓 + 사기 사용자 hash)

-- =========================================
-- trust_score_events
-- - 신뢰 점수 변동 이력 (WORM 영구 보존, 감사·분쟁 증거)
-- - SOT — users.trust_score / users.*_count 는 캐시. 이 테이블이 진실
-- - 이벤트 타입:
--     BOTH_SIGNED                       — 양측 서명 완료 (SIGNED 전이) +2 (양측 적립)
--     WARRANTY_PROVIDED                 — 판매자 보증 제공 + SIGNED +3 (판매자만)
--     FRAUD_REPORT_FILED_CONFIRMED      — 신고함 + 사기 확인 +5 (신고자)
--     FRAUD_REPORT_RECEIVED_CONFIRMED   — 신고당함 + 사기 확인 -15 (신고 대상)
--     MIN_FLOOR                         — 0점 floor 도달 audit (delta=0, after_score=0)
-- =========================================
CREATE TABLE trust_score_events
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    event_type   VARCHAR(48) NOT NULL,
    delta        INT         NOT NULL,
    before_score INT         NOT NULL CHECK (before_score BETWEEN 0 AND 100),
    after_score  INT         NOT NULL CHECK (after_score BETWEEN 0 AND 100),
    reason       TEXT,
    contract_id  BIGINT REFERENCES contracts (id),
    dispute_id   BIGINT REFERENCES dispute_records (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_trust_score_events_event_type
        CHECK (event_type IN (
                              'BOTH_SIGNED',
                              'WARRANTY_PROVIDED',
                              'FRAUD_REPORT_FILED_CONFIRMED',
                              'FRAUD_REPORT_RECEIVED_CONFIRMED',
                              'MIN_FLOOR'
            ))
);

CREATE INDEX idx_trust_score_events_user_id_created_at
    ON trust_score_events (user_id, created_at DESC);
CREATE INDEX idx_trust_score_events_contract_id
    ON trust_score_events (contract_id) WHERE contract_id IS NOT NULL;

-- WORM trigger — UPDATE 차단 (audit immutability), DELETE 는 cleanup batch 허용
-- 정책 (명세 부록): 탈퇴 1년 경과 + 사기 X user 의 점수 변동 이력 자동 삭제 (TrustScoreCleanupTask)
CREATE TRIGGER trg_trust_score_events_worm
    BEFORE UPDATE
    ON trust_score_events
    FOR EACH ROW
EXECUTE FUNCTION worm_protect();

COMMENT ON COLUMN trust_score_events.event_type IS 'BOTH_SIGNED | WARRANTY_PROVIDED | FRAUD_REPORT_FILED_CONFIRMED | FRAUD_REPORT_RECEIVED_CONFIRMED | MIN_FLOOR';
COMMENT ON COLUMN trust_score_events.delta IS '점수 변동 (+2 / +3 / +5 / -15 / 0=floor)';
COMMENT ON COLUMN trust_score_events.before_score IS '변동 전 점수 (audit 추적용)';
COMMENT ON COLUMN trust_score_events.after_score IS '변동 후 점수 (0~100 clamp 적용 후)';
COMMENT ON COLUMN trust_score_events.contract_id IS '연관 계약 (BOTH_SIGNED / WARRANTY_PROVIDED 시 채움)';
COMMENT ON COLUMN trust_score_events.dispute_id IS '연관 분쟁 (FRAUD_*_CONFIRMED 시 채움)';

-- =========================================
-- warranty_exemption_tickets
-- - 신뢰/우수 등급 월 발급 면제 티켓 (계약 생성 수수료 0원)
-- - 최우수 (90~100) = row 발급 X, 등급 자체로 결제 0원 분기
-- - 유효기간 = 지급일 +30일. 만료 3일 전 FCM 알림 (expiry_notified_at 으로 중복 발송 차단)
-- =========================================
CREATE TABLE warranty_exemption_tickets
(
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT      NOT NULL REFERENCES users (id),
    status             VARCHAR(16) NOT NULL DEFAULT 'UNUSED',
    issue_reason       VARCHAR(48) NOT NULL,
    issued_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ NOT NULL,
    used_at            TIMESTAMPTZ,
    used_contract_id   BIGINT REFERENCES contracts (id),
    expiry_notified_at TIMESTAMPTZ,
    CONSTRAINT chk_warranty_exemption_tickets_status
        CHECK (status IN ('UNUSED', 'USED', 'EXPIRED')),
    CONSTRAINT chk_warranty_exemption_tickets_issue_reason
        CHECK (issue_reason IN ('GRADE_TRUST_MONTHLY', 'GRADE_EXCELLENT_MONTHLY')),
    CONSTRAINT chk_warranty_exemption_tickets_used_consistent
        CHECK ((status = 'USED') = (used_at IS NOT NULL))
);

CREATE INDEX idx_warranty_exemption_tickets_user_unused
    ON warranty_exemption_tickets (user_id) WHERE status = 'UNUSED';
CREATE INDEX idx_warranty_exemption_tickets_expires_unused
    ON warranty_exemption_tickets (expires_at) WHERE status = 'UNUSED';

COMMENT ON COLUMN warranty_exemption_tickets.status IS 'UNUSED | USED | EXPIRED';
COMMENT ON COLUMN warranty_exemption_tickets.issue_reason IS 'GRADE_TRUST_MONTHLY (신뢰 월1) | GRADE_EXCELLENT_MONTHLY (우수 월3)';
COMMENT ON COLUMN warranty_exemption_tickets.expires_at IS '지급일 +30일. 경과 시 cleanup batch 가 status=EXPIRED 마킹';
COMMENT ON COLUMN warranty_exemption_tickets.expiry_notified_at IS '만료 3일 전 알림 발송 시각 (중복 발송 차단)';

-- =========================================
-- fraud_user_hashes
-- - 운영팀 사기 판정 받은 사용자의 식별자 hash 영구 보존
-- - 탈퇴 전 row 생성 (사기 판정 시) → 탈퇴 시 withdrawn_at 채움 + users.name/phone 마스킹
-- - B2B API 사기 조회 서비스 (W10+) 의 데이터 source
-- - WORM 영구 보존 (UPDATE/DELETE 차단)
-- =========================================
CREATE TABLE fraud_user_hashes
(
    id                            BIGSERIAL PRIMARY KEY,
    user_id_hash                  VARCHAR(64) NOT NULL UNIQUE,
    reporter_id_hashes            JSONB,
    fraud_confirmed_at            TIMESTAMPTZ NOT NULL,
    reason                        TEXT        NOT NULL,
    related_contract_public_codes JSONB,
    withdrawn_at                  TIMESTAMPTZ,
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_fraud_user_hashes_worm
    BEFORE UPDATE OR DELETE
    ON fraud_user_hashes
    FOR EACH ROW
EXECUTE FUNCTION worm_protect();

COMMENT ON COLUMN fraud_user_hashes.user_id_hash IS 'SHA-256(users.public_code) — 식별자 추적 가능, 원본 복원 불가';
COMMENT ON COLUMN fraud_user_hashes.reporter_id_hashes IS '신고자들의 user_id_hash 배열 (JSON)';
COMMENT ON COLUMN fraud_user_hashes.related_contract_public_codes IS '연관 계약 public_code 배열 (JSON, 12자 nanoid)';
COMMENT ON COLUMN fraud_user_hashes.withdrawn_at IS '탈퇴 시각. NULL = 활성 사용자 (사기 이력만 있음)';
