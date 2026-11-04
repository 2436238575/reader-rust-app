package org.example.reader.core.platform

import androidx.compose.runtime.Composable

/**
 * 系统当前屏幕亮度（0..1）。读不到时返回 `null`。
 *
 * 用途只有一处：**进入阅读器时**给亮度滑杆取初值。
 *
 * 为什么用 `@Composable` 而不是普通函数：读系统亮度需要 `Context`，做成组合函数
 * 就能在各自平台用 `LocalContext`，不必把 `Context` 一路传到 `commonMain`。
 *
 * 为什么用 `remember` 而不是 `LaunchedEffect`：语义是「一次进入只读一次」。
 * - 从后台恢复不会重建组合，所以不会把用户在阅读中调过的亮度又盖回系统值；
 * - 退出阅读器再进来会重建组合，于是重新取一次系统亮度；
 * - 旋转屏幕会重建组合，可能多读一次，但读到的仍是系统值，不会覆盖用户进度。
 */
@Composable
expect fun rememberSystemBrightness(): Float?
