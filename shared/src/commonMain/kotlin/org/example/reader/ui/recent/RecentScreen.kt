package org.example.reader.ui.recent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.reader.core.AppDependencies
import org.example.reader.ui.components.BookListRow
import org.example.reader.ui.components.EmptyState

/**
 * 最近阅读。
 *
 * 对应 Web 端 `views/RecentView.vue`：本地最近记录（最多 100 条、按时间倒序）、
 * 关键词过滤、单条删除、一键清空。点击直接进阅读器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentScreen(
    deps: AppDependencies,
    contentPadding: PaddingValues,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RecentViewModel = viewModel { RecentViewModel(deps) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverConfig by deps.session.serverConfig.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }
    val visible = state.visible

    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = {
                Text(
                    text = if (visible.isEmpty()) "最近阅读" else "最近阅读（${visible.size}）",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            actions = {
                IconButton(onClick = viewModel::toggleFilter) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索最近阅读")
                }
                IconButton(
                    onClick = { showClearConfirm = true },
                    enabled = state.entries.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "清空最近阅读")
                }
            },
        )

        if (state.filterExpanded) {
            OutlinedTextField(
                value = state.keyword,
                onValueChange = viewModel::onKeywordChange,
                placeholder = { Text("搜索最近阅读") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        when {
            state.entries.isEmpty() -> EmptyState("暂无最近阅读\n去书架打开一本书吧")
            visible.isEmpty() -> EmptyState("没有匹配的记录")
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visible, key = { it.bookId }) { book ->
                    BookListRow(
                        book = book,
                        serverConfig = serverConfig,
                        imageLoader = deps.imageLoader,
                        onClick = { onOpenBook(book.bookId) },
                        trailing = {
                            IconButton(onClick = { viewModel.remove(book.bookId) }) {
                                Icon(Icons.Filled.Close, contentDescription = "删除记录")
                            }
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 88.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空最近阅读") },
            text = { Text("将删除全部 ${state.entries.size} 条本地记录，不影响书架里的书。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearConfirm = false
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }
}
