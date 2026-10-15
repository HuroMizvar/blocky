package sk.miro.blocky.domain

import kotlin.math.abs

/**
 * Priradenie zliav k produktom a spracovanie stornovaných položiek.
 *
 * 1) **Storno:** záporný riadok s rovnakým názvom a sumou ako skorší produkt (napr. Tesco „POLOŽKA STORNOVANÁ“)
 *    produkt zruší – zmiznú oba riadky.
 * 2) **Zľava s názvom produktu** (Tesco Clubcard „CC TF OLD VINES 0.75 −0,90“ až pod medzisúčtom) sa priradí
 *    produktu s rovnakým názvom.
 * 3) **Zľava hneď pod produktom** (Lidl „Zľava Lidl Plus“) patrí tomuto produktu.
 * 4) Zľava na celý nákup („zľava na nákup / doklad“) alebo zľava, ktorú nevieme priradiť, sa rozpočíta pomerne.
 *
 * Vrátený tovar (typ „V“) a zálohy sa nepovažujú za zľavu.
 */
object DiscountFolder {

    data class Line(val name: String, val priceCents: Long, val itemType: String?)

    /** Výsledok pre každý pôvodný riadok: null = riadok zľavy alebo storna, ktorý sa zlúčil a má sa odstrániť. */
    data class Folded(val priceCents: Long, val discountCents: Long)

    private val receiptWide = listOf("nakup", "doklad", "celkov", "na ucet", "na blocek", "na blocok")

    /** Slová, ktoré obchody pridávajú pred názov produktu v riadku zľavy. */
    private val discountWords = setOf("cc", "clubcard", "zlava", "zl", "lidl", "plus", "k", "card", "akcia", "kupon",
        "bonus", "vernostna", "vernost", "sleva", "promo", "my", "billa", "tesco", "kaufland", "x")

    private fun isDeposit(n: String) =
        listOf("zaloh", "zal.", "zal ", "vratn", "vykup obal", "vratenie obal").any { " $n ".contains(" $it") }

    fun isDiscountLine(l: Line): Boolean {
        if (l.priceCents >= 0) return false
        val t = l.itemType?.uppercase()
        if (t == "V" || t == "VO" || t == "O") return false          // vrátenie tovaru / zálohy / oprava
        val n = Categorizer.normalize(l.name)
        if (isDeposit(n)) return false
        return true                                                  // záporný riadok = zľava
    }

    private fun isProduct(l: Line): Boolean =
        l.priceCents > 0 && !isDeposit(Categorizer.normalize(l.name))

    private fun tokens(name: String): Set<String> =
        Categorizer.normalize(name).split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }.toSet()

    fun fold(lines: List<Line>): List<Folded?> {
        val price = lines.map { it.priceCents }.toLongArray()
        val disc = LongArray(lines.size)
        val removed = BooleanArray(lines.size)

        // 1) storno: záporný riadok = rovnaký názov a suma ako skorší produkt → zrušiť oba
        lines.forEachIndexed { i, l ->
            if (l.priceCents >= 0 || isDeposit(Categorizer.normalize(l.name))) return@forEachIndexed
            val n = Categorizer.normalize(l.name)
            val j = (i - 1 downTo 0).firstOrNull {
                !removed[it] && lines[it].priceCents == -l.priceCents && Categorizer.normalize(lines[it].name) == n
            }
            if (j != null) { removed[i] = true; removed[j] = true }
        }

        fun spread(amount: Long) {   // amount > 0, rozpočítať pomerne podľa ceny produktov
            val idx = lines.indices.filter { !removed[it] && isProduct(lines[it]) && price[it] > 0 }
            val base = idx.sumOf { price[it] }
            if (idx.isEmpty() || base <= 0) return
            var left = amount
            idx.forEachIndexed { k, i ->
                val part = if (k == idx.lastIndex) left else minOf(left, amount * price[i] / base)
                price[i] -= part; disc[i] += part; left -= part
            }
        }

        // 2)–4) zľavy
        lines.forEachIndexed { i, l ->
            if (removed[i] || !isDiscountLine(l)) return@forEachIndexed
            val amount = abs(l.priceCents)
            val n = Categorizer.normalize(l.name)
            removed[i] = true
            if (receiptWide.any { n.contains(it) }) { spread(amount); return@forEachIndexed }

            // a) produkt, ktorého názov je v riadku zľavy
            val dt = tokens(l.name) - discountWords
            val byName = if (dt.isEmpty()) null else lines.indices
                .filter { !removed[it] && isProduct(lines[it]) && price[it] >= amount }
                .map { j ->
                    val pt = tokens(lines[j].name)
                    j to (if (pt.isEmpty()) 0.0 else pt.intersect(dt).size.toDouble() / pt.size)
                }
                .filter { it.second >= 0.6 }
                .maxWithOrNull(compareBy<Pair<Int, Double>> { it.second }.thenBy { -abs(it.first - i) })
                ?.first
            // b) produkt hneď nad zľavou (zálohy medzi nimi sa preskočia)
            val adjacent = (i - 1 downTo 0)
                .firstOrNull {
                    val skip = (lines[it].priceCents > 0 && isDeposit(Categorizer.normalize(lines[it].name))) ||
                        (removed[it] && isDiscountLine(lines[it]))      // zálohy a ďalšie zľavy toho istého produktu
                    !skip
                }
                ?.takeIf { !removed[it] && isProduct(lines[it]) && price[it] >= amount }
            val target = byName ?: adjacent
            if (target != null) { price[target] -= amount; disc[target] += amount } else spread(amount)
        }

        // ak sa nedalo nič priradiť (žiadny produkt), riadky ponecháme
        val anyProduct = lines.indices.any { !removed[it] && isProduct(lines[it]) }
        return lines.indices.map { i ->
            if (removed[i] && anyProduct) null
            else Folded(if (removed[i]) lines[i].priceCents else price[i], disc[i])
        }
    }
}
