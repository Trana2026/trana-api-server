-- V6: 보호자 도메인 (guardians + guardian_links)
-- 미성년자↔보호자 매칭은 identity_verifications.subject_user_id / guardian_id 에서 도출
-- (guardian_relations 테이블 생략 — identity_verifications가 audit + 관계 증거 겸함)

-- =========================================
-- guardians
-- - 보호자 마스터 (identifier_hash UNIQUE)
-- - 한 보호자가 여러 미성년자 인증 시 같은 row 재사용 (upsert)
-- - 보호자 KYC 흐름은 Phase 6에서 identity 도메인이 처리
-- =========================================
CREATE TABLE guardians
(
    id              BIGSERIAL PRIMARY KEY,
    identifier_hash VARCHAR(64)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    birth_date      DATE         NOT NULL,
    gender          VARCHAR(10)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_guardians_identifier_hash ON guardians (identifier_hash);

COMMENT ON TABLE guardians IS '보호자 마스터 (identifier_hash 기준 upsert)';
COMMENT ON COLUMN guardians.identifier_hash IS 'SHA-256 (보호자 주민번호/외국인등록번호)';

-- =========================================
-- guardian_links
-- - 미성년자가 보호자에게 발급하는 일회용 토큰 (jnanoid 21자)
-- - TTL 3일 (expires_at 경과 또는 used_at 채워지면 더 이상 사용 불가)
-- - Phase 6 보호자 KYC SUCCESS 시 markUsed
-- - 만료/사용된 row는 cleanup task가 일정 후 삭제
-- =========================================
CREATE TABLE guardian_links
(
    token       VARCHAR(64) PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    purpose     VARCHAR(30) NOT NULL DEFAULT 'SIGNUP',
    contract_id BIGINT,
    CONSTRAINT chk_guardian_links_purpose_contract
        CHECK (
            (purpose = 'SIGNUP' AND contract_id IS NULL)
                OR (purpose = 'CONTRACT_CONSENT' AND contract_id IS NOT NULL)
            )
);

CREATE INDEX idx_guardian_links_user ON guardian_links (user_id, created_at DESC);
CREATE INDEX idx_guardian_links_expires_unused ON guardian_links (expires_at) WHERE used_at IS NULL;
CREATE INDEX idx_guardian_links_contract
    ON guardian_links (contract_id) WHERE purpose = 'CONTRACT_CONSENT';

COMMENT ON TABLE guardian_links IS '미성년자→보호자 일회용 토큰 (3일 TTL)';
COMMENT ON COLUMN guardian_links.token IS 'jnanoid 21자 (URL 노출용)';
COMMENT ON COLUMN guardian_links.user_id IS '미성년자 user_id (논리 FK — cascade 사고 방지)';
COMMENT ON COLUMN guardian_links.used_at IS '보호자 KYC 완료 시각 (NULL = 미사용)';
COMMENT ON COLUMN guardian_links.purpose IS 'SIGNUP (미성년 가입) | CONTRACT_CONSENT (계약 보호자 동의, W4+)';
COMMENT ON COLUMN guardian_links.contract_id IS 'CONTRACT_CONSENT 일 때만 NOT NULL (CHECK 제약)';
