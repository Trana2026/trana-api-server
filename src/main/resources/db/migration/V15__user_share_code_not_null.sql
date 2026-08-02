-- share_code NOT NULL 승격.
-- 신규 가입은 createFromPass 가, 기존 유저는 ShareCodeBackfillRunner 가 채우지만,
-- 마이그레이션 시점 누락분을 방어적으로 백필한 뒤 제약을 추가한다.
-- 알파벳/길이는 TokenGenerator.generateShareCode 와 동일(혼동문자 0/O/1/I 제외 32자, 5자).
DO $$
DECLARE
    alphabet CONSTANT text := '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
    r        RECORD;
    code     text;
    i        int;
BEGIN
    FOR r IN SELECT id FROM users WHERE share_code IS NULL LOOP
        LOOP
            code := '';
            FOR i IN 1..5 LOOP
                code := code || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
            END LOOP;
            EXIT WHEN NOT EXISTS (SELECT 1 FROM users WHERE share_code = code);
        END LOOP;
        UPDATE users SET share_code = code WHERE id = r.id;
    END LOOP;
END $$;

ALTER TABLE users ALTER COLUMN share_code SET NOT NULL;
