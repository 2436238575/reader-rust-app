package org.example.reader.core.platform

import androidx.compose.runtime.Composable

/**
 * 把**当前应用窗口**的亮度设成 [percent]（0..1，1 = 不覆盖、跟随系统）。
 *
 * 为什么走窗口级而不是蒙版：蒙版是叠在内容上面的半透明黑矩形，正文与背景
 * 一起变暗，对比度随亮度下降而塌掉（越暗越看不清），并且面板 / 菜单跑在独立
 * Window 里，蒙版根本盖不到。窗口级 `WindowManager.LayoutParams.screenBrightness`
 * 由系统在合成阶段生效，文字与背景的相对亮度差不变。
 *
 * 边界（务必保持）：
 * - 只改**本应用当前窗口**，不改系统亮度设置，其它应用与桌面完全不受影响；
 * - 离开阅读器（Composable 离开组合）时还原进入前的原值，不是无条件写回默认值；
 * - 面板（ModalBottomSheet）是独立窗口，需要在面板内容里**再调一次**，
 *   否则低亮度下面板会掉回系统亮度，观感上会闪一下变亮。
 *
 * @param percent 目标亮度，1（或更大）表示交给系统，其余按绝对值生效。
 */
@Composable
expect fun ApplyWindowBrightness(percent: Float)
