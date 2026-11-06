package org.example.reader.core.highlight

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.reader.core.net.ReaderJson
import org.example.reader.core.settings.KeyValueStore
import org.example.reader.core.settings.SettingsKeys
import org.example.reader.core.util.nowMillis
import kotlin.random.Random

/**
 * 关键词高亮规则。
 *
 * 与前端 `KeywordHighlightRule` 同义：`scope=global` 对所有书生效，
 * `scope=book` 只对 [bookId] 生效；[color] 是 0xAARRGGBB 的半透明底色
 * （固定色盘见 [KeywordHighlightRule.COLORS]，深/浅主题都不至于糊住正文）。
 */
@Serializable
data class KeywordHighlightRule(
    val id: String,
    val keyword: String,
    val color: Long = DEFAULT_COLOR,
    val enabled: Boolean = true,
    /** `global` / `book`，见 [SCOPE_GLOBAL] / [SCOPE_BOOK]。 */
    val scope: String = SCOPE_GLOBAL,
    val bookId: String? = null,
    /** 最后更新时间（毫秒），跨端合并用；本端只写不读。 */
    val updatedAt: Long = 0,
) {
    val isGlobal: Boolean get() = scope != SCOPE_BOOK
    val normalizedColor: Long get() = normalizeColor(color)

    companion object {
        const val SCOPE_GLOBAL = "global"
        const val SCOPE_BOOK = "book"

        /** 与前端 `KEYWORD_COLORS` 同序同值（含 0x80 透明度）。 */
        const val DEFAULT_COLOR = 0x80FFD54AL

        val COLORS: List<Long> = listOf(
            0x80FFD54AL,
            0x807EE08AL,
            0x807CC4FFL,
            0x80FF9FB2L,
            0x80FFB37CL,
            0x80D3A6FFL,
        )

        /** 全透明或越界颜色会让高亮「看起来没生效」，统一回落默认色。 */
        fun normalizeColor(value: Long): Long {
            val alpha = (value ushr 24) and 0xFF
            return if (alpha == 0L) DEFAULT_COLOR else value
        }
    }
}

/**
 * 关键词高亮规则的本地存储。
 *
 * 只落本地 prefs（前端走 userdata 文档 + localStorage 镜像；本端跨端同步属于 P4-6）。
 * 解析对脏数据容错：单条坏了丢单条，不整体清空。
 */
class KeywordHighlightStore(
    private val settings: KeyValueStore,
    private val json: Json = ReaderJson,
) {
    private val _rules = MutableStateFlow(read())
    val rules: StateFlow<List<KeywordHighlightRule>> = _rules.asStateFlow()

    /** 规则变化代数：阅读器据此重建当前章（高亮在切块时写入 span）。 */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** 当前书生效的规则：启用 + 全局或本书。 */
    fun rulesFor(bookId: String): List<KeywordHighlightRule> = _rules.value.filter { rule ->
        rule.enabled && rule.keyword.isNotBlank() && (rule.isGlobal || rule.bookId == bookId)
    }

    /** 新增规则；关键词为空返回 null（UI 直接忽略）。 */
    fun add(keyword: String, color: Long, scope: String, bookId: String?): KeywordHighlightRule? {
        val text = keyword.trim()
        if (text.isEmpty()) return null
        val rule = KeywordHighlightRule(
            id = "kw-${nowMillis()}-${Random.nextInt(1000, 9999)}",
            keyword = text,
            color = KeywordHighlightRule.normalizeColor(color),
            enabled = true,
            scope = if (scope == KeywordHighlightRule.SCOPE_BOOK) KeywordHighlightRule.SCOPE_BOOK else KeywordHighlightRule.SCOPE_GLOBAL,
            bookId = if (scope == KeywordHighlightRule.SCOPE_BOOK) bookId else null,
            updatedAt = nowMillis(),
        )
        write(_rules.value + rule)
        return rule
    }

    fun setEnabled(id: String, enabled: Boolean) {
        write(_rules.value.map { if (it.id == id) it.copy(enabled = enabled, updatedAt = nowMillis()) else it })
    }

    fun delete(id: String) {
        write(_rules.value.filterNot { it.id == id })
    }

    private fun write(next: List<KeywordHighlightRule>) {
        _rules.value = next
        settings.putString(SettingsKeys.READER_KEYWORD_HIGHLIGHTS, json.encodeToString(next))
        _revision.value = _revision.value + 1
    }

    private fun read(): List<KeywordHighlightRule> {
        val raw = settings.getString(SettingsKeys.READER_KEYWORD_HIGHLIGHTS) ?: return emptyList()
        val parsed = runCatching {
            json.decodeFromString<List<KeywordHighlightRule>>(raw)
        }.getOrElse { emptyList() }
        return parsed
            .filter { it.id.isNotBlank() && it.keyword.isNotBlank() }
            .map {
                it.copy(
                    keyword = it.keyword.trim(),
                    color = it.normalizedColor,
                    scope = if (it.scope == KeywordHighlightRule.SCOPE_BOOK) {
                        KeywordHighlightRule.SCOPE_BOOK
                    } else {
                        KeywordHighlightRule.SCOPE_GLOBAL
                    },
                )
            }
    }
}
