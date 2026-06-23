package com.trana.contract.adapter.kakao

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    private val completedAtFormatter: DateTimeFormatter =
        DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Seoul"))

    override fun sendNewContract(message: NewContractMessage) {
        val body =
            """
            안녕하세요. ${message.receiverName}님, ${message.sellerName}님으로부터 안전 거래 계약 서명 요청이 도착했습니다.

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
                add("button_1", buildButtonJson("계약서 서명하기", message.invitationUrl))
            }

        send(formData, label = "sendNewContract", to = message.receiverPhone)
    }

    override fun sendReceiverSigned(message: ReceiverSignedMessage) {
        val body =
            """
            안녕하세요. ${message.creatorName}님, ${message.receiverName}님이 안전 거래 계약서에 서명을 완료했습니다.

            이제 ${message.creatorName}님의 최종 서명이 완료되면 계약이 효력을 발휘합니다. 아래 링크를 통해 최종 서명을 진행해 주세요.

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
                add("button_1", buildButtonJson("최종 서명하러 가기", message.reviewUrl))
            }

        send(formData, label = "sendReceiverSigned", to = message.creatorPhone)
    }

    override fun sendRevisionRequested(message: RevisionRequestedMessage) {
        val body =
            """
            안녕하세요. ${message.creatorName}님, ${message.requesterName}님이 수정 요청을 보냈습니다.

            아래 수정 사유를 확인하신 후, 계약 내용을 수정하여 다시 요청해 주세요.

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
                add("button_1", buildButtonJson("계약 수정하러 가기", message.reviewUrl))
            }

        send(formData, label = "sendRevisionRequested", to = message.creatorPhone)
    }

    override fun sendCompleted(message: ContractCompletedMessage) {
        val body =
            """
            안녕하세요, ${message.recipientName}님.
            진행 중이던 안전 거래 계약의 모든 서명이 완료되었습니다.

            체결된 계약서 양식은 아래 링크를 통해 언제든지 다시 확인하실 수 있습니다.

            상품명: ${message.contractTitle}
            거래 금액: ${formatPrice(message.price)}원
            계약 체결 일시: ${completedAtFormatter.format(message.completedAt)}
            ※ 안전한 거래를 위해 계약 내용을 준수해 주시기 바랍니다. 감사합니다.
            """.trimIndent()

        val formData =
            newFormData().apply {
                add("tpl_code", aligoProperties.tplCode.completed)
                add("emtitle_1", aligoProperties.tplCode.emtitleCompleted)
                add("receiver_1", normalizePhone(message.recipientPhone))
                add("subject_1", "안전 거래 계약 최종 완료")
                add("message_1", body)
                add("button_1", buildButtonJson("최종 계약서 확인하기", message.downloadUrl))
            }

        send(formData, label = "sendCompleted", to = message.recipientPhone)
    }

    override fun sendDisputeReported(message: DisputeReportedMessage) {
        val body =
            """
            [Trana] 거래에 대한 신고가 접수되었습니다.

            상품명: ${message.contractTitle}
            접수 일시: ${completedAtFormatter.format(message.reportedAt)}

            아래 버튼을 눌러 상세 내용을 확인해 주세요.
            """.trimIndent()

        val formData =
            newFormData().apply {
                add("tpl_code", aligoProperties.tplCode.disputeReported)
                add("receiver_1", normalizePhone(message.recipientPhone))
                add("subject_1", "거래 신고 접수")
                add("message_1", body)
                add("button_1", buildButtonJson("상세 보기", message.detailUrl))
            }

        send(formData, label = "sendDisputeReported", to = message.recipientPhone)
    }

    override fun sendCancellationRequested(message: CancellationRequestedMessage) {
        val body =
            """
            [Trana] 거래 계약 취소 요청이 도착했습니다.

            상품명: ${message.contractTitle}
            요청 일시: ${completedAtFormatter.format(message.requestedAt)}

            아래 버튼을 눌러 취소 내용을 확인해 주세요.
            """.trimIndent()

        val formData =
            newFormData().apply {
                add("tpl_code", aligoProperties.tplCode.cancellationRequested)
                add("receiver_1", normalizePhone(message.recipientPhone))
                add("subject_1", "계약 취소 요청")
                add("message_1", body)
                add("button_1", buildButtonJson("취소 내용 확인", message.detailUrl))
            }

        send(formData, label = "sendCancellationRequested", to = message.recipientPhone)
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

    /** Aligo `button_1` JSON — WL(웹링크) 타입 단일 버튼, 모바일/PC URL 동일. */
    private fun buildButtonJson(
        name: String,
        url: String,
    ): String {
        val json =
            mapOf(
                "button" to
                    listOf(
                        mapOf(
                            "name" to name,
                            "linkType" to "WL",
                            "linkTypeName" to "웹링크",
                            "linkMo" to url,
                            "linkPc" to url,
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
