package org.example.reader.core.platform

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 拿不到文件名时的兜底；后端按扩展名区分 TXT / EPUB，`.txt` 更安全。 */
private const val DEFAULT_FILE_NAME = "book.txt"

/** 100MB 的中文提示文案。 */
private const val TOO_LARGE_MESSAGE = "文件超过 100MB 上限，请改用更小的 TXT / EPUB"

@Composable
actual fun rememberBookFilePicker(onResult: (PickResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // null = 用户自己取消，不是错误，静默返回
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // 几十 MB 的文件不能在主线程读
            val result = withContext(Dispatchers.IO) { readPickedFile(context, uri) }
            onResult(result)
        }
    }
    // EPUB 的 MIME 在不同文件管理器里不一致，统一放开由扩展名判断
    return { launcher.launch(arrayOf("*/*")) }
}

/**
 * 读取 SAF 选中的文件，任何失败都折成带原因的 [PickResult.Failure]。
 *
 * 先查 `OpenableColumns.SIZE` 再读内容：超限的文件在读之前就拒掉，
 * 避免把上百 MB 一次性读进堆里触发 OOM。部分 provider 不提供 SIZE
 * （返回 -1），此时退化为「读完后按实际字节数再判一次」。
 */
private fun readPickedFile(context: Context, uri: Uri): PickResult {
    val resolver = context.contentResolver
    val (name, declaredSize) = queryOpenableColumns(resolver, uri)
    if (declaredSize > MAX_BOOK_FILE_BYTES) return PickResult.Failure(TOO_LARGE_MESSAGE)

    val mimeType = runCatching { resolver.getType(uri) }.getOrNull().orEmpty()
        .ifBlank { "application/octet-stream" }

    return try {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return PickResult.Failure("无法读取文件：无法打开输入流（$name）")
        if (bytes.size.toLong() > MAX_BOOK_FILE_BYTES) {
            return PickResult.Failure(TOO_LARGE_MESSAGE)
        }
        if (bytes.isEmpty()) {
            return PickResult.Failure("无法读取文件：「$name」是空文件")
        }
        PickResult.Success(PickedBookFile(name = name, bytes = bytes, mimeType = mimeType))
    } catch (e: OutOfMemoryError) {
        PickResult.Failure("无法读取文件：内存不足，文件过大（$name）")
    } catch (e: SecurityException) {
        PickResult.Failure("无法读取文件：没有访问权限，请重新选择（${e.message ?: e::class.simpleName}）")
    } catch (e: Exception) {
        PickResult.Failure("无法读取文件：${e.message ?: e::class.simpleName}")
    }
}

/**
 * 查显示名与声明大小。
 *
 * 查询本身可能抛异常或返回空游标（部分第三方 provider），这些都**不致命**：
 * 拿不到就按兜底名 + 未知大小继续，交给读取阶段判断。
 */
private fun queryOpenableColumns(
    resolver: ContentResolver,
    uri: Uri,
): Pair<String, Long> {
    var name = DEFAULT_FILE_NAME
    var size = -1L
    runCatching {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: name
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
    }
    return name to size
}
