package org.example.reader.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.Book
import org.example.reader.core.model.ExploreKind
import org.example.reader.ui.components.BookResultGrid
import org.example.reader.ui.components.EmptyState
import org.example.reader.ui.components.ErrorState
import org.example.reader.ui.components.FullScreenLoading
import org.example.reader.ui.util.toBook

/**
 * 发现页（书海）。
 *
 * 对应 Web 端 `views/ExploreView.vue`：书源下拉（多于一个才显示）+
 * 横向分类栏 + 结果网格无限滚动 + 加入书架。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    deps: AppDependencies,
    contentPadding: PaddingValues,
    onShowDetail: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ExploreViewModel = viewModel { ExploreViewModel(deps) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverConfig by deps.session.serverConfig.collectAsStateWithLifecycle()
    val layout by deps.layout.layout.collectAsStateWithLifecycle()
    var sourceMenuExpanded by remember { mutableStateOf(false) }

    // 从详情页加书回来时刷新「已在书架」角标
    LaunchedEffect(Unit) { viewModel.refreshShelfState() }

    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
            TopAppBar(
                title = { Text("发现") },
                actions = {
                    // 只有一个可用书源时切换没有意义，和前端一致地隐藏
                    if (state.exploreSources.size > 1) {
                        Box {
                            TextButton(onClick = { sourceMenuExpanded = true }) {
                                Text(
                                    text = state.activeSource?.bookSourceName ?: "选择书源",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 150.dp),
                                )
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = sourceMenuExpanded,
                                onDismissRequest = { sourceMenuExpanded = false },
                            ) {
                                state.exploreSources.forEach { source ->
                                    val id = source.bookSourceId ?: return@forEach
                                    DropdownMenuItem(
                                        text = { Text(source.bookSourceName) },
                                        onClick = {
                                            sourceMenuExpanded = false
                                            viewModel.selectSource(id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )

            if (state.kinds.isNotEmpty()) {
                ExploreKindRow(
                    kinds = state.kinds,
                    activeUrl = state.activeKindUrl,
                    onSelect = viewModel::selectKind,
                )
            }

            when {
                state.isLoadingSources -> FullScreenLoading(message = "正在加载书源")

                state.exploreSources.isEmpty() && state.error != null -> ErrorState(
                    message = state.error!!,
                    onRetry = viewModel::retry,
                )

                state.exploreSources.isEmpty() -> EmptyState("无带有发现规则的书源")

                state.error != null && state.books.isEmpty() && !state.isLoading -> ErrorState(
                    message = state.error!!,
                    onRetry = viewModel::retry,
                )

                state.books.isEmpty() && state.isLoading -> FullScreenLoading()

                state.books.isEmpty() -> EmptyState(
                    message = if (state.kinds.any { !it.url.isNullOrBlank() }) {
                        "该分类暂时没有书籍"
                    } else {
                        "该书源没有发现分类"
                    },
                )

                else -> BookResultGrid(
                    layout = layout,
                    books = state.books,
                    shelfIds = state.shelfIds,
                    serverConfig = serverConfig,
                    imageLoader = deps.imageLoader,
                    onSelect = { onShowDetail(it.toBook()) },
                    canLoadMore = state.hasMore,
                    onLoadMore = viewModel::loadMore,
                    footer = {
                        GridFooter(
                            isLoading = state.isLoading,
                            hasMore = state.hasMore,
                            error = state.error,
                            onRetry = viewModel::retry,
                        )
                    },
                )
            }
        }

}

@Composable
private fun ExploreKindRow(
    kinds: List<ExploreKind>,
    activeUrl: String?,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(kinds) { kind ->
            val url = kind.url
            if (url.isNullOrBlank()) {
                // 分组标题行：只做分隔，不可点击
                Text(
                    text = kind.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                    maxLines = 1,
                )
            } else {
                FilterChip(
                    selected = activeUrl == url,
                    onClick = { onSelect(url) },
                    label = { Text(kind.title) },
                )
            }
        }
    }
}

@Composable
private fun GridFooter(
    isLoading: Boolean,
    hasMore: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            // 已有结果时加载失败：把错误摆出来，别让它静默停住
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text("重试") }
            }

            isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "加载中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            !hasMore -> Text(
                text = "没有更多了",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
