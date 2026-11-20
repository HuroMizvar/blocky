package sk.miro.blocky

import org.junit.Assert.*
import org.junit.Test
import sk.miro.blocky.domain.ReportExport
import sk.miro.blocky.domain.Xlsx
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipInputStream

class ReportExportTest {
    private fun ms(d: LocalDate, h: Int = 12) = d.atTime(h, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val from = LocalDate.of(2026, 10, 10)
    private val to = LocalDate.of(2026, 11, 9)
    private val lines = listOf(
        ReportExport.Line(1, ms(from), "Lidl", "Mlieko", 2.0, 178, "MLIECNE"),
        ReportExport.Line(1, ms(from), "Lidl", "Chlieb <&> \"x\"", 1.0, 129, "PECIVO"),
        ReportExport.Line(2, ms(to), "Kaufland", "Syr", 1.0, 350, "MLIECNE"))

    private fun sheets(fixed: List<ReportExport.Fixed>?) = ReportExport.build(lines, from, to, mapOf("MLIECNE" to 1000L), fixed,
        label = { it.lowercase() }, group = { "Potraviny" })

    @Test fun summaryAndSheets() {
        val s = sheets(listOf(ReportExport.Fixed(LocalDate.of(2026, 10, 15), "Hypotéka", "Hypotéka", 50_000)))
        assertEquals(listOf("Prehľad", "Položky", "Bločky", "Obchody"), s.map { it.name })
        val ov = s[0].rows
        assertEquals(6.57, ov[2][1].num!!, 1e-9)                // výdavky spolu
        assertEquals(506.57, ov[4][1].num!!, 1e-9)              // s fixnými
        assertEquals(1 + 3 + 1, s[1].rows.size)                  // hlavička + 3 položky + 1 fixná platba
    }

    @Test fun withoutFixed() {
        val s = sheets(null)
        assertFalse(s[0].rows.any { r -> r.any { it.text == "Fixné výdavky" } })
        assertEquals(1 + 3, s[1].rows.size)
        assertEquals(1 + 2, s[2].rows.size)
        assertEquals("Kaufland", s[3].rows[1][0].text)          // najviac minuté
    }

    @Test fun writesValidZip() {
        val out = ByteArrayOutputStream()
        Xlsx.write(sheets(listOf(ReportExport.Fixed(LocalDate.of(2026, 10, 15), "Hypotéka", "Hypotéka", 50_000))), out)
        val names = mutableListOf<String>()
        ZipInputStream(out.toByteArray().inputStream()).use { z -> while (true) { val e = z.nextEntry ?: break; names += e.name } }
        assertTrue(names.containsAll(listOf("[Content_Types].xml", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet4.xml")))
        System.getProperty("xlsxOut")?.let { File(it).writeBytes(out.toByteArray()) }
        assertEquals("AA", Xlsx.col(26))
    }
}
