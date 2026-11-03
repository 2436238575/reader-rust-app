package org.example.reader.core.util

import androidx.compose.runtime.Composable

/**
 * 系统栏图标明暗。
 *
 * 阅读器可以切成暗色主题（黑底白字），此时状态栏/导航栏图标必须是浅色，
 * 否则时间、信号这些系统图标在暗背景上完全看不见。
 *
 * @param lightContent true = 浅色图标（用于深色背景）
 * @param restoreContent 离开本页面时恢复成哪种（一般传应用主题是否为暗色）
 */
@Composable
expect fun SystemBarsAppearance(lightContent: Boolean, restoreContent: Boolean)
