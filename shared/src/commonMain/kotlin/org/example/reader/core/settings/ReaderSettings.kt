package org.example.reader.core.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.reader.ui.reader.ReaderPalettes

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
 * 自动阅读的滚动方式。取值与前端 `readerConfig.autoPageMode` 逐项对齐，
 * 直接存字符串（`pixel` / `paragraph`），两边同一份配置可互换。
 */
object AutoPageMode {
    /** 逐帧滚动，速度由 `scrollPixel`（每帧像素）决定。 */
    const val PIXEL = "pixel"

    /** 按段落滚动，每段停留 `pageSpeed` 毫秒。 */
    const val PARAGRAPH = "paragraph"

    /**
     * 归一化落盘值。
     *
     * 旧版本、Web 端同步或被手改过的值都可能是任意字符串；不认识就回落到
     * 像素滚动（前端默认值），不要抛异常也不要留一个渲染层无法识别的分支。
     */
    fun normalize(value: String?): String =
        if (value == PARAGRAPH) PARAGRAPH else PIXEL
}

/**
 * 点按正文的行为。取值与前端 `readerConfig.clickAction` 一致。
 *
 * 三档只影响「上一屏/下一页」，中间格（切控件）永远可用——
 * `none` 档若连菜单也关掉，用户就呼不出控件了。
 */
object ClickAction {
    /** 到底/到顶时自动切章。 */
    const val AUTO = "auto"

    /** 只滚动/翻页，不切章。 */
    const val NEXT = "next"

    /** 忽略左右与上下格，只剩中间格切控件。 */
    const val NONE = "none"

    /** 未知值（旧版本、手改 prefs）一律回落 auto，避免三档都不选中。 */
    fun normalize(value: String?): String = when (value) {
        AUTO, NEXT, NONE -> value
        else -> AUTO
    }
}

/**
 * 阅读器排版设置。
 *
 * 默认值取自前端 `stores/readerConfig.ts`（字号 18 / 行高 1.8 / 段距 0.2 / 首屏上下滑动 /
 * 像素滚动 1px / 每段 1000ms），保证同一个人在两端看到的正文密度与滚动速度一致。
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
    /** 自动阅读滚动方式，取值见 [AutoPageMode]。 */
    val autoPageMode: String = DEFAULT_AUTO_PAGE_MODE,
    /** 像素滚动时**每帧**滚动的像素数 1..10；不做帧率补偿，60Hz 下 1px ≈ 60px/s。 */
    val scrollPixel: Int = DEFAULT_SCROLL_PIXEL,
    /** 段落滚动时每段停留的毫秒数 100..5000。 */
    val pageSpeed: Int = DEFAULT_PAGE_SPEED,
    /** 点按正文的行为，取值见 [ClickAction]。 */
    val clickAction: String = ClickAction.AUTO,
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
        /**
         * 亮度下限。
         *
         * 不能是 0.2：亮度默认值取自系统设置，夜间系统亮度常低于 0.2，
         * 夹到 0.2 会让「默认值 = 系统亮度」变成「比系统更亮」。0.01 与
         * `WindowBrightness` 的绝对下限一致（0 在 Android 上是黑屏而不是最暗）。
         */
        const val MIN_BRIGHTNESS = 0.01f
        const val MAX_BRIGHTNESS = 1f

        // 自动阅读（区间与前端 `ReadSettings.vue` 的 step 上下界一致）
        const val DEFAULT_AUTO_PAGE_MODE = AutoPageMode.PIXEL
        const val DEFAULT_SCROLL_PIXEL = 1
        const val MIN_SCROLL_PIXEL = 1
        const val MAX_SCROLL_PIXEL = 10
        const val DEFAULT_PAGE_SPEED = 1000
        const val MIN_PAGE_SPEED = 100
        const val MAX_PAGE_SPEED = 5000

        /** 主题序号合法区间上界。主题表的唯一定义在 [ReaderPalettes]，这里只取长度。 */
        val MAX_THEME_INDEX: Int = ReaderPalettes.lastIndex
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
 *
 * **写盘策略**：连续型控件（亮度、字号滑杆）拖动时每帧都会回调，
 * 用 [previewBrightness] / [previewFontSize] 只改内存态（正文/蒙版实时跟随），
 * 松手时才走 [setBrightness] / [setFontSize] 落盘。
 * 否则一次拖动会产生几百次 `SharedPreferences.apply()`（每次 update 连写 7 个键）。
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

    /**
     * 切换纸张主题。
     *
     * 上下界都要 clamp：越界索引会让 `readerPalette()` 静默回落到 0，
     * 用户点了「暗夜」却看到白底，且看不出是配置被改坏了。
     */
    fun setTheme(index: Int) = update {
        it.copy(themeIndex = index.coerceIn(0, ReaderSettings.MAX_THEME_INDEX))
    }

    fun setMethod(method: ReadMethod) = update { it.copy(method = method) }

    /**
     * 设置亮度。
     *
     * **只改内存，不落盘**：亮度不记忆，每次进阅读器都重新取系统亮度作为初值
     * （见 `rememberSystemBrightness()`）。这里若写盘，下次进阅读器就会用上一次
     * 手动拖出来的值，而不是用户系统当前的亮度。
     */
    fun setBrightness(value: Float) = previewBrightness(value)

    fun setEyeCare(enabled: Boolean) = update { it.copy(eyeCare = enabled) }

    fun setClickAction(value: String) = update { it.copy(clickAction = ClickAction.normalize(value)) }

    /**
     * 切换自动阅读的滚动方式（像素 / 段落）。
     *
     * 非法值一律归一到像素滚动，不写盘任意字符串：渲染层只认 [AutoPageMode] 两个常量，
     * 多出一个值会让 `when` 落到 else 分支。
     */
    fun setAutoPageMode(mode: String) =
        update { it.copy(autoPageMode = AutoPageMode.normalize(mode)) }

    /** 像素滚动的每帧像素数；越界值夹到 1..10（与前端 step 的上下界一致）。 */
    fun setScrollPixel(value: Int) = update {
        it.copy(
            scrollPixel = value.coerceIn(
                ReaderSettings.MIN_SCROLL_PIXEL,
                ReaderSettings.MAX_SCROLL_PIXEL,
            ),
        )
    }

    /** 段落滚动的每段停留毫秒数；越界值夹到 100..5000。 */
    fun setPageSpeed(value: Int) = update {
        it.copy(
            pageSpeed = value.coerceIn(ReaderSettings.MIN_PAGE_SPEED, ReaderSettings.MAX_PAGE_SPEED),
            clickAction = ClickAction.normalize(store.getString(SettingsKeys.READER_CLICK_ACTION)),
        )
    }

    fun reset() = update { ReaderSettings() }

    /**
     * 亮度预览：只改内存态，不落盘。
     *
     * 供滑杆 `onValueChange` 每帧调用；松手时再调 [setBrightness] 写一次。
     */
    fun previewBrightness(value: Float) {
        val next = value.coerceIn(ReaderSettings.MIN_BRIGHTNESS, ReaderSettings.MAX_BRIGHTNESS)
        val current = _state.value
        if (current.brightness == next) return
        _state.value = current.copy(brightness = next)
    }

    /**
     * 字号预览：只改内存态，不落盘。
     *
     * 供滑杆 `onValueChange` 每帧调用；松手时再调 [setFontSize] 写一次。
     */
    fun previewFontSize(value: Int) {
        val next = value.coerceIn(ReaderSettings.MIN_FONT_SIZE, ReaderSettings.MAX_FONT_SIZE)
        val current = _state.value
        if (current.fontSize == next) return
        _state.value = current.copy(fontSize = next)
    }

    private fun update(transform: (ReaderSettings) -> ReaderSettings) {
        val next = transform(_state.value)
        _state.value = next
        store.putInt(SettingsKeys.READER_FONT_SIZE, next.fontSize)
        store.putString(SettingsKeys.READER_LINE_HEIGHT, next.lineHeight.toString())
        store.putString(SettingsKeys.READER_PARAGRAPH_SPACING, next.paragraphSpacing.toString())
        store.putInt(SettingsKeys.READER_THEME_INDEX, next.themeIndex)
        store.putString(SettingsKeys.READER_METHOD, next.method.key)
        store.putBoolean(SettingsKeys.READER_EYE_CARE, next.eyeCare)
        store.putString(SettingsKeys.READER_AUTO_PAGE_MODE, next.autoPageMode)
        store.putInt(SettingsKeys.READER_SCROLL_PIXEL, next.scrollPixel)
        store.putInt(SettingsKeys.READER_PAGE_SPEED, next.pageSpeed)
        store.putString(SettingsKeys.READER_CLICK_ACTION, next.clickAction)
    }

    /**
     * 读盘。
     *
     * 落盘值来自旧版本、Web 端同步或被手改过时都可能是非法值
     * （越界字号、NaN、空串），直接透传给滑杆会崩或显示错乱。
     * 所以**每个字段都按 [`ReaderSettings`] 里的 MIN/MAX 夹一次**，
     * 不做「信任本地存储」的假设。
     */
    private fun load(): ReaderSettings {
        val storedFontSize = store.getInt(SettingsKeys.READER_FONT_SIZE, ReaderSettings.DEFAULT_FONT_SIZE)
        val storedLineHeight = store.getString(SettingsKeys.READER_LINE_HEIGHT)?.toFloatOrNull()
        val storedParagraphSpacing = store.getString(SettingsKeys.READER_PARAGRAPH_SPACING)?.toFloatOrNull()
        val storedThemeIndex = store.getInt(SettingsKeys.READER_THEME_INDEX, 0)
        return ReaderSettings(
            fontSize = storedFontSize.coerceIn(
                ReaderSettings.MIN_FONT_SIZE,
                ReaderSettings.MAX_FONT_SIZE,
            ),
            lineHeight = storedLineHeight.finiteOr(ReaderSettings.DEFAULT_LINE_HEIGHT).coerceIn(
                ReaderSettings.MIN_LINE_HEIGHT,
                ReaderSettings.MAX_LINE_HEIGHT,
            ),
            paragraphSpacing = storedParagraphSpacing
                .finiteOr(ReaderSettings.DEFAULT_PARAGRAPH_SPACING)
                .coerceIn(
                    ReaderSettings.MIN_PARAGRAPH_SPACING,
                    ReaderSettings.MAX_PARAGRAPH_SPACING,
                ),
            themeIndex = storedThemeIndex.coerceIn(0, ReaderSettings.MAX_THEME_INDEX),
            method = ReadMethod.fromKey(store.getString(SettingsKeys.READER_METHOD)),
            // 亮度不落盘也不读盘：初值用 MAX（= 跟随系统），进阅读器时再用实际系统亮度覆盖
            brightness = ReaderSettings.MAX_BRIGHTNESS,
            eyeCare = store.getBoolean(SettingsKeys.READER_EYE_CARE, false),
            autoPageMode = AutoPageMode.normalize(
                store.getString(SettingsKeys.READER_AUTO_PAGE_MODE),
            ),
            scrollPixel = store
                .getInt(SettingsKeys.READER_SCROLL_PIXEL, ReaderSettings.DEFAULT_SCROLL_PIXEL)
                .coerceIn(ReaderSettings.MIN_SCROLL_PIXEL, ReaderSettings.MAX_SCROLL_PIXEL),
            pageSpeed = store
                .getInt(SettingsKeys.READER_PAGE_SPEED, ReaderSettings.DEFAULT_PAGE_SPEED)
                .coerceIn(ReaderSettings.MIN_PAGE_SPEED, ReaderSettings.MAX_PAGE_SPEED),
        )
    }
}

/**
 * `Float.toFloatOrNull()` 对 "NaN"/"Infinity" 也返回数值，而 `coerceIn` 不会修正 NaN
 * （NaN 的比较恒为 false，会原样穿过夹取）。滑杆拿到 NaN 会画出空白轨道。
 */
private fun Float?.finiteOr(default: Float): Float =
    if (this != null && this.isFinite()) this else default
