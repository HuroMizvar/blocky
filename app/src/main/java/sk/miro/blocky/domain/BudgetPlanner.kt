package sk.miro.blocky.domain

import sk.miro.blocky.data.ItemRow
import java.time.LocalDate
import java.time.YearMonth

/**
 * Automatické rozpočty podľa celej histórie míňania.
 *
 * Pre každú kategóriu sa zoberú všetky ukončené mesiace (od prvého nákupu v kategórii, mesiace bez nákupu = 0 €).
 * Základ je medián mesačných výdavkov (odolný voči jednorazovým výkyvom), rozpočet je o niečo nižší,
 * aby motivoval šetriť – viac pri „zbytných“ výdavkoch, menej pri nevyhnutných:
 *
 * | typ | zníženie |
 * |---|---|
 * | sladkosti, alkohol, tabak, reštaurácie, nápoje, hotové jedlá | −15 % |
 * | ostatné nie nevyhnutné | −10 % |
 * | nevyhnutné (potraviny, drogéria, lieky, PHM…) | −5 % |
 *
 * Ak ste posledné 3 mesiace míňali menej ako zvyčajne, základom je toto nižšie míňanie – úspech sa nestratí.
 */
object BudgetPlanner {

    data class Suggestion(
        val category: Category,
        /** priemer mesačných výdavkov */
        val averageCents: Long,
        /** typický mesiac (medián) */
        val typicalCents: Long,
        val budgetCents: Long,
        /** z koľkých mesiacov sa počítalo (0 = odhad z aktuálneho mesiaca) */
        val months: Int,
        /** z koľkých dní sa počítalo pri výbere obdobia (0 = podľa mesiacov) */
        val days: Int = 0
    ) {
        val savingCents: Long get() = (averageCents - budgetCents).coerceAtLeast(0)
    }

    private val VICES = setOf(Category.SLADKOSTI, Category.ALKOHOL, Category.TABAK, Category.RESTAURACIE,
        Category.NAPOJE, Category.MRAZENE_HOTOVE)
    private val SKIP = setOf(Category.ZALOHY, Category.ZLAVY)
    private const val MIN_BASE = 300L   // pod 3 € mesačne rozpočet nemá zmysel

    fun factor(c: Category): Double = when {
        c in VICES -> 0.85
        c.essential -> 0.95
        else -> 0.90
    }

    /** Zaokrúhlenie nadol na „pekné“ sumy: do 100 € na celé eurá, do 300 € na 5 €, inak na 10 €. */
    fun roundDown(cents: Long): Long {
        val step = when {
            cents < 10_000 -> 100L
            cents < 30_000 -> 500L
            else -> 1_000L
        }
        return cents / step * step
    }

    fun median(v: List<Long>): Long {
        if (v.isEmpty()) return 0
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** priemerná dĺžka mesiaca pri prepočte z dní */
    const val DAYS_PER_MONTH = 30

    private fun dateOf(ms: Long): LocalDate = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

    /** dátum prvého výdavku v aplikácii (pre voľbu „celá história“) */
    fun firstDate(rows: List<ItemRow>): LocalDate? = rows.minOfOrNull { it.issuedAt }?.let { dateOf(it) }

    /**
     * Rozpočty z vybraného obdobia [from]–[to] (vrátane): výdavky kategórie / počet dní × 30 = mesačné míňanie,
     * z neho sa rovnako ako pri [plan] odvodí o niečo nižší rozpočet.
     */
    fun planRange(rows: List<ItemRow>, from: LocalDate, to: LocalDate): List<Suggestion> {
        if (to.isBefore(from)) return emptyList()
        val days = java.time.temporal.ChronoUnit.DAYS.between(from, to).toInt() + 1
        return rows.filter { val d = dateOf(it.issuedAt); !d.isBefore(from) && !d.isAfter(to) }
            .groupBy { Category.fromName(it.category) }
            .mapNotNull { (cat, list) ->
                if (cat in SKIP || !cat.active) return@mapNotNull null
                val monthly = list.sumOf { it.priceCents } * DAYS_PER_MONTH / days
                if (monthly < MIN_BASE) return@mapNotNull null
                val budget = roundDown((monthly * factor(cat)).toLong())
                if (budget <= 0) null else Suggestion(cat, monthly, monthly, budget, 0, days)
            }
            .sortedByDescending { it.averageCents }
    }

    fun plan(rows: List<ItemRow>, today: LocalDate = LocalDate.now()): List<Suggestion> {
        val current = Period.of(today)
        val byCat = rows.groupBy { Category.fromName(it.category) }
        val out = mutableListOf<Suggestion>()
        for ((cat, list) in byCat) {
            if (cat in SKIP || !cat.active) continue
            val perMonth = list.groupBy { Analytics.monthOf(it.issuedAt) }.mapValues { (_, r) -> r.sumOf { it.priceCents } }
            val first = perMonth.keys.minOrNull() ?: continue
            val complete = generateSequence(first) { it.plusMonths(1) }.takeWhile { it < current }
                .map { perMonth[it] ?: 0L }.toList()
            val s = if (complete.isNotEmpty()) suggest(cat, complete)
                else {
                    // ešte žiadny celý mesiac – odhad z doterajška (aspoň po týždni)
                    val day = Period.dayIndex(current, today)
                    if (day < 7) continue
                    val soFar = perMonth[current] ?: continue
                    val projected = soFar * Period.length(current) / day
                    suggest(cat, listOf(projected))?.copy(months = 0)
                }
            if (s != null) out += s
        }
        return out.sortedByDescending { it.averageCents }
    }

    private fun suggest(cat: Category, months: List<Long>): Suggestion? {
        val typical = median(months)
        val recent = if (months.size >= 4) median(months.takeLast(3)) else typical
        val base = minOf(typical, recent)
        if (base < MIN_BASE) return null
        val avg = months.sum() / months.size
        val budget = roundDown((base * factor(cat)).toLong())
        if (budget <= 0) return null
        return Suggestion(cat, avg, typical, budget, months.size)
    }
}
