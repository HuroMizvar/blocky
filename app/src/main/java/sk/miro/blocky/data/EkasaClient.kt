package sk.miro.blocky.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/** Obsah QR kódu z bločku eKasa. */
sealed interface EkasaQr {
    /** Online doklad – QR obsahuje priamo ID dokladu "O-XXXXXXXX...". */
    data class Online(val receiptId: String) : EkasaQr

    /** Offline doklad – OKP:kódPokladne:dátum(yyMMddHHmmss):poradovéČíslo:suma */
    data class Offline(
        val okp: String,
        val cashRegisterCode: String,
        val issueDate: LocalDateTime,
        val receiptNumber: Long,
        val totalAmount: String
    ) : EkasaQr

    companion object {
        private val ONLINE = Regex("^O-[0-9A-Fa-f]{32}$")

        fun parse(raw: String): EkasaQr? {
            val s = raw.trim()
            if (ONLINE.matches(s)) return Online(s.uppercase())
            // Niektoré QR obsahujú URL s ID dokladu
            Regex("O-[0-9A-Fa-f]{32}").find(s)?.let { return Online(it.value.uppercase()) }

            val parts = s.split(":")
            if (parts.size == 5) {
                val date = runCatching {
                    LocalDateTime.parse(parts[2], DateTimeFormatter.ofPattern("yyMMddHHmmss"))
                }.getOrNull() ?: return null
                val num = parts[3].toLongOrNull() ?: return null
                return Offline(parts[0], parts[1], date, num, parts[4])
            }
            return null
        }
    }
}

data class ParsedReceipt(
    val receiptId: String,
    val merchantName: String,
    val merchantIco: String?,
    val storeAddress: String?,
    val issuedAt: Long,
    val totalCents: Long,
    val items: List<ParsedItem>,
    val rawJson: String
)

data class ParsedItem(
    val name: String,
    val quantity: Double,
    val priceCents: Long,
    val vatRate: Double?,
    val itemType: String?
)

class EkasaException(message: String) : Exception(message)

/**
 * Verejné rozhranie portálu "Overenie dokladu" Finančnej správy SR.
 * Endpoint je neoficiálne zdokumentovaný (rovnaký volá web ekasa.financnasprava.sk),
 * preto je URL v nastaveniach aplikácie meniteľná.
 */
class EkasaClient(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    suspend fun fetch(qr: EkasaQr): ParsedReceipt = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            when (qr) {
                is EkasaQr.Online -> put("receiptId", qr.receiptId)
                is EkasaQr.Offline -> {
                    put("okp", qr.okp)
                    put("cashRegisterCode", qr.cashRegisterCode)
                    put("issueDateFormatted", qr.issueDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")))
                    put("receiptNumber", qr.receiptNumber)
                    put("totalAmount", qr.totalAmount.toDoubleOrNull() ?: 0.0)
                }
            }
        }.toString()

        val req = Request.Builder()
            .url(endpoint)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw EkasaException("Server eKasa vrátil chybu ${resp.code}.")
            parseResponse(text)
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://ekasa.financnasprava.sk/mdu/api/v1/opd/receipt/find"

        private val DATE_PATTERNS = listOf(
            "dd.MM.yyyy HH:mm:ss", "d.M.yyyy HH:mm:ss", "dd.MM.yyyy HH:mm", "yyyy-MM-dd'T'HH:mm:ss"
        ).map { DateTimeFormatter.ofPattern(it) }

        fun toCents(v: Double): Long = (v * 100).roundToLong()

        fun parseDate(s: String?): Long {
            if (s.isNullOrBlank()) return System.currentTimeMillis()
            val clean = s.trim().substringBefore(".000").substringBefore("+").removeSuffix("Z")
            for (f in DATE_PATTERNS) {
                val dt = runCatching { LocalDateTime.parse(clean, f) }.getOrNull()
                if (dt != null) return dt.atZone(ZoneId.of("Europe/Bratislava")).toInstant().toEpochMilli()
            }
            return System.currentTimeMillis()
        }

        fun parseResponse(text: String): ParsedReceipt {
            val root = runCatching { JSONObject(text) }.getOrElse {
                throw EkasaException("Neplatná odpoveď servera eKasa.")
            }
            val rv = root.optInt("returnValue", 0)
            val r = root.optJSONObject("receipt")
            if (rv != 0 || r == null) {
                throw EkasaException(
                    if (rv == -1) "Doklad sa v systéme eKasa nenašiel (offline doklady sa objavia s oneskorením)."
                    else "Doklad sa nepodarilo načítať (kód $rv)."
                )
            }
            val org = r.optJSONObject("organization")
            val unit = r.optJSONObject("unit")
            val merchant = org?.optString("name").orEmpty().ifBlank { "Neznámy obchod" }
            val address = unit?.let {
                listOf(it.optString("streetName"), it.optString("buildingNumber"), it.optString("municipality"))
                    .filter { p -> p.isNotBlank() && p != "null" }.joinToString(" ")
            }?.ifBlank { null }

            val itemsJson = r.optJSONArray("items")
            val items = buildList {
                if (itemsJson != null) for (i in 0 until itemsJson.length()) {
                    val it = itemsJson.getJSONObject(i)
                    add(
                        ParsedItem(
                            name = it.optString("name").trim().ifBlank { "Položka" },
                            quantity = it.optDouble("quantity", 1.0).let { q -> if (q.isNaN()) 1.0 else q },
                            priceCents = toCents(it.optDouble("price", 0.0)),
                            vatRate = it.optDouble("vatRate").takeUnless { v -> v.isNaN() },
                            itemType = it.optString("itemType").ifBlank { null }
                        )
                    )
                }
            }
            return ParsedReceipt(
                receiptId = r.optString("receiptId").ifBlank { "NOID-${System.currentTimeMillis()}" },
                merchantName = merchant,
                merchantIco = r.optString("ico").ifBlank { null },
                storeAddress = address,
                issuedAt = parseDate(r.optString("issueDate").ifBlank { r.optString("createDate") }),
                totalCents = toCents(r.optDouble("totalPrice", items.sumOf { it.priceCents } / 100.0)),
                items = items,
                rawJson = text
            )
        }
    }
}
