package org.example.reader.core.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** 沿 View 父链找窗口的层数上限（正常层级 < 10，纯粹防御异常视图树）。 */
private const val MAX_VIEW_DEPTH: Int = 32

/** 逐层拆 ContextWrapper 的层数上限，防止 BaseContext 成环卡住组合。 */
private const val MAX_CONTEXT_DEPTH: Int = 16

/**
 * 「跟随系统」的判定阈值。
 *
 * 滑杆上界正好是 1f，浮点拖动误差会让它变成 0.9999…，
 * 所以靠近 1 就当作「不覆盖」，不能写成 `== 1f`。
 */
private const val FOLLOW_SYSTEM_THRESHOLD: Float = 0.999f

/**
 * 绝对亮度下限。
 *
 * Android 上 `screenBrightness = 0` 是「屏幕全黑」，不是「最暗」：
 * 用户把滑杆拖到底会直接看不到正文，还以为应用崩了。0.01 在暗环境下仍然很暗。
 */
private const val MIN_ABSOLUTE_BRIGHTNESS: Float = 0.01f

@Composable
actual fun ApplyWindowBrightness(percent: Float) {
    val view = LocalView.current
    // 窗口只跟 View 走：亮度变化不该重新解析窗口（解析要拆 Context / 走父链）
    val window = remember(view) { view.findHostWindow() }

    // 生命周期：进入时记下原值，离开时**还原原值**。
    // 不能无条件写 BRIGHTNESS_OVERRIDE_NONE —— 原值可能是宿主自己设过的窗口亮度，
    // 一律写 -1 等于替别人改配置。
    DisposableEffect(window) {
        val original = window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        onDispose { window?.applyScreenBrightness(original) }
    }

    // 亮度变化：拖动滑杆时每帧都会重跑（LaunchedEffect 会取消上一帧），
    // 放在 DisposableEffect 里会因为「先还原再重设」而每帧闪一下。
    LaunchedEffect(window, percent) {
        window?.applyScreenBrightness(percent.toWindowBrightness())
    }
}

/**
 * 业务亮度 → `WindowManager.LayoutParams.screenBrightness`。
 *
 * 映射规则（两条都是刻意的）：
 * 1. `percent >= [FOLLOW_SYSTEM_THRESHOLD]` → [WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE]
 *    （-1，跟随系统）。`ReaderSettings.brightness` 的默认值就是 1f，若映射成绝对 1.0，
 *    一进阅读器就会把用户系统亮度顶到最亮（夜间刺眼），退出时还会被还原成 1.0 之外的值。
 * 2. 其余 → 夹在 [MIN_ABSOLUTE_BRIGHTNESS]..1 的绝对值，透传给窗口。
 */
internal fun Float.toWindowBrightness(): Float {
    if (isNaN()) return WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    if (this >= FOLLOW_SYSTEM_THRESHOLD) return WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    return coerceIn(MIN_ABSOLUTE_BRIGHTNESS, 1f)
}

/**
 * 写窗口亮度。
 *
 * `Window.getAttributes()` 返回的是 PhoneWindow 自己持有的一份可变对象，
 * 但**只改对象不会生效**：必须重新赋值给 `attributes`，才会走到
 * `WindowManager.updateViewLayout`（窗口还没 attach 时由 Window 自己缓存，attach 后应用）。
 *
 * 整段吞异常：面板（Dialog）关闭时 `onDispose` 可能晚于窗口移除执行，
 * 此时 `updateViewLayout` 会抛 `IllegalArgumentException("View not attached to window manager")`。
 * 亮度是增强项，不能因为「还原一个已经消失的窗口」把阅读器带崩。
 */
private fun Window.applyScreenBrightness(value: Float) {
    runCatching {
        val params = attributes
        if (params.screenBrightness != value) {
            params.screenBrightness = value
            attributes = params
        }
    }
}

/**
 * 解析当前 View 所在的窗口。
 *
 * 两条路，顺序不能换：
 * 1. **Dialog 路**（先找）：Compose 的 `Dialog` 与 M3 的 `ModalBottomSheet` 都把一个
 *    实现 [DialogWindowProvider] 的 `AbstractComposeView`（`DialogLayout` /
 *    `ModalBottomSheetDialogLayout`）作为对话窗的 contentView，而组合里的
 *    `LocalView` 是它内部的 `AndroidComposeView`，所以在父链上能找到它。
 *    Dialog 有独立的 window token 与 LayoutParams，改 Activity 窗口的亮度对它**无效**，
 *    低亮度下面板会亮回去。
 * 2. **Activity 路**（兜底）：普通页面没有对话窗，从 `Context` 逐层拆 `ContextWrapper`
 *    取 `Activity.window`。
 *
 * 都拿不到（例如 Preview / 用了 Application context）就返回 null，调用方静默跳过：
 * 亮度是「有更好」的增强，不能因此崩掉阅读器。
 */
private fun View.findHostWindow(): Window? = findDialogWindow() ?: context.findActivityWindow()

/** 沿 View 父链找实现 [DialogWindowProvider] 的节点（含自身）。 */
private fun View.findDialogWindow(): Window? {
    var node: View? = this
    var depth = 0
    while (node != null && depth < MAX_VIEW_DEPTH) {
        if (node is DialogWindowProvider) return node.window
        node = node.parent as? View
        depth++
    }
    return null
}

/** Activity 窗口。 */
private fun Context.findActivityWindow(): Window? = findActivity()?.window

/**
 * 逐层拆 `ContextWrapper` 找 Activity。
 *
 * Compose 里拿到的 Context 通常是 `ContextThemeWrapper` 套 Activity，
 * 不能只判一次 `is Activity`。`baseContext` 理论上不成环，仍加层数上限兜底。
 */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    var depth = 0
    while (ctx is ContextWrapper && depth < MAX_CONTEXT_DEPTH) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
        depth++
    }
    return null
}
