-- V13: contract_consents 사용자별 조회 인덱스 (plan 3-2 AI 동의 마이페이지 노출 A')
-- 기존 인덱스는 (contract_id, user_id) 선두라 user_id 단독 조회에 비효율.
-- 마이페이지 '동의한 약관 목록'이 사용자의 AI 국외이전 동의를 term당 최신 1건 조회하므로 전용 인덱스 추가.
CREATE INDEX idx_contract_consents_user_term
    ON contract_consents (user_id, term_id, consented_at DESC);
