package org.example.reader.ui.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.Book
import org.example.reader.core.model.RecentBook

data class RecentUiState(
    val entries: List<RecentBook> = emptyList(),
    val keyword: String = "",
    val filterExpanded: Boolean = false,
) {
    /**
     * 关键词过滤范围与前端 `RecentView.filteredRecentBooks` 一致：
     * 书名、作者、简介、书源名/地址、最新章节、已读章节。
     */
    val visible: List<Book>
        get() {
            val key = keyword.trim()
            if (key.isEmpty()) return entries.map { it.book }
            return entries.map { it.book }.filter { book ->
                listOfNotNull(
                    book.name,
                    book.author,
                    book.intro,
                    book.originName,
                    book.origin,
                    book.latestChapterTitle,
                    book.durChapterTitle,
                ).any { it.contains(key, ignoreCase = true) }
            }
        }
}

/**
 * 最近阅读。
 *
 * 数据是本地镜像（`RecentBooksStore`），阅读器加载章节时写入；
 * 跨端同步属于 P4-6，届时按前端「并集合并、取较新」的策略接后端 userdata。
 */
class RecentViewModel(private val deps: AppDependencies) : ViewModel() {

    private val _state = MutableStateFlow(RecentUiState(entries = deps.recentBooks.books.value))
    val state: StateFlow<RecentUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            deps.recentBooks.books.collect { entries ->
                _state.update { it.copy(entries = entries) }
            }
        }
    }

    fun onKeywordChange(value: String) {
        _state.update { it.copy(keyword = value) }
    }

    fun toggleFilter() {
        _state.update {
            if (it.filterExpanded) {
                it.copy(filterExpanded = false, keyword = "")
            } else {
                it.copy(filterExpanded = true)
            }
        }
    }

    fun remove(bookId: String) = deps.recentBooks.remove(bookId)

    fun clearAll() = deps.recentBooks.clear()
}
