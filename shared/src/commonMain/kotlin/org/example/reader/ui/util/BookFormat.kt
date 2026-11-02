package org.example.reader.ui.util

import org.example.reader.core.model.Book
import org.example.reader.core.session.ServerConfig

/**
 * 封面地址。
 *
 * 后端已经把书源封面收口成 `/reader3/image/<id>`（id 由后端映射表维护），
 * 只接受「本站相对路径」；完整外链是用户自设 `customCoverUrl` 的兜底场景。
 * 宽度参数让后端按需缩放，避免下载 1024px 大图塞进小卡片。
 */
fun Book.coverUrl(serverConfig: ServerConfig?, width: Int = 320): String? {
    val raw = customCoverUrl?.takeIf { it.isNotBlank() } ?: coverUrl?.takeIf { it.isNotBlank() }
        ?: return null
    if (serverConfig == null) return raw
    val resolved = serverConfig.resolveResource(raw)
    if (!raw.startsWith("/")) return resolved
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
