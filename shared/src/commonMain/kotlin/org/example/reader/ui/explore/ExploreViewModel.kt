package org.example.reader.ui.explore

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
import org.example.reader.core.model.BookSourceSummary
import org.example.reader.core.model.ExploreKind
import org.example.reader.core.model.SearchBook
import org.example.reader.core.net.ApiResult
import org.example.reader.core.net.SaveBookRequest

/**
 * 发现页状态。
 *
 * `sources` 是全部书源摘要，真正可用于发现的是 [exploreSources]
 * （`enabledExplore` 且 `exploreUrl` 非空）。分类由服务端解析 `exploreUrl` 得来，
 * 其中 `url` 为空的条目是分组标题，不可点击。
 */
data class ExploreUiState(
    val isLoadingSources: Boolean = true,
    val isLoading: Boolean = false,
    val sources: List<BookSourceSummary> = emptyList(),
    val activeSourceId: Long? = null,
    val kinds: List<ExploreKind> = emptyList(),
    val activeKindUrl: String? = null,
    val books: List<SearchBook> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null,
    val addingIds: Set<String> = emptySet(),
    val shelfIds: Set<String> = emptySet(),
) {
    val exploreSources: List<BookSourceSummary>
        get() = sources.filter {
            it.enabledExplore == true && it.bookSourceId != null && !it.exploreUrl.isNullOrBlank()
        }

    val activeSource: BookSourceSummary?
        get() = sources.firstOrNull { it.bookSourceId == activeSourceId }

    val activeKindTitle: String?
        get() = kinds.firstOrNull { it.url != null && it.url == activeKindUrl }?.title
}

/**
 * 发现页（书海）。
 *
 * 对应 Web 端 `views/ExploreView.vue` + `stores/explore.ts`：
 * 选源 → 解析分类 → 选分类 → 分页拉书 → 加入书架 / 打开。
 */
class ExploreViewModel(private val deps: AppDependencies) : ViewModel() {

    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    /** Toast 等价物：入架成功/失败的一次性提示。 */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    /**
     * 请求代际。切源/切分类后自增，旧请求回来时直接丢弃——
     * 否则「加载中切分类」会把上一个分类的结果混进新列表。
     */
    private var generation = 0

    init {
        viewModelScope.launch {
            refreshShelfIds()
            loadSources()
        }
    }

    /** 已入架集合：卡片据此显示「已在书架」，不必等用户手动加一本才翻转。 */
    private suspend fun refreshShelfIds() {
        val ids = deps.api.getBookshelfWithCacheInfo().getOrNull()
            .orEmpty()
            .mapNotNull { it.bookId.takeIf(String::isNotBlank) }
            .toSet()
        _state.update { it.copy(shelfIds = ids) }
    }

    private suspend fun loadSources() {
        when (val result = deps.api.getBookSourceList()) {
            is ApiResult.Failure -> _state.update {
                it.copy(isLoadingSources = false, error = result.message)
            }

            is ApiResult.Success -> {
                _state.update { it.copy(sources = result.value, isLoadingSources = false, error = null) }
                val firstId = _state.value.exploreSources.firstOrNull()?.bookSourceId
                if (firstId == null) {
                    // 没有可发现的书源：由 UI 显示对应空态，不算错误
                    _state.update { it.copy(isLoading = false, hasMore = false) }
                } else {
                    selectSource(firstId)
                }
            }
        }
    }

    /** 重试入口：书源都没拉到就重拉列表，否则重试当前分类的当前页。 */
    fun retry() {
        if (_state.value.isLoading) return
        if (_state.value.exploreSources.isEmpty()) {
            _state.update { it.copy(isLoadingSources = true, error = null) }
            viewModelScope.launch {
                refreshShelfIds()
                loadSources()
            }
            return
        }
        _state.update { it.copy(error = null, hasMore = true) }
        loadPage(generation)
    }

    /** 顶栏刷新：重新解析分类并回到第一页（不是重拉当前页）。 */
    fun refresh() {
        val s = _state.value
        if (s.isLoading || s.isLoadingSources) return
        if (s.exploreSources.isEmpty()) {
            retry()
            return
        }
        val sourceId = s.activeSourceId ?: s.exploreSources.first().bookSourceId ?: return
        // selectSource 对同一 id 会早退，先清空再用同一个 id 重新走「解析分类 → 拉第一页」
        _state.update { it.copy(activeSourceId = null) }
        selectSource(sourceId)
    }

    fun selectSource(sourceId: Long) {
        if (_state.value.activeSourceId == sourceId) return
        val gen = ++generation
        _state.update {
            it.copy(
                activeSourceId = sourceId,
                kinds = emptyList(),
                activeKindUrl = null,
                books = emptyList(),
                page = 1,
                hasMore = true,
                error = null,
                isLoading = true,
            )
        }
        viewModelScope.launch {
            val result = deps.api.getExploreKinds(sourceId)
            if (gen != generation) return@launch
            val kinds = result.getOrNull()
            if (kinds == null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        hasMore = false,
                        error = (result as ApiResult.Failure).message,
                    )
                }
                return@launch
            }
            val firstUrl = kinds.firstOrNull { !it.url.isNullOrBlank() }?.url
            _state.update { it.copy(kinds = kinds) }
            if (firstUrl == null) {
                _state.update { it.copy(isLoading = false, hasMore = false) }
            } else {
                selectKind(firstUrl)
            }
        }
    }

    fun selectKind(url: String) {
        val target = url.trim()
        if (target.isEmpty() || _state.value.activeKindUrl == target) return
        val gen = ++generation
        _state.update {
            it.copy(
                activeKindUrl = target,
                books = emptyList(),
                page = 1,
                hasMore = true,
                error = null,
            )
        }
        loadPage(gen)
    }

    /** 滚动到底部时触发；请求在途 / 没有更多时直接忽略。 */
    fun loadMore() {
        val s = _state.value
        if (s.isLoading || !s.hasMore || s.activeKindUrl == null) return
        loadPage(generation)
    }

    private fun loadPage(gen: Int) {
        val snapshot = _state.value
        val sourceId = snapshot.activeSourceId ?: return
        val kindUrl = snapshot.activeKindUrl ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = deps.api.exploreBook(sourceId, kindUrl, snapshot.page)
            if (gen != generation) return@launch
            val books = result.getOrNull()
            if (books == null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        // 停住自动翻页，错误由「重试」入口处理
                        hasMore = false,
                        error = (result as ApiResult.Failure).message,
                    )
                }
                return@launch
            }
            if (books.isEmpty()) {
                _state.update { it.copy(isLoading = false, hasMore = false) }
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        books = it.books + books,
                        page = it.page + 1,
                    )
                }
            }
        }
    }

    fun addToShelf(book: SearchBook) {
        val bookId = book.bookId
        val s = _state.value
        if (bookId.isBlank() || bookId in s.addingIds || bookId in s.shelfIds) return
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
}
