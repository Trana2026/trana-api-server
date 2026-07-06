-- ============================================================
-- contract_cancellation_requests
-- ============================================================
CREATE TABLE contract_cancellation_requests
(
    id                   BIGSERIAL PRIMARY KEY,
    contract_id          BIGINT       NOT NULL REFERENCES contracts (id) ON DELETE RESTRICT,
    requester_user_id    BIGINT       NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
    reason               VARCHAR(100) NOT NULL,
    detail               TEXT         NOT NULL,
    requester_ip         VARCHAR(45),
    requested_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    previous_status      VARCHAR(30)  NOT NULL,
    revoked_at           TIMESTAMPTZ,
    confirmed_at         TIMESTAMPTZ,
    confirmed_by_user_id BIGINT,

    CONSTRAINT chk_contract_cancellation_status
        CHECK (status IN ('REQUESTED', 'CONFIRMED', 'REVOKED'))
);

CREATE INDEX idx_contract_cancellation_contract
    ON contract_cancellation_requests (contract_id, requested_at DESC);
CREATE INDEX idx_contract_cancellation_requester
    ON contract_cancellation_requests (requester_user_id, requested_at DESC);

-- 한 계약에 활성(REQUESTED) 취소요청 1건만
CREATE UNIQUE INDEX uq_contract_cancellation_active_per_contract
    ON contract_cancellation_requests (contract_id)
    WHERE status = 'REQUESTED';

COMMENT ON TABLE contract_cancellation_requests IS '취소 요청 audit (W7). 부분 WORM. 한 계약 활성 요청 1건만. 보존 3년+';
COMMENT ON COLUMN contract_cancellation_requests.contract_id IS '취소 대상 계약 (ON DELETE RESTRICT)';
COMMENT ON COLUMN contract_cancellation_requests.requester_user_id IS '취소 요청자 user (논리 FK)';
COMMENT ON COLUMN contract_cancellation_requests.status IS 'REQUESTED | CONFIRMED | REVOKED';
COMMENT ON COLUMN contract_cancellation_requests.reason IS '취소 사유 (자유 텍스트 100자)';
COMMENT ON COLUMN contract_cancellation_requests.detail IS '취소 상세 내용 (자유 텍스트)';
COMMENT ON COLUMN contract_cancellation_requests.requester_ip IS '요청 시점 IP (audit)';
COMMENT ON COLUMN contract_cancellation_requests.previous_status IS '요청 시점 contract.status 스냅샷 (SHARED | RECEIVER_SIGNED). revoke 시 복구 대상. immutable';
COMMENT ON COLUMN contract_cancellation_requests.revoked_at IS '요청자 본인이 요청 취소한 시점';
COMMENT ON COLUMN contract_cancellation_requests.confirmed_at IS '상대 측 확정 시점';
COMMENT ON COLUMN contract_cancellation_requests.confirmed_by_user_id IS '확정한 상대 user_id (논리 FK)';
COMMENT ON INDEX uq_contract_cancellation_active_per_contract IS '한 계약에 활성 취소요청 1건만 (CONFIRMED 후 contract 자체 CANCELLED 라 재요청 의미 X)';

-- ============================================================
-- 부분 WORM trigger
-- - immutable: contract_id / requester_user_id / reason / detail / requester_ip / requested_at / previous_status
-- - UPDATE 허용: status / confirmed_at / confirmed_by_user_id
-- - DELETE 차단
-- ============================================================
CREATE OR REPLACE FUNCTION contract_cancellation_requests_worm_protect()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'WORM: contract_cancellation_requests is delete-protected';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF NEW.contract_id IS DISTINCT FROM OLD.contract_id THEN
            RAISE EXCEPTION 'WORM: contract_cancellation_requests.contract_id is immutable';
        END IF;
        IF NEW.requester_user_id IS DISTINCT FROM OLD.requester_user_id THEN
            RAISE EXCEPTION 'WORM: contract_cancellation_requests.requester_user_id is immutable';
        END IF;
        IF NEW.reason IS DISTINCT FROM OLD.reason THEN
            RAISE EXCEPTION 'WORM: contract_cancellation_requests.reason is immutable';
        END IF;
        IF NEW.detail IS DISTINCT FROM OLD.detail THEN
            RAISE EXCEPTION 'WORM: contract_cancellation_requests.detail is immutable';
        END IF;
        IF NEW.requester_ip IS DISTINCT FROM OLD.requester_ip THEN
            RAISE EXCEPTION 'WORM: contract_cancellation_requests.requester_ip is immutable';
        END IF;
        IF NEW.requested_at IS DISTINCT FROM OLD.requested_at THEN
            RAISE EXCEPTION 'WORM: contract_cancellation_requests.requested_at is immutable';
        END IF;
        IF NEW.previous_status IS DISTINCT FROM OLD.previous_status THEN
            RAISE EXCEPTION 'WORM: contract_cancellation_requests.previous_status is immutable';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION contract_cancellation_requests_worm_protect() IS '부분 WORM — requester_* / contract_id / requested_at / previous_status immutable + DELETE 차단. status / confirmed_* / revoked_at 는 UPDATE 가능';

CREATE TRIGGER trg_contract_cancellation_requests_worm
    BEFORE UPDATE OR DELETE
    ON contract_cancellation_requests
    FOR EACH ROW
EXECUTE FUNCTION contract_cancellation_requests_worm_protect();
