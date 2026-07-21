-- V12: 실제 약관 원문 반영 + 타입 확장 (plan 2)
-- - md 원문은 백엔드 리소스(src/main/resources/terms/*.md)에 저장, content_hash = 해당 파일 SHA-256
-- - content_url = Vercel 약관 상세 페이지(앱 webview 대상). 웹 라우팅 확정 시 조정 가능(현재 trana.kr/terms/{slug})
-- - PRIVACY 의미 정정(처리방침→수집·이용 동의) + PRIVACY_POLICY(처리방침, 열람전용) 신설
-- - AI 2종 / 보호자 3종(문서없는 GUARDIAN_LEGAL_REP 포함) 신설

COMMENT ON COLUMN terms_versions.type IS
    'SERVICE | PRIVACY | PRIVACY_POLICY | THIRD_PARTY | MARKETING | LOCATION | CONTRACT_AGREEMENT | ELECTRONIC_SIGNATURE | AI_AUTOFILL_NOTICE | AI_CROSS_BORDER | GUARDIAN_WARRANTY | GUARDIAN_PRIVACY | GUARDIAN_LEGAL_REP';

-- ── 기존 행: 실제 콘텐츠(제목/URL/해시)로 갱신 ──
UPDATE terms_versions SET title = 'Trana 서비스 이용약관',
    content_url = 'https://trana.kr/terms/service',
    content_hash = '2d1f7d695e8ebe22da0bf10830448d26d94cbbf500d80c3a82c3de6802cd6a1d'
  WHERE type = 'SERVICE' AND version = '1.0';

-- PRIVACY: 처리방침 → 수집·이용 동의 로 의미 정정
UPDATE terms_versions SET title = '개인정보 수집·이용 동의서',
    content_url = 'https://trana.kr/terms/privacy',
    content_hash = 'd45b6b420d79b4c13955e81f640925f85eeea62eb66cb73b5b5a64d588ea5d51'
  WHERE type = 'PRIVACY' AND version = '1.0';

UPDATE terms_versions SET title = '마케팅 정보 수신 동의서',
    content_url = 'https://trana.kr/terms/marketing',
    content_hash = 'd2f3dbf5cba2b1917bd2fa38581bcfccfdf1023290d9b91d0d97791667037010'
  WHERE type = 'MARKETING' AND version = '1.0';

UPDATE terms_versions SET title = '개인정보 제3자 제공 동의서',
    content_url = 'https://trana.kr/terms/third_party',
    content_hash = '22e0171240528c82dafdf5b7a9dc80603ed15fe76e9ac5e5a610c5ede4abb8aa'
  WHERE type = 'THIRD_PARTY' AND version = '1.0';

-- ── 신규 타입 행 INSERT ──
INSERT INTO terms_versions (type, version, title, content_url, content_hash, effective_at)
VALUES
  ('PRIVACY_POLICY', '1.0', 'Trana 개인정보 처리방침',
   'https://trana.kr/terms/privacy_policy',
   '4a09595224f80e7331d77edbd75ff37620ca151d017273021f688345fd8c135d', now()),
  ('AI_AUTOFILL_NOTICE', '1.0', 'AI 자동 기입(Auto-Fill) 기능 면책 고지문',
   'https://trana.kr/terms/ai_autofill_notice',
   '6697f7ecf128d6c006a1549992e7dbbcdd434431ec3e4fa3c81038433a3e3b26', now()),
  ('AI_CROSS_BORDER', '1.0', 'AI 자동 기입 기능 국외이전 동의서',
   'https://trana.kr/terms/ai_cross_border',
   'c59c371d5ea76cabcde60abe3daf141d364447979d3a6d5bc86dc30a98da61a7', now()),
  ('GUARDIAN_WARRANTY', '1.0', '본인확인 및 친권관계 보증 약관',
   'https://trana.kr/terms/guardian_warranty',
   '00a7d4891998a57bc7e4ddbb0cc12fde745ab88b05d50ffb4a1f5312f77ad80c', now()),
  ('GUARDIAN_PRIVACY', '1.0', '개인정보 수집·이용 동의서 (보호자용)',
   'https://trana.kr/terms/guardian_privacy',
   '8c2e00d2c4cc8a63d098a98fdfcc3aa48bbe6ab5a0bb3bdfcd5ea481de68b587', now()),
  ('GUARDIAN_LEGAL_REP', '1.0', '법정대리인 확인',
   'https://trana.kr/terms/guardian_legal_rep',
   '838c767cd5b4f598e9d4bab7e4fb16da7ed2fc595508d62dbf517dec77b5e3d6', now());
