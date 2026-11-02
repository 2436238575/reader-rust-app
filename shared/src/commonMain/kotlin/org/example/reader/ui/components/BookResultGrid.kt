package org.example.reader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import org.example.reader.core.model.SearchBook
import org.example.reader.core.session.ServerConfig
import org.example.reader.core.settings.ShelfLayout

/**
 * 发现 / 搜索结果列表。
 *
 * 跟随全局列表/网格偏好（`deps.layout`）：网格模式是封面卡片，
 * 列表模式信息密度更高（封面放大、两行简介、最新章节）。
 * 两种模式都在距末尾 6 项时预取下一页；effect 以 `books.size` 为 key，
 * 首屏没铺满时也会链式补页。
 */
@Composable
fun BookResultGrid(
    layout: ShelfLayout,
    books: List<SearchBook>,
    shelfIds: Set<String>,
    serverConfig: ServerConfig?,
    imageLoader: ImageLoader,
    onSelect: (SearchBook) -> Unit,
    modifier: Modifier = Modifier,
    /** bookSourceId → 书源名，多源结果展示来源。 */
    originNames: Map<Long, String> = emptyMap(),
    canLoadMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    if (layout == ShelfLayout.List) {
        val listState = rememberLazyListState()
        if (onLoadMore != null) {
            LaunchedEffect(listState, books.size, canLoadMore) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                    .collect { lastVisible ->
                        if (canLoadMore && lastVisible >= books.size - 6) onLoadMore()
                    }
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items = books,
                key = { index, book -> "${book.bookSourceId}-${book.bookId}-$index" },
            ) { _, book ->
                BookResultListRow(
                    book = book,
                    serverConfig = serverConfig,
                    imageLoader = imageLoader,
                    added = book.bookId in shelfIds,
                    originName = originNames[book.bookSourceId],
                    onClick = { onSelect(book) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 92.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
            if (footer != null) {
                item { footer() }
            }
        }
        return
    }

    val gridState = rememberLazyGridState()
    if (onLoadMore != null) {
        LaunchedEffect(gridState, books.size, canLoadMore) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .collect { lastVisible ->
                    if (canLoadMore && lastVisible >= books.size - 6) onLoadMore()
                }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(
            items = books,
            // 同一本书可能在不同批次/页重复出现，用下标兜住 key 冲突
            key = { index, book -> "${book.bookSourceId}-${book.bookId}-$index" },
        ) { _, book ->
            BookResultGridCard(
                book = book,
                serverConfig = serverConfig,
                imageLoader = imageLoader,
                added = book.bookId in shelfIds,
                originName = originNames[book.bookSourceId],
                onClick = { onSelect(book) },
            )
        }
        if (footer != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { footer() }
        }
    }
}
