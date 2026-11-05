package org.example.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.ImageLoader
import coil3.compose.AsyncImage
import kotlin.math.abs

/** 灯箱最大放大倍数。 */
private const val MAX_LIGHTBOX_SCALE = 4f

/** 双击放大到的倍数。 */
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * 章节配图灯箱（P1-9 的 L5：独占一层，不参与导航栈）。
 *
 * 交互与前端 `ImageLightbox.vue` 的移动端口径一致：单击/返回关闭、双击在 1× 与 2.5×
 * 之间切换、双指捏合缩放、放大后拖动平移。滚轮缩放是桌面能力，这里不做。
 *
 * 关闭动作由调用方的返回键层级统一处理（灯箱在栈顶），本组件不自己挂 BackHandler，
 * 否则同一次返回会被消费两次（先关灯箱再弹抽屉）。
 */
@Composable
internal fun ImageLightbox(
    url: String,
    imageLoader: ImageLoader,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    /** 放大后允许的最大平移量：图片中心到边界的距离。 */
    fun clampOffset(candidate: Offset, currentScale: Float): Offset {
        if (currentScale <= 1f || viewport.width == 0 || viewport.height == 0) return Offset.Zero
        val maxX = viewport.width * (currentScale - 1f) / 2f
        val maxY = viewport.height * (currentScale - 1f) / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    /** 以 [focal]（相对视图中心，未缩放坐标系）为焦点缩放到 [target]。 */
    fun zoomTo(target: Float, focal: Offset) {
        val next = target.coerceIn(1f, MAX_LIGHTBOX_SCALE)
        if (abs(next - scale) < 0.001f) return
        offset = if (next <= 1f) {
            Offset.Zero
        } else {
            clampOffset((offset - focal) * (next / scale) + focal, next)
        }
        scale = next
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewport = it }
            .pointerInput(url) {
                detectTapGestures(
                    onTap = { onClose() },
                    onDoubleTap = { position ->
                        val focal = position - Offset(size.width / 2f, size.height / 2f)
                        zoomTo(if (scale > 1f) 1f else DOUBLE_TAP_SCALE, focal)
                    },
                )
            }
            .pointerInput(url) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val focal = centroid - Offset(size.width / 2f, size.height / 2f)
                    val next = (scale * zoom).coerceIn(1f, MAX_LIGHTBOX_SCALE)
                    offset = if (next <= 1f) {
                        Offset.Zero
                    } else {
                        clampOffset((offset - focal) * (next / scale) + focal + pan, next)
                    }
                    scale = next
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = "配图大图",
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}
