package org.example.reader.ui.shelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.Book
import org.example.reader.core.model.BookGroup
import org.example.reader.core.platform.rememberBookFilePicker
import org.example.reader.core.util.AppBackHandler
import org.example.reader.core.settings.ShelfLayout
import org.example.reader.core.session.ServerConfig
import org.example.reader.ui.components.BookCoverThumb
import org.example.reader.ui.components.BookListRow
import org.example.reader.ui.components.SelectionBadge
import org.example.reader.ui.components.EmptyState
import org.example.reader.ui.components.ErrorState
import org.example.reader.ui.components.FullScreenLoading
import org.example.reader.ui.util.formatProgress
import org.example.reader.ui.util.progressPercent
import kotlin.math.abs
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

    val pickBookFile = rememberBookFilePicker(viewModel::onPickResult)

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    // 从「发现 / 搜索」加书回来时刷新列表；静默模式不闪加载态
    LaunchedEffect(Unit) { viewModel.refreshSilently() }
    // 多选/排序时系统返回先退出该模式，而不是直接退出应用
    AppBackHandler(enabled = state.selectionMode) { viewModel.exitSelection() }
    AppBackHandler(enabled = state.sortMode) {
        viewModel.exitSortMode()
        viewModel.refreshSilently()
    }

    Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            state.sortMode -> "排序书架"
                            state.selectionMode -> "已选 ${state.selectedIds.size}"
                            state.books.isEmpty() -> "书架"
                            else -> "书架（${state.books.size}）"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    if (state.selectionMode || state.sortMode) {
                        IconButton(
                            onClick = {
                                if (state.sortMode) {
                                    viewModel.exitSortMode()
                                    viewModel.refreshSilently()
                                } else {
                                    viewModel.exitSelection()
                                }
                            },
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = if (state.sortMode) "取消排序" else "退出多选",
                            )
                        }
                    }
                },
                actions = {
                    if (state.sortMode) {
                        TextButton(
                            onClick = viewModel::commitOrder,
                            enabled = !state.isSavingOrder,
                        ) {
                            Text("保存")
                        }
                    } else if (state.selectionMode) {
                        TextButton(onClick = viewModel::selectAllVisible) {
                            Text(if (state.allVisibleSelected) "取消全选" else "全选")
                        }
                    } else {
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
                                    text = {
                                        Text(
                                            if (layout == ShelfLayout.List) "切换为网格视图" else "切换为列表视图",
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        deps.layout.toggle()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("选择书籍") },
                                    enabled = state.books.isNotEmpty(),
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.enterSelection()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("排序书籍") },
                                    enabled = layout == ShelfLayout.List && state.books.size > 1,
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.enterSortMode()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (state.isImporting) "正在导入…" else "导入本地书") },
                                    enabled = !state.isImporting,
                                    onClick = {
                                        menuExpanded = false
                                        pickBookFile()
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

            if (searchExpanded && !state.selectionMode && !state.sortMode) {
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

            if (state.hasGroups && !state.sortMode) {
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
                        sortMode = state.sortMode,
                        onOpenBook = onOpenBook,
                        onShowDetail = onShowDetail,
                        onToggleSelection = viewModel::toggleSelection,
                        onMoveBook = viewModel::moveBookBy,
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

            AnimatedVisibility(
                visible = state.selectionMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                SelectionBar(
                    hasSelection = state.selectedIds.isNotEmpty(),
                    isWorking = state.isBulkWorking,
                    onMoveGroup = { showMoveGroup = true },
                    onDelete = { showBulkDelete = true },
                )
            }

            AnimatedVisibility(
                visible = state.sortMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                SortBar(isSaving = state.isSavingOrder)
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
                        IconButton(
                            onClick = { viewModel.moveGroup(group.groupId, -1) },
                            enabled = group != state.groups.firstOrNull(),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                        }
                        IconButton(
                            onClick = { viewModel.moveGroup(group.groupId, 1) },
                            enabled = group != state.groups.lastOrNull(),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                        }
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
private fun SortBar(isSaving: Boolean) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "拖动右侧手柄调整顺序，松手后点「保存」",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/**
 * 排序手柄。
 *
 * 拖动时按整行高度换算「移动几步」，实时在本地列表里换位；
 * 松手后由顶栏「保存」整架提交。行高从实际布局里量，字体放大也适用。
 *
 * 命中区 48dp（M3 最小触控尺寸），视觉仍是 24dp 的三条细线——
 * 手势检测挂在外层 48dp 节点上，视觉内缩到 24dp 的 Canvas 里，
 * 这样既满足点击区又不会在行尾杵一个大图标。拖拽换算用的是**行高**，
 * 与命中区尺寸无关，手感不变。
 */
@Composable
private fun DragHandle(
    bookId: String,
    rowHeightPx: Int,
    onMoveBook: (String, Int) -> Unit,
) {
    var accumulated by remember(bookId) { mutableFloatStateOf(0f) }
    val handleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Box(
        modifier = Modifier
            .size(48.dp)
            .pointerInput(bookId, rowHeightPx) {
                if (rowHeightPx <= 0) return@pointerInput
                detectDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = { accumulated = 0f },
                    onDragCancel = { accumulated = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    accumulated += dragAmount.y
                    val steps = (accumulated / rowHeightPx).toInt()
                    if (steps != 0) {
                        repeat(abs(steps)) {
                            onMoveBook(bookId, if (steps > 0) 1 else -1)
                        }
                        accumulated -= steps * rowHeightPx
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val lineWidth = 12.dp.toPx()
            val lineHeight = 1.5.dp.toPx()
            val gap = 3.5.dp.toPx()
            val totalHeight = lineHeight * 3 + gap * 2
            var y = (size.height - totalHeight) / 2
            repeat(3) {
                drawRoundRect(
                    color = handleColor,
                    topLeft = Offset((size.width - lineWidth) / 2, y),
                    size = Size(lineWidth, lineHeight),
                    cornerRadius = CornerRadius(lineHeight / 2),
                )
                y += lineHeight + gap
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
    sortMode: Boolean,
    onOpenBook: (String) -> Unit,
    onShowDetail: (Book) -> Unit,
    onToggleSelection: (String) -> Unit,
    onMoveBook: (String, Int) -> Unit,
) {
    var rowHeightPx by remember { mutableStateOf(0) }
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
                modifier = Modifier.onGloballyPositioned { coords ->
                    if (coords.size.height > 0) rowHeightPx = coords.size.height
                },
                trailing = if (sortMode) {
                    {
                        DragHandle(
                            bookId = book.bookId,
                            rowHeightPx = rowHeightPx,
                            onMoveBook = onMoveBook,
                        )
                    }
                } else {
                    null
                },
                onClick = {
                    when {
                        sortMode -> Unit
                        selectionMode -> onToggleSelection(book.bookId)
                        else -> onOpenBook(book.bookId)
                    }
                },
                onLongClick = {
                    // 长按进详情；多选/排序模式下不抢手势，避免误触
                    if (!selectionMode && !sortMode) onShowDetail(book)
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 96.dp),
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
                SelectionBadge(
                    selected = selected,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
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
