package org.example.reader.core.font

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.example.reader.core.settings.ReaderFont
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * 字体按需下载（GitHub Raw）。
 *
 * 仓库根目录的 `fonts/` 存放**未修改的原始 TTF**，与正式包同源；App 按「字体 + 字重」
 * 下载到 `filesDir/fonts/`，之后离线可用。下载走独立 OkHttpClient（不带会话 Cookie，
 * 避免把 reader 的凭据带到第三方域）。
 *
 * 完整性只做「字节数下限 + 先写 .part 再改名」：字体许可禁止改文件，不能做子集校验；
 * 下限能挡住错误页/半截下载，真正的字形错误交给系统字体回退。
 */
class AndroidFontStore(context: Context) : FontStore {

    private val directory = File(context.filesDir, "fonts")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _revision = MutableStateFlow(0)
    override val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _state = MutableStateFlow<FontDownloadState>(FontDownloadState.Idle)
    override val state: StateFlow<FontDownloadState> = _state.asStateFlow()

    /** 同一字重的并发 ensure 串行化，避免两次下载互相覆盖 .part。 */
    private val lock = Mutex()

    @Volatile
    private var currentCall: Call? = null

    override fun cachedPath(font: ReaderFont, weight: Int): String? {
        val file = font.fileForWeight(weight) ?: return null
        val cached = File(directory, file.fileName)
        return if (cached.isFile && cached.length() >= MIN_FONT_BYTES) cached.absolutePath else null
    }

    override fun bestCachedPath(font: ReaderFont, weight: Int): String? {
        val candidates = font.downloadableWeights
        if (candidates.isEmpty()) return null
        return candidates
            .filter { File(directory, it.fileName).let { f -> f.isFile && f.length() >= MIN_FONT_BYTES } }
            .minWithOrNull(compareBy({ abs(it.weight - weight) }, { -it.weight }))
            ?.let { File(directory, it.fileName).absolutePath }
    }

    override suspend fun ensure(font: ReaderFont, weight: Int): Result<Unit> = lock.withLock {
        if (cachedPath(font, weight) != null) {
            _state.value = FontDownloadState.Idle
            return@withLock Result.success(Unit)
        }
        val file = font.fileForWeight(weight)
            ?: return@withLock Result.success(Unit) // 系统字族：无需下载
        withContext(Dispatchers.IO) {
            download(font, file)
        }
    }

    private suspend fun download(font: ReaderFont, file: FontWeightFile): Result<Unit> {
        val target = File(directory, file.fileName)
        val temp = File(directory, "${file.fileName}.part")
        _state.value = FontDownloadState.Running(font, file.weight, 0, 0)
        val job = currentCoroutineContext()[Job]
        val cancellation = job?.invokeOnCompletion { cause ->
            if (cause is CancellationException) currentCall?.cancel()
        }
        return try {
            directory.mkdirs()
            val call = client.newCall(
                Request.Builder().url(FONT_RAW_BASE + file.fileName).build(),
            )
            currentCall = call
            call.execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("响应为空")
                val total = body.contentLength()
                var downloaded = 0L
                var lastReport = 0L
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            // 每 256KB 刷一次进度，避免 StateFlow 被小包刷屏
                            if (downloaded - lastReport >= PROGRESS_STEP || downloaded == total) {
                                lastReport = downloaded
                                _state.value = FontDownloadState.Running(
                                    font = font,
                                    weight = file.weight,
                                    downloaded = downloaded,
                                    total = total,
                                )
                            }
                        }
                    }
                }
                if (temp.length() < MIN_FONT_BYTES) {
                    error("文件不完整（${temp.length()} 字节）")
                }
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            }
            _state.value = FontDownloadState.Idle
            _revision.value = _revision.value + 1
            Result.success(Unit)
        } catch (e: CancellationException) {
            temp.delete()
            _state.value = FontDownloadState.Idle
            throw e
        } catch (e: Exception) {
            temp.delete()
            val message = e.message ?: e::class.simpleName ?: "下载失败"
            _state.value = FontDownloadState.Failed(font, file.weight, message)
            Result.failure(e)
        } finally {
            currentCall = null
            cancellation?.dispose()
        }
    }

    override fun cancel() {
        currentCall?.cancel()
        _state.value = FontDownloadState.Idle
    }

    override fun clearFailure() {
        if (_state.value is FontDownloadState.Failed) {
            _state.value = FontDownloadState.Idle
        }
    }

    private companion object {
        /** 与仓库 `fonts/` 一一对应；换仓库/分支时只改这一处。 */
        const val FONT_RAW_BASE =
            "https://raw.githubusercontent.com/2436238575/reader-rust-app/master/fonts/"

        /** 完整字体 7.4MB 以上；低于 3MB 只能是错误页或半截文件。 */
        const val MIN_FONT_BYTES = 3L * 1024 * 1024

        const val PROGRESS_STEP = 256L * 1024
    }
}
