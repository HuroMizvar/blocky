package sk.miro.blocky.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Testovacie dáta. Generuje realistické bločky za aktuálny + 3 predchádzajúce mesiace tak,
 * aby sa ukázali všetky typy odporúčaní:
 *  - Lidl je na rovnaké produkty lacnejší ako Billa (porovnanie cien),
 *  - maslo v aktuálnom mesiaci zdraželo (zdražené položky),
 *  - alkohol v aktuálnom mesiaci výrazne narástol (nárast nad priemer / podiel nad stropom),
 *  - veľa drobných nákupov v aktuálnom mesiaci (impulzívne nákupy),
 *  - zálohy za plechovky (nevrátené zálohy).
 * Bločky sa ukladajú so zdrojom "DEMO" a dajú sa jedným tlačidlom zmazať.
 */
object DemoData {
    const val SOURCE = "DEMO"

    // vymyslené obchody – testovacie bločky nesmú vyzerať ako doklady skutočných firiem
    private const val LIDL = "Supermarket Zelený košík"
    private const val BILLA = "Potraviny Pod lipou"
    private const val TESCO = "Hypermarket Veľký vozík"
    private const val KAUFLAND = "Supermarket Plná taška"
    private const val FRESH = "Večierka Na rohu"

    private val storePrice = mapOf(LIDL to 0.92, KAUFLAND to 0.97, TESCO to 1.0, BILLA to 1.12, FRESH to 1.2)

    /** názov, základná cena v centoch, pravdepodobnosť v košíku */
    private data class P(val name: String, val cents: Long, val prob: Double)

    private val basket = listOf(
        P("Rožok biely 40g", 9, 0.8), P("Chlieb Šumava 1kg", 199, 0.35), P("Mlieko polotučné 1,5% 1l", 99, 0.6),
        P("Jogurt biely 150g", 45, 0.4), P("Eidam 30% plátky 100g", 139, 0.35), P("Maslo 82% 250g", 259, 0.25),
        P("Vajcia M 10ks", 279, 0.2), P("Šunka dusená 100g", 159, 0.3), P("Kuracie prsia 1kg", 699, 0.2),
        P("Bravčová krkovička 1kg", 749, 0.12), P("Banány 1kg", 129, 0.45), P("Jablká 1kg", 149, 0.35),
        P("Paradajky 500g", 189, 0.3), P("Zemiaky 2kg", 239, 0.2), P("Cibuľa 1kg", 99, 0.15),
        P("Špagety 500g", 109, 0.15), P("Olej slnečnicový 1l", 229, 0.08), P("Kofola Original 2l", 189, 0.3),
        P("Rajec jemne perlivá 1,5l", 69, 0.35), P("Milka čokoláda 100g", 169, 0.25), P("Chips Lays 140g", 239, 0.2),
        P("Toaletný papier 8ks", 399, 0.08), P("Jar 900ml", 279, 0.05), P("Pizza šunková mrazená", 299, 0.12)
    )

    private const val BEER = "Pivo svetlé 10% 0,5l plech"
    private const val DEPOSIT = "Záloha plechovka"

    private fun millis(d: LocalDate, rnd: Random): Long =
        d.atTime(LocalTime.of(rnd.nextInt(8, 20), rnd.nextInt(0, 60))).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun receipt(id: String, merchant: String, at: Long, items: List<ParsedItem>) = ParsedReceipt(
        receiptId = id, merchantName = merchant, merchantIco = null, storeAddress = "Testovacia 1, Mestečko",
        issuedAt = at, totalCents = items.sumOf { it.priceCents }, items = items, rawJson = ""
    )

    private fun item(name: String, qty: Double, unitCents: Long) =
        ParsedItem(name, qty, (unitCents * qty).roundToLong(), 20.0, "K")

    private fun groceryItems(rnd: Random, store: String, currentMonth: Boolean, beerBoost: Boolean): List<ParsedItem> {
        val mult = storePrice.getValue(store)
        val items = mutableListOf<ParsedItem>()
        for (p in basket) if (rnd.nextDouble() < p.prob) {
            var price = p.cents * mult
            if (currentMonth && p.name.startsWith("Maslo")) price *= 1.18 // zdraženie
            val qty = if (rnd.nextDouble() < 0.2) 2.0 else 1.0
            items += item(p.name, qty, price.roundToLong())
        }
        val beerProb = if (beerBoost) 0.75 else 0.25
        if (rnd.nextDouble() < beerProb) {
            val qty = if (beerBoost) rnd.nextInt(4, 9).toDouble() else rnd.nextInt(1, 4).toDouble()
            items += item(BEER, qty, (89 * mult).roundToLong())
            items += item(DEPOSIT, qty, 15)
        }
        if (items.isEmpty()) items += item(basket.first().name, 2.0, (basket.first().cents * mult).roundToLong())
        return items
    }

    fun generate(today: LocalDate = LocalDate.now(), seed: Int = 42): List<ParsedReceipt> {
        val rnd = Random(seed)
        val start = today.withDayOfMonth(1).minusMonths(3)
        val out = mutableListOf<ParsedReceipt>()
        var n = 0
        var nextFuel = start.plusDays(3)
        var d = start
        while (!d.isAfter(today)) {
            val current = d.year == today.year && d.month == today.month
            fun id() = "DEMO-${seed}-${n++}"

            if (rnd.nextDouble() < 0.45) {
                val r = rnd.nextDouble()
                val store = when {
                    r < 0.40 -> LIDL
                    r < 0.65 -> BILLA
                    r < 0.85 -> TESCO
                    else -> KAUFLAND
                }
                out += receipt(id(), store, millis(d, rnd), groceryItems(rnd, store, current, beerBoost = current))
            }
            // drobné nákupy: v aktuálnom mesiaci častejšie
            if (rnd.nextDouble() < if (current) 0.45 else 0.08) {
                val snack = listOf("Kofola 0,5l" to 129L, "Croissant maslový" to 79L, "Tatranka 50g" to 59L, "Káva espresso" to 150L)
                    .shuffled(rnd).take(rnd.nextInt(1, 3))
                out += receipt(id(), FRESH, millis(d, rnd), snack.map { item(it.first, 1.0, it.second) })
            }
            if (!d.isBefore(nextFuel)) {
                val liters = rnd.nextInt(30, 46).toDouble()
                out += receipt(id(), "Čerpacia stanica Kilometer", millis(d, rnd), listOf(item("Benzín 95", liters, 162)))
                nextFuel = d.plusDays(rnd.nextLong(11, 17))
            }
            if (rnd.nextDouble() < 0.07) {
                out += receipt(id(), "Bistro Na korze", millis(d, rnd),
                    listOf(item("Burger menu", 1.0, 899), item("Kola 0,4l", 1.0, 249)))
            }
            if (d.dayOfMonth == 12) {
                out += receipt(id(), "Lekáreň U sovy", millis(d, rnd),
                    listOf(item("Paralen 500 24tbl", 1.0, 389), item("Vitamín C 500mg", 1.0, 599)))
                out += receipt(id(), "Drogéria Mydlinka", millis(d, rnd),
                    listOf(item("Šampón Nivea 400ml", 1.0, 449), item("Zubná pasta Colgate", 1.0, 229)))
            }
            d = d.plusDays(1)
        }
        return out
    }

    /** Ukážkové mesačné rozpočty k testovacím bločkom (nastavia sa len kategóriám bez vlastného rozpočtu). */
    val budgets: Map<sk.miro.blocky.domain.Category, Long> = mapOf(
        sk.miro.blocky.domain.Category.MLIECNE to 4_000, sk.miro.blocky.domain.Category.PECIVO to 2_500,
        sk.miro.blocky.domain.Category.OVOCIE_ZELENINA to 3_500, sk.miro.blocky.domain.Category.MASO to 5_000,
        sk.miro.blocky.domain.Category.ALKOHOL to 2_000, sk.miro.blocky.domain.Category.SLADKOSTI to 1_500,
        sk.miro.blocky.domain.Category.PHM to 12_000, sk.miro.blocky.domain.Category.DROGERIA to 2_000
    )

    /** id ukážkových fixných výdavkov začínajú týmto – nesynchronizujú sa a dajú sa zmazať naraz */
    const val FIXED_PREFIX = "demo-"

    fun fixed(today: LocalDate = LocalDate.now()): List<sk.miro.blocky.domain.FixedExpense> {
        val since = sk.miro.blocky.domain.Period.of(today).minusMonths(3).toString()
        return listOf(
            sk.miro.blocky.domain.FixedExpense("${FIXED_PREFIX}najom", "Nájom (test)", "Nájomné", 55_000, 5, since),
            sk.miro.blocky.domain.FixedExpense("${FIXED_PREFIX}internet", "Internet (test)", "Predplatné", 1_999, 15, since)
        )
    }

    /** Simulovaný eKasa server: na ľubovoľný QR vráti náhodný nákup s aktuálnym časom. */
    fun mockReceipt(rawQr: String): ParsedReceipt {
        val rnd = Random(rawQr.hashCode() xor System.nanoTime().toInt())
        val store = storePrice.keys.filter { it != FRESH }.random(rnd)
        return receipt("DEMO-MOCK-${UUID.randomUUID()}", store, System.currentTimeMillis(),
            groceryItems(rnd, store, currentMonth = true, beerBoost = false))
    }
}
