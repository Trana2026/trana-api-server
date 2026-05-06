-- V1: 사용자 + 소셜 계정 (W2 가입/로그인 최소 셋업)
-- KYC/약관/토큰/기기 등은 해당 단계 도입 시 ALTER 또는 V1 보강

-- =========================================
-- users: 가입한 사용자
-- =========================================
CREATE TABLE users (
                       id           BIGSERIAL    PRIMARY KEY,
                       public_code  VARCHAR(20)  NOT NULL UNIQUE,
                       email        VARCHAR(255) UNIQUE,
                       nickname     VARCHAR(50),
                       status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                       created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_public_code ON users (public_code);

COMMENT ON COLUMN users.public_code IS '외부 노출용 식별자 (nanoid 12자, URL-safe)';
  COMMENT ON COLUMN users.email IS 'OAuth 공급자가 제공 시 저장 (Apple은 첫 로그인에만 제공)';
  COMMENT ON COLUMN users.nickname IS '가입 시 사용자 입력 또는 OAuth nickname (nullable)';
  COMMENT ON COLUMN users.status IS 'ACTIVE | WITHDRAWN';

  -- =========================================
  -- social_accounts: OAuth 매핑 (한 user가 여러 provider 연결 가능)
  -- =========================================
CREATE TABLE social_accounts (
                                 id                BIGSERIAL    PRIMARY KEY,
                                 user_id           BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                 provider          VARCHAR(20)  NOT NULL,
                                 provider_user_id  VARCHAR(255) NOT NULL,
                                 created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                 UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_social_accounts_user ON social_accounts (user_id);

COMMENT ON COLUMN social_accounts.provider IS 'KAKAO | GOOGLE | APPLE';
  COMMENT ON COLUMN social_accounts.provider_user_id IS '공급자가 발급한 사용자 ID (변경 불가, 매핑 키)';
