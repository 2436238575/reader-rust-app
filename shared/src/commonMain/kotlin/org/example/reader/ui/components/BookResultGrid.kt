package org.example.reader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import org.example.reader.core.model.SearchBook
import org.example.reader.core.session.ServerConfig
import org.example.reader.ui.util.coverUrl

/**
 * 搜索结果 / 发现结果的封面网格。
 *
 * 两个页面只在「页脚」和「是否翻页」上不同，网格本身完全一致，因此收在这里。
 * 传入 [onLoadMore] 且 [canLoadMore] 为真时，滚动到距末尾 6 个内自动预取：
 * effect 以 `books.size` 为 key，首屏没铺满时也会链式补页。
 */
@Composable
fun BookResultGrid(
    books: List<SearchBook>,
    shelfIds: Set<String>,
    addingIds: Set<String>,
    serverConfig: ServerConfig?,
    imageLoader: ImageLoader,
    onOpenBook: (String) -> Unit,
    onAddToShelf: (SearchBook) -> Unit,
    modifier: Modifier = Modifier,
    /** bookSourceId → 书源名，多源搜索时展示来源。 */
    originNames: Map<Long, String> = emptyMap(),
    canLoadMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
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
        columns = GridCells.Adaptive(minSize = 104.dp),
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
            BookResultCard(
                name = book.name,
                author = book.author,
                cover = book.coverUrl(serverConfig, width = 320),
                imageLoader = imageLoader,
                added = book.bookId in shelfIds,
                adding = book.bookId in addingIds,
                originName = originNames[book.bookSourceId],
                onOpen = {
                    if (book.bookId.isNotBlank()) onOpenBook(book.bookId)
                },
                onAdd = { onAddToShelf(book) },
            )
        }
        if (footer != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { footer() }
        }
    }
}
