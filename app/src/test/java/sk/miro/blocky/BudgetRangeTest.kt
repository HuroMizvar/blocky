package sk.miro.blocky

import org.junit.Assert.*
import org.junit.Test
import sk.miro.blocky.data.ItemRow
import sk.miro.blocky.domain.BudgetPlanner
import sk.miro.blocky.domain.Category
import java.time.LocalDate
import java.time.ZoneId

class BudgetRangeTest {
    private var id = 0L
    private fun row(cat: Category, cents: Long, date: LocalDate) = ItemRow(++id, id, "x", "x", 1.0, cents, cat.name, "Obchod",
        date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

    @Test fun rangeIsScaledToThirtyDays() {
        val from = LocalDate.of(2026, 10, 1)
        // 15 dní, mliečne 30 € → 60 € mesačne → −5 % = 57 €
        val rows = listOf(row(Category.MLIECNE, 1_500, from), row(Category.MLIECNE, 1_500, from.plusDays(14)),
            row(Category.MLIECNE, 9_999, from.plusDays(15)))   // mimo obdobia
        val s = BudgetPlanner.planRange(rows, from, from.plusDays(14)).single()
        assertEquals(15, s.days)
        assertEquals(6_000, s.averageCents)
        assertEquals(5_700, s.budgetCents)
        // 35 dní, 70 € → 60 € mesačne
        val s2 = BudgetPlanner.planRange(listOf(row(Category.MLIECNE, 7_000, from)), from, from.plusDays(34)).single()
        assertEquals(6_000, s2.averageCents)
        assertEquals(from, BudgetPlanner.firstDate(rows))
        assertTrue(BudgetPlanner.planRange(rows, from, from.minusDays(1)).isEmpty())
    }

    @Test fun compareOnlyToSamePartOfPreviousMonth() {
        sk.miro.blocky.domain.Period.payDay = 1
        val today = LocalDate.of(2026, 10, 10)
        // september: 5 nákupov po 10 € v prvých 10 dňoch + 5 po 100 € neskôr
        val rows = (1..5).map { row(Category.MLIECNE, 1_000, LocalDate.of(2026, 9, it)) } +
            (1..5).map { row(Category.MLIECNE, 10_000, LocalDate.of(2026, 9, 20 + it)) } +
            listOf(row(Category.MLIECNE, 6_000, LocalDate.of(2026, 10, 5)))
        val r = sk.miro.blocky.domain.Analytics.report(rows, java.time.YearMonth.of(2026, 10), emptyMap(), today)
        assertEquals(5_000L, r.compareCents)          // len 1.–10. september
        // minulý mesiac s 1 malým bločkom → bez porovnania
        val few = listOf(row(Category.MLIECNE, 300, LocalDate.of(2026, 9, 2)), row(Category.MLIECNE, 6_000, LocalDate.of(2026, 10, 5)))
        assertNull(sk.miro.blocky.domain.Analytics.report(few, java.time.YearMonth.of(2026, 10), emptyMap(), today).compareCents)
    }
}
