package org.example.reader.ui.reader

import org.example.reader.core.settings.ReadMethod
import kotlin.math.roundToInt

/** 块下标 → 章内比例（0..1），页首/首可见块口径；空章返回 0。 */
internal fun blockFraction(paraIndex: Int, totalBlocks: Int): Float =
    if (totalBlocks <= 0) {
        0f
    } else {
        (paraIndex.toFloat() / totalBlocks).coerceIn(0f, 1f)
    }

/**
 * 章内比例（0..1）→ 块下标，使用与旧进度口径完全一致的四舍五入和边界。
 * 上界允许为 [totalBlocks]，因此 1f 可表达章末；空章返回 0。
 */
internal fun blockAtFraction(fraction: Float, totalBlocks: Int): Int =
    if (totalBlocks <= 0) {
        0
    } else {
        (fraction.coerceIn(0f, 1f) * totalBlocks)
            .roundToInt()
            .coerceIn(0, totalBlocks)
    }

/**
 * 横翻在一次停留期间共享的逻辑位置锚。
 *
 * 排版预览会连续创建多个分页代际。每个代际都必须从本锚恢复，而不能把上一代
 * 「目标块所在页的页首」写回后当作下一代输入；只有真实用户翻页才推进本锚。
 */
internal class HorizontalRestoreSession(initialFraction: Float) {
    var anchorFraction: Float = initialFraction.coerceIn(0f, 1f)
        private set

    /** 当前分页代际的恢复目标；章末仍由共享换算映射到末块。 */
    fun restoreBlock(totalBlocks: Int): Int = blockAtFraction(anchorFraction, totalBlocks)

    /**
     * 计算本代际恢复页。只有章末 1f 产生真实末页页首的归一值；
     * 此时尚未移动 Pager，不改变锚，由 [completeRestore] 在定位完成后提交。
     */
    fun restoreTarget(pages: List<ReaderPage>, totalBlocks: Int): HorizontalRestoreTarget {
        val pageIndex = pageForBlock(pages, restoreBlock(totalBlocks))
        val normalizedFraction = if (anchorFraction == 1f && totalBlocks > 0) {
            pages.getOrNull(pageIndex)?.let { page ->
                blockFraction(page.startBlockIndex, totalBlocks)
            }
        } else {
            null
        }
        return HorizontalRestoreTarget(pageIndex, normalizedFraction)
    }

    /** Pager 已到目标页且采集基线建立后，提交仅章末存在的一次性归一。 */
    fun completeRestore(target: HorizontalRestoreTarget) {
        target.normalizedFraction?.let { anchorFraction = it }
    }

    /** 真实用户翻到页首块 [startBlock] 后，更新后续重排与模式切换的位置源。 */
    fun onUserPage(startBlock: Int, totalBlocks: Int): Float {
        anchorFraction = blockFraction(startBlock, totalBlocks)
        return anchorFraction
    }
}

/** 一次横翻恢复的目标页，以及仅章末需要的归一化回写值。 */
internal data class HorizontalRestoreTarget(
    val pageIndex: Int,
    val normalizedFraction: Float?,
)

/**
 * 单章列表的可见 item 下标 → 正文块比例。
 *
 * item 0 是章头，1..totalBlocks 是正文，其后的加载/错误/翻章项代表已到章末。
 * 没有可见项或正文尚未建立时不产生位置，避免空快照把内存真源冲成 0。
 */
internal fun singleChapterParagraphFraction(firstVisibleIndex: Int?, totalBlocks: Int): Float? {
    if (firstVisibleIndex == null || totalBlocks <= 0) return null
    return when {
        firstVisibleIndex <= 0 -> 0f
        firstVisibleIndex <= totalBlocks -> blockFraction(firstVisibleIndex - 1, totalBlocks)
        else -> 1f
    }
}

/**
 * 五种阅读方式切换前的一次性视口交接。
 *
 * 单章/连续列表直接读取最后尚未被 snapshotFlow 投递的真实视口；横翻与双页的
 * Pager 已在真实翻页时更新 VM，因此沿用当前内存真源。目标方式不参与计算，
 * 所有目标都消费同一个「章号 + 逻辑块」结果。
 *
 * [chapterEndRestore] 只承载「上一章 → 程序化章末恢复」语义，不是另一个位置源。
 * 连续列表的锚定与视口交接都会把 1f 转成末块比例，这里必须在模式切换前恢复 1f，
 * 让横翻按真实末页页首归一；用户自然滚到末块不会带此标记，仍按普通块位置处理。
 */
internal fun modeSwitchFraction(
    sourceMethod: ReadMethod,
    currentFraction: Float,
    singleChapterFraction: Float? = null,
    continuousFraction: Float? = null,
    chapterEndRestore: Boolean = false,
): Float {
    if (chapterEndRestore &&
        (sourceMethod == ReadMethod.Scroll || sourceMethod == ReadMethod.Scroll2)
    ) {
        return 1f
    }
    return when (sourceMethod) {
        ReadMethod.SlideUp -> singleChapterFraction ?: currentFraction
        ReadMethod.Scroll, ReadMethod.Scroll2 -> continuousFraction ?: currentFraction
        ReadMethod.Horizontal, ReadMethod.DualPage -> currentFraction
    }.coerceIn(0f, 1f)
}
