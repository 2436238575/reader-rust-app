package org.example.reader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.reader.core.settings.AutoPageMode
import org.example.reader.core.settings.ReadMethod
import org.example.reader.core.settings.ReaderSettings
import kotlin.math.roundToInt

/**
 * 阅读设置面板。
 *
 * 结构与番茄小说一致：亮度 → 字号 → 背景 → 翻页方式 → 其他 → 自动阅读，
 * 每组一行、不加分组标题，靠间距区分。控件本身全部用 Material 3 组件
 * （Slider / SegmentedButton / FilterChip / OutlinedButton）。
 *
 * 未接入的入口（系统字体、评论设置、更多）不隐藏，
 * 点按给出明确提示，避免用户以为是坏的。
 *
 * 「自动阅读」（P1-8）是真控件：滚动方式两个 chip + 各模式对应的速度滑杆。
 * 开/关不在面板里 —— 底栏「自动翻页」是唯一开关，面板只负责「怎么滚」，
 * 免得两处各维护一份 `autoReading` 状态。
 *
 * 容器（R-22）：`ModalBottomSheet` 只按 `safeDrawing` 让出系统栏与输入法高度，
 * 内容自身不滚动，键盘弹起后可见高度变小，底部几行就被推出可视区、拖不到；
 * 所以内容列必须自己可滚（insets 已由 sheet 让出，见下方 `Column` 的注释）。
 *
 * ⚠️ 往这个面板加内容的硬约束：**不要引入同向可滚动容器**
 * （`LazyColumn` / 内层 `verticalScroll` / `fillMaxHeight`）。外层已经是
 * `verticalScroll`，再嵌一层同向滚动会在运行期崩 `infinity maximum height`，
 * 而且编译期看不出来。逐行排布用 `Column` / `Row` 即可（字号滑杆、自动阅读组都是这么做的）。
 */
@Composable
fun ReaderSettingsPanel(
    settings: ReaderSettings,
    /** 字号滑杆拖动中：只改内存态，正文实时重排。 */
    onFontSizePreview: (Int) -> Unit,
    /** 字号最终落盘（滑杆松手 / 步进按钮）。 */
    onFontSizeChange: (Int) -> Unit,
    onThemeChange: (Int) -> Unit,
    onMethodChange: (ReadMethod) -> Unit,
    /** 亮度滑杆拖动中：只改内存态，当前窗口亮度实时跟随。 */
    onBrightnessPreview: (Float) -> Unit,
    /** 亮度最终落盘（滑杆松手）。 */
    onBrightnessChange: (Float) -> Unit,
    /** 护眼开关：只提高正文对比度，不叠加任何颜色蒙版。 */
    onEyeCareChange: (Boolean) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    /**
     * 自动阅读（P1-8）三个回调。
     *
     * 由 `ReaderScreen` 的调用点接到 `deps.readerSettings::setAutoPageMode` /
     * `::setScrollPixel` / `::setPageSpeed`。**故意不给默认值**：这三个是功能必需的回调，
     * 留默认值会让「调用点漏传 → chip 能点但设置不生效」静默通过编译。
     */
    onAutoPageModeChange: (String) -> Unit,
    onScrollPixelChange: (Int) -> Unit,
    onPageSpeedChange: (Int) -> Unit,
    onReset: () -> Unit,
    onHint: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var spacingExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 只需要「能滚」：ModalBottomSheet 的内容容器已经用
            // `BottomSheetDefaults.modalWindowInsets`（= safeDrawing ∋ ime）做过
            // `windowInsetsPadding`，且 windowInsetsPadding **会把 inset 消费掉**，
            // 这里再加 `.imePadding()` 拿到的是 0（实测无任何效果，属死代码）。
            // 真正的缺口是内容自身不滚动 → 键盘弹起后底部几行被推出可视区（R-22）。
            // ⚠️ 保守取舍：M3 sheet 内部可能已自行处理 IME，个别版本/机型若实测仍有遮挡，
            // 再在此处补 `.imePadding()`（顺序必须是 imePadding → verticalScroll → padding）。
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "阅读设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReset) { Text("重置") }
        }

        BrightnessRow(
            brightness = settings.brightness,
            eyeCare = settings.eyeCare,
            onBrightnessPreview = onBrightnessPreview,
            onBrightnessChange = onBrightnessChange,
            onEyeCareChange = onEyeCareChange,
        )

        FontSizeRow(
            fontSize = settings.fontSize,
            onFontSizePreview = onFontSizePreview,
            onFontSizeChange = onFontSizeChange,
            onOpenFontFamily = { onHint("字体选择将在 P1-1 后续接入") },
        )

        BackgroundRow(selected = settings.themeIndex, onSelect = onThemeChange)

        PageMethodRow(
            method = settings.method,
            onSelect = { method ->
                onMethodChange(method)
                // 上下滚动两种还没做，先明确告知，不让人以为点了没反应
                if (method == ReadMethod.Scroll || method == ReadMethod.Scroll2) {
                    onHint("「${method.label}」将在后续接入，当前按上下滑动呈现")
                }
            },
        )

        MiscRow(
            spacingExpanded = spacingExpanded,
            onToggleSpacing = { spacingExpanded = !spacingExpanded },
            onOpenComment = { onHint("评论设置将在 P3 章评段评时接入") },
            onOpenMore = { onHint("更多设置将在 P1 阶段补齐") },
        )

        AnimatedVisibility(
            visible = spacingExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider()
                StepperRow(
                    label = "行高",
                    value = oneDecimal(settings.lineHeight),
                    onMinus = { onLineHeightChange(settings.lineHeight - 0.1f) },
                    onPlus = { onLineHeightChange(settings.lineHeight + 0.1f) },
                )
                StepperRow(
                    label = "段距",
                    value = oneDecimal(settings.paragraphSpacing),
                    onMinus = { onParagraphSpacingChange(settings.paragraphSpacing - 0.1f) },
                    onPlus = { onParagraphSpacingChange(settings.paragraphSpacing + 0.1f) },
                )
            }
        }

        AutoReadGroup(
            autoPageMode = settings.autoPageMode,
            scrollPixel = settings.scrollPixel,
            pageSpeed = settings.pageSpeed,
            // 横翻/双页下底栏按钮只会弹提示，这里提前把原因写出来，别让人白调参数
            autoReadAvailable = settings.method != ReadMethod.Horizontal &&
                settings.method != ReadMethod.DualPage,
            onAutoPageModeChange = onAutoPageModeChange,
            onScrollPixelChange = onScrollPixelChange,
            onPageSpeedChange = onPageSpeedChange,
        )
    }
}

/**
 * 亮度行。
 *
 * 亮度写的是**当前应用窗口**的 `screenBrightness`（见 `ApplyWindowBrightness`），
 * 不是叠一层黑色蒙版：蒙版会让正文与背景一起变暗、对比度塌掉，暗处反而更看不清。
 *
 * 拖动中走 [onBrightnessPreview]（只改内存态，窗口亮度实时跟进），
 * 松手才走 [onBrightnessChange] 落盘 —— 否则每一帧都会连写 7 个 SharedPreferences 键。
 * 本地 `dragValue` 保证 `onValueChangeFinished` 拿到的是松手瞬间的值，
 * 不依赖回调闭包里的旧快照。
 *
 * 「护眼」保持开关形态，作用是提高正文对比度（低蓝光由系统护眼模式负责，
 * 各家做得比应用层色偏滤镜更好），下面一行小字说明，避免被误解成滤镜。
 */
@Composable
private fun BrightnessRow(
    brightness: Float,
    eyeCare: Boolean,
    onBrightnessPreview: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onEyeCareChange: (Boolean) -> Unit,
) {
    var dragValue by remember(brightness) { mutableFloatStateOf(brightness) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("亮度", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = dragValue,
                onValueChange = { value ->
                    dragValue = value
                    onBrightnessPreview(value)
                },
                onValueChangeFinished = { onBrightnessChange(dragValue) },
                valueRange = ReaderSettings.MIN_BRIGHTNESS..ReaderSettings.MAX_BRIGHTNESS,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            FilterChip(
                selected = eyeCare,
                onClick = { onEyeCareChange(!eyeCare) },
                label = { Text("护眼") },
            )
        }
        Text(
            text = "护眼：提高正文对比度（不叠加颜色蒙版，低蓝光请用系统护眼）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
        )
    }
}

/**
 * 字号行：步进按钮 + 滑杆。
 *
 * 步进按钮是离散的一次性操作，直接落盘；滑杆与亮度同理，
 * 拖动中只预览、松手落盘（`steps` 让取值按 1sp 吸附，不会拖出 18.37 这种字号）。
 */
@Composable
private fun FontSizeRow(
    fontSize: Int,
    onFontSizePreview: (Int) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onOpenFontFamily: () -> Unit,
) {
    var dragValue by remember(fontSize) { mutableFloatStateOf(fontSize.toFloat()) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(label = "A−", onClick = { onFontSizeChange(fontSize - 1) })
            Text(
                text = "$fontSize",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(44.dp),
            )
            StepButton(label = "A＋", onClick = { onFontSizeChange(fontSize + 1) })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenFontFamily) { Text("系统字体") }
        }
        Slider(
            value = dragValue,
            onValueChange = { value ->
                dragValue = value
                onFontSizePreview(value.roundToInt())
            },
            onValueChangeFinished = { onFontSizeChange(dragValue.roundToInt()) },
            valueRange = ReaderSettings.MIN_FONT_SIZE.toFloat()..ReaderSettings.MAX_FONT_SIZE.toFloat(),
            // 步数 = 中间可停的整数值个数，让滑杆按 1sp 吸附
            steps = (ReaderSettings.MAX_FONT_SIZE - ReaderSettings.MIN_FONT_SIZE - 1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun BackgroundRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("背景", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ReaderPalettes.forEachIndexed { index, palette ->
                val isSelected = index == selected
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(palette.background)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        // clip 在前，水波纹自然被裁成圆形
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = palette.label,
                            tint = if (palette.isDark) Color.White else palette.text,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 双页翻页的最小窗口宽度。
 *
 * 与前端 `utils/horizontalPaging.ts` 的 `DUAL_PAGE_MIN_WIDTH = 960` 数值一致，但**单位不同**：
 * 前端是 CSS px，这里按 Android 的 dp 取。不要因为「前端写 960」就顺手当成 px 换算。
 */
private const val DUAL_PAGE_MIN_WIDTH_DP = 960

@Composable
private fun PageMethodRow(method: ReadMethod, onSelect: (ReadMethod) -> Unit) {
    // 与前端一致：双页在窄屏「可见但不可选」，不是从列表里消失——
    // 隐藏会让人以为本端不支持双页；置灰 + 说明才说明「是设备宽度不够」。
    // 已存 dual_page 的设备变窄时**不改写 prefs**：宽度恢复后自动回到双页。
    val widthDp = LocalConfiguration.current.screenWidthDp
    val dualPageAvailable = widthDp >= DUAL_PAGE_MIN_WIDTH_DP
    val options = ReadMethod.entries
    Column {
        Text("翻页方式", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val enabled = option != ReadMethod.DualPage || dualPageAvailable
                SegmentedButton(
                    selected = option == method,
                    onClick = { if (enabled) onSelect(option) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        if (!dualPageAvailable) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "双页翻页需要窗口宽度 ≥${DUAL_PAGE_MIN_WIDTH_DP}dp（当前 ${widthDp}dp）；" +
                    "已选双页时按左右翻页呈现。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MiscRow(
    spacingExpanded: Boolean,
    onToggleSpacing: () -> Unit,
    onOpenComment: () -> Unit,
    onOpenMore: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onToggleSpacing,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            Text(
                text = if (spacingExpanded) "收起间距" else "间距设置",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        OutlinedButton(
            onClick = onOpenComment,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            Text("评论设置", style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
            onClick = onOpenMore,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            Text("更多", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        StepButton(label = "—", onClick = onMinus)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.Center,
        )
        StepButton(label = "＋", onClick = onPlus)
    }
}

/**
 * 「自动阅读」配置组（P1-8）。
 *
 * 只配「怎么滚」，不配「要不要滚」：开关是底栏「自动翻页」那一个按钮
 * （`ReaderScreen.autoReading`），面板里再放一个开关就会出现两处状态各自为政。
 *
 * 两种滚动方式互斥显示各自的滑杆，而不是同时列出两条：
 * - 像素滚动：每帧步长 1..10 px（`scrollPixel`），速度随帧率，真机 60Hz ≈ 步长×60 px/s；
 * - 段落滚动：每段停留 100..5000 ms（`pageSpeed`），一整段跳到下一段。
 * 两者同时在屏上会让人以为「两个都要调」，且实际永远只有一个生效。
 *
 * 切换时各自的值不会丢：值都落在 `ReaderSettings` 里，本组只是把当前模式的
 * 那一条按 `committed` 重新初始化滑杆（见 [AutoSpeedSlider] 的 `remember` key）。
 */
@Composable
private fun AutoReadGroup(
    autoPageMode: String,
    scrollPixel: Int,
    pageSpeed: Int,
    autoReadAvailable: Boolean,
    onAutoPageModeChange: (String) -> Unit,
    onScrollPixelChange: (Int) -> Unit,
    onPageSpeedChange: (Int) -> Unit,
) {
    // 读盘时已被 AutoPageMode.normalize 归一过，这里只需要二分，不必再兜底第三种值
    val isParagraph = autoPageMode == AutoPageMode.PARAGRAPH
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("自动阅读", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(10.dp))
            FilterChip(
                selected = !isParagraph,
                onClick = { onAutoPageModeChange(AutoPageMode.PIXEL) },
                label = { Text("像素滚动") },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = isParagraph,
                onClick = { onAutoPageModeChange(AutoPageMode.PARAGRAPH) },
                label = { Text("段落滚动") },
            )
        }

        if (isParagraph) {
            AutoSpeedSlider(
                title = "每段停留",
                committed = pageSpeed,
                range = ReaderSettings.MIN_PAGE_SPEED..ReaderSettings.MAX_PAGE_SPEED,
                step = PAGE_SPEED_STEP,
                // 100ms 一档 = 48 个中间刻度，`steps=48` 会在轨道上画出一串点、手机屏上
                // 变成虚线段；这里用连续轨道 + 松手吸附到 100ms，取值与 steps=48 完全一致。
                discreteSteps = 0,
                format = { "${it}ms" },
                onCommit = onPageSpeedChange,
            )
        } else {
            AutoSpeedSlider(
                title = "每帧像素",
                committed = scrollPixel,
                range = ReaderSettings.MIN_SCROLL_PIXEL..ReaderSettings.MAX_SCROLL_PIXEL,
                step = 1,
                // 1px 一档 = 8 个中间刻度，点不多，用真实 steps 让拖动直接吸附到整数
                discreteSteps =
                    ReaderSettings.MAX_SCROLL_PIXEL - ReaderSettings.MIN_SCROLL_PIXEL - 1,
                format = { "${it}px" },
                onCommit = onScrollPixelChange,
            )
        }

        Text(
            text = "自动阅读仅在上下滑动下可用；手指拖动、切章、打开目录或面板会停止。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!autoReadAvailable) {
            Text(
                text = "当前翻页方式不是上下滑动，自动阅读不会启动；先切回「上下滑动」。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * 自动阅读的速度滑杆（两种模式共用）。
 *
 * 与字号/亮度同一套写盘策略：拖动中只改本地 `dragValue`（标签实时跟着变），
 * 松手 `onValueChangeFinished` 才落盘 —— 一次拖动只写一次 SharedPreferences。
 *
 * `remember(committed)`：外部值变了（重置、别处修改）就按新值重新初始化；
 * 只在拖动过程中由 `dragValue` 自己说了算，不会因为打字/落盘的回环把滑杆拽回去。
 */
@Composable
private fun AutoSpeedSlider(
    title: String,
    /** 已落盘的值。 */
    committed: Int,
    range: IntRange,
    /** 吸附步长：像素模式 1，段落模式 100ms。 */
    step: Int,
    /** 传给 M3 Slider 的中间刻度数；0 表示连续轨道（仍按 [step] 吸附）。 */
    discreteSteps: Int,
    format: (Int) -> String,
    onCommit: (Int) -> Unit,
) {
    var dragValue by remember(committed) { mutableFloatStateOf(committed.toFloat()) }
    val shown = snapToStep(dragValue.roundToInt(), range, step)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(
                text = format(shown),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = dragValue,
            onValueChange = { dragValue = it },
            // 从实时状态里再吸附一次，不依赖上一次组合时的 `shown`
            onValueChangeFinished = { onCommit(snapToStep(dragValue.roundToInt(), range, step)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = discreteSteps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 段落滚动速度的档位宽度（ms）。与前端「垂直速度」按 100 步进一致。 */
private const val PAGE_SPEED_STEP = 100

/** 把滑杆的连续取值吸附到 [step] 的整数倍并夹进区间。 */
private fun snapToStep(value: Int, range: IntRange, step: Int): Int {
    if (step <= 1) return value.coerceIn(range.first, range.last)
    return ((value.toFloat() / step).roundToInt() * step).coerceIn(range.first, range.last)
}

/** commonMain 没有 String.format（JVM 专属），这里自己保留一位小数。 */
private fun oneDecimal(value: Float): String {
    val scaled = (value * 10).roundToInt()
    return "${scaled / 10}.${scaled % 10}"
}
