package org.example.reader.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.example.reader.core.font.remoteFileName
import org.example.reader.core.text.ChineseMode

/** 内存版键值存储：只测读写语义，不碰 SharedPreferences。 */
private class FakeKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, Any>()

    override fun getString(key: String, default: String?): String? =
        values[key] as? String ?: default

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        values[key] as? Boolean ?: default

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    override fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default

    override fun putInt(key: String, value: Int) {
        values[key] = value
    }

    override fun getLong(key: String, default: Long): Long = values[key] as? Long ?: default

    override fun putLong(key: String, value: Long) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

class ReaderSettingsStoreAndroidHostTest {

    @Test
    fun defaultsMatchFrontendConfig() {
        val settings = ReaderSettingsStore(FakeKeyValueStore()).state.value

        assertEquals(18, settings.fontSize)
        assertEquals(1.8f, settings.lineHeight)
        assertEquals(0.2f, settings.paragraphSpacing)
        assertEquals(ReaderFont.System, settings.fontFamily)
        assertEquals(400, settings.fontWeight)
        assertEquals(800, settings.pageWidth)
        assertTrue(settings.preloadEnabled)
        assertTrue(settings.showChapterImages)
        assertEquals(ChineseMode.Simplified, settings.chineseMode)
        assertEquals(ReadMethod.SlideUp, settings.method)
    }

    @Test
    fun bundledFontKeysRoundTripAndFallBack() {
        assertEquals(ReaderFont.Harmony, ReaderFont.fromKey("harmony"))
        assertEquals(ReaderFont.MiSans, ReaderFont.fromKey("misans"))
        // 未知值回落系统字体，不能让渲染层拿到一个不存在的分支
        assertEquals(ReaderFont.System, ReaderFont.fromKey("comic-sans"))
        assertEquals(ReaderFont.System, ReaderFont.fromKey(null))
    }

    @Test
    fun remoteFontFileNamesAreStable() {
        // 仓库 fonts/ 下的文件名；改了会导致已下载用户 404，必须与仓库一致
        assertEquals("HarmonyOS_Sans_SC_Regular.ttf", ReaderFont.Harmony.remoteFileName)
        assertEquals("MiSans-Regular.ttf", ReaderFont.MiSans.remoteFileName)
        assertEquals(null, ReaderFont.System.remoteFileName)
        assertEquals(null, ReaderFont.Songti.remoteFileName)
    }

    @Test
    fun settersClampOutOfRangeValues() {
        val store = ReaderSettingsStore(FakeKeyValueStore())

        store.setFontWeight(50)
        assertEquals(100, store.state.value.fontWeight)
        store.setFontWeight(5_000)
        assertEquals(900, store.state.value.fontWeight)

        store.setPageWidth(100)
        assertEquals(300, store.state.value.pageWidth)
        store.setPageWidth(5_000)
        assertEquals(1200, store.state.value.pageWidth)

        // R-12: 主题索引上下界都必须夹住，越界会让调色板静默回落到 0
        store.setTheme(-3)
        assertEquals(0, store.state.value.themeIndex)
        store.setTheme(99)
        assertEquals(ReaderSettings.MAX_THEME_INDEX, store.state.value.themeIndex)
    }

    @Test
    fun settingsSurviveReload() {
        val fake = FakeKeyValueStore()
        val first = ReaderSettingsStore(fake)
        first.setFontFamily(ReaderFont.Harmony)
        first.setFontWeight(700)
        first.setPageWidth(450)
        first.setChineseMode(ChineseMode.Traditional)
        first.setShowChapterImages(false)
        first.setPreloadEnabled(false)

        val reloaded = ReaderSettingsStore(fake).state.value

        assertEquals(ReaderFont.Harmony, reloaded.fontFamily)
        assertEquals(700, reloaded.fontWeight)
        assertEquals(450, reloaded.pageWidth)
        assertEquals(ChineseMode.Traditional, reloaded.chineseMode)
        assertEquals(false, reloaded.showChapterImages)
        assertEquals(false, reloaded.preloadEnabled)
    }

    @Test
    fun corruptedPrefsAreClampedOnLoad() {
        val fake = FakeKeyValueStore()
        fake.putInt(SettingsKeys.READER_FONT_SIZE, 999)
        fake.putString(SettingsKeys.READER_LINE_HEIGHT, "NaN")
        fake.putString(SettingsKeys.READER_PARAGRAPH_SPACING, "-4")
        fake.putInt(SettingsKeys.READER_THEME_INDEX, 99)
        fake.putInt(SettingsKeys.READER_FONT_WEIGHT, 9_999)
        fake.putInt(SettingsKeys.READER_PAGE_WIDTH, 10)
        fake.putString(SettingsKeys.READER_FONT_FAMILY, "kaiti")
        fake.putString(SettingsKeys.READER_CHINESE_MODE, "tw")
        fake.putString(SettingsKeys.READER_METHOD, "unknown")

        val settings = ReaderSettingsStore(fake).state.value

        assertEquals(ReaderSettings.MAX_FONT_SIZE, settings.fontSize)
        assertEquals(ReaderSettings.DEFAULT_LINE_HEIGHT, settings.lineHeight)
        assertEquals(ReaderSettings.MIN_PARAGRAPH_SPACING, settings.paragraphSpacing)
        assertEquals(ReaderSettings.MAX_THEME_INDEX, settings.themeIndex)
        assertEquals(ReaderSettings.MAX_FONT_WEIGHT, settings.fontWeight)
        assertEquals(ReaderSettings.MIN_PAGE_WIDTH, settings.pageWidth)
        // 楷体/仿宋没有内置字族，统一落到宋体；未知简繁值回落简体
        assertEquals(ReaderFont.Songti, settings.fontFamily)
        assertEquals(ChineseMode.Simplified, settings.chineseMode)
        assertEquals(ReadMethod.SlideUp, settings.method)
    }
}
