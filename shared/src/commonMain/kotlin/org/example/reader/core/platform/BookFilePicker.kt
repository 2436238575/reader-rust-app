package org.example.reader.core.platform

import androidx.compose.runtime.Composable

/** 用户选中的本地书籍文件。 */
data class PickedBookFile(
    val name: String,
    val bytes: ByteArray,
    val mimeType: String,
)

/**
 * 系统文件选择器。
 *
 * commonMain 只声明「拿到文件」这件事，Android 侧用
 * `ActivityResultContracts.OpenDocument` + ContentResolver 读取。
 * 返回的 lambda 用来主动拉起选择器。
 */
@Composable
expect fun rememberBookFilePicker(onPicked: (PickedBookFile) -> Unit): () -> Unit
