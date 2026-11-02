package org.example.reader.core.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * 统一的 API 调用结果。
 *
 * 后端把错误文案放在 `errorMsg`，且 HTTP 状态码同样是判据（401 = 需要登录、
 * 408 = 请求超时、413 = 反代体积上限），所以两者都要带出来。
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(
        val code: Int,
        val message: String,
    ) : ApiResult<Nothing> {
        /** 会话失效，需要重新登录。 */
        val needsLogin: Boolean get() = code == 401
    }

    fun getOrNull(): T? = (this as? Success)?.value
}

/** 成功时执行 [block]；失败原样透传。 */
fun <T> ApiResult<T>.onSuccess(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) block(value)
    return this
}

/** 失败时执行 [block]；成功原样透传。 */
fun <T> ApiResult<T>.onFailure(block: (ApiResult.Failure) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) block(this)
    return this
}

/** 变换成功值形状，失败透传。 */
fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

/**
 * 解析 ApiResponse 包装：`isSuccess=false` 时取 `errorMsg` 作为失败原因，
 * 成功时把 `data` 交给调用方的序列化器解码。
 *
 * `httpCode` 传 HTTP 状态码（401/408/413 等）；本地解析失败时调用方传 -1。
 */
inline fun <reified T> Json.parseApiResponse(body: String, httpCode: Int): ApiResult<T> {
    val root: JsonObject = try {
        parseToJsonElement(body) as? JsonObject
            ?: return ApiResult.Failure(httpCode, "响应格式错误")
    } catch (e: Exception) {
        return ApiResult.Failure(httpCode, "响应解析失败：${e.message ?: e::class.simpleName}")
    }

    val success = (root["isSuccess"] as? JsonPrimitive)?.booleanOrNull ?: false
    val errorMsg = (root["errorMsg"] as? JsonPrimitive)?.content.orEmpty()
    if (!success) {
        return ApiResult.Failure(httpCode, errorMsg.ifBlank { "请求失败" })
    }

    // 端点只关心「成功/失败」时（logout、saveBookProgress 等），data 的形状
    // 无意义，别让解码失败把成功判成失败。
    if (T::class == Unit::class) {
        @Suppress("UNCHECKED_CAST")
        return ApiResult.Success(Unit as T)
    }

    val data = root["data"] ?: JsonNull
    return try {
        if (data is JsonNull) {
            ApiResult.Success(decodeFromJsonElement<T>(JsonNull))
        } else {
            ApiResult.Success(decodeFromJsonElement<T>(data))
        }
    } catch (e: Exception) {
        ApiResult.Failure(httpCode, "数据解析失败：${e.message ?: e::class.simpleName}")
    }
}
