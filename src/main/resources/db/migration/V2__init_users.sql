-- V2: users + social_accounts (도메인 코어)

-- =========================================
-- users
-- - 성인: KYC SUCCESS 시점에 INSERT (KYC 결과로 모든 필드 채워짐, age_group=ADULT)
-- - 미성년자: 소셜 로그인 시점에 INSERT (nickname/email + age_group=MINOR, KYC 필드는 null)
-- - 미성년자 가입 완료 = guardian_verified_at != null
-- =========================================
CREATE TABLE users
(
    id                          BIGSERIAL PRIMARY KEY,
    public_code                 VARCHAR(20) NOT NULL UNIQUE,
    email                       VARCHAR(255) UNIQUE,
    ci_hash                     VARCHAR(64),
    age_group                   VARCHAR(10),
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    name                        VARCHAR(255),
    birth_date                  VARCHAR(50),
    gender                      VARCHAR(10),
    phone                       VARCHAR(255),
    guardian_verified_at        TIMESTAMPTZ,
    push_enabled                BOOLEAN     NOT NULL DEFAULT TRUE,
    trust_score                 INT         NOT NULL DEFAULT 35
        CHECK (trust_score BETWEEN 0 AND 100),
    completed_contract_count    INT         NOT NULL DEFAULT 0
        CHECK (completed_contract_count >= 0),
    warranty_provided_count     INT         NOT NULL DEFAULT 0
        CHECK (warranty_provided_count >= 0),
    fraud_report_filed_count    INT         NOT NULL DEFAULT 0
        CHECK (fraud_report_filed_count >= 0),
    fraud_report_received_count INT         NOT NULL DEFAULT 0
        CHECK (fraud_report_received_count >= 0),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    withdrawn_at                TIMESTAMPTZ
);

CREATE INDEX idx_users_public_code ON users (public_code);
CREATE INDEX idx_users_ci_hash ON users (ci_hash) WHERE ci_hash IS NOT NULL;

COMMENT ON COLUMN users.public_code IS '외부 노출용 식별자 (nanoid 12자, URL-safe)';
COMMENT ON COLUMN users.email IS '소셜 로그인 시 공급자 제공 (미성년자만, 성인은 null 가능)';
COMMENT ON COLUMN users.ci_hash IS 'PASS 본인확인 ci SHA-256 (Option B: ADD-only NULL 허용. PASS-9 에서 NOT NULL + UNIQUE 변경 예정)';
COMMENT ON COLUMN users.age_group IS 'ADULT | MINOR — 가입 흐름에서 결정. NULL = 가입 미완 임시 상태';
COMMENT ON COLUMN users.status IS 'ACTIVE | WITHDRAWN';
COMMENT ON COLUMN users.name IS '표시명 — 성인: KYC 실명 / 미성년: 소셜 표시명 (V14 nickname 통합)';
COMMENT ON COLUMN users.birth_date IS 'KYC OCR 결과 — yyyy-MM-dd';
COMMENT ON COLUMN users.gender IS 'MALE | FEMALE | OTHER';
COMMENT ON COLUMN users.phone IS 'Verify 단계 사용자 입력 (성인) 또는 NULL (미성년자)';
COMMENT ON COLUMN users.guardian_verified_at IS '보호자 인증 완료 시각 (MINOR만). NULL이면 미인증';
COMMENT ON COLUMN users.push_enabled IS 'FCM 알림 수신 여부';
COMMENT ON COLUMN users.trust_score IS '신뢰 점수 (0~100). 신규 가입 default 35. SOT = trust_score_events';
COMMENT ON COLUMN users.completed_contract_count IS '양측 서명 완료 (SIGNED) 계약 누적 (캐시, 마이페이지 통계)';
COMMENT ON COLUMN users.warranty_provided_count IS '판매자 보증 제공 + SIGNED 누적 (캐시, 마이페이지 통계)';
COMMENT ON COLUMN users.fraud_report_filed_count IS '본인이 신고한 건 중 사기 확인 누적';
COMMENT ON COLUMN users.fraud_report_received_count IS '본인이 신고 당한 건 중 사기 확인 누적 (마이페이지 통계의 "분쟁 여부")';
COMMENT ON COLUMN users.withdrawn_at IS '탈퇴 시각. NULL이면 활성';

-- 1:1 문의 (단방향) — 사용자 → 운영자.
-- 운영자 회신은 Slack 채널 + 사용자 입력 이메일로 직접 회신 (DB 저장 X).
-- 첨부파일 / 상태 / 답변 컬럼 X — 단순 audit 용.

CREATE TABLE user_inquiries
(
    id          BIGSERIAL PRIMARY KEY,
    public_code VARCHAR(20)  NOT NULL UNIQUE,
    user_id     BIGINT       NOT NULL,
    email       VARCHAR(255) NOT NULL,
    title       VARCHAR(100) NOT NULL,
    content     TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_inquiries_user_created
    ON user_inquiries (user_id, created_at DESC);
