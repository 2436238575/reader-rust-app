package org.example.reader.ui.nav

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.example.reader.core.AppDependencies
import org.example.reader.core.model.Book
import org.example.reader.ui.catalog.CatalogScreen
import org.example.reader.ui.components.EmptyState
import org.example.reader.ui.detail.BookDetailScreen
import org.example.reader.ui.explore.ExploreScreen
import org.example.reader.ui.reader.ReaderScreen
import org.example.reader.ui.recent.RecentScreen
import org.example.reader.ui.search.SearchScreen
import org.example.reader.ui.shelf.ShelfScreen

/** 目录页的目标：从详情或阅读器进入都只需要这三样。 */
private data class CatalogTarget(
    val bookId: String,
    val title: String,
    val currentIndex: Int?,
)

/** 底部导航项。书架 / 发现 / 搜索 / 最近 / 我的（搜索是独立入口，不挤在书架里）。 */
private enum class MainTab(val label: String, val icon: ImageVector) {
    Shelf("书架", Icons.AutoMirrored.Filled.List),
    Explore("发现", Icons.Filled.Star),
    Search("搜索", Icons.Filled.Search),
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
    // 从详情页目录进入时指定起始章；普通打开为 null（续读）
    var openChapterIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    // 详情页是独立页面，覆盖在 tab 之上；进程被杀后不恢复（可用快照无法序列化）
    var detailBook by remember { mutableStateOf<Book?>(null) }
    var detailSession by remember { mutableIntStateOf(0) }
    var catalogTarget by remember { mutableStateOf<CatalogTarget?>(null) }

    fun openReader(bookId: String, startIndex: Int? = null) {
        openBookId = bookId
        openChapterIndex = startIndex
    }

    fun showDetail(book: Book) {
        detailBook = book
        detailSession += 1
    }

    // 目录覆盖在阅读器/详情之上：从阅读器打开目录时不销毁阅读状态
    val catalog = catalogTarget
    if (catalog != null) {
        CatalogScreen(
            deps = deps,
            bookId = catalog.bookId,
            bookTitle = catalog.title,
            currentIndex = catalog.currentIndex,
            onBack = { catalogTarget = null },
            onSelectChapter = { index ->
                catalogTarget = null
                openReader(catalog.bookId, index)
            },
            modifier = modifier,
        )
        return
    }

    val bookId = openBookId
    if (bookId != null) {
        ReaderScreen(
            deps = deps,
            bookId = bookId,
            startIndex = openChapterIndex,
            onBack = {
                openBookId = null
                openChapterIndex = null
            },
            modifier = modifier,
        )
        return
    }

    val detail = detailBook
    if (detail != null) {
        BookDetailScreen(
            deps = deps,
            initialBook = detail,
            sessionKey = detailSession,
            onBack = { detailBook = null },
            onOpenCatalog = { book ->
                catalogTarget = CatalogTarget(
                    bookId = book.bookId,
                    title = book.name,
                    currentIndex = if (book.durChapterTitle.isNullOrBlank()) null else book.durChapterIndex,
                )
            },
            onRead = { id, index -> openReader(id, index) },
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
                    onShowDetail = ::showDetail,
                )
                MainTab.Explore -> ExploreScreen(
                    deps = deps,
                    contentPadding = padding,
                    onShowDetail = ::showDetail,
                )
                MainTab.Search -> SearchScreen(
                    deps = deps,
                    contentPadding = padding,
                    onShowDetail = ::showDetail,
                )
                MainTab.Recent -> RecentScreen(
                    deps = deps,
                    contentPadding = padding,
                    onOpenBook = { openBookId = it },
                    onShowDetail = ::showDetail,
                )
                MainTab.Profile -> EmptyState(
                    message = "设置页开发中",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
