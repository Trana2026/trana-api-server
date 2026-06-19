-- 마지막 발송 시점 추적 — 마이페이지 기기 관리 화면 "5분 전 사용" UX.
-- FcmDispatchService.sendToUser 가 발송 성공 시 갱신.
-- 미사용 단말 (push off 사용자 / invalid token 사전 정리) 식별에도 활용 가능 (W9+ cleanup).

ALTER TABLE device_tokens
    ADD COLUMN last_used_at TIMESTAMPTZ;
