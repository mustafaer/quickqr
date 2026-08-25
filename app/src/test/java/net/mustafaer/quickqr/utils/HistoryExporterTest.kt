package net.mustafaer.quickqr.utils

import net.mustafaer.quickqr.data.ScanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryExporterTest {

    private fun scan(id: Int, text: String, type: String = "text") =
        ScanEntity(id = id, text = text, type = type, timestamp = 1_700_000_000_000L)

    private val fixedDate: (Long) -> String = { "2023-11-14 22:13:20" }

    // --- CSV -----------------------------------------------------------------

    /**
     * Scan content is entirely attacker-controlled: a QR code can carry a
     * spreadsheet formula, which Excel and Sheets execute when the export is
     * opened. Every leading trigger character has to be neutralised.
     */
    @Test
    fun `formula injection is neutralised in csv`() {
        listOf("=HYPERLINK(\"http://evil\",\"Click\")", "+1+1", "-1+1", "@SUM(A1)").forEach { payload ->
            val field = HistoryExporter.csvField(payload)
            assertTrue("expected a leading apostrophe for $payload", field.startsWith("\"'"))
        }
    }

    @Test
    fun `ordinary content is not prefixed`() {
        assertEquals("\"https://example.com\"", HistoryExporter.csvField("https://example.com"))
        assertFalse(HistoryExporter.csvField("hello").contains("'"))
    }

    @Test
    fun `quotes inside a csv field are doubled`() {
        assertEquals("\"say \"\"hi\"\"\"", HistoryExporter.csvField("say \"hi\""))
    }

    @Test
    fun `csv output has a header and one row per scan`() {
        val csv = HistoryExporter.toCsv(
            listOf(scan(1, "first"), scan(2, "second")),
            fixedDate
        )
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertEquals("id,text,type,date", lines[0])
        assertEquals("1,\"first\",\"text\",\"2023-11-14 22:13:20\"", lines[1])
    }

    @Test
    fun `a newline inside a scan stays inside its quoted field`() {
        val csv = HistoryExporter.toCsv(listOf(scan(1, "line one\nline two")), fixedDate)
        assertTrue(csv.contains("\"line one\nline two\""))
    }

    // --- JSON ----------------------------------------------------------------

    @Test
    fun `json escapes quotes, backslashes and control characters`() {
        assertEquals("\"say \\\"hi\\\"\"", HistoryExporter.jsonString("say \"hi\""))
        assertEquals("\"C:\\\\path\"", HistoryExporter.jsonString("C:\\path"))
        assertEquals("\"a\\nb\"", HistoryExporter.jsonString("a\nb"))
        assertEquals("\"a\\tb\"", HistoryExporter.jsonString("a\tb"))
        assertEquals("\"\\u0001\"", HistoryExporter.jsonString("\u0001"))
    }

    @Test
    fun `json keeps non-ascii text as-is`() {
        assertEquals("\"Kadıköy\"", HistoryExporter.jsonString("Kadıköy"))
        assertEquals("\"مرحبا\"", HistoryExporter.jsonString("مرحبا"))
    }

    @Test
    fun `json output is a well formed array`() {
        val json = HistoryExporter.toJson(listOf(scan(1, "first"), scan(2, "second")))
        assertTrue(json.trimStart().startsWith("["))
        assertTrue(json.trimEnd().endsWith("]"))
        // Two objects, one separating comma.
        assertEquals(2, Regex("\"id\":").findAll(json).count())
        assertEquals(1, Regex("\\},").findAll(json).count())
    }

    @Test
    fun `an empty history produces an empty json array`() {
        assertEquals("[\n]\n", HistoryExporter.toJson(emptyList()))
    }
}
