-- V1: 사용자 + 소셜 계정 (W2 가입/로그인 최소 셋업)
-- KYC/약관/토큰/기기 등은 해당 단계 도입 시 ALTER 또는 V1 보강

-- =========================================
-- users: 가입한 사용자
-- =========================================
CREATE TABLE users
(
    id           BIGSERIAL PRIMARY KEY,
    public_code  VARCHAR(20) NOT NULL UNIQUE,
    email        VARCHAR(255) UNIQUE,
    nickname     VARCHAR(50),
    age_group    VARCHAR(10),
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    name         VARCHAR(255),
    birth_date   VARCHAR(50),
    gender       VARCHAR(10),
    phone        VARCHAR(255),
    push_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    withdrawn_at TIMESTAMPTZ
);

CREATE INDEX idx_users_public_code ON users (public_code);

COMMENT ON COLUMN users.public_code IS '외부 노출용 식별자 (nanoid 12자, URL-safe)';
COMMENT ON COLUMN users.email IS 'OAuth 공급자가 제공 시 저장 (Apple은 첫 로그인에만 제공)';
COMMENT ON COLUMN users.nickname IS '가입 시 사용자 입력 또는 OAuth nickname';
COMMENT ON COLUMN users.age_group IS 'ADULT | MINOR — KYC 후 채움 (가입 시점에는 nullable)';
COMMENT ON COLUMN users.status IS 'ACTIVE | WITHDRAWN';
COMMENT ON COLUMN users.name IS 'AES-256-GCM 암호화 (KYC OCR 결과, W3에서 채움)';
COMMENT ON COLUMN users.birth_date IS 'AES-256-GCM 암호화 (KYC 후 채움)';
COMMENT ON COLUMN users.gender IS 'MALE | FEMALE | OTHER';
COMMENT ON COLUMN users.phone IS 'AES-256-GCM 암호화 (KYC 후 채움)';
COMMENT ON COLUMN users.push_enabled IS 'FCM 알림 수신 여부 (default true)';
COMMENT ON COLUMN users.withdrawn_at IS '탈퇴 시각 (NULL이면 활성)';

-- =========================================
-- social_accounts: OAuth 매핑 (한 user가 여러 provider 연결 가능)
-- =========================================
CREATE TABLE social_accounts
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_social_accounts_user ON social_accounts (user_id);

COMMENT ON COLUMN social_accounts.provider IS 'KAKAO | GOOGLE | APPLE';
COMMENT ON COLUMN social_accounts.provider_user_id IS '공급자가 발급한 사용자 ID (변경 불가, 매핑 키)';

-- =========================================
-- terms_versions: 약관 버전 관리
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
COMMENT ON COLUMN terms_versions.content_url IS '약관 본문 위치 (S3 또는 사내 CDN)';
COMMENT ON COLUMN terms_versions.content_hash IS '본문 SHA-256 (변조 방지 / 동의 시 hash 같이 기록 가능)';
COMMENT ON COLUMN terms_versions.effective_at IS '시행 시각 (이 시점부터 신규 동의는 이 버전)';

-- =========================================
-- user_consents: 사용자 약관 동의 기록 (법적 증거)
-- =========================================
CREATE TABLE user_consents
(
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT REFERENCES users (id),
    terms_version_id  BIGINT      NOT NULL REFERENCES terms_versions (id),
    context_type      VARCHAR(20) NOT NULL,
    context_id        BIGINT,
    signup_session_id UUID,
    age_group         VARCHAR(10) NOT NULL,
    agreed_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip                INET        NOT NULL,
    user_agent        TEXT
);

CREATE INDEX idx_user_consents_user ON user_consents (user_id);
CREATE INDEX idx_user_consents_signup_session ON user_consents (signup_session_id);

COMMENT ON COLUMN user_consents.user_id IS 'NULL 허용 — multi-step signup 시점 (signup_session_id로 매칭, KYC 완료 후 backfill)';
COMMENT ON COLUMN user_consents.terms_version_id IS '동의한 정확한 약관 버전 (법적 증거)';
COMMENT ON COLUMN user_consents.context_type IS 'SIGNUP | CONTRACT | MARKETING | KYC';
COMMENT ON COLUMN user_consents.context_id IS 'polymorphic FK (context_type별 가리키는 테이블 다름, FK 미설정)';
COMMENT ON COLUMN user_consents.signup_session_id IS 'multi-step signup의 임시 세션 (user 생성 전 동의 위해)';
COMMENT ON COLUMN user_consents.age_group IS '동의 시점의 ADULT | MINOR (보호자 동의 흐름 분기용)';
COMMENT ON COLUMN user_consents.ip IS '동의 시점 IP (법적 증거 — NOT NULL)';

-- =========================================
-- worm_protect: WORM 테이블의 UPDATE/DELETE 차단
-- audit_logs / signatures 등 이력성 테이블에 적용
-- =========================================
CREATE OR REPLACE FUNCTION worm_protect()
    RETURNS TRIGGER AS
$$
BEGIN
    RAISE EXCEPTION 'WORM table: % is immutable (operation=%)', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION worm_protect() IS 'WORM 트리거 함수 — UPDATE/DELETE 시 예외 발생';

-- =========================================
-- audit_logs: 감사 로그 (WORM, 법적 증거)
-- - 분쟁/감사/이상행위 추적용
-- - 파티셔닝은 W7~W9 단계에서 도입 예정 (현재는 단순 테이블)
-- =========================================
CREATE TABLE audit_logs
(
    id            BIGSERIAL PRIMARY KEY,
    event_type    VARCHAR(50) NOT NULL,
    actor_user_id BIGINT,
    entity_type   VARCHAR(50),
    entity_id     BIGINT,
    metadata      JSONB,
    ip            INET,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_user_id, created_at DESC);
CREATE INDEX idx_audit_logs_event ON audit_logs (event_type, created_at DESC);

COMMENT ON TABLE audit_logs IS 'WORM. 절대 UPDATE/DELETE 금지. 파티셔닝은 W7~W9에서 도입';
COMMENT ON COLUMN audit_logs.event_type IS '예: USER_SIGNED_IN, CONTRACT_CREATED, KYC_ATTEMPTED';
COMMENT ON COLUMN audit_logs.actor_user_id IS '논리 FK only (users 삭제 시 cascade 사고 방지로 FK 미설정)';
COMMENT ON COLUMN audit_logs.entity_type IS '대상 리소스 종류 (USER | CONTRACT | SIGNATURE 등)';
COMMENT ON COLUMN audit_logs.entity_id IS '대상 리소스 PK';
COMMENT ON COLUMN audit_logs.metadata IS '부가 정보 (provider, reason, score 등)';
COMMENT ON COLUMN audit_logs.ip IS 'PostgreSQL INET — IPv4/IPv6 모두';

-- =========================================
-- audit_logs WORM 트리거
-- =========================================
CREATE TRIGGER trg_audit_logs_worm
    BEFORE UPDATE OR DELETE
    ON audit_logs
    FOR EACH ROW
EXECUTE FUNCTION worm_protect();
