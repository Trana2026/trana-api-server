-- V11: 분쟁 도메인 (W7 Phase A)
-- - dispute_records: 신고 audit (부분 WORM)
-- - 활성 신고 1회 한정 (한 계약 + 한 신고자 + REPORTED partial UNIQUE)
-- - 신고 가능 상태 {SIGNED, COMPLETED} 는 entity 검증
-- - 부분 WORM: contract_id / reporter_user_id / reason / detail / reporter_ip / reported_at immutable + DELETE 차단
-- - contracts.dispute_state CHECK 제약 보강 (NONE / REPORTED 만)

-- ============================================================
-- dispute_records
-- ============================================================
CREATE TABLE dispute_records
(
    id               BIGSERIAL PRIMARY KEY,
    contract_id      BIGINT       NOT NULL REFERENCES contracts (id) ON DELETE RESTRICT,
    reporter_user_id BIGINT       NOT NULL,
    status           VARCHAR(30)  NOT NULL DEFAULT 'REPORTED',
    reason           VARCHAR(100) NOT NULL,
    detail           TEXT         NOT NULL,
    reporter_ip      VARCHAR(45),
    reported_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cancelled_at     TIMESTAMPTZ,

    CONSTRAINT chk_dispute_records_status
        CHECK (status IN ('REPORTED', 'CANCELLED_BY_REPORTER'))
);

CREATE INDEX idx_dispute_records_contract ON dispute_records (contract_id, reported_at DESC);
CREATE INDEX idx_dispute_records_reporter ON dispute_records (reporter_user_id, reported_at DESC);

-- 한 신고자 = 한 계약에 활성(REPORTED) 신고 1건만 (CANCELLED_BY_REPORTER 후 재신고 가능)
CREATE UNIQUE INDEX uq_dispute_records_active_per_contract_reporter
    ON dispute_records (contract_id, reporter_user_id)
    WHERE status = 'REPORTED';

COMMENT ON TABLE dispute_records IS '신고 audit. 부분 WORM (immutable + DELETE 차단). 보존 3년+';
COMMENT ON COLUMN dispute_records.contract_id IS '신고 대상 계약 (ON DELETE RESTRICT — 분쟁 보존)';
COMMENT ON COLUMN dispute_records.reporter_user_id IS '신고자 user (논리 FK — cascade 사고 방지)';
COMMENT ON COLUMN dispute_records.status IS 'REPORTED | CANCELLED_BY_REPORTER';
COMMENT ON COLUMN dispute_records.reason IS '신고 사유 (자유 텍스트 100자 — 한 줄 요약)';
COMMENT ON COLUMN dispute_records.detail IS '신고 상세 내용 (자유 텍스트)';
COMMENT ON COLUMN dispute_records.reporter_ip IS '신고 시점 IP (audit)';
COMMENT ON COLUMN dispute_records.cancelled_at IS '신고자 본인 취소 시점 (status = CANCELLED_BY_REPORTER)';
COMMENT ON INDEX uq_dispute_records_active_per_contract_reporter IS '한 신고자 = 한 계약에 활성 신고 1건만 (취소 후 재신고 가능)';

-- ============================================================
-- 부분 WORM trigger
-- - DELETE 차단
-- - immutable: contract_id / reporter_user_id / reason / detail / reporter_ip / reported_at
-- - UPDATE 허용: status / cancelled_at (신고자 본인 취소 시 entity 가 전이)
-- ============================================================
CREATE OR REPLACE FUNCTION dispute_records_worm_protect()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'WORM: dispute_records is delete-protected';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF NEW.contract_id IS DISTINCT FROM OLD.contract_id THEN
            RAISE EXCEPTION 'WORM: dispute_records.contract_id is immutable';
        END IF;
        IF NEW.reporter_user_id IS DISTINCT FROM OLD.reporter_user_id THEN
            RAISE EXCEPTION 'WORM: dispute_records.reporter_user_id is immutable';
        END IF;
        IF NEW.reason IS DISTINCT FROM OLD.reason THEN
            RAISE EXCEPTION 'WORM: dispute_records.reason is immutable';
        END IF;
        IF NEW.detail IS DISTINCT FROM OLD.detail THEN
            RAISE EXCEPTION 'WORM: dispute_records.detail is immutable';
        END IF;
        IF NEW.reporter_ip IS DISTINCT FROM OLD.reporter_ip THEN
            RAISE EXCEPTION 'WORM: dispute_records.reporter_ip is immutable';
        END IF;
        IF NEW.reported_at IS DISTINCT FROM OLD.reported_at THEN
            RAISE EXCEPTION 'WORM: dispute_records.reported_at is immutable';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION dispute_records_worm_protect() IS '부분 WORM — immutable 컬럼 + DELETE 차단. status / cancelled_at 은 UPDATE 가능 (전이 entity 책임)';

CREATE TRIGGER trg_dispute_records_worm
    BEFORE UPDATE OR DELETE
    ON dispute_records
    FOR EACH ROW
EXECUTE FUNCTION dispute_records_worm_protect();
