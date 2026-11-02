package org.example.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.reader.core.AppDependencies
import org.example.reader.ui.components.FullScreenLoading

/**
 * 阅读器。
 *
 * 当前实现「上下滑动」单章阅读（Web 端默认模式）：只渲染当前章，
 * 章节切换靠底部上一章/下一章与目录。其他四种翻页模式（左右翻页、
 * 双页翻页、上下滚动连续、隐藏已读）在后续阶段补齐。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    deps: AppDependencies,
    bookId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 指定起始章（详情页点目录进入）；null 表示续读。 */
    startIndex: Int? = null,
) {
    val viewModel: ReaderViewModel = viewModel(key = "reader-$bookId") {
        ReaderViewModel(deps, bookId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(startIndex) {
        if (startIndex != null) viewModel.openAt(startIndex)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 换章后回到顶部，否则新章正文停在上一章的滚动位置。
    LaunchedEffect(state.currentIndex, state.paragraphs) {
        if (state.paragraphs.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.book?.name ?: "阅读",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val chapter = state.currentChapter
                        if (chapter != null) {
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshChapter) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新本章")
                    }
                    IconButton(onClick = viewModel::toggleCatalog) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "目录")
                    }
                },
            )
        },
        bottomBar = {
            ReaderBottomBar(
                hasPrev = state.hasPrev,
                hasNext = state.hasNext,
                current = state.currentIndex + 1,
                total = state.chapters.size,
                onPrev = viewModel::prevChapter,
                onNext = viewModel::nextChapter,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> FullScreenLoading()
                state.error != null && state.paragraphs.isEmpty() -> Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            text = state.currentChapter?.title.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    itemsIndexed(state.paragraphs) { _, paragraph ->
                        Text(
                            text = paragraph,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (state.isLoadingContent) {
                        item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    }
                    if (state.error != null) {
                        item {
                            Text(
                                text = state.error!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            TextButton(
                                onClick = viewModel::prevChapter,
                                enabled = state.hasPrev,
                            ) { Text("上一章") }
                            TextButton(
                                onClick = viewModel::nextChapter,
                                enabled = state.hasNext,
                            ) { Text("下一章") }
                        }
                    }
                }
            }
        }
    }

    if (state.showCatalog) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissCatalog,
            sheetState = sheetState,
        ) {
            CatalogSheet(
                chapters = state.chapters,
                currentIndex = state.currentIndex,
                onSelect = viewModel::goToChapter,
            )
        }
    }
}

@Composable
private fun ReaderBottomBar(
    hasPrev: Boolean,
    hasNext: Boolean,
    current: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onPrev, enabled = hasPrev) { Text("上一章") }
            Text(
                text = if (total > 0) "$current / $total" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onNext, enabled = hasNext) { Text("下一章") }
        }
    }
}

/** 目录面板。当前章高亮并自动滚动到可见位置。 */
@Composable
private fun CatalogSheet(
    chapters: List<org.example.reader.core.model.BookChapter>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val catalogState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex in chapters.indices) {
            catalogState.scrollToItem(currentIndex.coerceAtLeast(0))
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "目录（${chapters.size} 章）",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        HorizontalDivider()
        LazyColumn(state = catalogState, modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            itemsIndexed(chapters) { index, chapter ->
                val selected = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface,
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
