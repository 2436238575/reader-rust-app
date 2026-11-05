package org.example.reader.core.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.example.reader.core.settings.ReaderFont

/**
 * 字重 → 文件映射。
 *
 * 这些断言同时是**仓库契约**：文件名改了会让已缓存用户 404，必须在同一提交里
 * 同步 `fonts/`、本测试与 AndroidFontStore 的基址。
 */
class FontWeightFileAndroidHostTest {

    @Test
    fun harmonyMapsConfiguredWeightsToNearestFile() {
        assertEquals("HarmonyOS_Sans_SC_Thin.ttf", file(ReaderFont.Harmony, 100))
        assertEquals("HarmonyOS_Sans_SC_Thin.ttf", file(ReaderFont.Harmony, 200))
        assertEquals("HarmonyOS_Sans_SC_Light.ttf", file(ReaderFont.Harmony, 300))
        assertEquals("HarmonyOS_Sans_SC_Regular.ttf", file(ReaderFont.Harmony, 400))
        assertEquals("HarmonyOS_Sans_SC_Medium.ttf", file(ReaderFont.Harmony, 500))
        // 600 在 Medium(500)/Bold(700) 之间距离相同：取更重的一侧
        assertEquals("HarmonyOS_Sans_SC_Bold.ttf", file(ReaderFont.Harmony, 600))
        assertEquals("HarmonyOS_Sans_SC_Bold.ttf", file(ReaderFont.Harmony, 700))
        // 800 同理取 Black(900)
        assertEquals("HarmonyOS_Sans_SC_Black.ttf", file(ReaderFont.Harmony, 800))
        assertEquals("HarmonyOS_Sans_SC_Black.ttf", file(ReaderFont.Harmony, 900))
    }

    @Test
    fun miSansMapsConfiguredWeightsToNearestFile() {
        assertEquals("MiSans-Thin.ttf", file(ReaderFont.MiSans, 100))
        assertEquals("MiSans-ExtraLight.ttf", file(ReaderFont.MiSans, 200))
        assertEquals("MiSans-Light.ttf", file(ReaderFont.MiSans, 300))
        assertEquals("MiSans-Regular.ttf", file(ReaderFont.MiSans, 400))
        assertEquals("MiSans-Medium.ttf", file(ReaderFont.MiSans, 500))
        assertEquals("MiSans-Semibold.ttf", file(ReaderFont.MiSans, 600))
        assertEquals("MiSans-Bold.ttf", file(ReaderFont.MiSans, 700))
        assertEquals("MiSans-Heavy.ttf", file(ReaderFont.MiSans, 800))
        assertEquals("MiSans-Heavy.ttf", file(ReaderFont.MiSans, 900))
    }

    @Test
    fun fileNamesAndWeightsMatchTheRepositoryLayout() {
        assertEquals(
            listOf(
                "HarmonyOS_Sans_SC_Thin.ttf" to 250,
                "HarmonyOS_Sans_SC_Light.ttf" to 300,
                "HarmonyOS_Sans_SC_Regular.ttf" to 400,
                "HarmonyOS_Sans_SC_Medium.ttf" to 500,
                "HarmonyOS_Sans_SC_Bold.ttf" to 700,
                "HarmonyOS_Sans_SC_Black.ttf" to 900,
            ),
            ReaderFont.Harmony.downloadableWeights.map { it.fileName to it.weight },
        )
        assertEquals(
            listOf(
                "MiSans-Thin.ttf" to 100,
                "MiSans-ExtraLight.ttf" to 200,
                "MiSans-Light.ttf" to 300,
                "MiSans-Regular.ttf" to 400,
                "MiSans-Medium.ttf" to 500,
                "MiSans-Semibold.ttf" to 600,
                "MiSans-Bold.ttf" to 700,
                "MiSans-Heavy.ttf" to 900,
            ),
            ReaderFont.MiSans.downloadableWeights.map { it.fileName to it.weight },
        )
    }

    @Test
    fun systemFontsAreNotDownloadable() {
        listOf(ReaderFont.System, ReaderFont.Heiti, ReaderFont.Songti, ReaderFont.Mono).forEach { font ->
            assertTrue(!font.isDownloadable, "$font 不应走下载")
            assertTrue(font.downloadableWeights.isEmpty())
            assertNull(font.fileForWeight(400))
        }
        assertTrue(ReaderFont.Harmony.isDownloadable)
        assertTrue(ReaderFont.MiSans.isDownloadable)
    }

    @Test
    fun downloadProgressFractionHandlesUnknownTotal() {
        assertEquals(0.5f, FontDownloadState.Running(ReaderFont.Harmony, 400, 4, 8).fraction)
        assertNull(FontDownloadState.Running(ReaderFont.Harmony, 400, 4, 0).fraction)
        assertEquals(
            1f,
            FontDownloadState.Running(ReaderFont.Harmony, 400, 12, 8).fraction,
            "超出 total 时也要夹到 1",
        )
    }

    private fun file(font: ReaderFont, weight: Int): String =
        requireNotNull(font.fileForWeight(weight)) { "$font $weight 没有对应文件" }.fileName
}
