-- V7: 계약 도메인 (W4 범위 — contracts + parties + attachments + ai_extractions)
-- W5 (invitations / signatures / PDF / final_hash) 는 진입 시 같은 V7 수정 (개발 단계)
-- 도메인 내부 FK = ON DELETE CASCADE / 다른 도메인 user_id = 논리 FK (기존 V 파일 정책)

-- ============================================================
-- contracts
-- - 작성 권한: creator_user_id 만 (DRAFT 단계)
-- - status × dispute_state 직교 (W7 분쟁)
-- - 미성년 분기는 consent_type 으로 표현
-- ============================================================
CREATE TABLE contracts
(
    id                         BIGSERIAL PRIMARY KEY,
    public_code                VARCHAR(20) NOT NULL UNIQUE,
    creator_user_id            BIGINT      NOT NULL,
    status                     VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    dispute_state              VARCHAR(20) NOT NULL DEFAULT 'NONE',

    -- 거래 정보
    delivery_type              VARCHAR(20) NOT NULL,
    title                      VARCHAR(200),
    price                      BIGINT,
    condition_summary          TEXT,
    condition_details          TEXT,
    warranty_period_days       INT         NOT NULL DEFAULT 3,
    location                   VARCHAR(100),

    -- 미성년 분기
    consent_type               VARCHAR(30) NOT NULL,
    guardian_id                BIGINT,
    guardian_consent_at        TIMESTAMPTZ,

    -- 동의 / 약관 (W5+ 채워짐)
    acknowledgements_version   VARCHAR(20),
    acknowledged_at            TIMESTAMPTZ,
    sign_terms_version         VARCHAR(20),
    sign_terms_acknowledged_at TIMESTAMPTZ,

    -- 거래 완료 (W6)
    seller_completed_at        TIMESTAMPTZ,
    buyer_completed_at         TIMESTAMPTZ,
    completed_at               TIMESTAMPTZ,

    -- 본문 / 무결성 (W5)
    generated_body             TEXT,
    content_hash               CHAR(64),
    final_hash                 CHAR(64),
    version                    INT         NOT NULL DEFAULT 1,

    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at                 TIMESTAMPTZ
);

CREATE INDEX idx_contracts_public_code ON contracts (public_code);
CREATE INDEX idx_contracts_creator ON contracts (creator_user_id, created_at DESC);
CREATE INDEX idx_contracts_status ON contracts (status) WHERE deleted_at IS NULL;

COMMENT ON TABLE contracts IS 'C2C 안전 거래 전자계약. status × dispute_state 직교';
COMMENT ON COLUMN contracts.public_code IS 'jnanoid 12자 (외부 노출용)';
COMMENT ON COLUMN contracts.creator_user_id IS '작성자 user_id (논리 FK — cascade 사고 방지)';
COMMENT ON COLUMN contracts.status IS 'DRAFT | SIGN_REQUESTED | REVISION_REQUESTED | SIGNED | COMPLETED';
COMMENT ON COLUMN contracts.dispute_state IS 'NONE | REPORTED | RESOLVED | DISMISSED (W7)';
COMMENT ON COLUMN contracts.delivery_type IS 'DIRECT | SHIPPING';
COMMENT ON COLUMN contracts.price IS '원 단위 정수';
COMMENT ON COLUMN contracts.warranty_period_days IS '보증 기간 일수 (현재 3일 고정)';
COMMENT ON COLUMN contracts.location IS 'AI 추출 결과 (nullable)';
COMMENT ON COLUMN contracts.consent_type IS 'GUARDIAN_REQUIRED | NONE | NOT_APPLICABLE';
COMMENT ON COLUMN contracts.guardian_id IS 'CONTRACT_CONSENT 보호자 KYC SUCCESS 시 guardians FK (논리)';
COMMENT ON COLUMN contracts.content_hash IS 'SHA-256 (본문 + 첨부 메타). W5 PDF 생성 시 확정';
COMMENT ON COLUMN contracts.final_hash IS 'SHA-256. 양측 SIGNED 시점 확정 (W5)';
COMMENT ON COLUMN contracts.version IS 'REVISION 시 증가 (W5)';
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
    UNIQUE (contract_id, party_type)
);

CREATE INDEX idx_contract_parties_contract ON contract_parties (contract_id);
CREATE INDEX idx_contract_parties_user ON contract_parties (user_id, joined_at DESC);

COMMENT ON COLUMN contract_parties.party_type IS 'SELLER | BUYER';
COMMENT ON COLUMN contract_parties.validated IS '본인 확인(KYC SUCCESS + ACTIVE user) 통과 여부';

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
-- guardian_links 확장 (W4)
-- 기존 SIGNUP 흐름 + 신규 CONTRACT_CONSENT
-- - SIGNUP: contract_id 는 반드시 NULL
-- - CONTRACT_CONSENT: contract_id 는 반드시 NOT NULL
-- ============================================================
ALTER TABLE guardian_links
    ADD COLUMN purpose     VARCHAR(30) NOT NULL DEFAULT 'SIGNUP',
    ADD COLUMN contract_id BIGINT;

ALTER TABLE guardian_links
    ADD CONSTRAINT chk_guardian_links_purpose_contract
        CHECK (
            (purpose = 'SIGNUP' AND contract_id IS NULL)
                OR (purpose = 'CONTRACT_CONSENT' AND contract_id IS NOT NULL)
            );

CREATE INDEX idx_guardian_links_contract
    ON guardian_links (contract_id) WHERE purpose = 'CONTRACT_CONSENT';

COMMENT ON COLUMN guardian_links.purpose IS 'SIGNUP (미성년 가입) | CONTRACT_CONSENT (계약 보호자 동의, W4+)';
COMMENT ON COLUMN guardian_links.contract_id IS 'CONTRACT_CONSENT 일 때만 NOT NULL (CHECK 제약)';
