package sk.miro.blocky.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

/**
 * Import e-bločkov (PDF, obrázok, text) – napr. z e-mailu cez „Zdieľať → Bločky“ alebo výberom súboru.
 * Všetko prebieha v telefóne: stránka PDF sa vykreslí a QR kód eKasa sa prečíta pomocou ZXing.
 * Súbor sa nikam neodosiela ani neukladá.
 */
object ReceiptImport {

    private const val MAX_PAGES = 5
    private const val MAX_PIXELS = 6_000_000
    private val ONLINE = Regex("O-[0-9A-Fa-f]{32}")

    /** Kódy bločkov nájdené v texte (napr. „UID: O-E1E3…“ v tele e-mailu). */
    fun codesInText(text: String?): List<String> =
        if (text.isNullOrBlank()) emptyList()
        else ONLINE.findAll(text).map { it.value.uppercase() }.distinct().toList()

    /** Nájde kódy bločkov eKasa v súbore (PDF alebo obrázok). */
    suspend fun codesInUri(context: Context, uri: Uri, mime: String?): List<String> = withContext(Dispatchers.Default) {
        val type = mime ?: context.contentResolver.getType(uri) ?: ""
        val found = LinkedHashSet<String>()
        runCatching {
            when {
                type == "application/pdf" || uri.toString().endsWith(".pdf", true) -> found += fromPdf(context, uri)
                type.startsWith("image/") -> found += fromImage(context, uri)
                type.startsWith("text/") -> context.contentResolver.openInputStream(uri)?.use {
                    found += codesInText(String(it.readBytes(), Charsets.UTF_8))
                }
                else -> { // neznámy typ – skúsiť obrázok, potom PDF
                    val img = runCatching { fromImage(context, uri) }.getOrDefault(emptyList())
                    found += img.ifEmpty { runCatching { fromPdf(context, uri) }.getOrDefault(emptyList()) }
                }
            }
        }
        found.toList()
    }

    private fun fromPdf(context: Context, uri: Uri): List<String> {
        // PdfRenderer potrebuje súbor s možnosťou presunu – obsah z e-mailu skopírujeme do dočasného súboru
        val tmp = File.createTempFile("import", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: return emptyList()
            // niektoré PDF majú identifikátor dokladu priamo v texte
            val inText = codesInText(tmp.readBytes().toString(Charsets.ISO_8859_1))
            val out = LinkedHashSet<String>()
            val fd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdf = PdfRenderer(fd)   // zatvorí aj fd
            try {
                for (i in 0 until minOf(pdf.pageCount, MAX_PAGES)) {
                    val page = pdf.openPage(i)
                    try {
                        for (scale in listOf(2.0, 3.0, 1.0)) {
                            val s = minOf(scale, sqrt(MAX_PIXELS.toDouble() / (page.width.toDouble() * page.height)))
                            val w = (page.width * s).toInt().coerceAtLeast(1)
                            val h = (page.height * s).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)   // PDF má priehľadné pozadie
                            page.render(bmp, null, Matrix().apply { setScale(w / page.width.toFloat(), h / page.height.toFloat()) },
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val codes = decode(bmp)
                            bmp.recycle()
                            if (codes.isNotEmpty()) { out += codes; break }
                        }
                    } finally { page.close() }
                }
            } finally { pdf.close() }
            return (out + inText).toList()
        } finally {
            tmp.delete()
        }
    }

    private fun fromImage(context: Context, uri: Uri): List<String> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return emptyList()
        val out = LinkedHashSet<String>()
        // najprv zmenšené (rýchle), potom v plnom rozlíšení (malé QR na veľkej fotke)
        for (target in listOf(2000, 4000)) {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return emptyList()
            out += decode(bmp)
            bmp.recycle()
            if (out.isNotEmpty() || sample == 1) break
        }
        return out.toList()
    }

    /** Všetky QR kódy na obrázku, ktoré sú bločkami eKasa. */
    private fun decode(bmp: Bitmap): List<String> {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, px)))
        val results = runCatching { GenericMultipleBarcodeReader(MultiFormatReader()).decodeMultiple(bitmap, hints).toList() }
            .getOrDefault(emptyList())
        return results.mapNotNull { r -> r.text?.takeIf { EkasaQr.parse(it) != null }?.trim() }.distinct()
    }
}
