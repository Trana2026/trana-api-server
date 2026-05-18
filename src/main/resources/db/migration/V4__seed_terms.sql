-- V4: 약관 시드 데이터 (개발용)
-- prod 진입 시점에 별도 마이그레이션으로 분리 검토

INSERT INTO terms_versions (type, version, title, content_url, content_hash, effective_at)
VALUES ('SERVICE', '1.0', 'TRANA 서비스 이용약관',
        'https://example.com/terms/service-1.0.html',
        repeat('a', 64), now()),
       ('PRIVACY', '1.0', 'TRANA 개인정보 처리방침',
        'https://example.com/terms/privacy-1.0.html',
        repeat('b', 64), now()),
       ('MARKETING', '1.0', '마케팅 정보 수신 동의 (선택)',
        'https://example.com/terms/marketing-1.0.html',
        repeat('c', 64), now()),
       ('LOCATION', '1.0', '위치정보 이용 동의 (선택)',
        'https://example.com/terms/location-1.0.html',
        repeat('d', 64), now());
