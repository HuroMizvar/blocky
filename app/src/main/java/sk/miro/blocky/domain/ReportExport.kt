package sk.miro.blocky.domain

import sk.miro.blocky.domain.Xlsx.Cell
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Report výdavkov za zvolené obdobie do Excelu – 4 hárky:
 * Prehľad (súhrn podľa kategórií s rozpočtom), Položky, Bločky, Obchody.
 */
object ReportExport {

    /** Jedna položka bločku v období (už bez prečiarknutých položiek). */
    data class Line(val receiptKey: Long, val issuedAt: Long, val merchant: String, val name: String,
                    val quantity: Double, val cents: Long, val category: String)

    /** Platba fixného výdavku v období. */
    data class Fixed(val date: LocalDate, val name: String, val kind: String, val cents: Long)

    private val D = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun fileName(from: LocalDate, to: LocalDate) = "Blocky_report_${from}_${to}.xlsx"

    /**
     * @param budgets rozpočet kategórie za celé obdobie v centoch (už prepočítaný na dĺžku obdobia)
     * @param fixed platby fixných výdavkov; null = nezahrnúť
     * @param label názov kategórie podľa jej mena, [group] názov skupiny, [tr] preklad textov
     */
    fun build(lines: List<Line>, from: LocalDate, to: LocalDate, budgets: Map<String, Long>, fixed: List<Fixed>?,
              label: (String) -> String, group: (String) -> String, tr: (String) -> String = { it }): List<Xlsx.Sheet> {
        val total = lines.sumOf { it.cents }
        val receipts = lines.groupBy { it.receiptKey }
        val fixedTotal = fixed?.sumOf { it.cents } ?: 0L

        // ---- Prehľad ----
        val ov = mutableListOf<List<Cell>>()
        ov += listOf(Cell.t(tr("Bločky – report výdavkov"), bold = true))
        ov += listOf(Cell.t(tr("Obdobie")), Cell.t("${from.format(D)} – ${to.format(D)}"))
        ov += listOf(Cell.t(tr("Výdavky spolu")), Cell.money(total, bold = true))
        if (fixed != null) {
            ov += listOf(Cell.t(tr("Fixné výdavky")), Cell.money(fixedTotal))
            ov += listOf(Cell.t(tr("Spolu s fixnými výdavkami"), bold = true), Cell.money(total + fixedTotal, bold = true))
        }
        ov += listOf(Cell.t(tr("Počet bločkov")), Cell.int(receipts.size.toLong()))
        ov += emptyList<Cell>()
        val header = ov.size
        ov += listOf("Skupina", "Kategória", "Minuté", "Rozpočet", "Zostáva z rozpočtu", "Podiel na výdavkoch")
            .map { Cell.t(tr(it), bold = true) }
        val spent = lines.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.cents } }
        val cats = (spent.keys + budgets.keys).distinct()
            .sortedWith(compareByDescending<String> { spent[it] ?: 0L }.thenBy { label(it) })
        for (c in cats) {
            val s = spent[c] ?: 0L
            val b = budgets[c]
            ov += listOf(Cell.t(group(c)), Cell.t(label(c)), Cell.money(s),
                b?.let { Cell.money(it) } ?: Cell.EMPTY, b?.let { Cell.money(it - s) } ?: Cell.EMPTY,
                if (total > 0) Cell.pct(s.toDouble() / total) else Cell.EMPTY)
        }
        if (fixed != null && fixedTotal != 0L)
            ov += listOf(Cell.t(tr("Fixné výdavky")), Cell.t(tr("Fixné výdavky")), Cell.money(fixedTotal))
        val budgetSum = budgets.values.sum()
        ov += listOf(Cell.t(tr("Spolu"), bold = true), Cell.EMPTY, Cell.money(total + fixedTotal, bold = true),
            if (budgetSum > 0) Cell.money(budgetSum, bold = true) else Cell.EMPTY)

        // ---- Položky ----
        val itemRows = mutableListOf<List<Cell>>()
        itemRows += listOf("Dátum", "Obchod", "Položka", "Kategória", "Skupina", "Množstvo", "Cena")
            .map { Cell.t(tr(it), bold = true) }
        val rows = lines.map { l ->
            l.issuedAt to listOf(Cell.dateTime(l.issuedAt), Cell.t(l.merchant), Cell.t(l.name), Cell.t(label(l.category)),
                Cell.t(group(l.category)), Cell.num(l.quantity), Cell.money(l.cents))
        } + (fixed ?: emptyList()).map { f ->
            val ms = f.date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            ms to listOf(Cell.dateTime(ms), Cell.t(f.name), Cell.t(f.kind), Cell.t(tr("Fixné výdavky")),
                Cell.t(tr("Fixné výdavky")), Cell.num(1.0), Cell.money(f.cents))
        }
        rows.sortedBy { r -> r.first }.forEach { r -> itemRows += r.second }

        // ---- Bločky ----
        val rc = mutableListOf<List<Cell>>()
        rc += listOf("Dátum", "Obchod", "Počet položiek", "Suma").map { Cell.t(tr(it), bold = true) }
        receipts.values.sortedBy { l -> l.first().issuedAt }.forEach { l ->
            val f = l.first()
            rc += listOf(Cell.dateTime(f.issuedAt), Cell.t(f.merchant), Cell.int(l.size.toLong()), Cell.money(l.sumOf { x -> x.cents }))
        }

        // ---- Obchody ----
        val sh = mutableListOf<List<Cell>>()
        sh += listOf("Obchod", "Počet bločkov", "Suma", "Priemerný nákup", "Podiel na výdavkoch").map { Cell.t(tr(it), bold = true) }
        receipts.values.groupBy { l -> l.first().merchant }
            .map { (m, rs) -> Triple(m, rs.size, rs.sumOf { l -> l.sumOf { x -> x.cents } }) }
            .sortedByDescending { t -> t.third }
            .forEach { (m, n, s) ->
                sh += listOf(Cell.t(m), Cell.int(n.toLong()), Cell.money(s), Cell.money(if (n > 0) s / n else 0),
                    if (total > 0) Cell.pct(s.toDouble() / total) else Cell.EMPTY)
            }

        return listOf(
            Xlsx.Sheet(tr("Prehľad"), ov, listOf(26, 28, 14, 14, 18, 18), headerRow = header),
            Xlsx.Sheet(tr("Položky"), itemRows, listOf(17, 28, 34, 26, 24, 10, 12), headerRow = 0),
            Xlsx.Sheet(tr("Bločky"), rc, listOf(17, 34, 15, 12), headerRow = 0),
            Xlsx.Sheet(tr("Obchody"), sh, listOf(34, 14, 14, 16, 18), headerRow = 0)
        )
    }
}
