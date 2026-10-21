package sk.miro.blocky.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Vlastné kategórie používateľa (najviac 8). Uložené v nastaveniach aplikácie, v domácnosti sa zdieľajú.
 * Každá obsadí jeden voľný slot CUSTOM1…CUSTOM8, takže sa s ňou dá pracovať ako s bežnou kategóriou.
 */
object CustomCategories {
    data class Def(val slot: Category, val label: String, val icon: String, val color: Int)

    /**
     * Compose stav – obrazovky, ktoré čítajú názov / ikonu / farbu vlastnej kategórie, sa samy prekreslia,
     * aj keď zmena príde zo synchronizácie domácnosti na pozadí.
     */
    private val state = androidx.compose.runtime.mutableStateOf<Map<Category, Def>>(emptyMap())
    var defs: Map<Category, Def>
        get() = state.value
        private set(v) { state.value = v }

    private val slots = Category.entries.filter { it.isCustom }

    fun labelOf(c: Category): String? = if (c.name.startsWith("CUSTOM")) defs[c]?.label else null
    fun isActive(c: Category) = defs.containsKey(c)
    fun def(c: Category): Def? = defs[c]
    val all: List<Def> get() = slots.mapNotNull { defs[it] }

    /** Vytvorí kategóriu vo voľnom slote; null = všetkých 8 je obsadených. */
    fun create(label: String, icon: String, color: Int): Category? {
        val free = slots.firstOrNull { it !in defs } ?: return null
        defs = defs + (free to Def(free, label.trim().take(30), icon, color))
        return free
    }

    /** Nahradí všetky definície (zlúčenie pri vstupe do domácnosti). */
    fun replaceAll(m: Map<Category, Def>) { defs = m }

    fun update(c: Category, label: String, icon: String, color: Int) {
        if (c in defs) defs = defs + (c to Def(c, label.trim().take(30), icon, color))
    }

    fun remove(c: Category) { defs = defs - c }

    fun toJson(): String = JSONArray().apply {
        all.forEach { put(JSONObject().put("slot", it.slot.name).put("label", it.label).put("icon", it.icon).put("color", it.color)) }
    }.toString()

    fun load(json: String?) { defs = parse(json) }

    fun parse(json: String?): Map<Category, Def> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val a = JSONArray(json)
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val slot = Category.entries.firstOrNull { it.name == o.getString("slot") && it.isCustom } ?: return@mapNotNull null
                slot to Def(slot, o.getString("label"), o.optString("icon", "star"), o.optInt("color", 0))
            }.toMap()
        } catch (e: Exception) { emptyMap() }
    }
}
