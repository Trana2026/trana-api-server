-- 코드(shareCode) 공유 시 수신자 번호가 없을 수 있음(번호 미보유 계정) → receiver_phone NOT NULL 해제.
-- 번호 직접입력 방식은 애플리케이션에서 여전히 번호를 필수로 채운다. 알림톡은 번호 없으면 스킵.
ALTER TABLE contract_invitations
    ALTER COLUMN receiver_phone DROP NOT NULL;

COMMENT ON COLUMN contract_invitations.receiver_phone IS '수신자 알림톡 발송 번호. 코드 공유·번호 미보유 계정은 null(알림톡 스킵)';
