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
