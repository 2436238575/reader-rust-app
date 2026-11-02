package org.example.reader.core

import coil3.ImageLoader
import org.example.reader.core.net.ReaderApi
import org.example.reader.core.recent.RecentBooksStore
import org.example.reader.core.session.SessionStore
import org.example.reader.core.settings.KeyValueStore

/**
 * 应用依赖容器。
 *
 * commonMain 只声明需要的接口，Android 侧在 `AndroidDependencies` 里装配
 * OkHttp / SharedPreferences。这样 UI 层完全不感知平台实现。
 */
interface AppDependencies {
    val api: ReaderApi
    val session: SessionStore
    val settings: KeyValueStore

    /**
     * 图片加载器。必须与 API 共用同一个 HTTP 客户端，否则取不到会话 Cookie，
     * 封面与正文配图会全部 401。
     */
    val imageLoader: ImageLoader

    /** 最近阅读本地镜像：阅读器写入、最近页读取，必须共用同一实例。 */
    val recentBooks: RecentBooksStore

    /** 清理会话凭据（Cookie + 本地登录提示）。 */
    fun clearSession()
}
