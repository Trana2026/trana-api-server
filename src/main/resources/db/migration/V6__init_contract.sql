-- V7: 계약 도메인 (W4 범위 — contracts + parties + attachments + ai_extractions)
-- W5 (invitations / signatures / PDF / final_hash) 는 진입 시 같은 V7 수정 (개발 단계)
-- 도메인 내부 FK = ON DELETE CASCADE / 다른 도메인 user_id = 논리 FK (기존 V 파일 정책)
-- - 미성년 계약 단계 보호자 동의는 폐지 (2026-07-10). 민법 제5조 제1항 사전 포괄 동의 불가 → 상대방 위험 고지 확인 방식으로 대체

-- ============================================================
-- contracts
-- - 작성 권한: creator_user_id 만 (DRAFT 단계)
-- - status × dispute_state 직교 (W7 분쟁)
-- ============================================================
CREATE TABLE contracts
(
    id                         BIGSERIAL PRIMARY KEY,
    public_code                VARCHAR(20) NOT NULL UNIQUE,
    creator_user_id            BIGINT      NOT NULL,
    status                     VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    dispute_state              VARCHAR(20) NOT NULL DEFAULT 'NONE',

    -- 거래 정보
    delivery_type              VARCHAR(20),
    trading_platform           VARCHAR(50),
    title                      VARCHAR(200),
    price                      BIGINT,
    condition_summary          TEXT,
    condition_details          TEXT,
    warranty_period_days       INT         NOT NULL DEFAULT 3,

    -- 동의 / 약관
    acknowledgements_version   VARCHAR(20),
    acknowledged_at            TIMESTAMPTZ,
    sign_terms_version         VARCHAR(20),
    sign_terms_acknowledged_at TIMESTAMPTZ,

    -- 거래 완료 (양측 모두 클릭 시점, 자동 전이 timestamp)
    completed_at               TIMESTAMPTZ,

    -- 본문 / 무결성
    generated_body             TEXT,
    pdf_s3_key                 VARCHAR(500),
    content_hash               VARCHAR(64),
    final_hash                 VARCHAR(64),
    pdf_generated_at           TIMESTAMPTZ,
    version                    INT         NOT NULL DEFAULT 0,
    optimistic_version         BIGINT      NOT NULL DEFAULT 0,
    status_updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at                 TIMESTAMPTZ,

    CONSTRAINT chk_contracts_status
        CHECK (status IN ('IN_PROGRESS', 'DRAFT', 'READY', 'SHARED', 'REVISION_REQUESTED', 'RECEIVER_SIGNED',
                          'CANCEL_REQUESTED', 'SIGNED', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_contracts_dispute_state
        CHECK (dispute_state IN ('NONE', 'REPORTED'))
);

CREATE INDEX idx_contracts_public_code ON contracts (public_code);
CREATE INDEX idx_contracts_creator ON contracts (creator_user_id, created_at DESC);
CREATE INDEX idx_contracts_status ON contracts (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_contracts_status_updated_at
    ON contracts (status, status_updated_at) WHERE status IN ('SHARED', 'RECEIVER_SIGNED');

COMMENT ON TABLE contracts IS 'C2C 안전 거래 전자계약. status × dispute_state 직교';
COMMENT ON COLUMN contracts.public_code IS 'jnanoid 12자 (외부 노출용)';
COMMENT ON COLUMN contracts.creator_user_id IS '작성자 user_id (논리 FK — cascade 사고 방지)';
COMMENT ON COLUMN contracts.status IS 'IN_PROGRESS | DRAFT | READY | SHARED | REVISION_REQUESTED | RECEIVER_SIGNED | CANCEL_REQUESTED | SIGNED | COMPLETED | CANCELLED | EXPIRED';
COMMENT ON COLUMN contracts.status_updated_at IS '상태 전이 시각 — SHARED/RECEIVER_SIGNED 72h 자동 만료 검사 기준 (2026-07 refactor)';
COMMENT ON COLUMN contracts.dispute_state IS 'NONE | REPORTED | RESOLVED | DISMISSED (W7)';
COMMENT ON COLUMN contracts.delivery_type IS '거래 방식. IN_PROGRESS 단계에서 nullable, READY 이상 NOT NULL 강제 (Service 검증)';
COMMENT ON COLUMN contracts.trading_platform IS '거래 발견 플랫폼 (자유 텍스트 50자, 예: 당근마켓 / 번개장터 / 인스타그램 DM). 분쟁 audit + AI 자동 추출';
COMMENT ON COLUMN contracts.price IS '원 단위 정수';
COMMENT ON COLUMN contracts.warranty_period_days IS '보증 기간 일수 (현재 3일 고정)';
COMMENT ON COLUMN contracts.pdf_s3_key IS 'trana-pdf-archive-{env} 버킷 키 — markReady 시 채워짐. Versioning 으로 히스토리';
COMMENT ON COLUMN contracts.content_hash IS 'PDF SHA-256 hex (64자). markReady 시 PDF 생성 직후 계산. 분쟁 증거';
COMMENT ON COLUMN contracts.final_hash IS 'SHA-256 hex (64자). 양측 SIGNED 시점 PDF + 서명 결합 해시 (W6)';
COMMENT ON COLUMN contracts.pdf_generated_at IS 'PDF 생성 시각 — markReady 시점 (재 markReady 시 갱신)';
COMMENT ON COLUMN contracts.completed_at IS '양측 SELLER+BUYER 모두 거래 완료 클릭 시점. 보증기간(3일) 시작 기준 (W7)';
COMMENT ON COLUMN contracts.version IS 'markReady 마다 +1. PDF 리비전 식별 (W5+)';
COMMENT ON COLUMN contracts.optimistic_version IS 'JPA @Version 낙관적 잠금 — confirmCompletion 등 race 차단. W6 refactor (f). 비즈니스 PDF version 과 분리';
COMMENT ON COLUMN contracts.deleted_at IS 'soft delete (DRAFT 만 허용)';

-- ============================================================
-- contract_parties
-- W4: creator(SELLER 또는 BUYER) 만 INSERT
-- W5: invitation 매핑 시 상대편 INSERT
-- ============================================================
CREATE TABLE contract_parties
(
    id           BIGSERIAL PRIMARY KEY,
    contract_id  BIGINT      NOT NULL REFERENCES contracts (id) ON DELETE CASCADE,
    user_id      BIGINT      NOT NULL,
    party_type   VARCHAR(20) NOT NULL,
    validated    BOOLEAN     NOT NULL DEFAULT FALSE,
    validated_at TIMESTAMPTZ,
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ NULL,
    UNIQUE (contract_id, party_type)
);

CREATE INDEX idx_contract_parties_contract ON contract_parties (contract_id);
CREATE INDEX idx_contract_parties_user ON contract_parties (user_id, joined_at DESC);

COMMENT ON COLUMN contract_parties.party_type IS 'SELLER | BUYER';
COMMENT ON COLUMN contract_parties.validated IS '본인 확인(KYC SUCCESS + ACTIVE user) 통과 여부';
COMMENT ON COLUMN contract_parties.completed_at IS '거래 완료 클릭 시점 (양측 각자, W7)';

-- ============================================================
-- contract_attachments
-- 게시글 스크린샷 1~7장. S3 archive 버킷, 3년 보존
-- ============================================================
CREATE TABLE contract_attachments
(
    id                BIGSERIAL PRIMARY KEY,
    contract_id       BIGINT       NOT NULL REFERENCES contracts (id) ON DELETE CASCADE,
    s3_key            VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255),
    content_type      VARCHAR(100),
    size_bytes        BIGINT,
    sha256            VARCHAR(64)  NOT NULL,
    sort_order        INT          NOT NULL DEFAULT 0,
    uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_contract_attachments_contract ON contract_attachments (contract_id, sort_order);

COMMENT ON TABLE contract_attachments IS '게시글 스크린샷. S3 archive 3년 보존, EXIF 미저장';
COMMENT ON COLUMN contract_attachments.s3_key IS 'trana-archive-{env} 버킷 키';
COMMENT ON COLUMN contract_attachments.sha256 IS 'S3 객체 SHA-256 hex (64자). 분쟁 증거 / PDF 본문 해시 입력. register 시점 서버 계산';

-- ============================================================
-- contract_ai_extractions
-- gpt-4o-mini 호출 결과 + 사용자 동의 audit (5년 보존)
-- W5: 비동기 처리. row 라이프사이클 = PENDING → SUCCESS / FAILED
-- ============================================================
CREATE TABLE contract_ai_extractions
(
    id                   BIGSERIAL PRIMARY KEY,
    contract_id          BIGINT      NOT NULL REFERENCES contracts (id) ON DELETE CASCADE,
    model_name           VARCHAR(50) NOT NULL,
    prompt_version       VARCHAR(20) NOT NULL,
    consent_text_version VARCHAR(20) NOT NULL,
    consented_at         TIMESTAMPTZ NOT NULL,
    attachment_ids       BIGINT[]    NOT NULL,

    -- 비동기 결과 (PENDING 동안 NULL, SUCCESS 시 채움)
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    extracted_json       TEXT,
    prompt_tokens        INT,
    completion_tokens    INT,
    total_tokens         INT,
    latency_ms           BIGINT,
    error_message        TEXT,

    extracted_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_contract_ai_extractions_status
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_contract_ai_extractions_contract
    ON contract_ai_extractions (contract_id, extracted_at DESC);

CREATE INDEX idx_contract_ai_extractions_status
    ON contract_ai_extractions (status);

COMMENT ON COLUMN contract_ai_extractions.model_name IS 'gpt-4o-mini';
COMMENT ON COLUMN contract_ai_extractions.attachment_ids IS '추출 입력 사진 id 배열 (재현 가능)';
COMMENT ON COLUMN contract_ai_extractions.status IS 'PENDING (요청 등록) | SUCCESS (응답 수신) | FAILED (예외)';
COMMENT ON COLUMN contract_ai_extractions.extracted_json IS 'gpt-4o-mini raw 응답 (5년 보존). PENDING/FAILED 시 NULL';
COMMENT ON COLUMN contract_ai_extractions.prompt_tokens IS 'OpenAI usage.prompt_tokens — 비용 추적. PENDING/FAILED 시 NULL';
COMMENT ON COLUMN contract_ai_extractions.completion_tokens IS 'OpenAI usage.completion_tokens — 비용 추적. PENDING/FAILED 시 NULL';
COMMENT ON COLUMN contract_ai_extractions.total_tokens IS 'OpenAI usage.total_tokens — 모니터링/대시보드. PENDING/FAILED 시 NULL';
COMMENT ON COLUMN contract_ai_extractions.latency_ms IS '호출 시작~응답 완료 (ms) — 성능 모니터링. PENDING/FAILED 시 NULL';
COMMENT ON COLUMN contract_ai_extractions.error_message IS 'FAILED 시 예외 메시지 (사용자 노출 안 함, 운영 로그)';

-- ============================================================
-- contract_status_logs (W5)
-- WORM: insert-only. 상태 전이 audit. 분쟁 증거 (5년 보존)
-- INITIAL 전이 = from_status NULL → to_status DRAFT (createDraft 시점)
-- ============================================================
CREATE TABLE contract_status_logs
(
    id            BIGSERIAL PRIMARY KEY,
    contract_id   BIGINT      NOT NULL REFERENCES contracts (id) ON DELETE RESTRICT,
    from_status   VARCHAR(30),
    to_status     VARCHAR(30) NOT NULL,
    actor_user_id BIGINT,
    reason        TEXT,
    changed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_contract_status_logs_to
        CHECK (to_status IN ('IN_PROGRESS', 'DRAFT', 'READY', 'SHARED', 'REVISION_REQUESTED', 'RECEIVER_SIGNED',
                             'CANCEL_REQUESTED', 'SIGNED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_contract_status_logs_from
        CHECK (from_status IS NULL OR from_status IN ('IN_PROGRESS', 'DRAFT', 'READY', 'SHARED', 'REVISION_REQUESTED',
                                                      'RECEIVER_SIGNED', 'CANCEL_REQUESTED', 'SIGNED', 'COMPLETED',
                                                      'CANCELLED'))
);

CREATE INDEX idx_contract_status_logs_contract
    ON contract_status_logs (contract_id, changed_at);

COMMENT ON TABLE contract_status_logs IS 'WORM 상태 전이 로그 — insert-only, audit/분쟁 증거 5년 보존';
COMMENT ON COLUMN contract_status_logs.from_status IS '이전 상태. NULL = INITIAL (계약 생성 시점)';
COMMENT ON COLUMN contract_status_logs.actor_user_id IS '전이를 일으킨 user. NULL = 시스템 자동 (만료 등)';
COMMENT ON COLUMN contract_status_logs.reason IS '사유 (취소 사유 등 — nullable)';

-- WORM trigger 부착 (refactor ll-2) — V1 promise 와 일관 유지
-- entity 가 val 만 — JPA 가 UPDATE 발행 X. raw SQL 우회 INSERT 만 허용
CREATE TRIGGER trg_contract_status_logs_worm
    BEFORE UPDATE OR DELETE
    ON contract_status_logs
    FOR EACH ROW
EXECUTE FUNCTION worm_protect();

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
    pdf_sha256_at_sign  VARCHAR(64),

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
COMMENT ON COLUMN contract_signatures.pdf_sha256_at_sign IS '서명 시점 PDF sha256 hash 스냅샷 — 분쟁 시 서명한 그 PDF 증명. W6 refactor (e)';

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
CREATE UNIQUE INDEX uq_contract_consents_contract_user_term
    ON contract_consents (contract_id, user_id, term_id);

COMMENT ON TABLE contract_consents IS '계약 도메인 약관 동의 audit (4 + 1). 양측이 각자 서명 직전 동의 — user_consents 와 별도';
COMMENT ON COLUMN contract_consents.term_id IS 'terms 테이블 FK (논리)';
COMMENT ON COLUMN contract_consents.term_version IS '동의 시점 term version (snapshot)';
COMMENT ON INDEX uq_contract_consents_contract_user_term IS '한 user 가 같은 contract 의 같은 term 에 1번만 동의 (audit 카운트 무결성). W6 refactor (g)';

-- ============================================================
-- contract_revision_requests
-- W6: 수신자가 SHARED 상태에서 필드별 수정 이유 입력 → REVISION_REQUESTED 전이
-- WORM (insert-only). 한 계약에 여러 revision 가능 (재요청 시 새 row)
-- ============================================================
CREATE TABLE contract_revision_requests
(
    id                       BIGSERIAL PRIMARY KEY,
    contract_id              BIGINT      NOT NULL REFERENCES contracts (id) ON DELETE RESTRICT,
    requester_user_id        BIGINT      NOT NULL,
    delivery_type_reason     TEXT,
    trading_platform_reason  TEXT,
    title_reason             TEXT,
    price_reason             TEXT,
    condition_summary_reason TEXT,
    condition_details_reason TEXT,
    requested_at             TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_contract_revision_at_least_one_reason
        CHECK (
            delivery_type_reason IS NOT NULL
                OR trading_platform_reason IS NOT NULL
                OR title_reason IS NOT NULL
                OR price_reason IS NOT NULL
                OR condition_summary_reason IS NOT NULL
                OR condition_details_reason IS NOT NULL
            )
);

CREATE INDEX idx_contract_revision_requests_contract
    ON contract_revision_requests (contract_id, requested_at DESC);

COMMENT ON TABLE contract_revision_requests IS '수신자 수정 요청 audit (WORM). 한 계약에 여러 row 가능 (재요청)';
COMMENT ON COLUMN contract_revision_requests.requester_user_id IS '수정 요청한 수신자 user (논리 FK)';
COMMENT ON COLUMN contract_revision_requests.delivery_type_reason IS '거래 방식 (대면/택배) 수정 이유';
COMMENT ON COLUMN contract_revision_requests.trading_platform_reason IS '거래 발견 플랫폼 수정 이유';
COMMENT ON COLUMN contract_revision_requests.title_reason IS '거래 물품명 수정 이유 (nullable — 해당 필드 수정 안 원하면 NULL)';
COMMENT ON COLUMN contract_revision_requests.price_reason IS '거래 금액 수정 이유';
COMMENT ON COLUMN contract_revision_requests.condition_summary_reason IS '상품 상태 수정 이유';
COMMENT ON COLUMN contract_revision_requests.condition_details_reason IS '상품 상세 설명 수정 이유';

-- WORM trigger 부착 (refactor ll-2) — 이력성 테이블 raw SQL 우회 차단
-- contract_signatures / contract_consents / contract_revision_requests: entity 가 val 만
-- contract_invitations 는 accept 시 UPDATE 발생 (usedAt / accepted_by_user_id) → 부착 X
CREATE TRIGGER trg_contract_signatures_worm
    BEFORE UPDATE OR DELETE
    ON contract_signatures
    FOR EACH ROW
EXECUTE FUNCTION worm_protect();

CREATE TRIGGER trg_contract_consents_worm
    BEFORE UPDATE OR DELETE
    ON contract_consents
    FOR EACH ROW
EXECUTE FUNCTION worm_protect();

CREATE TRIGGER trg_contract_revision_requests_worm
    BEFORE UPDATE OR DELETE
    ON contract_revision_requests
    FOR EACH ROW
EXECUTE FUNCTION worm_protect();

-- ============================================================
-- minor_disclosure_confirmations
-- 미성년자와 거래하는 상대(성인)의 서명 전 위험 고지 확인 audit
-- 이용약관 제32조 제2항 의무 + 분쟁 시 고지 입증 유일 수단
-- ============================================================
CREATE TABLE minor_disclosure_confirmations
(
    id               BIGSERIAL PRIMARY KEY,
    contract_id      BIGINT      NOT NULL REFERENCES contracts (id) ON DELETE CASCADE,
    party_user_id    BIGINT      NOT NULL,
    template_version VARCHAR(20) NOT NULL,
    disclosed_at     TIMESTAMPTZ NOT NULL,
    confirmed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip               INET,
    user_agent       TEXT,
    UNIQUE (contract_id, party_user_id)
);

CREATE INDEX idx_minor_disclosure_confirmations_contract
    ON minor_disclosure_confirmations (contract_id);

COMMENT ON TABLE minor_disclosure_confirmations IS
    '미성년자와 거래하는 상대방(성인)의 위험 고지 확인 audit. 이용약관 제32조 제2항 의무 + 분쟁 시 고지 입증 유일 수단';
COMMENT ON COLUMN minor_disclosure_confirmations.party_user_id IS
    '확인한 상대방(성인) user_id — 논리 FK. 미성년자 계약에서 서명 전 게이트';
COMMENT ON COLUMN minor_disclosure_confirmations.template_version IS
    '고지 문구 버전 (예: "v1"). 코드 상수 MinorDisclosureTemplate.LATEST_VERSION 과 매핑';
COMMENT ON COLUMN minor_disclosure_confirmations.disclosed_at IS
    '프론트가 고지 화면 표시한 시각 — 확인 클릭 시 request body 로 전달';
COMMENT ON COLUMN minor_disclosure_confirmations.confirmed_at IS
    '서버에서 확인 버튼 처리한 시각';
COMMENT ON COLUMN minor_disclosure_confirmations.ip IS
    'inet 타입. 확인 시점 client IP (audit)';
COMMENT ON COLUMN minor_disclosure_confirmations.user_agent IS
    '확인 시점 브라우저/앱 UA (audit)';
