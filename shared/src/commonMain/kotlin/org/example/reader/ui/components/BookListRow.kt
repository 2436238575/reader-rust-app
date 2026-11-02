package org.example.reader.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import org.example.reader.core.model.Book
import org.example.reader.core.session.ServerConfig
import org.example.reader.ui.util.formatProgress
import org.example.reader.ui.util.progressPercent
import kotlin.math.roundToInt

/**
 * 书籍列表行：封面缩略图 + 书名 / 作者·来源 / 读至章节 / 进度条 / 统计。
 *
 * 书架、最近阅读共用。`trailing` 用于放行尾动作（最近阅读的删除按钮）。
 * 不用 M3 `ListItem`：底部要横排「进度条 + 百分比 + 缓存/未读」，三段式插槽放不下。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListRow(
    book: Book,
    serverConfig: ServerConfig?,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    /** 多选模式：行首显示勾选框，点击整行切换选中，不再进详情/阅读。 */
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = if (selectionMode) 4.dp else 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = null)
        }
        BookCoverThumb(
            book = book,
            serverConfig = serverConfig,
            imageLoader = imageLoader,
            modifier = Modifier.size(width = 64.dp, height = 88.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
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
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}
