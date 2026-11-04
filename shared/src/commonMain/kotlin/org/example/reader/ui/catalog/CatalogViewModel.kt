package org.example.reader.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.BookChapter
import org.example.reader.core.net.ApiResult

/**
 * 目录列表里的一行。
 *
 * `LazyColumn` 需要稳定 key：
 * - 卷头的 key 用「插入位置（原始目录下标）+ 卷名」，避免同一部书里 卷一 → 卷二 → 卷一
 *   这种重名卷产生重复 key；
 * - 章节行的 key 用原始目录下标。
 */
sealed interface CatalogRow {
    val key: String

    /** 卷头（仅当章节目录带卷信息且换卷时插入）。 */
    data class VolumeHeader(
        val volume: String,
        override val key: String,
    ) : CatalogRow

    /** 章节行。 */
    data class ChapterRow(
        val chapter: BookChapter,
        /** 章节原始下标（`chapters` 里的位置），点选时原样回传。 */
        val position: Int,
        /** 该行上方刚插过卷头；副标题里不再重复卷名（二选一）。 */
        val opensVolume: Boolean,
        override val key: String,
    ) : CatalogRow
}

/**
 * 目录页状态。
 *
 * `rows` / `chapterRowOf` / `matchCount` / `volumeCount` 都是**派生结果**，
 * 在 ViewModel 里算好（过滤 + 卷分组），composable 只做渲染——
 * 700+ 章不允许在组合期间做过滤或分组。
 */
data class CatalogUiState(
    val chapters: List<BookChapter> = emptyList(),
    /** 过滤 + 卷分组后的渲染序列（卷头与章节行混排）。 */
    val rows: List<CatalogRow> = emptyList(),
    /** 章节原始下标（`chapters` 里的位置）→ `rows` 里的行号。 */
    val chapterRowOf: Map<Int, Int> = emptyMap(),
    /** 关键词命中的章节数（不含卷头）。 */
    val matchCount: Int = 0,
    /** 本次列表里出现的卷头数量；0 表示这本书没有卷信息。 */
    val volumeCount: Int = 0,
    val isLoading: Boolean = true,
    /** 刷新中（列表已有内容时为顶栏小转圈，不遮列表）。 */
    val isRefreshing: Boolean = false,
    /** 首屏错误：整屏展示并带重试。 */
    val error: String? = null,
    /** 一次性提示（刷新结果 / 刷新失败），弹完即清。 */
    val notice: String? = null,
    val keyword: String = "",
    val filterExpanded: Boolean = false,
    /** 每次成功载入目录 +1，界面据此决定「恢复原位」还是「滚到在读章」。 */
    val loadVersion: Int = 0,
) {
    /** 这本书是否有卷信息；为 false 时列表里一个卷头都不该出现。 */
    val hasVolumes: Boolean get() = volumeCount > 0

    val lastRowIndex: Int get() = rows.lastIndex

    /**
     * 章节原始下标 → 行号。
     *
     * 返回 null 表示「不在当前列表里」（没进度，或搜索过滤把它滤掉了），
     * 界面据此隐藏「定位当前章」按钮。
     */
    fun rowOf(chapterPosition: Int?): Int? {
        if (chapterPosition == null) return null
        return chapterRowOf[chapterPosition]
    }
}

/**
 * 目录（独立页面）。
 *
 * 书籍详情与阅读器共用：详情页用它「看看有哪些章」，
 * 阅读器用它跳章——所以这里只依赖 bookId 与回调，不感知来源页面。
 */
class CatalogViewModel(
    private val deps: AppDependencies,
    private val bookId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init {
        load()
    }

    /**
     * 拉目录。
     *
     * @param force false = 走服务端缓存（首屏进入，快）；true = 带 `refresh=1`
     *   让后端重抓书源目录（顶栏「刷新目录」）。
     */
    fun load(force: Boolean = false) {
        val hadContent = _state.value.chapters.isNotEmpty()
        _state.update {
            it.copy(
                // 首屏（还没有任何目录）才整屏转圈；刷新时列表保持可见
                isLoading = !hadContent,
                isRefreshing = hadContent || force,
                error = null,
            )
        }
        viewModelScope.launch {
            when (val result = deps.api.getChapterList(bookId, refresh = force)) {
                is ApiResult.Success -> _state.update { current ->
                    current.withChapters(
                        chapters = result.value,
                        keyword = current.keyword,
                        notice = if (force) describeRefresh(current.chapters, result.value) else current.notice,
                    ).copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        loadVersion = current.loadVersion + 1,
                    )
                }

                is ApiResult.Failure -> _state.update { current ->
                    val keepList = current.chapters.isNotEmpty()
                    current.copy(
                        isLoading = false,
                        isRefreshing = false,
                        // 已有目录时不把列表换成一整屏错误页，只在提示条里说一声
                        error = if (keepList) null else result.message,
                        notice = if (keepList) result.message else current.notice,
                    )
                }
            }
        }
    }

    fun onKeywordChange(value: String) {
        _state.update { it.withChapters(it.chapters, value) }
    }

    fun toggleFilter() {
        _state.update {
            if (it.filterExpanded) {
                // 收起搜索：清空关键词，列表恢复完整目录
                it.withChapters(it.chapters, "").copy(filterExpanded = false)
            } else {
                it.copy(filterExpanded = true)
            }
        }
    }

    /** 提示已弹出，清掉避免重复弹。 */
    fun consumeNotice() {
        _state.update { if (it.notice == null) it else it.copy(notice = null) }
    }
}

/** 用新的章节目录 + 关键词重算派生字段（行序列、行号映射、命中数、卷数）。 */
private fun CatalogUiState.withChapters(
    chapters: List<BookChapter>,
    keyword: String,
    notice: String? = this.notice,
): CatalogUiState {
    val content = deriveCatalogContent(chapters, keyword)
    return copy(
        chapters = chapters,
        rows = content.rows,
        chapterRowOf = content.chapterRowOf,
        matchCount = content.matchCount,
        volumeCount = content.volumeCount,
        keyword = keyword,
        notice = notice,
    )
}

/** 目录派生结果。 */
internal data class CatalogContent(
    val rows: List<CatalogRow> = emptyList(),
    val chapterRowOf: Map<Int, Int> = emptyMap(),
    val matchCount: Int = 0,
    val volumeCount: Int = 0,
)

/**
 * 过滤 + 卷分组。
 *
 * 卷头规则：`volume` 非空、且**与过滤后序列里的上一章不同名**时插一行；
 * 全部章节都没有卷信息时一个卷头都不插（`volumeCount = 0`）。
 * 章节自身是卷标题行（`isVolume = true`）时不再重复插卷头。
 */
internal fun deriveCatalogContent(chapters: List<BookChapter>, keyword: String): CatalogContent {
    if (chapters.isEmpty()) return CatalogContent()
    val key = keyword.trim()
    val indexed = chapters.withIndex()
    val filtered: List<IndexedValue<BookChapter>> =
        if (key.isEmpty()) indexed.toList() else indexed.filter { it.value.title.contains(key, true) }
    if (filtered.isEmpty()) return CatalogContent()

    val rows = ArrayList<CatalogRow>(filtered.size + 8)
    val chapterRowOf = HashMap<Int, Int>(filtered.size * 2)
    // 上一章生效的卷名（null 表示上一章没有卷信息，遇到卷名会重新插头）
    var previousVolume: String? = null
    var volumeCount = 0

    filtered.forEach { entry ->
        val chapter = entry.value
        val label = volumeLabelOf(chapter)
        val isVolumeTitle = chapter.isVolume == true
        val opensVolume = !isVolumeTitle && label != null && label != previousVolume
        if (opensVolume) {
            rows.add(CatalogRow.VolumeHeader(volume = label.orEmpty(), key = "v:${entry.index}"))
            volumeCount++
        }
        chapterRowOf[entry.index] = rows.size
        rows.add(
            CatalogRow.ChapterRow(
                chapter = chapter,
                position = entry.index,
                opensVolume = opensVolume,
                key = "c:${entry.index}",
            ),
        )
        previousVolume = label
    }

    return CatalogContent(
        rows = rows,
        chapterRowOf = chapterRowOf,
        matchCount = filtered.size,
        volumeCount = volumeCount,
    )
}

/**
 * 章节所属卷名。
 *
 * 优先用 `volume`；章节自身就是卷标题行（`isVolume = true`）但 `volume` 为空时退化成标题，
 * 这样后面的章节同卷名判断不会因为「卷标题行没写 volume」而重复插头。
 */
private fun volumeLabelOf(chapter: BookChapter): String? {
    val declared = chapter.volume?.trim()
    if (!declared.isNullOrEmpty()) return declared
    if (chapter.isVolume != true) return null
    val title = chapter.title.trim()
    return title.ifEmpty { null }
}

/** 刷新结果的一句话提示（无内容差异也要给反馈，否则用户不知道刷没刷）。 */
private fun describeRefresh(old: List<BookChapter>, new: List<BookChapter>): String {
    if (old.isEmpty()) return if (new.isEmpty()) "目录为空" else "目录已更新（${new.size} 章）"
    val delta = new.size - old.size
    return when {
        delta > 0 -> "目录已更新，新增 $delta 章"
        delta < 0 -> "目录已更新，减少 ${-delta} 章"
        else -> "目录已是最新（${new.size} 章）"
    }
}
