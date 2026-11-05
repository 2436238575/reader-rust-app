package org.example.reader.ui.reader

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import org.example.reader.core.model.ChapterBlock
import org.example.reader.core.model.ImageBlock
import org.example.reader.core.model.TextBlock
import org.example.reader.core.model.TextSpanStyle

/** 块内 span 的唯一 Compose 适配入口；NONE 不添加任何装饰。 */
internal fun TextBlock.toAnnotatedString(): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    spans.forEach { span ->
        when (span.style) {
            TextSpanStyle.NONE -> Unit
        }
    }
    return builder.toAnnotatedString()
}

/** 所有正文容器共用的唯一块渲染分派。 */
@Composable
internal fun ChapterBlockContent(block: ChapterBlock, bodyStyle: TextStyle) {
    when (block) {
        is TextBlock -> Text(text = block.toAnnotatedString(), style = bodyStyle)
        is ImageBlock -> error("ImageBlock rendering is not enabled")
    }
}
