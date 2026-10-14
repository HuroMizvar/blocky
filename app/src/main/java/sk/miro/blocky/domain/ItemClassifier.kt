package sk.miro.blocky.domain

import kotlin.math.exp
import kotlin.math.ln

/**
 * Vstavaná AI na zaraďovanie položiek – beží len v telefóne, nič neodosiela.
 *
 * Naive Bayes nad príznakmi z názvu (slová, predpony slov, „kostra“ bez samohlások, trojice znakov),
 * vďaka čomu rozumie aj skratkám z bločkov („MLK POLOT.“, „KUR.PRS.REZNE“). Model je natrénovaný
 * na slovníku slovenských produktov (tools/ai/train.py) a v telefóne sa ďalej učí z opráv používateľa.
 *
 * Funkcie [normalize], [tokens] a [features] sa MUSIA zhodovať s tools/ai/train.py.
 */
class ItemClassifier private constructor(
    private val classes: List<Category>,
    private val counts: HashMap<String, IntArray>
) {
    data class Prediction(val category: Category, val confidence: Double)

    private var totals = IntArray(classes.size)
    private var vocab = 0

    init { recompute() }

    private fun recompute() {
        val t = IntArray(classes.size)
        for (v in counts.values) for (i in v.indices) t[i] += v[i]
        totals = t
        vocab = counts.size
    }

    /** Doučí model z opravených položiek (názov → kategória). Volá sa pri štarte a po oprave. */
    fun learn(examples: Map<String, Category>, weight: Int = LEARN_WEIGHT) {
        for ((name, cat) in examples) {
            val ci = classes.indexOf(cat)
            if (ci < 0) continue
            for (f in features(name)) counts.getOrPut(f) { IntArray(classes.size) }[ci] += weight
        }
        recompute()
    }

    /** Navrhne kategóriu, alebo null, ak si nie je istá (vtedy rozhodnú pravidlá / používateľ). */
    fun predict(name: String, threshold: Double = THRESHOLD): Prediction? {
        val feats = features(name)
        val c = classes.size
        val score = DoubleArray(c)
        var known = 0
        for (f in feats) {
            val v = counts[f] ?: continue
            known++
            for (i in 0 until c) score[i] += ln((v[i] + ALPHA) / (totals[i] + ALPHA * vocab))
        }
        if (known == 0) return null
        var best = 0
        for (i in 1 until c) if (score[i] > score[best]) best = i
        val m = score[best]
        var z = 0.0
        for (i in 0 until c) z += exp(score[i] - m)
        val conf = 1.0 / z
        // podpora: aspoň jeden výrazný príznak (slovo, dlhšia predpona, kostra) musí jasne ukazovať na víťaza
        var support = 0
        for (f in feats) {
            if (f[0] == 'g' || f[0] == 'b' || (f[0] == 'p' && f.length < 6)) continue
            val v = counts[f] ?: continue
            val t = v.sum()
            if (t >= 3 && v[best].toDouble() / t >= 0.6) support++
        }
        if (support == 0 || conf < threshold) return null
        return Prediction(classes[best], conf)
    }

    companion object {
        const val ALPHA = 0.3
        const val THRESHOLD = 0.6
        const val LEARN_WEIGHT = 5
        /** zvýšiť pri novom modeli → existujúce položky sa preradia */
        const val MODEL_VERSION = 1

        private val STOP = setOf("ks", "kg", "g", "l", "ml", "bal", "x", "pet", "plech", "fl", "cca", "vaz", "sk", "pack",
            "the", "and", "s", "a", "z", "na", "do", "pre", "v", "so", "od", "zo", "k")
        private const val VOWELS = "aeiouy"
        private val SPLIT = Regex("[ .,%]+")

        private val LETTER_DIGIT = Regex("([a-z])([0-9])")
        private val DIGIT_LETTER = Regex("([0-9])([a-z])")

        fun tokens(normalized: String): List<String> =
            normalized.replace(LETTER_DIGIT, "$1 $2").replace(DIGIT_LETTER, "$1 $2")
                .split(SPLIT).filter { t -> t.length >= 2 && t.none { it.isDigit() } && t !in STOP }

        fun features(name: String): Set<String> {
            val toks = tokens(Categorizer.normalize(name))
            val f = LinkedHashSet<String>()
            for (t in toks) {
                f += "w:$t"
                for (k in 3..minOf(6, t.length)) f += "p:" + t.substring(0, k)
                val cs = t.substring(0, 1) + t.substring(1).filter { it !in VOWELS }
                if (cs.length >= 2) f += "c:" + cs.take(4)
                val b = "_${t}_"
                for (i in 0 until b.length - 2) f += "g:" + b.substring(i, i + 3)
            }
            for (i in 0 until toks.size - 1) f += "b:" + toks[i].take(4) + "_" + toks[i + 1].take(4)
            return f
        }

        /** Načíta model zo súboru item_model.tsv (assets). */
        fun parse(text: String): ItemClassifier {
            val lines = text.lineSequence().iterator()
            require(lines.next().startsWith("BLOCKY-NB")) { "Neplatný model" }
            val classes = lines.next().split('\t').map { Category.fromName(it) }
            lines.next() // počty dokladov (prior je rovnomerný)
            val counts = HashMap<String, IntArray>(32_768)
            while (lines.hasNext()) {
                val l = lines.next()
                if (l.isEmpty()) continue
                val tab = l.indexOf('\t')
                val arr = IntArray(classes.size)
                for (p in l.substring(tab + 1).split(',')) {
                    val c = p.indexOf(':')
                    arr[p.substring(0, c).toInt()] = p.substring(c + 1).toInt()
                }
                counts[l.substring(0, tab)] = arr
            }
            return ItemClassifier(classes, counts)
        }
    }
}
