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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.Book
import org.example.reader.core.platform.ApplyWindowBrightness
import org.example.reader.core.platform.rememberSystemBrightness
import org.example.reader.core.settings.AutoPageMode
import org.example.reader.core.settings.ClickAction
import org.example.reader.core.settings.ReadMethod
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
    val palette = readerPalette(settings.themeIndex).let {
        if (settings.eyeCare) it.highContrast() else it
    }
    // 亮度初值取自系统，不记忆：每次进入阅读器都读一次用户当前的屏幕亮度，
    // 而不是沿用上次手动拖出来的值。从后台恢复不会重建组合，因此不会重读、
    // 更不会把用户在阅读中调好的亮度盖回系统值。
    val systemBrightness = rememberSystemBrightness()
    LaunchedEffect(systemBrightness) {
        systemBrightness?.let { deps.readerSettings.setBrightness(it) }
    }
    // 亮度走**窗口级** screenBrightness（不改系统设置、不叠蒙版）：
    // 蒙版会把正文和背景一起压暗，对比度随亮度下降而塌掉；窗口亮度由系统合成，
    // 文字与背景的亮度差不变，暗处才读得清。离开阅读器自动还原原值。
    ApplyWindowBrightness(settings.brightness)
    val listState = rememberLazyListState()

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    // 自动阅读开关**不进 prefs**（前端 `store.isAutoScrolling` 同样不持久化）：
    // 退出阅读器再进来不该自己开始滚。用 rememberSaveable 只是为了转屏 / 进程回收后
    // 不丢当前状态，与控件显隐（controlsVisible）同一档位。
    var autoReading by rememberSaveable { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf<ReaderPanel?>(null) }
    var moreMenuExpanded by remember { mutableStateOf(false) }

    // 暗色正文配浅色系统栏图标，离开阅读器时恢复应用主题
    SystemBarsAppearance(lightContent = palette.isDark, restoreContent = isSystemInDarkTheme())

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // 手势回调要读**最新**的点击档位：设置面板改完不重进阅读器也应立即生效。
    // 把值放进 pointerInput 的 key 也能重建，但会连带取消进行中的手势；用 updated state 更稳。
    val currentClickAction by rememberUpdatedState(settings.clickAction)
    val hint: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(startIndex) { viewModel.applyStartIndex(startIndex) }

    // 加载/刷新失败的可见提示。
    // 正文区末尾虽然也有一个 error item，但读到 85% 处刷新失败时它落在屏幕外，
    // 用户只会看到「什么都没发生」。这里统一走 snackbar，文案由 VM 给出
    // （刷新失败会带「刷新失败：」前缀，与首次加载错误区分）。
    LaunchedEffect(state.error) {
        state.error?.let(hint)
    }

    val horizontalMode = settings.method == ReadMethod.Horizontal || settings.method == ReadMethod.DualPage

    // 进入/切换翻页方式时的定位依据：优先服务端恢复位置，其次内存里的当前位置
    val restoreFraction = state.pendingScrollFraction ?: viewModel.currentFraction()

    // 竖排模式的定位：pendingScrollFraction 有值就恢复，否则回章首
    LaunchedEffect(state.currentIndex, state.contentIndex, state.paragraphs, horizontalMode) {
        if (horizontalMode || state.paragraphs.isEmpty()) return@LaunchedEffect
        // 换章时 currentIndex 先变、正文后到：此时 paragraphs 还是**上一章**的，
        // 按它滚到「末尾」（prev 的方向）会闪一帧旧正文。等正文所属章对齐再定位。
        if (state.contentIndex != state.currentIndex) return@LaunchedEffect
        val restore = restoreFraction
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
    LaunchedEffect(listState, state.currentIndex, horizontalMode) {
        if (horizontalMode) return@LaunchedEffect
        snapshotFlow { listState.chapterFraction() }.collect { viewModel.onScrolled(it) }
    }

    // ------------------------------------------------------------------ 自动阅读（P1-8）
    // 只在竖排可用：横翻/双页是「一屏一页」的切换，没有「匀速滚动」这回事，
    // 硬做只会变成「每隔 N 秒自动翻页」，和这里承诺的行为不是同一件事。
    val toggleAutoReading: () -> Unit = {
        if (horizontalMode) {
            hint("自动阅读暂只支持上下滑动")
        } else {
            autoReading = !autoReading
        }
    }

    // 切章打断：自动阅读滚到章末就停，不自动续翻下一章（避免连锁翻章烧流量、
    // 也避免用户离开后书还在自己往下翻）。手动翻章同理。
    // 翻页方式切到横翻/双页也一并打断：那时正文不再由 listState 承载，
    // 留着开关只会「按钮显示暂停、画面不动」。
    LaunchedEffect(state.currentIndex, horizontalMode) { autoReading = false }

    /**
     * 自动滚动循环。
     *
     * 「停」只有三条路，其它一律不停：
     * 1. **滚到章末** —— `canScrollForward` 变 false；
     * 2. **用户拖动** —— `interactionSource` 收到 `DragInteraction.Start`。
     *    程序化滚动（`scrollBy` / `animateScrollToItem`）不会产生 DragInteraction，
     *    所以不会把自己的滚动误判成用户操作（`isScrollInProgress` 会，不能用）。
     * 3. **effect 被取消** —— 切章、离开阅读器、改设置，由 Compose 负责取消。
     *
     * 特别注意**不能**把「滚动权被抢占」当成停止：`scrollBy` 走 `MutatePriority.Default`，
     * 列表重新布局、上一段动画收尾、手势收尾的 fling 都可能短暂抢走它并抛
     * `CancellationException`。它和「用户打断」抛的是同一个异常，含义却相反，
     * 当成停止会让自动阅读滚几秒莫名其妙自己关掉。所以每一帧的失败只跳过该帧，
     * 见 [autoScrollStep]。
     *
     * 实测（emulator-5554 竖排 / 默认 1px 每帧）：527 帧推进 478px ≈ 60px/s，匀速无跳帧；
     * 到章末自动停并清掉开关；手指拖动 140px 立即停且不会自己重开。
     */
    LaunchedEffect(autoReading, settings.autoPageMode, settings.scrollPixel, settings.pageSpeed) {
        if (!autoReading) return@LaunchedEffect
        launch {
            listState.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) autoReading = false
            }
        }
        // 刚切章时列表还没量出版面，`canScrollForward` 是 false、`totalItemsCount` 是 0，
        // 直接开滚会「一启动就判定到底、自己关掉」。等到有内容再开始。
        if (listState.layoutInfo.totalItemsCount == 0) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        }
        if (settings.autoPageMode == AutoPageMode.PARAGRAPH) {
            // 段落滚动：每段停留 pageSpeed 毫秒，然后滚到下一项的开头。
            // 每段行数不同，用固定停留时间而不是按行数加权 —— 与前端
            // 「同一段停留 pageSpeed」的默认语义一致，行为可预期。
            while (true) {
                delay(settings.pageSpeed.toLong())
                val next = listState.firstVisibleItemIndex + 1
                if (next >= listState.layoutInfo.totalItemsCount) break
                autoScrollStep { listState.animateScrollToItem(next) }
            }
        } else {
            // 像素滚动：步长就是「每帧多少像素」，不乘帧率也不做时间补偿。
            // withFrameNanos 跟随屏幕刷新率（60/90/120Hz），比 delay(16) 硬编码
            // 更稳：delay 在低帧率设备上会累积漂移，高刷屏上又会掉帧。
            val step = settings.scrollPixel.coerceAtLeast(1).toFloat()
            while (true) {
                withFrameNanos { }
                // 这一帧的滚动权被抢走就跳过，下一帧接着滚，不要当成「到底了」
                if (!autoScrollStep { listState.scrollBy(step) }) continue
                if (!listState.canScrollForward) break
            }
        }
        // 滚到章末：停下并把「要不要继续」留给用户（不自动跳下一章）
        autoReading = false
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
                        // 选章本身会触发 currentIndex 变化（上面已打断自动阅读），
                        // 这里再显式置 false：同章连点两次时 currentIndex 不变，
                        // 那样就会「进了目录又回来还在自己滚」。
                        autoReading = false
                        viewModel.goToChapter(index)
                        showCatalog = false
                        controlsVisible = false
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (horizontalMode) {
                HorizontalReader(
                    state = state,
                    settings = settings,
                    palette = palette,
                    onPrevChapter = viewModel::prevChapter,
                    onNextChapter = viewModel::nextChapter,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onProgress = viewModel::onScrolled,
                    onRestoreConsumed = viewModel::consumePendingScroll,
                    restoreFraction = restoreFraction,
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
                        .pointerInput(autoReading) {
                            // 九宫格：上排上一屏、下排下一屏、中间一排切控件；
                            // 四边 4% 是防误触留白（相对屏宽/屏高，不是厘米）。
                            detectTapGestures { offset ->
                                // 自动阅读中点按 = 暂停（不翻屏、不切控件）；再点一次恢复
                                if (autoReading) {
                                    autoReading = false
                                    return@detectTapGestures
                                }
                                when (verticalTapZone(offset, size)) {
                                    // 读最新档位
                                    ReaderTapZone.Menu -> controlsVisible = !controlsVisible

                                    ReaderTapZone.Prev -> if (currentClickAction != ClickAction.NONE) {
                                        scope.launch {
                                            val viewport =
                                                listState.layoutInfo.viewportSize.height.toFloat()
                                            if (listState.canScrollBackward) {
                                                listState.animateScrollBy(-viewport * 0.8f)
                                            } else if (currentClickAction == ClickAction.AUTO) {
                                                // 到顶且 auto 才切章；落点由 VM 统一给 1f（章末）
                                                viewModel.prevChapter()
                                            }
                                        }
                                    }

                                    ReaderTapZone.Next -> if (currentClickAction != ClickAction.NONE) {
                                        scope.launch {
                                            val viewport =
                                                listState.layoutInfo.viewportSize.height.toFloat()
                                            if (listState.canScrollForward) {
                                                listState.animateScrollBy(viewport * 0.8f)
                                            } else if (currentClickAction == ClickAction.AUTO) {
                                                viewModel.nextChapter()
                                            }
                                        }
                                    }

                                    ReaderTapZone.Ignore -> Unit
                                }
                            }
                        },
                )
            }
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
                onOpenCatalog = {
                    autoReading = false
                    showCatalog = true
                },
                onRefresh = {
                    // 刷新会换掉正文，滚到一半刷新等于「脚下的地在动」，先停
                    autoReading = false
                    viewModel.refreshChapter()
                },
                onHint = hint,
                moreExpanded = moreMenuExpanded,
                onMoreExpandedChange = { expanded ->
                    if (expanded) autoReading = false
                    moreMenuExpanded = expanded
                },
                isNight = palette.isDark,
                onToggleNight = {
                    deps.readerSettings.setTheme(if (palette.isDark) 0 else ReaderPalettes.lastIndex)
                },
                onShowDetail = {
                    autoReading = false
                    state.book?.let(onShowDetail)
                },
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
                autoReading = autoReading,
                onPrev = viewModel::prevChapter,
                onNext = viewModel::nextChapter,
                onSeekChapter = viewModel::goToChapter,
                onToggleAutoReading = toggleAutoReading,
                onOpenCatalog = {
                    autoReading = false
                    showCatalog = true
                },
                onOpenSettings = {
                    autoReading = false
                    activePanel = ReaderPanel.Settings
                },
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
        // 面板跑在独立 Window（Dialog）里，容器色从阅读主题派生（不留白面板/亮粉面板），
        // 亮度则在这里**再设一次窗口亮度**：面板窗口有自己的 LayoutParams，
        // 不设就会掉回系统亮度，低亮度下打开设置会闪一下变亮。
        val sheetScheme = readerSheetColorScheme(palette)
        ModalBottomSheet(
            onDismissRequest = { activePanel = null },
            sheetState = sheetState,
            containerColor = sheetScheme.surface,
        ) {
            // ModalBottomSheet 不会因为 containerColor 变化而推导内容色，
            // LocalContentColor 仍是外层主题的深色 —— 暗面板上标签会整片看不见。
            CompositionLocalProvider(LocalContentColor provides sheetScheme.onSurface) {
                MaterialTheme(colorScheme = sheetScheme) {
                    ApplyWindowBrightness(settings.brightness)
                    when (panel) {
                        ReaderPanel.Settings -> ReaderSettingsPanel(
                            settings = settings,
                            // 滑杆拖动中只改内存（正文/面板实时跟随），松手才落盘
                            onFontSizePreview = deps.readerSettings::previewFontSize,
                            onFontSizeChange = deps.readerSettings::setFontSize,
                            onThemeChange = deps.readerSettings::setTheme,
                            onMethodChange = deps.readerSettings::setMethod,
                            onBrightnessPreview = deps.readerSettings::previewBrightness,
                            onBrightnessChange = deps.readerSettings::setBrightness,
                            onEyeCareChange = deps.readerSettings::setEyeCare,
                            onLineHeightChange = deps.readerSettings::setLineHeight,
                            onParagraphSpacingChange = deps.readerSettings::setParagraphSpacing,
                            // 自动阅读（P1-8）：面板只配「怎么滚」，开关仍在底栏
                            onClickActionChange = deps.readerSettings::setClickAction,
                            onAutoPageModeChange = deps.readerSettings::setAutoPageMode,
                            onScrollPixelChange = deps.readerSettings::setScrollPixel,
                            onPageSpeedChange = deps.readerSettings::setPageSpeed,
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
            // 消费点击：控件是覆盖层，点书名/圆钮之间的空白若穿透到正文，
            // 会把控件又切回收起状态（R-08，与底栏一致的处理）。
            // 圆钮/菜单是更深的节点，自己先拿到事件，不会被这里抢走。
            .pointerInput(Unit) { detectTapGestures { } }
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
                // 菜单是 Popup（独立 Window），默认用应用主题的亮色 surface ——
                // 暗色正文下这就是「整屏最亮的一块」。这里用 palette 派生的面板色，
                // 并**同时**提供 LocalContentColor 与 MaterialTheme
                // （AGENTS「已知陷阱 6」：只改 containerColor 会让标签沿用外层深色，
                // 暗底上整片看不见）。亮度由窗口级 screenBrightness 统一压暗，不再手算。
                val menuScheme = readerSheetColorScheme(palette)
                DropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = { onMoreExpandedChange(false) },
                    containerColor = menuScheme.surface,
                ) {
                    CompositionLocalProvider(LocalContentColor provides menuScheme.onSurface) {
                        MaterialTheme(colorScheme = menuScheme) {
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
    /** 自动阅读是否正在跑；决定「自动翻页」项的图标/文案/激活态。 */
    autoReading: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeekChapter: (Int) -> Unit,
    onToggleAutoReading: () -> Unit,
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
                BarAction(
                    icon = if (autoReading) PauseGlyph else ReaderIcons.AutoPage,
                    label = if (autoReading) "暂停" else "自动翻页",
                    palette = palette,
                    active = autoReading,
                ) { onToggleAutoReading() }
                BarAction(Icons.AutoMirrored.Filled.List, "目录", palette, onClick = onOpenCatalog)
                BarAction(ReaderIcons.VolumeUp, "朗读", palette) { onHint("朗读将在 P3 接入") }
                BarAction(Icons.Filled.Settings, "设置", palette, onClick = onOpenSettings)
            }
        }
    }
}

/**
 * 底栏功能项：图标在上、文字在下，宽度均分。
 *
 * [active] 只影响配色（图标与文字升到正文色 + 一层淡淡的前景底），
 * 不改变布局，避免「开/关」之间整条底栏跳动。
 */
@Composable
private fun BarAction(
    icon: ImageVector,
    label: String,
    palette: ReaderPalette,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .background(
                color = if (active) palette.text.copy(alpha = 0.10f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) palette.text else palette.icon,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) palette.text else palette.secondaryText,
            maxLines = 1,
        )
    }
}

/**
 * 「暂停」图标。
 *
 * `material-icons-core` 里没有 Pause（它在 extended 包里，会给 debug 构建多塞几十 MB），
 * 这里直接用 Material Symbols 的 24dp 路径数据。本该放进 `ReaderIcons`，但本轮改动
 * 只允许动三个文件、且 `ReaderIcons.kt` 正被别的改动占用 —— 下次动它时把这段搬过去。
 */
private val PauseGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = addPathNodes("M6 5h4v14H6V5zm8 0h4v14h-4V5z"),
            fill = SolidColor(Color.Black),
        )
    }.build()
}

/**
 * 执行一步程序化滚动，把「滚动权被抢占」与「effect 被取消」区分开。
 *
 * `scrollBy` / `animateScrollToItem` 都走 `MutatePriority.Default`，被更高优先级的
 * 手势或另一个滚动抢占时，`MutatorMutex` 会取消本次滚动并抛 `CancellationException`。
 * 这两件事看起来是同一个异常，含义完全不同：
 * - **只被抢走这一帧**：effect 的 Job 仍然 active —— 返回 false，调用方跳过该帧，
 *   下一帧继续（用户手势那种真打断由 `DragInteraction` 监听负责停下）；
 * - **effect 自己被取消**：切章/离开阅读器/改设置，Job 已 inactive —— 必须原样抛出，
 *   否则协程「取消不干净」，`LaunchedEffect` 的收尾与兄弟协程都会被搅乱。
 */
private suspend fun autoScrollStep(block: suspend () -> Unit): Boolean =
    try {
        block()
        true
    } catch (e: CancellationException) {
        if (!currentCoroutineContext().isActive) throw e
        false
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
