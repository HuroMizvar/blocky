package sk.miro.blocky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sk.miro.blocky.domain.DiscountFolder
import sk.miro.blocky.domain.DiscountFolder.Folded
import sk.miro.blocky.domain.DiscountFolder.Line

class DiscountTest {
    @Test fun discountGoesToProductAbove() {
        val r = DiscountFolder.fold(listOf(
            Line("Rožok", 90, "K"),
            Line("Maslo 82%", 299, "K"),
            Line("Zľava Lidl Plus", -50, "Z"),
            Line("Mlieko", 99, "K")
        ))
        assertEquals(Folded(90, 0), r[0])
        assertEquals(Folded(249, 50), r[1])
        assertNull(r[2])
        assertEquals(Folded(99, 0), r[3])
    }

    @Test fun depositsAndReturnsAreNotDiscounts() {
        val r = DiscountFolder.fold(listOf(
            Line("Pivo", 89, "K"),
            Line("Záloha plechovka", 15, "K"),
            Line("Vrátenie záloh", -150, "K"),
            Line("Vrátený tovar", -200, "V")
        ))
        assertEquals(Folded(89, 0), r[0])
        assertEquals(Folded(-150, 0), r[2])
        assertEquals(Folded(-200, 0), r[3])
    }

    @Test fun discountAfterDepositSkipsDeposit() {
        val r = DiscountFolder.fold(listOf(
            Line("Kofola 2l", 189, "K"),
            Line("Záloha PET", 15, "K"),
            Line("Akcia -20%", -38, "Z")
        ))
        assertEquals(Folded(151, 38), r[0])
        assertEquals(Folded(15, 0), r[1])
        assertNull(r[2])
    }

    @Test fun receiptWideDiscountIsSpread() {
        val r = DiscountFolder.fold(listOf(
            Line("A", 600, "K"),
            Line("B", 400, "K"),
            Line("Zľava na nákup", -100, "Z")
        ))
        assertEquals(Folded(540, 60), r[0])
        assertEquals(Folded(360, 40), r[1])
        assertNull(r[2])
    }
}

class DiscountRealReceiptTest {
    /** Skutočný bloček Tesco 15. 10. 2026: Clubcard zľavy pod medzisúčtom + stornovaná žemľa. */
    @Test fun tescoClubcardAndStorno() {
        val r = DiscountFolder.fold(listOf(
            Line("TF OLD VINES 0.75l", 409, "K"),
            Line("Magn. j.per. 1,5l VO", 55, "K"),
            Line("PET obal záloha", 15, "K"),
            Line("MALOK.RYZ.JOGURT 60g", 70, "K"),
            Line("Jemná sal.štand.50%", 26, "K"),
            Line("TS Camembert 120 g", 99, "K"),
            Line("Lun.Šqvar.nátie.120g", 99, "K"),
            Line("TS žemľa 50 g", 45, "K"),
            Line("TS žemľa 50 g", -45, "O"),
            Line("Pletenka 80 g", 87, "K"),
            Line("CC MALOK.RYZ.JOGURT", -15, "Z"),
            Line("CC TF OLD VINES 0.75", -90, "Z")
        ))
        assertEquals(Folded(319, 90), r[0])          // víno 4,09 − 0,90
        assertEquals(Folded(55, 0), r[1])
        assertEquals(Folded(15, 0), r[2])            // záloha
        assertEquals(Folded(55, 15), r[3])           // jogurt 0,70 − 0,15
        assertEquals(Folded(26, 0), r[4])
        assertEquals(Folded(99, 0), r[5])
        assertEquals(Folded(99, 0), r[6])
        assertNull(r[7]); assertNull(r[8])           // stornovaná žemľa zmizne
        assertEquals(Folded(87, 0), r[9])            // pletenka bez zľavy
        assertNull(r[10]); assertNull(r[11])
        // súčet = zaplatené za tovar 7,55 €
        assertEquals(755L, r.filterNotNull().sumOf { it.priceCents })
    }

    @Test fun twoDiscountLinesUnderOneProduct() {
        val r = DiscountFolder.fold(listOf(
            Line("Maslo", 299, "K"),
            Line("Zľava", -30, "Z"),
            Line("Zľava Lidl Plus", -20, "Z")
        ))
        assertEquals(Folded(249, 50), r[0])
    }
}
