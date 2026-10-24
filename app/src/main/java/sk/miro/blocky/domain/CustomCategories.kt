package sk.miro.blocky.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Vlastné kategórie (najviac 8 na domácnosť). Uložené v nastaveniach aplikácie, v domácnosti sa zdieľajú.
 * Každá obsadí jeden slot CUSTOM1…CUSTOM8, takže sa s ňou dá pracovať ako s bežnou kategóriou.
 *
 * Synchronizácia ide po jednotlivých slotoch. Každá kategória má jedinečné [Def.id] a čas vytvorenia
 * [Def.createdAt]. Ak dvaja členovia naraz obsadia ten istý slot, slot ostane staršej kategórii a novšia sa
 * presunie do voľného slotu (viď [resolve]); kategórie s rovnakým názvom sa zlúčia do jednej.
 */
object CustomCategories {
    data class Def(
        val slot: Category,
        val label: String,
        val icon: String,
        val color: Int,
        val id: String = "legacy-" + label.trim().lowercase(),
        val createdAt: Long = 0,
        /** čas poslednej úpravy (názov, ikona, farba) */
        val at: Long = 0
    )

    /**
     * Compose stav – obrazovky, ktoré čítajú názov / ikonu / farbu vlastnej kategórie, sa samy prekreslia,
     * aj keď zmena príde zo synchronizácie domácnosti na pozadí.
     */
    private val state = androidx.compose.runtime.mutableStateOf<Map<Category, Def>>(emptyMap())
    var defs: Map<Category, Def>
        get() = state.value
        private set(v) { state.value = v }

    val slots = Category.entries.filter { it.isCustom }

    fun labelOf(c: Category): String? = if (c.name.startsWith("CUSTOM")) defs[c]?.label else null
    fun isActive(c: Category) = defs.containsKey(c)
    fun def(c: Category): Def? = defs[c]
    val all: List<Def> get() = slots.mapNotNull { defs[it] }
    fun freeSlot(except: Category? = null): Category? = slots.firstOrNull { it !in defs && it != except }

    /** Vytvorí kategóriu vo voľnom slote; null = všetkých 8 je obsadených. */
    fun create(label: String, icon: String, color: Int, now: Long = System.currentTimeMillis()): Category? {
        val free = freeSlot() ?: return null
        defs = defs + (free to Def(free, label.trim().take(30), icon, color,
            id = java.util.UUID.randomUUID().toString(), createdAt = now, at = now))
        return free
    }

    fun update(c: Category, label: String, icon: String, color: Int, now: Long = System.currentTimeMillis()) {
        val d = defs[c] ?: return
        defs = defs + (c to d.copy(label = label.trim().take(30), icon = icon, color = color, at = now))
    }

    fun put(d: Def) { defs = defs + (d.slot to d) }
    fun remove(c: Category) { defs = defs - c }
    fun replaceAll(m: Map<Category, Def>) { defs = m }

    // ---------- konflikty pri synchronizácii ----------

    sealed interface Resolution {
        /** prijať kategóriu z domácnosti do slotu */
        data object Accept : Resolution
        /** naša verzia je novšia / slot patrí nám – poslať ju znova */
        data object KeepLocal : Resolution
        /** rovnaký názov – naše položky presunúť do kategórie z domácnosti */
        data object MergeLocalIntoRemote : Resolution
        /** slot patrí kategórii z domácnosti – našu presunúť do voľného slotu */
        data object RelocateLocal : Resolution
    }

    fun resolve(local: Def?, remote: Def): Resolution = when {
        local == null -> Resolution.Accept
        local.id == remote.id -> if (remote.at >= local.at) Resolution.Accept else Resolution.KeepLocal
        local.label.trim().equals(remote.label.trim(), ignoreCase = true) ->
            if (remote.createdAt < local.createdAt || (remote.createdAt == local.createdAt && remote.id < local.id))
                Resolution.MergeLocalIntoRemote else Resolution.KeepLocal
        remote.createdAt < local.createdAt || (remote.createdAt == local.createdAt && remote.id < local.id) ->
            Resolution.RelocateLocal
        else -> Resolution.KeepLocal
    }

    // ---------- JSON ----------

    fun defToJson(d: Def): String = defObj(d).toString()

    private fun defObj(d: Def) = JSONObject().put("slot", d.slot.name).put("label", d.label).put("icon", d.icon)
        .put("color", d.color).put("id", d.id).put("createdAt", d.createdAt).put("at", d.at)

    fun parseDef(slotName: String?, json: String): Def? = try {
        val o = JSONObject(json)
        val slot = Category.entries.firstOrNull { it.name == (slotName ?: o.getString("slot")) && it.isCustom }
        if (slot == null) null else {
            val label = o.getString("label")
            Def(slot, label, o.optString("icon", "star"), o.optInt("color", 0),
                o.optString("id", "").ifBlank { "legacy-" + label.trim().lowercase() },
                o.optLong("createdAt", 0), o.optLong("at", 0))
        }
    } catch (e: Exception) { null }

    fun toJson(): String = JSONArray().apply { all.forEach { put(defObj(it)) } }.toString()

    fun load(json: String?) { defs = parse(json) }

    fun parse(json: String?): Map<Category, Def> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val a = JSONArray(json)
            (0 until a.length()).mapNotNull { i -> parseDef(null, a.getJSONObject(i).toString()) }.associateBy { it.slot }
        } catch (e: Exception) { emptyMap() }
    }
}
