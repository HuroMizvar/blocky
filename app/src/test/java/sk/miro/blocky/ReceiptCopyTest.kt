package sk.miro.blocky

import org.junit.Assert.*
import org.junit.Test
import sk.miro.blocky.domain.ReceiptCopy

class ReceiptCopyTest {
    private val copy = ReceiptCopy(
        merchant = "Lidl Slovenská republika, v.o.s.",
        orgAddress = "Ružinovská 1/E, 821 05 Bratislava",
        ico = "35793783", dic = "2020259983", icDph = "SK2020259983",
        unitName = "Lidl Pezinok", unitAddress = "Holubyho 2, 902 01 Pezinok",
        cashRegisterCode = "88812345678900001", receiptNumber = "152",
        issuedAt = 1_762_348_282_000,
        items = listOf(
            ReceiptCopy.Line("Pánske tričko bavlnené s veľmi dlhým názvom položky", 1.0, 999, 23.0),
            ReceiptCopy.Line("Mlieko 1,5 %", 2.0, 178, 5.0),
            ReceiptCopy.Line("Banány", 0.734, 88, 5.0)
        ),
        totalCents = 1265,
        receiptId = "O-0123456789ABCDEF0123456789ABCDEF"
    )

    @Test fun rowsFitReceiptWidth() {
        val rows = copy.rows() + copy.footer()
        assertTrue(rows.all { it.text.length <= ReceiptCopy.WIDTH })
        val text = rows.joinToString("\n") { it.text }
        assertTrue(text.contains("IČO: 35793783"))
        assertTrue(text.contains("SPOLU EUR"))
        assertTrue(text.contains("12,65"))
        assertTrue(text.contains("  2 x 0,89"))
        assertTrue(text.contains("0,734 x 1,20"))
        assertTrue(text.contains("O-0123456789ABCDEF0123456789ABCDEF"))
        assertTrue(text.contains("ELEKTRONICKÁ KÓPIA"))
    }

    @Test fun vatFromItemPrices() {
        val v = copy.vat()
        assertEquals(listOf(23.0, 5.0), v.map { it.rate })
        assertEquals(187L, v[0].vatCents)            // 9,99 × 23/123
        assertEquals(999L, v[0].baseCents + v[0].vatCents)
        assertEquals(266L, v[1].baseCents + v[1].vatCents)
    }

    @Test fun qrOnlyForEkasa() {
        assertEquals("O-0123456789ABCDEF0123456789ABCDEF", copy.qrContent)
        assertNull(copy.copy(manual = true, receiptId = null).qrContent)
        assertTrue(copy.copy(manual = true, receiptId = null).rows().any { it.text.contains("RUČNE ZADANÝ") })
    }

    @Test fun fileNameIsSafe() {
        val n = copy.fileName()
        assertTrue(n, n.matches(Regex("Blocek_Lidl_\\d{4}-\\d{2}-\\d{2}_\\d{4}\\.pdf")))
        assertEquals(listOf("abc", "defgh"), ReceiptCopy.wrap("abc defgh", 6))
        assertEquals(listOf("abcdef", "gh"), ReceiptCopy.wrap("abcdefgh", 6))
        assertEquals("-1,05", ReceiptCopy.money(-105))
    }
}
