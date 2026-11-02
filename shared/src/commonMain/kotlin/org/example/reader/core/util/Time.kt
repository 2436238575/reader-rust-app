package org.example.reader.core.util

/**
 * 当前时间（Unix 毫秒）。
 *
 * commonMain 不能直接引用 `System.currentTimeMillis()`，由 Android 侧提供实现。
 * 只用于本地排序/展示时间戳，不参与协议计算。
 */
expect fun nowMillis(): Long
