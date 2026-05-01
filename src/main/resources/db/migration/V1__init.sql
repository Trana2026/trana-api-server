-- V1: 초기 검증용 마이그레이션

CREATE TABLE init_check
(
    id         BIGSERIAL PRIMARY KEY,
    note       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO init_check (note)
VALUES ('initial migration applied');
