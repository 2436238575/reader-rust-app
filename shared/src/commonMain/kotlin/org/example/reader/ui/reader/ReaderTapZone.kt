package org.example.reader.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/**
 * 正文点按落在哪一格。
 *
 * 三档 `clickAction` 只影响 [Prev] / [Next] 的行为，[Menu] 永远可用——
 * 否则 `none` 档下用户呼不出控件，等于把自己关在书里。
 */
enum class ReaderTapZone { Prev, Next, Menu, Ignore }

/**
 * 四边防误触带占屏幕宽/高的比例。
 *
 * 用**相对量**而不是「1cm」：同样 4%，1080×2400 @420dpi 下横向约 0.26cm、纵向约 0.58cm，
 * 差一个量级；写成厘米在平板上又会变成另一套手感。前端 `utils/readerClick.ts` 同口径。
 */
const val TAP_EDGE_FRACTION: Float = 0.04f

/**
 * 竖排九宫格：上排 = 上一屏，下排 = 下一屏，中间一排 = 切控件。
 *
 * 四角也归上/下排（按行判），避免「点角落没反应」被当成失灵。
 */
fun verticalTapZone(offset: Offset, size: IntSize): ReaderTapZone {
    if (inEdgeBand(offset, size)) return ReaderTapZone.Ignore
    return when {
        offset.y < size.height / 3f -> ReaderTapZone.Prev
        offset.y > size.height * 2f / 3f -> ReaderTapZone.Next
        else -> ReaderTapZone.Menu
    }
}

/**
 * 横翻九宫格：左列 = 上一页，右列 = 下一页，中间一列 = 切控件。
 *
 * 四角也归左/右列（按列判），与竖排的取舍一致。
 */
fun horizontalTapZone(offset: Offset, size: IntSize): ReaderTapZone {
    if (inEdgeBand(offset, size)) return ReaderTapZone.Ignore
    return when {
        offset.x < size.width / 3f -> ReaderTapZone.Prev
        offset.x > size.width * 2f / 3f -> ReaderTapZone.Next
        else -> ReaderTapZone.Menu
    }
}

/** 是否落在四边留白带内（含四个角）。 */
private fun inEdgeBand(offset: Offset, size: IntSize): Boolean {
    if (size.width <= 0 || size.height <= 0) return true
    val edgeX = size.width * TAP_EDGE_FRACTION
    val edgeY = size.height * TAP_EDGE_FRACTION
    return offset.x < edgeX || offset.x > size.width - edgeX ||
        offset.y < edgeY || offset.y > size.height - edgeY
}
