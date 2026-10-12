package sk.miro.blocky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.miro.blocky.data.ItemRow
import sk.miro.blocky.domain.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class MerchantTest {
    private val lidl = "Lidl Slovenská republika, v.o.s."

    /** Nahlásená chyba: víno, pizza a saláma z Lidla skončili v reštauráciách („re-PUB-lika“). */
    @Test fun lidlIsGroceryNotPub() {
        assertEquals(MerchantType.GROCERY, MerchantResolver.resolve(lidl))
        assertEquals(Category.ALKOHOL, Categorizer.categorize("Víno Tokaj 0,75l", lidl, 599))
        assertEquals(Category.MRAZENE_HOTOVE, Categorizer.categorize("Pizza šunková", lidl, 299))
        assertEquals(Category.MASO, Categorizer.categorize("Saláma Inovecká", lidl, 189))
    }

    @Test fun merchantTypes() {
        assertEquals(MerchantType.FUEL, MerchantResolver.resolve("SLOVNAFT, a.s."))
        assertEquals(MerchantType.FUEL, MerchantResolver.resolve("ORLEN Slovensko s.r.o."))
        assertEquals(MerchantType.GROCERY, MerchantResolver.resolve("Kaufland Slovenská republika v.o.s."))
        assertEquals(MerchantType.GROCERY, MerchantResolver.resolve("TESCO STORES SR, a.s."))
        assertEquals(MerchantType.RESTAURANT, MerchantResolver.resolve("Pizzeria Napoli s.r.o."))
        assertEquals(MerchantType.HOTEL, MerchantResolver.resolve("Hotel Tatra s.r.o."))
        assertEquals(MerchantType.UNKNOWN, MerchantResolver.resolve("ABC Trade s.r.o."))
    }

    @Test fun restaurantTakesWholeReceipt() {
        val pizzeria = "Pizzeria Napoli s.r.o."
        assertEquals(Category.RESTAURACIE, Categorizer.categorize("Pizza Margherita", pizzeria, 890))
        assertEquals(Category.RESTAURACIE, Categorizer.categorize("Pivo 0,5l", pizzeria, 250))
    }

    @Test fun fuelStationShopItems() {
        assertEquals(Category.PHM, Categorizer.categorize("NATURAL 95", "SLOVNAFT, a.s.", 6000))
        assertEquals(Category.NAPOJE, Categorizer.categorize("Káva espresso", "SLOVNAFT, a.s.", 180))
        assertEquals(Category.TABAK, Categorizer.categorize("Marlboro Gold", "SLOVNAFT, a.s.", 550))
    }

    @Test fun depositsAlwaysSeparate() {
        assertEquals(Category.ZALOHY, Categorizer.categorize("Záloha PET", lidl, 15))
        assertEquals(Category.ZALOHY, Categorizer.categorize("ZAL. PLECHOVKA", lidl, 15))
        assertEquals(Category.ZALOHY, Categorizer.categorize("Vrátenie záloh", lidl, -150))
    }

    private fun ms(y: Int, m: Int, d: Int) =
        LocalDateTime.of(y, m, d, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun depositsExcludedFromTotal() {
        val t = ms(2026, 10, 5)
        val rows = listOf(
            ItemRow(1, 1, "Pivo", "pivo", 4.0, 400, Category.ALKOHOL.name, lidl, t),
            ItemRow(2, 1, "Záloha plech", "zaloha plech", 4.0, 60, Category.ZALOHY.name, lidl, t),
            ItemRow(3, 2, "Vrátenie záloh", "vratenie zaloh", 1.0, -30, Category.ZALOHY.name, lidl, t)
        )
        val rep = Analytics.report(rows, YearMonth.of(2026, 10), emptyMap(), LocalDate.of(2026, 10, 31))
        assertEquals(400L, rep.totalCents)
        assertEquals(60L, rep.depositPaidCents)
        assertEquals(30L, rep.depositReturnedCents)
        assertTrue(rep.categories.none { it.category == Category.ZALOHY })
    }

    @Test fun profileSummary() {
        assertEquals("3 osoby · 1 dieťa · auto", HouseholdProfile(people = 3, kids = 1, car = true).summary())
    }
}
