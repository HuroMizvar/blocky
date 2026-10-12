package sk.miro.blocky

import org.junit.Assert.assertTrue
import org.junit.Test
import sk.miro.blocky.data.DemoData
import sk.miro.blocky.data.ItemRow
import sk.miro.blocky.domain.Analytics
import sk.miro.blocky.domain.Categorizer
import java.time.LocalDate
import java.time.YearMonth

class DemoDataTest {
    @Test fun demoDataTriggersRecommendations() {
        val today = LocalDate.of(2026, 10, 28)
        val receipts = DemoData.generate(today)
        assertTrue("málo bločkov: ${receipts.size}", receipts.size > 60)
        var rid = 0L
        val rows = receipts.flatMapIndexed { idx, r ->
            r.items.map {
                ItemRow(rid++, idx.toLong(), it.name, Categorizer.normalize(it.name), it.quantity, it.priceCents,
                    Categorizer.categorize(it.name, r.merchantName, it.priceCents).name, r.merchantName, r.issuedAt)
            }
        }
        val months = Analytics.availableMonths(rows)
        assertTrue("mesiace: $months", months.size == 4)

        val rep = Analytics.report(rows, YearMonth.from(today), emptyMap(), today)
        val recs = Analytics.recommendations(rows, rep, today).map { it.title }
        val expected = listOf("lacnejšie v", "Zdražené:", "drobných nákupov", "zálohy")
        val hits = expected.count { e -> recs.any { it.contains(e) } }
        assertTrue("odporúčania: $recs", hits >= 3)
    }

    @Test fun mockReceiptHasItems() {
        val r = DemoData.mockReceipt("ľubovoľný QR")
        assertTrue(r.items.isNotEmpty() && r.totalCents > 0)
    }
}
