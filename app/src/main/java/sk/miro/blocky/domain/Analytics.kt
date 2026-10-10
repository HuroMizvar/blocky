package sk.miro.blocky.domain

import sk.miro.blocky.data.ItemRow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToLong

data class CategoryStat(
    val category: Category,
    val cents: Long,
    val share: Double,
    val prevCents: Long,
    /** priemer predchádzajúcich (max. 3) mesiacov s dátami; null = bez histórie */
    val avg3Cents: Long?,
    val budgetCents: Long?
)

data class MerchantStat(val name: String, val cents: Long, val visits: Int)

data class ItemAgg(val name: String, val category: Category, val cents: Long, val quantity: Double, val purchases: Int)

data class MonthReport(
    val month: YearMonth,
    val totalCents: Long,
    val prevTotalCents: Long,
    val avg3TotalCents: Long?,
    val receiptCount: Int,
    val categories: List<CategoryStat>,
    val merchants: List<MerchantStat>,
    val topItems: List<ItemAgg>,
    /** projekcia do konca mesiaca – len pre bežiaci mesiac */
    val projectedTotalCents: Long?
)

enum class Severity { HIGH, MEDIUM, INFO }

data class Recommendation(
    val severity: Severity,
    val title: String,
    val detail: String,
    /** Odhad mesačnej úspory v centoch. Vždy ide o ODHAD – v UI sa tak aj označuje. */
    val estSavingCents: Long? = null
)

object Analytics {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun monthOf(epochMillis: Long): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(zone))

    private fun dateOf(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun availableMonths(rows: List<ItemRow>): List<YearMonth> =
        rows.map { monthOf(it.issuedAt) }.distinct().sortedDescending()

    private fun previousMonthsWithData(byMonth: Map<YearMonth, List<ItemRow>>, month: YearMonth, n: Int) =
        (1..24).map { month.minusMonths(it.toLong()) }.filter { byMonth.containsKey(it) }.take(n)

    fun report(
        rows: List<ItemRow>,
        month: YearMonth,
        budgets: Map<Category, Long>,
        today: LocalDate = LocalDate.now(zone)
    ): MonthReport {
        val byMonth = rows.groupBy { monthOf(it.issuedAt) }
        val cur = byMonth[month].orEmpty()
        val prev = byMonth[month.minusMonths(1)].orEmpty()
        val hist = previousMonthsWithData(byMonth, month, 3)

        val total = cur.sumOf { it.priceCents }
        val avg3Total = if (hist.isEmpty()) null else hist.sumOf { m -> byMonth[m]!!.sumOf { it.priceCents } } / hist.size

        val catsInPlay = (cur.map { it.category } + budgets.keys.map { it.name }).distinct()
        val categories = catsInPlay.map { cName ->
            val c = Category.fromName(cName)
            val cents = cur.filter { it.category == cName }.sumOf { it.priceCents }
            CategoryStat(
                category = c,
                cents = cents,
                share = if (total > 0) cents.toDouble() / total else 0.0,
                prevCents = prev.filter { it.category == cName }.sumOf { it.priceCents },
                avg3Cents = if (hist.isEmpty()) null else
                    hist.sumOf { m -> byMonth[m]!!.filter { it.category == cName }.sumOf { it.priceCents } } / hist.size,
                budgetCents = budgets[c]
            )
        }.sortedByDescending { it.cents }

        val merchants = cur.groupBy { it.merchantName }.map { (name, list) ->
            MerchantStat(name, list.sumOf { it.priceCents }, list.map { it.receiptDbId }.distinct().size)
        }.sortedByDescending { it.cents }

        val topItems = cur.filter { it.priceCents > 0 && it.category !in setOf(Category.ZALOHY.name, Category.ZLAVY.name) }
            .groupBy { it.normName }
            .map { (_, list) ->
                ItemAgg(list.first().name, Category.fromName(list.first().category),
                    list.sumOf { it.priceCents }, list.sumOf { it.quantity }, list.size)
            }.sortedByDescending { it.cents }.take(15)

        val projected = if (month == YearMonth.from(today) && today.dayOfMonth < month.lengthOfMonth() && total > 0)
            (total.toDouble() / today.dayOfMonth * month.lengthOfMonth()).roundToLong() else null

        return MonthReport(month, total, prev.sumOf { it.priceCents }, avg3Total,
            cur.map { it.receiptDbId }.distinct().size, categories, merchants, topItems, projected)
    }

    fun recommendations(
        rows: List<ItemRow>,
        report: MonthReport,
        today: LocalDate = LocalDate.now(zone)
    ): List<Recommendation> {
        val out = mutableListOf<Recommendation>()
        val month = report.month
        val cur = rows.filter { monthOf(it.issuedAt) == month }
        if (cur.isEmpty()) return emptyList()
        val skip = setOf(Category.ZALOHY, Category.ZLAVY)
        val isCurrent = month == YearMonth.from(today)

        // 1) Rozpočty
        for (c in report.categories) {
            val b = c.budgetCents ?: continue
            if (c.cents > b) {
                out += Recommendation(Severity.HIGH, "Prekročený rozpočet: ${c.category.label}",
                    "Minuté ${eur(c.cents)} pri rozpočte ${eur(b)} (+${eur(c.cents - b)}).", c.cents - b)
            } else if (isCurrent && report.projectedTotalCents != null && report.totalCents > 0) {
                val proj = (c.cents.toDouble() * report.projectedTotalCents / report.totalCents).roundToLong()
                if (proj > b) out += Recommendation(Severity.MEDIUM, "Hrozí prekročenie: ${c.category.label}",
                    "Pri súčasnom tempe skončíte na ~${eur(proj)} (rozpočet ${eur(b)}). Projekcia je lineárny odhad.")
            }
        }

        // 2) Nárast oproti 3-mesačnému priemeru
        for (c in report.categories) {
            if (c.category in skip) continue
            val avg = c.avg3Cents ?: continue
            val diff = c.cents - avg
            if (avg > 0 && diff >= 1000 && c.cents > avg * 1.25) {
                out += Recommendation(Severity.MEDIUM, "${c.category.label}: +${pct(diff.toDouble() / avg)} oproti priemeru",
                    "Tento mesiac ${eur(c.cents)}, priemer predchádzajúcich mesiacov ${eur(avg)}.", diff)
            }
        }

        // 3) Podiel neesenciálnych kategórií nad orientačným stropom
        if (report.totalCents >= 10000) for (c in report.categories) {
            val cap = c.category.benchmarkShare ?: continue
            if (c.share > cap) {
                val excess = c.cents - (cap * report.totalCents).roundToLong()
                out += Recommendation(Severity.MEDIUM, "${c.category.label} tvorí ${pct(c.share)} výdavkov",
                    "Orientačný strop ${pct(cap)} (heuristika aplikácie). Nad stropom: ${eur(excess)}.", excess)
            }
        }

        // 4) Rovnaký produkt lacnejšie v inom obchode (okno 90 dní)
        val windowStart = month.atEndOfMonth().minusDays(90)
        val window = rows.filter {
            val d = dateOf(it.issuedAt); d.isAfter(windowStart) && !d.isAfter(month.atEndOfMonth()) &&
                it.priceCents > 0 && it.quantity > 0 && Category.fromName(it.category) !in skip
        }
        data class Cmp(val name: String, val cheapMerchant: String, val cheapUnit: Double, val saving: Long, val worstMerchant: String, val worstUnit: Double)
        val cmps = window.groupBy { it.normName }.mapNotNull { (_, list) ->
            val perMerchant = list.groupBy { it.merchantName }
            if (perMerchant.size < 2) return@mapNotNull null
            val unit = perMerchant.mapValues { (_, l) -> l.sumOf { it.priceCents }.toDouble() / l.sumOf { it.quantity } }
            val (cheapM, cheapU) = unit.minBy { it.value }
            var saving = 0.0
            for ((m, u) in unit) if (m != cheapM && u > cheapU * 1.05) {
                val qtyPerMonth = perMerchant[m]!!.sumOf { it.quantity } / 3.0
                saving += (u - cheapU) * qtyPerMonth
            }
            val (worstM, worstU) = unit.maxBy { it.value }
            if (saving < 50) null else Cmp(list.last().name, cheapM, cheapU, saving.roundToLong(), worstM, worstU)
        }.sortedByDescending { it.saving }.take(5)
        for (c in cmps) out += Recommendation(Severity.INFO, "${c.name}: lacnejšie v ${c.cheapMerchant}",
            "${c.cheapMerchant} ${eur(c.cheapUnit.roundToLong())}/ks vs. ${c.worstMerchant} ${eur(c.worstUnit.roundToLong())}/ks. " +
                "Odhad podľa vašich nákupov za 90 dní.", c.saving)

        // 5) Zdraženie toho istého produktu v tom istom obchode (okno 180 dní)
        val since = month.atEndOfMonth().minusDays(180)
        val raises = rows.filter { dateOf(it.issuedAt).isAfter(since) && it.priceCents > 0 && it.quantity > 0 &&
                !dateOf(it.issuedAt).isAfter(month.atEndOfMonth()) }
            .groupBy { it.normName to it.merchantName }
            .mapNotNull { (key, l) ->
                val sorted = l.sortedBy { it.issuedAt }
                val first = sorted.first(); val last = sorted.last()
                if (monthOf(last.issuedAt) != month || first.issuedAt == last.issuedAt) return@mapNotNull null
                val u0 = first.priceCents / first.quantity; val u1 = last.priceCents / last.quantity
                if (u0 > 0 && u1 > u0 * 1.10 && u1 - u0 >= 10) Triple(last.name, key.second, u0 to u1) else null
            }.sortedByDescending { (it.third.second - it.third.first) / it.third.first }.take(5)
        for ((name, merchant, p) in raises) out += Recommendation(Severity.INFO, "Zdražené: $name ($merchant)",
            "${eur(p.first.roundToLong())} → ${eur(p.second.roundToLong())}/ks (+${pct((p.second - p.first) / p.first)}).")

        // 6) Veľa malých nákupov
        val receiptTotals = cur.groupBy { it.receiptDbId }.mapValues { (_, l) -> l.sumOf { it.priceCents } }
        val small = receiptTotals.values.filter { it in 1L until 500L }
        if (small.size >= 8) out += Recommendation(Severity.MEDIUM, "${small.size} drobných nákupov pod 5 €",
            "Spolu ${eur(small.sum())}. Drobné nákupy často znamenajú impulzívne výdavky – skúste ich nahradiť jedným plánovaným nákupom.")

        // 7) Opakované položky – kandidáti na väčšie balenie
        cur.filter { it.priceCents > 0 && Category.fromName(it.category) !in skip }
            .groupBy { it.normName }
            .filter { (_, l) -> l.size >= 4 && l.sumOf { it.priceCents } >= 1500 }
            .entries.sortedByDescending { e -> e.value.sumOf { it.priceCents } }.take(3)
            .forEach { (_, l) -> out += Recommendation(Severity.INFO, "Často kupované: ${l.last().name}",
                "${l.size}× tento mesiac za ${eur(l.sumOf { it.priceCents })}. Zvážte väčšie balenie alebo nákup v akcii.") }

        // 8) Zálohy
        val deposits = cur.filter { it.category == Category.ZALOHY.name }.sumOf { it.priceCents }
        if (deposits >= 100) out += Recommendation(Severity.INFO, "Zálohy na obaly: ${eur(deposits)}",
            "Vráťte fľaše a plechovky – tieto peniaze sa dajú získať späť.", deposits)

        // 9) Podiel neesenciálnych výdavkov celkovo
        val nonEss = report.categories.filter { !it.category.essential }.sumOf { it.cents }
        if (report.totalCents >= 10000 && nonEss.toDouble() / report.totalCents > 0.35)
            out += Recommendation(Severity.INFO, "Neesenciálne výdavky ${pct(nonEss.toDouble() / report.totalCents)}",
                "Alkohol, tabak, sladkosti, nápoje, hotové jedlá a reštaurácie spolu ${eur(nonEss)}.")

        return out.sortedWith(compareBy<Recommendation> { it.severity.ordinal }.thenByDescending { it.estSavingCents ?: 0L })
    }

    fun eur(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val a = abs(cents)
        return "$sign${a / 100},${(a % 100).toString().padStart(2, '0')} €"
    }

    fun pct(x: Double): String = "${(x * 100).roundToLong()} %"
}
