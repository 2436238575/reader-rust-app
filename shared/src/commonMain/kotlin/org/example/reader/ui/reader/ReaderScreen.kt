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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.reader.core.AppDependencies
import org.example.reader.core.font.remoteFileName
import org.example.reader.core.model.Book
import org.example.reader.core.model.ChapterBlock
import org.example.reader.core.model.ImageBlock
import org.example.reader.core.model.TextBlock
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

/** 单章列表的一次恢复身份；正文应用代际、章号或渲染类别变化都会使旧完成标记失效。 */
private data class SingleChapterRestoreKey(
    val chapterIndex: Int,
    val contentIndex: Int,
    val body: ChapterBlockSnapshot?,
    val horizontalMode: Boolean,
    val continuousMode: Boolean,
)

/**
 * 单章列表「正文 + 渲染方式」签名，用来判断刷新后是否需要重新对齐。
 *
 * 注意它与 [SingleChapterRestoreKey] 的差别：这里**不含** `revision`。同一份正文的
 * 刷新会产生新 revision（恢复 effect 要重跑），但块列表完全相同、LazyColumn 已保留
 * 真实视口偏移，再 scrollToItem 会把顶部被裁的长段落换成「块首对齐」，用户会看到
 * 整屏上跳。内容真变了（换章、洗稿后段数/文字不同）才重新定位。
 */
private data class SingleChapterContentKey(
    val chapterIndex: Int,
    val layout: BlockLayout,
    val horizontalMode: Boolean,
    val continuousMode: Boolean,
)

/**
 * 块列表的**版式身份**：文本比文字，图片比锚点/尺寸/说明，**不比 URL**。
 *
 * 刷新本章会重新求值配图接口，签名地址每次都变（`x-signature`），但版面完全一样。
 * 若把 URL 算进签名，每次刷新都会被判成「内容变了」→ 重新 scrollToItem，
 * 正好把 96cfd46 修掉的「刷新上跳」重新引回来。图片尺寸变了（书源换了图）仍算变化。
 */
private class BlockLayout(val blocks: List<ChapterBlock>) {
    override fun equals(other: Any?): Boolean {
        if (other !is BlockLayout || blocks.size != other.blocks.size) return false
        for (index in blocks.indices) {
            val left = blocks[index]
            val right = other.blocks[index]
            val same = when {
                left is TextBlock && right is TextBlock -> left.text == right.text
                left is ImageBlock && right is ImageBlock ->
                    left.paraIndex == right.paraIndex &&
                        left.width == right.width &&
                        left.height == right.height &&
                        left.caption == right.caption
                else -> false
            }
            if (!same) return false
        }
        return true
    }

    override fun hashCode(): Int = blocks.size * 31 + (blocks.lastOrNull()?.paraIndex ?: 0)
}

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
    // 图片灯箱（P1-9 L5）：独占一层，打开前先清掉控件与所有面板/菜单。
    var lightboxUrl by remember { mutableStateOf<String?>(null) }
    val openImage: (String) -> Unit = { url ->
        controlsVisible = false
        showCatalog = false
        activePanel = null
        moreMenuExpanded = false
        lightboxUrl = url
    }

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

    // 需要联网下载的字体（鸿蒙/小米）：revision 变化会触发重组，让字体族重新解析
    val fontRevision by deps.fonts.revision.collectAsStateWithLifecycle()
    val fontPath = remember(settings.fontFamily, fontRevision) {
        deps.fonts.cachedPath(settings.fontFamily)
    }
    LaunchedEffect(settings.fontFamily, fontRevision) {
        val font = settings.fontFamily
        if (font.remoteFileName == null || deps.fonts.cachedPath(font) != null) return@LaunchedEffect
        hint("正在下载字体（约 8MB）…")
        val result = deps.fonts.ensure(font)
        if (result.isSuccess) {
            // 后台预解析 Typeface，避免下载完成后第一次重组在主线程解析 8MB 字体
            withContext(Dispatchers.Default) {
                font.toFontFamily(deps.fonts.cachedPath(font))
            }
        } else {
            hint("字体下载失败，暂用系统字体：${result.exceptionOrNull()?.message ?: "未知错误"}")
        }
    }

    LaunchedEffect(startIndex) { viewModel.applyStartIndex(startIndex) }

    // 只改「显示方式」的设置（配图开关 / 简繁转换）：改动后当场重建当前章。
    // 首帧不触发——那不是「改动」，只是进入阅读器时读到的现值。
    var lastDisplayKey by remember {
        mutableStateOf(settings.showChapterImages to settings.chineseMode)
    }
    LaunchedEffect(settings.showChapterImages, settings.chineseMode) {
        val key = settings.showChapterImages to settings.chineseMode
        if (lastDisplayKey == key) return@LaunchedEffect
        lastDisplayKey = key
        viewModel.rebuildCurrentChapter()
    }

    // 加载/刷新失败的可见提示。
    // 正文区末尾虽然也有一个 error item，但读到 85% 处刷新失败时它落在屏幕外，
    // 用户只会看到「什么都没发生」。这里统一走 snackbar，文案由 VM 给出
    // （刷新失败会带「刷新失败：」前缀，与首次加载错误区分）。
    LaunchedEffect(state.error) {
        state.error?.let(hint)
    }

    val horizontalMode = settings.method == ReadMethod.Horizontal || settings.method == ReadMethod.DualPage
    // 连续阅读：整块窗口拼多章。与横翻互斥，也不参与横翻的定位/上报路径
    // （那边按「章内段落」算，连续模式按「窗口内首个可见 item」算，各管各的）。
    val continuousMode =
        settings.method == ReadMethod.Scroll || settings.method == ReadMethod.Scroll2

    // 进入/切换翻页方式时只从 VM 的位置真源恢复；pendingScrollFraction 是待消费的一次性
    // UI 指令，可能残留旧值，不能覆盖 currentFraction()。
    val restoreFraction = viewModel.currentFraction()
    val singleChapterRestoreKey = SingleChapterRestoreKey(
        chapterIndex = state.currentIndex,
        contentIndex = state.contentIndex,
        body = state.body,
        horizontalMode = horizontalMode,
        continuousMode = continuousMode,
    )
    var completedSingleChapterRestore by remember {
        mutableStateOf<SingleChapterRestoreKey?>(null)
    }
    // 「同一章 + 同一份块列表 + 同一渲染方式」的刷新不必重新对齐：LazyColumn 自己会
    // 保留首项下标与项内像素偏移，重新 scrollToItem(块首) 反而会把视口顶部长段落的
    // 尾巴顶出屏幕（实测 255px）。只有正文真的变了（换章/洗稿/首次进入）才重新定位。
    var renderedContent by remember {
        mutableStateOf<SingleChapterContentKey?>(null)
    }

    // 单章竖排按 currentFraction 恢复；恢复完成前禁止列表快照覆盖 VM 真源。
    // revision 让相同正文的同章刷新也重新定位，不能以结构相等的 blocks 代替应用代际。
    // 横翻自己定位（PagerState），连续模式也自己定位（窗口要等正文到位）——两边都跳过这里。
    LaunchedEffect(
        state.currentIndex,
        state.contentIndex,
        state.blocks,
        state.body?.revision,
        horizontalMode,
        continuousMode,
    ) {
        if (horizontalMode || continuousMode || state.blocks.isEmpty()) {
            // 横翻/连续模式不用本列表定位；同时清掉签名，切回单章时必须重新对齐
            // （横翻期间 VM 位置可能已经变了，保留旧像素偏移会回到离开时的位置）。
            renderedContent = null
            return@LaunchedEffect
        }
        // 换章时 currentIndex 先变、正文后到：此时快照还是**上一章**的，
        // 按它滚到「末尾」（prev 的方向）会闪一帧旧正文。等正文所属章对齐再定位。
        if (state.contentIndex != state.currentIndex) return@LaunchedEffect
        val contentKey = SingleChapterContentKey(
            chapterIndex = state.currentIndex,
            layout = BlockLayout(state.blocks),
            horizontalMode = horizontalMode,
            continuousMode = continuousMode,
        )
        val keepViewport = renderedContent == contentKey
        renderedContent = contentKey
        if (keepViewport) {
            // 内容与渲染方式都没变：保留现有像素偏移，只重新开放采集。
            completedSingleChapterRestore = singleChapterRestoreKey
            return@LaunchedEffect
        }
        val restore = restoreFraction
        // 口径统一为**段落比例**（R-06）：上报是「首个可见段落下标 / 段数」，
        // 恢复必须用它的逆运算，否则退出重进会稳定偏移若干段。
        // 列表第 0 项是章头，段落 item 的下标 = 段落下标 + 1。
        val total = state.blocks.size
        if (restore > 0.001f && total > 0) {
            val block = blockAtFraction(restore, total)
            listState.scrollToItem((block + 1).coerceIn(0, total))
            viewModel.consumePendingScroll()
        } else {
            listState.scrollToItem(0)
        }
        // 先完成程序化定位，再开放采集；effect 重启前的旧 key 样本始终无效。
        completedSingleChapterRestore = singleChapterRestoreKey
    }

    // 章内位置回传：VM 内部按 10s 节流落库，不必在这里控频。
    // 连续模式自己按窗口回传（见 ContinuousReader），这里跳过，否则会按单章的项数报错章。
    LaunchedEffect(
        listState,
        singleChapterRestoreKey,
    ) {
        if (horizontalMode || continuousMode) return@LaunchedEffect
        val paragraphCount = state.blocks.size
        snapshotFlow {
            val completed = completedSingleChapterRestore == singleChapterRestoreKey
            val fraction = if (completed) {
                listState.chapterParagraphFraction(paragraphCount)
            } else {
                null
            }
            completed to fraction
        }.collect { (completed, fraction) ->
            if (completed && fraction != null) viewModel.onScrolled(fraction)
        }
    }

    // 连续模式只改 `currentIndex`、不动 `contentIndex`（正文是整块窗口）。切回单章竖排 /
    // 横翻后「正文所属章」会落后于当前章，而这两种渲染都以 `contentIndex == currentIndex`
    // 为守卫 —— 没人补这一刀就会停在「正文为空」或显示上一章正文，且不会自愈。
    //
    // 除「切翻页方式」外也挂在两个下标上：切换那一刻若正有一条加载在途，
    // `ensureContentForCurrentIndex` 会主动让路（见其注释），等它落地后这里会再判一次。
    LaunchedEffect(
        horizontalMode,
        continuousMode,
        state.currentIndex,
        state.contentIndex,
    ) {
        // 连续模式内部 contentIndex 本来就滞后，这里绝不能插手
        if (continuousMode) return@LaunchedEffect
        if (state.contentIndex != state.currentIndex) viewModel.ensureContentForCurrentIndex()
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
    //
    // **连续阅读例外**：那里的「换章」是滚过章末自然发生的（正文本来就是一整条连续的流），
    // 每跨一章就停一次等于自动阅读根本没法用。手动跳章（目录 / 底栏滑块）都会显式停下。
    LaunchedEffect(state.currentIndex, horizontalMode, continuousMode) {
        if (!continuousMode) autoReading = false
    }

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

    /**
     * 竖排正文的点按层（单章竖排与连续阅读共用）。
     *
     * 挂在**内容区**而不是每个组件内部：`ContinuousReader` 自己再挂一层会双触发
     * （一次切控件、一次被当成翻屏），横翻那套九宫格也早已在组件内部，不在此列。
     */
    val verticalTapModifier = Modifier
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
                            val viewport = listState.layoutInfo.viewportSize.height.toFloat()
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
                            val viewport = listState.layoutInfo.viewportSize.height.toFloat()
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
        }

    // 离开阅读器（返回 / 切页）时立刻落库，不等节流窗口
    DisposableEffect(Unit) {
        onDispose { viewModel.flushProgress() }
    }

    // 返回键：先关灯箱 → 再关目录 → 再关面板 → 再收控件 → 最后才退出阅读器
    AppBackHandler {
        when {
            lightboxUrl != null -> lightboxUrl = null
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
                    imageLoader = deps.imageLoader,
                    onImageClick = openImage,
                    fontPath = fontPath,
                    onPrevChapter = viewModel::prevChapter,
                    onNextChapter = viewModel::nextChapter,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onProgress = viewModel::onScrolled,
                    onRestoreConsumed = viewModel::consumePendingScroll,
                    restoreFraction = restoreFraction,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (continuousMode) {
                // 上下滚动：多章拼成一根列表；「上下滚动2」只多一个「隐藏已读章」
                ContinuousReader(
                    state = state,
                    settings = settings,
                    palette = palette,
                    viewModel = viewModel,
                    listState = listState,
                    imageLoader = deps.imageLoader,
                    onImageClick = openImage,
                    fontPath = fontPath,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    restoreFraction = restoreFraction,
                    hideReadChapters = settings.method == ReadMethod.Scroll2,
                    modifier = verticalTapModifier,
                )
            } else {
                ReaderContent(
                    state = state,
                    settings = settings,
                    palette = palette,
                    listState = listState,
                    imageLoader = deps.imageLoader,
                    onImageClick = openImage,
                    fontPath = fontPath,
                    onPrev = viewModel::prevChapter,
                    onNext = viewModel::nextChapter,
                    modifier = verticalTapModifier,
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
                onSeekChapter = { index ->
                    // 拖滑块跳章是明确的人工操作：先停下自动阅读再跳。
                    // 连续模式下「换章不打断自动阅读」（滚过章末是正常阅读），
                    // 这条就是那个例外场景的兜底。
                    autoReading = false
                    viewModel.goToChapter(index)
                },
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

        // L5 图片灯箱：盖住正文与控件（sheet 是独立窗口，打开前已被 openImage 清掉）。
        lightboxUrl?.let { url ->
            ImageLightbox(
                url = url,
                imageLoader = deps.imageLoader,
                onClose = { lightboxUrl = null },
            )
        }
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
                            onMethodChange = { method ->
                                if (method == settings.method) return@ReaderSettingsPanel
                                // 所有列表源都在切换前同步读取真实视口，覆盖 SlideUp 与
                                // Scroll↔Scroll2；横翻/双页则沿用真实翻页已写入的 VM 真源。
                                val contentMatches = state.contentIndex == state.currentIndex
                                val layout = listState.layoutInfo
                                // 与 chapterParagraphFraction 共用同一「块顶进入视口」口径，
                                // 保证切换模式时交出去的位置和滚动采样一致。
                                val first = firstContinuousViewportItem(
                                    items = layout.visibleItemsInfo,
                                    viewportStart = layout.viewportStartOffset,
                                ) { it.offset }
                                val singleHandoff = if (settings.method == ReadMethod.SlideUp && contentMatches) {
                                    singleChapterParagraphFraction(first?.index, state.blocks.size)
                                } else {
                                    null
                                }
                                val continuousHandoff = if (continuousMode && contentMatches) {
                                    continuousFractionForModeSwitch(
                                        key = first?.key as? String,
                                        currentChapter = state.currentIndex,
                                        totalParagraphs = state.blocks.size,
                                    )
                                } else {
                                    null
                                }
                                viewModel.onModeSwitchPosition(
                                    modeSwitchFraction(
                                        sourceMethod = settings.method,
                                        currentFraction = viewModel.currentFraction(),
                                        singleChapterFraction = singleHandoff,
                                        continuousFraction = continuousHandoff,
                                        chapterEndRestore = viewModel.isChapterEndRestore(),
                                    ),
                                )
                                deps.readerSettings.setMethod(method)
                            },
                            onBrightnessPreview = deps.readerSettings::previewBrightness,
                            onBrightnessChange = deps.readerSettings::setBrightness,
                            onEyeCareChange = deps.readerSettings::setEyeCare,
                            onLineHeightChange = deps.readerSettings::setLineHeight,
                            onParagraphSpacingChange = deps.readerSettings::setParagraphSpacing,
                            onFontFamilyChange = deps.readerSettings::setFontFamily,
                            onFontWeightChange = deps.readerSettings::setFontWeight,
                            onPageWidthChange = deps.readerSettings::setPageWidth,
                            onShowChapterImagesChange = deps.readerSettings::setShowChapterImages,
                            onChineseModeChange = deps.readerSettings::setChineseMode,
                            onPreloadChange = deps.readerSettings::setPreloadEnabled,
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

    // 灯箱已在 Box 内绘制（L5）；返回键由上面的层级统一先关它。
}

@Composable
private fun ReaderContent(
    state: ReaderUiState,
    settings: ReaderSettings,
    palette: ReaderPalette,
    listState: androidx.compose.foundation.lazy.LazyListState,
    imageLoader: ImageLoader,
    onImageClick: (String) -> Unit,
    /** 已下载字体的本地路径；未下载时为空，回退系统字族。 */
    fontPath: String?,
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
    if (state.error != null && state.blocks.isEmpty()) {
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
    val lineMetrics = ReaderLineMetrics.from(settings, fontPath)
    val paragraphGap = lineMetrics.paragraphGapDp(LocalDensity.current)
    val bodyStyle = lineMetrics.bodyStyle(palette.text)

    // 页宽：只有正文列收窄居中，点按热区仍由外层 modifier（全屏）承担
    BoxWithConstraints(modifier.fillMaxSize()) {
        val side = readerSidePadding(maxWidth, settings.pageWidth.dp)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = side,
                end = side,
                top = topInset + 16.dp,
                bottom = bottomInset + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(paragraphGap),
            modifier = Modifier.fillMaxSize(),
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
            itemsIndexed(state.blocks) { _, block ->
                ChapterBlockContent(
                    block = block,
                    bodyStyle = bodyStyle,
                    imageLoader = imageLoader,
                    onImageClick = onImageClick,
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
 * 章内阅读位置（0..1），**段落比例**口径（R-06）。
 *
 * 列表结构固定是「章头 item + 段落 item + 尾部若干 item」，只有段落能表达「读到哪」：
 * 旧实现拿 `totalItemsCount` 当分母、拿块下标当分子，分母里混进了章头/进度/错误/翻章行，
 * 于是同一段落在竖排与横翻（按段落下标）算出来的比例不同，换模式或重进就稳定差一页。
 *
 * 这里分子一律是「首个可见块下标」（首项是章头时取 0），分母是该章块数，
 * 与 `HorizontalReader` 的 `blockFraction`、连续阅读的上报口径完全一致。
 *
 * 「首个可见」必须用 `firstContinuousViewportItem`（排除块顶被裁到视口外的那一项），
 * **不能**直接取 `visibleItemsInfo.firstOrNull()`：长段落可以顶在视口外很远
 * （实测 first.offset=-441 仍有大半在屏内），取它会把位置报成上一块，恢复又按该块对齐，
 * 于是刷新一次退一块（实测 7010→6907，正文同步后退一段）。这里的「可见」是
 * 「块顶进入视口」，与 `scrollToItem` 的对齐口径互逆。
 */
private fun androidx.compose.foundation.lazy.LazyListState.chapterParagraphFraction(
    paragraphCount: Int,
): Float? {
    val layout = layoutInfo
    val first = firstContinuousViewportItem(
        items = layout.visibleItemsInfo,
        viewportStart = layout.viewportStartOffset,
    ) { it.offset }
    return singleChapterParagraphFraction(first?.index, paragraphCount)
}
