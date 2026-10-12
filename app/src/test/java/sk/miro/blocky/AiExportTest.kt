package sk.miro.blocky

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.miro.blocky.data.ItemRow
import sk.miro.blocky.domain.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class AiExportTest {
    private fun ms(m: Int, d: Int) = LocalDateTime.of(2026, m, d, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val rows = listOf(
        ItemRow(1, 1, "Mlieko 1,5%", "mlieko 1,5%", 2.0, 198, Category.MLIECNE.name, "Lidl Slovenská republika, v.o.s.", ms(9, 3), "GROCERY"),
        ItemRow(2, 2, "Pizza Margherita", "pizza margherita", 1.0, 890, Category.RESTAURACIE.name, "Pizzeria Napoli s.r.o.", ms(10, 4), "RESTAURANT"),
        ItemRow(3, 3, "Záloha PET", "zaloha pet", 1.0, 15, Category.ZALOHY.name, "Lidl Slovenská republika, v.o.s.", ms(10, 5), "GROCERY")
    )

    @Test fun containsSummaryAndProfile() {
        val txt = AiExport.build(rows, emptyMap(), HouseholdProfile(people = 2, note = "varím doma"), emptyList(),
            today = LocalDate.of(2026, 10, 12))
        assertTrue(txt.contains("2 osoby"))
        assertTrue(txt.contains("O mne: varím doma"))
        assertTrue(txt.contains("Mliečne a vajcia"))
        assertTrue(txt.contains("10/2026*"))
        assertTrue(txt.contains("Pizzeria Napoli"))
        assertFalse("zálohy nie sú výdavok", txt.contains("| Zálohy za fľaše"))
    }

    @Test fun optionsHideMerchantsAndItems() {
        val txt = AiExport.build(rows, emptyMap(), HouseholdProfile(), emptyList(),
            AiExportOptions(includeMerchants = false, includeItems = false), LocalDate.of(2026, 10, 12))
        assertFalse(txt.contains("Pizzeria Napoli"))
        assertFalse(txt.contains("Mlieko 1,5%"))
    }
}
