package org.example.reader.core.font

import kotlinx.coroutines.flow.StateFlow
import org.example.reader.core.settings.ReaderFont
import kotlin.math.abs

/**
 * 一个可下载字重：仓库 `fonts/` 下的文件名与它实际的 `usWeightClass`。
 *
 * 文件名是契约——改名会让已缓存用户 404，`FontStore` 与本表必须同时改。
 */
data class FontWeightFile(val fileName: String, val weight: Int)

/**
 * 需要按需下载的字重表（仓库 `fonts/`）。
 *
 * 字体厂商许可禁止修改字体文件，所以只分发**原样文件**，不做子集化；体积大（单字重 7.5–8MB）
 * 因此不随 APK 分发，选中哪个字重下载哪个，其余按需。
 *
 * MiSans 的 `Normal`(400) / `Demibold`(600) 与 `Regular` / `Semibold` 字重相同，
 * 滑块只能按数值选，故不重复收录（只保留 Regular / Semibold）。
 */
val ReaderFont.downloadableWeights: List<FontWeightFile>
    get() = when (this) {
        ReaderFont.Harmony -> listOf(
            FontWeightFile("HarmonyOS_Sans_SC_Thin.ttf", 250),
            FontWeightFile("HarmonyOS_Sans_SC_Light.ttf", 300),
            FontWeightFile("HarmonyOS_Sans_SC_Regular.ttf", 400),
            FontWeightFile("HarmonyOS_Sans_SC_Medium.ttf", 500),
            FontWeightFile("HarmonyOS_Sans_SC_Bold.ttf", 700),
            FontWeightFile("HarmonyOS_Sans_SC_Black.ttf", 900),
        )

        ReaderFont.MiSans -> listOf(
            FontWeightFile("MiSans-Thin.ttf", 100),
            FontWeightFile("MiSans-ExtraLight.ttf", 200),
            FontWeightFile("MiSans-Light.ttf", 300),
            FontWeightFile("MiSans-Regular.ttf", 400),
            FontWeightFile("MiSans-Medium.ttf", 500),
            FontWeightFile("MiSans-Semibold.ttf", 600),
            FontWeightFile("MiSans-Bold.ttf", 700),
            FontWeightFile("MiSans-Heavy.ttf", 900),
        )

        else -> emptyList()
    }

/** 该字体是否需要联网下载。 */
val ReaderFont.isDownloadable: Boolean get() = downloadableWeights.isNotEmpty()

/**
 * 设置里的字重（100..900）→ 最接近的可下载文件。
 *
 * 距离相同时取**更重**的一侧（600 在 500/700 之间选 700），与阅读器「宁粗勿细」的取向一致。
 * 系统字族没有可下载文件，返回 null。
 */
fun ReaderFont.fileForWeight(weight: Int): FontWeightFile? =
    downloadableWeights.minWithOrNull(
        compareBy({ abs(it.weight - weight) }, { -it.weight }),
    )

/** 字体下载状态：设置面板据此显示进度、失败原因与取消/重试。 */
sealed interface FontDownloadState {
    data object Idle : FontDownloadState

    data class Running(
        val font: ReaderFont,
        val weight: Int,
        val downloaded: Long,
        val total: Long,
    ) : FontDownloadState {
        /** 0..1；服务端没给 Content-Length 时为 null（进度条改为不确定态）。 */
        val fraction: Float?
            get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else null
    }

    data class Failed(
        val font: ReaderFont,
        val weight: Int,
        val message: String,
    ) : FontDownloadState
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

    /** 当前下载状态，供设置面板显示进度/失败。 */
    val state: StateFlow<FontDownloadState>

    /** 精确字重的缓存路径；未下载或文件不完整返回 null。 */
    fun cachedPath(font: ReaderFont, weight: Int): String?

    /**
     * 已缓存字重里与 [weight] 最接近的路径。
     *
     * 目标字重还在下载时先用它顶替，正文不会突然跳回系统字体；下载完成后
     * [revision] 自增，自动切到精确字重。
     */
    fun bestCachedPath(font: ReaderFont, weight: Int): String?

    /**
     * 确保该字重可用（已缓存直接成功）。
     *
     * 失败返回 failure 并把 [state] 置为 [FontDownloadState.Failed]；调用方负责提示与重试。
     */
    suspend fun ensure(font: ReaderFont, weight: Int): Result<Unit>

    /** 取消正在进行的下载（切换字体/字重时调用）。 */
    fun cancel()

    /** 清掉失败态（重试前调用）。 */
    fun clearFailure()
}
