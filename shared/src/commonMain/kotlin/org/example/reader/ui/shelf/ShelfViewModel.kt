package org.example.reader.ui.shelf

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
import org.example.reader.core.model.BookGroup
import org.example.reader.core.platform.PickedBookFile
import org.example.reader.core.net.ApiResult

data class ShelfUiState(
    val isLoading: Boolean = true,
    val books: List<Book> = emptyList(),
    val groups: List<BookGroup> = emptyList(),
    /** 0 = 全部；-1 = 未分组；其余为分组位域值。 */
    val activeGroupId: Long = 0,
    val keyword: String = "",
    val error: String? = null,
    // ── 多选（编辑模式） ──
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isBulkWorking: Boolean = false,
    // ── 排序模式（拖动右侧手柄） ──
    val sortMode: Boolean = false,
    val isSavingOrder: Boolean = false,
    // ── 本地书导入 ──
    val isImporting: Boolean = false,
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

    val allVisibleSelected: Boolean
        get() = visibleBooks.isNotEmpty() && visibleBooks.all { it.bookId in selectedIds }

    companion object {
        const val UNGROUPED_ID = -1L
    }
}

/**
 * 书架状态机。
 *
 * 除列表/分组/筛选外，还承载多选批量操作与分组增删改。
 * 视图形态不在这里：它是全局偏好（`deps.layout`），发现/搜索也要跟随。
 */
class ShelfViewModel(private val deps: AppDependencies) : ViewModel() {

    private val _state = MutableStateFlow(ShelfUiState())
    val state: StateFlow<ShelfUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() = load(showLoading = true)

    /** 静默刷新：切回书架 tab、批量操作落地后调用，不闪加载态。 */
    fun refreshSilently() = load(showLoading = false)

    private fun load(showLoading: Boolean) {
        if (showLoading) {
            if (_state.value.isLoading && _state.value.books.isNotEmpty()) return
            _state.update { it.copy(isLoading = true, error = null) }
        }
        viewModelScope.launch {
            val booksResult = deps.api.getBookshelfWithCacheInfo()
            val groups = deps.api.getBookGroups().getOrNull()
            when (booksResult) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        books = booksResult.value,
                        groups = groups ?: it.groups,
                        error = null,
                    )
                }

                is ApiResult.Failure -> if (showLoading) {
                    _state.update { it.copy(isLoading = false, error = booksResult.message) }
                }
            }
        }
    }

    fun onKeywordChange(value: String) {
        _state.update { it.copy(keyword = value) }
    }

    fun onGroupSelect(groupId: Long) {
        _state.update { it.copy(activeGroupId = groupId) }
    }

    // ─── 多选 ────────────────────────────────────────────────────────────────

    fun enterSelection(bookId: String? = null) {
        if (_state.value.books.isEmpty()) return
        _state.update {
            it.copy(
                selectionMode = true,
                selectedIds = bookId?.let { id -> setOf(id) } ?: emptySet(),
            )
        }
    }

    fun exitSelection() {
        _state.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
    }

    fun toggleSelection(bookId: String) {
        _state.update {
            val next = if (bookId in it.selectedIds) it.selectedIds - bookId else it.selectedIds + bookId
            it.copy(selectedIds = next)
        }
    }

    /** 全选只作用于当前筛选结果，避免把看不见的书也删掉。 */
    fun selectAllVisible() {
        _state.update { state ->
            if (state.allVisibleSelected) {
                state.copy(selectedIds = emptySet())
            } else {
                state.copy(selectedIds = state.visibleBooks.map { it.bookId }.toSet())
            }
        }
    }

    // ─── 排序模式 ────────────────────────────────────────────────────────────

    /**
     * 进入排序模式。
     *
     * 排序作用于整架顺序，所以清掉分组与关键词筛选——否则拖动的下标
     * 与可见列表不一致。
     */
    fun enterSortMode() {
        if (_state.value.books.size < 2) return
        _state.update {
            it.copy(
                sortMode = true,
                selectionMode = false,
                selectedIds = emptySet(),
                activeGroupId = 0L,
                keyword = "",
            )
        }
    }

    fun exitSortMode() {
        _state.update { it.copy(sortMode = false) }
    }

    /** 拖动时按「移动一步」实时换位，松手后由 [commitOrder] 整架落库。 */
    fun moveBookBy(bookId: String, delta: Int) {
        val state = _state.value
        if (!state.sortMode) return
        val list = state.books.toMutableList()
        val from = list.indexOfFirst { it.bookId == bookId }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return
        val item = list.removeAt(from)
        list.add(to, item)
        _state.update { it.copy(books = list) }
    }

    fun commitOrder() {
        val state = _state.value
        if (!state.sortMode || state.isSavingOrder) return
        _state.update { it.copy(isSavingOrder = true) }
        viewModelScope.launch {
            when (val result = deps.api.saveBooks(state.books)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isSavingOrder = false, sortMode = false, books = result.value) }
                    _messages.tryEmit("书架顺序已保存")
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(isSavingOrder = false) }
                    _messages.tryEmit(result.message)
                }
            }
        }
    }

    // ─── 分组排序 ────────────────────────────────────────────────────────────

    /**
     * 分组上移/下移。
     *
     * `/saveBookGroupOrder` 是整表提交语义（不在列表里的分组会被删除），
     * 所以必须带完整分组列表，并按新下标重写 orderNo。
     */
    fun moveGroup(groupId: Long, delta: Int) {
        val groups = _state.value.groups.toMutableList()
        val from = groups.indexOfFirst { it.groupId == groupId }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, groups.lastIndex)
        if (to == from) return
        val item = groups.removeAt(from)
        groups.add(to, item)
        val ordered = groups.mapIndexed { index, group -> group.copy(orderNo = index) }
        _state.update { it.copy(groups = ordered) }
        viewModelScope.launch {
            when (val result = deps.api.saveBookGroupOrder(ordered)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> {
                    _messages.tryEmit(result.message)
                    load(showLoading = false)
                }
            }
        }
    }

    // ─── 本地书导入 ──────────────────────────────────────────────────────────

    fun importBook(file: PickedBookFile) {
        if (_state.value.isImporting) return
        _state.update { it.copy(isImporting = true) }
        _messages.tryEmit("正在导入「${file.name}」…")
        viewModelScope.launch {
            when (val result = deps.api.uploadBookFile(file.name, file.bytes, file.mimeType)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isImporting = false) }
                    _messages.tryEmit("已导入「${result.value.name}」")
                    load(showLoading = false)
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(isImporting = false) }
                    _messages.tryEmit(result.message)
                }
            }
        }
    }

    fun bulkSetGroup(groupId: Long) {
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty() || _state.value.isBulkWorking) return
        _state.update { it.copy(isBulkWorking = true) }
        viewModelScope.launch {
            var error: String? = null
            // 后端 `/saveBookGroupId` 是单本「设置分组」（0 = 移出全部分组），
            // 逐本调用才能得到「移动」语义；`addBookGroupMulti` 是位域 OR，不是替换。
            for (id in ids) {
                val result = deps.api.saveBookGroupId(id, groupId)
                if (result is ApiResult.Failure) {
                    error = result.message
                    break
                }
            }
            _state.update {
                it.copy(isBulkWorking = false, selectionMode = false, selectedIds = emptySet())
            }
            _messages.tryEmit(error ?: "已移动 ${ids.size} 本书")
            load(showLoading = false)
        }
    }

    fun bulkDelete() {
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty() || _state.value.isBulkWorking) return
        _state.update { it.copy(isBulkWorking = true) }
        viewModelScope.launch {
            when (val result = deps.api.deleteBooks(ids)) {
                is ApiResult.Success -> {
                    // 本地先摘掉，界面立刻正确；再用服务端结果对齐
                    _state.update {
                        it.copy(
                            books = it.books.filterNot { book -> book.bookId in ids },
                            selectionMode = false,
                            selectedIds = emptySet(),
                            isBulkWorking = false,
                        )
                    }
                    _messages.tryEmit("已删除 ${result.value} 本书")
                    load(showLoading = false)
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(isBulkWorking = false) }
                    _messages.tryEmit(result.message)
                }
            }
        }
    }

    // ─── 分组管理 ────────────────────────────────────────────────────────────

    /**
     * 新建/重命名分组。
     *
     * 从「移动分组 → 新建分组」进来时，建完直接把选中的书移进去，
     * 少一步「再点一次移动分组」。
     */
    fun saveGroup(groupId: Long?, name: String, moveSelection: Boolean = false) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _messages.tryEmit("分组名不能为空")
            return
        }
        viewModelScope.launch {
            when (val result = deps.api.saveBookGroup(groupId, trimmed)) {
                is ApiResult.Success -> {
                    _messages.tryEmit(if (groupId == null) "已新建分组「$trimmed」" else "已重命名为「$trimmed」")
                    val groups = deps.api.getBookGroups().getOrNull()
                    if (groups != null) _state.update { it.copy(groups = groups) }
                    if (groupId == null && moveSelection) {
                        // 新建接口只回 "success"，分组 id 要回查（取同名里最大的 id）
                        val created = groups.orEmpty()
                            .filter { it.groupName == trimmed }
                            .maxByOrNull { it.groupId }
                        if (created != null) {
                            bulkSetGroup(created.groupId)
                        } else {
                            load(showLoading = false)
                        }
                    } else {
                        load(showLoading = false)
                    }
                }

                is ApiResult.Failure -> _messages.tryEmit(result.message)
            }
        }
    }

    fun deleteGroup(group: BookGroup) {
        viewModelScope.launch {
            when (val result = deps.api.deleteBookGroup(group.groupId)) {
                is ApiResult.Success -> {
                    _messages.tryEmit("已删除分组「${group.groupName}」")
                    if (_state.value.activeGroupId == group.groupId) {
                        _state.update { it.copy(activeGroupId = 0L) }
                    }
                    load(showLoading = false)
                }

                is ApiResult.Failure -> _messages.tryEmit(result.message)
            }
        }
    }
}
