package org.example.reader.core.net

import kotlinx.serialization.json.Json

/**
 * 全局 JSON 配置。
 *
 * - `ignoreUnknownKeys`：后端字段只增不减，客户端不能因为多一个字段就崩；
 * - `coerceInputValues`：后端可能把可选字段下发成 null，靠默认值兜住；
 * - `explicitNulls = false`：请求体里不输出 `null`，与前端 axios 行为一致
 *   （后端不少 handler 把「字段存在但为 null」当成显式置空）。
 */
val ReaderJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}
