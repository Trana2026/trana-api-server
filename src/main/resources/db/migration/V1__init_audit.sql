-- V1: 감사 로그 (WORM, cross-cutting 인프라)

-- =========================================
-- worm_protect: WORM 테이블 UPDATE/DELETE 차단 함수
-- audit_logs / signatures / 기타 이력성 테이블에 적용
-- =========================================
CREATE OR REPLACE FUNCTION worm_protect()
      RETURNS TRIGGER AS
  $$
BEGIN
      RAISE EXCEPTION 'WORM table: % is immutable (operation=%)', TG_TABLE_NAME, TG_OP;
END;
  $$ LANGUAGE plpgsql;

  COMMENT ON FUNCTION worm_protect() IS 'WORM 트리거 함수 — UPDATE/DELETE 시 예외 발생';

  -- =========================================
  -- audit_logs (WORM, 법적 증거)
  -- =========================================
CREATE TABLE audit_logs
(
    id            BIGSERIAL PRIMARY KEY,
    event_type    VARCHAR(50) NOT NULL,
    actor_user_id BIGINT,
    entity_type   VARCHAR(50),
    entity_id     BIGINT,
    metadata      JSONB,
    ip            INET,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_user_id, created_at DESC);
CREATE INDEX idx_audit_logs_event ON audit_logs (event_type, created_at DESC);

COMMENT ON TABLE audit_logs IS 'WORM. UPDATE/DELETE 금지 (트리거). 파티셔닝은 W7~';
  COMMENT ON COLUMN audit_logs.actor_user_id IS '논리 FK only (cascade 사고 방지로 FK 미설정)';
  COMMENT ON COLUMN audit_logs.metadata IS '부가 정보 (JSONB)';

CREATE TRIGGER trg_audit_logs_worm
    BEFORE UPDATE OR DELETE
ON audit_logs
      FOR EACH ROW
  EXECUTE FUNCTION worm_protect();
