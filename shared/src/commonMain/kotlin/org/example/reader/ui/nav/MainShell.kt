package org.example.reader.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.example.reader.core.AppDependencies
import org.example.reader.ui.components.EmptyState
import org.example.reader.ui.reader.ReaderScreen
import org.example.reader.ui.shelf.ShelfScreen

/** 底部导航项。顺序与 Web 端顶栏一致：书架 / 发现 / 最近 / 我的。 */
private enum class MainTab(val label: String, val icon: ImageVector) {
    Shelf("书架", Icons.AutoMirrored.Filled.List),
    Explore("发现", Icons.Filled.Search),
    Recent("最近", Icons.Filled.DateRange),
    Profile("我的", Icons.Filled.Person),
}

/**
 * 主框架。
 *
 * 阅读器不做成底部导航的一个 tab，而是覆盖层：阅读时应当全屏无干扰。
 * 这里用一个 `openBookId` 状态控制——非空即全屏阅读器。
 */
@Composable
fun MainShell(deps: AppDependencies, modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var openBookId by rememberSaveable { mutableStateOf<String?>(null) }

    val bookId = openBookId
    if (bookId != null) {
        ReaderScreen(
            deps = deps,
            bookId = bookId,
            onBack = { openBookId = null },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when (MainTab.entries[selectedTab]) {
                MainTab.Shelf -> ShelfScreen(
                    deps = deps,
                    contentPadding = padding,
                    onOpenBook = { openBookId = it },
                )
                MainTab.Explore -> EmptyState(
                    message = "发现页开发中",
                    modifier = Modifier.fillMaxSize(),
                )
                MainTab.Recent -> EmptyState(
                    message = "最近阅读开发中",
                    modifier = Modifier.fillMaxSize(),
                )
                MainTab.Profile -> EmptyState(
                    message = "设置页开发中",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
