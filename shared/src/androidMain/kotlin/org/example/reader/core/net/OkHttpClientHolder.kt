package org.example.reader.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * OkHttp 传输实现。
 *
 * 会话语义与 Web 前端一致：
 * - 认证走后端下发的 HttpOnly Cookie（`reader_access` / `reader_refresh`），
 *   由 [SharedPrefsCookieJar] 持久化，进程重启后仍是登录态；
 * - 401 由 OkHttp 自带的 [Authenticator] 接管：静默换令牌后自动重放原请求。
 *   用库的扩展点而非自己包重试层，能正确覆盖重定向、请求体重放与重试计数。
 */
class OkHttpClientHolder(
    private val baseUrlProvider: () -> String,
    private val cookieJar: CookieJar,
    /** 刷新彻底失败（Refresh Token 也过期）时回调，用于切到登录页。 */
    private val onSessionExpired: () -> Unit = {},
) : HttpClient {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * 刷新令牌专用客户端：**不挂 Authenticator**。
     * 否则 /refreshToken 返回 401 时会再触发刷新，形成递归。
     */
    private val refreshClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(
            HttpLoggingInterceptor { message -> android.util.Log.d("ReaderHttp", message) }
                .setLevel(HttpLoggingInterceptor.Level.BASIC),
        )
        .connectTimeout(30, TimeUnit.SECONDS)
        // 读写不设上限：目录/正文可能等上游 20s+，大文件上传要几分钟。
        // 真正的时限交给每请求的 callTimeout。
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .authenticator(RefreshAuthenticator())
        .build()

    /**
     * 供图片加载复用：Coil 必须走同一个客户端，否则拿不到会话 Cookie，
     * 封面/配图会全部 401。
     */
    val callFactory: OkHttpClient get() = baseClient

    /** 按超时值缓存派生客户端：`newBuilder()` 复用连接池，代价很小。 */
    private val timeoutClients = ConcurrentHashMap<Long, OkHttpClient>()

    private fun clientFor(timeoutMillis: Long): OkHttpClient =
        timeoutClients.getOrPut(timeoutMillis) {
            baseClient.newBuilder()
                .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build()
        }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val okRequest = request.toOkHttpRequest()
        android.util.Log.d("ReaderHttp", "→ ${okRequest.method} ${okRequest.url}")
        // awaitBuffered 已在 OkHttp 线程读完 body，这里拿到的是完整内存响应。
        return clientFor(request.timeoutMillis).newCall(okRequest).awaitBuffered()
    }

    /**
     * 同步刷新会话（给 [Authenticator] 在工作线程上调用）。
     *
     * 只认 `reader_refresh` Cookie，请求体留空——与后端契约一致。
     */
    internal fun refreshBlocking(): Boolean {
        val url = buildUrl(baseUrlProvider(), "/refreshToken", emptyMap())
        val request = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return try {
            refreshClient.newCall(request).execute().use { response ->
                val ok = response.isSuccessful
                if (!ok && response.code == 401) onSessionExpired()
                ok
            }
        } catch (e: IOException) {
            false
        }
    }

    override fun stream(request: HttpRequest): Flow<SseEvent> = callbackFlow {
        // SSE 不受 callTimeout 约束（服务端会长时间挂住），只保留连接超时。
        val client = baseClient.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).build()
        val sseRequest = request.toOkHttpRequest().newBuilder()
            .header("Accept", "text/event-stream")
            .build()
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                when (type) {
                    "error" -> trySend(SseEvent.Error(data))
                    "end" -> trySend(SseEvent.End)
                    else -> trySend(SseEvent.Message(data))
                }
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(SseEvent.End)
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val code = response?.code
                if (code == 401) onSessionExpired()
                val message = when {
                    code == 401 -> "需要登录"
                    t != null -> t.message ?: "连接中断"
                    else -> "SSE 连接失败（HTTP ${code ?: 0}）"
                }
                close(IOException(message))
            }
        }
        val source = EventSources.createFactory(client).newEventSource(sseRequest, listener)
        awaitClose { source.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun HttpRequest.toOkHttpRequest(): Request {
        val url = buildUrl(baseUrlProvider(), path, query)
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        val okhttpBody = when (val b = body) {
            is RequestBody.Json -> b.json.toRequestBody(JSON_MEDIA_TYPE)
            is RequestBody.Raw -> b.bytes.toRequestBody(b.contentType.toMediaType())
            is RequestBody.Multipart -> {
                val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                b.fields.forEach { (k, v) -> multipart.addFormDataPart(k, v) }
                b.files.forEach { part ->
                    multipart.addFormDataPart(
                        part.name,
                        part.filename,
                        part.bytes.toRequestBody(part.contentType.toMediaType()),
                    )
                }
                multipart.build()
            }
            null -> null
        }
        // GET 必须显式传 null body，否则 OkHttp 抛「method GET must not have a body」。
        builder.method(method, okhttpBody)
        return builder.build()
    }

    /**
     * 401 自动续期。
     *
     * 与前端 `api/http.ts` 的 401 分支一致：
     * - 同一请求最多重放一次（`priorResponse` 计数 > 1 即已重试过）；
     * - `/login` 与 `/refreshToken` 的 401 绝不触发刷新，避免递归；
     * - 刷新失败返回 null，401 照常上抛给调用方拉起登录页。
     */
    private inner class RefreshAuthenticator : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (responseCount(response) > 1) return null
            val path = response.request.url.encodedPath
            if (path.endsWith("/refreshToken") || path.endsWith("/login")) return null
            if (!refreshBlocking()) return null
            // Cookie 已由 CookieJar 更新，重放原请求即可。
            return response.request.newBuilder().build()
        }

        private fun responseCount(response: Response): Int {
            var count = 1
            var prior = response.priorResponse
            while (prior != null) {
                count++
                prior = prior.priorResponse
            }
            return count
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun buildUrl(base: String, path: String, query: Map<String, String>): HttpUrl {
            val normalizedBase = base.trimEnd('/')
            val normalizedPath = if (path.startsWith("/")) path else "/$path"
            val url = "$normalizedBase$normalizedPath".toHttpUrl()
            if (query.isEmpty()) return url
            val builder = url.newBuilder()
            query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
            return builder.build()
        }
    }
}
