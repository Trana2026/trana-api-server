package com.trana.contract.service

import com.trana.contract.entity.ContractSignature
import java.security.MessageDigest

/**
 * 최종 서명본 결합 해시(final_hash) 계산기.
 *
 * SIGNED 전이(양측 서명 완료) 시점에 아래를 정규화(canonical) 문자열로 결합해 SHA-256 → hex 64자:
 * - 계약 publicCode
 * - 최종 PDF v3 SHA-256 (contents.content_hash)
 * - 양측 서명 각각: partyType | 서명 시점 PDF 해시 | 서명 이미지(base64) 해시 | 서명 시각
 *
 * 서명은 partyType 기준 정렬로 결정론적. 계약서 제11조 ③(문서 해시값 생성·보관) 이행.
 * 동일 입력 → 항상 동일 결과이므로 분쟁 시 재계산으로 위변조 검출 가능.
 */
object ContractFinalHasher {
    const val SCHEME = "TRANA-CONTRACT-FINAL-V1"

    fun compute(
        publicCode: String,
        finalPdfSha256: String,
        signatures: List<ContractSignature>,
    ): String {
        val sigLines =
            signatures
                .sortedBy { it.partyType.name }
                .joinToString("\n") { sig ->
                    "sig[${sig.partyType.name}]=" +
                        "${sig.pdfSha256AtSign.orEmpty()}:" +
                        "${sha256Hex(sig.signatureData)}:" +
                        "${sig.signedAt}"
                }
        val canonical =
            buildString {
                append(SCHEME).append('\n')
                append("contract=").append(publicCode).append('\n')
                append("finalPdfSha256=").append(finalPdfSha256).append('\n')
                append(sigLines)
            }
        return sha256Hex(canonical)
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
