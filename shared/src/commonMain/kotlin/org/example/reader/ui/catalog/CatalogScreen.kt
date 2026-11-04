package org.example.reader.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.BookChapter
import org.example.reader.ui.components.EmptyState
import org.example.reader.ui.components.ErrorState
import org.example.reader.ui.components.FullScreenLoading
import kotlin.math.abs

/**
 * 刷新前的滚动锚点：记的是**行号**（不是章下标），因为卷头会改变行偏移。
 *
 * [loadVersion] 是请求发出时的载入版本号：只有新目录到达（版本号变了）才该消费锚点，
 * 否则「上次刷新失败」留下的锚点会被后续的关键词输入误触发。
 */
private data class ScrollAnchor(
    val firstVisibleRow: Int,
    val rowCount: Int,
    val loadVersion: Int,
)

/** 目录长度变化不超过这个比例时才认为「还是同一份目录」，可以原位恢复。 */
private const val REFRESH_ANCHOR_TOLERANCE = 0.1f

/**
 * 目录页（独立页面）。
 *
 * 详情页与阅读器共用一个实现：传入当前章用于高亮与初始滚动，
 * 选中章节通过 `onSelectChapter(原始下标)` 回传，由调用方决定是开阅读器还是跳章。
 *
 * 列表始终是 `LazyColumn`（700+ 章不能一次性组合）；过滤与卷分组在
 * `CatalogViewModel` 里算好，这里只渲染 `state.rows`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    deps: AppDependencies,
    bookId: String,
    bookTitle: String,
    /** 当前在读章；null 表示没有进度。 */
    currentIndex: Int?,
    onBack: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CatalogViewModel = viewModel(key = "catalog-$bookId") {
        CatalogViewModel(deps, bookId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 首次进入自动定位到在读章后置真；后续（关键词、刷新）不再抢位置
    var didScroll by remember(bookId) { mutableStateOf(false) }
    // 刷新前记下的锚点，等新列表到达后消费
    var pendingAnchor by remember(bookId) { mutableStateOf<ScrollAnchor?>(null) }

    val currentRow: Int? = remember(state.chapterRowOf, currentIndex) { state.rowOf(currentIndex) }

    // 「当前章是否在可视区」——用 layoutInfo 判断，别让定位按钮常驻
    val currentRowVisible by remember(listState, currentRow) {
        derivedStateOf {
            val row = currentRow
            if (row == null) return@derivedStateOf false
            val visible = listState.layoutInfo.visibleItemsInfo
            // 还没排版（visible 为空）时先当作可见，否则按钮会在首帧闪一下
            visible.isEmpty() || (row >= visible.first().index && row <= visible.last().index)
        }
    }

    // 载入完成（首屏或刷新）后决定滚动位置
    LaunchedEffect(state.loadVersion, state.rows.size, currentIndex) {
        if (state.rows.isEmpty()) return@LaunchedEffect
        val lastIndex = state.rows.lastIndex
        val chapterRow = state.rowOf(currentIndex)
        if (!didScroll) {
            didScroll = true
            listState.scrollToItem((chapterRow ?: 0).coerceIn(0, lastIndex))
            return@LaunchedEffect
        }
        val anchor = pendingAnchor ?: return@LaunchedEffect
        // 新目录还没到（版本号没变）：留着锚点继续等，先别动滚动位置
        if (anchor.loadVersion == state.loadVersion) return@LaunchedEffect
        pendingAnchor = null
        val tolerance = (anchor.rowCount * REFRESH_ANCHOR_TOLERANCE).toInt().coerceAtLeast(1)
        val sameCatalog = anchor.rowCount > 0 && abs(state.rows.size - anchor.rowCount) <= tolerance
        val target = if (sameCatalog) {
            // 目录基本没变：留在原来那一段，别把用户甩回在读章
            anchor.firstVisibleRow.coerceIn(0, lastIndex)
        } else {
            // 章节数变化超过 10%：旧行号已不可信，退回在读章
            (chapterRow ?: 0).coerceIn(0, lastIndex)
        }
        listState.scrollToItem(target)
    }

    // 刷新结果 / 刷新失败用 Snackbar 反馈，弹完清掉
    LaunchedEffect(state.notice) {
        val message = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeNotice()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = bookTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = catalogSubtitle(state),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleFilter) {
                        Icon(
                            imageVector = if (state.filterExpanded) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (state.filterExpanded) "关闭搜索" else "搜索章节",
                        )
                    }
                    IconButton(
                        enabled = !state.isRefreshing,
                        onClick = {
                            // 先记位置，再拉新目录（refresh=1）
                            pendingAnchor = ScrollAnchor(
                                firstVisibleRow = listState.firstVisibleItemIndex,
                                rowCount = state.rows.size,
                                loadVersion = state.loadVersion,
                            )
                            viewModel.load(force = true)
                        },
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新目录")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // 只有「有在读章、且它不在可视区」时才出现
            if (currentRow != null && !currentRowVisible) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(currentRow) } },
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = "定位到当前章")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.filterExpanded) {
                OutlinedTextField(
                    value = state.keyword,
                    onValueChange = viewModel::onKeywordChange,
                    placeholder = { Text("搜索章节标题") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                // 搜索态专属的跳顶 / 跳底，收起搜索就消失，不占常驻空间
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val hasRows = state.rows.isNotEmpty()
                    TextButton(
                        enabled = hasRows,
                        onClick = { scope.launch { listState.scrollToItem(0) } },
                    ) {
                        Text("跳到开头")
                    }
                    TextButton(
                        enabled = hasRows,
                        onClick = {
                            if (state.rows.isNotEmpty()) {
                                scope.launch { listState.scrollToItem(state.rows.lastIndex) }
                            }
                        },
                    ) {
                        Text("跳到结尾")
                    }
                }
            }

            when {
                state.isLoading -> FullScreenLoading()

                state.error != null && state.chapters.isEmpty() -> ErrorState(
                    message = state.error!!,
                    onRetry = { viewModel.load() },
                )

                state.chapters.isEmpty() -> EmptyState("目录为空，可能是书源规则失效")

                state.rows.isEmpty() -> EmptyState("没有匹配的章节")

                else -> LazyColumn(
                    state = listState,
                    // 底部留出定位按钮的高度，免得最后几章被 FAB 压住
                    contentPadding = PaddingValues(bottom = 88.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.rows, key = { it.key }) { row ->
                        when (row) {
                            is CatalogRow.VolumeHeader -> VolumeHeaderRow(volume = row.volume)

                            is CatalogRow.ChapterRow -> ChapterRowItem(
                                chapter = row.chapter,
                                opensVolume = row.opensVolume,
                                isCurrent = currentIndex != null && row.position == currentIndex,
                                isRead = currentIndex != null && row.position < currentIndex,
                                onClick = { onSelectChapter(row.position) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 标题副行：刷新中 > 搜索命中数 > 章数（带卷数）。 */
private fun catalogSubtitle(state: CatalogUiState): String = when {
    state.isRefreshing -> "正在刷新目录…"
    state.keyword.isNotBlank() -> "匹配 ${state.matchCount} 章"
    state.hasVolumes -> "目录 · ${state.chapters.size} 章 · ${state.volumeCount} 卷"
    else -> "目录 · ${state.chapters.size} 章"
}

/** 卷头行。卷全为空时不会有这一行。 */
@Composable
private fun VolumeHeaderRow(volume: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = volume,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 章节行。
 *
 * 状态陈列优先级：当前章 > 已读淡化。
 * 「已缓存」徽章**不做**：离线缓存属 P2-6，当前没有数据来源，不编造。
 *
 * 副标题二选一（避免和卷头重复）：字数优先；没有字数时，非卷首行才显示卷名
 * （卷首行的正上方就是卷头，再写一遍等于重复）。
 */
@Composable
private fun ChapterRowItem(
    chapter: BookChapter,
    opensVolume: Boolean,
    isCurrent: Boolean,
    isRead: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (isCurrent) colors.surfaceContainerHigh else colors.surface
    val titleColor = when {
        isCurrent -> colors.primary
        isRead -> colors.onSurfaceVariant.copy(alpha = 0.6f)
        else -> colors.onSurface
    }
    val subtitleColor = if (isRead) colors.onSurfaceVariant.copy(alpha = 0.6f) else colors.onSurfaceVariant
    val wordCount = chapter.wordCount?.trim()?.takeIf { it.isNotEmpty() }
    val volumeName = chapter.volume?.trim()?.takeIf { it.isNotEmpty() && !opensVolume }
    val subtitle = wordCount ?: volumeName
    val isVolumeTitle = chapter.isVolume == true

    Column(modifier = Modifier.fillMaxWidth().background(background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.title,
                    style = if (isVolumeTitle) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (isCurrent) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "在读",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f))
    }
}
