package org.example.reader.core.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 翻页方式。取值与前端 `readerConfig.readMethod` 逐项对齐，
 * `key` 是落盘用的稳定标识（改文案不会丢配置）。
 */
enum class ReadMethod(val label: String, val key: String) {
    SlideUp("上下滑动", "slide_up"),
    Horizontal("左右翻页", "horizontal"),
    DualPage("双页翻页", "dual_page"),
    Scroll("上下滚动", "scroll"),
    Scroll2("上下滚动2", "scroll2"),
    ;

    companion object {
        fun fromKey(key: String?): ReadMethod = entries.firstOrNull { it.key == key } ?: SlideUp
    }
}

/**
 * 阅读器排版设置。
 *
 * 默认值取自前端 `stores/readerConfig.ts`（字号 18 / 行高 1.8 / 段距 0.2 / 首屏上下滑动），
 * 保证同一个人在两端看到的正文密度一致。
 */
data class ReaderSettings(
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val lineHeight: Float = DEFAULT_LINE_HEIGHT,
    val paragraphSpacing: Float = DEFAULT_PARAGRAPH_SPACING,
    val themeIndex: Int = 0,
    val method: ReadMethod = ReadMethod.SlideUp,
    /** 亮度 0.2–1.0；1 表示不额外压暗（不触碰系统亮度）。 */
    val brightness: Float = 1f,
    val eyeCare: Boolean = false,
) {
    companion object {
        const val DEFAULT_FONT_SIZE = 18
        const val MIN_FONT_SIZE = 12
        const val MAX_FONT_SIZE = 32
        const val DEFAULT_LINE_HEIGHT = 1.8f
        const val MIN_LINE_HEIGHT = 1.2f
        const val MAX_LINE_HEIGHT = 2.6f
        const val DEFAULT_PARAGRAPH_SPACING = 0.2f
        const val MIN_PARAGRAPH_SPACING = 0f
        const val MAX_PARAGRAPH_SPACING = 1.2f
        const val MIN_BRIGHTNESS = 0.2f
    }

    val textLineHeightSp: Float get() = fontSize * lineHeight

    /** 段间额外间距（sp）。与前端 `paragraphSpacing` 的 em 语义一致。 */
    val paragraphGapSp: Float get() = fontSize * paragraphSpacing
}

/**
 * 阅读器设置仓库。
 *
 * 阅读器与设置面板共用同一实例：面板改一项，正文立即重排，
 * 不需要「确定」再回写。
 */
class ReaderSettingsStore(private val store: KeyValueStore) {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<ReaderSettings> = _state.asStateFlow()

    fun setFontSize(size: Int) = update {
        it.copy(
            fontSize = size.coerceIn(ReaderSettings.MIN_FONT_SIZE, ReaderSettings.MAX_FONT_SIZE),
        )
    }

    fun setLineHeight(value: Float) = update {
        it.copy(
            lineHeight = value.coerceIn(ReaderSettings.MIN_LINE_HEIGHT, ReaderSettings.MAX_LINE_HEIGHT),
        )
    }

    fun setParagraphSpacing(value: Float) = update {
        it.copy(
            paragraphSpacing = value.coerceIn(
                ReaderSettings.MIN_PARAGRAPH_SPACING,
                ReaderSettings.MAX_PARAGRAPH_SPACING,
            ),
        )
    }

    fun setTheme(index: Int) = update { it.copy(themeIndex = index.coerceAtLeast(0)) }

    fun setMethod(method: ReadMethod) = update { it.copy(method = method) }

    fun setBrightness(value: Float) = update {
        it.copy(brightness = value.coerceIn(ReaderSettings.MIN_BRIGHTNESS, 1f))
    }

    fun setEyeCare(enabled: Boolean) = update { it.copy(eyeCare = enabled) }

    fun reset() = update { ReaderSettings() }

    private fun update(transform: (ReaderSettings) -> ReaderSettings) {
        val next = transform(_state.value)
        _state.value = next
        store.putInt(SettingsKeys.READER_FONT_SIZE, next.fontSize)
        store.putString(SettingsKeys.READER_LINE_HEIGHT, next.lineHeight.toString())
        store.putString(SettingsKeys.READER_PARAGRAPH_SPACING, next.paragraphSpacing.toString())
        store.putInt(SettingsKeys.READER_THEME_INDEX, next.themeIndex)
        store.putString(SettingsKeys.READER_METHOD, next.method.key)
        store.putString(SettingsKeys.READER_BRIGHTNESS, next.brightness.toString())
        store.putBoolean(SettingsKeys.READER_EYE_CARE, next.eyeCare)
    }

    private fun load(): ReaderSettings = ReaderSettings(
        fontSize = store.getInt(SettingsKeys.READER_FONT_SIZE, ReaderSettings.DEFAULT_FONT_SIZE),
        lineHeight = store.getString(SettingsKeys.READER_LINE_HEIGHT)?.toFloatOrNull()
            ?: ReaderSettings.DEFAULT_LINE_HEIGHT,
        paragraphSpacing = store.getString(SettingsKeys.READER_PARAGRAPH_SPACING)?.toFloatOrNull()
            ?: ReaderSettings.DEFAULT_PARAGRAPH_SPACING,
        themeIndex = store.getInt(SettingsKeys.READER_THEME_INDEX, 0),
        method = ReadMethod.fromKey(store.getString(SettingsKeys.READER_METHOD)),
        brightness = store.getString(SettingsKeys.READER_BRIGHTNESS)?.toFloatOrNull() ?: 1f,
        eyeCare = store.getBoolean(SettingsKeys.READER_EYE_CARE, false),
    )
}
