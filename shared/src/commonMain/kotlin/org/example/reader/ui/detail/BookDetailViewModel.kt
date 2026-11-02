package org.example.reader.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.Book
import org.example.reader.core.net.ApiResult
import org.example.reader.core.net.SaveBookRequest

data class BookDetailUiState(
    val book: Book,
    val onShelf: Boolean = false,
    val isAdding: Boolean = false,
) {
    val readChapters: Int
        get() = if (book.totalChapterNum <= 0) {
            0
        } else {
            (book.durChapterIndex + 1).coerceAtMost(book.totalChapterNum)
        }
}

/**
 * 书籍详情。
 *
 * 只判断是否已入架并取回书架侧的最新数据（进度、缓存章数）。
 * 目录属于独立页面 `CatalogViewModel`，不在这里加载——详情页不需要
 * 一次拉上千章。
 */
class BookDetailViewModel(
    private val deps: AppDependencies,
    initialBook: Book,
) : ViewModel() {

    private val _state = MutableStateFlow(BookDetailUiState(book = initialBook))
    val state: StateFlow<BookDetailUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val shelfBook = deps.api.getBookshelfWithCacheInfo().getOrNull()
                ?.firstOrNull { it.bookId == _state.value.book.bookId }
            if (shelfBook != null) {
                _state.update { it.copy(book = shelfBook, onShelf = true) }
            }
        }
    }

    fun addToShelf() {
        val book = _state.value.book
        if (_state.value.onShelf || _state.value.isAdding) return
        _state.update { it.copy(isAdding = true) }
        viewModelScope.launch {
            val result = deps.api.saveBook(
                SaveBookRequest(
                    bookId = book.bookId,
                    bookSourceId = book.bookSourceId,
                    name = book.name,
                    author = book.author,
                    bookUrl = book.bookUrl,
                    origin = book.origin,
                    coverUrl = book.coverUrl,
                ),
            )
            when (result) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            book = result.value,
                            onShelf = true,
                            isAdding = false,
                        )
                    }
                    _messages.tryEmit("「${book.name}」已加入书架")
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(isAdding = false) }
                    _messages.tryEmit(result.message)
                }
            }
        }
    }
}
