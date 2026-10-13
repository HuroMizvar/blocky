package sk.miro.blocky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sk.miro.blocky.domain.ItemEdit

class ItemEditTest {
    @Test fun twoCoffeesPaidOne() {
        val r = ItemEdit.quantity(2.0, 360, 40, null, null, null, 1.0)
        assertEquals(1.0, r.quantity, 0.0)
        assertEquals(180L, r.priceCents)
        assertEquals(20L, r.discountCents)
        assertEquals(2.0, r.origQuantity!!, 0.0)
        assertEquals(360L, r.origPriceCents)
    }

    @Test fun backToOriginalClearsMarker() {
        val r = ItemEdit.quantity(1.0, 180, 20, 2.0, 360, 40, 2.0)
        assertEquals(360L, r.priceCents)
        assertEquals(40L, r.discountCents)
        assertNull(r.origQuantity)
    }

    @Test fun repeatedEditsUseOriginalPrice() {
        val r = ItemEdit.quantity(2.0, 300, 0, 3.0, 450, 0, 1.0)
        assertEquals(150L, r.priceCents)
        assertEquals(3.0, r.origQuantity!!, 0.0)
    }
}
