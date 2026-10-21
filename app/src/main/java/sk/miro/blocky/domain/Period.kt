package sk.miro.blocky.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Rozpočtové obdobie („mesiac“) podľa dňa výplaty.
 *
 * Deň výplaty 1 = kalendárny mesiac. Pri dni výplaty 10 je obdobie „január“ od 10. 1. do 9. 2.,
 * „február“ od 10. 2. do 9. 3. atď. Ak mesiac daný deň nemá (31. vo februári), obdobie začne posledný deň mesiaca.
 */
object Period {
    @Volatile var payDay: Int = 1
        set(v) { field = v.coerceIn(1, 31) }

    private fun startDay(m: YearMonth) = minOf(payDay, m.lengthOfMonth())

    /** prvý deň obdobia */
    fun start(m: YearMonth): LocalDate = m.atDay(startDay(m))

    /** prvý deň nasledujúceho obdobia */
    fun endExclusive(m: YearMonth): LocalDate = start(m.plusMonths(1))

    /** posledný deň obdobia */
    fun end(m: YearMonth): LocalDate = endExclusive(m).minusDays(1)

    fun length(m: YearMonth): Int = ChronoUnit.DAYS.between(start(m), endExclusive(m)).toInt()

    /** obdobie, do ktorého patrí deň */
    fun of(date: LocalDate): YearMonth {
        val m = YearMonth.from(date)
        return if (date.dayOfMonth >= startDay(m)) m else m.minusMonths(1)
    }

    fun current(today: LocalDate = LocalDate.now()): YearMonth = of(today)

    /** koľký deň obdobia je [today] (1 = prvý deň) */
    fun dayIndex(m: YearMonth, today: LocalDate): Int = ChronoUnit.DAYS.between(start(m), today).toInt() + 1

    /** „10. 1. – 9. 2.“; pri kalendárnom mesiaci null */
    fun rangeLabel(m: YearMonth): String? {
        if (payDay == 1) return null
        val s = start(m); val e = end(m)
        return "${s.dayOfMonth}. ${s.monthValue}. – ${e.dayOfMonth}. ${e.monthValue}."
    }
}
