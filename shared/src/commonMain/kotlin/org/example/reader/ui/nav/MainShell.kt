package org.example.reader.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.Book
import org.example.reader.core.util.AppBackHandler
import org.example.reader.ui.catalog.CatalogScreen
import org.example.reader.ui.components.EmptyState
import org.example.reader.ui.detail.BookDetailScreen
import org.example.reader.ui.explore.ExploreScreen
import org.example.reader.ui.reader.ReaderScreen
import org.example.reader.ui.recent.RecentScreen
import org.example.reader.ui.search.SearchScreen
import org.example.reader.ui.shelf.ShelfScreen

/** 底部导航项。书架 / 发现 / 搜索 / 最近 / 我的（搜索是独立入口，不挤在书架里）。 */
private enum class MainTab(val label: String, val icon: ImageVector) {
    Shelf("书架", Icons.AutoMirrored.Filled.List),
    Explore("发现", Icons.Filled.Star),
    Search("搜索", Icons.Filled.Search),
    Recent("最近", Icons.Filled.DateRange),
    Profile("我的", Icons.Filled.Person),
}

/**
 * 覆盖在 tab 之上的页面。用栈管理：阅读器、详情、目录可以互相叠，
 * 系统返回键逐层弹出，而不是直接回桌面。
 */
private sealed interface Overlay {
    data class Reader(val bookId: String, val startIndex: Int?) : Overlay

    data class Detail(val book: Book, val session: Int) : Overlay

    data class Catalog(
        val bookId: String,
        val title: String,
        val currentIndex: Int?,
    ) : Overlay
}

/**
 * 主框架。
 *
 * 底部导航 + 覆盖层栈。所有层级切换都走 [AnimatedContent]，
 * 系统返回由 [AppBackHandler] 统一接管：
 * 有覆盖层就弹一层，书架多选由 ShelfScreen 自己拦。
 */
@Composable
fun MainShell(deps: AppDependencies, modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val overlays = remember { mutableStateListOf<Overlay>() }
    var detailSession by remember { mutableIntStateOf(0) }

    fun popOverlay() {
        if (overlays.isNotEmpty()) overlays.removeAt(overlays.lastIndex)
    }

    fun openReader(bookId: String, startIndex: Int? = null) {
        overlays.add(Overlay.Reader(bookId, startIndex))
    }

    fun showDetail(book: Book) {
        detailSession += 1
        overlays.add(Overlay.Detail(book, detailSession))
    }

    fun openCatalog(book: Book) {
        overlays.add(
            Overlay.Catalog(
                bookId = book.bookId,
                title = book.name,
                currentIndex = if (book.durChapterTitle.isNullOrBlank()) null else book.durChapterIndex,
            ),
        )
    }

    AppBackHandler(enabled = overlays.isNotEmpty(), onBack = ::popOverlay)

    AnimatedContent(
        targetState = overlays.lastOrNull(),
        modifier = modifier.fillMaxSize(),
        label = "screen",
        transitionSpec = {
            if (targetState != null) {
                // 入栈：右侧滑入 + 淡入
                (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(200))) togetherWith
                    fadeOut(tween(120))
            } else {
                // 出栈：向右滑出 + 淡出
                fadeIn(tween(200)) togetherWith
                    (slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(160)))
            }
        },
    ) { screen ->
        when (screen) {
            null -> MainTabs(
                deps = deps,
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                onOpenBook = { openReader(it) },
                onShowDetail = ::showDetail,
            )

            is Overlay.Reader -> ReaderScreen(
                deps = deps,
                bookId = screen.bookId,
                startIndex = screen.startIndex,
                onBack = ::popOverlay,
                modifier = Modifier.fillMaxSize(),
            )

            is Overlay.Detail -> BookDetailScreen(
                deps = deps,
                initialBook = screen.book,
                sessionKey = screen.session,
                onBack = ::popOverlay,
                onOpenCatalog = ::openCatalog,
                onRead = { id, index -> openReader(id, index) },
                modifier = Modifier.fillMaxSize(),
            )

            is Overlay.Catalog -> CatalogScreen(
                deps = deps,
                bookId = screen.bookId,
                bookTitle = screen.title,
                currentIndex = screen.currentIndex,
                onBack = ::popOverlay,
                onSelectChapter = { index ->
                    popOverlay()
                    openReader(screen.bookId, index)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MainTabs(
    deps: AppDependencies,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onOpenBook: (String) -> Unit,
    onShowDetail: (Book) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { onSelectTab(index) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.fillMaxSize(),
                label = "tab",
                transitionSpec = {
                    val forward = targetState > initialState
                    (
                        slideInHorizontally(tween(240)) { if (forward) it / 10 else -it / 10 } +
                            fadeIn(tween(200))
                        ) togetherWith (
                        slideOutHorizontally(tween(180)) { if (forward) -it / 10 else it / 10 } +
                            fadeOut(tween(140))
                        )
                },
            ) { index ->
                when (MainTab.entries[index]) {
                    MainTab.Shelf -> ShelfScreen(
                        deps = deps,
                        contentPadding = padding,
                        onOpenBook = onOpenBook,
                        onShowDetail = onShowDetail,
                    )

                    MainTab.Explore -> ExploreScreen(
                        deps = deps,
                        contentPadding = padding,
                        onShowDetail = onShowDetail,
                    )

                    MainTab.Search -> SearchScreen(
                        deps = deps,
                        contentPadding = padding,
                        onShowDetail = onShowDetail,
                    )

                    MainTab.Recent -> RecentScreen(
                        deps = deps,
                        contentPadding = padding,
                        onOpenBook = onOpenBook,
                        onShowDetail = onShowDetail,
                    )

                    MainTab.Profile -> EmptyState(
                        message = "设置页开发中",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
