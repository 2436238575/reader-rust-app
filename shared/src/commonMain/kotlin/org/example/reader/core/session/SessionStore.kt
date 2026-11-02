package org.example.reader.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 服务端地址。
 *
 * 用户填的是**部署前缀**（例如 `https://example.org/read/reader3` 或
 * `http://10.0.2.2:8080/reader3`），即后端 `/reader3` 命名空间所在的完整路径。
 * 之所以让用户填到 `/reader3` 为止，是因为子路径部署时 Cookie Path 也带前缀
 * （后端 `COOKIE_PATH=/read/reader3`），客户端要按同一前缀拼资源直链。
 */
data class ServerConfig(
    /** 规范化后的接口基址，形如 `https://example.org/read/reader3`（无尾部斜杠）。 */
    val apiBase: String,
) {
    /** 部署前缀（去掉末尾 `/reader3`），用于拼服务端返回的绝对路径资源。 */
    val appBase: String
        get() = if (apiBase.endsWith(API_SUFFIX)) {
            apiBase.removeSuffix(API_SUFFIX)
        } else {
            apiBase
        }

    /** 把服务端返回的绝对路径（如 `/reader3/image/xxx`）拼成可直接请求的 URL。 */
    fun resolveResource(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val normalized = if (path.startsWith("/")) path else "/$path"
        return appBase + normalized
    }

    companion object {
        const val API_SUFFIX = "/reader3"

        /**
         * AVD 调试默认地址。
         *
         * 走 `adb reverse tcp:8080 tcp:8080` 建立的回环隧道，而不是 10.0.2.2：
         * - 后端默认只绑 127.0.0.1，经 adb 隧道转发最稳，不需要开防火墙入站；
         * - Host 头是 127.0.0.1（回环），后端 `COOKIE_SECURE=auto` 不会给会话
         *   Cookie 加 Secure，明文 HTTP 下 Cookie 能正常回传；
         *   换成 10.0.2.2 等非回环地址时 Cookie 会被加 Secure 而失效。
         */
        const val DEFAULT_DEBUG_API_BASE = "http://127.0.0.1:8080/reader3"

        /**
         * 规范化用户输入：
         * - 补全缺失的 `/reader3` 后缀
         * - 去掉尾部斜杠
         * - 空输入返回 null
         */
        fun normalize(input: String): String? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
            return if (trimmed.endsWith(API_SUFFIX)) trimmed else trimmed + API_SUFFIX
        }
    }
}

/** 登录态。 */
sealed interface AuthState {
    /** 尚未登录。 */
    data object Unknown : AuthState

    data object LoggedOut : AuthState

    data class LoggedIn(
        val username: String,
        val uid: Long,
    ) : AuthState

    val isLoggedIn: Boolean get() = this is LoggedIn
}

/**
 * 会话容器：保存服务地址与登录态。
 *
 * 只放内存 + DataStore 持久化地址/凭据，**不**保存 accessToken 字符串
 * （后端下发的是 HttpOnly Cookie，客户端拿不到也不需要）；
 * 只需要记住「已登录 + 用户名」用于界面展示。
 */
class SessionStore {
    private val _serverConfig = MutableStateFlow<ServerConfig?>(null)
    val serverConfig: StateFlow<ServerConfig?> = _serverConfig.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun updateServerConfig(config: ServerConfig?) {
        _serverConfig.value = config
    }

    fun updateAuthState(state: AuthState) {
        _authState.value = state
    }
}
