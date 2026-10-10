package sk.miro.blocky

import org.junit.Assert.*
import org.junit.Test
import sk.miro.blocky.data.EkasaClient
import sk.miro.blocky.data.EkasaQr
import sk.miro.blocky.data.ItemRow
import sk.miro.blocky.domain.Analytics
import sk.miro.blocky.domain.Categorizer
import sk.miro.blocky.domain.Category
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class LogicTest {

    @Test fun qrOnline() {
        val q = EkasaQr.parse("O-7DBCDA8A56EE426DBBDA8A56EE726D50")
        assertTrue(q is EkasaQr.Online)
    }

    @Test fun qrOffline() {
        val q = EkasaQr.parse("1234567890ABCDEF:88812345678900001:261010090300:15:12.50")
        assertTrue(q is EkasaQr.Offline)
        assertEquals(15L, (q as EkasaQr.Offline).receiptNumber)
    }

    @Test fun qrGarbage() = assertNull(EkasaQr.parse("https://example.com"))

    @Test fun categorize() {
        assertEquals(Category.ZDRAVIE, Categorizer.categorize("Ibalgin 400", "Dr.Max", 500))
        assertEquals(Category.ALKOHOL, Categorizer.categorize("Zlatý Bažant 12% 0,5l", "Billa", 120))
        assertEquals(Category.NAPOJE, Categorizer.categorize("KOFOLA ORIGINAL 2L", "Tesco", 250))
        assertEquals(Category.PHM, Categorizer.categorize("NATURAL 95", "Slovnaft a.s.", 6000))
        assertEquals(Category.RESTAURACIE, Categorizer.categorize("Big Mac Menu", "McDonald's", 899))
        assertEquals(Category.PECIVO, Categorizer.categorize("Rožok biely 40g", "Lidl", 9))
        assertEquals(Category.ZLAVY, Categorizer.categorize("Zľava Lidl Plus", "Lidl", -50))
    }

    @Test fun parseResponse() {
        val json = """{"returnValue":0,"receipt":{"receiptId":"O-AAA","ico":"35793783",
            "issueDate":"10.10.2026 09:03:00","totalPrice":3.47,
            "organization":{"name":"Lidl Slovenská republika, v.o.s."},
            "unit":{"streetName":"Hlavná","buildingNumber":"1","municipality":"Košice"},
            "items":[{"name":"Rožok","itemType":"K","quantity":10,"vatRate":10,"price":0.9},
                     {"name":"Mlieko 1,5%","itemType":"K","quantity":2,"vatRate":10,"price":2.57}]}}"""
        val r = EkasaClient.parseResponse(json)
        assertEquals(347L, r.totalCents)
        assertEquals(2, r.items.size)
        assertEquals(90L, r.items[0].priceCents)
        assertEquals("Hlavná 1 Košice", r.storeAddress)
    }

    private fun ms(y: Int, m: Int, d: Int) =
        LocalDateTime.of(y, m, d, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private var rid = 0L
    private fun row(name: String, cents: Long, cat: Category, merchant: String, t: Long, qty: Double = 1.0) =
        ItemRow(rid++, rid, name, Categorizer.normalize(name), qty, cents, cat.name, merchant, t)

    @Test fun reportAndRecommendations() {
        val rows = mutableListOf<ItemRow>()
        // 3 mesiace histórie: alkohol 20 €/mes, mlieko v Lidli aj Bille
        for (m in 7..9) {
            rows += row("Pivo", 2000, Category.ALKOHOL, "Lidl", ms(2026, m, 5))
            rows += row("Mlieko", 99, Category.MLIECNE, "Lidl", ms(2026, m, 6))
            rows += row("Mlieko", 199, Category.MLIECNE, "Billa", ms(2026, m, 7))
            rows += row("Chlieb", 20000, Category.PECIVO, "Lidl", ms(2026, m, 8))
        }
        // október: alkohol vystrelí na 60 €
        rows += row("Pivo", 6000, Category.ALKOHOL, "Lidl", ms(2026, 10, 3))
        rows += row("Mlieko", 199, Category.MLIECNE, "Billa", ms(2026, 10, 4))
        rows += row("Chlieb", 20000, Category.PECIVO, "Lidl", ms(2026, 10, 4))

        val oct = YearMonth.of(2026, 10)
        val rep = Analytics.report(rows, oct, mapOf(Category.ALKOHOL to 3000L), LocalDate.of(2026, 10, 31))
        assertEquals(26199L, rep.totalCents)
        val alc = rep.categories.first { it.category == Category.ALKOHOL }
        assertEquals(2000L, alc.avg3Cents)

        val recs = Analytics.recommendations(rows, rep, LocalDate.of(2026, 10, 31))
        assertTrue(recs.any { it.title.startsWith("Prekročený rozpočet: Alkohol") && it.estSavingCents == 3000L })
        assertTrue(recs.any { it.title.startsWith("Alkohol: +") })
        assertTrue(recs.any { it.title.contains("lacnejšie v Lidl") })
        assertEquals("1234,50 €", Analytics.eur(123450))
    }
}
