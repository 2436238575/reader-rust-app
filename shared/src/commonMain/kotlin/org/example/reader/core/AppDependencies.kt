package org.example.reader.core

import coil3.ImageLoader
import org.example.reader.core.font.FontStore
import org.example.reader.core.net.ReaderApi
import org.example.reader.core.recent.RecentBooksStore
import org.example.reader.core.session.SessionStore
import org.example.reader.core.settings.KeyValueStore
import org.example.reader.core.settings.LayoutPreference
import org.example.reader.core.settings.ReaderSettingsStore
import org.example.reader.core.text.ChineseConverter

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

    /** 列表 / 网格偏好：书架切换后，发现与搜索结果立即跟随。 */
    val layout: LayoutPreference

    /** 阅读器排版设置：设置面板改一项，正文立即重排。 */
    val readerSettings: ReaderSettingsStore

    /**
     * 图片加载器。必须与 API 共用同一个 HTTP 客户端，否则取不到会话 Cookie，
     * 封面与正文配图会全部 401。
     */
    val imageLoader: ImageLoader

    /** 最近阅读本地镜像：阅读器写入、最近页读取，必须共用同一实例。 */
    val recentBooks: RecentBooksStore

    /**
     * 正文简繁转换。
     *
     * 接口在 commonMain，实现在 androidMain（opencc4j）；只用于显示，
     * 快照里的原文与进度换算都不经过它。
     */
    val chineseConverter: ChineseConverter

    /** 需要按需下载的字体（小米 MiSans）的本地缓存与下载。 */
    val fonts: FontStore

    /** 清理会话凭据（Cookie + 本地登录提示）。 */
    fun clearSession()
}
