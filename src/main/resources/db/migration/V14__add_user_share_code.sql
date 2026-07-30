-- 유저 고유코드(share_code) — 계약 요청 식별자.
-- 영대문자+숫자 5자(혼동문자 0/O/1/I 제외), PASS 가입 시 발급, 불변.
-- 판매자가 채팅으로 코드 전달 → 구매자가 코드로 서명요청 → 상대 번호로 알림톡.
--
-- nullable 로 추가 → 기존 유저는 ApplicationRunner(ShareCodeBackfillRunner)가 부팅 시 백필.
-- 전부 채워진 것 확인 후 별도 마이그레이션에서 NOT NULL 승격 예정.
ALTER TABLE users
    ADD COLUMN share_code VARCHAR(8) UNIQUE;

COMMENT ON COLUMN users.share_code IS '계약 요청용 고유코드 (대문자+숫자 5자, 혼동문자 제외). 탈퇴 시 null 방출(재사용).';
