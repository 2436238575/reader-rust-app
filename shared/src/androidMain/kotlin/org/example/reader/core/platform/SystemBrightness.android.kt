package org.example.reader.core.platform

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** 系统亮度基准：`Settings.System.SCREEN_BRIGHTNESS` 在绝大多数设备上是 0..255。 */
private const val SYSTEM_BRIGHTNESS_MAX = 255f

/**
 * 读系统屏幕亮度。
 *
 * 只读 `Settings.System.SCREEN_BRIGHTNESS`，不需要任何权限（写才需要 `WRITE_SETTINGS`）。
 * 自动亮度开启时这个值仍会随环境更新，正是我们要的「用户此刻看到的亮度」。
 *
 * 返回 `null` 的三种情况都退回「跟随系统」（`ApplyWindowBrightness` 收到 1f 时不覆盖）：
 * 读不到（受限 / 厂商定制）、值为 0、抛出异常。
 * 值为 0 不能当默认值用：`screenBrightness = 0` 在 Android 上是黑屏，不是最暗。
 */
@Composable
actual fun rememberSystemBrightness(): Float? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val raw = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
            )
            (raw / SYSTEM_BRIGHTNESS_MAX).takeIf { it > 0f }?.coerceIn(0.01f, 1f)
        }.getOrNull()
    }
}
