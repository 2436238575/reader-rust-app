package org.example.reader.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import org.example.reader.ui.components.EmptyState
import org.example.reader.ui.components.ErrorState
import org.example.reader.ui.components.FullScreenLoading

/**
 * 目录页（独立页面）。
 *
 * 详情页与阅读器共用一个实现：传入当前章用于高亮与初始滚动，
 * 选中章节通过 `onSelectChapter(原始下标)` 回传，由调用方决定是开阅读器还是跳章。
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
    var didScroll by remember(bookId) { mutableStateOf(false) }

    // 首次拿到目录后直接定位到在读章，省去用户在 700 章里找位置
    LaunchedEffect(state.chapters.size, currentIndex) {
        if (!didScroll && state.chapters.isNotEmpty()) {
            didScroll = true
            val target = (currentIndex ?: 0).coerceIn(0, state.chapters.lastIndex)
            listState.scrollToItem(target)
        }
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
                            text = if (state.keyword.isBlank()) {
                                "目录 · ${state.chapters.size} 章"
                            } else {
                                "匹配 ${state.visible.size} 章"
                            },
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
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新目录")
                    }
                },
            )
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
            }

            when {
                state.isLoading -> FullScreenLoading()

                state.error != null && state.chapters.isEmpty() -> ErrorState(
                    message = state.error!!,
                    onRetry = viewModel::load,
                )

                state.chapters.isEmpty() -> EmptyState("目录为空，可能是书源规则失效")

                state.visible.isEmpty() -> EmptyState("没有匹配的章节")

                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.visible, key = { it.index }) { indexed ->
                        val chapter = indexed.value
                        val isCurrent = indexed.index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrent) {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                )
                                .clickable { onSelectChapter(indexed.index) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (isCurrent) {
                                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "在读",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }
    }
}
