package sk.miro.blocky.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.miro.blocky.domain.Category
import sk.miro.blocky.domain.Categorizer
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed interface ScanResult {
    data class Saved(val receiptDbId: Long, val merchant: String, val totalCents: Long, val items: Int) : ScanResult
    data class Duplicate(val receiptId: String) : ScanResult
    data class Error(val message: String) : ScanResult
}

class Repository(private val context: Context) {
    private val dao = AppDatabase.get(context).dao()
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var endpoint: String
        get() = prefs.getString("endpoint", EkasaClient.DEFAULT_ENDPOINT) ?: EkasaClient.DEFAULT_ENDPOINT
        set(v) = prefs.edit().putString("endpoint", v.trim()).apply()

    /** Testovací režim: skenovanie nevolá eKasa, ale vráti náhodný nákup (uloží sa ako DEMO). */
    var mockMode: Boolean
        get() = prefs.getBoolean("mockMode", false)
        set(v) = prefs.edit().putBoolean("mockMode", v).apply()

    val demoCount = dao.countBySource(DemoData.SOURCE)
    val receipts = dao.receipts()
    val itemRows = dao.allItemRows()
    val budgets = dao.budgets()
    fun receipt(id: Long) = dao.receiptWithItems(id)

    suspend fun scan(rawQr: String): ScanResult {
        if (mockMode) return save(DemoData.mockReceipt(rawQr), DemoData.SOURCE)
        val qr = EkasaQr.parse(rawQr)
            ?: return ScanResult.Error("QR kód nie je bloček eKasa.\nObsah: ${rawQr.take(80)}")
        if (qr is EkasaQr.Online && dao.countByReceiptId(qr.receiptId) > 0) return ScanResult.Duplicate(qr.receiptId)
        val parsed = try {
            EkasaClient(endpoint).fetch(qr)
        } catch (e: EkasaException) {
            return ScanResult.Error(e.message ?: "Chyba eKasa")
        } catch (e: Exception) {
            return ScanResult.Error("Chyba pripojenia: ${e.message ?: e.javaClass.simpleName}")
        }
        if (dao.countByReceiptId(parsed.receiptId) > 0) return ScanResult.Duplicate(parsed.receiptId)
        return save(parsed, "EKASA")
    }

    suspend fun addManual(merchant: String, totalCents: Long, category: Category, issuedAt: Long): ScanResult =
        save(
            ParsedReceipt(
                receiptId = "MANUAL-${System.currentTimeMillis()}",
                merchantName = merchant.ifBlank { "Ručný záznam" },
                merchantIco = null, storeAddress = null, issuedAt = issuedAt, totalCents = totalCents,
                items = listOf(ParsedItem(merchant.ifBlank { category.label }, 1.0, totalCents, null, "MANUAL")),
                rawJson = ""
            ),
            "MANUAL", forced = category
        )

    private suspend fun save(p: ParsedReceipt, source: String, forced: Category? = null): ScanResult {
        val userRules = dao.rules().associate { it.normName to Category.fromName(it.category) }
        val receipt = ReceiptEntity(
            receiptId = p.receiptId, merchantName = p.merchantName, merchantIco = p.merchantIco,
            storeAddress = p.storeAddress, issuedAt = p.issuedAt, totalCents = p.totalCents,
            rawJson = p.rawJson.ifBlank { null }, source = source
        )
        val items = p.items.map {
            val norm = Categorizer.normalize(it.name)
            ItemEntity(
                receiptDbId = 0, name = it.name, normName = norm, quantity = it.quantity,
                priceCents = it.priceCents, vatRate = it.vatRate, itemType = it.itemType,
                category = (forced ?: Categorizer.categorize(it.name, p.merchantName, it.priceCents, userRules)).name,
                categoryManual = forced != null
            )
        }
        val id = dao.insertReceiptWithItems(receipt, items)
        return ScanResult.Saved(id, p.merchantName, p.totalCents, items.size)
    }

    /** Zmení kategóriu položky; ak [applyToAll], zapamätá si pravidlo pre všetky rovnaké názvy. */
    suspend fun recategorize(item: ItemEntity, category: Category, applyToAll: Boolean) {
        if (applyToAll) {
            dao.upsertRule(CategoryRuleEntity(item.normName, category.name))
            dao.setCategoryForName(item.normName, category.name)
        } else dao.setItemCategory(item.id, category.name)
    }

    suspend fun deleteReceipt(id: Long) = dao.deleteReceipt(id)

    /** Nahradí demo bločky novou sadou (3 mesiace + aktuálny). Vráti počet bločkov. */
    suspend fun seedDemo(): Int {
        dao.deleteBySource(DemoData.SOURCE)
        val list = DemoData.generate()
        list.forEach { save(it, DemoData.SOURCE) }
        return list.size
    }

    suspend fun clearDemo(): Int = dao.deleteBySource(DemoData.SOURCE)

    /** Import surovej JSON odpovede eKasa (napr. skopírovanej z webu) – na ladenie parsera. */
    suspend fun importJson(text: String): ScanResult {
        val parsed = try {
            EkasaClient.parseResponse(text.trim())
        } catch (e: EkasaException) {
            return ScanResult.Error(e.message ?: "Neplatný JSON")
        }
        if (dao.countByReceiptId(parsed.receiptId) > 0) return ScanResult.Duplicate(parsed.receiptId)
        return save(parsed, "EKASA")
    }
    suspend fun setBudget(c: Category, cents: Long?) =
        if (cents == null || cents <= 0) dao.deleteBudget(c.name) else dao.upsertBudget(BudgetEntity(c.name, cents))

    /** Export všetkých položiek do CSV (UTF-8 s BOM, oddeľovač ";" – otvorí sa priamo v Exceli). */
    suspend fun exportCsv(rows: List<ItemRow>): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, "blocky_export.csv")
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        f.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿datum;obchod;polozka;kategoria;mnozstvo;cena_eur\n")
            for (r in rows) {
                val d = Instant.ofEpochMilli(r.issuedAt).atZone(ZoneId.systemDefault()).format(fmt)
                val price = "%.2f".format(java.util.Locale.ROOT, r.priceCents / 100.0).replace('.', ',')
                val qty = r.quantity.toString().replace('.', ',')
                w.write("$d;${q(r.merchantName)};${q(r.name)};${q(Category.fromName(r.category).label)};$qty;$price\n")
            }
        }
        f
    }
}
