package org.example.reader.ui.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.Book
import org.example.reader.core.settings.ReaderSettings
import org.example.reader.core.util.AppBackHandler
import org.example.reader.core.util.SystemBarsAppearance
import org.example.reader.ui.catalog.CatalogScreen
import org.example.reader.ui.components.ReaderIcons
import kotlin.math.roundToInt

/** 阅读器内可挂载的面板。目录是独立页面，不在此列。 */
private enum class ReaderPanel { Settings }

/**
 * 阅读器。
 *
 * 沉浸式：正文全屏，点正文呼出/收起控件。控件布局参考 legado(MD3) 与番茄小说：
 * - 顶部：左侧返回圆钮 + 右侧功能圆钮（换源 / 刷新 / 缓存 / 更多），下方是书名与章名
 * - 底部：上一章 — 全书进度滑块 — 下一章，下面一排「全文搜索 / 自动翻页 / 目录 / 朗读 / 设置」
 * - 配色由 [ReaderPalette] 决定，工具条跟随正文主题，暗色主题下不会出现白条
 *
 * 尚未接入的入口保持可见但会给出明确提示，避免被当成故障。
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
    val settings by deps.readerSettings.state.collectAsStateWithLifecycle()
    val palette = readerPalette(settings.themeIndex)
    val listState = rememberLazyListState()

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf<ReaderPanel?>(null) }
    var moreMenuExpanded by remember { mutableStateOf(false) }

    // 暗色正文配浅色系统栏图标，离开阅读器时恢复应用主题
    SystemBarsAppearance(lightContent = palette.isDark, restoreContent = isSystemInDarkTheme())

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hint: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(startIndex) { viewModel.applyStartIndex(startIndex) }

    // 进入阅读器时要恢复的位置。ViewModel 按 bookId 缓存，重进不会重建，
    // 所以只在本次进入的第一次定位时使用，之后换章一律从章首开始。
    var pendingEntryRestore by remember { mutableStateOf(true) }

    LaunchedEffect(state.currentIndex, state.paragraphs) {
        if (state.paragraphs.isEmpty()) return@LaunchedEffect
        val restore = state.pendingScrollFraction
            ?: if (pendingEntryRestore) viewModel.entryFraction() else 0f
        pendingEntryRestore = false
        if (restore > 0.001f) {
            // 列表还没量出项数时 totalItemsCount 是 0，等它稳定再定位
            val total = snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            val target = (restore * total).toInt().coerceIn(0, total - 1)
            listState.scrollToItem(target)
            viewModel.consumePendingScroll()
        } else {
            listState.scrollToItem(0)
        }
    }

    // 章内位置回传：VM 内部按 10s 节流落库，不必在这里控频
    LaunchedEffect(listState, state.currentIndex) {
        snapshotFlow { listState.chapterFraction() }.collect { viewModel.onScrolled(it) }
    }

    // 离开阅读器（返回 / 切页）时立刻落库，不等节流窗口
    DisposableEffect(Unit) {
        onDispose { viewModel.flushProgress() }
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

    Box(modifier = modifier.fillMaxSize().background(palette.background)) {
        AnimatedContent(
            targetState = showCatalog,
            modifier = Modifier.fillMaxSize(),
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
                ReaderContent(
                    state = state,
                    settings = settings,
                    palette = palette,
                    listState = listState,
                    onPrev = viewModel::prevChapter,
                    onNext = viewModel::nextChapter,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { controlsVisible = !controlsVisible }
                        },
                )
            }
        }

        // 亮度降低与护眼都是叠加蒙版：不改系统亮度，退出阅读器即失效
        val dimAlpha = (1f - settings.brightness) * 0.6f
        if (dimAlpha > 0.01f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimAlpha)))
        }
        if (settings.eyeCare) {
            Box(Modifier.fillMaxSize().background(Color(0xFFFFB74D).copy(alpha = 0.10f)))
        }

        AnimatedVisibility(
            visible = controlsVisible && !showCatalog,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                palette = palette,
                bookName = state.book?.name ?: "阅读",
                chapterTitle = state.currentChapter?.title.orEmpty(),
                onBack = onBack,
                onOpenCatalog = { showCatalog = true },
                onRefresh = viewModel::refreshChapter,
                onHint = hint,
                moreExpanded = moreMenuExpanded,
                onMoreExpandedChange = { moreMenuExpanded = it },
                isNight = palette.isDark,
                onToggleNight = {
                    deps.readerSettings.setTheme(if (palette.isDark) 0 else ReaderPalettes.lastIndex)
                },
                onShowDetail = { state.book?.let(onShowDetail) },
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && !showCatalog,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderBottomBar(
                palette = palette,
                settings = settings,
                chapterIndex = state.currentIndex,
                chapterCount = state.chapters.size,
                hasPrev = state.hasPrev,
                hasNext = state.hasNext,
                onPrev = viewModel::prevChapter,
                onNext = viewModel::nextChapter,
                onSeekChapter = viewModel::goToChapter,
                onOpenCatalog = { showCatalog = true },
                onOpenSettings = { activePanel = ReaderPanel.Settings },
                onHint = hint,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 200.dp),
        )
    }

    activePanel?.let { panel ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val sheetScheme = readerSheetColorScheme(palette)
        ModalBottomSheet(
            onDismissRequest = { activePanel = null },
            sheetState = sheetState,
            containerColor = sheetScheme.surfaceContainerLow,
        ) {
            // ModalBottomSheet 不会因为 containerColor 变化而推导内容色，
            // LocalContentColor 仍是外层主题的深色 —— 暗面板上标签会整片看不见。
            CompositionLocalProvider(LocalContentColor provides sheetScheme.onSurface) {
                MaterialTheme(colorScheme = sheetScheme) {
                    when (panel) {
                        ReaderPanel.Settings -> ReaderSettingsPanel(
                            settings = settings,
                            onFontSizeChange = deps.readerSettings::setFontSize,
                            onThemeChange = deps.readerSettings::setTheme,
                            onMethodChange = deps.readerSettings::setMethod,
                            onBrightnessChange = deps.readerSettings::setBrightness,
                            onEyeCareChange = deps.readerSettings::setEyeCare,
                            onLineHeightChange = deps.readerSettings::setLineHeight,
                            onParagraphSpacingChange = deps.readerSettings::setParagraphSpacing,
                            onReset = deps.readerSettings::reset,
                            onHint = hint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderContent(
    state: ReaderUiState,
    settings: ReaderSettings,
    palette: ReaderPalette,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = palette.text)
        }
        return
    }
    if (state.error != null && state.paragraphs.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = state.error!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }

    // 正文永远避开系统栏；控件只是覆盖层，不参与布局
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 段距与字号同样以 sp 计，换算成布局用的 dp
    val paragraphGap = with(LocalDensity.current) { settings.paragraphGapSp.sp.toDp() }
    val bodyStyle = TextStyle(
        fontSize = settings.fontSize.sp,
        lineHeight = settings.textLineHeightSp.sp,
        color = palette.text,
        // 中文段落首行缩进两个字宽，与前端「首行缩进」一致
        textIndent = TextIndent(firstLine = (settings.fontSize * 2).sp),
    )

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topInset + 16.dp,
            bottom = bottomInset + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(paragraphGap),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = state.currentChapter?.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = (settings.fontSize + 4).sp,
                    color = palette.text,
                ),
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        itemsIndexed(state.paragraphs) { _, paragraph ->
            Text(text = paragraph, style = bodyStyle)
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
                TextButton(onClick = onPrev, enabled = state.hasPrev) {
                    Text("上一章", color = palette.secondaryText)
                }
                TextButton(onClick = onNext, enabled = state.hasNext) {
                    Text("下一章", color = palette.secondaryText)
                }
            }
        }
    }
}

/**
 * 顶部控件：悬浮圆钮 + 书名/章名。
 *
 * 参考 legado 的形态——不做整条背景，按钮浮在正文上，
 * 阅读时视觉压力更小。
 */
@Composable
private fun ReaderTopBar(
    palette: ReaderPalette,
    bookName: String,
    chapterTitle: String,
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    onRefresh: () -> Unit,
    onHint: (String) -> Unit,
    moreExpanded: Boolean,
    onMoreExpandedChange: (Boolean) -> Unit,
    isNight: Boolean,
    onToggleNight: () -> Unit,
    onShowDetail: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 控件是覆盖层，正文会滚到下面；用同色渐变盖住，避免书名/章名与正文叠字。
            // 只做视觉遮挡，不参与布局，所以切换控件时正文不会跳动。
            .background(
                Brush.verticalGradient(
                    0f to palette.background,
                    0.94f to palette.background,
                    1f to palette.background.copy(alpha = 0f),
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .padding(bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = "返回",
                palette = palette,
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            CircleAction(
                icon = ReaderIcons.SwapHoriz,
                description = "换源",
                palette = palette,
                onClick = { onHint("换源将在 P2 接入") },
            )
            Spacer(Modifier.width(8.dp))
            CircleAction(
                icon = Icons.Filled.Refresh,
                description = "刷新本章",
                palette = palette,
                onClick = onRefresh,
            )
            Spacer(Modifier.width(8.dp))
            CircleAction(
                icon = ReaderIcons.Download,
                description = "缓存本章",
                palette = palette,
                onClick = { onHint("离线缓存将在 P2 接入") },
            )
            Spacer(Modifier.width(8.dp))
            Box {
                CircleAction(
                    icon = Icons.Filled.MoreVert,
                    description = "更多",
                    palette = palette,
                    onClick = { onMoreExpandedChange(true) },
                )
                DropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = { onMoreExpandedChange(false) },
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isNight) "日间模式" else "夜间模式") },
                        onClick = {
                            onMoreExpandedChange(false)
                            onToggleNight()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("书籍详情") },
                        onClick = {
                            onMoreExpandedChange(false)
                            onShowDetail()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("目录") },
                        onClick = {
                            onMoreExpandedChange(false)
                            onOpenCatalog()
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = bookName,
            style = MaterialTheme.typography.titleMedium,
            color = palette.text,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (chapterTitle.isNotBlank()) {
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.labelMedium,
                color = palette.secondaryText,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** 悬浮圆钮：40dp 圆盘 + 22dp 图标，点击区 44dp。 */
@Composable
private fun CircleAction(
    icon: ImageVector,
    description: String,
    palette: ReaderPalette,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = palette.controlSurface,
        shadowElevation = 2.dp,
        modifier = Modifier.size(40.dp),
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = palette.icon,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * 底部控件。
 *
 * 行序照番茄小说：全书进度滑块夹在上一章/下一章之间，
 * 下面一排五项功能（对齐 legado 的 5 项，也是本项目后续要补齐的入口）。
 */
@Composable
private fun ReaderBottomBar(
    palette: ReaderPalette,
    settings: ReaderSettings,
    chapterIndex: Int,
    chapterCount: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeekChapter: (Int) -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenSettings: () -> Unit,
    onHint: (String) -> Unit,
) {
    // 拖动时只更新本地值，松手才真正跳章，避免一路触发加载
    var dragValue by remember(chapterIndex, chapterCount) {
        mutableFloatStateOf(chapterIndex.toFloat())
    }

    Surface(
        // 吞掉空白区域的点击：否则会穿透到正文，把控件又切回隐藏
        modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } },
        color = palette.controlSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            ) {
                TextButton(onClick = onPrev, enabled = hasPrev) {
                    Text("上一章", color = if (hasPrev) palette.icon else palette.secondaryText)
                }
                Slider(
                    value = dragValue,
                    onValueChange = { dragValue = it },
                    onValueChangeFinished = {
                        val target = dragValue.roundToInt().coerceIn(0, (chapterCount - 1).coerceAtLeast(0))
                        if (target != chapterIndex) onSeekChapter(target)
                    },
                    valueRange = 0f..(chapterCount - 1).coerceAtLeast(1).toFloat(),
                    enabled = chapterCount > 1,
                    colors = SliderDefaults.colors(
                        thumbColor = palette.text,
                        activeTrackColor = palette.text.copy(alpha = 0.75f),
                        inactiveTrackColor = palette.text.copy(alpha = 0.18f),
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                TextButton(onClick = onNext, enabled = hasNext) {
                    Text("下一章", color = if (hasNext) palette.icon else palette.secondaryText)
                }
            }

            Text(
                text = if (chapterCount > 0) {
                    "第 ${chapterIndex + 1} / $chapterCount 章"
                } else {
                    ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(2.dp))
            HorizontalDivider(color = palette.text.copy(alpha = 0.08f))
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BarAction(Icons.Filled.Search, "搜索", palette) { onHint("书内搜索将在 P2 接入") }
                BarAction(ReaderIcons.AutoPage, "自动翻页", palette) { onHint("自动翻页将在 P1-8 接入") }
                BarAction(Icons.AutoMirrored.Filled.List, "目录", palette, onOpenCatalog)
                BarAction(ReaderIcons.VolumeUp, "朗读", palette) { onHint("朗读将在 P3 接入") }
                BarAction(Icons.Filled.Settings, "设置", palette, onOpenSettings)
            }
        }
    }
}

/** 底栏功能项：图标在上、文字在下，宽度均分。 */
@Composable
private fun BarAction(
    icon: ImageVector,
    label: String,
    palette: ReaderPalette,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = palette.icon,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.secondaryText,
            maxLines = 1,
        )
    }
}

/**
 * 章内阅读位置（0..1）。
 *
 * LazyColumn 拿不到像素级总高度，用「首个可见项下标 + 项内位移 / 总项数」估算：
 * 段落高度接近，误差在一两句之内，足够表达万分比进度。
 */
private fun androidx.compose.foundation.lazy.LazyListState.chapterFraction(): Float {
    val info = layoutInfo
    val total = info.totalItemsCount
    if (total <= 0) return 0f
    val first = info.visibleItemsInfo.firstOrNull() ?: return 0f
    val itemHeight = first.size.toFloat().coerceAtLeast(1f)
    val scrolled = first.index + (info.viewportStartOffset - first.offset).toFloat() / itemHeight
    return (scrolled / total).coerceIn(0f, 1f)
}
