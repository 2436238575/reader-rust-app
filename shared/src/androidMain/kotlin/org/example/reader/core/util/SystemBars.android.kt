package org.example.reader.core.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun SystemBarsAppearance(lightContent: Boolean, restoreContent: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = view.context.findActivity() ?: return
    val controller = remember(activity, view) { WindowCompat.getInsetsController(activity.window, view) }

    // isAppearanceLightXxx = true 表示「用深色图标」，所以这里取反
    SideEffect {
        controller.isAppearanceLightStatusBars = !lightContent
        controller.isAppearanceLightNavigationBars = !lightContent
    }
    // 离开阅读器（或切换主题）时恢复成应用主题的明暗，避免影响其它页面
    DisposableEffect(activity, view) {
        onDispose {
            controller.isAppearanceLightStatusBars = !restoreContent
            controller.isAppearanceLightNavigationBars = !restoreContent
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
