-- V11: 개인정보 제3자 제공 동의(THIRD_PARTY) 약관 추가
-- 온보딩 필수 동의인데 타입/시드 누락으로 서버에 동의 기록이 남지 않던 문제 해결 (plan 1-1)
-- 개인정보보호법 제17조 — 거래 상대방(제3자)에게 본인확인 정보 제공 동의
-- 참고: PASS/Aligo/FCM/AWS 는 제3자 제공이 아니라 처리위탁(제26조) → 개인정보 처리방침에서 고지

-- type 코멘트 갱신 (THIRD_PARTY 포함)
COMMENT ON COLUMN terms_versions.type IS
    'SERVICE | PRIVACY | THIRD_PARTY | MARKETING | LOCATION | CONTRACT_AGREEMENT | ELECTRONIC_SIGNATURE';

-- THIRD_PARTY 시드 1 row
-- content_url / content_hash 는 dev placeholder (V3 시드와 동일 패턴) — 운영 반영 전 실제 본문 확정 필요
INSERT INTO terms_versions (type, version, title, content_url, content_hash, effective_at)
VALUES ('THIRD_PARTY', '1.0', '개인정보 제3자 제공 동의',
        'https://example.com/terms/third-party-1.0.html',
        repeat('g', 64), now());
