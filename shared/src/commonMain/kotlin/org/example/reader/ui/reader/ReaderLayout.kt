package org.example.reader.ui.reader

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 正文列左右最小留白；与历代实现保持一致，避免页宽调到最大时贴边。 */
internal val ReaderContentMinPadding: Dp = 20.dp

/**
 * 页宽 → 左右留白。
 *
 * 窗口比 [pageWidth] 宽时把剩余空间均分到两侧（正文居中），否则退回最小留白满宽排版。
 * 只作用于**正文列**：顶/底栏、点按热区、进度条仍是全宽，翻页与滚动不受影响。
 */
internal fun readerSidePadding(windowWidth: Dp, pageWidth: Dp): Dp {
    if (windowWidth <= 0.dp) return ReaderContentMinPadding
    val side = (windowWidth - pageWidth) / 2f
    return if (side > ReaderContentMinPadding) side else ReaderContentMinPadding
}
