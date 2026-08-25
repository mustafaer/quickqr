package net.mustafaer.quickqr.utils

import net.mustafaer.quickqr.data.ScanEntity

/**
 * Turns scan history into CSV and JSON text.
 *
 * Kept free of Android APIs (no `org.json`, no `Context`) so both formats can be
 * verified by plain JVM unit tests — the escaping rules here are exactly the kind
 * of thing that breaks quietly on one unusual scan.
 */
object HistoryExporter {

    /** Characters that make a spreadsheet treat a cell as a formula. */
    private const val FORMULA_TRIGGERS = "=+-@\t\r"

    val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun toCsv(scans: List<ScanEntity>, formatDate: (Long) -> String): String =
        buildString {
            append("id,text,type,date\n")
            scans.forEach { scan ->
                append(scan.id).append(',')
                append(csvField(scan.text)).append(',')
                append(csvField(scan.type)).append(',')
                append(csvField(formatDate(scan.timestamp))).append('\n')
            }
        }

    fun toJson(scans: List<ScanEntity>): String =
        buildString {
            append("[\n")
            scans.forEachIndexed { index, scan ->
                append("  {\n")
                append("    \"id\": ").append(scan.id).append(",\n")
                append("    \"text\": ").append(jsonString(scan.text)).append(",\n")
                append("    \"type\": ").append(jsonString(scan.type)).append(",\n")
                append("    \"timestamp\": ").append(scan.timestamp).append('\n')
                append("  }")
                if (index != scans.lastIndex) append(',')
                append('\n')
            }
            append("]\n")
        }

    /**
     * Quotes a CSV field and neutralises spreadsheet formula injection.
     *
     * Scan content is entirely attacker-controlled — a QR code can carry
     * `=HYPERLINK("http://evil","Click")`, which Excel and Sheets will happily
     * execute when the export is opened. Prefixing an apostrophe forces the cell
     * to be read as text; the apostrophe itself is not displayed.
     */
    internal fun csvField(value: String): String {
        val guarded = if (value.isNotEmpty() && value[0] in FORMULA_TRIGGERS) "'$value" else value
        return "\"" + guarded.replace("\"", "\"\"") + "\""
    }

    internal fun jsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c == '\b' -> append("\\b")
                c == '\u000C' -> append("\\f")
                c < ' ' -> append("\\u" + c.code.toString(16).padStart(4, '0'))
                else -> append(c)
            }
        }
        append('"')
    }
}
