package com.trana.identity.adapter

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.time.format.DateTimeFormatter

/**
 * NCP CLOVA eKYC Verify API 어댑터.
 *
 * - 행안부/경찰청 진위확인
 * - Document API와 같은 invoke URL/Secret 사용 (`trana.kyc.ncp.id-card`)
 * - idType별 path + body 4분기
 */
@Component
class NcpIdCardVerifyAdapter(
    private val props: NcpEkycProperties,
) : IdCardVerifyAdapter {
    private val client: RestClient =
        RestClient
            .builder()
            .baseUrl(props.idCard.invokeUrl)
            .defaultHeader(EKYC_VERIFY_SECRET_HEADER, props.idCard.secretKey)
            .build()

    override fun verify(input: IdCardVerifyInput): IdCardVerifyResult {
        val path = pathFor(input.idType)
        val body = buildBody(input)

        val response =
            client
                .post()
                .uri("/verify/$path")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body<NcpIdCardVerifyResponse>()
                ?: error("NCP Verify API 응답이 비어 있음")

        return IdCardVerifyResult(
            isValid = response.result == "SUCCESS",
            errorCode = response.code,
            errorMessage = response.message,
        )
    }

    private fun pathFor(idType: IdType): String =
        when (idType) {
            IdType.ID_CARD -> "ic"
            IdType.DRIVER_LICENSE -> "dl"
            IdType.PASSPORT -> "pp"
            IdType.ALIEN_REGISTRATION -> "ac"
        }

    private fun buildBody(input: IdCardVerifyInput): Map<String, Any?> {
        val data =
            when (input.idType) {
                IdType.ID_CARD -> buildIcData(input)
                IdType.DRIVER_LICENSE -> buildDlData(input)
                IdType.PASSPORT -> buildPpData(input)
                IdType.ALIEN_REGISTRATION -> buildAcData(input)
            }
        return mapOf(
            "requestId" to input.requestId,
            "data" to listOf(data),
        )
    }

    private fun buildIcData(input: IdCardVerifyInput): Map<String, Any?> =
        mapOf(
            "name" to input.name,
            "personalNum" to requireNotNull(input.personalNum) { "ic Verify: personalNum 필수" },
            "issueDate" to requireNotNull(input.issueDate?.format(DATE_FORMAT)) { "ic Verify: issueDate 필수" },
        )

    private fun buildDlData(input: IdCardVerifyInput): Map<String, Any?> {
        val base =
            mutableMapOf<String, Any?>(
                "name" to input.name,
                "personalNum" to requireNotNull(input.personalNum) { "dl Verify: personalNum 필수" },
                "num" to requireNotNull(input.licenseNum) { "dl Verify: 면허번호 필수" },
            )
        if (input.licenseCode != null) {
            base["code"] = input.licenseCode
        } else {
            base["skipCodeCheck"] = true
        }
        return base
    }

    private fun buildPpData(input: IdCardVerifyInput): Map<String, Any?> =
        mapOf(
            "fullNameKor" to input.name,
            "num" to requireNotNull(input.passportNum) { "pp Verify: 여권번호 필수" },
            "birthDate" to requireNotNull(input.birthDate?.format(DATE_FORMAT)) { "pp Verify: 생년월일 필수" },
            "issueDate" to requireNotNull(input.issueDate?.format(DATE_FORMAT)) { "pp Verify: 발급일 필수" },
            "expireDate" to requireNotNull(input.expireDate?.format(DATE_FORMAT)) { "pp Verify: 만료일 필수" },
        )

    private fun buildAcData(input: IdCardVerifyInput): Map<String, Any?> =
        mapOf(
            "alienRegNum" to requireNotNull(input.alienRegNum) { "ac Verify: 외국인등록번호 필수" },
            "issueDate" to requireNotNull(input.issueDate?.format(DATE_FORMAT)) { "ac Verify: 발급일 필수" },
            "serialNum" to requireNotNull(input.serialNum) { "ac Verify: serialNum 필수" },
        )
}

private const val EKYC_VERIFY_SECRET_HEADER = "X-EKYC-SECRET"
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
