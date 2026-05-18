-- V3: 약관/동의 도메인 (terms_versions + user_consents)

-- =========================================
-- terms_versions
-- =========================================
CREATE TABLE terms_versions
(
    id           BIGSERIAL PRIMARY KEY,
    type         VARCHAR(20)  NOT NULL DEFAULT 'SERVICE',
    version      VARCHAR(20)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    content_url  TEXT         NOT NULL,
    content_hash VARCHAR(64)  NOT NULL,
    effective_at TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (type, version)
);

CREATE INDEX idx_terms_versions_type_effective ON terms_versions (type, effective_at DESC);

COMMENT ON COLUMN terms_versions.type IS 'SERVICE | PRIVACY | MARKETING | LOCATION';
COMMENT ON COLUMN terms_versions.version IS '시맨틱 버전 (1.0, 1.1, 2.0)';
COMMENT ON COLUMN terms_versions.content_url IS '약관 본문 위치 (S3 또는 CDN)';
COMMENT ON COLUMN terms_versions.content_hash IS '본문 SHA-256 (변조 방지)';
COMMENT ON COLUMN terms_versions.effective_at IS '시행 시각 (이 시점부터 신규 동의는 이 버전)';

-- =========================================
-- user_consents
-- - 성인 가입: user_id=null, signup_session_id=uuid (Compare SUCCESS 시 user_id 백필)
-- - 미성년자/로그인 후: user_id=있음, signup_session_id=null
-- =========================================
CREATE TABLE user_consents
(
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT REFERENCES users (id),
    terms_version_id    BIGINT      NOT NULL REFERENCES terms_versions (id),
    context_type        VARCHAR(20) NOT NULL,
    context_id          BIGINT,
    signup_session_id   UUID,
    guardian_link_token VARCHAR(64),
    age_group           VARCHAR(10) NOT NULL,
    agreed_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip                  INET        NOT NULL,
    user_agent          TEXT
);

CREATE INDEX idx_user_consents_user ON user_consents (user_id);
CREATE INDEX idx_user_consents_signup_session ON user_consents (signup_session_id);
CREATE INDEX idx_user_consents_guardian_link ON user_consents (guardian_link_token);

COMMENT ON COLUMN user_consents.user_id IS 'SIGNUP=null→Compare SUCCESS 시 백필 / GUARDIAN_CONSENT=미성년자 user_id';
COMMENT ON COLUMN user_consents.terms_version_id IS '동의한 정확한 약관 버전 (법적 증거)';
COMMENT ON COLUMN user_consents.context_type IS 'SIGNUP | GUARDIAN_CONSENT | CONTRACT | MARKETING | KYC';
COMMENT ON COLUMN user_consents.context_id IS 'polymorphic FK (FK 미설정, context_type별 가리키는 테이블 다름)';
COMMENT ON COLUMN user_consents.signup_session_id IS '성인 가입 매칭 (서버 UUID, TTL 30분). 보호자 동의는 null';
COMMENT ON COLUMN user_consents.guardian_link_token IS '보호자 동의 매칭 (guardian_links.token). 성인 가입은 null';
COMMENT ON COLUMN user_consents.age_group IS '동의 시점 ADULT | MINOR (보호자 동의 흐름 분기용)';
COMMENT ON COLUMN user_consents.ip IS '동의 시점 IP (법적 증거 — NOT NULL)';
