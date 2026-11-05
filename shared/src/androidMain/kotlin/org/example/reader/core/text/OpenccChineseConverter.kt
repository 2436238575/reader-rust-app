package org.example.reader.core.text

import com.github.houbb.opencc4j.util.ZhTwConverterUtil

/**
 * opencc4j 实现（简繁转换的唯一实现）。
 *
 * 选型：OpenCC 官方文档列出的 Java 移植（`com.github.houbb:opencc4j`，Apache-2.0，
 * 1.14.0 / 2025-09），纯 JVM 无 NDK，自带 OpenCC 词表与最大正向匹配分词，
 * 词组级转换不依赖 jieba（实测「头发→頭髮」「树干→樹幹」「软件→軟體」均正确）。
 *
 * 用 **Tw 变体**与前端 `opencc-js` 的 `from:'cn', to:'tw'` / `from:'tw', to:'cn'` 对齐：
 * 同一句话在 Web 与 Android 上看到的是同一套台湾正体词汇（軟體、滑鼠、矽二極體）。
 */
class OpenccChineseConverter : ChineseConverter {

    override fun convert(text: String, mode: ChineseMode): String {
        if (text.isEmpty()) return text
        return try {
            when (mode) {
                ChineseMode.Traditional -> ZhTwConverterUtil.toTraditional(text)
                ChineseMode.Simplified -> ZhTwConverterUtil.toSimple(text)
            }
        } catch (e: Exception) {
            // 词典加载失败或输入异常都不该让正文变空白：退回原文，把原因留在日志里
            println("OpenccChineseConverter: 转换失败 ${e::class.simpleName}: ${e.message}")
            text
        }
    }

    override fun warmUp() {
        // 第一次调用会加载并解析 OpenCC 词表（桌面 JVM 约 0.4s），在后台线程预热
        runCatching { ZhTwConverterUtil.toTraditional("汉") }
    }
}
