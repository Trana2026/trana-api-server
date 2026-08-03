-- 만료 30분 전 경고 알림톡(UJ_9524) 발송 멱등용.
-- 값이 있으면 이미 경고 발송함 → 중복 발송 방지.
ALTER TABLE contracts
    ADD COLUMN expiry_warned_at TIMESTAMPTZ;

COMMENT ON COLUMN contracts.expiry_warned_at IS '만료 30분 전 경고 알림톡 발송 시각 (중복 발송 방지). null=미발송';
