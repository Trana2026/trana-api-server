-- V4: PASS 신원확인 (identity_verifications)
-- id_card_verify_sessions 테이블 + NCP 관련 컬럼은 PASS-9 정리로 제거됨

-- =========================================
-- identity_verifications
-- - PASS 시도/결과 영구 기록 (audit + 분쟁 증거 + 중복 가입 방지)
-- - 본인 KYC (purpose=SIGNUP) + 보호자 KYC (purpose=GUARDIAN)
-- =========================================
CREATE TABLE identity_verifications
(
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT,
    signup_session_id   UUID,
    status              VARCHAR(20) NOT NULL,
    client_tx_id        VARCHAR(40),
    ci_hash             VARCHAR(64),
    name                VARCHAR(100),
    birth_date          DATE,
    gender              VARCHAR(10),
    phone               VARCHAR(255),
    purpose             VARCHAR(20) NOT NULL DEFAULT 'SIGNUP',
    subject_user_id     BIGINT,
    guardian_id         BIGINT,
    guardian_link_token VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_identity_verifications_user ON identity_verifications (user_id, created_at DESC);
CREATE INDEX idx_identity_verifications_session ON identity_verifications (signup_session_id);
CREATE INDEX idx_identity_verifications_ci_hash
    ON identity_verifications (ci_hash) WHERE ci_hash IS NOT NULL;
CREATE INDEX idx_identity_verifications_client_tx_id
    ON identity_verifications (client_tx_id) WHERE client_tx_id IS NOT NULL;
CREATE INDEX idx_identity_verifications_subject ON identity_verifications (subject_user_id, created_at DESC);
CREATE INDEX idx_identity_verifications_guardian ON identity_verifications (guardian_id, created_at DESC);


COMMENT ON TABLE identity_verifications IS 'PASS 본인확인 시도/결과 영구 기록 (audit + 분쟁 증거)';
COMMENT ON COLUMN identity_verifications.user_id IS '논리 FK (cascade 사고 방지). SIGNUP 은 return SUCCESS 시 백필';
COMMENT ON COLUMN identity_verifications.signup_session_id IS '성인 가입 multi-step 매칭 (user 생성 전)';
COMMENT ON COLUMN identity_verifications.status IS 'PENDING (req-client-info) → SUCCESS (return 복호화 성공)';
COMMENT ON COLUMN identity_verifications.client_tx_id IS 'PASS 표준창 clientTxId (20~40자, 매 요청 고유)';
COMMENT ON COLUMN identity_verifications.ci_hash IS 'PASS 본인확인 ci SHA-256 (중복 가입/사기 lookup 키)';
COMMENT ON COLUMN identity_verifications.phone IS 'PASS 결과 phone (return endpoint 에서 백필)';
COMMENT ON COLUMN identity_verifications.purpose IS 'SIGNUP (본인) | GUARDIAN (보호자)';
COMMENT ON COLUMN identity_verifications.subject_user_id IS 'GUARDIAN 인증 시 보호 대상(미성년자) user_id';
COMMENT ON COLUMN identity_verifications.guardian_id IS 'GUARDIAN 인증 SUCCESS 시 guardians FK (논리)';
COMMENT ON COLUMN identity_verifications.guardian_link_token IS 'GUARDIAN 인증 시 사용된 매칭 토큰';
