package sk.miro.blocky

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sk.miro.blocky.domain.I18n
import java.io.File

class I18nTest {
    @Before fun load() { I18n.load(File("src/main/assets/i18n_en.tsv").readText()) }

    private fun en(s: String) = I18n.translate(s)

    @Test fun exactTexts() {
        assertEquals("Settings", en("Nastavenia"))
        assertEquals("Meat & fish", en("Mäso a ryby"))
        assertEquals("Fixed expenses", en("Fixné výdavky"))
    }

    @Test fun templatesKeepValues() {
        assertEquals("Found: 3 receipts · total 12,30 €", en("Nájdené: 3 bločky · spolu 12,30 €"))
        assertEquals("Budget exceeded: Meat & fish", en("Prekročený rozpočet: Mäso a ryby"))
        assertEquals("Synced 22. 10. 21:30", en("Synchronizované 22. 10. 21:30"))
        assertEquals("Price increase: ROZOK 40G (Lidl)", en("Zdražené: ROZOK 40G (Lidl)"))
    }

    @Test fun joinedPartsAndCounts() {
        assertEquals("Wed 22. 10. · 5 items · manual", en("Wed 22. 10. · 5 položiek · ručne"))
        assertEquals("12 receipts", en("12 bločkov"))
    }

    @Test fun itemNamesStayAsOnReceipt() {
        assertEquals("ROZOK 40G", en("ROZOK 40G"))
        assertEquals("Jemná sal.štand.50%", en("Jemná sal.štand.50%"))
    }

    @Test fun everyEntryHasSamePlaceholders() {
        File("src/main/assets/i18n_en.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { l ->
            val (k, v) = l.split('\t')
            assertEquals("Placeholders in: $k", k.split("{}").size, v.split("{}").size)
        }
    }

    @Test fun offWhenSlovak() {
        I18n.english = false
        assertEquals("Nastavenia", I18n.tr("Nastavenia"))
    }
}
