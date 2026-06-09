package com.trana.auth.oauth

import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * Apple Sign In client_secret JWT (ES256) 생성.
 *
 * Apple OAuth code → token 교환 (https://appleid.apple.com/auth/token) 시
 * client_secret 파라미터로 송신할 JWT.
 *
 * 사양 (Apple 공식):
 * - alg: ES256, kid: Key ID
 * - iss: Team ID
 * - iat: now
 * - exp: now + 최대 6개월 (Apple 강제)
 * - aud: https://appleid.apple.com
 * - sub: Services ID (= 우리 oauth.apple.client-id)
 *
 * 매 호출마다 새로 생성 (TTL 5분 — 호출 직전 발급/사용/폐기).
 * Apple 정책상 최대 6개월 가능하나 짧게 가서 cron 갱신 불필요 + 키 노출 risk 최소화.
 */
@Component
class AppleClientSecretGenerator(
    private val props: AppleOidcProperties,
) {
    private val privateKey: ECPrivateKey by lazy { parsePrivateKey(props.privateKey) }

    fun generate(ttl: Duration = DEFAULT_TTL): String {
        val now = Instant.now()
        return Jwts
            .builder()
            .header()
            .keyId(props.keyId)
            .and()
            .issuer(props.teamId)
            .audience()
            .add(APPLE_ISSUER)
            .and()
            .subject(props.clientId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(privateKey, Jwts.SIG.ES256)
            .compact()
    }

    private fun parsePrivateKey(pem: String): ECPrivateKey {
        val pkcs8 =
            pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
        val decoded = Base64.getDecoder().decode(pkcs8)
        val keySpec = PKCS8EncodedKeySpec(decoded)
        val factory = KeyFactory.getInstance("EC")
        return factory.generatePrivate(keySpec) as ECPrivateKey
    }

    companion object {
        private const val APPLE_ISSUER = "https://appleid.apple.com"
        private val DEFAULT_TTL: Duration = Duration.ofMinutes(5)
    }
}
