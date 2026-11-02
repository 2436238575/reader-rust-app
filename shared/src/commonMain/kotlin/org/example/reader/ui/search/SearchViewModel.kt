package org.example.reader.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.BookSourceSummary
import org.example.reader.core.model.SearchBook
import org.example.reader.core.net.ApiResult
import org.example.reader.core.net.ReaderJson
import org.example.reader.core.net.SaveBookRequest
import org.example.reader.core.net.SseEvent

/** 搜索范围。只有一个启用书源时强制按「全部」处理（等价于单源）。 */
enum class SearchScope(val label: String) {
    All("全部书源"),
    Group("按分组"),
    Source("单个书源"),
}

data class SearchUiState(
    val keyword: String = "",
    /** 已提交的搜索词；空表示还没搜过。 */
    val submittedKey: String = "",
    val scope: SearchScope = SearchScope.All,
    val sources: List<BookSourceSummary> = emptyList(),
    val group: String = "",
    val sourceId: Long? = null,
    val results: List<SearchBook> = emptyList(),
    val isSearching: Boolean = false,
    /** 连接失败/后端 error 帧。要与「没搜到」区分开，否则会给出误导性空态。 */
    val failed: Boolean = false,
    val errorMessage: String? = null,
    val shelfIds: Set<String> = emptySet(),
    val addingIds: Set<String> = emptySet(),
) {
    val enabledSources: List<BookSourceSummary>
        get() = sources
            .filter { it.enabled != false && it.bookSourceId != null }
            .sortedWith(compareBy({ it.customOrder ?: 0 }, { it.bookSourceName }))

    val groups: List<String>
        get() = enabledSources
            .flatMap { splitBookSourceGroups(it.bookSourceGroup) }
            .distinct()
            .sorted()

    val originNames: Map<Long, String>
        get() = enabledSources.mapNotNull { source ->
            source.bookSourceId?.let { it to source.bookSourceName }
        }.toMap()

    /** 启用的书源多于一个才有范围选择的意义。 */
    val showFilters: Boolean get() = enabledSources.size > 1
}

/**
 * 多源搜索。
 *
 * 后端 `/searchBookMultiSSE`：默认帧 `{lastIndex, data:[SearchBook]}`，
 * 命名 `error` 帧 `{errorMsg}`，命名 `end` 帧收尾。它是**流式增量**的，
 * 因此结果边到边渲染，去重集合跨帧维护。
 */
class SearchViewModel(private val deps: AppDependencies) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            refreshShelfIds()
            loadSources()
        }
    }

    private suspend fun refreshShelfIds() {
        val ids = deps.api.getBookshelfWithCacheInfo().getOrNull()
            .orEmpty()
            .mapNotNull { it.bookId.takeIf(String::isNotBlank) }
            .toSet()
        _state.update { it.copy(shelfIds = ids) }
    }

    private suspend fun loadSources() {
        when (val result = deps.api.getBookSourceList()) {
            is ApiResult.Success -> {
                _state.update { it.copy(sources = result.value) }
                ensureSelection()
            }

            is ApiResult.Failure -> _state.update { it.copy(errorMessage = result.message) }
        }
    }

    fun onKeywordChange(value: String) {
        _state.update { it.copy(keyword = value) }
    }

    /** 提交搜索。空关键词等价于清空结果。 */
    fun submit() {
        val key = _state.value.keyword.trim()
        if (key.isEmpty()) {
            clear()
            return
        }
        ensureSelection()
        val state = _state.value
        // 只有一个书源时筛选区整条隐藏，范围等同「全部」——
        // 避免上次选过的分组/单源在隐藏后仍悄悄限制结果（与前端一致）
        val scope = if (state.showFilters) state.scope else SearchScope.All
        if (scope == SearchScope.Group && state.group.isBlank()) return
        if (scope == SearchScope.Source && state.sourceId == null) return
        runSearch(key, scope)
    }

    fun retry() {
        if (_state.value.submittedKey.isBlank()) return
        submit()
    }

    fun setScope(scope: SearchScope) {
        _state.update { it.copy(scope = scope) }
        ensureSelection()
        rerunIfSearched()
    }

    fun setGroup(group: String) {
        _state.update { it.copy(group = group) }
        rerunIfSearched()
    }

    fun setSource(sourceId: Long) {
        _state.update { it.copy(sourceId = sourceId) }
        rerunIfSearched()
    }

    fun clear() {
        searchJob?.cancel()
        searchJob = null
        _state.update {
            it.copy(
                keyword = "",
                submittedKey = "",
                results = emptyList(),
                isSearching = false,
                failed = false,
                errorMessage = null,
            )
        }
    }

    private fun rerunIfSearched() {
        if (_state.value.submittedKey.isNotBlank()) submit()
    }

    private fun ensureSelection() {
        val state = _state.value
        if (state.scope == SearchScope.Group && state.group !in state.groups) {
            state.groups.firstOrNull()?.let { first -> _state.update { it.copy(group = first) } }
        }
        val sourceIds = state.enabledSources.mapNotNull { it.bookSourceId }
        if (state.scope == SearchScope.Source && state.sourceId !in sourceIds) {
            sourceIds.firstOrNull()?.let { first -> _state.update { it.copy(sourceId = first) } }
        }
    }

    private fun runSearch(key: String, scope: SearchScope) {
        searchJob?.cancel()
        val params = _state.value
        _state.update {
            it.copy(
                submittedKey = key,
                results = emptyList(),
                isSearching = true,
                failed = false,
                errorMessage = null,
            )
        }
        searchJob = viewModelScope.launch {
            val seen = HashSet<String>()
            try {
                deps.api.searchBookMultiSse(
                    key = key,
                    bookSourceGroup = if (scope == SearchScope.Group) params.group else null,
                    bookSourceId = if (scope == SearchScope.Source) params.sourceId else null,
                    concurrentCount = 24,
                ).collect { event ->
                    when (event) {
                        is SseEvent.Message -> appendFrame(event.data, seen)
                        is SseEvent.Error -> _state.update {
                            it.copy(
                                isSearching = false,
                                failed = true,
                                errorMessage = parseErrorMessage(event.message) ?: "服务返回了未知错误",
                            )
                        }

                        SseEvent.End -> _state.update { it.copy(isSearching = false) }
                    }
                }
                // 流被对端关闭但没发 end 帧：兜底收掉「搜索中」
                _state.update { if (it.isSearching) it.copy(isSearching = false) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSearching = false,
                        failed = true,
                        errorMessage = e.message ?: "搜索连接中断",
                    )
                }
            }
        }
    }

    private fun appendFrame(raw: String, seen: MutableSet<String>) {
        val frame = try {
            ReaderJson.decodeFromString<SearchFrame>(raw)
        } catch (_: Exception) {
            return
        }
        if (frame.books.isEmpty()) return
        val fresh = ArrayList<SearchBook>(frame.books.size)
        for (book in frame.books) {
            // 后端已按 merge_key 去重，这里再兜一层：多帧之间可能重复
            val dedupeKey = book.bookId.ifBlank { "${book.origin}::${book.bookUrl}" }
            if (dedupeKey.isBlank() || !seen.add(dedupeKey)) continue
            fresh.add(book)
        }
        if (fresh.isEmpty()) return
        _state.update { it.copy(results = it.results + fresh) }
    }

    fun addToShelf(book: SearchBook) {
        val bookId = book.bookId
        val state = _state.value
        if (bookId.isBlank() || bookId in state.addingIds || bookId in state.shelfIds) return
        _state.update { it.copy(addingIds = it.addingIds + bookId) }
        viewModelScope.launch {
            val result = deps.api.saveBook(
                SaveBookRequest(
                    bookId = bookId,
                    bookSourceId = book.bookSourceId,
                    name = book.name,
                    author = book.author,
                    bookUrl = book.bookUrl,
                    origin = book.origin,
                    coverUrl = book.coverUrl,
                ),
            )
            _state.update { it.copy(addingIds = it.addingIds - bookId) }
            when (result) {
                is ApiResult.Success -> {
                    val savedId = result.value.bookId.ifBlank { bookId }
                    _state.update { it.copy(shelfIds = it.shelfIds + savedId) }
                    _messages.tryEmit("「${book.name}」已加入书架")
                }

                is ApiResult.Failure -> _messages.tryEmit(result.message)
            }
        }
    }

    private fun parseErrorMessage(raw: String): String? = try {
        ReaderJson.decodeFromString<SseErrorFrame>(raw).errorMsg?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

/** SSE 默认帧：`data` 是新增结果，`lastIndex` 是已完成的书源游标。 */
@Serializable
private data class SearchFrame(
    val lastIndex: Int = 0,
    @SerialName("data") val books: List<SearchBook> = emptyList(),
)

@Serializable
private data class SseErrorFrame(
    @SerialName("errorMsg") val errorMsg: String? = null,
)

/**
 * 书源分组拆分，与后端 `split_source_groups` 保持一致：
 * 只认半角逗号/分号、全角分号、顿号。前端不得比后端更宽。
 */
internal fun splitBookSourceGroups(raw: String?): List<String> =
    raw.orEmpty()
        .split(',', ';', '；', '、')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
