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

COMMENT ON COLUMN terms_versions.type IS 'SERVICE | PRIVACY | MARKETING | LOCATION | CONTRACT_AGREEMENT | ELECTRONIC_SIGNATURE';
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

-- 보호자 동의 흐름 idempotency (refactor ee) — 같은 (token, term) 조합 중복 INSERT 차단
-- partial index: guardian_link_token IS NOT NULL 인 row 만 적용 (성인 흐름은 영향 X)
CREATE UNIQUE INDEX uq_user_consents_guardian_link_term
    ON user_consents (guardian_link_token, terms_version_id)
    WHERE guardian_link_token IS NOT NULL;

-- terms_versions seed (V4 + V10 통합)
-- 가입 단계 4종 + 계약 단계 2종 = 총 6 row
INSERT INTO terms_versions (type, version, title, content_url, content_hash, effective_at)
VALUES ('SERVICE', '1.0', 'TRANA 서비스 이용약관',
        'https://example.com/terms/service-1.0.html',
        repeat('a', 64), now()),
       ('PRIVACY', '1.0', 'TRANA 개인정보 처리방침',
        'https://example.com/terms/privacy-1.0.html',
        repeat('b', 64), now()),
       ('MARKETING', '1.0', '마케팅 정보 수신 동의 (선택)',
        'https://example.com/terms/marketing-1.0.html',
        repeat('c', 64), now()),
       ('LOCATION', '1.0', '위치정보 이용 동의 (선택)',
        'https://example.com/terms/location-1.0.html',
        repeat('d', 64), now()),
       ('CONTRACT_AGREEMENT', '1.0', 'TRANA 거래 계약 동의',
        'https://example.com/terms/contract-agreement-1.0.html',
        repeat('e', 64), now()),
       ('ELECTRONIC_SIGNATURE', '1.0', 'TRANA 전자서명 동의',
        'https://example.com/terms/electronic-signature-1.0.html',
        repeat('f', 64), now());

