package sk.miro.blocky

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import sk.miro.blocky.domain.Category
import sk.miro.blocky.domain.CustomCategories
import sk.miro.blocky.domain.CustomCategories.Def
import sk.miro.blocky.domain.CustomCategories.Resolution
import sk.miro.blocky.domain.FixedExpense
import sk.miro.blocky.domain.FixedExpenses
import sk.miro.blocky.domain.Period
import java.time.LocalDate
import java.time.YearMonth

class HouseholdDataTest {
    @After fun reset() { Period.payDay = 1 }

    private fun d(label: String, id: String, created: Long, at: Long = created, slot: Category = Category.CUSTOM1) =
        Def(slot, label, "star", 0, id, created, at)

    @Test fun sameCategoryNewerEditWins() {
        assertEquals(Resolution.Accept, CustomCategories.resolve(d("Deti", "a", 1, 5), d("Deti", "a", 1, 9)))
        assertEquals(Resolution.KeepLocal, CustomCategories.resolve(d("Deti", "a", 1, 9), d("Deti", "a", 1, 5)))
    }

    @Test fun slotConflictOlderKeepsSlot() {
        // dvaja členovia naraz vytvorili rôzne kategórie v tom istom slote
        assertEquals(Resolution.RelocateLocal, CustomCategories.resolve(d("Chata", "b", 20), d("Deti", "a", 10)))
        assertEquals(Resolution.KeepLocal, CustomCategories.resolve(d("Deti", "a", 10), d("Chata", "b", 20)))
    }

    @Test fun sameNameIsMerged() {
        assertEquals(Resolution.MergeLocalIntoRemote, CustomCategories.resolve(d("deti ", "b", 20), d("Deti", "a", 10)))
        assertEquals(Resolution.Accept, CustomCategories.resolve(null, d("Deti", "a", 10)))
    }

    private fun fe(day: Int, since: String = "2026-01") = FixedExpense("x", "Hypotéka", "Hypotéka", 65000, day, since)

    @Test fun fixedPaidOnlyAfterDueDate() {
        val m = YearMonth.of(2026, 10)
        assertEquals(1, FixedExpenses.payments(listOf(fe(15)), m, LocalDate.of(2026, 10, 15)).size)
        assertEquals(0, FixedExpenses.payments(listOf(fe(15)), m, LocalDate.of(2026, 10, 14)).size)
        // pred dátumom „od“ sa nezapočítava
        assertEquals(0, FixedExpenses.payments(listOf(fe(5, "2026-11")), m, LocalDate.of(2026, 10, 30)).size)
    }

    @Test fun fixedDueDateFollowsPayday() {
        Period.payDay = 10
        // obdobie október = 10. 10. – 9. 11.; platba 5. → 5. 11.
        assertEquals(LocalDate.of(2026, 11, 5), FixedExpenses.dueDate(fe(5), YearMonth.of(2026, 10)))
        assertEquals(LocalDate.of(2026, 10, 20), FixedExpenses.dueDate(fe(20), YearMonth.of(2026, 10)))
        // 31. vo februári → posledný deň
        Period.payDay = 1
        assertEquals(LocalDate.of(2026, 2, 28), FixedExpenses.dueDate(fe(31), YearMonth.of(2026, 2)))
    }
}
