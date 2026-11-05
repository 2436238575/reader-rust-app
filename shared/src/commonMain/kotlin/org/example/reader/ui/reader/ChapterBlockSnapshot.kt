package org.example.reader.ui.reader

import org.example.reader.core.model.ChapterBlock
import org.example.reader.core.model.ChapterContent

/**
 * 一章正文的原子快照。
 *
 * 完整 [source] 与 [chapterIndex]、[blocks] 同批替换；版本归属和内容身份不只依赖可空 versionId。
 * [revision] 标识正文应用代际：即使刷新返回完全相同的正文，恢复身份也必须变化。
 */
data class ChapterBlockSnapshot(
    val chapterIndex: Int,
    val source: ChapterContent,
    val blocks: List<ChapterBlock>,
    val revision: Int = 0,
)
