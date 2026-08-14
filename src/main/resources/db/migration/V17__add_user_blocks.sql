-- 사용자 차단(Block) — App Store UGC 심사 대응(학대 사용자 차단 수단).
-- 차단 시 "차단당한 사용자가 생성한 계약" 을 차단한 사람 화면에서 숨긴다(순수 가시성 필터).
-- 단, 양측 서명 완료(SIGNED/COMPLETED) 계약은 예외적으로 항상 노출 → 애플리케이션 필터에서 처리.
-- 데이터 삭제 없음(계약/서명/audit 원본 보존).

CREATE TABLE user_blocks (
    id              BIGSERIAL PRIMARY KEY,
    blocker_user_id BIGINT      NOT NULL, -- 차단한 사람 (논리 FK users)
    blocked_user_id BIGINT      NOT NULL, -- 차단당한 사람 (논리 FK users)
    reason          VARCHAR(50),          -- 선택(추후 신고 연계 대비, null 허용)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_blocks_pair UNIQUE (blocker_user_id, blocked_user_id),
    CONSTRAINT ck_user_blocks_not_self CHECK (blocker_user_id <> blocked_user_id)
);

-- 계약 목록/상세 필터에서 blocker 기준 차단 대상 조회 최적화.
CREATE INDEX idx_user_blocks_blocker ON user_blocks (blocker_user_id);

COMMENT ON TABLE user_blocks IS '사용자 차단 (단방향 blocker→blocked). 차단당한 사용자가 생성한 미서명 계약 숨김';
COMMENT ON COLUMN user_blocks.blocker_user_id IS '차단한 사용자 (논리 FK users)';
COMMENT ON COLUMN user_blocks.blocked_user_id IS '차단당한 사용자 (논리 FK users)';
