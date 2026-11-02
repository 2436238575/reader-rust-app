package org.example.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.Book
import org.example.reader.core.model.BookChapter
import org.example.reader.core.net.ApiResult
import org.example.reader.core.util.nowMillis

data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val chapters: List<BookChapter> = emptyList(),
    val currentIndex: Int = 0,
    val paragraphs: List<String> = emptyList(),
    val isLoadingContent: Boolean = false,
    val error: String? = null,
    /** 正文来自用户编辑版（而非上游）；界面上给个轻标记。 */
    val isEdited: Boolean = false,
    val showCatalog: Boolean = false,
) {
    val currentChapter: BookChapter? get() = chapters.getOrNull(currentIndex)
    val hasPrev: Boolean get() = currentIndex > 0
    val hasNext: Boolean get() = currentIndex < chapters.lastIndex
}

/**
 * 阅读器状态机。
 *
 * 章节身份是 `chapterUrl`（换源会变），书籍身份是 `bookId`（换源不变）。
 * 进度对齐 Web 端：服务端 `durChapterPos` 用 0–10000 的万分比。
 */
class ReaderViewModel(
    private val deps: AppDependencies,
    private val bookId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        open()
    }

    fun open() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val bookResult = deps.api.getShelfBook(bookId)
            val book = when (bookResult) {
                is ApiResult.Success -> bookResult.value
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = bookResult.message) }
                    return@launch
                }
            }
            val chapterResult = deps.api.getChapterList(bookId)
            when (chapterResult) {
                is ApiResult.Success -> {
                    val chapters = chapterResult.value
                    if (chapters.isEmpty()) {
                        _state.update {
                            it.copy(isLoading = false, book = book, error = "目录为空，可能是书源规则失效")
                        }
                        return@launch
                    }
                    val startIndex = book.durChapterIndex.coerceIn(0, chapters.lastIndex)
                    _state.update {
                        it.copy(isLoading = false, book = book, chapters = chapters, currentIndex = startIndex)
                    }
                    loadContent(startIndex)
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false, book = book, error = chapterResult.message) }
                }
            }
        }
    }

    private fun loadContent(index: Int) {
        val chapter = _state.value.chapters.getOrNull(index) ?: return
        _state.update { it.copy(isLoadingContent = true, currentIndex = index, error = null) }
        viewModelScope.launch {
            val result = deps.api.getBookContent(
                bookId = bookId,
                chapterUrl = chapter.url,
                index = chapter.index,
            )
            when (result) {
                is ApiResult.Success -> {
                    val paragraphs = splitParagraphs(result.value.content)
                    _state.update {
                        it.copy(
                            isLoadingContent = false,
                            paragraphs = paragraphs,
                            isEdited = result.value.edited,
                        )
                    }
                    // 最近阅读以「实际读到哪一章」为准（Web 端同样在章节落定后记录）
                    val book = _state.value.book?.copy(
                        durChapterIndex = index,
                        durChapterTitle = chapter.title,
                        durChapterTime = nowMillis(),
                    )
                    if (book != null) {
                        _state.update { it.copy(book = book) }
                        deps.recentBooks.record(book)
                    }
                    persistProgress(index, chapter)
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoadingContent = false, error = result.message) }
                }
            }
        }
    }

    fun goToChapter(index: Int) {
        if (index !in _state.value.chapters.indices) return
        _state.update { it.copy(showCatalog = false) }
        loadContent(index)
    }

    fun nextChapter() = goToChapter(_state.value.currentIndex + 1)

    fun prevChapter() = goToChapter(_state.value.currentIndex - 1)

    fun toggleCatalog() {
        _state.update { it.copy(showCatalog = !it.showCatalog) }
    }

    fun dismissCatalog() {
        _state.update { it.copy(showCatalog = false) }
    }

    fun refreshChapter() {
        loadContent(_state.value.currentIndex)
    }

    /** 上报进度。接口对未入库书返回 400，静默忽略即可（与 Web 端一致）。 */
    private fun persistProgress(index: Int, chapter: BookChapter) {
        viewModelScope.launch {
            deps.api.saveBookProgress(bookId = bookId, index = index, position = 0)
        }
    }

    /**
     * 正文按行切段。
     *
     * 后端正文是纯文本 + `\n` 分段，但可能夹带 HTML 片段（配图 `<figure>`）。
     * 这里保留原样交给渲染层判断，不做破坏性清洗——清洗规则属于
     * 服务端「净化规则」的职责。
     */
    private fun splitParagraphs(content: String): List<String> =
        content.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
