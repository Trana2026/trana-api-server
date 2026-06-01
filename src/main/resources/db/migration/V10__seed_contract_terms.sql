-- V10: 계약 도메인 약관 시드 (W6, #43)
-- 계약 약관 (계약/거래 동의) + 전자서명 약관 (서명 행위 동의) 2종
-- contract_consents.term_id 가 가리킬 row — 양측이 각자 서명 직전 동의 (W6 흐름)

INSERT INTO terms_versions (type, version, title, content_url, content_hash, effective_at)
VALUES ('CONTRACT_AGREEMENT', '1.0', 'TRANA 거래 계약 동의',
        'https://example.com/terms/contract-agreement-1.0.html',
        repeat('e', 64), now()),
       ('ELECTRONIC_SIGNATURE', '1.0', 'TRANA 전자서명 동의',
        'https://example.com/terms/electronic-signature-1.0.html',
        repeat('f', 64), now());

COMMENT ON COLUMN terms_versions.type IS 'SERVICE | PRIVACY | MARKETING | LOCATION | CONTRACT_AGREEMENT | ELECTRONIC_SIGNATURE';
