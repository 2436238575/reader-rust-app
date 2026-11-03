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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * 未接入的入口（系统字体、评论设置、更多、自动阅读）不隐藏，
 * 点按给出明确提示，避免用户以为是坏的。
 */
@Composable
fun ReaderSettingsPanel(
    settings: ReaderSettings,
    onFontSizeChange: (Int) -> Unit,
    onThemeChange: (Int) -> Unit,
    onMethodChange: (ReadMethod) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onEyeCareChange: (Boolean) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    onReset: () -> Unit,
    onHint: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var spacingExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
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
            onBrightnessChange = onBrightnessChange,
            onEyeCareChange = onEyeCareChange,
        )

        FontSizeRow(
            fontSize = settings.fontSize,
            onFontSizeChange = onFontSizeChange,
            onOpenFontFamily = { onHint("字体选择将在 P1-1 后续接入") },
        )

        BackgroundRow(selected = settings.themeIndex, onSelect = onThemeChange)

        PageMethodRow(
            method = settings.method,
            onSelect = { method ->
                onMethodChange(method)
                if (method != ReadMethod.SlideUp) {
                    onHint("「${method.label}」将在 P1-2 接入，当前仍为上下滑动")
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

        TextButton(
            onClick = { onHint("自动阅读将在 P1-8 接入") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("开启自动阅读", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun BrightnessRow(
    brightness: Float,
    eyeCare: Boolean,
    onBrightnessChange: (Float) -> Unit,
    onEyeCareChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("亮度", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = brightness,
            onValueChange = onBrightnessChange,
            valueRange = ReaderSettings.MIN_BRIGHTNESS..1f,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        FilterChip(
            selected = eyeCare,
            onClick = { onEyeCareChange(!eyeCare) },
            label = { Text("护眼") },
        )
    }
}

@Composable
private fun FontSizeRow(
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    onOpenFontFamily: () -> Unit,
) {
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

@Composable
private fun PageMethodRow(method: ReadMethod, onSelect: (ReadMethod) -> Unit) {
    // 双页翻页与前端一致：窄屏不可用（前端在 <960px 时禁用），手机端不列出
    val options = ReadMethod.entries.filter { it != ReadMethod.DualPage }
    Column {
        Text("翻页方式", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == method,
                    onClick = { onSelect(option) },
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

/** commonMain 没有 String.format（JVM 专属），这里自己保留一位小数。 */
private fun oneDecimal(value: Float): String {
    val scaled = (value * 10).roundToInt()
    return "${scaled / 10}.${scaled % 10}"
}
