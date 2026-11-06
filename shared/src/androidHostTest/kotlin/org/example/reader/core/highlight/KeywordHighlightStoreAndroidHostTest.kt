package org.example.reader.core.highlight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.example.reader.core.settings.KeyValueStore
import org.example.reader.core.settings.SettingsKeys

private class FakeStore : KeyValueStore {
    private val values = mutableMapOf<String, Any>()

    override fun getString(key: String, default: String?): String? = values[key] as? String ?: default
    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getLong(key: String, default: Long): Long = values[key] as? Long ?: default
    override fun putLong(key: String, value: Long) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }

    fun raw(key: String): String? = values[key] as? String
}

class KeywordHighlightStoreAndroidHostTest {

    @Test
    fun addTrimsKeywordAndPersistsAcrossReload() {
        val fake = FakeStore()
        val store = KeywordHighlightStore(fake)
        val rule = store.add("  苏桃  ", 0x807CC4FFL, KeywordHighlightRule.SCOPE_GLOBAL, null)

        assertNotNull(rule)
        assertEquals("苏桃", rule.keyword)
        assertTrue(fake.raw(SettingsKeys.READER_KEYWORD_HIGHLIGHTS)!!.contains("苏桃"))

        val reloaded = KeywordHighlightStore(fake).rules.value
        assertEquals(1, reloaded.size)
        assertEquals("苏桃", reloaded.single().keyword)
        assertEquals(0x807CC4FFL, reloaded.single().color)
    }

    @Test
    fun blankKeywordIsRejected() {
        val store = KeywordHighlightStore(FakeStore())
        assertNull(store.add("   ", KeywordHighlightRule.DEFAULT_COLOR, "global", null))
    }

    @Test
    fun bookScopedRuleOnlyAppliesToThatBook() {
        val store = KeywordHighlightStore(FakeStore())
        store.add("苏桃", KeywordHighlightRule.DEFAULT_COLOR, KeywordHighlightRule.SCOPE_GLOBAL, null)
        store.add("池小橙", KeywordHighlightRule.DEFAULT_COLOR, KeywordHighlightRule.SCOPE_BOOK, "book_1")

        assertEquals(2, store.rulesFor("book_1").size)
        assertEquals(listOf("苏桃"), store.rulesFor("book_2").map { it.keyword })
    }

    @Test
    fun toggleAndDeleteBumpRevisionAndPersist() {
        val fake = FakeStore()
        val store = KeywordHighlightStore(fake)
        val rule = store.add("苏桃", KeywordHighlightRule.DEFAULT_COLOR, "global", null)!!
        val afterAdd = store.revision.value

        store.setEnabled(rule.id, false)
        assertEquals(afterAdd + 1, store.revision.value)
        assertFalse(store.rules.value.single().enabled)
        assertEquals(0, store.rulesFor("book_1").size)

        store.delete(rule.id)
        assertTrue(store.rules.value.isEmpty())
        assertEquals(emptyList(), KeywordHighlightStore(fake).rules.value)
    }

    @Test
    fun corruptedPrefsAreDroppedWithoutClearingValidRules() {
        val fake = FakeStore()
        fake.putString(SettingsKeys.READER_KEYWORD_HIGHLIGHTS, "not json")
        assertEquals(emptyList(), KeywordHighlightStore(fake).rules.value)

        // 单条坏数据只丢那一条；alpha=0 的颜色回落默认色
        fake.putString(
            SettingsKeys.READER_KEYWORD_HIGHLIGHTS,
            """[{"id":"","keyword":"坏"},{"id":"ok","keyword":" 苏桃 ","color":0,"scope":"book","bookId":"book_1"}]""",
        )
        val rules = KeywordHighlightStore(fake).rules.value
        assertEquals(1, rules.size)
        assertEquals("苏桃", rules.single().keyword)
        assertEquals(KeywordHighlightRule.DEFAULT_COLOR, rules.single().color)
        assertEquals(KeywordHighlightRule.SCOPE_BOOK, rules.single().scope)
    }
}
