package org.example.reader.ui.reader

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.example.reader.core.settings.ReaderSettings
import kotlin.math.roundToInt

/** 连续阅读的窗口半径：当前章前后各 3 章，最多 7 章同时在窗口里。 */
private const val CONTINUOUS_WINDOW = 3

/**
 * 等待「某章正文到位 / 某个 item 出现在列表里」的上限。
 *
 * **必须有上限**：锚定协程一旦挂死，恢复就永远无法完成。超时后若当前章正文
 * 已可用，仍按最新 rows 做一次目标定位；无法定位或校验不确定则进入失败保护，
 * 保留当前视口，由用户实际拖动接管。
 */
private const val ANCHOR_WAIT_MS = 5_000L

/**
 * 等 VM 那条正文请求落地的时间上限。
 *
 * 只用于「VM 正在取这一章，别重复发请求」这个加速分支：等不到就自己取，
 * 因此宁可变短——等太久会让占位章多留一屏。
 */
private const val VM_REQUEST_WAIT_MS = 2_000L

/**
 * 定位的尝试次数。
 *
 * 第一次用的是当前 `itemsHolder` 快照算出的下标；若这一帧刚好有章节加载完成
 * （一章 = 几十项），那个下标已经过期，会落在前面若干章。用「按 key 校验 + 重试」
 * 把它纠回来；重试次数有界，网络挂了要能收手。
 */
private const val ANCHOR_ATTEMPTS = 3

/** 两次定位尝试之间的间隔：等列表把这一章拼进来（通常 1 帧就够）。 */
private const val ANCHOR_RETRY_DELAY_MS = 300L

/** 滚动之后等「目标 key 出现在视口首位」的上限。 */
private const val VISIBLE_WAIT_MS = 1_000L

/** 等「某个 item 出现在列表里」的上限：正文已到的话下一帧就该在。 */
private const val ITEM_WAIT_MS = 1_000L

/**
 * 连续阅读（上下滚动 / 上下滚动2）。
 *
 * 与 [ReaderContent]（只渲染当前章）的区别只有一个：正文是**一段连续拼接的列表**，
 * 当前章上下各有若干章在同一根 `LazyColumn` 里，滚过章末自然接下一章，不用点「下一章」。
 *
 * 三条决定实现形态的约束：
 * 1. **item key 必须带章节下标**（`h:<章>` / `p:<章>:<段>`）。窗口里上一章加载完成时，
 *    会往列表**头部**插入几十个 item，同一个 item 的下标整体平移；只有 key 稳定，
 *    `LazyListState` 才能把视口钉在原来那句话上，而不是整体跳一段。
 * 2. **`listState` 由外部传入**，不在这里 `rememberLazyListState()`：
 *    自动阅读（`ReaderScreen`）只认同一个 `listState`，换一个列表滚，画面不会动。
 * 3. **不挂整屏 tap**：九宫格点按由 `ReaderScreen` 的 `pointerInput` 统一处理，
 *    这里再挂一次会双触发（一次切控件、一次被当成翻屏）。
 *
 * 「上下滚动2」不复制渲染逻辑，只把 [hideReadChapters] 置真：不渲染已读章（章号小于当前章）。
 * 副作用是**向上滚不出当前章**——已读章不在列表里，也就没有可回退的项；
 * 想回看上一章要走目录或点按热区切章，这与「隐藏已读」的语义一致。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ContinuousReader(
    state: ReaderUiState,
    settings: ReaderSettings,
    palette: ReaderPalette,
    viewModel: ReaderViewModel,
    /** 与竖排/HorizontalReader 共用的列表状态：自动阅读只认它。 */
    listState: LazyListState,
    /** 点按切控件由 `ReaderScreen` 的 pointerInput 负责，本组件不使用，只为调用形状统一。 */
    onToggleControls: () -> Unit,
    /** 进入时的章内位置（0..1，段落比例）：续读 / 重进 / 切换翻页方式都靠它定位。 */
    restoreFraction: Float,
    /** 「上下滚动2」：不渲染已读章。 */
    hideReadChapters: Boolean,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading && state.chapters.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = palette.text)
        }
        return
    }
    if (state.chapters.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = state.error ?: "目录为空",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }

    val chapters = state.chapters
    // 协程里要读最新入参：这些 lambda 被长生命周期协程持有，直接捕获会拿到「首次组合」的旧值
    val stateRef = rememberUpdatedState(state)
    val hideReadRef = rememberUpdatedState(hideReadChapters)
    val restoreRef = rememberUpdatedState(restoreFraction)
    val tracker = remember { ContinuousTracker(initialChapter = state.currentIndex) }

    /** 已取到的正文（目录下标 → 段落）。这里是渲染的唯一内容源，VM 的 LRU 只是取数通道。 */
    val loaded = remember { mutableStateMapOf<Int, List<String>>() }

    /** 在途章节，避免同一章被「进入窗口」重复触发。 */
    val loading = remember { mutableStateMapOf<Int, Boolean>() }

    /** 取失败的章节：占位改成「加载失败」文案，滚到它附近会再试一次。 */
    val failed = remember { mutableStateMapOf<Int, Boolean>() }

    /** 是否已完成首次定位。定位前只从当前章往后画，避免先画上一章又被定位顶掉。 */
    var restored by remember { mutableStateOf(false) }

    /**
     * 当前 item 列表的快照。
     *
     * 只给协程读（按 key 找扁平下标）。写在组合里是因为列表本身就是组合的产物；
     * `mutableStateOf` 用结构相等，内容没变时这次赋值不会触发任何重组，
     * 也不存在「组合期间读到自己刚写的值」的回环。
     */
    val itemsHolder = remember { mutableStateOf<List<ContinuousItem>>(emptyList()) }

    // 外部跳章的判定必须**同步做在组合里**，不能放进 effect：
    // 进度回传与 effect 是两条协程，谁先跑没有保证；晚一步就会把「跳章后第一帧的旧视口」
    // 当成用户位置写回去，把刚跳到的章又改回来（随后又被当成一次新的跳章，来回拉锯）。
    // 组合先于布局，布局先于 layoutInfo 变化，所以在组合里立旗一定能拦住那次回传。
    val center = state.currentIndex
    if (tracker.expected != center) {
        tracker.requestRestore(center)
    }

    /** 窗口内需要钉住的章：它们正在屏幕上，不能被 VM 的 LRU 裁掉。 */
    fun pinnedWindow(): Set<Int> {
        val snapshot = stateRef.value
        val current = snapshot.currentIndex
        val from = (current - CONTINUOUS_WINDOW).coerceAtLeast(0)
        val to = (current + CONTINUOUS_WINDOW).coerceAtMost(snapshot.chapters.lastIndex)
        return if (from <= to) (from..to).toSet() else emptySet()
    }

    /** 等某章正文到位；超时返回 null（网络挂了不该把锚定协程永久挂住）。 */
    suspend fun awaitParagraphs(index: Int, timeoutMs: Long = ANCHOR_WAIT_MS): List<String>? =
        withTimeoutOrNull(timeoutMs) {
            snapshotFlow { loaded[index] }.first { it != null }
        }

    /** 取一章正文并落到 [loaded]。失败只记一笔，不抛、不弹错（阅读不该被网络打断）。 */
    suspend fun ensureChapter(index: Int) {
        val snapshot = stateRef.value
        if (index !in snapshot.chapters.indices) return
        // 上下滚动2：已读章既不渲染也不下载，否则等于白烧流量
        if (hideReadRef.value && index < snapshot.currentIndex) return
        if (loaded[index] != null || loading[index] == true) return
        loading[index] = true
        try {
            // VM 正在取的就是这一章（刚进书 / 目录跳章 / 刷新本章）：镜像 effect 会把它填进
            // loaded，这里再发一条就是同一章两条请求。等不到（VM 那条失败，或者此刻
            // isLoadingContent 指的是别的章）就自己取一次——**等待超时不等于加载失败**。
            if (stateRef.value.isLoadingContent && stateRef.value.currentIndex == index) {
                if (awaitParagraphs(index, timeoutMs = VM_REQUEST_WAIT_MS) != null) return
            }
            val content = viewModel.fetchChapterForContinuous(index, pinned = pinnedWindow())
            if (content == null) {
                failed[index] = true
                return
            }
            // 与 VM 用同一套切分规则，段落下标才能跨模块换算进度
            loaded[index] = splitParagraphs(content.content)
            failed.remove(index)
        } finally {
            loading.remove(index)
        }
    }

    /** 等某个 item key 出现在列表里，返回它的扁平下标；没等到返回 -1。 */
    suspend fun awaitItemIndex(key: String): Int =
        withTimeoutOrNull(ITEM_WAIT_MS) {
            snapshotFlow { itemsHolder.value.indexOfFirst { it.key == key } }.first { it >= 0 }
        } ?: -1

    /**
     * 等「扩窗后的新结构」真的量过一遍。
     *
     * 信号取 `itemsHolder` 的第一章：扩窗后它应当变成 `max(0, 当前章 - 3)`
     * （「上下滚动2」下仍是当前章，因为已读章不渲染）。组合产物变了说明新结构已提交，
     * 再等一帧让 measure 把下标落实——**之后算出的下标才是最终下标**。
     */
    suspend fun awaitExpandedWindow() {
        val snapshot = stateRef.value
        val current = snapshot.currentIndex
        val expectedFirst = if (hideReadRef.value) {
            current
        } else {
            (current - CONTINUOUS_WINDOW).coerceAtLeast(0)
        }
        withTimeoutOrNull(VISIBLE_WAIT_MS) {
            snapshotFlow { itemsHolder.value.firstOrNull()?.chapter }.first { it == expectedFirst }
        }
        withFrameNanos { }
    }

    /**
     * 尽量等本轮会扩成正文的前后排章全部进入终态，再计算恢复下标。
     *
     * 预取顺序是 center、+1、-1、+2、-2；只等首章扩窗时，-1/-2 仍可能在
     * `scrollToItem` 之后插入几十项。失败章保留单个 Pending，结构同样稳定，
     * 因而 loaded 与 failed 都是可接受的终态；loading 必须已经结束。超时返回 false，
     * 调用方仍可基于当前章正文做一次定位，不能把超时当成滚到章头的理由。
     */
    suspend fun awaitStablePrefetchWindow(chapter: Int): Boolean {
        val snapshot = stateRef.value
        val from = if (hideReadRef.value) chapter else (chapter - 2).coerceAtLeast(0)
        val to = (chapter + 2).coerceAtMost(snapshot.chapters.lastIndex)
        if (from > to) return false
        val stable = withTimeoutOrNull(ANCHOR_WAIT_MS) {
            snapshotFlow {
                (from..to).all { index -> loaded[index] != null || failed[index] == true } &&
                    (from..to).none { index -> loading[index] == true }
            }.first { it }
        } ?: return false
        // 终态先落到 composition，再等一帧让 rows/layout 采用同一份结构。
        withFrameNanos { }
        return stable
    }

    /**
     * 落位：[chapter] 的 [fraction] 处。成功、用户取消、真失败三种结果严格分流。
     *
     * 每轮都按**最新 rows**取下标，然后核对真实 `layoutInfo`：首个可见项必须是目标 key，
     * 它的 layout 下标必须等于最新 rows 下标，目标项也确实存在于本次 visibleItemsInfo，
     * 且 layout 的总项数与 rows 一致。这样不会把滚动前的瞬时 key 相等当成成功。
     *
     * `scrollToItem` 会清掉 Compose 记着的上一个可见 key，因此优先等窗口稳定；等待超时后
     * 调用方可用 [attempts] 限制为一次 best-effort 定位。任何失败都只进入进度保护并保留
     * 当前视口，不再把章头或全局首项当成兜底目标。用户拖动或新代际定位会立即使旧代际
     * 失效，旧协程不能随后再抢滚动权。
     */
    suspend fun anchorStable(
        chapter: Int,
        fraction: Float,
        paragraphs: List<String>,
        generation: Int,
        attempts: Int = ANCHOR_ATTEMPTS,
    ): ContinuousAnchorResult {
        val key = restoreAnchorKey(chapter, fraction, paragraphs.size)
        repeat(attempts.coerceAtLeast(1)) {
            if (!tracker.isRestoring(generation)) return ContinuousAnchorResult.USER_CANCELLED
            val target = awaitItemIndex(key)
            if (!tracker.isRestoring(generation)) return ContinuousAnchorResult.USER_CANCELLED
            if (target >= 0) {
                listState.scrollToItem(target)
                val anchoredLayout = withTimeoutOrNull(VISIBLE_WAIT_MS) {
                    snapshotFlow {
                        val layout = listState.layoutInfo
                        val visibleItems = layout.visibleItemsInfo
                        val first = firstContinuousViewportItem(
                            items = visibleItems,
                            viewportStart = layout.viewportStartOffset,
                        ) { it.offset }
                        val latestTarget = itemsHolder.value.indexOfFirst { it.key == key }
                        val targetItem = visibleItems.firstOrNull { it.key == key }
                        val matches = first?.key == key &&
                            latestTarget >= 0 &&
                            first.index == latestTarget &&
                            targetItem?.index == latestTarget &&
                            layout.totalItemsCount == itemsHolder.value.size
                        if (matches) key else null
                    }.first { it == key }
                }
                if (anchoredLayout == key && tracker.isRestoring(generation)) {
                    return ContinuousAnchorResult.ANCHORED
                }
            }
            if (!tracker.isRestoring(generation)) return ContinuousAnchorResult.USER_CANCELLED
            delay(ANCHOR_RETRY_DELAY_MS)
        }
        return if (tracker.isRestoring(generation)) {
            ContinuousAnchorResult.FAILED
        } else {
            ContinuousAnchorResult.USER_CANCELLED
        }
    }

    /** 定位失败只保护进度并保留当前视口；用户取消或新代际不得被旧结果覆盖。 */
    fun failRestore(generation: Int) {
        tracker.markAnchorFailed(generation)
    }

    // ─── 取章节奏 ──────────────────────────────────────────────────────────────
    // 当前章 → 前后各 1 章 → 前后各 2 章。不做「一次拉满 7 章」：首屏只为当前章
    // 及紧邻章付费，跨过章末那一屏才需要它们；更远的章留在窗口里当占位。
    // 顺序从近到远，「用户正在等的那一章」永远排在前面。
    LaunchedEffect(center, hideReadChapters, chapters.size) {
        if (center !in chapters.indices) return@LaunchedEffect
        ensureChapter(center)
        // 被切章打断时整个 effect 取消，新窗口的一组请求随即接手（见各自的 finally 清理）
        listOf(1, -1, 2, -2).forEach { delta -> ensureChapter(center + delta) }
    }

    // VM 手里的那一章（进入时的当前章 / 目录跳章 / 刷新本章）直接镜像过来，不必再取一次。
    // 独立 effect：它有自己的节拍，跟窗口预取互不阻塞。
    LaunchedEffect(state.contentIndex, state.paragraphs) {
        val index = state.contentIndex
        if (index < 0 || state.paragraphs.isEmpty()) return@LaunchedEffect
        loaded[index] = state.paragraphs
        failed.remove(index)
    }

    // ─── 定位 ──────────────────────────────────────────────────────────────────
    // 进入与外部跳章走同一条路。RESTORING 与 FAILED 都压住进度；ANCHORED 与
    // USER_TAKEN_OVER 才允许同步。拖动开始只取消本轮定位，不会直接放开旧快照。
    LaunchedEffect(center, chapters.size) {
        val jumpTarget = tracker.pendingAnchor
        if (jumpTarget == null && restored) return@LaunchedEffect
        if (chapters.isEmpty()) return@LaunchedEffect
        val chapter = jumpTarget ?: center
        val fraction = if (jumpTarget == null) restoreRef.value else viewModel.currentFraction()
        val generation = if (jumpTarget == null) tracker.beginRestore() else tracker.generation
        tracker.pendingAnchor = null
        // 先扩窗，再尽量等本轮 ±2 预取章全部 loaded/failed，最后按最新 rows 计算下标。
        // 稳定等待超时不等于当前章无法恢复：正文已可用时仍做一次 best-effort 定位，
        // 但不反复抢滚动；任何真失败都只保护进度，绝不主动把视口滚到章头。
        restored = true
        awaitExpandedWindow()
        if (!tracker.isRestoring(generation)) return@LaunchedEffect
        val prefetchStable = awaitStablePrefetchWindow(chapter)
        if (!tracker.isRestoring(generation)) return@LaunchedEffect
        val paragraphs = awaitParagraphs(chapter)
        if (paragraphs == null) {
            failRestore(generation)
            return@LaunchedEffect
        }
        when (
            anchorStable(
                chapter = chapter,
                fraction = fraction,
                paragraphs = paragraphs,
                generation = generation,
                attempts = if (prefetchStable) ANCHOR_ATTEMPTS else 1,
            )
        ) {
            ContinuousAnchorResult.ANCHORED -> {
                // 若 DragStart 已抢先发生，成功结果也属于旧代际，绝不能覆盖用户接管。
                if (!tracker.markAnchorSucceeded(generation, chapter)) return@LaunchedEffect
                viewModel.consumePendingScroll()
                // 补一次上报：定位期间的回传都被丢弃了，而快照流有去重、不会重吐同一个值。
                val anchored = parseContinuousKey(
                    restoreAnchorKey(chapter, fraction, paragraphs.size),
                )?.second ?: 0
                val position = if (paragraphs.isEmpty()) 0f else anchored.toFloat() / paragraphs.size
                viewModel.syncContinuousPosition(chapter, position)
            }

            ContinuousAnchorResult.FAILED -> failRestore(generation)
            ContinuousAnchorResult.USER_CANCELLED -> Unit
        }
    }

    /** 当前真实 layoutInfo 的可视位置；key、下标、偏移共同识别一次实际位移。 */
    fun currentVisiblePosition(): ContinuousVisiblePosition {
        val layout = listState.layoutInfo
        val first = firstContinuousViewportItem(
            items = layout.visibleItemsInfo,
            viewportStart = layout.viewportStartOffset,
        ) { it.offset }
        return ContinuousVisiblePosition(
            key = first?.key as? String,
            index = first?.index ?: -1,
            offset = first?.offset ?: 0,
        )
    }

    // DragStart 只进入 DRAG_PENDING 并记录起点；尚不能把定位前旧快照写库。
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                tracker.onDragStart(currentVisiblePosition())
            }
        }
    }

    // ─── 进度回传 / 用户接管 ───────────────────────────────────────────────────
    // 依据是首个可见 item 的稳定 key，段数一并纳入快照以感知 Pending → 正文。
    // offset 也纳入快照：同一 paragraph 内的真实拖动同样能证明用户已经接管。
    LaunchedEffect(listState) {
        snapshotFlow {
            val position = currentVisiblePosition()
            val chapter = position.key?.let { parseContinuousKey(it)?.first }
            ContinuousPositionSample(
                position = position,
                total = chapter?.let { loaded[it]?.size } ?: 0,
            )
        }.collect { sample ->
            val key = sample.position.key ?: return@collect
            val (chapter, paragraph) = parseContinuousKey(key) ?: return@collect
            // snapshotFlow 可能投递排队中的旧值；位置与段数都必须仍和当前状态一致。
            val currentPosition = currentVisiblePosition()
            if (sample.position != currentPosition || sample.total != (loaded[chapter]?.size ?: 0)) {
                return@collect
            }
            if (tracker.phase == ContinuousRestorePhase.DRAG_PENDING) {
                // 只有拖动后出现了新的、真实的可见位置才解除抑制；DragStart 本身不算。
                if (sample.total <= 0 || !listState.isScrollInProgress) return@collect
                if (!tracker.completeUserTakeOver(
                        observed = sample.position,
                        current = currentPosition,
                        chapter = chapter,
                        isScrollInProgress = true,
                    )
                ) {
                    return@collect
                }
                viewModel.consumePendingScroll()
            } else {
                if (tracker.suppress || !restored) return@collect
            }
            // 段数未知的占位章不是合法阅读位置；FAILED 在上方持续抑制旧位置写库。
            if (sample.total <= 0) return@collect
            val fraction = (paragraph.toFloat() / sample.total).coerceIn(0f, 1f)
            // ANCHORED / USER_TAKEN_OVER 下允许正常前进与后退，不按方向封禁进度。
            tracker.expected = chapter
            viewModel.syncContinuousPosition(chapter, fraction)
        }
    }

    // 首次定位完成前只从当前章往后画；否则先画的上一章会被紧接着的定位顶掉，闪一下
    val lowerBound = if (restored || hideReadChapters) {
        (center - CONTINUOUS_WINDOW).coerceAtLeast(0)
    } else {
        center
    }
    val upperBound = (center + CONTINUOUS_WINDOW).coerceAtMost(chapters.lastIndex)
    // 窗口 → 扁平列表：章头 + 段落；未取到的章只放一个占位项（占一屏）
    val rows = mutableListOf<ContinuousItem>()
    if (lowerBound <= upperBound) {
        for (chapter in lowerBound..upperBound) {
            if (hideReadChapters && chapter < center) continue
            val paragraphs = loaded[chapter]
            val title = chapters[chapter].title
            if (paragraphs == null) {
                rows += ContinuousItem.Pending(chapter, title, failed[chapter] == true)
            } else {
                rows += ContinuousItem.Header(chapter, title)
                paragraphs.forEachIndexed { index, text ->
                    rows += ContinuousItem.Body(chapter, index, text)
                }
            }
        }
    }
    itemsHolder.value = rows

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val paragraphGap = with(LocalDensity.current) { settings.paragraphGapSp.sp.toDp() }
    val titleStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = (settings.fontSize + 4).sp,
        color = palette.text,
    )
    val bodyStyle = TextStyle(
        fontSize = settings.fontSize.sp,
        lineHeight = settings.textLineHeightSp.sp,
        color = palette.text,
        // 首行缩进两个字宽，与单章竖排 / 横翻保持一致
        textIndent = TextIndent(firstLine = (settings.fontSize * 2).sp),
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        // 占位高度取「一屏」：占位章在列表里占掉的体量与真实章接近，
        // 加载完成时列表不会因为差异过大而整体抖动，滚动位置也不会疯狂跳。
        val placeholderHeight = maxHeight.coerceIn(MIN_PLACEHOLDER_HEIGHT, MAX_PLACEHOLDER_HEIGHT)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = topInset + 16.dp,
                bottom = bottomInset + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(paragraphGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items = rows, key = { item -> item.key }) { item ->
                when (item) {
                    is ContinuousItem.Header -> Text(
                        text = item.title,
                        style = titleStyle,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    is ContinuousItem.Body -> Text(text = item.text, style = bodyStyle)

                    is ContinuousItem.Pending -> Column(
                        modifier = Modifier.fillMaxWidth().height(placeholderHeight),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = item.title, style = titleStyle, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (item.failed) {
                                "第 ${item.chapter + 1} 章加载失败，回到本章会重试"
                            } else {
                                "正在加载第 ${item.chapter + 1} 章…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.secondaryText,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** 一次定位尝试的最终结果；用户取消绝不能走失败兜底。 */
private enum class ContinuousAnchorResult {
    ANCHORED,
    USER_CANCELLED,
    FAILED,
}

/** 连续阅读恢复状态机。只有 ANCHORED / USER_TAKEN_OVER 允许写阅读位置。 */
internal enum class ContinuousRestorePhase {
    RESTORING,
    ANCHORED,
    DRAG_PENDING,
    USER_TAKEN_OVER,
    FAILED,
}

/** 来自真实 LazyListLayoutInfo 的首个可见位置，而不是协程排队时的旧 key。 */
internal data class ContinuousVisiblePosition(
    val key: String?,
    val index: Int,
    val offset: Int,
)

/** [position] 与其所属章段数的一份原子采样。 */
private data class ContinuousPositionSample(
    val position: ContinuousVisiblePosition,
    val total: Int,
)

/**
 * 连续阅读的跨协程协调状态。**故意不是 Compose 状态**，组合里必须能同步读写。
 *
 * 每次恢复都有单调递增的代际：外部跳章立即使旧协程失效；成功与失败都只允许
 * 作用在同代 RESTORING 上，因而成功后的迟到失败不能覆盖成功态，DragStart 后的
 * 迟到成功也不能覆盖用户。FAILED 只抑制进度、不移动视口；DRAG_PENDING 必须观察到
 * 拖动后的新 layout 位置才能离开。
 */
internal class ContinuousTracker(initialChapter: Int) {
    /** 最近一次已知的当前章：进度回传与外部跳章判定共同维护。 */
    var expected: Int = initialChapter

    /** 待处理的外部跳章目标；null 表示没有待处理跳章。 */
    var pendingAnchor: Int? = null

    /** 当前恢复代际，只增不减。 */
    var generation: Int = 0
        private set

    /** 当前状态；外部只能通过下面的转换方法修改。 */
    var phase: ContinuousRestorePhase = ContinuousRestorePhase.RESTORING
        private set

    private var dragStart: ContinuousVisiblePosition? = null
    private var dragRequiresKeyChange: Boolean = false

    /** 失败保护与定位/拖动待确认都禁止同步，避免把恢复前旧快照写入数据库。 */
    val suppress: Boolean
        get() = phase != ContinuousRestorePhase.ANCHORED &&
            phase != ContinuousRestorePhase.USER_TAKEN_OVER

    /** 外部跳章在组合里同步失效旧代际，不能等 LaunchedEffect 才压住回传。 */
    fun requestRestore(chapter: Int) {
        expected = chapter
        pendingAnchor = chapter
        generation += 1
        phase = ContinuousRestorePhase.RESTORING
        dragStart = null
        dragRequiresKeyChange = false
    }

    /** 开始首次恢复并返回本轮代际。 */
    fun beginRestore(): Int {
        generation += 1
        phase = ContinuousRestorePhase.RESTORING
        dragStart = null
        dragRequiresKeyChange = false
        return generation
    }

    /** 只有同代且未被用户接管时，定位协程才仍拥有滚动权。 */
    fun isRestoring(token: Int): Boolean =
        token == generation && phase == ContinuousRestorePhase.RESTORING

    /** 成功是同代 RESTORING 的终态；之后同代失败/成功结果都不能再改写它。 */
    fun markAnchorSucceeded(token: Int, chapter: Int): Boolean {
        if (!isRestoring(token)) return false
        expected = chapter
        phase = ContinuousRestorePhase.ANCHORED
        dragStart = null
        dragRequiresKeyChange = false
        return true
    }

    /** 真失败进入保护态并保持当前视口；用户取消不会调用这个转换。 */
    fun markAnchorFailed(token: Int): Boolean {
        if (!isRestoring(token)) return false
        phase = ContinuousRestorePhase.FAILED
        dragStart = null
        dragRequiresKeyChange = false
        return true
    }

    /** 记录拖动起点和当前代际，但不解除抑制。 */
    fun onDragStart(position: ContinuousVisiblePosition) {
        if (phase != ContinuousRestorePhase.RESTORING && phase != ContinuousRestorePhase.FAILED) return
        dragRequiresKeyChange = phase == ContinuousRestorePhase.FAILED
        dragStart = position
        phase = ContinuousRestorePhase.DRAG_PENDING
    }

    /**
     * 用户实际滚动后接管：采样必须与当前 layout 一致、相对拖动起点有变化且仍在滚动。
     * 从失败保护接管时还要求 key 改变，避免仅调整同一项的像素偏移就写回失败前旧位置。
     */
    fun completeUserTakeOver(
        observed: ContinuousVisiblePosition,
        current: ContinuousVisiblePosition,
        chapter: Int,
        isScrollInProgress: Boolean,
    ): Boolean {
        if (phase != ContinuousRestorePhase.DRAG_PENDING || !isScrollInProgress) return false
        val start = dragStart ?: return false
        if (observed.key == null || observed != current || observed == start) return false
        if (dragRequiresKeyChange && observed.key == start.key) return false
        expected = chapter
        phase = ContinuousRestorePhase.USER_TAKEN_OVER
        dragStart = null
        dragRequiresKeyChange = false
        return true
    }
}

/** 占位章最小高度：矮窗（分屏/横屏）下也要能一眼看出「这是一整章的空位」。 */
private val MIN_PLACEHOLDER_HEIGHT: Dp = 240.dp

/** 占位章最大高度：折叠屏展开 / 平板竖屏下别让占位明显超过一屏。 */
private val MAX_PLACEHOLDER_HEIGHT: Dp = 1200.dp

/**
 * 连续阅读列表的 item 模型。
 *
 * `key` 是稳定身份，且**带章节下标**：`h:<章>`（章头 / 占位）与 `p:<章>:<段>`。
 * 占位与章头共用 `h:<章>` 是有意的——正文到位时占位被章头**原地替换**（同 key），
 * 不会因为「换了一个 item」而让列表把这段当成新增内容重排。
 */
internal sealed interface ContinuousItem {
    val key: String
    val chapter: Int

    /** 章头：本章标题。 */
    data class Header(override val chapter: Int, val title: String) : ContinuousItem {
        override val key: String get() = headerKey(chapter)
    }

    /** 正文段落。[paragraphIndex] 是该段在本章内的下标（进度换算的唯一依据）。 */
    data class Body(
        override val chapter: Int,
        val paragraphIndex: Int,
        val text: String,
    ) : ContinuousItem {
        override val key: String get() = paragraphKey(chapter, paragraphIndex)
    }

    /** 未取到正文的占位章：固定占一屏，文案告知第几章。 */
    data class Pending(
        override val chapter: Int,
        val title: String,
        val failed: Boolean,
    ) : ContinuousItem {
        override val key: String get() = headerKey(chapter)
    }
}

private fun headerKey(chapter: Int): String = "h:$chapter"

private fun paragraphKey(chapter: Int, paragraph: Int): String = "p:$chapter:$paragraph"

/**
 * item key → （章下标, 段落下标）。
 *
 * key 只由本文件生成，格式固定；认不出来就返回 null（对未知项静默跳过，
 * 不要把进度写到一个猜出来的章上）。
 */
private fun parseContinuousKey(key: String): Pair<Int, Int>? = when {
    key.startsWith("h:") -> key.removePrefix("h:").toIntOrNull()?.let { it to 0 }

    key.startsWith("p:") -> {
        val parts = key.split(':')
        val chapter = parts.getOrNull(1)?.toIntOrNull()
        val paragraph = parts.getOrNull(2)?.toIntOrNull()
        if (chapter != null && paragraph != null) chapter to paragraph else null
    }

    else -> null
}

/**
 * 正文视口的首个位置：只排除顶部位于 [viewportStart] 之前的项。
 *
 * LazyList 的 item offset 与 viewportStartOffset 使用同一坐标系，后者包含内容 padding
 * 的边界语义。不要检查 item 底边，否则一个超长段落可能永远没有合法候选。
 */
internal fun <T> firstContinuousViewportItem(
    items: List<T>,
    viewportStart: Int,
    offsetOf: (T) -> Int,
): T? = items.firstOrNull { offsetOf(it) >= viewportStart }

/**
 * 连续阅读切到单章分页前，从正文视口首个位置计算当前章内位置。
 *
 * 设置变更与最后一次 LazyList 快照回传可能同帧交错，不能只依赖尚未刷新的 VM 比例。
 * 仅接受当前章的合法段落 key；章头等价于第 0 段，其他章或未知 key 不改写位置。
 */
internal fun continuousFractionForModeSwitch(
    key: String?,
    currentChapter: Int,
    totalParagraphs: Int,
): Float? {
    if (key == null || totalParagraphs <= 0) return null
    val (chapter, paragraph) = parseContinuousKey(key) ?: return null
    if (chapter != currentChapter || paragraph !in 0 until totalParagraphs) return null
    return paragraph.toFloat() / totalParagraphs
}

/**
 * 章内比例（0..1）→ 段落下标，**四舍五入**。
 *
 * 与上报口径必须严格互逆：上报是「段落下标 / 段数」，恢复是「比例 × 段数」。
 * 用四舍五入而不是截断：比例要经 `× 10000` 取整再回来（`durChapterPos`），
 * 截断会把 3/7 这类值算回 2，退出重进就退一段。
 *
 * 公式与 `HorizontalReader.kt` 的 `paragraphAtFraction` 相同（含上界取段数，
 * 好让 `1f` 能落到末段）。那边是文件私有实现，本轮不改那个文件，故此处独写一份；
 * 两边口径必须一起改。
 */
private fun paragraphAtFraction(fraction: Float, totalParagraphs: Int): Int =
    if (totalParagraphs <= 0) {
        0
    } else {
        (fraction.coerceIn(0f, 1f) * totalParagraphs)
            .roundToInt()
            .coerceIn(0, totalParagraphs)
    }

/** 定位用的 item key：非章首取段落项，章首取章头。 */
private fun restoreAnchorKey(chapter: Int, fraction: Float, totalParagraphs: Int): String {
    if (totalParagraphs <= 0 || fraction <= 0.001f) return headerKey(chapter)
    val paragraph = paragraphAtFraction(fraction, totalParagraphs)
        .coerceIn(0, totalParagraphs - 1)
    return paragraphKey(chapter, paragraph)
}
