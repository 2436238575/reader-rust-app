package org.example.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.reader.core.highlight.KeywordHighlightRule

/**
 * 关键词高亮管理（P2-3）。
 *
 * 设置抽屉里的二级页：返回箭头回到「阅读设置」，系统返回键按 M3 sheet 的默认行为关闭整个面板。
 * 规则改动会写回 [KeywordHighlightStore]；阅读器收到 revision 后重建当前章，正文立即生效。
 */
@Composable
internal fun KeywordHighlightPanel(
    rules: List<KeywordHighlightRule>,
    bookId: String,
    bookTitle: String,
    onAdd: (keyword: String, color: Long, scope: String, bookId: String?) -> Unit,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onDelete: (id: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var keyword by remember { mutableStateOf("") }
    var color by remember { mutableLongStateOf(KeywordHighlightRule.DEFAULT_COLOR) }
    var onlyThisBook by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回阅读设置")
            }
            Column(Modifier.weight(1f)) {
                Text("关键词高亮", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "命中处显示底色，只影响阅读显示",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it.take(MAX_KEYWORD_LENGTH) },
            label = { Text("要标出的词") },
            placeholder = { Text("角色名、地名、口头禅…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("底色", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.size(8.dp))
            KeywordHighlightRule.COLORS.forEach { argb ->
                val selected = color == argb
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(28.dp)
                        .background(Color(argb), CircleShape)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        .clickable { color = argb },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = !onlyThisBook,
                onClick = { onlyThisBook = false },
                label = { Text("全部书籍", style = MaterialTheme.typography.labelSmall) },
            )
            Spacer(Modifier.size(6.dp))
            FilterChip(
                selected = onlyThisBook,
                onClick = { onlyThisBook = true },
                label = { Text("仅本书", style = MaterialTheme.typography.labelSmall) },
            )
        }
        Text(
            text = if (onlyThisBook) "只对《$bookTitle》生效" else "对所有书生效",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextButton(
            onClick = {
                if (keyword.isBlank()) return@TextButton
                onAdd(
                    keyword.trim(),
                    color,
                    if (onlyThisBook) KeywordHighlightRule.SCOPE_BOOK else KeywordHighlightRule.SCOPE_GLOBAL,
                    if (onlyThisBook) bookId else null,
                )
                keyword = ""
            },
            enabled = keyword.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("添加规则")
        }

        HorizontalDivider()

        if (rules.isEmpty()) {
            Text(
                text = "还没有规则。添加后会立刻在正文里标出。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            rules.forEach { rule ->
                KeywordRuleRow(
                    rule = rule,
                    onToggle = { onToggle(rule.id, it) },
                    onDelete = { onDelete(rule.id) },
                )
            }
        }
    }
}

@Composable
private fun KeywordRuleRow(
    rule: KeywordHighlightRule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(Color(rule.normalizedColor), CircleShape),
        )
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = rule.keyword,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (rule.isGlobal) "全部书籍" else "仅本书",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = rule.enabled, onCheckedChange = onToggle)
        TextButton(onClick = onDelete) { Text("删除") }
    }
}

private const val MAX_KEYWORD_LENGTH = 20
