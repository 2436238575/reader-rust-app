package org.example.reader.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 阅读器自用的几个图标。
 *
 * `material-icons-core` 里没有朗读 / 换源 / 缓存 / 夜间，而引入 `material-icons-extended`
 * 会给 debug 构建多塞几十 MB 的类。这里直接用官方 Material Symbols 的 24dp 路径数据，
 * 体积可以忽略，视觉与其余核心图标一致。
 */
object ReaderIcons {

    val VolumeUp: ImageVector by lazy {
        vector(
            "VolumeUp",
            "M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 " +
                "2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 " +
                "7-4.49 7-8.77s-2.99-7.86-7-8.77z",
        )
    }

    val SwapHoriz: ImageVector by lazy {
        vector(
            "SwapHoriz",
            "M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z",
        )
    }

    val Download: ImageVector by lazy {
        vector("Download", "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z")
    }

    val DarkMode: ImageVector by lazy {
        vector(
            "DarkMode",
            "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 " +
                "1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z",
        )
    }

    /** 自动翻页：书页 + 播放三角。 */
    val AutoPage: ImageVector by lazy {
        vector(
            "AutoPage",
            "M4 5h9v2H4V5zm0 4h7v2H4V9zm0 4h9v2H4v-2zm0 4h6v2H4v-2zM15.5 13.5v7l5-3.5-5-3.5z",
        )
    }

    private fun vector(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
        }.build()
}
