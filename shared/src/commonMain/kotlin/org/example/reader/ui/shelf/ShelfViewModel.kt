package org.example.reader.ui.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.Book
import org.example.reader.core.model.BookGroup
import org.example.reader.core.net.ApiResult
import org.example.reader.core.settings.SettingsKeys

/** 书架视图形态。列表信息密度更高，手机竖屏更常用，因此作为默认。 */
enum class ShelfLayout(val storageValue: String) {
    List("list"),
    Grid("grid"),
    ;

    companion object {
        fun fromStorage(value: String?): ShelfLayout =
            entries.firstOrNull { it.storageValue == value } ?: List
    }
}

data class ShelfUiState(
    val isLoading: Boolean = true,
    val books: List<Book> = emptyList(),
    val groups: List<BookGroup> = emptyList(),
    /** 0 = 全部；-1 = 未分组；其余为分组位域值。 */
    val activeGroupId: Long = 0,
    val keyword: String = "",
    val error: String? = null,
    val layout: ShelfLayout = ShelfLayout.List,
) {
    /**
     * 分组过滤。
     *
     * 后端把分组存成**位域**：一本书可属多个分组，`group & activeGroupId != 0`
     * 命中。0 表示「全部」，-1 表示「未分组」（位域为 0）。
     */
    val visibleBooks: List<Book>
        get() {
            val byKeyword = if (keyword.isBlank()) {
                books
            } else {
                val k = keyword.trim()
                books.filter { book ->
                    book.name.contains(k, ignoreCase = true) ||
                        book.author.contains(k, ignoreCase = true)
                }
            }
            return when (activeGroupId) {
                0L -> byKeyword
                UNGROUPED_ID -> byKeyword.filter { it.group == 0L }
                else -> byKeyword.filter { (it.group and activeGroupId) != 0L }
            }
        }

    /** 有没有任何分组（没有就不显示分组筛选栏，与 Web 端一致）。 */
    val hasGroups: Boolean get() = groups.isNotEmpty() || books.any { it.group != 0L }

    companion object {
        const val UNGROUPED_ID = -1L
    }
}

class ShelfViewModel(private val deps: AppDependencies) : ViewModel() {

    private val _state = MutableStateFlow(
        ShelfUiState(
            layout = ShelfLayout.fromStorage(deps.settings.getString(SettingsKeys.SHELF_LAYOUT)),
        ),
    )
    val state: StateFlow<ShelfUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.isLoading && _state.value.books.isNotEmpty()) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val booksResult = deps.api.getBookshelfWithCacheInfo()
            val groupsResult = deps.api.getBookGroups()

            when (booksResult) {
                is ApiResult.Success -> {
                    val groups = groupsResult.getOrNull().orEmpty()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            books = booksResult.value,
                            groups = groups,
                            error = null,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = booksResult.message) }
                }
            }
        }
    }

    fun onKeywordChange(value: String) {
        _state.update { it.copy(keyword = value) }
    }

    fun toggleLayout() {
        val next = when (_state.value.layout) {
            ShelfLayout.List -> ShelfLayout.Grid
            ShelfLayout.Grid -> ShelfLayout.List
        }
        deps.settings.putString(SettingsKeys.SHELF_LAYOUT, next.storageValue)
        _state.update { it.copy(layout = next) }
    }

    fun onGroupSelect(groupId: Long) {
        _state.update { it.copy(activeGroupId = groupId) }
    }
}
