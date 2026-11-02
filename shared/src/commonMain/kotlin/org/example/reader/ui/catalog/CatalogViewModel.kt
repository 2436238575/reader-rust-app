package org.example.reader.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.BookChapter
import org.example.reader.core.net.ApiResult

data class CatalogUiState(
    val chapters: List<BookChapter> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val keyword: String = "",
    val filterExpanded: Boolean = false,
) {
    /** 保留原始下标：过滤后点章节仍要跳到目录里的真实位置。 */
    val visible: List<IndexedValue<BookChapter>>
        get() {
            val key = keyword.trim()
            val all = chapters.withIndex()
            return if (key.isEmpty()) all.toList() else all.filter { it.value.title.contains(key, true) }
    }
}

/**
 * 目录（独立页面）。
 *
 * 书籍详情与阅读器共用：详情页用它「看看有哪些章」，
 * 阅读器用它跳章——所以这里只依赖 bookId 与回调，不感知来源页面。
 */
class CatalogViewModel(
    private val deps: AppDependencies,
    private val bookId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = deps.api.getChapterList(bookId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(chapters = result.value, isLoading = false, error = null)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun onKeywordChange(value: String) {
        _state.update { it.copy(keyword = value) }
    }

    fun toggleFilter() {
        _state.update {
            if (it.filterExpanded) it.copy(filterExpanded = false, keyword = "") else it.copy(filterExpanded = true)
        }
    }
}
