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
    val at: Long = 0,
    /** mesiace platby (1–12) podľa dátumu platby; prázdne = každý mesiac */
    val months: Set<Int> = emptySet(),
    /** upravená skutočná suma v konkrétnom rozpočtovom mesiaci (yyyy-MM → centy) */
    val overrides: Map<String, Long> = emptyMap()
) {
    /** suma v rozpočtovom mesiaci [m] – upravená, inak bežná */
    fun centsIn(m: YearMonth): Long = overrides[m.toString()] ?: cents
}

/** Platba fixného výdavku v danom období. */
data class FixedPayment(val expense: FixedExpense, val date: LocalDate, val month: YearMonth, val cents: Long) {
    val edited: Boolean get() = expense.overrides.containsKey(month.toString())
}

/**
 * Fixné výdavky domácnosti. Počítajú sa ZVLÁŠŤ od bežných výdavkov z bločkov ako kategória
 * [Category.FIXNE], ktorá je v prehľade vždy na konci.
 */
object FixedExpenses {
    /** Frekvencie platby – predvolené výbery mesiacov. */
    enum class Frequency(val label: String, val step: Int) {
        MONTHLY("Mesačne", 1), QUARTERLY("Štvrťročne", 3), HALF_YEAR("Polročne", 6), YEARLY("Ročne", 12), CUSTOM("Vlastné", 0)
    }

    /** mesiace pre frekvenciu [f] začínajúc mesiacom [first] */
    fun monthsFor(f: Frequency, first: Int): Set<Int> =
        if (f == Frequency.MONTHLY || f.step == 0) emptySet()
        else (0 until 12 / f.step).map { (first - 1 + it * f.step) % 12 + 1 }.toSet()

    /** rozpozná frekvenciu z výberu mesiacov */
    fun frequencyOf(months: Set<Int>): Frequency {
        if (months.isEmpty() || months.size == 12) return Frequency.MONTHLY
        val first = months.min()
        return listOf(Frequency.QUARTERLY, Frequency.HALF_YEAR, Frequency.YEARLY)
            .firstOrNull { monthsFor(it, first) == months } ?: Frequency.CUSTOM
    }

    private val SHORT = listOf("jan", "feb", "mar", "apr", "máj", "jún", "júl", "aug", "sep", "okt", "nov", "dec")
    private val SHORT_EN = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    fun monthShort(m: Int) = (if (I18n.english) SHORT_EN else SHORT)[m - 1]

    /** krátky popis, kedy sa platí (napr. „ročne · mar“) */
    fun describe(e: FixedExpense): String {
        val f = frequencyOf(e.months)
        return if (f == Frequency.MONTHLY) "mesačne"
        else f.label.lowercase() + " · " + e.months.sorted().joinToString(", ") { monthShort(it) }
    }

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
            .map { FixedPayment(it, dueDate(it, m), m, it.centsIn(m)) }
            .filter { (it.expense.months.isEmpty() || it.date.monthValue in it.expense.months) && !it.date.isAfter(today) }

    fun toJson(e: FixedExpense): String = JSONObject().put("id", e.id).put("name", e.name).put("kind", e.kind)
        .put("cents", e.cents).put("day", e.day).put("since", e.since).put("at", e.at)
        .apply { if (e.months.isNotEmpty()) put("months", JSONArray().apply { e.months.sorted().forEach { put(it) } }) }
        .apply { if (e.overrides.isNotEmpty()) put("overrides", JSONObject().apply { e.overrides.forEach { (k, v) -> put(k, v) } }) }
        .toString()

    fun fromJson(s: String): FixedExpense? = try {
        val o = JSONObject(s)
        FixedExpense(o.getString("id"), o.getString("name"), o.optString("kind", "Iné"), o.getLong("cents"),
            o.optInt("day", 1).coerceIn(1, 31), o.optString("since", YearMonth.now().toString()), o.optLong("at", 0),
            months = o.optJSONArray("months")?.let { a -> (0 until a.length()).map { a.getInt(it) }.filter { it in 1..12 }.toSet() } ?: emptySet(),
            overrides = o.optJSONObject("overrides")?.let { j -> j.keys().asSequence().associateWith { j.getLong(it) } } ?: emptyMap())
    } catch (e: Exception) { null }

    fun listToJson(l: List<FixedExpense>): String = JSONArray().apply { l.forEach { put(JSONObject(toJson(it))) } }.toString()
    fun listFromJson(s: String?): List<FixedExpense> = try {
        if (s.isNullOrBlank()) emptyList()
        else JSONArray(s).let { a -> (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it).toString()) } }
    } catch (e: Exception) { emptyList() }
}
