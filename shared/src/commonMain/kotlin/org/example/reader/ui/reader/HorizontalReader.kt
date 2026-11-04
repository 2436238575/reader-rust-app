package org.example.reader.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.example.reader.core.settings.ReaderSettings
import kotlin.math.roundToInt

/**
 * 左右翻页。
 *
 * 分页方式：按段落整体装箱——逐段量高，装不下就换页，不在段落中间切。
 * 中文段落短（通常 1–4 行），整段装箱与逐字分页肉眼差别很小，
 * 换来的是不必处理「一行字被拆到两页」的边界。
 *
 * 交互：
 * - 横滑翻页
 * - 点左侧 1/3 上一页（首页切上一章），点右侧 1/3 下一页（末页切下一章），
 *   点中间呼出/收起控件
 *
 * 进度口径（**唯一**）：页首段落 / 全章段数。上报与恢复共用
 * [pageStartFraction] 与 [paragraphAtFraction] 这一对互逆函数——
 * 只要两端点不同源，「退出重进」就会稳定偏移若干页（A4 就是这么来的）。
 *
 * 正文只避开系统栏：工具条是可收起的覆盖层，不为它预留高度，
 * 收起控件后整页都可见，与竖排模式一致。
 */
@Composable
fun HorizontalReader(
    state: ReaderUiState,
    settings: ReaderSettings,
    palette: ReaderPalette,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onToggleControls: () -> Unit,
    onProgress: (Float) -> Unit,
    onRestoreConsumed: () -> Unit,
    /** 进入时的章内位置（0..1）：续读、重进、切换翻页方式都靠它定位。 */
    restoreFraction: Float,
    modifier: Modifier = Modifier,
) {
    // 正文必须与当前章对齐：换章瞬间 paragraphs 还是上一章的
    val contentReady = state.paragraphs.isNotEmpty() && state.contentIndex == state.currentIndex

    if (state.isLoading && state.paragraphs.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = palette.text)
        }
        return
    }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val titleStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = (settings.fontSize + 4).sp,
        lineHeight = ((settings.fontSize + 4) * 1.4f).sp,
        color = palette.text,
    )
    val bodyStyle = TextStyle(
        fontSize = settings.fontSize.sp,
        lineHeight = settings.textLineHeightSp.sp,
        color = palette.text,
        // 首行缩进两个汉字宽，与上下滑动模式保持一致
        textIndent = TextIndent(firstLine = (settings.fontSize * 2).sp),
    )

    BoxWithConstraints(modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset)) {
        val horizontal = 20.dp
        val vertical = 16.dp
        val widthPx = with(density) { (maxWidth - horizontal * 2).roundToPx() }
        val heightPx = with(density) { (maxHeight - vertical * 2).roundToPx() }
        val gapPx = with(density) { settings.paragraphGapSp.sp.toPx() }.toInt()
        val gapDp = with(density) { settings.paragraphGapSp.sp.toDp() }

        // 尺寸还没量出来时不分页，否则会先出一份错误分页再重排
        val pages = if (widthPx <= 0 || heightPx <= 0) {
            emptyList()
        } else {
            remember(state.paragraphs, state.currentChapter?.title, widthPx, heightPx, gapPx, titleStyle, bodyStyle) {
                paginate(
                    measurer = measurer,
                    title = state.currentChapter?.title.orEmpty(),
                    titleStyle = titleStyle,
                    paragraphs = state.paragraphs,
                    bodyStyle = bodyStyle,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    gapPx = gapPx,
                )
            }
        }

        when {
            !contentReady -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isLoadingContent || state.isLoading) {
                    CircularProgressIndicator(color = palette.secondaryText)
                } else {
                    Text(
                        text = state.error ?: "正文为空",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            pages.isEmpty() && state.isLoadingContent -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = palette.secondaryText)
            }

            pages.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.error ?: "正文为空",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // 以「正文所属章」为 key：换章时重建 PagerState，避免上一章残留的半页偏移
            else -> key(state.contentIndex) {
                val pagerState = rememberPagerState(pageCount = { pages.size })
                PagedContent(
                    pagerState = pagerState,
                    pages = pages,
                    state = state,
                    palette = palette,
                    titleStyle = titleStyle,
                    bodyStyle = bodyStyle,
                    gapDp = gapDp,
                    horizontal = horizontal,
                    vertical = vertical,
                    restoreFraction = restoreFraction,
                    onPrevChapter = onPrevChapter,
                    onNextChapter = onNextChapter,
                    onToggleControls = onToggleControls,
                    onProgress = onProgress,
                    onRestoreConsumed = onRestoreConsumed,
                )
            }
        }
    }
}

@Composable
private fun PagedContent(
    pagerState: PagerState,
    pages: List<ReaderPage>,
    state: ReaderUiState,
    palette: ReaderPalette,
    titleStyle: TextStyle,
    bodyStyle: TextStyle,
    gapDp: Dp,
    horizontal: Dp,
    vertical: Dp,
    restoreFraction: Float,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onToggleControls: () -> Unit,
    onProgress: (Float) -> Unit,
    onRestoreConsumed: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // 进入本页时定位一次：比例 → 段落 → 它所在的页
    LaunchedEffect(Unit) {
        val target = if (restoreFraction > 0.001f) {
            val targetParagraph = paragraphAtFraction(restoreFraction, state.paragraphs.size)
            pages.indexOfLast { it.startParagraph <= targetParagraph }.coerceIn(0, pages.lastIndex)
        } else {
            0
        }
        pagerState.scrollToPage(target)
        if (restoreFraction > 0.001f) onRestoreConsumed()
    }

    // 翻页即进度：按本页**页首**段落对应的比例，与恢复时是同一个端点。
    //
    // 这里必须与 [pageStartFraction] / [paragraphAtFraction] 的映射严格互逆：
    // 旧实现上报页尾（`startParagraph + blocks.size`）、恢复却按页首落页，
    // 于是「退出 → 重进」每次都前进一页（A4，末页除外的假象来自末页没有下一页可进）。
    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.currentPage }.collect { index ->
            val page = pages.getOrNull(index) ?: return@collect
            val total = state.paragraphs.size
            if (total > 0) onProgress(pageStartFraction(page.startParagraph, total))
        }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
        val page = pages[pageIndex]
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontal, vertical = vertical)
                .pointerInput(pageIndex, pages.size, state.currentIndex) {
                    detectTapGestures { offset ->
                        val third = size.width / 3f
                        when {
                            offset.x < third -> if (pageIndex == 0) {
                                onPrevChapter()
                            } else {
                                scope.launch { pagerState.animateScrollToPage(pageIndex - 1) }
                            }

                            offset.x > size.width - third -> if (pageIndex == pages.lastIndex) {
                                onNextChapter()
                            } else {
                                scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                            }

                            else -> onToggleControls()
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(gapDp),
        ) {
            if (page.showTitle && page.blocks.isNotEmpty()) {
                Text(
                    text = state.currentChapter?.title.orEmpty(),
                    style = titleStyle,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            page.blocks.forEach { paragraph ->
                Text(text = paragraph, style = bodyStyle)
            }
        }
    }

    if (state.isLoadingContent) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.padding(4.dp),
                strokeWidth = 2.dp,
                color = palette.secondaryText,
            )
        }
    }
}

/** 每页装哪些段落。`showTitle` 只让本章第一页带标题。 */
private data class ReaderPage(
    val blocks: List<String>,
    val showTitle: Boolean,
    /**
     * 本页**第一段**在整章里的下标：进度与恢复的唯一基准。
     *
     * 页尾（`startParagraph + blocks.size`）**不是**可用的位置端点——它同时是下一页的页首，
     * 按页尾存、按页首读会让每次「退出重进」前进一页（A4）。
     */
    val startParagraph: Int,
)

/**
 * 段落下标 → 章内比例（0..1），**页首口径**。
 *
 * 与 [paragraphAtFraction] 严格互逆：`paragraphAtFraction(pageStartFraction(p, total), total) == p`。
 * 上报进度（页首）与恢复位置（比例 → 段落）必须共用这一对函数，否则端点定义会再次分叉。
 */
private fun pageStartFraction(startParagraph: Int, totalParagraphs: Int): Float =
    if (totalParagraphs <= 0) 0f else (startParagraph.toFloat() / totalParagraphs).coerceIn(0f, 1f)

/**
 * 章内比例（0..1）→ 段落下标，[pageStartFraction] 的逆运算。
 *
 * 用**四舍五入**而不是截断：比例在服务端要经 `× 10000` 取整再回来
 * （`durChapterPos`），截断会把 3/7 这类值算回 2，导致恢复时反而退一页。
 * 上界取 `totalParagraphs`（而不是 `total - 1`）：`1f`（「上一章」落章末）要能取到末页。
 */
private fun paragraphAtFraction(fraction: Float, totalParagraphs: Int): Int =
    if (totalParagraphs <= 0) {
        0
    } else {
        (fraction.coerceIn(0f, 1f) * totalParagraphs)
            .roundToInt()
            .coerceIn(0, totalParagraphs)
    }

/**
 * 按段落装箱分页。
 *
 * 逐段用 [TextMeasurer] 量高，累计超过可用高度就换页；
 * 单段本身就超过一页时独占一页（不切段，保证首行缩进与断行不被破坏）。
 */
private fun paginate(
    measurer: TextMeasurer,
    title: String,
    titleStyle: TextStyle,
    paragraphs: List<String>,
    bodyStyle: TextStyle,
    widthPx: Int,
    heightPx: Int,
    gapPx: Int,
): List<ReaderPage> {
    if (widthPx <= 0 || heightPx <= 0) return emptyList()

    val titleHeight = if (title.isBlank()) {
        0
    } else {
        measurer.measure(
            text = AnnotatedString(title),
            style = titleStyle,
            constraints = Constraints(maxWidth = widthPx),
        ).size.height + gapPx * 3
    }

    val pages = mutableListOf<ReaderPage>()
    var current = mutableListOf<String>()
    var used = titleHeight
    var first = true
    // 已装进前面各页的段数，用于记录每页的起始段落下标
    var consumed = 0

    fun flush() {
        if (current.isNotEmpty() || first) {
            pages += ReaderPage(blocks = current.toList(), showTitle = first, startParagraph = consumed)
            consumed += current.size
        }
        current = mutableListOf()
        used = 0
        first = false
    }

    paragraphs.forEach { paragraph ->
        val height = measurer.measure(
            text = AnnotatedString(paragraph),
            style = bodyStyle,
            constraints = Constraints(maxWidth = widthPx),
        ).size.height
        val need = if (current.isEmpty()) height else height + gapPx
        if (current.isNotEmpty() && used + need > heightPx) {
            flush()
        }
        current += paragraph
        used += if (current.size == 1) height else height + gapPx
    }
    if (current.isNotEmpty()) flush()
    return pages.ifEmpty { listOf(ReaderPage(blocks = emptyList(), showTitle = true, startParagraph = 0)) }
}
