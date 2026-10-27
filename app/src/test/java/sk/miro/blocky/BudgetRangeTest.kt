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
}
