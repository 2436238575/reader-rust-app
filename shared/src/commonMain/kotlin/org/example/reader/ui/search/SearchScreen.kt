package org.example.reader.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.reader.core.AppDependencies
import org.example.reader.ui.components.BookResultGrid
import org.example.reader.ui.components.EmptyState
import org.example.reader.ui.components.ErrorState
import org.example.reader.ui.components.FullScreenLoading

/**
 * 多源搜索。
 *
 * 对应 Web 端 `SearchView` + `SearchResults`：范围筛选（启用源 >1 才显示）→
 * 流式结果增量渲染 → 去重 → 加入书架。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    deps: AppDependencies,
    contentPadding: PaddingValues,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SearchViewModel = viewModel { SearchViewModel(deps) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverConfig by deps.session.serverConfig.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("搜索") })

            OutlinedTextField(
                value = state.keyword,
                onValueChange = viewModel::onKeywordChange,
                placeholder = { Text("输入书名或作者") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.keyword.isNotEmpty()) {
                        IconButton(onClick = viewModel::clear) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.submit()
                        keyboard?.hide()
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            if (state.showFilters) {
                SearchScopeRow(
                    state = state,
                    onScopeChange = viewModel::setScope,
                    onGroupChange = viewModel::setGroup,
                    onSourceChange = viewModel::setSource,
                )
            }

            SearchStatusRow(
                state = state,
                onRetry = viewModel::retry,
            )

            when {
                state.failed && state.results.isEmpty() && !state.isSearching -> ErrorState(
                    message = state.errorMessage ?: "搜索失败或连接中断",
                    onRetry = viewModel::retry,
                )

                state.isSearching && state.results.isEmpty() -> FullScreenLoading(message = "正在搜索…")

                state.results.isEmpty() && state.submittedKey.isNotBlank() -> EmptyState("未找到相关书籍")

                state.results.isEmpty() -> EmptyState("输入书名或作者开始搜索")

                else -> BookResultGrid(
                    books = state.results,
                    shelfIds = state.shelfIds,
                    addingIds = state.addingIds,
                    serverConfig = serverConfig,
                    imageLoader = deps.imageLoader,
                    onOpenBook = onOpenBook,
                    onAddToShelf = viewModel::addToShelf,
                    originNames = state.originNames,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SearchScopeRow(
    state: SearchUiState,
    onScopeChange: (SearchScope) -> Unit,
    onGroupChange: (String) -> Unit,
    onSourceChange: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SearchScope.entries) { scope ->
                FilterChip(
                    selected = state.scope == scope,
                    onClick = { onScopeChange(scope) },
                    label = { Text(scope.label) },
                )
            }
        }
        when (state.scope) {
            SearchScope.Group -> PickerRow(
                label = state.group.ifBlank { "选择分组" },
                options = state.groups,
                optionLabel = { it },
                onSelect = onGroupChange,
            )

            SearchScope.Source -> PickerRow(
                label = state.enabledSources
                    .firstOrNull { it.bookSourceId == state.sourceId }
                    ?.bookSourceName
                    ?: "选择书源",
                options = state.enabledSources.mapNotNull { source ->
                    source.bookSourceId?.let { id -> id to source.bookSourceName }
                },
                optionLabel = { it.second },
                onSelect = { onSourceChange(it.first) },
            )

            SearchScope.All -> Unit
        }
    }
}

@Composable
private fun <T> PickerRow(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
        TextButton(onClick = { expanded = true }) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** 状态行：搜索中显示进度，结束显示总数；中断时给出错误与重试。 */
@Composable
private fun SearchStatusRow(
    state: SearchUiState,
    onRetry: () -> Unit,
) {
    val hasStatus = state.isSearching || state.submittedKey.isNotBlank()
    if (!hasStatus) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            state.isSearching -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "搜索中…（已找到 ${state.results.size} 本）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            state.failed -> {
                Text(
                    text = state.errorMessage ?: "搜索失败或连接中断",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text("重试") }
            }

            else -> Text(
                text = "共 ${state.results.size} 个结果",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
