package com.trana.contract.adapter.kakao

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.trana.common.util.KstFormatter
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Duration

/**
 * 알리고 카카오 알림톡 실발송 클라이언트.
 *
 * 활성 조건: `alimtalk-live` profile.
 * - 기본 (모든 profile) 은 [MockKakaoAlimtalkClient] 가 동작
 * - 활성화: `SPRING_PROFILES_ACTIVE=...,alimtalk-live`
 * - dev 는 `test-mode: true` (dry-run) 로 1차 검증 후 prod 전환
 *
 * API: `POST https://kakaoapi.aligo.in/akv10/alimtalk/send/`
 * - Content-Type: `application/x-www-form-urlencoded`
 * - 응답 `code != 0` → [AligoSendException]
 *
 * 템플릿 4종 (UI_4032 / UI_4033 / UI_4034 / UI_4037) 사전 등록 + 카카오 심사 완료 전제.
 * 본문 / 치환자 / 버튼이름은 등록 템플릿과 1글자도 어긋나면 발송 거절됨.
 */
@Component
@Profile("alimtalk-live")
@Suppress("TooManyFunctions")
class LiveAligoAlimtalkClient(
    private val aligoProperties: AligoProperties,
    private val objectMapper: ObjectMapper,
) : KakaoAlimtalkClient {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient =
        RestClient
            .builder()
            .baseUrl(ALIGO_BASE_URL)
            .requestFactory(buildRequestFactory())
            .build()

    override fun sendNewContract(message: NewContractMessage) {
        val body =
            """
            안녕하세요. ${message.receiverName}님,
            ${message.sellerName}님으로부터
            안전 거래 계약 서명 요청이 도착했습니다.

            아래 계약 내용을 확인하신 후 서명을 진행해 주세요.

            상품명: ${message.contractTitle}
            거래 금액: ${formatPrice(message.price)}원
            """.trimIndent()

        val formData =
            newFormData().apply {
                add("tpl_code", aligoProperties.tplCode.newContract)
                add("emtitle_1", aligoProperties.tplCode.emtitleNewContract)
                add("receiver_1", normalizePhone(message.receiverPhone))
                add("subject_1", "안전 거래 계약 서명 요청")
                add("message_1", body)
                add("button_1", buildButtonJson("계약서 서명하기", message.invitationAppUrl, message.invitationUrl))
            }

        send(formData, label = "sendNewContract", to = message.receiverPhone)
    }

    override fun sendReceiverSigned(message: ReceiverSignedMessage) {
        val body =
            """
            안녕하세요. ${message.creatorName}님,
            ${message.receiverName}님이
            안전 거래 계약서에 서명을 완료했습니다.

            ${message.creatorName}님의 최종 서명이 완료되면 계약이 효력을 발휘합니다.${" "}

            아래 링크를 통해 서명을 진행해 주세요.

            상품명: ${message.contractTitle}
            거래 금액: ${formatPrice(message.price)}원
            """.trimIndent()

        val formData =
            newFormData().apply {
                add("tpl_code", aligoProperties.tplCode.receiverSigned)
                add("emtitle_1", aligoProperties.tplCode.emtitleReceiverSigned)
                add("receiver_1", normalizePhone(message.creatorPhone))
                add("subject_1", "최종 서명 요청")
                add("message_1", body)
                add("button_1", buildButtonJson("최종 서명하기", message.reviewAppUrl, message.reviewUrl))
            }

        send(formData, label = "sendReceiverSigned", to = message.creatorPhone)
    }

    override fun sendRevisionRequested(message: RevisionRequestedMessage) {
        val body =
            """
            안녕하세요. ${message.creatorName}님,${" "}
            ${message.requesterName}님이 수정 요청을 보냈습니다.

            아래 수정 사유를 확인하신 후,${" "}
            계약 내용을 수정하여 다시 요청해 주세요.

            상품명: ${message.contractTitle}
            거래 금액: ${formatPrice(message.price)}원
            수정 요청 사유: ${message.revisionReason}
            """.trimIndent()

        val formData =
            newFormData().apply {
                add("tpl_code", aligoProperties.tplCode.revisionRequested)
                add("emtitle_1", aligoProperties.tplCode.emtitleRevisionRequested)
                add("receiver_1", normalizePhone(message.creatorPhone))
                add("subject_1", "안전 거래 계약 수정 요청")
                add("message_1", body)
                add("button_1", buildButtonJson("계약 수정하기", message.reviewAppUrl, message.reviewUrl))
            }

        send(formData, label = "sendRevisionRequested", to = message.creatorPhone)
    }

    override fun sendCompleted(message: ContractCompletedMessage) {
        val body =
            """
            안녕하세요, ${message.recipientName}님.
            진행 중이던 안전 거래 계약의 모든 서명이 완료되었습니다.

            체결된 계약서 양식은 아래 링크를 통해${" "}
            언제든지 다시 확인하실 수 있습니다.

            상품명: ${message.contractTitle}
            거래 금액: ${formatPrice(message.price)}원
            계약 체결 일시: ${KstFormatter.DISPLAY.format(message.completedAt)}
            """.trimIndent()

        val formData =
            newFormData().apply {
                add("tpl_code", aligoProperties.tplCode.completed)
                add("emtitle_1", aligoProperties.tplCode.emtitleCompleted)
                add("receiver_1", normalizePhone(message.recipientPhone))
                add("subject_1", "안전 거래 계약 최종 완료")
                add("message_1", body)
                add("button_1", buildButtonJson("계약서 확인하기", message.downloadAppUrl, message.downloadUrl))
            }

        send(formData, label = "sendCompleted", to = message.recipientPhone)
    }

    override fun sendDisputeReported(message: DisputeReportedMessage) {
        val body =
            """
            [Trana] 거래에 대한 신고가 접수되었습니다.

            상품명: ${message.contractTitle}
            접수 일시: ${KstFormatter.DISPLAY.format(message.reportedAt)}

            아래 버튼을 눌러 상세 내용을 확인해 주세요.
            """.trimIndent()

        val formData =
            newFormData().apply {
                add("tpl_code", aligoProperties.tplCode.disputeReported)
                add("receiver_1", normalizePhone(message.recipientPhone))
                add("subject_1", "거래 신고 접수")
                add("message_1", body)
                add("button_1", buildButtonJson("상세 보기", message.detailAppUrl, message.detailUrl))
            }

        send(formData, label = "sendDisputeReported", to = message.recipientPhone)
    }

    override fun sendCancellationRequested(message: CancellationRequestedMessage) {
        val body =
            """
            [Trana] 거래 계약 취소 요청이 도착했습니다.

            상품명: ${message.contractTitle}
            요청 일시: ${KstFormatter.DISPLAY.format(message.requestedAt)}

            아래 버튼을 눌러 취소 내용을 확인해 주세요.
            """.trimIndent()

        val formData =
            newFormData().apply {
                add("tpl_code", aligoProperties.tplCode.cancellationRequested)
                add("receiver_1", normalizePhone(message.recipientPhone))
                add("subject_1", "계약 취소 요청")
                add("message_1", body)
                add("button_1", buildButtonJson("취소 내용 확인", message.detailAppUrl, message.detailUrl))
            }

        send(formData, label = "sendCancellationRequested", to = message.recipientPhone)
    }

    override fun sendCancellationConfirmed(message: CancellationConfirmedMessage) {
        val body =
            """
            [Trana] 거래 계약이 취소되었습니다.

            상품명: ${message.contractTitle}
            취소 일시: ${KstFormatter.DISPLAY.format(message.cancelledAt)}

            상대방이 취소를 확정하여 계약이 최종 취소되었습니다.
            아래 버튼을 눌러 앱에서 확인해 주세요.
            """.trimIndent()

        val formData =
            newFormData().apply {
                add("tpl_code", aligoProperties.tplCode.cancellationConfirmed)
                add("receiver_1", normalizePhone(message.recipientPhone))
                add("subject_1", "계약 취소 완료")
                add("message_1", body)
                add("button_1", buildButtonJson("앱에서 확인", message.homeAppUrl, message.homeUrl))
            }

        send(formData, label = "sendCancellationConfirmed", to = message.recipientPhone)
    }

    override fun sendGuardianContractCompleted(message: GuardianContractCompletedMessage) {
        val body =
            """
            [Trana] 안전 거래 계약이 체결되었습니다.

            자녀 (${message.minorName}) 님이 ${message.counterpartyName} 님과 계약을 체결했습니다.

            상품명: ${message.contractTitle}
            거래 금액: ${formatPrice(message.price)}원

            민법상 미성년자가 당사자인 계약은 취소할 수 있습니다.
            취소를 원하실 경우 아래 버튼으로 접수 방법을 확인해 주세요.
            """.trimIndent()

        val formData =
            newFormData().apply {
                add("tpl_code", aligoProperties.tplCode.guardianContractCompleted)
                add("emtitle_1", aligoProperties.tplCode.emtitleGuardianContractCompleted)
                add("receiver_1", normalizePhone(message.recipientPhone))
                add("subject_1", "계약 체결 통보")
                add("message_1", body)
                add("button_1", buildButtonJson("계약 내용 확인", message.contractDetailAppUrl, message.contractDetailUrl))
            }

        send(formData, label = "sendGuardianContractCompleted", to = message.recipientPhone)
    }

    /** 공통 필드 6개 (apikey/userid/senderkey/sender/testmode/failover) 미리 채운 form data 생성. */
    private fun newFormData(): LinkedMultiValueMap<String, String> =
        LinkedMultiValueMap<String, String>().apply {
            add("apikey", aligoProperties.apiKey)
            add("userid", aligoProperties.userId)
            add("senderkey", aligoProperties.senderKey)
            add("sender", aligoProperties.sender)
            add("testmode", if (aligoProperties.testMode) "Y" else "N")
            add("failover", "N")
        }

    private fun send(
        formData: MultiValueMap<String, String>,
        label: String,
        to: String,
    ) {
        log.info(
            "[ALIGO] {} DIAG tpl={} emtitle=[{}] subject=[{}] message_1=[{}] button_1=[{}]",
            label,
            formData.getFirst("tpl_code"),
            formData.getFirst("emtitle_1"),
            formData.getFirst("subject_1"),
            formData
                .getFirst("message_1")
                ?.replace("\r", "\\r")
                ?.replace("\n", "\\n")
                ?.replace("\u200B", "[ZWS]"),
            formData.getFirst("button_1"),
        )
        val response =
            restClient
                .post()
                .uri(SEND_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body<AligoResponse>()
                ?: throw AligoSendException(label, to, -1, "응답 body null")

        if (response.code != 0) {
            throw AligoSendException(label, to, response.code, response.message)
        }
        log.info(
            "[ALIGO] {} OK — to={}, mid={}, testMode={}",
            label,
            maskPhone(to),
            response.info?.get("mid"),
            aligoProperties.testMode,
        )
    }

    /**
     * Aligo `button_1` JSON — AL(앱링크) 단일 버튼.
     *
     * - linkAnd/linkIos: 앱 스킴(trana://trana.kr/...) → 카카오톡이 네이티브로 앱 실행(인앱브라우저 우회).
     * - linkMo/linkPc: 미설치/PC 폴백 웹 URL(기존 랜딩). 카카오 정책상 AL 도 필수.
     * - 템플릿 버튼도 AL 타입으로 등록·심사되어 있어야 함(deeplink.md 참조).
     */
    private fun buildButtonJson(
        name: String,
        appUrl: String,
        webUrl: String,
    ): String {
        val json =
            mapOf(
                "button" to
                    listOf(
                        mapOf(
                            "name" to name,
                            "linkType" to "AL",
                            "linkTypeName" to "앱링크",
                            "linkAnd" to appUrl,
                            "linkIos" to appUrl,
                            "linkMo" to webUrl,
                            "linkPc" to webUrl,
                        ),
                    ),
            )
        return objectMapper.writeValueAsString(json)
    }

    private fun formatPrice(price: Long): String = "%,d".format(price)

    private fun normalizePhone(phone: String): String = phone.replace("-", "").trim()

    private fun maskPhone(phone: String): String {
        val n = normalizePhone(phone)
        return if (n.length < MIN_PHONE_LENGTH) "****" else n.replaceRange(MASK_START..MASK_END, "****")
    }

    private fun buildRequestFactory(): JdkClientHttpRequestFactory {
        val httpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build()
        return JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
        }
    }

    companion object {
        private const val ALIGO_BASE_URL = "https://kakaoapi.aligo.in"
        private const val SEND_PATH = "/akv10/alimtalk/send/"
        private const val CONNECT_TIMEOUT_SECONDS = 5L
        private const val READ_TIMEOUT_SECONDS = 10L
        private const val MIN_PHONE_LENGTH = 10
        private const val MASK_START = 4
        private const val MASK_END = 7
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AligoResponse(
    val code: Int,
    val message: String,
    val info: Map<String, Any>? = null,
)

class AligoSendException(
    val label: String,
    val to: String,
    val code: Int,
    val responseMessage: String,
) : RuntimeException("알리고 발송 실패 [$label] to=$to code=$code msg=$responseMessage")
