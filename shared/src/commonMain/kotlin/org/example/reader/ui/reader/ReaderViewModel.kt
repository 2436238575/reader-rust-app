package org.example.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import org.example.reader.core.model.ChapterBlockBuilder
import org.example.reader.core.model.ChapterContent
import org.example.reader.core.net.ApiResult
import org.example.reader.core.util.nowMillis
import kotlin.math.roundToInt

data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val chapters: List<BookChapter> = emptyList(),
    val currentIndex: Int = 0,
    /** 当前已应用正文的不可变快照；章号、完整来源与块列表同批替换。 */
    val body: ChapterBlockSnapshot? = null,
    val isLoadingContent: Boolean = false,
    val error: String? = null,
    /**
     * 一次性 UI 滚动指令（0..1），**不是位置源**。
     *
     * 位置真源只有 VM 内的 `lastFraction`；这里只在「换到另一章且要落到非章首」时下发，
     * 界面滚完调 [ReaderViewModel.consumePendingScroll] 消费掉。
     * 它不能当位置读：残留值会在重进阅读器时把位置拽回旧章旧处，甚至把进度冲成 0。
     */
    val pendingScrollFraction: Float? = null,
) {
    val blocks get() = body?.blocks.orEmpty()
    /** 正文所属章；空态保持 -1。 */
    val contentIndex: Int get() = body?.chapterIndex ?: -1
    /** 正文是否来自用户编辑版，由同一份快照派生。 */
    val isEdited: Boolean get() = body?.source?.edited ?: false
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

    /**
     * 连续阅读在途的单章请求：目录下标 → 结果通知。
     *
     * 连续阅读会同时为窗口里的多章取正文，滚动时同一章很容易被两次「进入窗口」触发。
     * 这里让后到的那次直接 `await` 前一次的结果，而不是再发一条一模一样的请求
     * （重复请求不仅浪费流量，还会让先返回的那条被后返回的覆盖，缓存顺序也跟着乱）。
     */
    private val continuousRequests = mutableMapOf<Int, CompletableDeferred<ChapterContent?>>()

    /** 当前章内位置（0..1），滚动时持续更新，落库时换算成万分比。 */
    private var lastFraction = 0f

    /**
     * 本次位置是否来自「上一章 → 程序化章末恢复」。
     *
     * 连续列表会把 1f 锚定成最后正文块，不能从比例反推此语义；标记只用于紧接着的
     * 模式交接，真实用户位置回传立即清除。章内位置仍只有 [lastFraction] 一个真源。
     */
    private var chapterEndRestore = false

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
        val previousChapterEndRestore = chapterEndRestore
        pendingSaveJob?.cancel()
        pendingSaveJob = null
        lastFraction = restore
        chapterEndRestore = restore == 1f
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
                    fallbackChapterEndRestore = previousChapterEndRestore,
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
    private fun onContentFailure(
        message: String,
        fallbackFraction: Float,
        fallbackChapterEndRestore: Boolean,
        isRefresh: Boolean,
    ) {
        // 刷新失败的提示要能与普通加载错误区分（验收要求文案含「刷新」字样）
        val text = if (isRefresh) "刷新失败：$message" else message
        val rollbackTo = _state.value.contentIndex
        if (rollbackTo >= 0) {
            pendingSaveJob?.cancel()
            pendingSaveJob = null
            lastFraction = fallbackFraction.coerceIn(0f, 1f)
            chapterEndRestore = fallbackChapterEndRestore
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
        val snapshot = ChapterBlockSnapshot(
            chapterIndex = index,
            source = content,
            blocks = ChapterBlockBuilder.build(content),
            revision = generation,
        )
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
                body = snapshot,
                pendingScrollFraction = scrollCommand,
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
     *
     * [pinned] 是**本次写入不能被裁掉**的目录下标集合：连续阅读的窗口有 7 章，
     * 写完第 7 章时最前面那章仍在屏幕上，按普通 LRU 会被挤出去，表现为
     * 「往回滚一屏就得重新加载」。窗口由调用方给出，这里只负责别裁它。
     */
    private fun remember(index: Int, content: ChapterContent, pinned: Set<Int> = emptySet()) {
        contentCache[index] = content
        trimCache(pinned)
    }

    /**
     * 按访问序裁剪，并**钉住当前章**。
     *
     * 正在读的章一旦被挤出，重进阅读器就会时而命中、时而落空，
     * 表现为「重进有时恢复到 85%、有时回到章首」（R-11）。没有可淘汰的键就停手。
     *
     * [pinned] 见 [remember]：连续阅读的可见窗口一律不裁。
     */
    private fun trimCache(pinned: Set<Int> = emptySet()) {
        while (contentCache.size > CONTENT_CACHE_SIZE) {
            val current = _state.value.currentIndex
            val victim = contentCache.keys.firstOrNull { it != current && it !in pinned } ?: break
            contentCache.remove(victim)
        }
    }

    // ─── 连续阅读（上下滚动 / 上下滚动2） ──────────────────────────────────────

    /**
     * 只读缓存：命中返回正文，未命中返回 null。
     *
     * **不触发网络、不改任何状态**，连续阅读用它判断「这一章能不能直接画出来」。
     * 唯一副作用是访问序 `LinkedHashMap` 的 `get` 会把该章挪到「最近使用」，
     * 这正是 LRU 想要的语义，不会引起重组（不是 Compose 状态）。
     */
    fun cachedContent(index: Int): ChapterContent? = contentCache[index]

    /**
     * 为连续阅读按需取一章正文（缓存优先，其次单章接口）。
     *
     * 与 [loadContent] 的区别是**只取内容、不动状态机**：
     * - 不改 `currentIndex`（连续模式里「当前章」由界面按可见位置回传）；
     * - 不动 [generation]（连续模式没有「本次请求是唯一有效」的语义，各章互不排斥）；
     * - 不写进度（何时同步位置由 [syncContinuousPosition] 决定）。
     *
     * 同一章的并发调用共用一次请求（见 [continuousRequests]）。
     * [pinned] 直接转给 [remember]，保证窗口内的章不会被 LRU 裁掉。
     */
    suspend fun fetchChapterForContinuous(
        index: Int,
        pinned: Set<Int> = emptySet(),
    ): ChapterContent? {
        val chapter = _state.value.chapters.getOrNull(index) ?: return null
        cachedContent(index)?.let { return it }
        // 同章在途：等它的结果，不再发第二条请求
        continuousRequests[index]?.let { return it.await() }
        val deferred = CompletableDeferred<ChapterContent?>()
        continuousRequests[index] = deferred
        return try {
            val result = deps.api.getBookContent(
                bookId = bookId,
                chapterUrl = chapter.url,
                index = chapter.index,
            )
            val content = (result as? ApiResult.Success)?.value
            if (content != null) remember(index, content, pinned)
            deferred.complete(content)
            content
        } catch (e: CancellationException) {
            // 调用方被取消（切书 / 离开阅读器）：把等在同一个请求上的兄弟协程一起了结，
            // 否则它们会一直挂着等一个永远不会完成的 Deferred
            deferred.complete(null)
            throw e
        } finally {
            continuousRequests.remove(index)
        }
    }

    /**
     * 保证 `blocks` 属于 [ReaderUiState.currentIndex]（不属于就补一次加载）。
     *
     * 连续阅读滚动时**只改 `currentIndex`、不动 `contentIndex`**（正文是整块窗口，
     * `blocks` 只是顺手加载的那一章）。一旦切回单章竖排 / 横翻，两种渲染都以
     * `contentIndex == currentIndex` 为守卫：没人补这一刀，横翻会停在「正文为空」，
     * 竖排会显示上一章的正文，且没有任何路径会自愈（要等用户点「下一章」）。
     *
     * 走缓存优先的 [loadContent]，并把当前位置作为恢复参数带上：
     * 换的只是「正文归属」，不该顺带把人送回章首。
     */
    fun ensureContentForCurrentIndex() {
        val state = _state.value
        if (state.chapters.isEmpty()) return
        if (state.contentIndex == state.currentIndex) return
        // 正常换章本来就是「currentIndex 先变、正文后到」，此刻 isLoadingContent 必为真。
        // 这种在途请求不能插手：再发一次会把它按代际作废，白跑一次网络；等它落地后
        // 调用方（界面按 contentIndex/currentIndex 变化重跑）自然会再判一次。
        if (state.isLoadingContent) return
        // force = false：命中缓存就别发请求（连续阅读刚把这章取过，通常必中）
        loadContent(index = state.currentIndex, restoreFraction = currentFraction())
    }

    /**
     * 连续滚动时同步「当前章」与章内位置。
     *
     * [fraction] 的单位与其它两种翻页方式一致：**段落比例**（首个可见段落下标 / 段数），
     * 不是像素也不是含章头/章尾的项数（R-06）。
     *
     * 只有 [index] 变化才改章号、`book` 的进度字段与最近阅读——连续滚动时每段都会回调，
     * 每段都写一遍最近阅读是纯浪费，也会让「最近阅读」的时间戳变成滚动时间。
     *
     * **不动 `contentIndex`**：`contentIndex` 的语义是「`blocks` 属于哪一章」，
     * 用来在换章时卡住排版（正文没到别急着分页）。连续模式下正文是一整块窗口，
     * `blocks` 只是 VM 顺手加载的那一章的副本，与可见位置无关。
     * 因此**不要**在连续模式里用 `contentIndex != currentIndex` 当守卫——那会一直成立，
     * 把整块正文挡掉。
     *
     * [programmaticChapterEnd] 只由恢复协程在原始恢复值为 1f 时传入；普通可见位置
     * 同步（包括用户自然滚到最后正文块）一律为 false，不能继承章末恢复语义。
     */
    fun syncContinuousPosition(
        index: Int,
        fraction: Float,
        programmaticChapterEnd: Boolean = false,
    ) {
        val state = _state.value
        val chapter = state.chapters.getOrNull(index) ?: return
        val position = fraction.coerceIn(0f, 1f)
        if (index != state.currentIndex) {
            val book = state.book?.copy(
                durChapterIndex = index,
                durChapterTitle = chapter.title,
                durChapterTime = nowMillis(),
                durChapterPos = (position * 10000).roundToInt(),
            )
            _state.update { it.copy(currentIndex = index, book = book ?: it.book) }
            if (book != null) deps.recentBooks.record(book)
            // 换章是一次「位置语义变更」，不该被 10s 节流窗口压住：
            // 取消挂起的补存并把上次保存时间清零，让紧接着的 onScrolled 立刻落库。
            // 顺序很重要——先改 currentIndex 再落库，否则写进库的是「旧章 × 新位置」。
            pendingSaveJob?.cancel()
            pendingSaveJob = null
            lastSavedAt = 0L
        }
        onScrolled(position)
        // onScrolled 会清除旧标记；只有本次确为程序化章末恢复才重新建立它。
        chapterEndRestore = programmaticChapterEnd
    }

    // ─── 进度 ──────────────────────────────────────────────────────────────────

    /**
     * 滚动位置回传（0..1）。按 10s 节流上报，避免每个像素都发请求。
     */
    fun onScrolled(fraction: Float) {
        lastFraction = fraction.coerceIn(0f, 1f)
        chapterEndRestore = false
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
     * ViewModel 按 bookId 注册在 Activity 作用域，重进/切换翻页方式都不会重建。
     * 界面进入/切换模式时直接用它定位；`pendingScrollFraction` 只是待消费的一次性
     * UI 指令，不能优先于这个位置真源，否则残留指令会把视口拽回旧处。
     */
    fun currentFraction(): Float = lastFraction

    /** 当前连续阅读位置是否仍带尚未交接的程序化章末语义。 */
    fun isChapterEndRestore(): Boolean = chapterEndRestore

    /**
     * 切换渲染方式前同步真实可见位置，只更新内存真源，不另发一次中间进度请求。
     * 取消旧位置补存；目标渲染器完成定位后会按自己的页首口径接管保存。
     * 章末标记已被 [modeSwitchFraction] 消费，不能泄漏到后续真实阅读。
     */
    fun onModeSwitchPosition(fraction: Float) {
        pendingSaveJob?.cancel()
        pendingSaveJob = null
        lastFraction = fraction.coerceIn(0f, 1f)
        chapterEndRestore = false
    }

    /** 界面对着 [ReaderUiState.pendingScrollFraction] 滚完一次后调用，避免重复恢复。 */
    fun consumePendingScroll() {
        _state.update { it.copy(pendingScrollFraction = null) }
    }

    /** 离开阅读器/切后台时立即落库，不等节流窗口。 */
    fun flushProgress() {
        val state = _state.value
        if (state.blocks.isEmpty()) return
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

    private companion object {
        /**
         * 常驻正文缓存的章数。
         *
         * 连续阅读的可见窗口最宽 7 章（当前章 ±3），它们全在 [trimCache] 的 `pinned` 里；
         * 余下几章留给「刚滚过去的那几章」，往回翻一屏不必再取。再多意义不大：
         * 更远的章有单章接口与批量预取兜底。
         */
        const val CONTENT_CACHE_SIZE = 12

        /** 进度上报节流窗口，与前端 `readerProgressAutoSave` 一致。 */
        const val PROGRESS_SAVE_INTERVAL_MS = 10_000L
    }
}
