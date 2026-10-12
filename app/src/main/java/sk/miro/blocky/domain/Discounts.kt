package sk.miro.blocky.domain

import kotlin.math.abs

/**
 * Priradenie zliav k produktom.
 *
 * Na bločku eKasa je zľava samostatný záporný riadok, zvyčajne hneď pod produktom, ku ktorému patrí
 * (napr. „Maslo 2,99“ a pod ním „Zľava −0,50“). Zľavu pripočítame k tomuto produktu:
 * produkt bude mať čistú cenu 2,49 € a zapamätanú zľavu 0,50 €; riadok zľavy zmizne.
 *
 * - Zľava na celý nákup („zľava na nákup / doklad“) alebo zľava väčšia ako produkt nad ňou
 *   sa rozpočíta pomerne na všetky produkty.
 * - Vrátený tovar (typ „V“) a zálohy sa nepovažujú za zľavu.
 */
object DiscountFolder {

    data class Line(val name: String, val priceCents: Long, val itemType: String?)

    /** Výsledok pre každý pôvodný riadok: null = riadok zľavy, ktorý sa zlúčil a má sa odstrániť. */
    data class Folded(val priceCents: Long, val discountCents: Long)

    private val receiptWide = listOf("nakup", "doklad", "celkov", "na ucet", "na blocek", "na blocok")

    private fun isDeposit(n: String) =
        listOf("zaloh", "zal.", "zal ", "vratn", "vykup obal", "vratenie obal").any { " $n ".contains(" $it") }

    fun isDiscountLine(l: Line): Boolean {
        if (l.priceCents >= 0) return false
        val t = l.itemType?.uppercase()
        if (t == "V" || t == "VO" || t == "O") return false          // vrátenie tovaru / zálohy
        val n = Categorizer.normalize(l.name)
        if (isDeposit(n)) return false
        return true                                                  // záporný riadok = zľava
    }

    private fun isProduct(l: Line): Boolean =
        l.priceCents > 0 && !isDeposit(Categorizer.normalize(l.name))

    fun fold(lines: List<Line>): List<Folded?> {
        val price = lines.map { it.priceCents }.toLongArray()
        val disc = LongArray(lines.size)
        val removed = BooleanArray(lines.size)

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

        lines.forEachIndexed { i, l ->
            if (!isDiscountLine(l)) return@forEachIndexed
            val amount = abs(l.priceCents)
            val n = Categorizer.normalize(l.name)
            removed[i] = true
            val target = (i - 1 downTo 0).firstOrNull { !removed[it] && isProduct(lines[it]) }
            when {
                receiptWide.any { n.contains(it) } || target == null -> spread(amount)
                price[target] >= amount -> { price[target] -= amount; disc[target] += amount }
                else -> spread(amount)
            }
        }
        // ak sa zľava nedala nikam priradiť (žiadny produkt), riadok ponecháme
        val anyProduct = lines.any { isProduct(it) }
        return lines.indices.map { i ->
            if (removed[i] && anyProduct) null
            else Folded(if (removed[i]) lines[i].priceCents else price[i], disc[i])
        }
    }
}
