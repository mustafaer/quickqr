package net.mustafaer.quickqr.utils

import net.mustafaer.quickqr.data.ScanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryFilterTest {

    private fun scan(id: Int, text: String, type: String = "text") =
        ScanEntity(id = id, text = text, type = type, timestamp = id.toLong())

    private val history = listOf(
        scan(1, "https://example.com"),
        scan(2, "50% off today"),
        scan(3, "user_name"),
        scan(4, "back\\slash"),
        scan(5, "İSTANBUL Kadıköy"),
        scan(6, "Grüße aus München"),
        scan(7, "مرحبا بالعالم"),
        scan(8, "नमस्ते दुनिया"),
        scan(9, "WIFI:S:Cafe;;", type = "wifi")
    )

    @Test
    fun `an empty query returns everything untouched`() {
        assertEquals(history, HistoryFilter.apply(history, ""))
        assertEquals(history, HistoryFilter.apply(history, "   "))
    }

    /**
     * Regression: the search used to run as a SQL `LIKE`, where `%` and `_` are
     * wildcards. Escaping them in Kotlin without an `ESCAPE` clause in the query
     * made every search containing one return zero rows.
     */
    @Test
    fun `sql wildcard characters are matched literally`() {
        assertEquals(listOf(history[1]), HistoryFilter.apply(history, "50%"))
        assertEquals(listOf(history[2]), HistoryFilter.apply(history, "user_"))
        assertEquals(listOf(history[3]), HistoryFilter.apply(history, "back\\"))
    }

    @Test
    fun `a percent sign does not behave as a wildcard`() {
        // If `%` were still a wildcard this would match every row.
        assertEquals(1, HistoryFilter.apply(history, "%").size)
    }

    /**
     * SQLite's `LIKE` only folds case for ASCII, so it could never have matched
     * these. Filtering in Kotlin makes search work in every shipped language.
     */
    @Test
    fun `matching is case-insensitive beyond ascii`() {
        assertTrue(HistoryFilter.apply(history, "istanbul").contains(history[4]))
        assertTrue(HistoryFilter.apply(history, "kadıköy").contains(history[4]))
        assertTrue(HistoryFilter.apply(history, "münchen").contains(history[5]))
        assertTrue(HistoryFilter.apply(history, "MÜNCHEN").contains(history[5]))
        assertTrue(HistoryFilter.apply(history, "grüße").contains(history[5]))
    }

    @Test
    fun `arabic and hindi text is searchable`() {
        assertEquals(listOf(history[6]), HistoryFilter.apply(history, "بالعالم"))
        assertEquals(listOf(history[7]), HistoryFilter.apply(history, "दुनिया"))
    }

    @Test
    fun `the scan type is searchable too`() {
        assertEquals(listOf(history[8]), HistoryFilter.apply(history, "wifi"))
    }

    @Test
    fun `surrounding whitespace in the query is ignored`() {
        assertEquals(HistoryFilter.apply(history, "example"), HistoryFilter.apply(history, "  example  "))
    }

    @Test
    fun `a query that matches nothing returns an empty list`() {
        assertTrue(HistoryFilter.apply(history, "no such content").isEmpty())
    }
}
