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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import kotlinx.coroutines.launch
import org.example.reader.core.settings.ClickAction
import org.example.reader.core.settings.ReaderSettings

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
 * [blockFraction] 与 [blockAtFraction] 这一对互逆函数——
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
    imageLoader: ImageLoader,
    onImageClick: (String) -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onToggleControls: () -> Unit,
    onProgress: (Float) -> Unit,
    onRestoreConsumed: () -> Unit,
    /** 进入时的章内位置（0..1）：续读、重进、切换翻页方式都靠它定位。 */
    restoreFraction: Float,
    modifier: Modifier = Modifier,
) {
    // 正文必须与当前章对齐：换章瞬间快照还是上一章的
    val contentReady = state.blocks.isNotEmpty() && state.contentIndex == state.currentIndex
    // 同一章停留期间跨全部分页代际保留逻辑块锚。真实翻页会推进它；恢复本身不会。
    val restoreSession = remember(state.currentIndex, state.contentIndex) {
        HorizontalRestoreSession(restoreFraction)
    }

    if (state.isLoading && state.blocks.isEmpty()) {
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
    val lineMetrics = ReaderLineMetrics.from(settings)
    val bodyStyle = lineMetrics.bodyStyle(palette.text)

    BoxWithConstraints(modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset)) {
        val horizontal = 20.dp
        val vertical = 16.dp
        val widthPx = with(density) { (maxWidth - horizontal * 2).roundToPx() }
        val heightPx = with(density) { (maxHeight - vertical * 2).roundToPx() }
        val gapPx = lineMetrics.paragraphGapPx(density)
        val gapDp = lineMetrics.paragraphGapDp(density)
        val captionGapPx = with(density) { IMAGE_CAPTION_GAP_DP.dp.roundToPx() }
        val snapshot = state.body
        val layoutGeneration = snapshot?.let {
            HorizontalLayoutGeneration(
                snapshot = it,
                widthPx = widthPx,
                heightPx = heightPx,
                lineMetrics = lineMetrics,
                title = state.currentChapter?.title.orEmpty(),
                titleStyle = titleStyle,
            )
        }

        // 内容、尺寸、正文 metrics 或标题测量输入变化都形成新代际，不能只 clamp 旧页码。
        val pages = if (layoutGeneration == null || widthPx <= 0 || heightPx <= 0) {
            emptyList()
        } else {
            remember(layoutGeneration) {
                paginateBlocks(
                    blocks = layoutGeneration.snapshot.blocks,
                    titleHeightPx = measureChapterTitle(
                        measurer = measurer,
                        title = layoutGeneration.title,
                        titleStyle = layoutGeneration.titleStyle,
                        widthPx = widthPx,
                        gapPx = gapPx,
                    ),
                    widthPx = widthPx,
                    heightPx = heightPx,
                    gapPx = gapPx,
                ) { block ->
                    measureChapterBlock(measurer, block, bodyStyle, widthPx, captionGapPx)
                }
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

            // 每一布局代际重建 PagerState 与恢复闸门，旧代际页码/半页偏移不得复用。
            else -> key(layoutGeneration) {
                val pagerState = rememberPagerState(pageCount = { pages.size })
                PagedContent(
                    pagerState = pagerState,
                    pages = pages,
                    layoutGeneration = requireNotNull(layoutGeneration),
                    state = state,
                    clickAction = settings.clickAction,
                    palette = palette,
                    imageLoader = imageLoader,
                    onImageClick = onImageClick,
                    titleStyle = titleStyle,
                    bodyStyle = bodyStyle,
                    gapDp = gapDp,
                    horizontal = horizontal,
                    vertical = vertical,
                    restoreSession = restoreSession,
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

/** 横翻布局代际：任一测量输入变化都必须重新定位，而不是沿用旧 currentPage。 */
private data class HorizontalLayoutGeneration(
    val snapshot: ChapterBlockSnapshot,
    val widthPx: Int,
    val heightPx: Int,
    val lineMetrics: ReaderLineMetrics,
    val title: String,
    val titleStyle: TextStyle,
)

@Composable
private fun PagedContent(
    pagerState: PagerState,
    pages: List<ReaderPage>,
    layoutGeneration: HorizontalLayoutGeneration,
    state: ReaderUiState,
    clickAction: String,
    palette: ReaderPalette,
    imageLoader: ImageLoader,
    onImageClick: (String) -> Unit,
    titleStyle: TextStyle,
    bodyStyle: TextStyle,
    gapDp: Dp,
    horizontal: Dp,
    vertical: Dp,
    restoreSession: HorizontalRestoreSession,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onToggleControls: () -> Unit,
    onProgress: (Float) -> Unit,
    onRestoreConsumed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 与竖排一致：档位改动要立刻生效，不靠重进阅读器。
    val currentClickAction by rememberUpdatedState(clickAction)
    // null 表示恢复尚未完成。普通恢复后的目标页只作为基线，不向 VM 上报；
    // 章末 1f 是唯一例外：按既有语义归一到真实末页页首，且归一后锚已小于 1f，
    // 后续排版代际不会重复回写，更不会形成普通 mid-page 的累计向前漂移。
    var settledPage by remember { mutableStateOf<Int?>(null) }

    // 每个布局代际都从同一个跨代际逻辑锚定位：比例 → 逻辑块 → 它所在的新页。
    LaunchedEffect(layoutGeneration) {
        val restoreFraction = restoreSession.anchorFraction
        val restore = restoreSession.restoreTarget(pages, layoutGeneration.snapshot.blocks.size)
        pagerState.scrollToPage(restore.pageIndex)
        settledPage = restore.pageIndex
        // 先建立采集基线，再提交章末的一次性归一；普通恢复 normalizedFraction=null。
        restore.normalizedFraction?.let { normalized ->
            restoreSession.completeRestore(restore)
            onProgress(normalized)
        }
        if (restoreFraction > 0.001f) onRestoreConsumed()
    }

    // 翻页即进度：按本页**页首**段落对应的比例，与恢复时是同一个端点。
    //
    // 这里必须与 [blockFraction] / [blockAtFraction] 的映射严格互逆：
    // 旧实现上报页尾（`startBlockIndex + blocks.size`）、恢复却按页首落页，
    // 于是「退出 → 重进」每次都前进一页（A4，末页除外的假象来自末页没有下一页可进）。
    // scrollToPage 完成前，PagerState 默认的 currentPage == 0 不是真实阅读位置，
    // 必须整体屏蔽；落在恢复目标页也不上报。只有翻离已稳定页才代表真实阅读位移。
    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.currentPage to settledPage }.collect { (index, baseline) ->
            val previous = baseline ?: return@collect
            if (index == previous) return@collect
            val page = pages.getOrNull(index) ?: return@collect
            val total = state.blocks.size
            if (total > 0) {
                settledPage = index
                onProgress(restoreSession.onUserPage(page.startBlockIndex, total))
            }
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
                        // 与竖排同一套九宫格（左列上一页 / 右列下一页 / 中间列切控件），
                        // 四边 4% 留白不触发；不要回到「单行三等分」的旧口径。
                        when (horizontalTapZone(offset, size)) {
                            ReaderTapZone.Menu -> onToggleControls()

                            ReaderTapZone.Prev -> if (currentClickAction != ClickAction.NONE) {
                                if (pageIndex == 0) {
                                    if (currentClickAction == ClickAction.AUTO) onPrevChapter()
                                } else {
                                    scope.launch { pagerState.animateScrollToPage(pageIndex - 1) }
                                }
                            }

                            ReaderTapZone.Next -> if (currentClickAction != ClickAction.NONE) {
                                if (pageIndex == pages.lastIndex) {
                                    if (currentClickAction == ClickAction.AUTO) onNextChapter()
                                } else {
                                    scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                                }
                            }

                            ReaderTapZone.Ignore -> Unit
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
            page.blocks.forEach { block ->
                ChapterBlockContent(
                    block = block,
                    bodyStyle = bodyStyle,
                    imageLoader = imageLoader,
                    onImageClick = onImageClick,
                )
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
