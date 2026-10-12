package sk.miro.blocky.domain

import sk.miro.blocky.data.ItemRow
import java.time.LocalDate
import java.time.YearMonth

/**
 * Súhrn výdavkov pre AI analýzu.
 *
 * Aplikácia NIČ neodosiela sama: text sa zobrazí používateľovi na kontrolu a ten ho
 * sám skopíruje / zdieľa do AI aplikácie podľa vlastného výberu. Neobsahuje adresy,
 * IČO, ID bločkov ani presné časy nákupov – len súčty po kategóriách a mesiacoch.
 */
data class AiExportOptions(
    val months: Int = 3,
    val includeMerchants: Boolean = true,
    val includeItems: Boolean = true
)

object AiExport {

    private fun ym(m: YearMonth) = "%02d/%d".format(m.monthValue, m.year)

    fun build(
        rows: List<ItemRow>,
        budgets: Map<Category, Long>,
        profile: HouseholdProfile,
        tips: List<Recommendation>,
        options: AiExportOptions = AiExportOptions(),
        today: LocalDate = LocalDate.now()
    ): String {
        val nowYm = YearMonth.from(today)
        val months = Analytics.availableMonths(rows).filter { !it.isAfter(nowYm) }.take(options.months).sorted()
        val sb = StringBuilder()

        sb.appendLine("Si môj osobný finančný poradca. Nižšie sú moje výdavky z aplikácie na skenovanie bločkov.")
        sb.appendLine("Odpovedz po slovensky, stručne a konkrétne:")
        sb.appendLine("1. Zhrnutie: kam idú moje peniaze a čo sa mení medzi mesiacmi.")
        sb.appendLine("2. 5 konkrétnych odporúčaní, kde a ako ušetriť, s odhadom sumy za mesiac (jasne označ, že ide o odhad).")
        sb.appendLine("3. Čo vyzerá v poriadku.")
        sb.appendLine("4. Až 3 otázky, ktorých odpovede by ti pomohli poradiť presnejšie.")
        sb.appendLine("Vychádzaj len z týchto údajov, nevymýšľaj čísla. Sumy sú v eurách.")
        sb.appendLine()

        sb.appendLine("## Domácnosť")
        if (profile.filled) sb.appendLine("- ${profile.summary()}") else sb.appendLine("- neuvedené")
        if (profile.note.isNotBlank()) sb.appendLine("- O mne: ${profile.note.trim()}")
        sb.appendLine()

        if (months.isEmpty()) {
            sb.appendLine("(Zatiaľ žiadne bločky.)")
            return sb.toString().trim()
        }

        val reports = months.associateWith { Analytics.report(rows, it, budgets, today) }
        val partial = months.last() == nowYm && today.dayOfMonth < nowYm.lengthOfMonth()

        sb.appendLine("## Výdavky podľa kategórií (bez vratných záloh)")
        val header = months.joinToString(" | ") { ym(it) + if (it == nowYm && partial) "*" else "" }
        sb.appendLine("| Kategória | $header | Mesačný rozpočet |")
        sb.appendLine("|---|" + months.joinToString("") { "---:|" } + "---:|")
        val cats = reports.values.flatMap { r -> r.categories.filter { it.cents != 0L }.map { it.category } }
            .distinct()
            .sortedByDescending { c -> reports.values.sumOf { r -> r.categories.firstOrNull { it.category == c }?.cents ?: 0L } }
        for (c in cats) {
            val vals = months.joinToString(" | ") { m -> money(reports[m]!!.categories.firstOrNull { it.category == c }?.cents ?: 0L) }
            sb.appendLine("| ${c.label} | $vals | ${budgets[c]?.let { money(it) } ?: "–"} |")
        }
        sb.appendLine("| **Spolu** | " + months.joinToString(" | ") { "**${money(reports[it]!!.totalCents)}**" } + " | " +
            (budgets.values.sum().takeIf { it > 0 }?.let { money(it) } ?: "–") + " |")
        sb.appendLine("| Počet bločkov | " + months.joinToString(" | ") { "${reports[it]!!.receiptCount}" } + " | |")
        if (partial) sb.appendLine("\\* bežiaci mesiac, údaje k ${today.dayOfMonth}. ${today.monthValue}.")
        val dep = reports.values.sumOf { it.depositPaidCents - it.depositReturnedCents }
        if (dep > 0) sb.appendLine("Nevrátené zálohy za obaly za obdobie: ${money(dep)}.")
        sb.appendLine()

        val period = rows.filter { YearMonth.from(java.time.Instant.ofEpochMilli(it.issuedAt)
            .atZone(java.time.ZoneId.systemDefault())) in months && it.category != Category.ZALOHY.name }

        if (options.includeMerchants) {
            sb.appendLine("## Obchody a prevádzky (spolu za obdobie)")
            sb.appendLine("| Prevádzka | Typ | Nákupy | Spolu |")
            sb.appendLine("|---|---|---:|---:|")
            period.groupBy { it.merchantName }.map { (name, l) ->
                Triple(name, l, l.sumOf { it.priceCents })
            }.sortedByDescending { it.third }.take(12).forEach { (name, l, sum) ->
                sb.appendLine("| ${clean(name)} | ${MerchantType.fromName(l.first().merchantType).label} | " +
                    "${l.map { it.receiptDbId }.distinct().size} | ${money(sum)} |")
            }
            sb.appendLine()
        }

        if (options.includeItems) {
            sb.appendLine("## Najčastejšie a najdrahšie položky (spolu za obdobie)")
            sb.appendLine("| Položka | Kategória | Nákupy | Spolu | Priem. cena/ks |")
            sb.appendLine("|---|---|---:|---:|---:|")
            period.filter { it.priceCents > 0 && it.category != Category.ZLAVY.name }
                .groupBy { it.normName }
                .map { (_, l) -> l }
                .sortedByDescending { l -> l.sumOf { it.priceCents } }
                .take(25)
                .forEach { l ->
                    val qty = l.sumOf { it.quantity }.takeIf { it > 0 } ?: 1.0
                    sb.appendLine("| ${clean(l.last().name)} | ${Category.fromName(l.first().category).label} | ${l.size} | " +
                        "${money(l.sumOf { it.priceCents })} | ${money(Math.round(l.sumOf { it.priceCents } / qty))} |")
                }
            sb.appendLine()
        }

        if (tips.isNotEmpty()) {
            sb.appendLine("## Čo už našla aplikácia (pravidlá, nie AI)")
            tips.take(8).forEach { sb.appendLine("- ${it.title}: ${it.detail}") }
        }
        return sb.toString().trim()
    }

    private fun money(c: Long) = Analytics.eur(c)
    private fun clean(s: String) = s.replace("|", "/").replace("\n", " ").trim()
}
