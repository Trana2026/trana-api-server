-- V8: 계약 서명 흐름 변경 (W6, 2026-05-28)
-- - status enum: SHARED / RECEIVER_SIGNED 추가, SIGN_REQUESTED / REVISION_REQUESTED 제거
-- - 신규 테이블 3종: contract_invitations / contract_signatures / contract_consents
-- - 흐름: DRAFT → READY → SHARED → RECEIVER_SIGNED → SIGNED (수신자 먼저, 생성자 최종)

-- ============================================================
-- status CHECK 제약 갱신
-- ============================================================
ALTER TABLE contracts
    DROP CONSTRAINT chk_contracts_status;

ALTER TABLE contracts
    ADD CONSTRAINT chk_contracts_status
        CHECK (status IN ('DRAFT', 'READY', 'SHARED', 'RECEIVER_SIGNED', 'SIGNED', 'COMPLETED', 'CANCELLED'));

ALTER TABLE contract_status_logs
    DROP CONSTRAINT chk_contract_status_logs_to;

ALTER TABLE contract_status_logs
    ADD CONSTRAINT chk_contract_status_logs_to
        CHECK (to_status IN ('DRAFT', 'READY', 'SHARED', 'RECEIVER_SIGNED', 'SIGNED', 'COMPLETED', 'CANCELLED'));

ALTER TABLE contract_status_logs
    DROP CONSTRAINT chk_contract_status_logs_from;

ALTER TABLE contract_status_logs
    ADD CONSTRAINT chk_contract_status_logs_from
        CHECK (from_status IS NULL OR from_status IN
                                      ('DRAFT', 'READY', 'SHARED', 'RECEIVER_SIGNED', 'SIGNED', 'COMPLETED',
                                       'CANCELLED'));

COMMENT ON COLUMN contracts.status IS 'DRAFT | READY | SHARED | RECEIVER_SIGNED | SIGNED | COMPLETED | CANCELLED (W6 흐름)';

-- ============================================================
-- contract_invitations
-- 생성자가 "공유하기" → 수신자 이름/phone 으로 카톡 알림톡 발송 시 row 생성
-- 수신자가 가입 전이라 user_id 모름 → 이름/phone 직접 저장
-- ============================================================
CREATE TABLE contract_invitations
(
    id                  BIGSERIAL PRIMARY KEY,
    contract_id         BIGINT      NOT NULL REFERENCES contracts (id) ON DELETE CASCADE,
    token               VARCHAR(40) NOT NULL UNIQUE,
    receiver_name       VARCHAR(50) NOT NULL,
    receiver_phone      VARCHAR(20) NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    used_at             TIMESTAMPTZ,
    accepted_by_user_id BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_contract_invitations_token ON contract_invitations (token);
CREATE INDEX idx_contract_invitations_contract ON contract_invitations (contract_id);

COMMENT ON TABLE contract_invitations IS '수신자 초대 토큰. 카카오톡 알림톡 1번 템플릿으로 발송, TTL 7일';
COMMENT ON COLUMN contract_invitations.token IS 'jnanoid 21자 (URL 동봉, 추측 어려움)';
COMMENT ON COLUMN contract_invitations.receiver_name IS '수신자 이름 (가입 전이라 user_id 없음, audit)';
COMMENT ON COLUMN contract_invitations.receiver_phone IS '수신자 phone (한국 010-XXXX-XXXX 또는 E.164, 알림톡 발송)';
COMMENT ON COLUMN contract_invitations.used_at IS '수신자 서명 완료 시점 (RECEIVER_SIGNED 전이 시 마킹)';
COMMENT ON COLUMN contract_invitations.accepted_by_user_id IS '수신자가 가입 후 수락한 user_id (논리 FK)';

-- ============================================================
-- contract_signatures
-- 양측 서명 audit (WORM — insert-only)
-- 수신자 먼저, 생성자 나중 INSERT
-- ============================================================
CREATE TABLE contract_signatures
(
    id                  BIGSERIAL PRIMARY KEY,
    contract_id         BIGINT      NOT NULL REFERENCES contracts (id) ON DELETE RESTRICT,
    user_id             BIGINT      NOT NULL,
    party_type          VARCHAR(20) NOT NULL,
    signed_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    signer_ip           VARCHAR(45),
    signer_user_agent   TEXT,
    signature_data      TEXT        NOT NULL,
    pdf_version_at_sign INT         NOT NULL,

    CONSTRAINT chk_contract_signatures_party
        CHECK (party_type IN ('SELLER', 'BUYER'))
);

CREATE INDEX idx_contract_signatures_contract ON contract_signatures (contract_id, signed_at);
CREATE UNIQUE INDEX uq_contract_signatures_party ON contract_signatures (contract_id, party_type);

COMMENT ON TABLE contract_signatures IS '양측 전자서명 audit. WORM (insert-only). 분쟁 증거 5년+';
COMMENT ON COLUMN contract_signatures.user_id IS '서명한 user (논리 FK)';
COMMENT ON COLUMN contract_signatures.party_type IS 'SELLER | BUYER';
COMMENT ON COLUMN contract_signatures.signer_ip IS '서명자 IP (IPv4/v6 둘 다 수용)';
COMMENT ON COLUMN contract_signatures.signature_data IS '서명 데이터 (base64 image 또는 typed name 등). 향후 PAdES 검토 (W7+)';
COMMENT ON COLUMN contract_signatures.pdf_version_at_sign IS '서명 시점 contracts.version 스냅샷 — 어느 리비전 PDF 에 서명했는지 audit';

-- ============================================================
-- contract_consents
-- 계약 도메인 약관 동의 audit (4 + 1)
-- user_consents (가입 약관) 와 별도 — 계약 단위
-- ============================================================
CREATE TABLE contract_consents
(
    id           BIGSERIAL PRIMARY KEY,
    contract_id  BIGINT      NOT NULL REFERENCES contracts (id) ON DELETE RESTRICT,
    user_id      BIGINT      NOT NULL,
    term_id      BIGINT      NOT NULL,
    term_version VARCHAR(20) NOT NULL,
    consented_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    consenter_ip VARCHAR(45)
);

CREATE INDEX idx_contract_consents_contract_user ON contract_consents (contract_id, user_id);

COMMENT ON TABLE contract_consents IS '계약 도메인 약관 동의 audit (4 + 1). 양측이 각자 서명 직전 동의 — user_consents 와 별도';
COMMENT ON COLUMN contract_consents.term_id IS 'terms 테이블 FK (논리)';
COMMENT ON COLUMN contract_consents.term_version IS '동의 시점 term version (snapshot)';
