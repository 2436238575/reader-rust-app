package org.example.reader.core.font

import kotlinx.coroutines.flow.StateFlow
import org.example.reader.core.settings.ReaderFont

/**
 * 随仓库分发、按需下载的字体文件名（`fonts/` 目录）。
 *
 * 鸿蒙/小米字体体积大（单字重 7.5–7.9MB），不随 APK 打包；许可证要求
 * **以未修改的原始文件**分发，所以仓库里存原件、App 下载原件，不做子集化。
 */
val ReaderFont.remoteFileName: String?
    get() = when (this) {
        ReaderFont.Harmony -> "HarmonyOS_Sans_SC_Regular.ttf"
        ReaderFont.MiSans -> "MiSans-Regular.ttf"
        else -> null
    }

/**
 * 字体文件的本地缓存与下载。
 *
 * 只处理「需要联网取回」的字体；系统字族（系统/黑体/宋体/等宽）不走这里。
 * 下载到应用私有目录，之后完全离线可用；换设备重新下载。
 */
interface FontStore {

    /** 下载完成或缓存被清理后自增，用来触发重组（字体族要重新解析）。 */
    val revision: StateFlow<Int>

    /** 已缓存的本地绝对路径；未下载或文件损坏返回 null。 */
    fun cachedPath(font: ReaderFont): String?

    /**
     * 确保字体可用（已缓存直接成功）。
     *
     * 失败返回 failure，由调用方提示并回退系统字体——阅读器不能因为字体下载失败变空白。
     */
    suspend fun ensure(font: ReaderFont): Result<Unit>
}
