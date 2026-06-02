-- ============================================================
-- V11: ContractSignature pdf_sha256_at_sign 추가
-- ============================================================
-- 서명 시점 PDF hash 보존 — 분쟁 시 "서명한 그 시점의 PDF hash" 증명
-- 현재 pdf_version_at_sign 만 있어 같은 version 의 PDF 내용이 바뀌면 audit 불일치 가능
-- Refactor audit (e): CLAUDE.md 보류 항목

ALTER TABLE contract_signatures
    ADD COLUMN pdf_sha256_at_sign VARCHAR(64);

COMMENT ON COLUMN contract_signatures.pdf_sha256_at_sign IS '서명 시점 PDF sha256 hash 스냅샷 — 분쟁 시 서명한 그 PDF 증명. W6 refactor (e). 기존 row 는 NULL (legacy)';
