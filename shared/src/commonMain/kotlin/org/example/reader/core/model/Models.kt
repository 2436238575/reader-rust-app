package org.example.reader.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 后端统一响应包装（`src/error/error.rs` 的 `ApiResponse<T>`）。
 *
 * 注意字段的实际取值，不要按直觉推断：
 * - `isSuccess`：成功 true，失败 false
 * - `errorMsg`：成功时是**空字符串**（不是 null），失败时是用户可读文案
 * - `data`：业务数据，失败时通常为 null
 */
@Serializable
data class ApiResponse<T>(
    @SerialName("isSuccess") val isSuccess: Boolean = false,
    @SerialName("errorMsg") val errorMsg: String = "",
    val data: T? = null,
)

/** 账号信息。`accessToken` 仅登录/刷新响应里带，普通查询不返回。 */
@Serializable
data class UserInfo(
    val uid: Long = 0,
    val username: String = "",
    val lastLoginAt: Long = 0,
    val createdAt: Long = 0,
    @SerialName("accessToken") val accessToken: String? = null,
    @SerialName("accessTokenExpiresIn") val accessTokenExpiresIn: Long? = null,
)

/** `getUserInfo` 的 data 外壳。 */
@Serializable
data class UserInfoHolder(
    @SerialName("userInfo") val userInfo: UserInfo? = null,
)

/**
 * 书籍。后端分配的稳定身份是 `book_<n>` 形式的 `bookId`，不随换源变化；
 * 章节身份是 `chapterUrl`，换源后会变。
 */
@Serializable
data class Book(
    @SerialName("bookId") val bookId: String = "",
    @SerialName("bookSourceId") val bookSourceId: Long = 0,
    val name: String = "",
    val author: String = "",
    @SerialName("bookUrl") val bookUrl: String = "",
    val origin: String = "",
    @SerialName("originName") val originName: String? = null,
    @SerialName("coverUrl") val coverUrl: String? = null,
    @SerialName("customCoverUrl") val customCoverUrl: String? = null,
    @SerialName("tocUrl") val tocUrl: String? = null,
    val charset: String? = null,
    @SerialName("canUpdate") val canUpdate: Boolean? = null,
    @SerialName("durChapterIndex") val durChapterIndex: Int = 0,
    @SerialName("durChapterPos") val durChapterPos: Int = 0,
    @SerialName("durChapterTime") val durChapterTime: Long = 0,
    @SerialName("durChapterTitle") val durChapterTitle: String? = null,
    val intro: String? = null,
    @SerialName("latestChapterTitle") val latestChapterTitle: String? = null,
    @SerialName("lastCheckTime") val lastCheckTime: Long = 0,
    @SerialName("totalChapterNum") val totalChapterNum: Int = 0,
    val type: Int = 0,
    /** 分组位域：一本书可属多个分组，`group & 分组id != 0` 命中。0 = 未分组。 */
    val group: Long = 0,
    @SerialName("wordCount") val wordCount: String? = null,
    val kind: String? = null,
    @SerialName("updateTime") val updateTime: String? = null,
    /** 服务端已缓存章节数（`getShelfBookWithCacheInfo` 下发）。 */
    @SerialName("cachedChapterCount") val cachedChapterCount: Int = 0,
    /** 本地（浏览器缓存等价物）已缓存章节数，安卓侧由本地库统计。 */
    @SerialName("browserCachedChapterCount") val browserCachedChapterCount: Int = 0,
)

/** 目录条目。`volume` 非空表示该章属于某个卷（前端按连续同名卷分组）。 */
@Serializable
data class BookChapter(
    val title: String = "",
    val url: String = "",
    val index: Int = 0,
    val tag: String? = null,
    @SerialName("isVip") val isVip: Boolean? = null,
    @SerialName("isPay") val isPay: Boolean? = null,
    @SerialName("isVolume") val isVolume: Boolean? = null,
    val volume: String? = null,
    @SerialName("wordCount") val wordCount: String? = null,
)

/** 分组。`group` 位域里 0 表示「未分组」。 */
@Serializable
data class BookGroup(
    @SerialName("groupId") val groupId: Long = 0,
    @SerialName("groupName") val groupName: String = "",
    @SerialName("orderNo") val orderNo: Int = 0,
)

/** 搜索结果。`bookId` 产出即登记，后续入架/详情/正文都用它。 */
@Serializable
data class SearchBook(
    @SerialName("bookId") val bookId: String = "",
    @SerialName("bookSourceId") val bookSourceId: Long = 0,
    val name: String = "",
    val author: String = "",
    @SerialName("bookUrl") val bookUrl: String = "",
    val origin: String = "",
    @SerialName("coverUrl") val coverUrl: String? = null,
    val intro: String? = null,
    val kind: String? = null,
    @SerialName("lastChapter") val lastChapter: String? = null,
    @SerialName("updateTime") val updateTime: String? = null,
    @SerialName("wordCount") val wordCount: String? = null,
)

/**
 * 发现分类。后端 `ExploreKind` 用 `title`（不是 name）。
 *
 * `url` 为空表示这是**分组标题行**（书源 `exploreUrl` 里用「标题::URL」排版），
 * 只做视觉分隔，不可点击。
 */
@Serializable
data class ExploreKind(
    val title: String = "",
    val url: String? = null,
)

/**
 * 书源摘要（`/getBookSourceList` 下发）。
 *
 * 含 `exploreUrl`（发现页筛源与解析分类要用），但**不含** rule 系列规则字段；
 * 单条全量规则走 `/getBookSource`。
 */
@Serializable
data class BookSourceSummary(
    @SerialName("bookSourceName") val bookSourceName: String = "",
    @SerialName("bookSourceUrl") val bookSourceUrl: String = "",
    @SerialName("bookSourceId") val bookSourceId: Long? = null,
    @SerialName("bookSourceGroup") val bookSourceGroup: String? = null,
    @SerialName("bookSourceType") val bookSourceType: Int? = null,
    val enabled: Boolean? = null,
    @SerialName("enabledExplore") val enabledExplore: Boolean? = null,
    @SerialName("enabledCookieJar") val enabledCookieJar: Boolean? = null,
    @SerialName("lastUpdateTime") val lastUpdateTime: Long? = null,
    @SerialName("respondTime") val respondTime: Long? = null,
    @SerialName("customOrder") val customOrder: Int? = null,
    val weight: Int? = null,
    @SerialName("exploreUrl") val exploreUrl: String? = null,
)

/** 书签 / 划线。身份 = bookId + chapterIndex + chapterPos + kind。 */
@Serializable
data class Bookmark(
    @SerialName("bookId") val bookId: String = "",
    /**
     * 创建时间（毫秒时间戳）。**必填**：后端按它排序下发书签列表，
     * 批量删除也按整条匹配，回传时必须原样带上，不要在这里重新取当前时间。
     */
    val time: Long = 0,
    @SerialName("bookName") val bookName: String = "",
    @SerialName("bookAuthor") val bookAuthor: String = "",
    @SerialName("chapterIndex") val chapterIndex: Int = 0,
    /** 万分比章内进度（0–10000）。 */
    @SerialName("chapterPos") val chapterPos: Int = 0,
    @SerialName("chapterName") val chapterName: String? = null,
    @SerialName("bookText") val bookText: String? = null,
    val content: String? = null,
    /**
     * 类型：`bookmark`（章书签）/ `underline`（划线）。
     *
     * 用非空默认值而不是 `String?`：`ReaderJson` 开了 `explicitNulls = false`，
     * 可空字段为 null 时会被整体省略，后端把「缺省」一律当成 `bookmark`，
     * 划线就被静默降级成章书签（R-09）。`encodeDefaults = true` 保证它一定
     * 出现在请求体里。
     */
    val kind: String = KIND_BOOKMARK,
) {
    companion object {
        /** 章书签。 */
        const val KIND_BOOKMARK: String = "bookmark"

        /** 划线。 */
        const val KIND_UNDERLINE: String = "underline"
    }
}

/** 内容净化规则。`scope`：`*` 全部书籍 / `book:<id>` / `source:<id>`。 */
@Serializable
data class ReplaceRule(
    val id: Long = 0,
    val name: String = "",
    val group: String? = null,
    val pattern: String = "",
    val replacement: String = "",
    val scope: String? = null,
    @SerialName("isEnabled") val isEnabled: Boolean = true,
    @SerialName("isRegex") val isRegex: Boolean = false,
    val order: Int = 0,
)

/** 章节正文响应。`unchanged=true` 表示服务端认为客户端缓存版本仍是最新，content 为空串。 */
@Serializable
data class ChapterContent(
    val content: String = "",
    val edited: Boolean = false,
    val variant: String = "default",
    @SerialName("versionId") val versionId: Long? = null,
    @SerialName("versionKind") val versionKind: String? = null,
    @SerialName("baseVersionId") val baseVersionId: Long? = null,
    @SerialName("diffBaseVersionId") val diffBaseVersionId: Long? = null,
    @SerialName("latestUserVersionId") val latestUserVersionId: Long? = null,
    @SerialName("latestUpstreamVersionId") val latestUpstreamVersionId: Long? = null,
    @SerialName("upstreamChanged") val upstreamChanged: Boolean = false,
    @SerialName("selectionSource") val selectionSource: String = "selected",
    val stale: Boolean = false,
    @SerialName("lastAttemptAt") val lastAttemptAt: Long? = null,
    @SerialName("lastSuccessAt") val lastSuccessAt: Long? = null,
    @SerialName("upstreamChangedAt") val upstreamChangedAt: Long? = null,
    @SerialName("editedAt") val editedAt: Long? = null,
    val unchanged: Boolean = false,
)

/**
 * 章节配图。
 *
 * [paraIndex] 是**正文按 `\n` 切分的原始行号**（插在该行之前），与正文块下标不是一回事：
 * 空行会被 `ChapterBlockBuilder` 过滤掉，映射由构建器统一做。`null` 表示取不到位置，排章末。
 */
@Serializable
data class ChapterImageInfo(
    val url: String = "",
    val caption: String = "",
    @SerialName("paraIndex") val paraIndex: Int? = null,
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * 一章的配图列表。
 *
 * `enabled=false` 表示**书源没有声明配图规则**，不是「这一章没图」；前端据此决定
 * 还要不要继续请求。`enabled=true` + 空列表才是「这一章没有配图」。
 */
@Serializable
data class ChapterImages(
    val enabled: Boolean = false,
    val images: List<ChapterImageInfo> = emptyList(),
)

/** 批量取正文里单章的结果：某章失败只写 `error`，不影响其他章节。 */
@Serializable
data class BatchChapterContent(
    /** 请求里带过去的章节下标，用于对号入座（后端按请求顺序回，但仍以此为准）。 */
    val index: Int = 0,
    /** 成功时是完整正文；失败时为 null 且 `error` 有值，互不影响其它章。 */
    val data: ChapterContent? = null,
    val error: String? = null,
)

/** 缓存占用统计。 */
@Serializable
data class CacheStats(
    val cover: CacheStat = CacheStat(),
    val chapterList: CacheStat = CacheStat(),
    val searchResults: CacheStat = CacheStat(),
    val review: CacheStat = CacheStat(),
    val content: CacheStat = CacheStat(),
)

@Serializable
data class CacheStat(
    val files: Long = 0,
    val bytes: Long = 0,
    val chapters: Long = 0,
    val versions: Long = 0,
)

/** 后端模型配置只回报布尔状态，地址/Key/模型名不下发。 */
@Serializable
data class AiServerModelStatus(
    @SerialName("canUseServerModel") val canUseServerModel: Boolean = false,
    @SerialName("textReady") val textReady: Boolean = false,
    @SerialName("imageReady") val imageReady: Boolean = false,
    @SerialName("speechReady") val speechReady: Boolean = false,
    @SerialName("agentReady") val agentReady: Boolean = false,
)

/**
 * 最近阅读条目。
 *
 * 前端把整本 Book 存进 localStorage 并额外记 `recentReadAt`；这里同构，
 * 保证「换源后书架数据变了、最近记录仍能显示当时的书名/封面」。
 */
@Serializable
data class RecentBook(
    val book: Book,
    /** 最近一次阅读时间（毫秒），排序键。 */
    val recentReadAt: Long = 0,
)
