package sk.miro.blocky.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.miro.blocky.domain.Category
import sk.miro.blocky.domain.ItemClassifier

/**
 * Vstavaná AI (v aplikácii). Model sa načíta zo súboru assets/item_model.tsv a doučí sa
 * z opráv používateľa (ručné pravidlá + opravené položky). Všetko prebieha len v telefóne.
 */
object InternalAi {
    @Volatile private var base: String? = null
    @Volatile private var model: ItemClassifier? = null

    private fun baseText(context: Context): String =
        base ?: context.assets.open("item_model.tsv").bufferedReader(Charsets.UTF_8).use { it.readText() }.also { base = it }

    /** Model doučený z opráv. Prvé volanie trvá chvíľu (načítanie ~0,5 MB). */
    suspend fun get(context: Context, dao: BlockyDao): ItemClassifier =
        model ?: rebuild(context, dao)

    /** Znova zostaví model s aktuálnymi opravami (po ručnej zmene kategórie). */
    suspend fun rebuild(context: Context, dao: BlockyDao): ItemClassifier = withContext(Dispatchers.Default) {
        val m = ItemClassifier.parse(baseText(context))
        val learned = LinkedHashMap<String, Category>()
        dao.aiExamples().forEach { learned[it.normName] = Category.fromName(it.category) }
        dao.rules().forEach { learned[it.normName] = Category.fromName(it.category) }
        m.learn(learned.filterValues { it != Category.INE })
        model = m
        m
    }

    /** Opravy sa zmenili (napr. prišli od iného člena domácnosti) – model sa pri ďalšom použití zostaví znova. */
    fun invalidate() { model = null }
}
