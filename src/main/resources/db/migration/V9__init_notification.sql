-- V13: FCM 디바이스 토큰 도메인 (Phase C 시작)
-- - device_tokens: 사용자별 FCM 토큰 다중 등록 (multi-device 지원 — 명세서 2.4.5)
-- - token_encrypted (AES-256-GCM) + token_hash (SHA-256 hex 64자) 분리
--   → 랜덤 IV 로 평문 매칭 불가 문제를 hash 보조 컬럼으로 해소 (identity.identifier_hash 동일 패턴)
-- - platform CHECK = ANDROID | IOS (명세서 P 카테고리 OS 푸시만)
-- - audit 성격 아님 — 갱신/삭제 자유 (WORM trigger 없음). user 탈퇴 시 ON DELETE CASCADE

-- ============================================================
-- device_tokens
-- ============================================================
CREATE TABLE device_tokens
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_encrypted TEXT        NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,
    platform        VARCHAR(16) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at    TIMESTAMPTZ,

    CONSTRAINT chk_device_tokens_platform
        CHECK (platform IN ('ANDROID', 'IOS')),
    CONSTRAINT uq_device_tokens_hash
        UNIQUE (token_hash)
);

CREATE INDEX idx_device_tokens_user_id
    ON device_tokens (user_id);

COMMENT ON TABLE device_tokens IS
    'FCM 디바이스 토큰 (Phase C). 한 user 가 여러 row 보유 가능 (multi-device). C-5 가 invalid 응답 받아 삭제, user 탈퇴 시 CASCADE';
COMMENT ON COLUMN device_tokens.user_id IS
    '소유자 user (ON DELETE CASCADE — 탈퇴 시 토큰 자동 정리)';
COMMENT ON COLUMN device_tokens.token_encrypted IS
    'AES-256-GCM 암호화 (랜덤 IV). LiveFcmClient.send 가 복호화 후 FCM 호출 (명세서 보안 정책)';
COMMENT ON COLUMN device_tokens.token_hash IS
    'SHA-256 deterministic hex (64자). UNIQUE 제약 + 등록 시 중복 체크 + invalid 응답 매칭';
COMMENT ON COLUMN device_tokens.platform IS
    'ANDROID | IOS — 명세서 P01~P04 OS 푸시만 지원. WEB 푸시는 요건 없음';
COMMENT ON INDEX idx_device_tokens_user_id IS
    '한 user 의 모든 활성 토큰 일괄 조회 (멀티캐스트 발송 시점)';
COMMENT ON INDEX uq_device_tokens_hash IS
    '같은 단말이 재로그인해도 token_hash 동일 → row 1개 유지 (C-4 가 user_id 갱신 또는 delete+insert 결정)';
COMMENT ON COLUMN device_tokens.last_used_at IS
    '마이페이지 기기 관리 "최근 활동 시각" — Flutter ping endpoint / FCM 발송 성공 시 갱신';
