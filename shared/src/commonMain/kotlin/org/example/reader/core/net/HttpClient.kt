package org.example.reader.core.net

import kotlinx.coroutines.flow.Flow

/**
 * 传输层抽象。commonMain 只依赖这层契约，OkHttp 实现放在 androidMain，
 * 这样协议细节（Cookie、SSE、重试）不会渗进业务代码。
 */
interface HttpClient {
    /** 发起一次请求，返回原始响应。**不做**业务级解包。 */
    suspend fun execute(request: HttpRequest): HttpResponse

    /** 打开一条 SSE 流。服务端用 `data:` / `event: error` / `event: end` 三种帧。 */
    fun stream(request: HttpRequest): Flow<SseEvent>
}

data class HttpRequest(
    val method: String,
    /** 相对 `apiBase` 的路径，例如 `/getBookshelf`。 */
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val body: RequestBody? = null,
    val headers: Map<String, String> = emptyMap(),
    /** 单次请求超时（毫秒）。 */
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Long = 120_000
        /** 大文件上传用，与后端 `INBOUND_UPLOAD_TIMEOUT_SECS`（900s）对齐。 */
        const val UPLOAD_TIMEOUT_MILLIS: Long = 900_000
    }
}

data class HttpResponse(
    val code: Int,
    val body: ByteArray,
    val headers: Map<String, List<String>> = emptyMap(),
) {
    fun text(): String = body.decodeToString()

    fun isSuccessful(): Boolean = code in 200..299
}

/** 请求体。multipart 用于本地书导入与书源文件上传。 */
sealed interface RequestBody {
    data class Json(val json: String) : RequestBody

    data class Raw(val bytes: ByteArray, val contentType: String) : RequestBody

    data class Multipart(
        val fields: Map<String, String> = emptyMap(),
        val files: List<FilePart> = emptyList(),
    ) : RequestBody
}

data class FilePart(
    val name: String,
    val filename: String,
    val bytes: ByteArray,
    val contentType: String = "application/octet-stream",
)

/** SSE 帧。服务端默认帧是 `data:`，另有 `error` 与 `end` 两个具名事件。 */
sealed interface SseEvent {
    data class Message(val data: String) : SseEvent
    data class Error(val message: String) : SseEvent
    data object End : SseEvent
}
