package org.example.reader.core.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 书架 / 发现 / 搜索共用的视图形态。列表信息密度更高，手机竖屏更常用，因此作为默认。 */
enum class ShelfLayout(val storageValue: String) {
    List("list"),
    Grid("grid"),
    ;

    companion object {
        fun fromStorage(value: String?): ShelfLayout =
            entries.firstOrNull { it.storageValue == value } ?: List
    }
}

/**
 * 全局视图形态偏好。
 *
 * 书架切换后，发现与搜索结果要立刻跟着变，所以状态放在这里由
 * `AppDependencies` 持有；各页面 collect 同一个 StateFlow，不要各存一份。
 */
class LayoutPreference(private val settings: KeyValueStore) {

    private val _layout = MutableStateFlow(
        ShelfLayout.fromStorage(settings.getString(SettingsKeys.SHELF_LAYOUT)),
    )
    val layout: StateFlow<ShelfLayout> = _layout.asStateFlow()

    fun toggle() {
        val next = when (_layout.value) {
            ShelfLayout.List -> ShelfLayout.Grid
            ShelfLayout.Grid -> ShelfLayout.List
        }
        settings.putString(SettingsKeys.SHELF_LAYOUT, next.storageValue)
        _layout.value = next
    }
}
