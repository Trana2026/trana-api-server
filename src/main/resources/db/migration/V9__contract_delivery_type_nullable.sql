-- V9: contracts.delivery_type NOT NULL → NULL (W6, 미성년 임시저장 흐름)
-- 흐름: createDraft 시점에 deliveryType 생략 가능 → updateDraft 로 채움
-- markReady 단계 validateReadyEligible 에서 NOT NULL 강제 (Service 검증)

ALTER TABLE contracts
    ALTER COLUMN delivery_type DROP NOT NULL;

COMMENT ON COLUMN contracts.delivery_type IS '거래 방식. IN_PROGRESS 단계에서 nullable, READY 이상 NOT NULL 강제 (Service 검증)';
