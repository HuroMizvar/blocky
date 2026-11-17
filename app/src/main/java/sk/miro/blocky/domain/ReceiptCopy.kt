package sk.miro.blocky.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Elektronická kópia bločku – údaje, z ktorých sa poskladá obrazovka aj PDF vo vzhľade papierového bločku.
 * Údaje pochádzajú z eKasa (pôvodná odpoveď servera), pri starších / zdieľaných bločkoch z uložených položiek.
 * Nie je to originál dokladu – pravosť sa overí cez [receiptId] v systéme eKasa.
 */
data class ReceiptCopy(
    val merchant: String,
    val orgAddress: String? = null,
    val ico: String? = null,
    val dic: String? = null,
    val icDph: String? = null,
    val unitName: String? = null,
    val unitAddress: String? = null,
    val cashRegisterCode: String? = null,
    val receiptNumber: String? = null,
    val issuedAt: Long,
    val items: List<Line>,
    val totalCents: Long,
    /** ID dokladu eKasa (O-…); null pri ručne zadanom zázname */
    val receiptId: String? = null,
    val okp: String? = null,
    val paragon: Boolean = false,
    /** ručne zadaný záznam – nie je doklad z eKasa */
    val manual: Boolean = false,
    /** údaje sú z pôvodnej odpovede eKasa (úplná kópia); false = zložené z uložených položiek */
    val complete: Boolean = true
) {
    data class Line(val name: String, val quantity: Double, val totalCents: Long, val vatRate: Double?)

    data class VatRow(val rate: Double, val baseCents: Long, val vatCents: Long)

    /** Riadok výstupu: [text] má presne šírku bločku alebo menej. */
    data class Row(val text: String, val bold: Boolean = false, val center: Boolean = false, val big: Boolean = false)

    /** Rozpis DPH podľa sadzby – vypočítaný z položiek (cena položky je s DPH). */
    fun vat(): List<VatRow> = items.filter { it.vatRate != null && it.vatRate > 0 }
        .groupBy { it.vatRate!! }
        .map { (rate, l) ->
            val total = l.sumOf { it.totalCents }
            val vat = (total * rate / (100 + rate)).roundToLong()
            VatRow(rate, total - vat, vat)
        }
        .sortedByDescending { it.rate }

    /** Text bločku v stĺpci so šírkou [width] znakov (rovnaký pre obrazovku aj PDF). */
    fun rows(width: Int = WIDTH): List<Row> {
        val out = mutableListOf<Row>()
        fun center(s: String, bold: Boolean = false, big: Boolean = false) =
            wrap(s, width).forEach { out += Row(it, bold = bold, center = true, big = big) }
        fun text(s: String, bold: Boolean = false) = wrap(s, width).forEach { out += Row(it, bold = bold) }
        fun pair(l: String, r: String, bold: Boolean = false) {
            if (l.length + r.length + 1 <= width) out += Row(l + " ".repeat(width - l.length - r.length) + r, bold = bold)
            else { text(l, bold); out += Row(r.padStart(width), bold = bold) }
        }
        fun rule(c: Char = '-') { out += Row(c.toString().repeat(width)) }

        center(merchant, bold = true, big = true)
        orgAddress?.let { center(it) }
        listOfNotNull(ico?.let { "IČO: $it" }, dic?.let { "DIČ: $it" }).takeIf { it.isNotEmpty() }
            ?.let { center(it.joinToString("  ")) }
        icDph?.let { center("IČ DPH: $it") }
        if (unitName != null || unitAddress != null) {
            rule()
            unitName?.takeIf { it != merchant }?.let { center(it) }
            unitAddress?.takeIf { it != orgAddress }?.let { center(it) }
        }
        cashRegisterCode?.let { center("Kód pokladnice: $it") }
        rule('=')
        if (manual) { center("RUČNE ZADANÝ ZÁZNAM", bold = true); rule('=') }
        else if (paragon) { center("PARAGÓN", bold = true); rule('=') }

        for (it in items) {
            text(it.name)
            val qty = if (abs(it.quantity - 1.0) < 1e-9) "1" else num(it.quantity)
            val unit = if (it.quantity != 0.0) (it.totalCents / it.quantity).roundToLong() else it.totalCents
            val vat = it.vatRate?.let { r -> " " + (num(r) + "%").padStart(3) }.orEmpty()
            pair("  $qty x ${money(unit)}", money(it.totalCents) + vat)
        }
        rule('=')
        pair("SPOLU EUR", money(totalCents), bold = true)
        val vatRows = vat()
        if (vatRows.isNotEmpty()) {
            rule()
            pair("DPH", "základ    daň")
            vatRows.forEach { v -> pair("  ${num(v.rate)} %", "${money(v.baseCents).padStart(8)} ${money(v.vatCents).padStart(6)}") }
        }
        rule()
        pair("Dátum:", DATE.format(Instant.ofEpochMilli(issuedAt).atZone(ZoneId.systemDefault())))
        receiptNumber?.let { pair("Číslo dokladu:", it) }
        receiptId?.let { text("ID dokladu:"); text(it, bold = true) }
        okp?.let { text("OKP:"); text(it) }
        return out
    }

    /** Pätička kópie – vždy na konci, pod QR kódom. */
    fun footer(width: Int = WIDTH): List<Row> = buildList {
        add(Row("-".repeat(width)))
        wrap("ELEKTRONICKÁ KÓPIA Z APLIKÁCIE BLOČKY", width).forEach { add(Row(it, bold = true, center = true)) }
        val note = when {
            manual -> "Ručne zadaný záznam, nie je to doklad z eKasa."
            receiptId != null -> "Nie je to originál dokladu. Pravosť overíte podľa ID dokladu alebo QR kódu " +
                "v aplikácii eKasa alebo na ekasa.financnasprava.sk."
            else -> "Nie je to originál dokladu."
        }
        wrap(note, width).forEach { add(Row(it, center = true)) }
        if (!complete && !manual) wrap("Zostavené z uložených položiek; údaje o predajcovi môžu chýbať.", width)
            .forEach { add(Row(it, center = true)) }
    }

    /** Obsah QR kódu na overenie v eKasa (len doklady eKasa s ID). */
    val qrContent: String? get() = receiptId?.takeIf { !manual && it.startsWith("O-") }

    /** Názov súboru PDF, napr. „Blocek_Lidl_2026-11-05_1411.pdf“. */
    fun fileName(): String {
        val m = merchant.split(' ', ',').firstOrNull { it.length > 1 } ?: "blocek"
        val safe = java.text.Normalizer.normalize(m, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").replace(Regex("[^A-Za-z0-9-]"), "").take(20).ifBlank { "blocek" }
        return "Blocek_${safe}_" + FILE_DATE.format(Instant.ofEpochMilli(issuedAt).atZone(ZoneId.systemDefault())) + ".pdf"
    }

    companion object {
        /** znakov na riadok – ako 80 mm pokladničný bloček */
        const val WIDTH = 40
        private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        private val FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")

        fun money(c: Long): String {
            val s = if (c < 0) "-" else ""
            val a = abs(c)
            return "$s${a / 100},${(a % 100).toString().padStart(2, '0')}"
        }

        fun num(d: Double): String =
            if (d == Math.floor(d) && abs(d) < 1e12) d.toLong().toString()
            else "%.3f".format(java.util.Locale.ROOT, d).trimEnd('0').trimEnd('.').replace('.', ',')

        /** Zalomenie textu na riadky najviac [w] znakov (po slovách, dlhé slová sa rozdelia). */
        fun wrap(s: String, w: Int): List<String> {
            val out = mutableListOf<String>()
            for (para in s.split('\n')) {
                var line = ""
                for (word0 in para.trim().split(Regex("\\s+"))) {
                    var word = word0
                    while (word.length > w) {
                        if (line.isNotEmpty()) { out += line; line = "" }
                        out += word.take(w); word = word.drop(w)
                    }
                    line = when {
                        line.isEmpty() -> word
                        line.length + 1 + word.length <= w -> "$line $word"
                        else -> { out += line; word }
                    }
                }
                if (line.isNotEmpty() || para.isBlank()) out += line
            }
            return out
        }
    }
}
