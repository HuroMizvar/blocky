package sk.miro.blocky

import org.junit.Assert.*
import org.junit.Test
import sk.miro.blocky.domain.Category
import sk.miro.blocky.domain.Categorizer
import sk.miro.blocky.domain.ItemClassifier
import sk.miro.blocky.domain.MerchantType
import java.io.File

class ItemClassifierTest {
    private val model by lazy { ItemClassifier.parse(File("src/main/assets/item_model.tsv").readText()) }

    /** Kotlin musí dávať rovnaké výsledky ako trénovací skript (tools/ai/train.py). */
    @Test fun sameAsTrainingScript() {
        val lines = javaClass.classLoader!!.getResource("item_model_expected.tsv")!!.readText().trim().lines()
        var diff = 0
        for (l in lines) {
            val (name, exp) = l.split('\t')
            val got = model.predict(name)?.category?.name ?: "-"
            if (got != exp) { diff++; println("rozdiel: $name python=$exp kotlin=$got") }
        }
        assertTrue("príliš veľa rozdielov: $diff", diff <= 2)
    }

    @Test fun abbreviationsFromReceipts() {
        assertEquals(Category.MLIECNE, model.predict("MLK POLOT.1,5% 1L")?.category)
        assertEquals(Category.MASO, model.predict("KUR.PRS.REZNE 1KG")?.category)
        assertEquals(Category.PECIVO, model.predict("ROZOK 40G")?.category)
        assertEquals(Category.TRVANLIVE, model.predict("Sój.omáčka150ml")?.category)
    }

    @Test fun unknownStaysUnknown() {
        assertNull(model.predict("XYZ 12345"))
        assertNull(model.predict("POLOZKA"))
    }

    @Test fun learnsFromCorrection() {
        val m = ItemClassifier.parse(File("src/main/assets/item_model.tsv").readText())
        m.learn(mapOf(Categorizer.normalize("Saguaro Karpatka") to Category.NAPOJE,
                      Categorizer.normalize("Saguaro jemne perlivá") to Category.NAPOJE))
        assertEquals(Category.NAPOJE, m.predict("SAGUARO NEPERL. 1,5L")?.category)
    }

    @Test fun aiDoesNotPutGroceryIntoRestaurants() {
        val ai: (String) -> Category? = { Category.RESTAURACIE }
        val r = Categorizer.categorizeWithSource("Espresso zrnková káva", MerchantType.GROCERY, 499, emptyMap(), ai)
        assertNotEquals(Category.RESTAURACIE, r.category)
    }
}
