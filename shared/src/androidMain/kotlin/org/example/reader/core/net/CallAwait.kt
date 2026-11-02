package org.example.reader.core.net

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 把 OkHttp 异步请求桥接到协程，并**在回调线程里读完响应体**。
 *
 * 不能在 resume 之后才读 body：`enqueue` 的回调只保证响应头到达，body 仍是
 * 网络流；协程恢复到主线程后再读 `body.bytes()` 会触发
 * `NetworkOnMainThreadException`。小响应因为已被完整缓冲所以看不出问题，
 * 一旦响应变大（比如上千章的目录）就必崩——这个坑非常隐蔽。
 *
 * 顺带把协程取消同步到 OkHttp 的 Call，避免请求继续占用连接。
 */
suspend fun Call.awaitBuffered(): HttpResponse = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        runCatching { cancel() }
    }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) {
                continuation.resumeWithException(e)
            }
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                // 此刻仍在 OkHttp 的工作线程，允许做阻塞读。
                val buffered = response.use {
                    HttpResponse(
                        code = it.code,
                        body = it.body?.bytes() ?: ByteArray(0),
                        headers = it.headers.toMultimap(),
                    )
                }
                if (!continuation.isCancelled) {
                    continuation.resume(buffered)
                }
            } catch (e: Exception) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(e)
                }
            }
        }
    })
}
