package sk.miro.blocky

import org.junit.Assert.assertEquals
import org.junit.Test
import sk.miro.blocky.domain.*

class AutoMotoTest {
    @Test fun autoMerchants() {
        assertEquals(MerchantType.AUTO, MerchantResolver.resolve("AUTOSERVIS Novák s.r.o."))
        assertEquals(MerchantType.AUTO, MerchantResolver.resolve("Auto Kelly Slovakia s.r.o."))
        assertEquals(MerchantType.AUTO, MerchantResolver.resolve("STK Bratislava a.s."))
        assertEquals(Category.AUTO_MOTO, Categorizer.categorize("Výmena oleja", "AUTOSERVIS Novák s.r.o.", 4500))
    }

    @Test fun autoItemsAnywhere() {
        val lidl = "Lidl Slovenská republika, v.o.s."
        assertEquals(Category.AUTO_MOTO, Categorizer.categorize("Motorový olej 5W-30 1l", lidl, 899))
        assertEquals(Category.AUTO_MOTO, Categorizer.categorize("Zimná zmes do ostrekovačov 3l", lidl, 349))
        assertEquals(Category.AUTO_MOTO, Categorizer.categorize("Diaľničná známka 365", "SLOVNAFT, a.s.", 9000))
        assertEquals(Category.TRVANLIVE, Categorizer.categorize("Olej slnečnicový 1l", lidl, 199))
        assertEquals(Category.PHM, Categorizer.categorize("Natural 95", "SLOVNAFT, a.s.", 6000))
    }
}
