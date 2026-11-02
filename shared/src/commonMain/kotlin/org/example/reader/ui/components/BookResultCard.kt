package org.example.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import org.example.reader.core.model.SearchBook
import org.example.reader.core.session.ServerConfig
import org.example.reader.ui.util.coverUrl

/**
 * 发现 / 搜索结果的网格卡片。
 *
 * 结果卡不再放「加入书架」按钮：动作收进书籍详情页，卡片只保留
 * 封面右上角的已入架对勾，避免误点与两处状态不同步。
 */
@Composable
fun BookResultGridCard(
    book: SearchBook,
    serverConfig: ServerConfig?,
    imageLoader: ImageLoader,
    added: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    originName: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            ResultCover(book = book, serverConfig = serverConfig, imageLoader = imageLoader)
            if (added) AddedBadge(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = book.name,
            style = MaterialTheme.typography.bodyMedium,
            // 固定两行：书名长短不一时同排卡片的高度仍然一致
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle(book, originName),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!book.lastChapter.isNullOrBlank()) {
            Text(
                text = latestLabel(book.lastChapter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 发现 / 搜索结果的行卡片。
 *
 * 列表模式信息密度更高：封面放大、补一至两行简介，
 * 作者后面带书源名，方便多源结果里判断来源。
 */
@Composable
fun BookResultListRow(
    book: SearchBook,
    serverConfig: ServerConfig?,
    imageLoader: ImageLoader,
    added: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    originName: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 88.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                ResultCover(book = book, serverConfig = serverConfig, imageLoader = imageLoader)
            }
            if (added) {
                AddedBadge(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
        }
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
                text = subtitle(book, originName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!book.lastChapter.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = latestLabel(book.lastChapter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val intro = book.intro?.trim().orEmpty()
            if (intro.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = intro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ResultCover(
    book: SearchBook,
    serverConfig: ServerConfig?,
    imageLoader: ImageLoader,
) {
    val cover = book.coverUrl(serverConfig, width = 320)
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
            text = book.name.take(6),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center).padding(6.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "已在书架",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** 部分书源只下发章节序号（"1723"），补成「第1723章」更可读。 */
private fun latestLabel(raw: String?): String {
    val text = raw?.trim().orEmpty()
    return if (text.isNotEmpty() && text.all(Char::isDigit)) "最新：第${text}章" else "最新：$text"
}

private fun subtitle(book: SearchBook, originName: String?): String = buildString {
    append(book.author.ifBlank { "佚名" })
    if (!originName.isNullOrBlank()) append(" · ").append(originName)
}
