package com.trana.common.crypto

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.encrypt.BytesEncryptor
import org.springframework.security.crypto.encrypt.Encryptors
import org.springframework.security.crypto.encrypt.TextEncryptor

/**
 * 암호화 Bean 등록 — AES-256-GCM (PBKDF2 키 유도, 랜덤 IV).
 *
 * - bytesEncryptor: byte[] → byte[]
 * - textEncryptor: String → String (hex 인코딩)
 *
 * 키 유도는 Bean 생성 시 1회 — Bean을 싱글톤으로 주입받아 재사용할 것.
 */
@Configuration
class CryptoConfig {
    @Bean
    fun bytesEncryptor(props: CryptoProperties): BytesEncryptor = Encryptors.stronger(props.password, props.salt)

    @Bean
    fun textEncryptor(props: CryptoProperties): TextEncryptor = Encryptors.delux(props.password, props.salt)
}
