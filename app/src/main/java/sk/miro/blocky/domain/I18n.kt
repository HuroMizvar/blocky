package sk.miro.blocky.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Jazyk aplikácie. Texty v kóde sú po slovensky; v angličtine sa prekladajú pri zobrazení podľa
 * slovníka assets/i18n_en.tsv (slovenský text → anglický, {} = premenná časť, napr. suma).
 *
 * Názvy položiek z bločkov, obchodov a vlastných kategórií sa NEPREKLADAJÚ – v slovníku nie sú.
 */
object I18n {
    /** Compose stav – prepnutie jazyka prekreslí celú aplikáciu */
    var english by mutableStateOf(false)

    private var exact: Map<String, String> = emptyMap()
    private var templates: List<Pair<Regex, String>> = emptyList()
    private val cache = HashMap<String, String>()

    /** slová pri počtoch („5 bločkov“) – prekladajú sa aj samostatne */
    private val COUNT_WORDS = setOf("bloček", "bločky", "bločkov", "položka", "položky", "položiek", "mesiaca", "mesiacov",
        "prevádzku", "prevádzky", "tip na úsporu", "tipy na úsporu", "tipov na úsporu")

    fun load(tsv: String) {
        val ex = HashMap<String, String>()
        val tp = ArrayList<Pair<Regex, String>>()
        for (line in tsv.lineSequence()) {
            if (line.isBlank() || line.startsWith("#")) continue
            val tab = line.indexOf('\t'); if (tab <= 0) continue
            val k = unescape(line.substring(0, tab)); val v = unescape(line.substring(tab + 1))
            ex[k] = v
            // časti spájané v kóde („ · ručne“) aj bez oddeľovačov
            val bare = k.trim(' ', '·').trim()
            if (bare != k && bare.isNotEmpty() && !ex.containsKey(bare)) ex[bare] = v.trim(' ', '·').trim()
            if (k.contains("{}")) {
                val pattern = k.split("{}").joinToString("(.+?)") { Regex.escape(it) }
                tp += Regex("^$pattern$", RegexOption.DOT_MATCHES_ALL) to v
            }
        }
        exact = ex
        // dlhšie (konkrétnejšie) vzory majú prednosť
        templates = tp.sortedByDescending { it.first.pattern.length }
        synchronized(cache) { cache.clear() }
    }

    private fun unescape(s: String) = s.replace("\\n", "\n").replace("\\\\", "\\")

    /** Preloží text, ak je zapnutá angličtina; inak ho vráti nezmenený. */
    fun tr(text: String): String {
        if (!english || text.isBlank() || exact.isEmpty()) return text
        synchronized(cache) { cache[text]?.let { return it } }
        val out = translate(text)
        synchronized(cache) { if (cache.size > 4000) cache.clear(); cache[text] = out }
        return out
    }

    /** Preklad bez ohľadu na nastavený jazyk (pre testy). */
    fun translate(text: String): String =
        whole(text) ?: segments(text) ?: countWords(text) ?: text

    private fun whole(t: String): String? {
        exact[t]?.let { return it }
        for ((re, v) in templates) {
            val m = re.matchEntire(t) ?: continue
            var i = 0
            return v.split("{}").let { parts ->
                buildString {
                    parts.forEachIndexed { idx, p ->
                        append(p)
                        if (idx < parts.lastIndex) { append(value(m.groupValues.getOrElse(++i) { "" })) }
                    }
                }
            }
        }
        return null
    }

    /** premenná časť: preloží sa len ak je to známy text (napr. názov kategórie), inak ostane (názov položky) */
    private fun value(v: String): String = exact[v] ?: countWords(v) ?: v

    private val SEP = Regex("( · |\\n|(?<=[.!?…]) )")

    /** text zložený z viacerých viet / častí oddelených „ · “ */
    private fun segments(t: String): String? {
        if (!SEP.containsMatchIn(t)) return null
        val parts = ArrayList<String>(); val seps = ArrayList<String>()
        var last = 0
        for (m in SEP.findAll(t)) { parts += t.substring(last, m.range.first); seps += m.value; last = m.range.last + 1 }
        parts += t.substring(last)
        var changed = false
        val tr = parts.map { p -> (whole(p) ?: countWords(p))?.also { changed = true } ?: p }
        if (!changed) return null
        return buildString { tr.forEachIndexed { i, p -> append(p); if (i < seps.size) append(seps[i]) } }
    }

    /** „5 bločkov“ → „5 receipts“ */
    private fun countWords(t: String): String? {
        if (!t.any { it.isDigit() }) return null
        var out = t; var changed = false
        for (w in COUNT_WORDS.sortedByDescending { it.length }) {
            val e = exact[w] ?: continue
            val re = Regex("(?<=\\d )" + Regex.escape(w) + "(?![\\p{L}])")
            if (re.containsMatchIn(out)) { out = re.replace(out, e); changed = true }
        }
        return if (changed) out else null
    }
}
