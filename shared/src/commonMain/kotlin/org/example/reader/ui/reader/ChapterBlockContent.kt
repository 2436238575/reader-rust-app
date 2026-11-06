package org.example.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import org.example.reader.core.model.ChapterBlock
import org.example.reader.core.model.ImageBlock
import org.example.reader.core.model.TextBlock
import org.example.reader.core.model.TextSpanStyle

/** 块内 span 的唯一 Compose 适配入口；NONE 不添加任何装饰。 */
internal fun TextBlock.toAnnotatedString(): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    spans.forEach { span ->
        when (val style = span.style) {
            TextSpanStyle.NONE -> Unit
            is TextSpanStyle.Background -> builder.addStyle(
                SpanStyle(background = Color(style.argb)),
                span.start.coerceIn(0, text.length),
                span.end.coerceIn(0, text.length),
            )
        }
    }
    return builder.toAnnotatedString()
}

/**
 * 所有正文容器共用的唯一块渲染分派。
 *
 * 图片的占位高度由 [imageBlockAspect] 决定，与 `measureChapterBlock` 是同一套口径；
 * 点击回调只关掉阅读器内部的一层（灯箱），不参与导航栈。
 */
@Composable
internal fun ChapterBlockContent(
    block: ChapterBlock,
    bodyStyle: TextStyle,
    imageLoader: ImageLoader,
    onImageClick: (String) -> Unit,
) {
    when (block) {
        is TextBlock -> Text(text = block.toAnnotatedString(), style = bodyStyle)
        is ImageBlock -> ChapterImageBlock(block = block, bodyStyle = bodyStyle, imageLoader = imageLoader, onClick = onImageClick)
    }
}

@Composable
private fun ChapterImageBlock(
    block: ImageBlock,
    bodyStyle: TextStyle,
    imageLoader: ImageLoader,
    onClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = block.url,
            contentDescription = block.caption.ifBlank { "章节配图" },
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / imageBlockAspect(block))
                .clip(RoundedCornerShape(8.dp))
                // 加载中/失败时用正文色的低透明度占位，避免高度塌陷成 0；
                // 不取 MaterialTheme 的 surfaceVariant——阅读器配色独立于 App 主题
                .background(bodyStyle.color.copy(alpha = 0.08f))
                .clickable { onClick(block.url) },
        )
        if (block.caption.isNotBlank()) {
            Spacer(Modifier.height(IMAGE_CAPTION_GAP_DP.dp))
            Text(
                text = block.caption,
                style = bodyStyle.imageCaptionStyle(),
                maxLines = IMAGE_CAPTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
