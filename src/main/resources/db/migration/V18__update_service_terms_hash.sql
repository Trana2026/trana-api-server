-- 서비스 이용약관(SERVICE) 1.0 본문 개정 — 제21조의2(이용자 생성 콘텐츠 및
-- 부적절한 콘텐츠·행위에 대한 무관용 원칙) 신설. App Store UGC 심사 대응(신고·차단·무관용 정책 명문화).
-- 출시 전 개정이라 버전(1.0)은 유지하고 content_hash(파일 SHA-256)만 갱신한다.
-- 신규 해시 = SHA-256(src/main/resources/terms/service_1.0.md).
UPDATE terms_versions
   SET content_hash = 'f3e7b60fe96e3c1afcdb1b71051b74737ca1600915c5ee0fa27d22615594f28f'
 WHERE type = 'SERVICE' AND version = '1.0';
