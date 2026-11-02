package org.example.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import org.example.reader.core.model.Book
import org.example.reader.core.session.ServerConfig
import org.example.reader.ui.util.coverUrl

/**
 * 书籍封面（列表行与网格卡共用）。
 *
 * 无封面时用书名前几字占位——空白块在深色主题下看起来像加载失败。
 */
@Composable
fun BookCoverThumb(
    book: Book,
    serverConfig: ServerConfig?,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 6.dp,
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
