package org.example.reader.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.Book
import org.example.reader.core.session.ServerConfig
import org.example.reader.ui.components.BookCoverThumb

/**
 * 书籍详情（独立页面，不是弹层）。
 *
 * 只放「这本书是什么」：封面、书名/作者/来源/分类、在读与最新章、简介、动作。
 * 目录是独立页面（`CatalogScreen`），阅读器里也复用它。
 *
 * 入口：书架长按、发现与搜索结果点击。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    deps: AppDependencies,
    initialBook: Book,
    /** 每次打开详情递增，避免复用上一次的 ViewModel 状态。 */
    sessionKey: Int,
    onBack: () -> Unit,
    onOpenCatalog: (Book) -> Unit,
    onRead: (bookId: String, startIndex: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BookDetailViewModel = viewModel(
        key = "detail-${initialBook.bookId}-$sessionKey",
    ) {
        BookDetailViewModel(deps, initialBook)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverConfig by deps.session.serverConfig.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("书籍详情") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    DetailHeader(
                        book = state.book,
                        serverConfig = serverConfig,
                        imageLoader = deps.imageLoader,
                    )
                }
                item {
                    ActionRow(
                        onShelf = state.onShelf,
                        isAdding = state.isAdding,
                        hasProgress = state.readChapters > 0,
                        onAddToShelf = viewModel::addToShelf,
                        onOpenCatalog = { onOpenCatalog(state.book) },
                        onRead = { onRead(state.book.bookId, null) },
                    )
                }
                item { ReadingMeta(state = state) }
                item { HorizontalDivider() }
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("简介", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.book.intro?.takeIf { it.isNotBlank() } ?: "暂无简介",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item { HorizontalDivider() }
                item {
                    // 详情不承载目录：给一个明确入口，避免用户以为没做
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCatalog(state.book) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "查看目录",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (state.book.totalChapterNum > 0) {
                                "共 ${state.book.totalChapterNum} 章"
                            } else {
                                ""
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun DetailHeader(
    book: Book,
    serverConfig: ServerConfig?,
    imageLoader: coil3.ImageLoader,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        BookCoverThumb(
            book = book,
            serverConfig = serverConfig,
            imageLoader = imageLoader,
            modifier = Modifier.size(width = 104.dp, height = 144.dp),
            cornerRadius = 8.dp,
            requestWidth = 480,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = book.author.ifBlank { "佚名" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!book.originName.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = book.originName!!,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val tags = book.kind.orEmpty()
                    .split(',', '，')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .take(2)
                tags.forEach { tag -> TagChip(tag) }
                if (!book.wordCount.isNullOrBlank()) TagChip("${book.wordCount}字")
            }
        }
    }
}

@Composable
private fun TagChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ActionRow(
    onShelf: Boolean,
    isAdding: Boolean,
    hasProgress: Boolean,
    onAddToShelf: () -> Unit,
    onOpenCatalog: () -> Unit,
    onRead: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        DetailAction(
            icon = if (onShelf) Icons.Filled.Check else Icons.Filled.Add,
            label = when {
                onShelf -> "已在书架"
                isAdding -> "加入中…"
                else -> "加入书架"
            },
            enabled = !onShelf && !isAdding,
            onClick = onAddToShelf,
        )
        DetailAction(
            icon = Icons.AutoMirrored.Filled.List,
            label = "查看目录",
            onClick = onOpenCatalog,
        )
        DetailAction(
            icon = Icons.Filled.PlayArrow,
            label = if (hasProgress) "继续阅读" else "开始阅读",
            onClick = onRead,
        )
    }
}

@Composable
private fun DetailAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ReadingMeta(state: BookDetailUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        state.book.durChapterTitle?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = "在读 · $title",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        state.book.latestChapterTitle?.takeIf { it.isNotBlank() }?.let { title ->
            Text(
                text = "最新 · $title",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = buildString {
                if (state.book.totalChapterNum > 0) append("共 ${state.book.totalChapterNum} 章")
                if (state.readChapters > 0) append(" · 已读 ${state.readChapters} 章")
                if (state.book.cachedChapterCount > 0) {
                    append(" · 已缓存 ${state.book.cachedChapterCount} 章")
                }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
    }
}
