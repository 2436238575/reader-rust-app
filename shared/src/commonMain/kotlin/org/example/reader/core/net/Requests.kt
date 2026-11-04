package org.example.reader.core.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 请求体 DTO。
 *
 * 一律用 @Serializable 建模 + kotlinx.serialization 编码，不要手拼 JSON 字符串：
 * 手拼容易漏转义（书名/搜索词里的引号、反斜杠）、改字段要同步两处，
 * 且编译期查不出拼写错误。
 */

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String,
)

@Serializable
data class BookIdRequest(
    val bookId: String,
)

@Serializable
data class BookIdsRequest(
    val bookIds: List<String>,
)

@Serializable
data class SaveBooksRequest(
    val books: List<org.example.reader.core.model.Book>,
    /** WebDAV 恢复：整架覆盖 + 登记表清表重建。 */
    val restore: Boolean = false,
)

/**
 * 书架排序（`/saveBookOrder`）：只提交 bookId 顺序，服务端**只写 position 一列**。
 *
 * 与 [SaveBooksRequest] 的区别是它不做 DELETE + 重插，所以请求里没带的列
 * （`infoHtml` / `tocHtml` / `canReName` / `downloadUrls` 等）不会丢。
 * 后端要求 `bookIds` 是**当前书架的完整集合**：数量不符或集合不等时返回
 * 400「书架已变化，请刷新后重试」。
 */
@Serializable
data class SaveBookOrderRequest(
    @SerialName("bookIds") val bookIds: List<String>,
)

/**
 * 分组排序的单条（`/saveBookGroupOrder` 的 body 是**裸数组**）。
 *
 * 后端只按 `orderNo` 重排：未提交的分组保持原顺序、不会被删除，
 * 也不校验/改写 `groupName`（增删改的唯一入口是 `/saveBookGroup` 与
 * `/deleteBookGroup`）。
 */
@Serializable
data class BookGroupOrderItem(
    @SerialName("groupId") val groupId: Long,
    /** 目标下标，从 0 起连续递增。 */
    @SerialName("orderNo") val orderNo: Int,
)

/**
 * 进度上报。字段名对齐后端 `SaveBookProgressRequest`：
 * `index`（章序号，必填）与 `position`（万分比章内进度 0–10000）。
 * 传 `chapterIndex`/`chapterPos` 会被后端当成缺参，返回 400 `index required`。
 */
@Serializable
data class SaveProgressRequest(
    val bookId: String,
    val index: Int,
    val position: Int? = null,
)

@Serializable
data class ChapterContentRequest(
    val bookId: String,
    val chapterUrl: String,
    val index: Int? = null,
    /** 1 = 强制回源，绕过服务端目录/正文缓存。 */
    val refresh: Int? = null,
    /** `upstream` 只预览最新上游；缺省按服务端选择解析。 */
    val variant: String? = null,
    /** 本地缓存里的版本号：与当前版本一致时响应 unchanged、不传正文。 */
    val knownVersionId: Long? = null,
)

/** 批量取正文的单章：字段名对齐后端 `BatchContentChapter`（`chapterUrl` 不是 `url`）。 */
@Serializable
data class BatchContentChapterRequest(
    val index: Int,
    @SerialName("chapterUrl") val chapterUrl: String,
)

@Serializable
data class BatchContentsRequest(
    val bookId: String,
    val chapters: List<BatchContentChapterRequest>,
)

@Serializable
data class ExploreKindsRequest(
    val bookSourceId: Long,
)

@Serializable
data class ExploreRequest(
    val bookSourceId: Long,
    val ruleFindUrl: String,
    val page: Int,
)

/**
 * 加入书架（`/saveBook`）。
 *
 * 只带前端同款的最小字段：后端会自己登记 bookId 并按需补齐元数据。
 * 直接序列化 `Book` 会因 `encodeDefaults = true` 把 `durChapterIndex`、`group`
 * 等默认值一起发过去，等于把已在架书籍的阅读进度覆盖成 0。
 */
@Serializable
data class SaveBookRequest(
    val bookId: String,
    val bookSourceId: Long,
    val name: String,
    val author: String,
    val bookUrl: String,
    val origin: String,
    val coverUrl: String? = null,
)

@Serializable
data class PurgeCacheRequest(
    /** `kind` 按类型清理，`user` 清当前用户全部。 */
    val scope: String,
    /** `cover` / `chapterList` / `searchResults` / `review`。 */
    val kind: String? = null,
)

@Serializable
data class SetBookSourceRequest(
    val bookId: String,
    /** 换源目标：候选搜索结果自己的 bookId（搜索产出即登记）。 */
    val targetBookId: String,
)

/** 单本书设置分组；`groupId = 0` 表示移出全部分组（后端 `/saveBookGroupId`）。 */
@Serializable
data class SaveBookGroupIdRequest(
    val bookId: String,
    val groupId: Long,
)

@Serializable
data class BookGroupRequest(
    @SerialName("groupId") val groupId: Long? = null,
    @SerialName("groupName") val groupName: String,
    @SerialName("orderNo") val orderNo: Int? = null,
)

@Serializable
data class UserdataBatchRequest(
    /** 文档名 → 内容（JSON 字符串）。 */
    val documents: Map<String, String>,
)
