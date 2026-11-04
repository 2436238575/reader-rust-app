package org.example.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
     * 一次性 UI 滚动指令（0..1），**不是位置源**。
     *
     * 位置真源只有 VM 内的 `lastFraction`；这里只在「换到另一章且要落到非章首」时下发，
     * 界面滚完调 [ReaderViewModel.consumePendingScroll] 消费掉。
     * 它不能当位置读：残留值会在重进阅读器时把位置拽回旧章旧处，甚至把进度冲成 0。
     */
    val pendingScrollFraction: Float? = null,
    /**
     * `paragraphs` 属于哪一章。
     *
     * 换章时 `currentIndex` 会先变、正文后到；分页/定位必须等两者一致，
     * 否则会拿上一章的正文排版并提前消费掉恢复位置。
     */
    val contentIndex: Int = -1,
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
 * 正文有两层加速：内存 LRU（访问序，默认 8 章，当前章不被挤出）+ 进章后预取相邻章，
 * 连续翻章基本不用等网络。
 *
 * 位置只有两个来源，别再加第三个：
 * 1. 服务端进度（`open()` 续读，换算成 0..1 后作为**参数**传给 `loadContent`）；
 * 2. 内存里的 `lastFraction`（与 `currentIndex` 同批更新，见 [loadContent]）。
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

    /**
     * 正文请求代际。每发起一次换章/刷新自增，网络返回后必须比对，落后者整条作废。
     *
     * 为什么用代际而不是 `Job.cancel()`：`awaitBuffered` 的取消检查发生在 resume 之前，
     * 而「响应字节已经读完、协程却还没被调度执行」之间存在窗口，取消在那个窗口里拦不住；
     * 况且预取（`/getBookContents`）与单章请求共用同一个 `viewModelScope`，
     * 按 Job 取消要么漏、要么误伤。代际校验是唯一能覆盖全部窗口、且与现象一一对应的判据。
     */
    private var generation = 0

    /**
     * 正文缓存：key 是目录下标。换源/刷新时整条清掉，避免串内容。
     *
     * `accessOrder = true`（访问序）：`get` 与 `put` 都会把键挪到队尾，
     * 「最近使用」由访问顺序自动维护，不再依赖写入顺序（旧实现是 FIFO，见 R-11）。
     */
    private val contentCache = LinkedHashMap<Int, ChapterContent>(16, 0.75f, true)

    /** 正在预取的目录下标，避免同一章重复请求。 */
    private val prefetching = mutableSetOf<Int>()

    /** 当前章内位置（0..1），滚动时持续更新，落库时换算成万分比。 */
    private var lastFraction = 0f

    /** 上次上报时间：滚动过程中按 [PROGRESS_SAVE_INTERVAL_MS] 节流。 */
    private var lastSavedAt = 0L

    /** 节流窗口内挂起的补存任务；只在窗口到期后补一次，保证位置最终一定落库。 */
    private var pendingSaveJob: Job? = null

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
                    // 只有「续读上次那章」才恢复章内位置；从目录指定章节一律从章首开始。
                    // 位置以参数形式传下去：它属于「这一次加载」，放进字段就会被迟到的旧章结果吃掉。
                    val restoreFraction =
                        if (startIndex == book.durChapterIndex && book.durChapterPos > 0) {
                            book.durChapterPos / 10000f
                        } else {
                            0f
                        }
                    _state.update {
                        it.copy(isLoading = false, book = book, chapters = chapters, currentIndex = startIndex)
                    }
                    loadContent(startIndex, restoreFraction = restoreFraction)
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
     * [restoreFraction] 是本次加载要落到的章内位置（0..1），**必须是参数**：
     * 放在字段里的后果是迟到的旧章结果会把待恢复位置「取出即清空」地吃掉，
     * 用户真正要去的章反而落在章首（R-02 的第四笔受损面）。
     *
     * 命中缓存直接出文字并顺手预取相邻章；[force] 为 `true`（刷新本章）时绕过缓存，
     * 但**不删缓存**——刷新失败时旧正文与旧缓存都要留着兜底（R-05）。
     */
    private fun loadContent(index: Int, restoreFraction: Float = 0f, force: Boolean = false) {
        val chapter = _state.value.chapters.getOrNull(index) ?: return
        // 代际自增：从这一刻起本次调用是唯一有效的正文请求，之前发出去的全部作废
        val gen = ++generation
        val restore = restoreFraction.coerceIn(0f, 1f)
        // 章号与位置必须同批更新（本段到 _state.update 之间不得有挂起点）：
        // 挂起的补存写的是「currentIndex × lastFraction」，只改章号就会把脏组合落库
        val previousFraction = lastFraction
        pendingSaveJob?.cancel()
        pendingSaveJob = null
        lastFraction = restore
        _state.update {
            it.copy(
                isLoadingContent = true,
                currentIndex = index,
                error = null,
                // 上一次加载下发的一次性滚动指令已过期，清掉避免残留位置被重放
                pendingScrollFraction = null,
            )
        }

        if (!force) {
            val cached = contentCache[index]
            if (cached != null) {
                // 访问序 Map：上面这次 get 已把该章挪到队尾，命中即刷新 LRU 顺序
                applyContent(index, chapter, cached, restore)
                prefetchNeighbors(index)
                return
            }
        }

        viewModelScope.launch {
            val result = deps.api.getBookContent(
                bookId = bookId,
                chapterUrl = chapter.url,
                index = chapter.index,
            )
            // 代际校验必须落在协程内、写任何状态之前：
            // 期间用户又换了章的话，这条迟到的结果整条丢弃，否则会出现「新章标题 + 旧章正文」
            if (gen != generation || index != _state.value.currentIndex) return@launch
            when (result) {
                is ApiResult.Success -> {
                    remember(index, result.value)
                    applyContent(index, chapter, result.value, restore)
                    prefetchNeighbors(index)
                }

                is ApiResult.Failure -> onContentFailure(
                    message = result.message,
                    fallbackFraction = previousFraction,
                    isRefresh = force,
                )
            }
        }
    }

    /**
     * 正文加载失败。
     *
     * 不允许先丢旧正文：只要 [ReaderUiState.contentIndex] 有效，就把 `currentIndex` 回滚到
     * 正文实际所属的那一章，同时把 `lastFraction` 退回离场位置——「章号 × 位置」必须始终成对，
     * 否则退出时的 `flushProgress()` 会把旧章位置写进新章。只有首次加载（还没有正文）才是真空态。
     */
    private fun onContentFailure(message: String, fallbackFraction: Float, isRefresh: Boolean) {
        // 刷新失败的提示要能与普通加载错误区分（验收要求文案含「刷新」字样）
        val text = if (isRefresh) "刷新失败：$message" else message
        val rollbackTo = _state.value.contentIndex
        if (rollbackTo >= 0) {
            pendingSaveJob?.cancel()
            pendingSaveJob = null
            lastFraction = fallbackFraction.coerceIn(0f, 1f)
            _state.update {
                it.copy(isLoadingContent = false, error = text, currentIndex = rollbackTo)
            }
        } else {
            _state.update { it.copy(isLoadingContent = false, error = text) }
        }
    }

    /**
     * 应用正文。
     *
     * 第一句就是归属校验（防御性）：正文只能落到它自己所属的那一章。它与代际校验是两道闸，
     * 互不替代——代际挡的是「旧的网络结果」，这一句挡的是所有「内容归属与当前章不一致」的路径。
     *
     * 位置走参数（[restoreFraction]），本方法**不改** `lastFraction`：位置与章号已经在
     * [loadContent] 里同批更新过，这里再写一次就会与 `onScrolled` 的延迟补存抢时序。
     */
    private fun applyContent(
        index: Int,
        chapter: BookChapter,
        content: ChapterContent,
        restoreFraction: Float,
    ) {
        if (index != _state.value.currentIndex) return
        val paragraphs = splitParagraphs(content.content)
        val restore = restoreFraction.coerceIn(0f, 1f)
        val previousContentIndex = _state.value.contentIndex
        // pendingScrollFraction 只是一次性 UI 滚动指令，不是位置源（真源是 lastFraction）。
        // 只在「换到另一章且要落到非章首」时下发：
        //  · 章首不下发 —— 界面本来就会回顶，残留的 0f 会让重进阅读器时把进度冲成 0；
        //  · 同章（刷新）不下发 —— 界面按 currentFraction() 兜底即可，否则指令一旦没被消费，
        //    下次重进阅读器会按它把位置拽回旧值。
        val scrollCommand: Float? =
            if (restore > 0f && index != previousContentIndex) restore else null
        _state.update {
            it.copy(
                isLoadingContent = false,
                paragraphs = paragraphs,
                isEdited = content.edited,
                pendingScrollFraction = scrollCommand,
                contentIndex = index,
            )
        }
        // 最近阅读以「实际读到哪一章」为准（Web 端同样在章节落定后记录）
        val book = _state.value.book?.copy(
            durChapterIndex = index,
            durChapterTitle = chapter.title,
            durChapterTime = nowMillis(),
            durChapterPos = (restore * 10000).roundToInt(),
        )
        if (book != null) {
            _state.update { it.copy(book = book) }
            deps.recentBooks.record(book)
        }
        lastSavedAt = nowMillis()
        persistProgress(index, restore)
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

    /**
     * 写缓存。
     *
     * `contentCache` 是访问序 `LinkedHashMap`：`put` 与 `get` 都会把键挪到队尾，
     * 所以「写序 = 读序」，命中缓存即刷新顺序。守卫不要加在这里——
     * 预取与单章加载共用本缓存，加错位置会让预取静默失效。
     */
    private fun remember(index: Int, content: ChapterContent) {
        contentCache[index] = content
        trimCache()
    }

    /**
     * 按访问序裁剪，并**钉住当前章**。
     *
     * 正在读的章一旦被挤出，重进阅读器就会时而命中、时而落空，
     * 表现为「重进有时恢复到 85%、有时回到章首」（R-11）。没有可淘汰的键就停手。
     */
    private fun trimCache() {
        while (contentCache.size > CONTENT_CACHE_SIZE) {
            val current = _state.value.currentIndex
            val victim = contentCache.keys.firstOrNull { it != current } ?: break
            contentCache.remove(victim)
        }
    }

    // ─── 进度 ──────────────────────────────────────────────────────────────────

    /**
     * 滚动位置回传（0..1）。按 10s 节流上报，避免每个像素都发请求。
     */
    fun onScrolled(fraction: Float) {
        lastFraction = fraction.coerceIn(0f, 1f)
        val now = nowMillis()
        val elapsed = now - lastSavedAt
        if (elapsed >= PROGRESS_SAVE_INTERVAL_MS) {
            lastSavedAt = now
            persistProgress(_state.value.currentIndex, lastFraction)
            return
        }
        // 横翻这类「一次翻页只有一次回调」的场景，只靠节流会一直等不到保存；
        // 在窗口剩余时间后补一次，滚动中每个事件都会重置它，等价于防抖。
        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            delay(PROGRESS_SAVE_INTERVAL_MS - elapsed)
            lastSavedAt = nowMillis()
            persistProgress(_state.value.currentIndex, lastFraction)
        }
    }

    /**
     * 当前内存里的章内位置（0..1）。
     *
     * ViewModel 按 bookId 注册在 Activity 作用域，重进/切换翻页方式都不会重建，
     * 界面进入时用 `pendingScrollFraction ?: currentFraction()` 定位，
     * 否则会退回章首并把进度冲成 0。
     */
    fun currentFraction(): Float = lastFraction

    /** 界面对着 [ReaderUiState.pendingScrollFraction] 滚完一次后调用，避免重复恢复。 */
    fun consumePendingScroll() {
        _state.update { it.copy(pendingScrollFraction = null) }
    }

    /** 离开阅读器/切后台时立即落库，不等节流窗口。 */
    fun flushProgress() {
        val state = _state.value
        if (state.paragraphs.isEmpty()) return
        pendingSaveJob?.cancel()
        lastSavedAt = nowMillis()
        persistProgress(state.currentIndex, lastFraction)
    }

    // ─── 章节切换 ──────────────────────────────────────────────────────────────

    /**
     * 切章。
     *
     * [restoreFraction] 指定落点：`0f` 章首，`1f` 章末。
     * **`1f` 只有 [prevChapter] 用**：「上一章」的语义是回到上一章的结尾继续读，
     * 给它下发 `0f` 会让人从章首重读；其它入口（下一章 / 目录跳转 / 详情指定）一律 `0f`。
     */
    fun goToChapter(index: Int, restoreFraction: Float = 0f) {
        val state = _state.value
        if (index !in state.chapters.indices || index == state.currentIndex) return
        // 先落「离场章 × 离场位置」，再切。顺序不能反：flushProgress 读的是当前章与当前位置，
        // 挪到 loadContent 之后就会把新章的位置写进旧章。
        flushProgress()
        loadContent(index, restoreFraction = restoreFraction)
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
        // 目录/详情指定章节从章首开始
        goToChapter(index, restoreFraction = 0f)
    }

    fun nextChapter() = goToChapter(_state.value.currentIndex + 1, restoreFraction = 0f)

    fun prevChapter() = goToChapter(_state.value.currentIndex - 1, restoreFraction = 1f)

    /**
     * 重新拉当前章（忽略缓存），用于「刷新本章」。
     *
     * 位置按当前实际位置回灌成参数：旧实现让 `applyContent` 把 `lastFraction` 归零并立即写盘，
     * 85% 处点一下就被写回章首（R-05）。刷新失败时正文与位置都保持原样，只出错误提示。
     */
    fun refreshChapter() {
        loadContent(
            index = _state.value.currentIndex,
            restoreFraction = currentFraction(),
            force = true,
        )
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
