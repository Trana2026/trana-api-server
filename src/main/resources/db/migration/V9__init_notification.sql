-- V9: 알림 도메인 (device_tokens + notifications)
-- - device_tokens: 사용자별 FCM 토큰 다중 등록 (multi-device — 명세서 2.4.5)
-- - notifications: 앱 안 알림함 (목록/읽음/삭제)
-- - token_encrypted (AES-256-GCM) + token_hash (SHA-256 hex 64자) 분리
--   → 랜덤 IV 로 평문 매칭 불가 문제를 hash 보조 컬럼으로 해소 (identity.identifier_hash 동일 패턴)
-- - platform CHECK = ANDROID | IOS (명세서 P 카테고리 OS 푸시만)
-- - audit 성격 아님 — 갱신/삭제 자유 (WORM trigger 없음). user 탈퇴 시 ON DELETE CASCADE
-- - 위치정보 (city/country) 는 위치정보법 개인위치정보 해당 → OS/앱 버전으로 대체 (2026-07-10 refactor)

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
    device_model    VARCHAR(100),
    os_version      VARCHAR(32),
    app_version     VARCHAR(32),
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
COMMENT ON COLUMN device_tokens.device_model IS
    'Flutter device_info_plus 로 식별한 기기 모델명 (예: "iPhone 15 Pro" / "SM-G998N"). 마이페이지 기기 관리 UX 노출용';
COMMENT ON COLUMN device_tokens.os_version IS
    'Flutter Platform.operatingSystemVersion (예: "iOS 18.2", "Android 14"). 마이페이지 기기 관리 표시용. 앱 이전 버전 미전송 시 NULL';
COMMENT ON COLUMN device_tokens.app_version IS
    'Flutter package_info_plus.version (예: "1.2.3+45"). 마이페이지 기기 관리 표시용. 앱 이전 버전 미전송 시 NULL';

-- ============================================================
-- notifications
-- ============================================================
CREATE TABLE notifications
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category   VARCHAR(32)   NOT NULL,
    title      VARCHAR(200)  NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    deep_link  VARCHAR(500),
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_created
    ON notifications (user_id, created_at DESC);

COMMENT ON TABLE notifications IS
    '앱 안 알림함 — user 별 알림 목록. push 발송 여부와 무관 저장 (pushEnabled=false 여도 리스트 노출). audit 성격 아님 (hard delete)';
COMMENT ON COLUMN notifications.user_id IS
    '알림 소유자 (ON DELETE CASCADE — 탈퇴 시 알림 자동 정리)';
COMMENT ON COLUMN notifications.category IS
    'CONTRACT (계약 서명/완료/수정 요청/취소 등). CHECK 제약 X — 코드 enum NotificationCategory 로만 제약, 카테고리 확장 유연';
COMMENT ON COLUMN notifications.title IS
    '알림 제목 — Flutter 리스트 카드 상단 (예: "새 계약서 도착")';
COMMENT ON COLUMN notifications.body IS
    '알림 본문 — Flutter 리스트 카드 하단 (예: "홍길동님이 서명을 요청했어요")';
COMMENT ON COLUMN notifications.deep_link IS
    'Flutter 앱 라우팅 URL (예: "trana://contracts/{publicCode}"). 리스트 탭 시 이동. null 이면 이동 X';
COMMENT ON COLUMN notifications.read_at IS
    '읽음 처리 시각 (null=미읽음). PATCH /v1/notifications/{id}/read 로 갱신 (idempotent — 이미 읽음이면 no-op)';
COMMENT ON INDEX idx_notifications_user_created IS
    '알림 리스트 최신순 페이징 (createdAt DESC 서버 강제)';
