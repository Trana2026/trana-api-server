-- nickname → name 통합 (도메인 정리).
-- 미성년 소셜 가입 시 nickname 에만 채우던 표시명을 name 으로 흡수.
-- 성인 KYC name 과 미성년 소셜 표시명을 단일 name 컬럼으로 통합.
-- 가입 미완 미성년자 (KYC 전이라도) 의 nickname 값도 name 으로 이전.

UPDATE users
SET name = nickname
WHERE name IS NULL
  AND nickname IS NOT NULL;

ALTER TABLE users
    DROP COLUMN nickname;
