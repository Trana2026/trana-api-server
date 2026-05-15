package com.trana.auth.oauth

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

/**
 * 소셜 공급자별 id_token 검증용 JwtDecoder 빈.
 *
 * - withIssuerLocation: OIDC discovery로 jwks_uri 자동 발견 + 키 캐싱
 * - 검증: iss (issuer) + exp + aud (client-id)
 *
 * Spring Security 7 (Spring Boot 4) 호환.
 */
@Configuration
class OidcDecoderConfig(
    private val kakaoProps: KakaoOidcProperties,
    private val googleProps: GoogleOidcProperties,
) {
    @Bean("kakaoIdTokenDecoder")
    fun kakaoIdTokenDecoder(): JwtDecoder = buildDecoder(kakaoProps.issuer, kakaoProps.clientId)

    @Bean("googleIdTokenDecoder")
    fun googleIdTokenDecoder(): JwtDecoder = buildDecoder(googleProps.issuer, googleProps.clientId)

    private fun buildDecoder(
        issuer: String,
        clientId: String,
    ): JwtDecoder {
        val decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(issuer),
                audienceValidator(clientId),
            ),
        )
        return decoder
    }

    private fun audienceValidator(expectedAud: String): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt ->
            if (jwt.audience.contains(expectedAud)) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error("invalid_token", "aud mismatch (expected=$expectedAud, actual=${jwt.audience})", null),
                )
            }
        }
}
