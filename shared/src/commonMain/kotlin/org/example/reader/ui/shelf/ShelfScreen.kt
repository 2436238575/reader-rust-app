package org.example.reader.ui.shelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.Book
import org.example.reader.core.model.BookGroup
import org.example.reader.core.settings.ShelfLayout
import org.example.reader.core.session.ServerConfig
import org.example.reader.ui.components.BookCoverThumb
import org.example.reader.ui.components.BookListRow
import org.example.reader.ui.components.EmptyState
import org.example.reader.ui.components.ErrorState
import org.example.reader.ui.components.FullScreenLoading
import org.example.reader.ui.util.formatProgress
import org.example.reader.ui.util.progressPercent
import kotlin.math.roundToInt

/**
 * 书架页。
 *
 * 对应 Web 端 `HomeView`：分组筛选 + 关键词过滤 + 列表/网格双视图 +
 * 多选批量操作 + 分组管理。
 *
 * 交互约定：**点击阅读，长按打开书籍详情**；多选走顶栏「更多 → 选择书籍」。
 * 视图形态是全局偏好（`deps.layout`），发现与搜索结果会跟随。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    deps: AppDependencies,
    contentPadding: PaddingValues,
    onOpenBook: (String) -> Unit,
    onShowDetail: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ShelfViewModel = viewModel { ShelfViewModel(deps) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val layout by deps.layout.layout.collectAsStateWithLifecycle()
    val serverConfig by deps.session.serverConfig.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchExpanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showGroupManager by remember { mutableStateOf(false) }
    var showMoveGroup by remember { mutableStateOf(false) }
    var showBulkDelete by remember { mutableStateOf(false) }
    var groupToDelete by remember { mutableStateOf<BookGroup?>(null) }
    // null = 新建；非 null = 正在编辑该分组
    var groupNameDialog by remember { mutableStateOf<Pair<Long?, String>?>(null) }
    // 从「移动分组 → 新建分组」进入时，建完直接移动选中的书
    var moveAfterCreate by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    // 从「发现 / 搜索」加书回来时刷新列表；静默模式不闪加载态
    LaunchedEffect(Unit) { viewModel.refreshSilently() }

    Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.selectionMode) {
                            "已选 ${state.selectedIds.size}"
                        } else if (state.books.isEmpty()) {
                            "书架"
                        } else {
                            "书架（${state.books.size}）"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    if (state.selectionMode) {
                        IconButton(onClick = viewModel::exitSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "退出多选")
                        }
                    }
                },
                actions = {
                    if (state.selectionMode) {
                        TextButton(onClick = viewModel::selectAllVisible) {
                            Text(if (state.allVisibleSelected) "取消全选" else "全选")
                        }
                    } else {
                        // 核心图标集没有网格图标，用文字标出「切换目标」比放语义不符的图标更清楚
                        val targetLabel = if (layout == ShelfLayout.List) "网格" else "列表"
                        TextButton(onClick = { deps.layout.toggle() }) {
                            Text(targetLabel, style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(onClick = { searchExpanded = !searchExpanded }) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索书架")
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("选择书籍") },
                                    enabled = state.books.isNotEmpty(),
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.enterSelection()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("分组管理") },
                                    onClick = {
                                        menuExpanded = false
                                        showGroupManager = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("刷新") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.refresh()
                                    },
                                )
                            }
                        }
                    }
                },
            )

            if (searchExpanded && !state.selectionMode) {
                OutlinedTextField(
                    value = state.keyword,
                    onValueChange = viewModel::onKeywordChange,
                    placeholder = { Text("搜索书架中的书名或作者") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (state.hasGroups) {
                GroupFilterRow(
                    groups = state.groups,
                    activeGroupId = state.activeGroupId,
                    onSelect = viewModel::onGroupSelect,
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading -> FullScreenLoading()
                    state.error != null -> ErrorState(
                        message = state.error!!,
                        onRetry = viewModel::refresh,
                    )

                    state.books.isEmpty() -> EmptyState(
                        message = "书架还是空的\n到「发现」或「搜索」里找本书吧",
                    )

                    layout == ShelfLayout.List -> BookList(
                        books = state.visibleBooks,
                        serverConfig = serverConfig,
                        imageLoader = deps.imageLoader,
                        selectionMode = state.selectionMode,
                        selectedIds = state.selectedIds,
                        onOpenBook = onOpenBook,
                        onShowDetail = onShowDetail,
                        onToggleSelection = viewModel::toggleSelection,
                    )

                    else -> BookGrid(
                        books = state.visibleBooks,
                        serverConfig = serverConfig,
                        imageLoader = deps.imageLoader,
                        selectionMode = state.selectionMode,
                        selectedIds = state.selectedIds,
                        onOpenBook = onOpenBook,
                        onShowDetail = onShowDetail,
                        onToggleSelection = viewModel::toggleSelection,
                    )
                }
            }

            if (state.selectionMode) {
                SelectionBar(
                    hasSelection = state.selectedIds.isNotEmpty(),
                    isWorking = state.isBulkWorking,
                    onMoveGroup = { showMoveGroup = true },
                    onDelete = { showBulkDelete = true },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // ─── 对话框 ──────────────────────────────────────────────────────────────

    if (showMoveGroup) {
        AlertDialog(
            onDismissRequest = { showMoveGroup = false },
            title = { Text("移动到分组") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    GroupOption("未分组") {
                        showMoveGroup = false
                        viewModel.bulkSetGroup(0L)
                    }
                    state.groups.forEach { group ->
                        GroupOption(group.groupName) {
                            showMoveGroup = false
                            viewModel.bulkSetGroup(group.groupId)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    GroupOption("＋ 新建分组") {
                        showMoveGroup = false
                        moveAfterCreate = true
                        groupNameDialog = null to ""
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveGroup = false }) { Text("取消") }
            },
        )
    }

    if (showBulkDelete) {
        AlertDialog(
            onDismissRequest = { showBulkDelete = false },
            title = { Text("删除书籍") },
            text = { Text("确定从书架删除选中的 ${state.selectedIds.size} 本书？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkDelete = false
                        viewModel.bulkDelete()
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDelete = false }) { Text("取消") }
            },
        )
    }

    if (showGroupManager) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showGroupManager = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("分组管理", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "显示在书架顶部的筛选栏",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            showGroupManager = false
                            moveAfterCreate = false
                            groupNameDialog = null to ""
                        },
                    ) { Text("新建") }
                }
                if (state.groups.isEmpty()) {
                    Text(
                        text = "还没有分组。新建后可以在书架按分组筛选。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                state.groups.forEach { group ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = group.groupName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { groupNameDialog = group.groupId to group.groupName }) {
                            Icon(Icons.Filled.Edit, contentDescription = "重命名")
                        }
                        IconButton(onClick = { groupToDelete = group }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除分组")
                        }
                    }
                }
            }
        }
    }

    groupToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text("删除分组") },
            text = { Text("删除「${group.groupName}」后，组内的书不会被删除，只会回到未分组。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGroup(group)
                        groupToDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) { Text("取消") }
            },
        )
    }

    groupNameDialog?.let { (groupId, initialName) ->
        var name by remember(groupId, initialName) { mutableStateOf(initialName) }
        AlertDialog(
            onDismissRequest = {
                groupNameDialog = null
                moveAfterCreate = false
            },
            title = { Text(if (groupId == null) "新建分组" else "重命名分组") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("分组名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveGroup(groupId, name, moveSelection = moveAfterCreate)
                        groupNameDialog = null
                        moveAfterCreate = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { groupNameDialog = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun GroupFilterRow(
    groups: List<BookGroup>,
    activeGroupId: Long,
    onSelect: (Long) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        item {
            FilterChip(
                selected = activeGroupId == 0L,
                onClick = { onSelect(0L) },
                label = { Text("全部") },
            )
        }
        items(groups, key = { it.groupId }) { group ->
            FilterChip(
                selected = activeGroupId == group.groupId,
                onClick = { onSelect(group.groupId) },
                label = { Text(group.groupName) },
            )
        }
        item {
            FilterChip(
                selected = activeGroupId == ShelfUiState.UNGROUPED_ID,
                onClick = { onSelect(ShelfUiState.UNGROUPED_ID) },
                label = { Text("未分组") },
            )
        }
    }
}

@Composable
private fun SelectionBar(
    hasSelection: Boolean,
    isWorking: Boolean,
    onMoveGroup: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onMoveGroup, enabled = hasSelection && !isWorking) {
                Text("移动分组")
            }
            TextButton(onClick = onDelete, enabled = hasSelection && !isWorking) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.weight(1f))
            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun GroupOption(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}

// ─── 列表布局 ────────────────────────────────────────────────────────────────

@Composable
private fun BookList(
    books: List<Book>,
    serverConfig: ServerConfig?,
    imageLoader: coil3.ImageLoader,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onOpenBook: (String) -> Unit,
    onShowDetail: (Book) -> Unit,
    onToggleSelection: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = "共 ${books.size} 本",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            )
        }
        items(books, key = { it.bookId }) { book ->
            BookListRow(
                book = book,
                serverConfig = serverConfig,
                imageLoader = imageLoader,
                selectionMode = selectionMode,
                selected = book.bookId in selectedIds,
                onClick = {
                    if (selectionMode) onToggleSelection(book.bookId) else onOpenBook(book.bookId)
                },
                onLongClick = {
                    // 长按进详情；多选模式下不抢手势，避免误触
                    if (!selectionMode) onShowDetail(book)
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = if (selectionMode) 116.dp else 96.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }
    }
}

// ─── 网格布局 ────────────────────────────────────────────────────────────────

@Composable
private fun BookGrid(
    books: List<Book>,
    serverConfig: ServerConfig?,
    imageLoader: coil3.ImageLoader,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onOpenBook: (String) -> Unit,
    onShowDetail: (Book) -> Unit,
    onToggleSelection: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "共 ${books.size} 本",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(books, key = { it.bookId }) { book ->
            ShelfGridCard(
                book = book,
                serverConfig = serverConfig,
                imageLoader = imageLoader,
                selectionMode = selectionMode,
                selected = book.bookId in selectedIds,
                onClick = {
                    if (selectionMode) onToggleSelection(book.bookId) else onOpenBook(book.bookId)
                },
                onLongClick = { if (!selectionMode) onShowDetail(book) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfGridCard(
    book: Book,
    serverConfig: ServerConfig?,
    imageLoader: coil3.ImageLoader,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            BookCoverThumb(
                book = book,
                serverConfig = serverConfig,
                imageLoader = imageLoader,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                cornerRadius = 12.dp,
                requestWidth = 320,
            )
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier.align(Alignment.TopStart).padding(2.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = book.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = book.author,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progressPercent(book) },
            modifier = Modifier.fillMaxWidth().height(2.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatProgress(book),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
