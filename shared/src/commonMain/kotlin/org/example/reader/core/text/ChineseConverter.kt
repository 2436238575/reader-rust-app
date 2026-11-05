package org.example.reader.core.text

/**
 * 简繁转换模式，取值与前端 `readerConfig.chineseMode` 一致（`simplified` / `traditional`）。
 *
 * 注意「简体」不是「不转换」：繁中书源在简体档位要显示简体，与前端 `t2cn` 的行为一致。
 */
enum class ChineseMode(val key: String, val label: String) {
    Simplified("simplified", "简体"),
    Traditional("traditional", "繁体"),
    ;

    companion object {
        /** 未知值（旧版本、手改 prefs）一律回落简体，不要留一个渲染层不认识的分支。 */
        fun fromKey(key: String?): ChineseMode =
            if (key == Traditional.key) Traditional else Simplified
    }
}

/**
 * 正文简繁转换的唯一入口。
 *
 * 实现约定：
 * - **失败必须返回原文**，不能抛到渲染层——阅读器不该因为词典异常变空白；
 * - 只做字符/词组替换，**不得改变换行数量**：配图的 `paraIndex` 是原始行号，
 *   行数一变图就插错位置；
 * - 只作用于显示，快照里的 `source` 始终保留原文（编辑、版本、diff 都用原文）。
 */
interface ChineseConverter {

    /** 按模式转换；[ChineseMode.Simplified] 会把繁体源转回简体。 */
    fun convert(text: String, mode: ChineseMode): String

    /**
     * 预热词典。
     *
     * 首次调用会加载 OpenCC 词表（桌面 JVM 约 0.4s，设备上更慢），
     * 必须在**后台线程**调用，否则会卡住首屏。
     */
    fun warmUp()
}
