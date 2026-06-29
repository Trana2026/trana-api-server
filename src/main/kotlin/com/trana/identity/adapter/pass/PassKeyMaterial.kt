package com.trana.identity.adapter.pass

import java.security.PrivateKey
import java.security.PublicKey

/**
 * mok_keyInfo.dat 복호화 결과. PassKeyInfoLoader 가 lazy 로 1회 복호화 후 메모리 캐시.
 *
 * - serviceId: 이용기관 서비스 ID (MOKReqClientInfo.serviceId 필드)
 * - clientPrivateKey: encryptMOKResult 의 RSA 부분 복호화 (PKCS#8 → RSA Private)
 * - serverPublicKey: encryptReqClientInfo 의 RSA 암호화 (X.509 → RSA Public)
 */
data class PassKeyMaterial(
    val serviceId: String,
    val clientPrivateKey: PrivateKey,
    val serverPublicKey: PublicKey,
)
