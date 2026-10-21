package sk.miro.blocky

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import sk.miro.blocky.domain.Period
import java.time.LocalDate
import java.time.YearMonth

class PeriodTest {
    @After fun reset() { Period.payDay = 1 }

    @Test fun calendarMonthByDefault() {
        Period.payDay = 1
        assertEquals(YearMonth.of(2026, 1), Period.of(LocalDate.of(2026, 1, 31)))
        assertEquals(31, Period.length(YearMonth.of(2026, 1)))
        assertNull(Period.rangeLabel(YearMonth.of(2026, 1)))
    }

    @Test fun payDayTenth() {
        Period.payDay = 10
        assertEquals(YearMonth.of(2026, 1), Period.of(LocalDate.of(2026, 1, 10)))
        assertEquals(YearMonth.of(2026, 1), Period.of(LocalDate.of(2026, 2, 9)))
        assertEquals(YearMonth.of(2026, 2), Period.of(LocalDate.of(2026, 2, 10)))
        assertEquals(YearMonth.of(2025, 12), Period.of(LocalDate.of(2026, 1, 9)))
        assertEquals(LocalDate.of(2026, 2, 9), Period.end(YearMonth.of(2026, 1)))
        assertEquals("10. 1. – 9. 2.", Period.rangeLabel(YearMonth.of(2026, 1)))
        assertEquals(1, Period.dayIndex(YearMonth.of(2026, 1), LocalDate.of(2026, 1, 10)))
    }

    @Test fun payDayBeyondShortMonth() {
        Period.payDay = 31
        // február nemá 31. → obdobie „február“ začne 28. 2.
        assertEquals(LocalDate.of(2026, 2, 28), Period.start(YearMonth.of(2026, 2)))
        assertEquals(YearMonth.of(2026, 1), Period.of(LocalDate.of(2026, 2, 27)))
        assertEquals(YearMonth.of(2026, 2), Period.of(LocalDate.of(2026, 3, 1)))
    }
}
