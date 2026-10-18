package sk.miro.blocky

import org.junit.Assert.*
import org.junit.Test
import sk.miro.blocky.data.ItemRow
import sk.miro.blocky.data.ReceiptImport
import sk.miro.blocky.domain.BudgetPlanner
import sk.miro.blocky.domain.Category
import java.time.LocalDate
import java.time.ZoneId

class BudgetPlannerTest {
    private var id = 0L
    private fun row(cat: Category, cents: Long, date: LocalDate) = ItemRow(++id, id, "x", "x", 1.0, cents, cat.name, "Obchod",
        date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

    private val today = LocalDate.of(2026, 10, 18)

    @Test fun vicesGetBiggerCutThanEssentials() {
        val rows = (1..6).flatMap { m ->
            val d = LocalDate.of(2026, 3 + m, 10)   // apríl – september
            listOf(row(Category.SLADKOSTI, 4_000, d), row(Category.MLIECNE, 4_000, d))
        }
        val p = BudgetPlanner.plan(rows, today).associateBy { it.category }
        assertEquals(3_400, p[Category.SLADKOSTI]!!.budgetCents)   // 40 € −15 % = 34 €
        assertEquals(3_800, p[Category.MLIECNE]!!.budgetCents)     // 40 € −5 % = 38 €
        assertEquals(6, p[Category.SLADKOSTI]!!.months)
        assertTrue(p[Category.SLADKOSTI]!!.savingCents > 0)
    }

    @Test fun medianIgnoresOneOffSpike() {
        val rows = (4..9).map { m -> row(Category.DOMACNOST, if (m == 6) 50_000 else 6_000, LocalDate.of(2026, m, 5)) }
        val s = BudgetPlanner.plan(rows, today).single()
        assertEquals(6_000, s.typicalCents)
        assertEquals(5_700, s.budgetCents)   // 60 € × 0,95 = 57 €
    }

    @Test fun recentImprovementIsKept() {
        val rows = (1..9).map { m -> row(Category.ALKOHOL, if (m >= 7) 2_000 else 4_000, LocalDate.of(2026, m, 5)) }
        val s = BudgetPlanner.plan(rows, today).single()
        assertEquals(1_700, s.budgetCents)   // posledné 3 mesiace 20 € → −15 % = 17 €
    }

    @Test fun skipsDepositsAndTinyCategories() {
        val d = LocalDate.of(2026, 9, 5)
        val rows = listOf(row(Category.ZALOHY, 5_000, d), row(Category.TABAK, 100, d))
        assertTrue(BudgetPlanner.plan(rows, today).isEmpty())
    }

    @Test fun firstMonthUsesProjection() {
        val rows = listOf(row(Category.PECIVO, 1_800, LocalDate.of(2026, 10, 3)))
        val s = BudgetPlanner.plan(rows, today).single()
        assertEquals(0, s.months)
        assertTrue(s.budgetCents in 2_500..3_100)   // 18 € za 18 dní ≈ 31 € za mesiac
    }

    @Test fun roundsToNiceAmounts() {
        assertEquals(1_900, BudgetPlanner.roundDown(1_999))
        assertEquals(12_500, BudgetPlanner.roundDown(12_999))
        assertEquals(35_000, BudgetPlanner.roundDown(35_099))
    }

    @Test fun receiptIdFromEmailText() {
        val text = "Elektronický doklad\nUID: O-E1E31477DCDA4897A31477DCDA3897F0\nĎakujeme"
        assertEquals(listOf("O-E1E31477DCDA4897A31477DCDA3897F0"), ReceiptImport.codesInText(text))
        assertTrue(ReceiptImport.codesInText("bez dokladu").isEmpty())
    }
}
