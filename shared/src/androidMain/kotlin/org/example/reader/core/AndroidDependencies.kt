package org.example.reader.core

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import org.example.reader.core.font.AndroidFontStore
import org.example.reader.core.font.FontStore
import org.example.reader.core.net.OkHttpClientHolder
import org.example.reader.core.net.ReaderApi
import org.example.reader.core.net.SharedPrefsCookieJar
import org.example.reader.core.recent.RecentBooksStore
import org.example.reader.core.session.AuthState
import org.example.reader.core.session.ServerConfig
import org.example.reader.core.session.SessionStore
import org.example.reader.core.settings.LayoutPreference
import org.example.reader.core.settings.ReaderSettingsStore
import org.example.reader.core.settings.SettingsKeys
import org.example.reader.core.settings.SharedPrefsKeyValueStore
import org.example.reader.core.text.ChineseConverter
import org.example.reader.core.text.OpenccChineseConverter
import kotlin.concurrent.thread

/**
 * Android 侧依赖装配。
 *
 * 服务地址运行时可改：`baseUrlProvider` 每次请求时读当前值，
 * 用户在登录页改地址后无需重建客户端。
 */
class AndroidDependencies(context: Context) : AppDependencies {

    private val appContext = context.applicationContext

    override val settings = SharedPrefsKeyValueStore(appContext)

    override val layout = LayoutPreference(settings)

    override val readerSettings = ReaderSettingsStore(settings)

    private val cookieJar = SharedPrefsCookieJar(appContext)

    override val session = SessionStore()

    private val http = OkHttpClientHolder(
        baseUrlProvider = { session.serverConfig.value?.apiBase ?: ServerConfig.DEFAULT_DEBUG_API_BASE },
        cookieJar = cookieJar,
        onSessionExpired = {
            // Refresh Token 也过期了：清掉本地登录标记，UI 会切回登录页。
            settings.putString(SettingsKeys.USERNAME, null)
            session.updateAuthState(AuthState.LoggedOut)
        },
    )

    override val api = ReaderApi(http)

    override val recentBooks = RecentBooksStore(settings)

    override val chineseConverter: ChineseConverter = OpenccChineseConverter()

    override val fonts: FontStore = AndroidFontStore(appContext)

    override val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .components {
            // 复用同一 OkHttp 客户端：带上 reader_access Cookie，封面才不会 401。
            add(OkHttpNetworkFetcherFactory(callFactory = { http.callFactory }))
        }
        .crossfade(true)
        .build()

    init {
        restoreSession()
        // OpenCC 词表解析是纯 CPU 初始化（桌面 JVM ~0.4s，设备更慢）。放到后台线程，
        // 避免用户首次切「繁体」时在渲染路径上卡住主线程；失败也不影响启动。
        thread(name = "opencc-warmup", isDaemon = true) { chineseConverter.warmUp() }
    }

    private fun restoreSession() {
        val storedBase = settings.getString(SettingsKeys.API_BASE)
        if (storedBase != null) {
            session.updateServerConfig(ServerConfig(storedBase))
        }
        val username = settings.getString(SettingsKeys.USERNAME)
        session.updateAuthState(
            if (username != null) AuthState.LoggedIn(username = username, uid = 0) else AuthState.LoggedOut,
        )
    }

    override fun clearSession() {
        cookieJar.clearAll()
        settings.putString(SettingsKeys.USERNAME, null)
        session.updateAuthState(AuthState.LoggedOut)
    }
}
