package sk.miro.blocky.domain

import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimálny zapisovač súborov Excel (.xlsx, Office Open XML) bez externých knižníc.
 * Podporuje viac hárkov, tučné písmo, formát eur / dátum / percentá, ukotvenú hlavičku a filtre.
 */
object Xlsx {

    enum class Kind { TEXT, MONEY, DATE_TIME, DATE, PERCENT, NUMBER, INT }

    data class Cell(val text: String? = null, val num: Double? = null, val kind: Kind = Kind.TEXT, val bold: Boolean = false) {
        companion object {
            fun t(s: String, bold: Boolean = false) = Cell(text = s, bold = bold)
            fun money(cents: Long, bold: Boolean = false) = Cell(num = cents / 100.0, kind = Kind.MONEY, bold = bold)
            fun dateTime(ms: Long) = Cell(num = serial(ms), kind = Kind.DATE_TIME)
            fun date(d: LocalDate) = Cell(num = ChronoUnit.DAYS.between(EPOCH, d).toDouble(), kind = Kind.DATE)
            fun pct(fraction: Double) = Cell(num = fraction, kind = Kind.PERCENT)
            fun num(v: Double) = Cell(num = v, kind = Kind.NUMBER)
            fun int(v: Long, bold: Boolean = false) = Cell(num = v.toDouble(), kind = Kind.INT, bold = bold)
            val EMPTY = Cell()
        }
    }

    /**
     * Hárok. [headerRow] = index riadku s hlavičkou tabuľky (ukotví sa a dostane filter), -1 = bez.
     * [widths] = šírky stĺpcov v znakoch.
     */
    data class Sheet(val name: String, val rows: List<List<Cell>>, val widths: List<Int>, val headerRow: Int = -1)

    private val EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    /** Dátum a čas ako číslo Excelu (dni od 30. 12. 1899, miestny čas). */
    fun serial(ms: Long): Double {
        val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val days = ChronoUnit.DAYS.between(EPOCH, t.toLocalDate()).toDouble()
        return days + t.toLocalTime().toSecondOfDay() / 86400.0
    }

    fun col(i: Int): String {   // 0 → A, 25 → Z, 26 → AA
        var n = i + 1; val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, ('A' + r)); n = (n - 1) / 26 }
        return sb.toString()
    }

    private fun esc(s: String) = buildString {
        for (ch in s) when {
            ch == '&' -> append("&amp;"); ch == '<' -> append("&lt;"); ch == '>' -> append("&gt;")
            ch == '"' -> append("&quot;")
            ch < ' ' && ch != '\t' && ch != '\n' && ch != '\r' -> Unit   // neplatné znaky v XML vynechať
            else -> append(ch)
        }
    }

    /** Index štýlu v styles.xml (cellXfs). */
    private fun style(c: Cell): Int = when (c.kind) {
        Kind.TEXT -> if (c.bold) 1 else 0
        Kind.MONEY -> if (c.bold) 3 else 2
        Kind.DATE_TIME -> 4
        Kind.DATE -> 5
        Kind.PERCENT -> 6
        Kind.NUMBER -> 7
        Kind.INT -> if (c.bold) 9 else 8
    }

    private fun safeName(n: String, used: MutableSet<String>): String {
        var s = n.replace(Regex("[\\\\/?*\\[\\]:]"), " ").trim().take(31).ifBlank { "Hárok" }
        var k = 2
        while (!used.add(s.lowercase())) { s = s.take(28) + " " + k++ }
        return s
    }

    fun write(sheets: List<Sheet>, out: OutputStream) {
        val used = mutableSetOf<String>()
        val names = sheets.map { safeName(it.name, used) }
        ZipOutputStream(out).use { z ->
            fun put(path: String, xml: String) {
                z.putNextEntry(ZipEntry(path)); z.write(xml.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            put("[Content_Types].xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                append("""<Default Extension="xml" ContentType="application/xml"/>""")
                append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
                append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
                sheets.indices.forEach {
                    append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
                }
                append("</Types>")
            })
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
                names.forEachIndexed { i, n -> append("""<sheet name="${esc(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
                append("</sheets>")
                // skrytá definícia filtra – Excel ju vyžaduje, aby filter fungoval spoľahlivo
                val dn = sheets.mapIndexedNotNull { i, s ->
                    if (s.headerRow < 0 || s.rows.size <= s.headerRow) null
                    else """<definedName name="_xlnm._FilterDatabase" localSheetId="$i" hidden="1">'${esc(names[i].replace("'", "''"))}'!${'$'}A${'$'}${s.headerRow + 1}:${'$'}${col(maxCols(s) - 1)}${'$'}${s.rows.size}</definedName>"""
                }
                if (dn.isNotEmpty()) append("<definedNames>").append(dn.joinToString("")).append("</definedNames>")
                append("</workbook>")
            })
            put("xl/_rels/workbook.xml.rels", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                sheets.indices.forEach {
                    append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""")
                }
                append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
                append("</Relationships>")
            })
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
    }

    private fun maxCols(s: Sheet) = maxOf(1, s.rows.maxOfOrNull { it.size } ?: 1, s.widths.size)

    private fun sheetXml(s: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        if (s.headerRow >= 0) {
            val top = s.headerRow + 2
            append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="${s.headerRow + 1}" topLeftCell="A$top" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        }
        if (s.widths.isNotEmpty()) {
            append("<cols>")
            s.widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
            append("</cols>")
        }
        append("<sheetData>")
        s.rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, cell ->
                val ref = col(c) + (r + 1)
                when {
                    cell.num != null && !cell.num.isNaN() && !cell.num.isInfinite() ->
                        append("""<c r="$ref" s="${style(cell)}"><v>${cell.num}</v></c>""")
                    cell.text != null ->
                        append("""<c r="$ref" s="${style(cell)}" t="inlineStr"><is><t xml:space="preserve">${esc(cell.text)}</t></is></c>""")
                    else -> Unit
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        if (s.headerRow >= 0 && s.rows.size > s.headerRow)
            append("""<autoFilter ref="A${s.headerRow + 1}:${col(maxCols(s) - 1)}${s.rows.size}"/>""")
        append("</worksheet>")
    }

    private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="4"><numFmt numFmtId="164" formatCode="#,##0.00\ &quot;€&quot;"/><numFmt numFmtId="165" formatCode="dd.mm.yyyy\ hh:mm"/><numFmt numFmtId="166" formatCode="dd.mm.yyyy"/><numFmt numFmtId="167" formatCode="0.###"/></numFmts>
<fonts count="2"><font><sz val="11"/><name val="Calibri"/><family val="2"/></font><font><b/><sz val="11"/><name val="Calibri"/><family val="2"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="10">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="164" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>
<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="10" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="167" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="1" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
}
