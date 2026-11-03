package org.example.reader.ui.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.Book
import org.example.reader.core.util.AppBackHandler
import org.example.reader.ui.catalog.CatalogScreen
import org.example.reader.ui.components.FullScreenLoading
import kotlin.math.roundToInt

/** 阅读器内可挂载的面板。目录是独立页面，不在此列。 */
private enum class ReaderPanel { Settings }

/**
 * 阅读器。
 *
 * 沉浸式布局：正文全屏，**点正文中央**呼出/收起控件。
 * - 顶栏：返回、书名/章名、目录、设置、更多（刷新本章 / 书籍详情）
 * - 底栏：章节进度条 + 上一章 / 下一章
 * - 目录是独立页面（`CatalogScreen`），阅读器与详情共用
 *
 * 面板容器（[ReaderPanel]）先立骨架，具体设置项由 P1-1 填充。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    deps: AppDependencies,
    bookId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 指定起始章（详情页点目录进入）；null 表示续读。 */
    startIndex: Int? = null,
    onShowDetail: (Book) -> Unit = {},
) {
    val viewModel: ReaderViewModel = viewModel(key = "reader-$bookId") {
        ReaderViewModel(deps, bookId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf<ReaderPanel?>(null) }
    var moreMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(startIndex) { viewModel.applyStartIndex(startIndex) }

    // 换章后回到顶部，否则新章正文停在上一章的滚动位置。
    LaunchedEffect(state.currentIndex, state.paragraphs) {
        if (state.paragraphs.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // 返回键：先关目录 → 再关面板 → 再收控件 → 最后才退出阅读器
    AppBackHandler {
        when {
            showCatalog -> showCatalog = false
            activePanel != null -> activePanel = null
            controlsVisible -> controlsVisible = false
            else -> onBack()
        }
    }

    AnimatedContent(
        targetState = showCatalog,
        modifier = modifier.fillMaxSize(),
        label = "catalogPage",
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(tween(260)) { it } + fadeIn(tween(180))) togetherWith
                    fadeOut(tween(140))
            } else {
                fadeIn(tween(180)) togetherWith
                    (slideOutHorizontally(tween(260)) { it } + fadeOut(tween(140)))
            }
        },
    ) { catalogVisible ->
        if (catalogVisible) {
            CatalogScreen(
                deps = deps,
                bookId = bookId,
                bookTitle = state.book?.name ?: "目录",
                currentIndex = state.currentIndex,
                onBack = { showCatalog = false },
                onSelectChapter = { index ->
                    viewModel.goToChapter(index)
                    showCatalog = false
                    controlsVisible = false
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                ReaderContent(
                    state = state,
                    listState = listState,
                    onPrev = viewModel::prevChapter,
                    onNext = viewModel::nextChapter,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { controlsVisible = !controlsVisible }
                        },
                )

                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(140)),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        ReaderTopControls(
                            bookName = state.book?.name ?: "阅读",
                            chapterTitle = state.currentChapter?.title.orEmpty(),
                            onBack = onBack,
                            onOpenCatalog = { showCatalog = true },
                            onOpenSettings = { activePanel = ReaderPanel.Settings },
                            moreExpanded = moreMenuExpanded,
                            onMoreExpandedChange = { moreMenuExpanded = it },
                            onRefresh = viewModel::refreshChapter,
                            onShowDetail = { state.book?.let(onShowDetail) },
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                        ReaderBottomControls(
                            hasPrev = state.hasPrev,
                            hasNext = state.hasNext,
                            current = state.currentIndex + 1,
                            total = state.chapters.size,
                            onPrev = viewModel::prevChapter,
                            onNext = viewModel::nextChapter,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }

    activePanel?.let { panel ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { activePanel = null },
            sheetState = sheetState,
        ) {
            when (panel) {
                ReaderPanel.Settings -> ReaderSettingsPlaceholder(
                    onClose = { activePanel = null },
                )
            }
        }
    }
}

@Composable
private fun ReaderContent(
    state: ReaderUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when {
            state.isLoading -> FullScreenLoading()
            state.error != null && state.paragraphs.isEmpty() -> Text(
                text = state.error!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )

            else -> {
                // 正文永远避开系统栏；控件只是覆盖层，不参与布局
                val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = topInset + 16.dp,
                        bottom = bottomInset + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                item {
                    Text(
                        text = state.currentChapter?.title.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                itemsIndexed(state.paragraphs) { _, paragraph ->
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (state.isLoadingContent) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                if (state.error != null) {
                    item {
                        Text(
                            text = state.error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                // 控件隐藏时也能翻章：正文末尾保留一组文字入口
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            TextButton(onClick = onPrev, enabled = state.hasPrev) { Text("上一章") }
                            TextButton(onClick = onNext, enabled = state.hasNext) { Text("下一章") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderTopControls(
    bookName: String,
    chapterTitle: String,
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenSettings: () -> Unit,
    moreExpanded: Boolean,
    onMoreExpandedChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onShowDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        // 吞掉空白区域的点击：否则会穿透到正文，把控件又切回隐藏
        modifier = modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } },
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    text = bookName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (chapterTitle.isNotBlank()) {
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onOpenCatalog) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "目录")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "阅读设置")
            }
            Box {
                IconButton(onClick = { onMoreExpandedChange(true) }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = { onMoreExpandedChange(false) },
                ) {
                    DropdownMenuItem(
                        text = { Text("刷新本章") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        onClick = {
                            onMoreExpandedChange(false)
                            onRefresh()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("书籍详情") },
                        onClick = {
                            onMoreExpandedChange(false)
                            onShowDetail()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomControls(
    hasPrev: Boolean,
    hasNext: Boolean,
    current: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (total > 0) current.toFloat() / total else 0f
    Surface(
        // 吞掉空白区域的点击：否则会穿透到正文，把控件又切回隐藏
        modifier = modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } },
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPrev, enabled = hasPrev) { Text("上一章") }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (total > 0) "$current / $total" else "",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = if (total > 0) "${(progress * 100).roundToInt()}%" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onNext, enabled = hasNext) { Text("下一章") }
            }
        }
    }
}

/**
 * 设置面板占位。
 *
 * P1-9 只负责容器：确认底部面板的高度、圆角与关闭行为。
 * 字号 / 行距 / 主题 / 翻页方式等控件在 P1-1 填充。
 */
@Composable
private fun ReaderSettingsPlaceholder(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Text("阅读设置", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "面板容器已就绪。字号、行距、背景主题、翻页方式等控件将在 P1-1 填入这里。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text("关闭") }
        }
    }
}
