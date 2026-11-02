package org.example.reader.core.settings

/**
 * 轻量键值存储。commonMain 只依赖这层抽象，Android 侧用 SharedPreferences 实现。
 *
 * 阅读器配置项很多（字号/行高/主题/翻页方式等），统一走这里，避免每加一项
 * 都要改平台接口。
 */
interface KeyValueStore {
    fun getString(key: String, default: String? = null): String?
    fun putString(key: String, value: String?)
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, default: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, default: Long = 0L): Long
    fun putLong(key: String, value: Long)
    fun remove(key: String)
}

/** 配置键集中在此，避免字符串散落。 */
object SettingsKeys {
    const val API_BASE = "api_base"
    const val USERNAME = "username"

    // 阅读器排版
    const val READER_FONT_SIZE = "reader_font_size"
    const val READER_LINE_HEIGHT = "reader_line_height"
    const val READER_PARAGRAPH_SPACING = "reader_paragraph_spacing"
    const val READER_FIRST_LINE_INDENT = "reader_first_line_indent"
    const val READER_THEME_INDEX = "reader_theme_index"
    const val READER_IS_NIGHT = "reader_is_night"
    const val READER_METHOD = "reader_method"
    const val READER_FONT_FAMILY = "reader_font_family"
    const val READER_FONT_WEIGHT = "reader_font_weight"
    const val READER_PAGE_WIDTH = "reader_page_width"
    const val READER_SHOW_CHAPTER_IMAGES = "reader_show_chapter_images"
    const val READER_PRELOAD = "reader_preload"

    // 应用
    const val APP_THEME = "app_theme"
}
