package org.example.reader.core.net

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.example.reader.core.model.AiServerModelStatus
import org.example.reader.core.model.BatchChapterContent
import org.example.reader.core.model.Book
import org.example.reader.core.model.BookChapter
import org.example.reader.core.model.BookGroup
import org.example.reader.core.model.BookSourceSummary
import org.example.reader.core.model.Bookmark
import org.example.reader.core.model.CacheStats
import org.example.reader.core.model.ChapterContent
import org.example.reader.core.model.ExploreKind
import org.example.reader.core.model.ReplaceRule
import org.example.reader.core.model.SearchBook
import org.example.reader.core.model.UserInfo
import org.example.reader.core.model.UserInfoHolder

/**
 * 后端 `/reader3/…` 接口封装。
 *
 * 约定与前端 `api/http.ts` 一致：
 * - 响应统一是 `{isSuccess, errorMsg, data}`，这里统一解包；
 * - 401 由传输层的 OkHttp `Authenticator` 静默续期并自动重放，
 *   走到这里的 401 表示刷新也失败了（会话真的失效），调用方去拉起登录页。
 */
class ReaderApi(
    // inline 的响应解析会触及这两个成员，必须标 @PublishedApi internal，
    // 否则 Kotlin 拒绝「public inline 访问 private 成员」。
    @PublishedApi internal val client: HttpClient,
    @PublishedApi internal val json: Json = ReaderJson,
) {
    // ─── 会话 ────────────────────────────────────────────────────────────────

    suspend fun login(username: String, password: String): ApiResult<UserInfo> =
        post("/login", json.encodeToString(LoginRequest.serializer(), LoginRequest(username, password)))

    suspend fun refreshToken(): ApiResult<UserInfo> = post("/refreshToken")

    suspend fun logout(): ApiResult<Unit> = post("/logout")

    /** 可选鉴权：未登录也返回 200，此时 `userInfo` 为 null。 */
    suspend fun getUserInfo(): ApiResult<UserInfo?> =
        get<UserInfoHolder>("/getUserInfo").map { it.userInfo }

    suspend fun changePassword(old: String, new: String): ApiResult<UserInfo> =
        post(
            "/changePassword",
            json.encodeToString(ChangePasswordRequest.serializer(), ChangePasswordRequest(old, new)),
        )

    // ─── 书架 ────────────────────────────────────────────────────────────────

    suspend fun getBookshelfWithCacheInfo(): ApiResult<List<Book>> = get("/getShelfBookWithCacheInfo")

    suspend fun getShelfBook(bookId: String): ApiResult<Book> =
        post("/getShelfBook", json.encodeToString(BookIdRequest.serializer(), BookIdRequest(bookId)))

    suspend fun getBookGroups(): ApiResult<List<BookGroup>> = get("/getBookGroups")

    suspend fun saveBook(book: Book): ApiResult<Book> =
        post("/saveBook", json.encodeToString(Book.serializer(), book))

    suspend fun saveBooks(books: List<Book>, restore: Boolean = false): ApiResult<List<Book>> =
        post(
            "/saveBooks",
            json.encodeToString(SaveBooksRequest.serializer(), SaveBooksRequest(books, restore)),
        )

    suspend fun deleteBook(bookId: String): ApiResult<String> =
        post("/deleteBook", json.encodeToString(BookIdRequest.serializer(), BookIdRequest(bookId)))

    suspend fun deleteBooks(bookIds: List<String>): ApiResult<Int> =
        post<DeleteBooksResponse>(
            "/deleteBooks",
            json.encodeToString(BookIdsRequest.serializer(), BookIdsRequest(bookIds)),
        ).map { it.deleted }

    suspend fun getBookInfo(bookId: String): ApiResult<Book> =
        post("/getBookInfo", json.encodeToString(BookIdRequest.serializer(), BookIdRequest(bookId)))

    suspend fun setBookSource(bookId: String, targetBookId: String): ApiResult<Book> =
        post(
            "/setBookSource",
            json.encodeToString(SetBookSourceRequest.serializer(), SetBookSourceRequest(bookId, targetBookId)),
        )

    suspend fun saveBookProgress(
        bookId: String,
        index: Int,
        position: Int,
    ): ApiResult<Unit> = post(
        "/saveBookProgress",
        json.encodeToString(
            SaveProgressRequest.serializer(),
            SaveProgressRequest(bookId = bookId, index = index, position = position),
        ),
    )

    /** 分组：不传 id 为新建，传 id 为改名/改排序。 */
    suspend fun saveBookGroup(groupId: Long?, groupName: String, orderNo: Int? = null): ApiResult<BookGroup> =
        post(
            "/saveBookGroup",
            json.encodeToString(BookGroupRequest.serializer(), BookGroupRequest(groupId, groupName, orderNo)),
        )

    suspend fun deleteBookGroup(groupId: Long): ApiResult<String> =
        post("/deleteBookGroup", json.encodeToString(LongRequest.serializer(), LongRequest(groupId)))

    suspend fun saveBookGroupId(bookIds: List<String>, groupId: Long): ApiResult<Unit> =
        post<Unit>(
            "/saveBookGroupId",
            json.encodeToString(
                SaveBookGroupIdRequest.serializer(),
                SaveBookGroupIdRequest(bookIds, groupId),
            ),
        )

    suspend fun saveBookGroupOrder(groupIds: List<Long>): ApiResult<Unit> =
        post<Unit>("/saveBookGroupOrder", json.encodeToString(GroupOrderRequest.serializer(), GroupOrderRequest(groupIds)))

    // ─── 目录与正文 ──────────────────────────────────────────────────────────

    suspend fun getChapterList(bookId: String, refresh: Boolean = false): ApiResult<List<BookChapter>> =
        get("/getChapterList", buildMap {
            put("bookId", bookId)
            if (refresh) put("refresh", "1")
        })

    suspend fun getBookContent(
        bookId: String,
        chapterUrl: String,
        index: Int? = null,
        refresh: Boolean = false,
        variant: String? = null,
        knownVersionId: Long? = null,
    ): ApiResult<ChapterContent> = post(
        "/getBookContent",
        json.encodeToString(
            ChapterContentRequest.serializer(),
            ChapterContentRequest(
                bookId = bookId,
                chapterUrl = chapterUrl,
                index = index,
                refresh = if (refresh) 1 else null,
                variant = variant,
                knownVersionId = knownVersionId,
            ),
        ),
    )

    suspend fun getBookContents(bookId: String, chapters: List<BookChapter>): ApiResult<List<BatchChapterContent>> =
        post(
            "/getBookContents",
            json.encodeToString(
                BatchContentsRequest.serializer(),
                BatchContentsRequest(bookId, chapters),
            ),
        )

    // ─── 书签 / 净化规则 ────────────────────────────────────────────────────

    suspend fun getBookmarks(): ApiResult<List<Bookmark>> = get("/getBookmarks")

    suspend fun saveBookmark(bookmark: Bookmark): ApiResult<Bookmark> =
        post("/saveBookmark", json.encodeToString(Bookmark.serializer(), bookmark))

    suspend fun saveBookmarks(bookmarks: List<Bookmark>): ApiResult<List<Bookmark>> =
        post("/saveBookmarks", json.encodeToString(BookmarkListRequest.serializer(), BookmarkListRequest(bookmarks)))

    suspend fun deleteBookmark(bookmark: Bookmark): ApiResult<String> =
        post("/deleteBookmark", json.encodeToString(Bookmark.serializer(), bookmark))

    suspend fun deleteBookmarks(bookmarks: List<Bookmark>): ApiResult<Int> =
        post<DeleteCountResponse>(
            "/deleteBookmarks",
            json.encodeToString(BookmarkListRequest.serializer(), BookmarkListRequest(bookmarks)),
        ).map { it.deleted }

    suspend fun getReplaceRules(): ApiResult<List<ReplaceRule>> = get("/getReplaceRules")

    suspend fun saveReplaceRule(rule: ReplaceRule): ApiResult<ReplaceRule> =
        post("/saveReplaceRule", json.encodeToString(ReplaceRule.serializer(), rule))

    suspend fun saveReplaceRules(rules: List<ReplaceRule>): ApiResult<List<ReplaceRule>> =
        post("/saveReplaceRules", json.encodeToString(ReplaceRuleListRequest.serializer(), ReplaceRuleListRequest(rules)))

    suspend fun deleteReplaceRule(id: Long): ApiResult<String> =
        post("/deleteReplaceRule", json.encodeToString(LongRequest.serializer(), LongRequest(id)))

    suspend fun deleteReplaceRules(ids: List<Long>): ApiResult<Int> =
        post<DeleteCountResponse>(
            "/deleteReplaceRules",
            json.encodeToString(LongListRequest.serializer(), LongListRequest(ids)),
        ).map { it.deleted }

    // ─── 搜索 / 发现 / 换源 ─────────────────────────────────────────────────

    /** 书源摘要列表（含 `exploreUrl`，不含规则字段）。 */
    suspend fun getBookSourceList(): ApiResult<List<BookSourceSummary>> = get("/getBookSourceList")

    /**
     * 发现分类。
     *
     * 服务端解析书源 `exploreUrl`（支持 JSON 数组 / 「标题::URL」行 / 空 URL 分组标题），
     * 比客户端拉全量规则自己解析更省，也避免两端解析口径漂移。
     */
    suspend fun getExploreKinds(bookSourceId: Long): ApiResult<List<ExploreKind>> =
        post(
            "/getExploreKinds",
            json.encodeToString(ExploreKindsRequest.serializer(), ExploreKindsRequest(bookSourceId)),
        )

    /** 加入书架（单本 upsert）。 */
    suspend fun saveBook(request: SaveBookRequest): ApiResult<Book> =
        post("/saveBook", json.encodeToString(SaveBookRequest.serializer(), request))

    suspend fun exploreBook(bookSourceId: Long, ruleFindUrl: String, page: Int): ApiResult<List<SearchBook>> =
        post(
            "/exploreBook",
            json.encodeToString(ExploreRequest.serializer(), ExploreRequest(bookSourceId, ruleFindUrl, page)),
        )

    /** 多书源搜索（SSE 流式）。 */
    fun searchBookMultiSse(
        key: String,
        bookSourceGroup: String? = null,
        bookSourceId: Long? = null,
        concurrentCount: Int = 24,
    ): Flow<SseEvent> = client.stream(
        HttpRequest(
            method = "GET",
            path = "/searchBookMultiSSE",
            query = buildMap {
                put("key", key)
                if (!bookSourceGroup.isNullOrBlank()) put("bookSourceGroup", bookSourceGroup)
                if (bookSourceId != null) put("bookSourceId", bookSourceId.toString())
                put("concurrentCount", concurrentCount.toString())
            },
        ),
    )

    /** 换源候选：全网搜同名书（SSE 流式）。 */
    fun getAvailableBookSourceSse(bookId: String, lastIndex: Int = 0): Flow<SseEvent> = client.stream(
        HttpRequest(
            method = "GET",
            path = "/getAvailableBookSourceSSE",
            query = buildMap {
                put("bookId", bookId)
                if (lastIndex > 0) put("lastIndex", lastIndex.toString())
            },
        ),
    )

    /** 服务端缓存整本书（SSE 进度流）。 */
    fun cacheBookSse(bookId: String, mode: String = "all"): Flow<SseEvent> = client.stream(
        HttpRequest(
            method = "GET",
            path = "/cacheBookSSE",
            query = mapOf("bookId" to bookId, "mode" to mode),
        ),
    )

    /** 书内搜索：只搜服务端已缓存章节。 */
    suspend fun searchCachedContent(bookId: String, keyword: String): ApiResult<List<CachedSearchHit>> =
        post(
            "/searchCachedContent",
            json.encodeToString(CachedSearchRequest.serializer(), CachedSearchRequest(bookId, keyword)),
        )

    // ─── 缓存 ────────────────────────────────────────────────────────────────

    suspend fun cacheStats(): ApiResult<CacheStats> = get("/cacheStats")

    suspend fun purgeCache(scope: String, kind: String? = null): ApiResult<String> =
        post("/purgeCache", json.encodeToString(PurgeCacheRequest.serializer(), PurgeCacheRequest(scope, kind)))

    suspend fun deleteBookCache(bookId: String): ApiResult<String> =
        post("/deleteBookCache", json.encodeToString(BookIdRequest.serializer(), BookIdRequest(bookId)))

    // ─── AI ─────────────────────────────────────────────────────────────────

    suspend fun getAiModelConfig(): ApiResult<AiServerModelStatus> = get("/getAiModelConfig")

    // ─── 内部 ────────────────────────────────────────────────────────────────

    private suspend inline fun <reified T> get(path: String, query: Map<String, String> = emptyMap()): ApiResult<T> =
        send(HttpRequest(method = "GET", path = path, query = query))

    /** 无请求体的 POST（logout / refreshToken）。 */
    private suspend inline fun <reified T> post(path: String): ApiResult<T> =
        send(HttpRequest(method = "POST", path = path))

    private suspend inline fun <reified T> post(path: String, jsonBody: String): ApiResult<T> =
        send(HttpRequest(method = "POST", path = path, body = RequestBody.Json(jsonBody)))

    private suspend inline fun <reified T> send(request: HttpRequest): ApiResult<T> {
        val response = try {
            client.execute(request)
        } catch (e: Exception) {
            // 异常类型必须打出来：很多网络栈异常的 message 是 null，
            // 只回兜底文案会让「为什么失败」无从查起。
            println("ReaderApi: ${request.method} ${request.path} 失败 ${e::class.simpleName}: ${e.message}")
            return ApiResult.Failure(-1, e.message ?: "网络请求失败（${e::class.simpleName}）")
        }
        return response.toApiResult()
    }

    @PublishedApi
    internal inline fun <reified T> HttpResponse.toApiResult(): ApiResult<T> {
        if (!isSuccessful()) {
            // 4xx/5xx 也可能带 ApiResponse 包装（400 通常带可读 errorMsg）。
            val message = runCatching {
                (json.parseToJsonElement(text()) as? JsonObject)
                    ?.get("errorMsg")
                    ?.let { e -> (e as? JsonPrimitive)?.content }
            }.getOrNull()?.takeIf { it.isNotBlank() }
            return ApiResult.Failure(code, message ?: defaultMessage(code))
        }
        return json.parseApiResponse(text(), code)
    }

    @PublishedApi
    internal fun defaultMessage(code: Int): String = when (code) {
        401 -> "需要登录"
        408 -> "请求超时"
        413 -> "上传内容过大（HTTP 413）：请检查反向代理的 client_max_body_size（建议 ≥100m）"
        else -> "请求失败（HTTP $code）"
    }
}

/** 书内搜索命中。 */
@kotlinx.serialization.Serializable
data class CachedSearchHit(
    @kotlinx.serialization.SerialName("chapterIndex") val chapterIndex: Int = 0,
    @kotlinx.serialization.SerialName("chapterTitle") val chapterTitle: String = "",
    @kotlinx.serialization.SerialName("chapterUrl") val chapterUrl: String = "",
    val snippet: String = "",
    val edited: Boolean = false,
)

@kotlinx.serialization.Serializable
internal data class DeleteBooksResponse(val deleted: Int = 0)

@kotlinx.serialization.Serializable
internal data class DeleteCountResponse(val deleted: Int = 0)

@kotlinx.serialization.Serializable
internal data class LongRequest(val id: Long)

@kotlinx.serialization.Serializable
internal data class LongListRequest(val ids: List<Long>)

@kotlinx.serialization.Serializable
internal data class GroupOrderRequest(@kotlinx.serialization.SerialName("groupIds") val groupIds: List<Long>)

@kotlinx.serialization.Serializable
internal data class BookmarkListRequest(val bookmarks: List<Bookmark>)

@kotlinx.serialization.Serializable
internal data class ReplaceRuleListRequest(val rules: List<ReplaceRule>)

@kotlinx.serialization.Serializable
internal data class CachedSearchRequest(val bookId: String, val keyword: String)

