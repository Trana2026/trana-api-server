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
