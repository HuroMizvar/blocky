package sk.miro.blocky.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import sk.miro.blocky.domain.ReceiptCopy
import java.io.File
import java.io.OutputStream

/** Zostavenie elektronickej kópie bločku a jej uloženie ako PDF. */
object ReceiptCopies {

    fun build(r: ReceiptEntity, items: List<ItemEntity>): ReceiptCopy =
        r.rawJson?.takeIf { it.isNotBlank() && r.source == "EKASA" }?.let { runCatching { fromEkasa(it) }.getOrNull()?.copy(issuedAt = r.issuedAt) }
            ?: fromStored(r, items)

    private fun JSONObject.s(k: String): String? = optString(k).trim().takeIf { it.isNotEmpty() && it != "null" }

    private fun address(o: JSONObject?): String? {
        o ?: return null
        val street = listOfNotNull(o.s("streetName"),
            listOfNotNull(o.s("propertyRegistrationNumber"), o.s("buildingNumber")).joinToString("/").ifBlank { null })
            .joinToString(" ")
        val town = listOfNotNull(o.s("postalCode"), o.s("municipality")).joinToString(" ")
        return listOf(street, town).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }
    }

    /** Úplná kópia z pôvodnej odpovede eKasa – položky presne ako na bločku (bez úprav v aplikácii). */
    fun fromEkasa(raw: String): ReceiptCopy? {
        val r = JSONObject(raw).optJSONObject("receipt") ?: return null
        val org = r.optJSONObject("organization")
        val unit = r.optJSONObject("unit")
        val arr = r.optJSONArray("items")
        val lines = buildList {
            if (arr != null) for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                add(ReceiptCopy.Line(
                    name = it.optString("name").trim().ifBlank { "Položka" },
                    quantity = it.optDouble("quantity", 1.0).let { q -> if (q.isNaN()) 1.0 else q },
                    totalCents = EkasaClient.toCents(it.optDouble("price", 0.0)),
                    vatRate = it.optDouble("vatRate").takeUnless { v -> v.isNaN() }
                ))
            }
        }
        val issued = EkasaClient.parseDate(r.s("issueDate") ?: r.s("createDate"))
        return ReceiptCopy(
            merchant = org?.s("name") ?: "Neznámy obchod",
            orgAddress = address(org),
            ico = r.s("ico") ?: org?.s("ico"),
            dic = r.s("dic") ?: org?.s("dic"),
            icDph = r.s("icDph") ?: org?.s("icDph"),
            unitName = unit?.s("name"),
            unitAddress = address(unit),
            cashRegisterCode = r.s("cashRegisterCode") ?: unit?.s("cashRegisterCode"),
            receiptNumber = r.s("receiptNumber"),
            issuedAt = issued,
            items = lines,
            totalCents = EkasaClient.toCents(r.optDouble("totalPrice", lines.sumOf { it.totalCents } / 100.0)),
            receiptId = r.s("receiptId"),
            okp = r.s("okp"),
            paragon = r.optBoolean("paragon", false),
            complete = true
        )
    }

    /** Kópia z uložených položiek – pôvodné množstvá a ceny (pred úpravami v aplikácii), zľavy zvlášť. */
    fun fromStored(r: ReceiptEntity, items: List<ItemEntity>): ReceiptCopy {
        val manual = r.source == "MANUAL"
        val sorted = items.sortedBy { it.id }
        val lines = sorted.map { i ->
            ReceiptCopy.Line(i.name, i.origQuantity ?: i.quantity,
                (i.origPriceCents ?: i.priceCents) + (i.origDiscountCents ?: i.discountCents), i.vatRate)
        }.toMutableList()
        val discount = sorted.sumOf { it.origDiscountCents ?: it.discountCents }
        if (discount != 0L) lines += ReceiptCopy.Line("Zľavy", 1.0, -discount, null)
        return ReceiptCopy(
            merchant = r.merchantName,
            orgAddress = r.storeAddress,
            ico = r.merchantIco,
            issuedAt = r.issuedAt,
            items = lines,
            totalCents = r.totalCents,
            receiptId = r.receiptId.takeIf { !manual && it.startsWith("O-") },
            manual = manual,
            complete = false
        )
    }

    fun qr(content: String, size: Int = 0): BitMatrix =
        QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))

    // ---------- PDF (80 mm pokladničný bloček) ----------

    private const val PAGE_W = 227          // 80 mm v bodoch
    private const val MARGIN = 14f
    private const val FONT = 8.2f
    private const val LINE_H = 10.6f
    private const val QR = 118f

    fun writePdf(copy: ReceiptCopy, out: OutputStream) {
        val rows = copy.rows()
        val footer = copy.footer()
        val qr = copy.qrContent?.let { qr(it) }
        val h = (MARGIN * 2 + (rows.size + footer.size) * LINE_H + (if (qr != null) QR + 14f else 4f)).toInt()
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, h, 1).create())
        val c = page.canvas
        c.drawColor(android.graphics.Color.WHITE)
        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textSize = FONT; color = android.graphics.Color.BLACK }
        val bold = Paint(normal).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        var y = MARGIN + FONT
        fun draw(list: List<ReceiptCopy.Row>) = list.forEach { row ->
            val p = if (row.bold) bold else normal
            val x = if (row.center) (PAGE_W - p.measureText(row.text)) / 2f else MARGIN
            c.drawText(row.text, x, y, p)
            y += LINE_H
        }
        draw(rows)
        if (qr != null) {
            y += 4f
            drawQr(c, qr, (PAGE_W - QR) / 2f, y, QR)
            y += QR + 10f
        }
        draw(footer)
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    private fun drawQr(c: Canvas, m: BitMatrix, x: Float, y: Float, size: Float) {
        val cell = size / m.width
        val p = Paint().apply { color = android.graphics.Color.BLACK; style = Paint.Style.FILL }
        for (i in 0 until m.width) for (j in 0 until m.height) if (m[i, j])
            c.drawRect(x + i * cell, y + j * cell, x + (i + 1) * cell + 0.2f, y + (j + 1) * cell + 0.2f, p)
    }

    /** Uloží PDF do Stiahnutých súborov (priečinok Bločky). Vráti adresu súboru alebo null (Android 8–9, chyba). */
    fun saveToDownloads(context: Context, copy: ReceiptCopy): Uri? =
        Downloads.save(context, copy.fileName(), "application/pdf") { writePdf(copy, it) }

    /** Súbor na zdieľanie (cache, sprístupnený cez FileProvider). */
    fun shareFile(context: Context, copy: ReceiptCopy): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, copy.fileName())
        f.outputStream().use { writePdf(copy, it) }
        return f
    }
}
