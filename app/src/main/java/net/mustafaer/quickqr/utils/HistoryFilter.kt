package net.mustafaer.quickqr.utils

import net.mustafaer.quickqr.data.ScanEntity

/**
 * Filters scan history for the search box.
 *
 * This runs in Kotlin rather than as a SQL `LIKE`, for two reasons. SQLite's
 * `LIKE` only folds case for ASCII, so a `LIKE` search could never match
 * "İSTANBUL" against "istanbul" — or any Turkish, Arabic or Hindi text with
 * case. And `LIKE` treats `%` and `_` as wildcards, which has to be escaped with
 * an `ESCAPE` clause that is easy to forget. History is capped at
 * [net.mustafaer.quickqr.data.AppDatabase.HISTORY_LIMIT] rows, so filtering in
 * memory costs nothing and is correct in every language.
 */
object HistoryFilter {

    fun apply(scans: List<ScanEntity>, query: String): List<ScanEntity> {
        val needle = query.trim()
        if (needle.isEmpty()) return scans
        return scans.filter { scan ->
            scan.text.contains(needle, ignoreCase = true) ||
                scan.type.contains(needle, ignoreCase = true)
        }
    }
}
