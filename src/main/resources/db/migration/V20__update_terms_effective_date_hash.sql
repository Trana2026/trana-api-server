-- 약관 최하단 시행일([서비스 배포일] → 2026년 8월 28일) 반영에 따른 content_hash 갱신.
-- content_hash = 각 약관 md 파일(src/main/resources/terms/*.md)의 SHA-256.
-- 본문 변경으로 해시가 바뀌므로 terms_versions.content_hash 를 새 값으로 갱신한다.
-- (md 파일이 없는 CONTRACT_AGREEMENT/ELECTRONIC_SIGNATURE 는 대상 아님.)

UPDATE terms_versions SET content_hash = '0985e5f26fed864eeca70ac2c78015e0c9ed66e0c5b3fc5c11e2469363479396' WHERE type = 'SERVICE'            AND version = '1.0';
UPDATE terms_versions SET content_hash = 'd638b427dc45d73b35ba00af8a3501d47c293287a26c25ae8611670874c79897' WHERE type = 'PRIVACY'            AND version = '1.0';
UPDATE terms_versions SET content_hash = 'c672306b4aace3549c031462412f1c8e384768907408aaf3b405d56862cdf43b' WHERE type = 'MARKETING'          AND version = '1.0';
UPDATE terms_versions SET content_hash = '25d4cb75692f7efa18c5ddc845211f62508969cca61e2384916c305ac154ee60' WHERE type = 'THIRD_PARTY'        AND version = '1.0';
UPDATE terms_versions SET content_hash = '0df20d706dd4057e120e04faf14dab86e9b306d63342716eb3baea1148f9327e' WHERE type = 'PRIVACY_POLICY'     AND version = '1.0';
UPDATE terms_versions SET content_hash = 'e8dc43dbe42814fb81df9b9692616f55614794cd8fbe4309a88696e534259eca' WHERE type = 'AI_AUTOFILL_NOTICE' AND version = '1.0';
UPDATE terms_versions SET content_hash = '609465b80e1d022e7e16efe37fbbabdb669ecefce3b624556879879daca71099' WHERE type = 'AI_CROSS_BORDER'    AND version = '1.0';
UPDATE terms_versions SET content_hash = '160853c5b6506e5518419c3a5cddf408482f4721690bb44462c3965c6ca1bae9' WHERE type = 'GUARDIAN_WARRANTY'  AND version = '1.0';
UPDATE terms_versions SET content_hash = '8734990e777fcb5c80b414fb0402794861d402b4f0d51c82480c815511c509ee' WHERE type = 'GUARDIAN_PRIVACY'   AND version = '1.0';
