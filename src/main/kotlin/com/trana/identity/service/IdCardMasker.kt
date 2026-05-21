package com.trana.identity.service

import com.trana.identity.adapter.MaskPolygon
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * NCP maskingPolys 좌표로 신분증 사진을 검은 사각형으로 마스킹.
 *
 * - 입력: 원본 이미지(JPG/PNG)
 * - 출력: 마스킹된 PNG (마스킹 영역 보존을 위해 무손실)
 * - 좌표는 NCP 응답 픽셀 단위 그대로 사용 (별도 스케일링 X)
 */
@Component
class IdCardMasker {
    fun apply(
        originalBytes: ByteArray,
        polygons: List<MaskPolygon>,
    ): ByteArray {
        val source =
            ImageIO.read(ByteArrayInputStream(originalBytes))
                ?: error("이미지 디코딩 실패")

        val canvas = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        val graphics = canvas.createGraphics()
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON,
            )
            graphics.drawImage(source, 0, 0, null)
            graphics.color = Color.BLACK
            polygons.forEach { poly ->
                val awtPoly = Polygon()
                poly.vertices.forEach { v -> awtPoly.addPoint(v.x.toInt(), v.y.toInt()) }
                graphics.fillPolygon(awtPoly)
            }
        } finally {
            graphics.dispose()
        }

        return ByteArrayOutputStream().use { output ->
            ImageIO.write(canvas, "PNG", output)
            output.toByteArray()
        }
    }
}
