package org.example.reader.core.util

import androidx.compose.runtime.Composable

/**
 * 拦截系统返回键。
 *
 * commonMain 不能直接引用 `androidx.activity.compose.BackHandler`，
 * 由 Android 侧提供实现。各覆盖层（阅读器/详情/目录、书架多选）都要挂上，
 * 否则系统返回会直接退出应用。
 */
@Composable
expect fun AppBackHandler(enabled: Boolean = true, onBack: () -> Unit)
