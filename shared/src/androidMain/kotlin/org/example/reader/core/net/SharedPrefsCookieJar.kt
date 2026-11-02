package org.example.reader.core.net

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 基于 SharedPreferences 的 Cookie 持久化。
 *
 * 后端会话是两枚 HttpOnly Cookie（`reader_access` / `reader_refresh`），
 * 客户端必须自己存盘，否则杀进程就掉登录态。
 *
 * 序列化用 OkHttp 自己的 `Cookie.parse` / `toString()`，但**必须连域名一起存**：
 * `Cookie.toString()` 对 hostOnly Cookie 不输出 `domain=`，回读时 `parse`
 * 会退化成基准 URL 的域名，Cookie 就绑错主机、请求不再携带。所以这里把
 * `cookie.domain` 单独存一列，回读时用它构造基准 URL。
 */
class SharedPrefsCookieJar(
    context: Context,
) : CookieJar {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val lock = Any()
    private val cache = LinkedHashMap<String, Cookie>()

    init {
        loadPersisted()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            cookies.forEach { cookie -> cache[key(cookie)] = cookie }
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        // 顺手剔除已过期的，避免失效会话留在磁盘上。
        val now = System.currentTimeMillis()
        val expired = cache.values.filter { it.expiresAt < now }
        if (expired.isNotEmpty()) {
            expired.forEach { cache.remove(key(it)) }
            persist()
        }
        cache.values.filter { it.matches(url) }
    }

    fun clearAll() {
        synchronized(lock) {
            cache.clear()
            prefs.edit().remove(KEY_COOKIES).apply()
        }
    }

    private fun loadPersisted() {
        val raw = prefs.getString(KEY_COOKIES, null) ?: return
        raw.split("\n").forEach { line ->
            if (line.isBlank()) return@forEach
            val separator = line.indexOf(FIELD_SEPARATOR)
            if (separator <= 0) return@forEach
            val domain = line.substring(0, separator)
            val cookieText = line.substring(separator + 1)
            // 用真实域名做基准 URL，保证 hostOnly 与 domain 属性都能正确还原。
            val baseUrl = "https://$domain/".toHttpUrlOrNull() ?: return@forEach
            val parsed = Cookie.parse(baseUrl, cookieText) ?: return@forEach
            cache[key(parsed)] = parsed
        }
    }

    private fun persist() {
        val encoded = cache.values.joinToString("\n") { "${it.domain}$FIELD_SEPARATOR$it" }
        prefs.edit().putString(KEY_COOKIES, encoded).apply()
    }

    private fun key(cookie: Cookie): String = "${cookie.name}|${cookie.domain}|${cookie.path}"

    private fun String.toHttpUrlOrNull(): HttpUrl? = runCatching { toHttpUrl() }.getOrNull()

    private companion object {
        const val PREFS_NAME = "reader_cookies"
        const val KEY_COOKIES = "cookies"

        /** 域名字段与 Cookie 串之间的分隔符；用不可见控制符避免与 Cookie 值冲突。 */
        const val FIELD_SEPARATOR = '\u0001'
    }
}
