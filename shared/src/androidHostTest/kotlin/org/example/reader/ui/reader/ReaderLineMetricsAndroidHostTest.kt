package org.example.reader.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import org.example.reader.core.settings.ReaderSettings

class ReaderLineMetricsAndroidHostTest {

    @Test
    fun defaultMetricsPreserveLegacyValues() {
        val metrics = ReaderLineMetrics.from(ReaderSettings())

        assertEquals(18.sp, metrics.fontSize)
        assertEquals(32.4f, metrics.lineHeight.value, absoluteTolerance = 0.0001f)
        assertEquals(3.6f, metrics.paragraphSpacing.value, absoluteTolerance = 0.0001f)
        assertEquals(36.sp, metrics.firstLineIndent)
    }

    @Test
    fun minimumAndMaximumSettingsUseOneFormula() {
        val minimum = ReaderLineMetrics.from(
            ReaderSettings(fontSize = 12, lineHeight = 1.2f, paragraphSpacing = 0f),
        )
        val maximum = ReaderLineMetrics.from(
            ReaderSettings(fontSize = 32, lineHeight = 2.6f, paragraphSpacing = 1.2f),
        )

        assertEquals(12.sp, minimum.fontSize)
        assertEquals(14.4f, minimum.lineHeight.value, absoluteTolerance = 0.0001f)
        assertEquals(0.sp, minimum.paragraphSpacing)
        assertEquals(24.sp, minimum.firstLineIndent)
        assertEquals(32.sp, maximum.fontSize)
        assertEquals(83.2f, maximum.lineHeight.value, absoluteTolerance = 0.0001f)
        assertEquals(38.4f, maximum.paragraphSpacing.value, absoluteTolerance = 0.0001f)
        assertEquals(64.sp, maximum.firstLineIndent)
    }

    @Test
    fun bodyStyleAndGapConversionsPreserveLegacySemantics() {
        val metrics = ReaderLineMetrics.from(ReaderSettings())
        val style = metrics.bodyStyle(Color.Magenta)
        val density = Density(density = 1.5f, fontScale = 1.25f)

        assertEquals(metrics.fontSize, style.fontSize)
        assertEquals(metrics.lineHeight, style.lineHeight)
        assertEquals(Color.Magenta, style.color)
        assertEquals(metrics.firstLineIndent, style.textIndent?.firstLine)
        assertEquals(4.5.dp, metrics.paragraphGapDp(density))
        // 3.6sp × 1.5 × 1.25 = 6.75px，必须沿用 toInt 截断为 6。
        assertEquals(6, metrics.paragraphGapPx(density))
    }

    @Test
    fun firstLineIndentAlwaysTracksTwoFontSizes() {
        listOf(12, 18, 27, 32).forEach { size ->
            val metrics = ReaderLineMetrics.from(ReaderSettings(fontSize = size))
            assertEquals((size * 2).sp, metrics.firstLineIndent)
        }
    }
}
