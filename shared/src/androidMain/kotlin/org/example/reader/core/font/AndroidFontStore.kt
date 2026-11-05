package org.example.reader.core.font

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.example.reader.core.settings.ReaderFont
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 字体按需下载（GitHub Raw）。
 *
 * 仓库根目录的 `fonts/` 存放**未修改的原始 TTF**，与正式包同源；App 首次选用时下载到
 * `filesDir/fonts/`，之后离线可用。下载走独立 OkHttpClient（不带会话 Cookie，
 * 避免把 reader 的凭据带到第三方域）。
 *
 * 完整性只做「字节数下限 + 先写 .part 再改名」：字体许可禁止改文件，不能做子集校验；
 * 下限能挡住错误页/半截下载，真正的字形错误交给系统字体回退。
 */
class AndroidFontStore(context: Context) : FontStore {

    private val directory = File(context.filesDir, "fonts")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _revision = MutableStateFlow(0)
    override val revision: StateFlow<Int> = _revision.asStateFlow()

    /** 同一字体的并发 ensure 串行化，避免两次下载互相覆盖 .part。 */
    private val lock = Mutex()

    override fun cachedPath(font: ReaderFont): String? {
        val name = font.remoteFileName ?: return null
        val file = File(directory, name)
        return if (file.isFile && file.length() >= MIN_FONT_BYTES) file.absolutePath else null
    }

    override suspend fun ensure(font: ReaderFont): Result<Unit> = lock.withLock {
        if (cachedPath(font) != null) return@withLock Result.success(Unit)
        val name = font.remoteFileName ?: return@withLock Result.success(Unit)
        withContext(Dispatchers.IO) {
            runCatching {
                directory.mkdirs()
                val temp = File(directory, "$name.part")
                val request = Request.Builder().url(FONT_RAW_BASE + name).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("响应为空")
                    body.byteStream().use { input ->
                        temp.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                    }
                }
                if (temp.length() < MIN_FONT_BYTES) {
                    temp.delete()
                    error("字体文件不完整（${temp.length()} 字节）")
                }
                val target = File(directory, name)
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            }
        }.onSuccess {
            _revision.value = _revision.value + 1
        }
    }

    private companion object {
        /** 与仓库 `fonts/` 一一对应；换仓库/分支时只改这一处。 */
        const val FONT_RAW_BASE =
            "https://raw.githubusercontent.com/2436238575/reader-rust-app/master/fonts/"

        /** 完整字体 7.5MB 以上；低于 3MB 只能是错误页或半截文件。 */
        const val MIN_FONT_BYTES = 3L * 1024 * 1024
    }
}
