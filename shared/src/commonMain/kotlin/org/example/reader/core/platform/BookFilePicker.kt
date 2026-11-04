package org.example.reader.core.platform

import androidx.compose.runtime.Composable

/** 用户选中的本地书籍文件。 */
data class PickedBookFile(
    val name: String,
    val bytes: ByteArray,
    val mimeType: String,
)

/**
 * 选择/读取结果。
 *
 * 为什么要区分成功与失败：旧实现读取失败时直接 `.getOrNull()` 把结果吞掉，
 * 用户点完「导入」什么都不发生，无从判断该重试还是换文件（R-20）。
 * 失败必须带可读原因，由上层用 Snackbar 原样展示。
 */
sealed interface PickResult {
    /** 拿到文件内容，可以上传了。 */
    data class Success(val file: PickedBookFile) : PickResult

    /** 未能读到文件，[message] 是给用户看的中文原因。 */
    data class Failure(val message: String) : PickResult
}

/**
 * 本地书导入上限（100MB）。
 *
 * 与后端反向代理的 `client_max_body_size`（建议 ≥100m）对齐：超过这个体积
 * 上传一定会被 413 打断，与其读进内存再失败，不如在选择阶段就拦下来。
 */
const val MAX_BOOK_FILE_BYTES: Long = 100L * 1024 * 1024

/**
 * 系统文件选择器。
 *
 * commonMain 只声明「拿到文件或明确失败」这件事，Android 侧用
 * `ActivityResultContracts.OpenDocument` + ContentResolver 读取。
 * 返回的 lambda 用来主动拉起选择器。
 *
 * 用户在选择器里取消**不会**回调（那是用户的主动放弃，不是错误）。
 */
@Composable
expect fun rememberBookFilePicker(onResult: (PickResult) -> Unit): () -> Unit
