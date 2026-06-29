-- V5: KYC 도메인 (id_card_verify_sessions + identity_verifications)

-- =========================================
-- id_card_verify_sessions
-- - 신분증 OCR → Verify까지의 임시 세션 (NCP requestId가 PK, 10분 TTL)
-- - 평문 식별번호 + S3 사진 키 + 사용자 입력 phone 임시 보관
-- - 만료된 row는 @Scheduled cleanup task가 삭제
-- =========================================
CREATE TABLE id_card_verify_sessions
(
    request_id                VARCHAR(100) PRIMARY KEY,
    id_type                   VARCHAR(30)  NOT NULL,
    name                      VARCHAR(100) NOT NULL,
    personal_number_encrypted BYTEA,
    license_number            VARCHAR(30),
    license_security_code     VARCHAR(20),
    serial_number             VARCHAR(30),
    issue_date                DATE,
    ocr_mask_polygons         TEXT,
    id_card_s3_key            VARCHAR(200),
    id_card_mime              VARCHAR(50),
    expires_at                TIMESTAMPTZ  NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_id_card_verify_session_expires ON id_card_verify_sessions (expires_at);

COMMENT ON TABLE id_card_verify_sessions IS 'OCR → Verify 임시 세션 (10분 TTL). 평문 식별번호 BYTEA 암호화';
COMMENT ON COLUMN id_card_verify_sessions.request_id IS 'NCP Document API requestId (Verify 호출 키)';
COMMENT ON COLUMN id_card_verify_sessions.id_type IS 'ID_CARD | DRIVER_LICENSE | ALIEN_REGISTRATION';
COMMENT ON COLUMN id_card_verify_sessions.personal_number_encrypted IS 'AES-256-GCM (ic/dl 주민번호, ac 외국인등록번호)';
COMMENT ON COLUMN id_card_verify_sessions.ocr_mask_polygons IS 'NCP maskingPolys JSON 직렬화 (프리뷰 합성용)';
COMMENT ON COLUMN id_card_verify_sessions.id_card_s3_key IS '신분증 사진 S3 키 (Compare 시 GET, 사용 후 즉시 DELETE)';
COMMENT ON COLUMN id_card_verify_sessions.id_card_mime IS '신분증 사진 MIME (image/jpeg, image/png)';

-- =========================================
-- identity_verifications
-- - KYC 시도/결과 영구 기록 (audit + 분쟁 증거 + 중복 가입 방지)
-- - 본인 KYC (purpose=SIGNUP) + 보호자 KYC (purpose=GUARDIAN, Phase 6에서 활용)
-- =========================================
CREATE TABLE identity_verifications
(
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT,
    signup_session_id       UUID,
    id_type                 VARCHAR(30),
    status                  VARCHAR(20) NOT NULL,
    ncp_document_request_id VARCHAR(100),
    identifier_hash         VARCHAR(64),
    client_tx_id            VARCHAR(40),
    ci_hash                 VARCHAR(64),
    name                    VARCHAR(100),
    birth_date              DATE,
    gender                  VARCHAR(10),
    phone                   VARCHAR(255),
    verify_passed           BOOLEAN     NOT NULL,
    verify_error_code       VARCHAR(50),
    verify_error_message    TEXT,
    face_similarity         DOUBLE PRECISION,
    face_match              BOOLEAN,
    failure_step            VARCHAR(30),
    purpose                 VARCHAR(20) NOT NULL DEFAULT 'SIGNUP',
    subject_user_id         BIGINT,
    guardian_id             BIGINT,
    guardian_link_token     VARCHAR(64),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_identity_verifications_user ON identity_verifications (user_id, created_at DESC);
CREATE INDEX idx_identity_verifications_session ON identity_verifications (signup_session_id);
CREATE INDEX idx_identity_verifications_hash
    ON identity_verifications (identifier_hash) WHERE identifier_hash IS NOT NULL;
CREATE INDEX idx_identity_verifications_ci_hash
    ON identity_verifications (ci_hash) WHERE ci_hash IS NOT NULL;
CREATE INDEX idx_identity_verifications_client_tx_id
    ON identity_verifications (client_tx_id) WHERE client_tx_id IS NOT NULL;
CREATE INDEX idx_identity_verifications_subject ON identity_verifications (subject_user_id, created_at DESC);
CREATE INDEX idx_identity_verifications_guardian ON identity_verifications (guardian_id, created_at DESC);


COMMENT ON TABLE identity_verifications IS 'KYC 시도/결과 영구 기록 (audit + 분쟁 증거)';
COMMENT ON COLUMN identity_verifications.user_id IS '논리 FK (cascade 사고 방지). SIGNUP은 Compare SUCCESS 시 백필';
COMMENT ON COLUMN identity_verifications.signup_session_id IS '성인 가입 multi-step 매칭 (user 생성 전)';
COMMENT ON COLUMN identity_verifications.id_type IS 'NCP: ID_CARD/DRIVER_LICENSE/ALIEN_REGISTRATION. PASS row 는 NULL';
COMMENT ON COLUMN identity_verifications.status IS 'PENDING | SUCCESS | FAILED — OCR 시 PENDING 생성';
COMMENT ON COLUMN identity_verifications.ncp_document_request_id IS 'NCP Document API requestId. PASS row 는 NULL';
COMMENT ON COLUMN identity_verifications.identifier_hash IS 'SHA-256 (주민번호/외국인등록번호) — 중복 가입 lookup';
COMMENT ON COLUMN identity_verifications.identifier_hash IS 'SHA-256 (NCP 주민번호/외국인등록번호). PASS row 는 NULL (대신 ci_hash 채움)';
COMMENT ON COLUMN identity_verifications.client_tx_id IS 'PASS 표준창 clientTxId (20~40자, 매 요청 고유. PASS-9 에서 UNIQUE 강제)';
COMMENT ON COLUMN identity_verifications.phone IS 'Verify 단계 사용자 입력. Compare SUCCESS 시 user.phone 백필';
COMMENT ON COLUMN identity_verifications.failure_step IS 'OCR | VERIFY | COMPARE | NULL(성공)';
COMMENT ON COLUMN identity_verifications.purpose IS 'SIGNUP(본인) | GUARDIAN(보호자) — Phase 6에서 GUARDIAN 사용';
COMMENT ON COLUMN identity_verifications.subject_user_id IS 'GUARDIAN 인증 시 보호 대상(미성년자) user_id';
COMMENT ON COLUMN identity_verifications.guardian_id IS 'GUARDIAN 인증 SUCCESS 시 guardians FK (논리)';
COMMENT ON COLUMN identity_verifications.guardian_link_token IS 'GUARDIAN 인증 시 사용된 매칭 토큰';


