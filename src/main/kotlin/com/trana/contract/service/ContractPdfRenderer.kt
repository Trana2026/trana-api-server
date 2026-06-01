package com.trana.contract.service

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.trana.contract.entity.Contract
import com.trana.contract.entity.DeliveryType
import org.springframework.stereotype.Component
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.util.Locale

/**
 * 계약서 PDF 렌더러.
 *
 * 흐름:
 * 1. Thymeleaf 로 contract-pdf.html 템플릿 + 변수 → XHTML
 * 2. openhtmltopdf 로 XHTML + 임베딩 폰트 → PDF byte[]
 *
 * 폰트:
 * - Pretendard Medium (500) — 본문
 * - Pretendard Bold (700) — 타이틀 / 조항 헤더 / 본문 강조
 * - subset=true — 실제 사용된 글리프만 임베딩 (용량 절감)
 *
 * 사용처: ContractDraftService.transitionToReady() 가 markReady 직전 호출.
 */
@Component
class ContractPdfRenderer(
    private val templateEngine: TemplateEngine,
) {
    fun render(input: ContractPdfRenderInput): ByteArray {
        val contract = input.contract
        val deliveryType = contract.deliveryType
        val context =
            Context(Locale.KOREA).apply {
                setVariable("title", contract.title ?: PLACEHOLDER)
                setVariable(
                    "priceFormatted",
                    contract.price?.let { String.format(Locale.KOREA, "%,d", it) } ?: PLACEHOLDER,
                )
                setVariable("conditionSummary", contract.conditionSummary ?: PLACEHOLDER)
                setVariable("conditionDetails", contract.conditionDetails ?: PLACEHOLDER)
                setVariable("shippingMark", if (deliveryType == DeliveryType.SHIPPING) CHECK_MARK else EMPTY_MARK)
                setVariable("directMark", if (deliveryType == DeliveryType.DIRECT) CHECK_MARK else EMPTY_MARK)
                setVariable("warrantyPeriodDays", contract.warrantyPeriodDays)
                setPartyVariables("seller", input.seller)
                setPartyVariables("buyer", input.buyer)
            }
        val html = templateEngine.process(TEMPLATE_NAME, context)

        val output = ByteArrayOutputStream()
        PdfRendererBuilder()
            .useFastMode()
            .withHtmlContent(html, null)
            .useFont(
                ::pretendardMediumStream,
                FONT_FAMILY,
                FONT_WEIGHT_MEDIUM,
                BaseRendererBuilder.FontStyle.NORMAL,
                true,
            ).useFont(
                ::pretendardBoldStream,
                FONT_FAMILY,
                FONT_WEIGHT_BOLD,
                BaseRendererBuilder.FontStyle.NORMAL,
                true,
            ).toStream(output)
            .run()

        return output.toByteArray()
    }

    private fun pretendardMediumStream(): InputStream =
        requireNotNull(javaClass.getResourceAsStream(FONT_MEDIUM_PATH)) {
            "Pretendard-Medium.ttf 폰트 리소스 누락 — $FONT_MEDIUM_PATH"
        }

    private fun pretendardBoldStream(): InputStream =
        requireNotNull(javaClass.getResourceAsStream(FONT_BOLD_PATH)) {
            "Pretendard-Bold.ttf 폰트 리소스 누락 — $FONT_BOLD_PATH"
        }

    private fun Context.setPartyVariables(
        prefix: String,
        party: PartyRenderInfo?,
    ) {
        setVariable("${prefix}Name", party?.name ?: PLACEHOLDER)
        setVariable("${prefix}BirthDate", party?.birthDate?.toString() ?: PLACEHOLDER)
        setVariable("${prefix}Phone", party?.phone ?: PLACEHOLDER)
        setVariable("${prefix}SignatureBase64", party?.signatureBase64)
    }

    companion object {
        private const val TEMPLATE_NAME = "contract-pdf"
        private const val FONT_FAMILY = "Pretendard"
        private const val FONT_MEDIUM_PATH = "/fonts/Pretendard-Medium.ttf"
        private const val FONT_BOLD_PATH = "/fonts/Pretendard-Bold.ttf"
        private const val FONT_WEIGHT_MEDIUM = 500
        private const val FONT_WEIGHT_BOLD = 700
        private val PLACEHOLDER = " ".repeat(20)
        private const val CHECK_MARK = "[✓]"
        private const val EMPTY_MARK = "[ ]"
    }
}

data class ContractPdfRenderInput(
    val contract: Contract,
    val seller: PartyRenderInfo? = null,
    val buyer: PartyRenderInfo? = null,
)

data class PartyRenderInfo(
    val name: String,
    val birthDate: LocalDate,
    val phone: String,
    val signatureBase64: String? = null,
)
