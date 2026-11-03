package org.example.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.Book
import org.example.reader.core.model.BookChapter
import org.example.reader.core.model.ChapterContent
import org.example.reader.core.net.ApiResult
import org.example.reader.core.util.nowMillis
import kotlin.math.roundToInt

data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val chapters: List<BookChapter> = emptyList(),
    val currentIndex: Int = 0,
    val paragraphs: List<String> = emptyList(),
    val isLoadingContent: Boolean = false,
    val error: String? = null,
    /** 正文来自用户编辑版（而非上游）；界面上给个轻标记。 */
    val isEdited: Boolean = false,
    /**
     * 待恢复的章内位置（0..1）。章节内容落定后由界面消费一次：
     * 读到一半退出，重进回到原处而不是章首。
     */
    val pendingScrollFraction: Float? = null,
) {
    val currentChapter: BookChapter? get() = chapters.getOrNull(currentIndex)
    val hasPrev: Boolean get() = currentIndex > 0
    val hasNext: Boolean get() = currentIndex < chapters.lastIndex
}

/**
 * 阅读器状态机。
 *
 * 章节身份是 `chapterUrl`（换源会变），书籍身份是 `bookId`（换源不变）。
 * 进度对齐 Web 端：服务端 `durChapterPos` 用 0–10000 的万分比。
 *
 * 正文有两层加速：内存 LRU（默认 8 章）+ 进章后预取相邻章，
 * 连续翻章基本不用等网络。
 */
class ReaderViewModel(
    private val deps: AppDependencies,
    private val bookId: String,
    /** 从详情页目录进入时指定的起始章；null 表示续读服务端记录的位置。 */
    private val initialIndex: Int? = null,
) : ViewModel() {

    /** 章节还没加载完时先记下目标章，`open()` 落定后直接用。 */
    private var pendingIndex: Int? = null

    /** 已应用过的起始章：从详情返回阅读器时避免把进度又拉回进入时那一章。 */
    private var lastAppliedStartIndex: Int? = null

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    /** 正文缓存：key 是目录下标。换源/刷新时整条清掉，避免串内容。 */
    private val contentCache = LinkedHashMap<Int, ChapterContent>()

    /** 正在预取的目录下标，避免同一章重复请求。 */
    private val prefetching = mutableSetOf<Int>()

    /** 当前章内位置（0..1），滚动时持续更新，落库时换算成万分比。 */
    private var lastFraction = 0f

    /** 上次上报时间：滚动过程中按 [PROGRESS_SAVE_INTERVAL_MS] 节流。 */
    private var lastSavedAt = 0L

    /** 打开已读过的章节时，记住要恢复到的位置。 */
    private var pendingRestoreFraction: Float? = null

    init {
        open()
    }

    fun open() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val bookResult = deps.api.getShelfBook(bookId)
            val book = when (bookResult) {
                is ApiResult.Success -> bookResult.value
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = bookResult.message) }
                    return@launch
                }
            }
            val chapterResult = deps.api.getChapterList(bookId)
            when (chapterResult) {
                is ApiResult.Success -> {
                    val chapters = chapterResult.value
                    if (chapters.isEmpty()) {
                        _state.update {
                            it.copy(isLoading = false, book = book, error = "目录为空，可能是书源规则失效")
                        }
                        return@launch
                    }
                    val requested = pendingIndex ?: initialIndex
                    pendingIndex = null
                    val startIndex = (requested ?: book.durChapterIndex)
                        .coerceIn(0, chapters.lastIndex)
                    // 只有「续读上次那章」才恢复章内位置；从目录指定章节一律从章首开始
                    pendingRestoreFraction = if (startIndex == book.durChapterIndex && book.durChapterPos > 0) {
                        book.durChapterPos / 10000f
                    } else {
                        null
                    }
                    _state.update {
                        it.copy(isLoading = false, book = book, chapters = chapters, currentIndex = startIndex)
                    }
                    loadContent(startIndex)
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false, book = book, error = chapterResult.message) }
                }
            }
        }
    }

    /**
     * 加载章节正文。
     *
     * 命中缓存直接出文字并顺手预取相邻章；未命中才走网络。
     */
    private fun loadContent(index: Int, force: Boolean = false) {
        val chapter = _state.value.chapters.getOrNull(index) ?: return
        _state.update { it.copy(isLoadingContent = true, currentIndex = index, error = null) }

        if (!force) {
            val cached = contentCache[index]
            if (cached != null) {
                applyContent(index, chapter, cached)
                prefetchNeighbors(index)
                return
            }
        } else {
            contentCache.remove(index)
        }

        viewModelScope.launch {
            val result = deps.api.getBookContent(
                bookId = bookId,
                chapterUrl = chapter.url,
                index = chapter.index,
            )
            when (result) {
                is ApiResult.Success -> {
                    remember(index, result.value)
                    applyContent(index, chapter, result.value)
                    prefetchNeighbors(index)
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoadingContent = false, error = result.message) }
                }
            }
        }
    }

    private fun applyContent(index: Int, chapter: BookChapter, content: ChapterContent) {
        val paragraphs = splitParagraphs(content.content)
        val restore = pendingRestoreFraction
        pendingRestoreFraction = null
        lastFraction = restore ?: 0f
        _state.update {
            it.copy(
                isLoadingContent = false,
                paragraphs = paragraphs,
                isEdited = content.edited,
                pendingScrollFraction = restore,
            )
        }
        // 最近阅读以「实际读到哪一章」为准（Web 端同样在章节落定后记录）
        val book = _state.value.book?.copy(
            durChapterIndex = index,
            durChapterTitle = chapter.title,
            durChapterTime = nowMillis(),
            durChapterPos = (lastFraction * 10000).roundToInt(),
        )
        if (book != null) {
            _state.update { it.copy(book = book) }
            deps.recentBooks.record(book)
        }
        lastSavedAt = nowMillis()
        persistProgress(index, lastFraction)
    }

    // ─── 预加载 ────────────────────────────────────────────────────────────────

    /**
     * 预取相邻章节（后 2 章 + 前 1 章）。
     *
     * 批量接口单次上限 10 章，这里最多 3 章，一次请求拿完。
     * 失败静默：预取只是加速，不该弹错误打断阅读。
     */
    private fun prefetchNeighbors(index: Int) {
        val chapters = _state.value.chapters
        if (chapters.isEmpty()) return
        val wanted = listOf(index + 1, index + 2, index - 1)
            .filter { it in chapters.indices && it !in contentCache && it !in prefetching }
        if (wanted.isEmpty()) return
        prefetching += wanted
        viewModelScope.launch {
            val requested = wanted.map { chapters[it] }
            val result = deps.api.getBookContents(bookId, requested)
            if (result is ApiResult.Success) {
                result.value.forEach { item ->
                    val content = item.data
                    if (content == null || item.error != null) return@forEach
                    // 后端回的是章节自身 index，映射回目录下标
                    val position = wanted.firstOrNull { chapters[it].index == item.index }
                        ?: return@forEach
                    remember(position, content)
                }
            }
            prefetching -= wanted
        }
    }

    /** 写缓存并按 LRU 上限裁剪（LinkedHashMap 的插入顺序即使用顺序）。 */
    private fun remember(index: Int, content: ChapterContent) {
        contentCache.remove(index)
        contentCache[index] = content
        while (contentCache.size > CONTENT_CACHE_SIZE) {
            val eldest = contentCache.keys.firstOrNull() ?: break
            contentCache.remove(eldest)
        }
    }

    // ─── 进度 ──────────────────────────────────────────────────────────────────

    /**
     * 滚动位置回传（0..1）。按 10s 节流上报，避免每个像素都发请求。
     */
    fun onScrolled(fraction: Float) {
        lastFraction = fraction.coerceIn(0f, 1f)
        val now = nowMillis()
        if (now - lastSavedAt >= PROGRESS_SAVE_INTERVAL_MS) {
            lastSavedAt = now
            persistProgress(_state.value.currentIndex, lastFraction)
        }
    }

    /**
     * 进入阅读器时的位置。
     *
     * ViewModel 按 bookId 缓存：离开阅读器再点回来不会重建，
     * 所以「上次读到哪」要靠这里拿，否则每次都退回章首并把进度冲成 0。
     */
    fun entryFraction(): Float = lastFraction

    /** 界面对着 [ReaderUiState.pendingScrollFraction] 滚完一次后调用，避免重复恢复。 */
    fun consumePendingScroll() {
        _state.update { it.copy(pendingScrollFraction = null) }
    }

    /** 离开阅读器/切后台时立即落库，不等节流窗口。 */
    fun flushProgress() {
        val state = _state.value
        if (state.paragraphs.isEmpty()) return
        lastSavedAt = nowMillis()
        persistProgress(state.currentIndex, lastFraction)
    }

    // ─── 章节切换 ──────────────────────────────────────────────────────────────

    fun goToChapter(index: Int) {
        val state = _state.value
        if (index !in state.chapters.indices || index == state.currentIndex) return
        // 先保存离开那一章的位置，再切
        flushProgress()
        loadContent(index)
    }

    /**
     * 外部（书籍详情页的目录）指定章节。
     *
     * 章节列表可能还没回来：先记 pending，由 `open()` 消费；
     * 已就绪则直接加载。ViewModel 按 bookId 缓存，所以不能只靠构造参数。
     */
    fun applyStartIndex(index: Int?) {
        if (index == null || index == lastAppliedStartIndex) return
        lastAppliedStartIndex = index
        openAt(index)
    }

    fun openAt(index: Int) {
        val chapters = _state.value.chapters
        if (chapters.isEmpty()) {
            pendingIndex = index
            return
        }
        goToChapter(index)
    }

    fun nextChapter() = goToChapter(_state.value.currentIndex + 1)

    fun prevChapter() = goToChapter(_state.value.currentIndex - 1)

    /** 重新拉当前章（忽略缓存），用于「刷新本章」。 */
    fun refreshChapter() {
        loadContent(_state.value.currentIndex, force = true)
    }

    /** 上报进度。接口对未入库书返回 400，静默忽略即可（与 Web 端一致）。 */
    private fun persistProgress(index: Int, fraction: Float) {
        val position = (fraction * 10000).roundToInt().coerceIn(0, 10000)
        viewModelScope.launch {
            deps.api.saveBookProgress(bookId = bookId, index = index, position = position)
        }
    }

    /**
     * 正文按行切段。
     *
     * 后端正文是纯文本 + `\n` 分段，但可能夹带 HTML 片段（配图 `<figure>`）。
     * 这里保留原样交给渲染层判断，不做破坏性清洗——清洗规则属于
     * 服务端「净化规则」的职责。
     */
    private fun splitParagraphs(content: String): List<String> =
        content.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private companion object {
        /** 常驻正文缓存的章数。再多意义不大：跨章回看有预取兜底。 */
        const val CONTENT_CACHE_SIZE = 8

        /** 进度上报节流窗口，与前端 `readerProgressAutoSave` 一致。 */
        const val PROGRESS_SAVE_INTERVAL_MS = 10_000L
    }
}
