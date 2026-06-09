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
 * - withIssuerLocation: OIDC discovery 로 jwks_uri 자동 발견 + 키 캐싱
 * - 검증: iss (issuer) + exp + aud (client-id, Apple 은 multi-audience)
 *
 * Apple multi-audience: iOS native (Bundle ID) + Android/Web (Services ID) 둘 다 허용.
 *
 * Spring Security 7 (Spring Boot 4) 호환.
 */
@Configuration
class OidcDecoderConfig(
    private val kakaoProps: KakaoOidcProperties,
    private val googleProps: GoogleOidcProperties,
    private val appleProps: AppleOidcProperties,
) {
    @Bean("kakaoIdTokenDecoder")
    fun kakaoIdTokenDecoder(): JwtDecoder = buildDecoder(kakaoProps.issuer, listOf(kakaoProps.clientId))

    @Bean("googleIdTokenDecoder")
    fun googleIdTokenDecoder(): JwtDecoder = buildDecoder(googleProps.issuer, listOf(googleProps.clientId))

    @Bean("appleIdTokenDecoder")
    fun appleIdTokenDecoder(): JwtDecoder = buildDecoder(appleProps.issuer, appleProps.audiences)

    private fun buildDecoder(
        issuer: String,
        audiences: Collection<String>,
    ): JwtDecoder {
        val decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(issuer),
                audienceValidator(audiences),
            ),
        )
        return decoder
    }

    private fun audienceValidator(expectedAudiences: Collection<String>): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt ->
            if (jwt.audience.any { it in expectedAudiences }) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error(
                        "invalid_token",
                        "aud mismatch (expected one of $expectedAudiences, actual=${jwt.audience})",
                        null,
                    ),
                )
            }
        }
}
