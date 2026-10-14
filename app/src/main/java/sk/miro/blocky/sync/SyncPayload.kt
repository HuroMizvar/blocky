package sk.miro.blocky.sync

import org.json.JSONArray
import org.json.JSONObject
import sk.miro.blocky.data.ItemEntity
import sk.miro.blocky.data.ReceiptEntity

/** Bloček s položkami ⇄ JSON (pred zašifrovaním). Pôvodný JSON z eKasa sa neposiela. */
object SyncPayload {
    data class Receipt(val receipt: ReceiptEntity, val items: List<ItemEntity>)

    fun toJson(r: ReceiptEntity, items: List<ItemEntity>): String = JSONObject().apply {
        put("v", 1)
        put("receiptId", r.receiptId)
        put("merchantName", r.merchantName)
        putN("merchantIco", r.merchantIco)
        putN("storeAddress", r.storeAddress)
        put("issuedAt", r.issuedAt)
        put("totalCents", r.totalCents)
        put("source", r.source)
        putN("merchantKey", r.merchantKey)
        putN("merchantType", r.merchantType)
        putN("addedBy", r.addedBy)
        putN("addedByUid", r.addedByUid)
        put("editedAt", r.editedAt)
        put("items", JSONArray().apply {
            items.sortedBy { it.id }.forEach { i ->
                put(JSONObject().apply {
                    put("name", i.name)
                    put("normName", i.normName)
                    put("quantity", i.quantity)
                    put("priceCents", i.priceCents)
                    putN("vatRate", i.vatRate)
                    putN("itemType", i.itemType)
                    put("category", i.category)
                    put("categoryManual", i.categoryManual)
                    put("discountCents", i.discountCents)
                    put("excluded", i.excluded)
                    putN("origQuantity", i.origQuantity)
                    putN("origPriceCents", i.origPriceCents)
                    putN("origDiscountCents", i.origDiscountCents)
                })
            }
        })
    }.toString()

    /** [syncId] = ID dokumentu na serveri. Vráti nový (neuložený) bloček a položky. */
    fun fromJson(json: String, syncId: String?): Receipt {
        val o = JSONObject(json)
        val r = ReceiptEntity(
            receiptId = o.getString("receiptId"),
            merchantName = o.getString("merchantName"),
            merchantIco = o.str("merchantIco"),
            storeAddress = o.str("storeAddress"),
            issuedAt = o.getLong("issuedAt"),
            totalCents = o.getLong("totalCents"),
            rawJson = null,
            source = o.getString("source"),
            merchantKey = o.str("merchantKey"),
            merchantType = o.str("merchantType"),
            syncId = syncId,
            addedBy = o.str("addedBy"),
            addedByUid = o.str("addedByUid"),
            dirty = false,
            editedAt = o.optLong("editedAt", 0)
        )
        val arr = o.getJSONArray("items")
        val items = (0 until arr.length()).map { k ->
            val i = arr.getJSONObject(k)
            ItemEntity(
                receiptDbId = 0,
                name = i.getString("name"),
                normName = i.getString("normName"),
                quantity = i.getDouble("quantity"),
                priceCents = i.getLong("priceCents"),
                vatRate = if (i.isNull("vatRate")) null else i.getDouble("vatRate"),
                itemType = i.str("itemType"),
                category = i.getString("category"),
                categoryManual = i.optBoolean("categoryManual", false),
                discountCents = i.optLong("discountCents", 0),
                excluded = i.optBoolean("excluded", false),
                origQuantity = if (i.isNull("origQuantity")) null else i.getDouble("origQuantity"),
                origPriceCents = if (i.isNull("origPriceCents")) null else i.getLong("origPriceCents"),
                origDiscountCents = if (i.isNull("origDiscountCents")) null else i.getLong("origDiscountCents")
            )
        }
        return Receipt(r, items)
    }

    /** Nastavenie (pravidlo kategórie, typ prevádzky, rozpočet, profil) – [value] null = zmazané. */
    data class Kv(val kind: String, val key: String, val value: String?, val at: Long)

    fun kvToJson(kv: Kv): String = JSONObject().apply {
        put("kind", kv.kind); put("key", kv.key); putN("value", kv.value); put("at", kv.at)
    }.toString()

    fun kvFromJson(json: String): Kv {
        val o = JSONObject(json)
        return Kv(o.getString("kind"), o.getString("key"), o.str("value"), o.optLong("at", 0))
    }

    private fun JSONObject.putN(k: String, v: Any?) { put(k, v ?: JSONObject.NULL) }
    private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
}
