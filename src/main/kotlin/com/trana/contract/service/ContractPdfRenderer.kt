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
 * - Pretendard Bold (700) — 타이틀 / 조항 헤더
 * - subset=true — 실제 사용된 글리프만 임베딩 (용량 절감)
 *
 * 사용처: ContractDraftService.transitionToReady() 가 markReady 직전 호출.
 */
@Component
class ContractPdfRenderer(
    private val templateEngine: TemplateEngine,
) {
    fun render(contract: Contract): ByteArray {
        val context =
            Context(Locale.KOREA).apply {
                setVariable("title", contract.title)
                setVariable("price", contract.price)
                setVariable("conditionSummary", contract.conditionSummary)
                setVariable("conditionDetails", contract.conditionDetails)
                setVariable("deliveryTypeLabel", deliveryLabel(contract.deliveryType))
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

    private fun deliveryLabel(type: DeliveryType): String =
        when (type) {
            DeliveryType.DIRECT -> "직거래"
            DeliveryType.SHIPPING -> "택배"
        }

    companion object {
        private const val TEMPLATE_NAME = "contract-pdf"
        private const val FONT_FAMILY = "Pretendard"
        private const val FONT_MEDIUM_PATH = "/fonts/Pretendard-Medium.ttf"
        private const val FONT_BOLD_PATH = "/fonts/Pretendard-Bold.ttf"
        private const val FONT_WEIGHT_MEDIUM = 500
        private const val FONT_WEIGHT_BOLD = 700
    }
}
