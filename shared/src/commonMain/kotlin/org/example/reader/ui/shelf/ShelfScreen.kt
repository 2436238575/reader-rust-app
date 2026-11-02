package org.example.reader.ui.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.Book
import org.example.reader.core.model.BookGroup
import org.example.reader.core.session.ServerConfig
import org.example.reader.ui.components.EmptyState
import org.example.reader.ui.components.ErrorState
import org.example.reader.ui.components.FullScreenLoading
import org.example.reader.ui.util.coverUrl
import org.example.reader.ui.util.formatProgress
import org.example.reader.ui.util.progressPercent
import kotlin.math.roundToInt

/**
 * 书架页。
 *
 * 对应 Web 端 `HomeView`：分组筛选 + 关键词过滤 + 书籍列表。
 * 分组是位域语义，一本书可同时属于多个分组。
 *
 * 视图形态有两种：**列表**（默认，信息密度高，手机竖屏更常用）与**网格**。
 * 选择持久化到本地设置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    deps: AppDependencies,
    contentPadding: PaddingValues,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ShelfViewModel = viewModel { ShelfViewModel(deps) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searchExpanded by remember { mutableStateOf(false) }
    val serverConfig by deps.session.serverConfig.collectAsStateWithLifecycle()

    // 从「发现 / 搜索」加书回来时刷新列表；静默模式不闪加载态
    LaunchedEffect(Unit) { viewModel.refreshSilently() }

    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = {
                Text(
                    text = if (state.books.isEmpty()) "书架" else "书架（${state.books.size}）",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            actions = {
                // 核心图标集没有网格图标，用文字标出「切换目标」比放一个语义不符的图标更清楚。
                val targetLabel = if (state.layout == ShelfLayout.List) "网格" else "列表"
                TextButton(
                    onClick = viewModel::toggleLayout,
                    modifier = Modifier.semantics {
                        contentDescription = "切换到${targetLabel}视图"
                    },
                ) {
                    Text(targetLabel, style = MaterialTheme.typography.labelLarge)
                }
                IconButton(onClick = { searchExpanded = !searchExpanded }) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            },
        )

        if (searchExpanded) {
            OutlinedTextField(
                value = state.keyword,
                onValueChange = viewModel::onKeywordChange,
                placeholder = { Text("搜索书名或作者") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (state.hasGroups) {
            GroupFilterRow(
                groups = state.groups,
                activeGroupId = state.activeGroupId,
                onSelect = viewModel::onGroupSelect,
            )
        }

        when {
            state.isLoading -> FullScreenLoading()
            state.error != null -> ErrorState(
                message = state.error!!,
                onRetry = viewModel::refresh,
            )
            state.books.isEmpty() -> EmptyState(
                message = "书架还是空的\n到「发现」或「搜索」里找本书吧",
            )
            state.layout == ShelfLayout.List -> BookList(
                books = state.visibleBooks,
                serverConfig = serverConfig,
                imageLoader = deps.imageLoader,
                onOpenBook = onOpenBook,
            )
            else -> BookGrid(
                books = state.visibleBooks,
                serverConfig = serverConfig,
                imageLoader = deps.imageLoader,
                onOpenBook = onOpenBook,
            )
        }
    }
}

@Composable
private fun GroupFilterRow(
    groups: List<BookGroup>,
    activeGroupId: Long,
    onSelect: (Long) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        item {
            FilterChip(
                selected = activeGroupId == 0L,
                onClick = { onSelect(0L) },
                label = { Text("全部") },
            )
        }
        items(groups, key = { it.groupId }) { group ->
            FilterChip(
                selected = activeGroupId == group.groupId,
                onClick = { onSelect(group.groupId) },
                label = { Text(group.groupName) },
            )
        }
        item {
            FilterChip(
                selected = activeGroupId == ShelfUiState.UNGROUPED_ID,
                onClick = { onSelect(ShelfUiState.UNGROUPED_ID) },
                label = { Text("未分组") },
            )
        }
    }
}

// ─── 列表布局 ────────────────────────────────────────────────────────────────

@Composable
private fun BookList(
    books: List<Book>,
    serverConfig: ServerConfig?,
    imageLoader: coil3.ImageLoader,
    onOpenBook: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = "共 ${books.size} 本",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            )
        }
        items(books, key = { it.bookId }) { book ->
            BookListItem(
                book = book,
                serverConfig = serverConfig,
                imageLoader = imageLoader,
                onClick = { onOpenBook(book.bookId) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 88.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }
    }
}

/**
 * 列表条目：封面缩略图 + 四行信息（书名 / 作者·来源 / 读至 / 进度）。
 *
 * 用自绘 Row 而非 M3 `ListItem`：需要「进度条 + 百分比 + 未读」横排在底部，
 * `ListItem` 的三段式插槽放不下。
 */
@Composable
private fun BookListItem(
    book: Book,
    serverConfig: ServerConfig?,
    imageLoader: coil3.ImageLoader,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        CoverThumbnail(
            book = book,
            serverConfig = serverConfig,
            imageLoader = imageLoader,
            modifier = Modifier.size(width = 56.dp, height = 74.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = book.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(book.author.ifBlank { "佚名" })
                    val origin = book.originName
                    if (!origin.isNullOrBlank()) append(" · ").append(origin)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = book.durChapterTitle?.takeIf { it.isNotBlank() }
                    ?.let { "读至 $it" }
                    ?: book.latestChapterTitle?.takeIf { it.isNotBlank() }?.let { "最新 $it" }
                    ?: "尚未开始阅读",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { progressPercent(book) },
                    modifier = Modifier.weight(1f).height(3.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${(progressPercent(book) * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(formatProgress(book))
                    if (book.cachedChapterCount > 0) append(" · 已缓存 ${book.cachedChapterCount} 章")
                    val unread = book.totalChapterNum - (book.durChapterIndex + 1)
                    if (book.totalChapterNum > 0 && unread > 0) {
                        append(" · 未读 ${if (unread > 99) "99+" else unread}")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─── 网格布局 ────────────────────────────────────────────────────────────────

@Composable
private fun BookGrid(
    books: List<Book>,
    serverConfig: ServerConfig?,
    imageLoader: coil3.ImageLoader,
    onOpenBook: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "共 ${books.size} 本",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(books, key = { it.bookId }) { book ->
            BookCard(
                book = book,
                serverConfig = serverConfig,
                imageLoader = imageLoader,
                onClick = { onOpenBook(book.bookId) },
            )
        }
    }
}

@Composable
private fun BookCard(
    book: Book,
    serverConfig: ServerConfig?,
    imageLoader: coil3.ImageLoader,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        CoverThumbnail(
            book = book,
            serverConfig = serverConfig,
            imageLoader = imageLoader,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
            cornerRadius = 12.dp,
            requestWidth = 320,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = book.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = book.author,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progressPercent(book) },
            modifier = Modifier.fillMaxWidth().height(2.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatProgress(book),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 封面（网格与列表共用）。无封面时用书名前几字做占位，避免空白块。 */
@Composable
private fun CoverThumbnail(
    book: Book,
    serverConfig: ServerConfig?,
    imageLoader: coil3.ImageLoader,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 6.dp,
    requestWidth: Int = 160,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val cover = book.coverUrl(serverConfig, width = requestWidth)
        if (cover != null) {
            AsyncImage(
                model = cover,
                imageLoader = imageLoader,
                contentDescription = book.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = book.name.take(4),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
