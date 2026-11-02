package org.example.reader.ui.util

import org.example.reader.core.model.Book
import org.example.reader.core.model.SearchBook
import org.example.reader.core.session.ServerConfig

/**
 * 封面地址。
 *
 * 后端已经把书源封面收口成 `/reader3/image/<id>`（id 由后端映射表维护），
 * 只接受「本站相对路径」；完整外链是用户自设 `customCoverUrl` 的兜底场景。
 * 宽度参数让后端按需缩放，避免下载 1024px 大图塞进小卡片。
 */
fun Book.coverUrl(serverConfig: ServerConfig?, width: Int = 320): String? =
    resolveCover(
        serverConfig,
        customCoverUrl?.takeIf { it.isNotBlank() } ?: coverUrl,
        width,
    )

/** 搜索结果 / 发现结果的封面：字段同 Book，但没有 `customCoverUrl`。 */
fun SearchBook.coverUrl(serverConfig: ServerConfig?, width: Int = 320): String? =
    resolveCover(serverConfig, coverUrl, width)

private fun resolveCover(serverConfig: ServerConfig?, raw: String?, width: Int): String? {
    val value = raw?.takeIf { it.isNotBlank() } ?: return null
    if (serverConfig == null) return value
    val resolved = serverConfig.resolveResource(value)
    if (!value.startsWith("/")) return resolved
    val separator = if (resolved.contains('?')) '&' else '?'
    return "$resolved${separator}w=$width"
}

/** 章内进度（0–10000 万分比）转百分比文本。 */
fun formatProgress(book: Book): String {
    val total = book.totalChapterNum
    if (total <= 0) return "未开始"
    val read = book.durChapterIndex + 1
    return "已读 ${read.coerceAtMost(total)}/$total 章"
}

/** 阅读进度百分比。 */
fun progressPercent(book: Book): Float {
    val total = book.totalChapterNum
    if (total <= 0) return 0f
    return ((book.durChapterIndex + 1).toFloat() / total).coerceIn(0f, 1f)
}

/**
 * 搜索结果 → 书籍详情的展示快照。
 *
 * 结果书可能还没入架，详情页需要先用它渲染，再按 bookId 拉目录；
 * 入架后由 `getShelfBookWithCacheInfo` 返回的完整数据替换。
 */
fun SearchBook.toBook(): Book = Book(
    bookId = bookId,
    bookSourceId = bookSourceId,
    name = name,
    author = author,
    bookUrl = bookUrl,
    origin = origin,
    coverUrl = coverUrl,
    intro = intro,
    latestChapterTitle = lastChapter,
    wordCount = wordCount,
    kind = kind,
)
