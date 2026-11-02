package org.example.reader.core.recent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.reader.core.model.Book
import org.example.reader.core.model.RecentBook
import org.example.reader.core.net.ReaderJson
import org.example.reader.core.settings.KeyValueStore
import org.example.reader.core.settings.SettingsKeys
import org.example.reader.core.util.nowMillis

/**
 * 最近阅读（本地镜像）。
 *
 * 与 Web 端 `utils/recentBooks.ts` 同义：整本 Book + `recentReadAt`，
 * 按时间倒序、按 `bookId` 去重、上限 100 条。当前只落本地；
 * 跨端同步（后端 userdata 文档）属于 P4-6，届时合并策略与前端一致。
 */
class RecentBooksStore(
    private val settings: KeyValueStore,
    private val json: Json = ReaderJson,
) {
    private val _books = MutableStateFlow(read())
    val books: StateFlow<List<RecentBook>> = _books.asStateFlow()

    /** 阅读器加载完一章后调用；阅读进度与时间以传入的 book 为准。 */
    fun record(book: Book) {
        if (book.bookId.isBlank()) return
        val at = nowMillis()
        val entry = RecentBook(book = book.copy(durChapterTime = at), recentReadAt = at)
        val next = buildList {
            add(entry)
            addAll(_books.value.filter { it.book.bookId != book.bookId })
        }.sortedByDescending { it.recentReadAt }
            .take(MAX_ENTRIES)
        _books.value = next
        persist(next)
    }

    fun remove(bookId: String) {
        if (bookId.isBlank()) return
        val next = _books.value.filter { it.book.bookId != bookId }
        _books.value = next
        persist(next)
    }

    fun clear() {
        _books.value = emptyList()
        settings.putString(SettingsKeys.RECENT_BOOKS, null)
    }

    private fun read(): List<RecentBook> {
        val raw = settings.getString(SettingsKeys.RECENT_BOOKS) ?: return emptyList()
        return try {
            json.decodeFromString<List<RecentBook>>(raw)
                .filter { it.book.bookId.isNotBlank() }
                .sortedByDescending { it.recentReadAt }
                .take(MAX_ENTRIES)
        } catch (_: Exception) {
            // 旧版本数据/损坏 JSON 不应让应用起不来，直接当空列表
            emptyList()
        }
    }

    private fun persist(list: List<RecentBook>) {
        settings.putString(SettingsKeys.RECENT_BOOKS, json.encodeToString(list))
    }

    private companion object {
        const val MAX_ENTRIES = 100
    }
}
