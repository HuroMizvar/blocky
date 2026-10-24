package sk.miro.blocky.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/** Pravidelný fixný výdavok (hypotéka, úver, leasing, nájomné…) s dňom platby. */
data class FixedExpense(
    val id: String,
    val name: String,
    val kind: String,
    val cents: Long,
    /** deň platby v mesiaci (1–31; ak mesiac taký deň nemá, platí posledný deň) */
    val day: Int,
    /** od ktorého rozpočtového mesiaca sa započítava (yyyy-MM) */
    val since: String,
    /** čas poslednej zmeny (synchronizácia domácnosti) */
    val at: Long = 0
)

/** Platba fixného výdavku v danom období. */
data class FixedPayment(val expense: FixedExpense, val date: LocalDate)

/**
 * Fixné výdavky domácnosti. Počítajú sa ZVLÁŠŤ od bežných výdavkov z bločkov ako kategória
 * [Category.FIXNE], ktorá je v prehľade vždy na konci.
 */
object FixedExpenses {
    val KINDS = listOf("Hypotéka", "Úver", "Leasing", "Nájomné", "Energie", "Poistenie", "Predplatné", "Iné")

    private val _list = MutableStateFlow<List<FixedExpense>>(emptyList())
    /** spoločný stav pre celú aplikáciu (mení ho aj synchronizácia na pozadí) */
    val list: StateFlow<List<FixedExpense>> = _list
    fun set(l: List<FixedExpense>) { _list.value = l.sortedByDescending { it.cents } }

    /** dátum platby v rozpočtovom období [m] */
    fun dueDate(e: FixedExpense, m: YearMonth): LocalDate {
        val start = Period.start(m)
        fun inMonth(ym: YearMonth) = ym.atDay(minOf(e.day, ym.lengthOfMonth()))
        val a = inMonth(YearMonth.from(start))
        return if (!a.isBefore(start)) a else inMonth(YearMonth.from(start).plusMonths(1))
    }

    /**
     * Platby v období [m]. V bežiacom období len tie, ktorých deň platby už nastal ([today]),
     * pretože ide o „minuté“; v minulých obdobiach všetky.
     */
    fun payments(list: List<FixedExpense>, m: YearMonth, today: LocalDate): List<FixedPayment> =
        list.filter { e -> runCatching { YearMonth.parse(e.since) }.getOrNull()?.let { !m.isBefore(it) } ?: true }
            .map { FixedPayment(it, dueDate(it, m)) }
            .filter { !it.date.isAfter(today) }

    fun toJson(e: FixedExpense): String = JSONObject().put("id", e.id).put("name", e.name).put("kind", e.kind)
        .put("cents", e.cents).put("day", e.day).put("since", e.since).put("at", e.at).toString()

    fun fromJson(s: String): FixedExpense? = try {
        val o = JSONObject(s)
        FixedExpense(o.getString("id"), o.getString("name"), o.optString("kind", "Iné"), o.getLong("cents"),
            o.optInt("day", 1).coerceIn(1, 31), o.optString("since", YearMonth.now().toString()), o.optLong("at", 0))
    } catch (e: Exception) { null }

    fun listToJson(l: List<FixedExpense>): String = JSONArray().apply { l.forEach { put(JSONObject(toJson(it))) } }.toString()
    fun listFromJson(s: String?): List<FixedExpense> = try {
        if (s.isNullOrBlank()) emptyList()
        else JSONArray(s).let { a -> (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it).toString()) } }
    } catch (e: Exception) { emptyList() }
}
