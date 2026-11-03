package org.example.reader.core.platform

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberBookFilePicker(onPicked: (PickedBookFile) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // 几十 MB 的文件不能在主线程读
            val picked = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    var name = "book.txt"
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: name
                            }
                        }
                    val mime = resolver.getType(uri) ?: "application/octet-stream"
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("无法读取所选文件")
                    PickedBookFile(name = name, bytes = bytes, mimeType = mime)
                }.getOrNull()
            }
            if (picked != null) onPicked(picked)
        }
    }
    // EPUB 的 MIME 在不同文件管理器里不一致，统一放开由扩展名判断
    return { launcher.launch(arrayOf("*/*")) }
}
