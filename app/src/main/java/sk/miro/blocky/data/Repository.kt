package sk.miro.blocky.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.miro.blocky.domain.Analytics
import sk.miro.blocky.domain.Category
import sk.miro.blocky.domain.Categorizer
import sk.miro.blocky.domain.CustomCategories
import sk.miro.blocky.domain.DiscountFolder
import sk.miro.blocky.domain.HouseholdProfile
import sk.miro.blocky.domain.ItemEdit
import sk.miro.blocky.domain.MerchantResolver
import sk.miro.blocky.domain.MerchantType
import sk.miro.blocky.domain.Period
import sk.miro.blocky.domain.FixedExpense
import sk.miro.blocky.domain.FixedExpenses
import sk.miro.blocky.domain.I18n
import sk.miro.blocky.sync.HouseholdSync
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed interface ScanResult {
    data class Saved(val receiptDbId: Long, val merchant: String, val totalCents: Long, val items: Int) : ScanResult
    /** [existing] = už uložený bloček (ak ho vieme nájsť) */
    data class Duplicate(val receiptId: String, val existing: ReceiptEntity? = null) : ScanResult
    data class Error(val message: String, val retryable: Boolean = false) : ScanResult
}

/** Výsledok okamžitého prijatia skenu (bez čakania na eKasa). */
sealed interface EnqueueResult {
    /** Sken je uložený vo fronte; [key] = kľúč riadku v pending_scans na sledovanie stavu. */
    data class Accepted(val key: String) : EnqueueResult
    data class AlreadyHave(val existing: ReceiptEntity?) : EnqueueResult
    data class Invalid(val message: String) : EnqueueResult
}

private val DUP_FMT = DateTimeFormatter.ofPattern("d. M. yyyy 'o' HH:mm")

fun duplicateText(r: ReceiptEntity): String =
    (r.addedBy?.let { "Nahral(a) $it · " } ?: "") +
    "Nákup z ${Instant.ofEpochMilli(r.issuedAt).atZone(ZoneId.systemDefault()).format(DUP_FMT)} · " +
        "${r.merchantName} · ${Analytics.eur(r.totalCents)}"

class Repository(private val context: Context) : HouseholdSync.Hooks {
    companion object {
        const val MOCK_PREFIX = "MOCK#"
        /** pri exponenciálnom odstupe WorkManagera ≈ niekoľko dní pokusov */
        const val MAX_ATTEMPTS = 25
        /** zvýšiť, keď sa zmení logika kategorizácie → existujúce bločky sa preradia */
        const val CATEGORIZER_VERSION = 6
    }

    private val dao = AppDatabase.get(context).dao()
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Spoločná domácnosť (voliteľné). */
    val sync = HouseholdSync(context, dao, this)

    init {
        CustomCategories.load(prefs.getString("customCats", null))
        Period.payDay = prefs.getInt("p_payday", 1)
        FixedExpenses.set(FixedExpenses.listFromJson(prefs.getString("fixed", null)))
        I18n.english = prefs.getBoolean("lang_en", false)
        if (I18n.english) loadTranslations()
    }

    private fun loadTranslations() {
        runCatching { I18n.load(context.assets.open("i18n_en.tsv").bufferedReader().use { it.readText() }) }
    }

    /** farebná téma (ukladá sa len v tomto zariadení – každý člen domácnosti môže mať inú) */
    var theme: String
        get() = prefs.getString("theme", "PAPER") ?: "PAPER"
        set(v) = prefs.edit().putString("theme", v).apply()

    /** anglický jazyk aplikácie */
    var english: Boolean
        get() = prefs.getBoolean("lang_en", false)
        set(v) {
            if (v) loadTranslations()
            prefs.edit().putBoolean("lang_en", v).apply()
            I18n.english = v
        }

    // ---------- fixné výdavky ----------
    private fun persistFixed(l: List<FixedExpense>) {
        FixedExpenses.set(l)
        prefs.edit().putString("fixed", FixedExpenses.listToJson(l)).apply()
    }

    suspend fun saveFixed(e: FixedExpense) {
        val v = e.copy(at = System.currentTimeMillis())
        persistFixed(FixedExpenses.list.value.filter { it.id != v.id } + v)
        if (sync.enabled) { sync.queue("fixed", v.id, FixedExpenses.toJson(v)); sync.schedule() }
    }

    suspend fun deleteFixed(id: String) {
        persistFixed(FixedExpenses.list.value.filter { it.id != id })
        if (sync.enabled) { sync.queue("fixed", id, null); sync.schedule() }
    }

    override fun fixedJsons(): Map<String, String> = FixedExpenses.list.value.associate { it.id to FixedExpenses.toJson(it) }

    override fun applyFixed(id: String, json: String?) {
        val cur = FixedExpenses.list.value
        if (json == null) { persistFixed(cur.filter { it.id != id }); return }
        val e = FixedExpenses.fromJson(json) ?: return
        val local = cur.firstOrNull { it.id == id }
        if (local == null || e.at >= local.at) persistFixed(cur.filter { it.id != id } + e)
    }

    override fun customCategoryJsons(): Map<String, String> =
        CustomCategories.all.associate { it.slot.name to CustomCategories.defToJson(it) }

    /** mení sa pri zmene vlastných kategórií (aj zo synchronizácie domácnosti) */
    val customCatsVersion = kotlinx.coroutines.flow.MutableStateFlow(0)

    private fun persistCats() {
        prefs.edit().putString("customCats", CustomCategories.toJson()).apply()
        customCatsVersion.value++
    }

    /** Uloží a pošle do domácnosti jednu vlastnú kategóriu (null = zmazaná). */
    private suspend fun publishCustomCategory(slot: Category) {
        persistCats()
        if (sync.enabled) {
            sync.queue("customcat", slot.name, CustomCategories.def(slot)?.let { CustomCategories.defToJson(it) })
            sync.schedule()
        }
    }

    /** Nová vlastná kategória; null = už je 8 vlastných kategórií. */
    suspend fun createCustomCategory(label: String, icon: String, color: Int): Category? =
        CustomCategories.create(label, icon, color)?.also { publishCustomCategory(it) }

    /** Pre okamžitý výber v dialógu: vytvorí kategóriu hneď, odoslanie prebehne v [publishCreated]. */
    fun createCustomCategoryNow(label: String, icon: String, color: Int): Category? =
        CustomCategories.create(label, icon, color)?.also { persistCats() }

    suspend fun publishCreated(c: Category) = publishCustomCategory(c)

    suspend fun updateCustomCategory(c: Category, label: String, icon: String, color: Int) {
        CustomCategories.update(c, label, icon, color); publishCustomCategory(c)
    }

    /** Zmaže vlastnú kategóriu – jej položky sa presunú do „Iné“. */
    suspend fun deleteCustomCategory(c: Category) {
        dao.clearCategory(c.name); dao.deleteRulesFor(c.name); dao.deleteAiExamplesFor(c.name); dao.deleteBudget(c.name)
        CustomCategories.remove(c); publishCustomCategory(c)
        InternalAi.invalidate()
    }

    private suspend fun markCategoryDirty(c: Category) {
        if (sync.enabled) dao.markDirtyByCategory(c.name, System.currentTimeMillis())
    }

    /** Vlastná kategória prišla z domácnosti – zapracuje ju a vyrieši prípadný konflikt slotov. */
    override suspend fun applyCustomCategory(slotName: String, json: String?) {
        val slot = Category.entries.firstOrNull { it.name == slotName && it.isCustom } ?: return
        val local = CustomCategories.def(slot)
        if (json == null) {   // kategóriu niekto zmazal
            if (local != null) {
                dao.clearCategory(slot.name); dao.deleteRulesFor(slot.name); dao.deleteAiExamplesFor(slot.name); dao.deleteBudget(slot.name)
                CustomCategories.remove(slot); InternalAi.invalidate()
            }
            persistCats(); return
        }
        val remote = CustomCategories.parseDef(slotName, json) ?: return
        when (CustomCategories.resolve(local, remote)) {
            CustomCategories.Resolution.Accept, CustomCategories.Resolution.MergeLocalIntoRemote -> CustomCategories.put(remote)
            CustomCategories.Resolution.KeepLocal -> {
                if (sync.enabled && local != null) sync.queue("customcat", slot.name, CustomCategories.defToJson(local))
                persistCats(); return
            }
            CustomCategories.Resolution.RelocateLocal -> {
                val l = local!!
                val free = CustomCategories.freeSlot(except = slot)
                val target = free ?: Category.INE
                dao.moveCategory(slot.name, target.name)
                markCategoryDirty(target)
                if (free != null) {
                    CustomCategories.put(l.copy(slot = free))
                    if (sync.enabled) sync.queue("customcat", free.name, CustomCategories.defToJson(l.copy(slot = free)))
                }
                CustomCategories.put(remote)
            }
        }
        // rovnaký názov v inom slote → zlúčiť do staršej kategórie
        for (d in CustomCategories.all) {
            if (d.slot == slot || d.id == remote.id || !d.label.trim().equals(remote.label.trim(), ignoreCase = true)) continue
            if (remote.createdAt < d.createdAt || (remote.createdAt == d.createdAt && remote.id < d.id)) {
                dao.moveCategory(d.slot.name, slot.name)
                markCategoryDirty(slot)
                CustomCategories.remove(d.slot)
                if (sync.enabled) sync.queue("customcat", d.slot.name, null)
            }
        }
        InternalAi.invalidate()
        persistCats()
        if (sync.enabled) sync.schedule()
    }

    /** Zaznamená úpravu bločku na odoslanie ostatným členom. */
    private suspend fun touchItem(itemId: Long) {
        if (sync.enabled) { dao.markDirtyByItem(itemId, System.currentTimeMillis()); sync.schedule() }
    }

    override fun profileJson(): String = profile.let { p ->
        JSONObject().put("people", p.people).put("kids", p.kids).put("carer", p.carer).put("pets", p.pets)
            .put("car", p.car).put("note", p.note).put("payDay", p.payDay).toString()
    }

    override fun applyProfileJson(json: String) {
        val o = JSONObject(json)
        profile = HouseholdProfile(o.optInt("people"), o.optInt("kids"), o.optBoolean("carer"), o.optBoolean("pets"),
            o.optBoolean("car"), o.optString("note"), o.optInt("payDay", 1))
    }

    /** Uloží profil a pošle ho ostatným členom domácnosti. */
    suspend fun saveProfile(p: HouseholdProfile) {
        profile = p
        if (sync.enabled) { sync.queue("profile", "household", profileJson()); sync.schedule() }
    }

    var endpoint: String
        get() = prefs.getString("endpoint", EkasaClient.DEFAULT_ENDPOINT) ?: EkasaClient.DEFAULT_ENDPOINT
        set(v) = prefs.edit().putString("endpoint", v.trim()).apply()

    /** Testovací režim: skenovanie nevolá eKasa, ale vráti náhodný nákup (uloží sa ako DEMO). */
    var mockMode: Boolean
        get() = prefs.getBoolean("mockMode", false)
        set(v) = prefs.edit().putBoolean("mockMode", v).apply()

    /** Vstavaná AI na zaraďovanie položiek (len v telefóne, nič neodosiela). */
    var aiEnabled: Boolean
        get() = prefs.getBoolean("aiEnabled", true)
        set(v) = prefs.edit().putBoolean("aiEnabled", v).apply()

    /** AI telefónu (Gemini Nano) – má prednosť pred vstavanou AI, ak ju telefón má a používateľ ju zapne. */
    var phoneAiEnabled: Boolean
        get() = prefs.getBoolean("phoneAiEnabled", false)
        set(v) = prefs.edit().putBoolean("phoneAiEnabled", v).apply()

    private suspend fun aiFn(): ((String) -> Category?)? {
        if (!aiEnabled) return null
        val m = InternalAi.get(context, dao)
        return { name -> m.predict(name)?.category }
    }

    /** Zapne/vypne vstavanú AI a preradí existujúce bločky. */
    suspend fun setAi(on: Boolean) {
        aiEnabled = on
        recategorizeReceipts(dao.allReceiptsWithItems())
        prefs.edit().putString("aiVersion", aiVersionKey()).apply()
    }

    private fun aiVersionKey() = "${sk.miro.blocky.domain.ItemClassifier.MODEL_VERSION}:$aiEnabled"

    /** Kontrola aktualizácií na GitHube (posiela len bežnú požiadavku na stiahnutie, žiadne dáta). */
    var autoUpdate: Boolean
        get() = prefs.getBoolean("autoUpdate", true)
        set(v) = prefs.edit().putBoolean("autoUpdate", v).apply()

    var profile: HouseholdProfile
        get() = HouseholdProfile(
            people = prefs.getInt("p_people", 0), kids = prefs.getInt("p_kids", 0),
            carer = prefs.getBoolean("p_carer", false), pets = prefs.getBoolean("p_pets", false),
            car = prefs.getBoolean("p_car", false), note = prefs.getString("p_note", "") ?: "",
            payDay = prefs.getInt("p_payday", 1)
        )
        set(v) = prefs.edit().putInt("p_people", v.people).putInt("p_kids", v.kids)
            .putBoolean("p_carer", v.carer).putBoolean("p_pets", v.pets).putBoolean("p_car", v.car)
            .putString("p_note", v.note.take(1000)).putInt("p_payday", v.payDay.coerceIn(1, 31)).apply()
            .also { Period.payDay = v.payDay }

    val demoCount = dao.countBySource(DemoData.SOURCE)
    val unknownMerchants = dao.unknownMerchants()
    fun observeReceipt(id: Long) = dao.observeReceipt(id)
    val receipts = dao.receipts()
    val receiptList = dao.receiptList()
    suspend fun setExcluded(itemId: Long, excluded: Boolean) { dao.setExcluded(itemId, excluded); touchItem(itemId) }

    /** Zmení množstvo položky (napr. na bločku 2 kávy, platil som 1). Cena a zľava sa prepočítajú pomerne. */
    suspend fun setQuantity(itemId: Long, qty: Double) {
        val item = dao.itemById(itemId) ?: return
        val q = ItemEdit.quantity(item.quantity, item.priceCents, item.discountCents,
            item.origQuantity, item.origPriceCents, item.origDiscountCents, qty)
        dao.setItemQuantity(itemId, q.quantity, q.priceCents, q.discountCents, q.origQuantity, q.origPriceCents, q.origDiscountCents)
        touchItem(itemId)
    }

    /** Vráti položku do pôvodného stavu z bločku (množstvo, cena, započítanie). */
    suspend fun restoreItem(itemId: Long) {
        val item = dao.itemById(itemId) ?: return
        item.origQuantity?.let { setQuantity(itemId, it) }
        if (item.excluded) { dao.setExcluded(itemId, false); touchItem(itemId) }
    }
    val itemRows = dao.allItemRows()
    val budgets = dao.budgets()
    fun receipt(id: Long) = dao.receiptWithItems(id)

    suspend fun scan(rawQr: String): ScanResult {
        if (mockMode) return save(DemoData.mockReceipt(rawQr), DemoData.SOURCE)
        val qr = EkasaQr.parse(rawQr)
            ?: return ScanResult.Error("QR kód nie je bloček eKasa.\nObsah: ${rawQr.take(80)}")
        if (qr is EkasaQr.Online) {
            dao.receiptByReceiptId(qr.receiptId)?.let { return ScanResult.Duplicate(qr.receiptId, it) }
            sync.remoteDuplicate(qr.receiptId)?.let { return ScanResult.Duplicate(qr.receiptId, it) }
        }
        val parsed = try {
            EkasaClient(endpoint).fetch(qr)
        } catch (e: EkasaException) {
            return ScanResult.Error(e.message ?: "Chyba eKasa", e.retryable)
        } catch (e: Exception) {
            return ScanResult.Error("Bez pripojenia – načíta sa automaticky neskôr.", retryable = true)
        }
        dao.receiptByReceiptId(parsed.receiptId)?.let { return ScanResult.Duplicate(parsed.receiptId, it) }
        sync.remoteDuplicate(parsed.receiptId)?.let { return ScanResult.Duplicate(parsed.receiptId, it) }
        return save(parsed, "EKASA")
    }

    // ---------- Rýchle skenovanie cez frontu ----------

    val openPending = dao.openPending()
    fun observePending(key: String) = dao.observePending(key)
    suspend fun deletePending(key: String) = dao.deletePending(key)

    /** Okamžite prijme QR (žiadna sieť), uloží do fronty a spustí načítanie na pozadí. */
    suspend fun enqueue(raw: String): EnqueueResult {
        val key = if (mockMode) "$MOCK_PREFIX${System.currentTimeMillis()}#$raw" else raw.trim()
        if (!mockMode) {
            val qr = EkasaQr.parse(key) ?: return EnqueueResult.Invalid("Toto nie je QR kód z bločku eKasa.")
            if (qr is EkasaQr.Online) dao.receiptByReceiptId(qr.receiptId)?.let { return EnqueueResult.AlreadyHave(it) }
            dao.pending(key)?.let { existing ->
                when (existing.status) {
                    "DONE", "DUP" -> {
                        val r = existing.receiptDbId?.let { dao.receiptById(it) }
                        if (r != null) return EnqueueResult.AlreadyHave(r)
                        // bloček medzitým zmazali → naskenovať znova
                        dao.updatePending(existing.copy(status = "PENDING", attempts = 0, message = null, receiptDbId = null))
                    }
                    "FAILED" -> dao.updatePending(existing.copy(status = "PENDING", attempts = 0, message = null))
                    else -> Unit // už čaká vo fronte
                }
                FetchWorker.enqueue(context)
                return EnqueueResult.Accepted(key)
            }
        }
        dao.insertPending(PendingScanEntity(key, System.currentTimeMillis(), "PENDING", 0, null, null))
        FetchWorker.enqueue(context)
        return EnqueueResult.Accepted(key)
    }

    /** Spracuje frontu. Vráti true, ak niečo ostalo na neskôr (worker naplánuje opakovanie). */
    suspend fun processPending(): Boolean {
        dao.cleanupDone(System.currentTimeMillis() - 2 * 24 * 3600_000L)
        var retryLater = false
        for (p in dao.pendingToProcess()) {
            val result = if (p.raw.startsWith(MOCK_PREFIX))
                save(DemoData.mockReceipt(p.raw), DemoData.SOURCE)
            else scan(p.raw)
            val updated = when (result) {
                is ScanResult.Saved -> p.copy(status = "DONE", receiptDbId = result.receiptDbId,
                    message = "${result.merchant} · ${result.items} pol. · ${Analytics.eur(result.totalCents)}")
                is ScanResult.Duplicate -> p.copy(status = "DUP", receiptDbId = result.existing?.id,
                    message = result.existing?.let { duplicateText(it) } ?: "Tento bloček už máte uložený.")
                is ScanResult.Error ->
                    if (result.retryable && p.attempts < MAX_ATTEMPTS) {
                        retryLater = true
                        p.copy(attempts = p.attempts + 1, message = result.message)
                    } else p.copy(status = "FAILED", attempts = p.attempts + 1, message = result.message)
            }
            dao.updatePending(updated)
        }
        return retryLater
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

    /** Zistí typ prevádzky: odpoveď používateľa > známa značka / kľúčové slovo > UNKNOWN (opýtať sa). */
    private suspend fun resolveMerchant(key: String, name: String): MerchantType {
        val existing = dao.merchant(key)
        if (existing?.userSet == true) return MerchantType.fromName(existing.type)
        val t = MerchantResolver.resolve(name)
        if (existing == null || existing.type != t.name) dao.upsertMerchant(MerchantEntity(key, name, t.name, false))
        return t
    }

    private suspend fun save(p: ParsedReceipt, source: String, forced: Category? = null): ScanResult {
        val userRules = dao.rules().associate { it.normName to Category.fromName(it.category) }
        val mKey = MerchantResolver.key(p.merchantIco, p.merchantName)
        val mType = if (source == "MANUAL") MerchantType.OTHER else resolveMerchant(mKey, p.merchantName)
        val receipt = ReceiptEntity(
            receiptId = p.receiptId, merchantName = p.merchantName, merchantIco = p.merchantIco,
            storeAddress = p.storeAddress, issuedAt = p.issuedAt, totalCents = p.totalCents,
            rawJson = p.rawJson.ifBlank { null }, source = source,
            merchantKey = if (source == "MANUAL") null else mKey, merchantType = mType.name,
            // v domácnosti: kto bloček nahral + odoslať ostatným (demo bločky sa neposielajú)
            addedBy = if (sync.enabled) sync.memberName else null,
            addedByUid = if (sync.enabled) sync.myUid else null,
            dirty = sync.enabled && source != DemoData.SOURCE,
            editedAt = System.currentTimeMillis()
        )
        // Zľavy (záporné riadky) sa pripočítajú k produktu, ku ktorému patria.
        val items = buildItems(p, mType, forced, userRules)
        val id = try {
            dao.insertReceiptWithItems(receipt, items)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            return ScanResult.Duplicate(p.receiptId, dao.receiptByReceiptId(p.receiptId)) // súbežné uloženie
        }
        if (sync.enabled && source != DemoData.SOURCE) { sync.forgetDeletion(p.receiptId); sync.schedule() }
        return ScanResult.Saved(id, p.merchantName, p.totalCents, items.size)
    }

    /** Položky bločku: zľavy priradené k produktom, stornované riadky odstránené, kategórie (pravidlá + AI). */
    private suspend fun buildItems(p: ParsedReceipt, mType: MerchantType, forced: Category?,
                                   userRules: Map<String, Category>): List<ItemEntity> {
        val ai = if (forced == null && mType.single == null) aiFn() else null
        val folded = if (forced != null) p.items.map { DiscountFolder.Folded(it.priceCents, 0) }
            else DiscountFolder.fold(p.items.map { DiscountFolder.Line(it.name, it.priceCents, it.itemType) })
        val items = p.items.zip(folded).mapNotNull { (src, f) ->
            if (f == null) return@mapNotNull null
            val norm = Categorizer.normalize(src.name)
            val res = if (forced != null) Categorizer.Result(forced)
                else Categorizer.categorizeWithSource(src.name, mType, src.priceCents, userRules, ai)
            ItemEntity(
                receiptDbId = 0, name = src.name, normName = norm, quantity = src.quantity,
                priceCents = f.priceCents, vatRate = src.vatRate, itemType = src.itemType,
                category = res.category.name,
                categoryManual = forced != null, discountCents = f.discountCents,
                catSource = if (res.byAi) "ai" else null
            )
        }
        return items
    }

    /** Raz po aktualizácii: znova spracuje zľavy a storná v uložených bločkoch eKasa (z pôvodných údajov eKasa). */
    private suspend fun refoldFromRaw() {
        val userRules = dao.rules().associate { it.normName to Category.fromName(it.category) }
        for (rw in dao.allReceiptsWithItems()) {
            val r = rw.receipt
            val raw = r.rawJson ?: continue
            if (r.source != "EKASA") continue
            if (rw.items.any { it.excluded || it.origQuantity != null }) continue   // ručné úpravy nemeníme
            val parsed = try { EkasaClient.parseResponse(raw) } catch (e: Exception) { continue }
            val mType = MerchantType.fromName(r.merchantType)
            val manual = rw.items.filter { it.categoryManual }.associate { it.normName to it.category }
            val items = buildItems(parsed, mType, null, userRules).map { i ->
                manual[i.normName]?.let { i.copy(category = it, categoryManual = true, catSource = null) } ?: i
            }
            val same = items.size == rw.items.size && items.zip(rw.items.sortedBy { it.id })
                .all { (a, b) -> a.priceCents == b.priceCents && a.discountCents == b.discountCents && a.name == b.name }
            if (same) continue
            val now = System.currentTimeMillis()
            dao.replaceReceipt(r.copy(dirty = sync.enabled || r.dirty, editedAt = if (sync.enabled) now else r.editedAt), items)
        }
    }

    /** Zmení kategóriu položky; ak [applyToAll], zapamätá si pravidlo pre všetky rovnaké názvy. */
    suspend fun recategorize(itemId: Long, normName: String, category: Category, applyToAll: Boolean) {
        if (applyToAll) {
            dao.upsertRule(CategoryRuleEntity(normName, category.name))
            dao.setCategoryForName(normName, category.name)
            if (sync.enabled) {
                dao.markDirtyByName(normName, System.currentTimeMillis())
                sync.queue("rule", normName, category.name)
                sync.schedule()
            }
        } else {
            dao.setItemCategory(itemId, category.name); touchItem(itemId)
            // oprava = nový príklad pre vstavanú AI (učí sa z neho na podobných názvoch)
            dao.upsertAiExample(AiExampleEntity(normName, category.name))
            if (sync.enabled) sync.queue("aiex", normName, category.name)
        }
        InternalAi.rebuild(context, dao)
    }

    // ---------- AI telefónu (Gemini Nano) ----------

    /** Zaradí položky pomocou AI telefónu. Volá sa len pri otvorenej aplikácii (podmienka Androidu). Vráti počet zmien. */
    suspend fun refineWithPhoneAi(): Int {
        if (!phoneAiEnabled || !PhoneAi.isReady()) return 0
        var changed = 0
        repeat(5) {   // najviac 5 dávok naraz, zvyšok pri ďalšom otvorení
            val batch = dao.itemsForPhoneAi(20)
            if (batch.isEmpty()) return changed
            val answers = PhoneAi.classify(batch.map { it.name }) ?: return changed
            batch.forEachIndexed { i, item ->
                val c = answers.getOrNull(i)
                if (c != null) { dao.setItemCategoryAuto(item.id, c.name, "nano"); changed++ }
                else dao.setItemCategoryAuto(item.id, item.category, "nano-none")
            }
        }
        return changed
    }

    /** Úprava ručne zadaného bločku: popis, suma, kategória. */
    suspend fun updateManual(id: Long, merchant: String, cents: Long, category: Category) {
        val rw = dao.receiptWithItemsOnce(id) ?: return
        val name = merchant.ifBlank { category.label }
        val r = rw.receipt.copy(merchantName = merchant.ifBlank { "Ručný záznam" }, totalCents = cents,
            dirty = sync.enabled, editedAt = System.currentTimeMillis())
        val base = rw.items.firstOrNull()
        val item = (base ?: ItemEntity(receiptDbId = id, name = name, normName = "", quantity = 1.0, priceCents = cents,
            vatRate = null, itemType = "MANUAL", category = category.name))
            .copy(name = name, normName = Categorizer.normalize(name), priceCents = cents, category = category.name,
                categoryManual = true, catSource = null, discountCents = 0, excluded = false,
                origQuantity = null, origPriceCents = null, origDiscountCents = null, quantity = 1.0)
        dao.replaceReceipt(r, listOf(item))
        if (sync.enabled) sync.schedule()
    }

    suspend fun deleteReceipt(id: Long) {
        val r = dao.receiptById(id)
        dao.deleteReceipt(id)
        dao.deletePendingFor(id)   // zmazaný bloček sa dá naskenovať znova
        if (sync.enabled && r?.syncId != null) { dao.addTombstone(SyncTombstoneEntity(r.syncId)); sync.schedule() }
    }

    /** Nahradí demo bločky novou sadou (3 mesiace + aktuálny). Vráti počet bločkov. */
    suspend fun seedDemo(): Int {
        dao.deleteBySource(DemoData.SOURCE)
        val list = DemoData.generate()
        list.forEach { save(it, DemoData.SOURCE) }
        return list.size
    }

    suspend fun clearDemo(): Int = dao.deleteBySource(DemoData.SOURCE)

    // ---------- Prevádzky a preradenie kategórií ----------

    /** Používateľ odpovedal, čo je prevádzka zač → zapamätať a preradiť všetky jej bločky. */
    suspend fun setMerchantType(key: String, name: String, type: MerchantType) {
        dao.upsertMerchant(MerchantEntity(key, name, type.name, true))
        recategorizeReceipts(dao.receiptsWithItemsByMerchant(key))
        if (sync.enabled) {
            dao.markDirtyByMerchant(key, System.currentTimeMillis())
            sync.queue("merchant", key, JSONObject().put("name", name).put("type", type.name).toString())
            sync.schedule()
        }
    }

    private suspend fun recategorizeReceipts(list: List<ReceiptWithItems>) {
        val userRules = dao.rules().associate { it.normName to Category.fromName(it.category) }
        val ai = aiFn()
        for (rw in list) {
            val r = rw.receipt
            if (r.source == "MANUAL") continue
            val key = MerchantResolver.key(r.merchantIco, r.merchantName)
            val type = resolveMerchant(key, r.merchantName)
            if (r.merchantKey != key || r.merchantType != type.name) dao.setReceiptMerchant(r.id, key, type.name)
            for (i in rw.items) {
                if (i.categoryManual || i.catSource == "nano") continue   // ručná oprava a AI telefónu ostávajú
                val res = Categorizer.categorizeWithSource(i.name, type, i.priceCents, userRules, ai)
                val src = if (res.byAi) "ai" else null
                if (res.category.name != i.category || src != i.catSource) dao.setItemCategoryAuto(i.id, res.category.name, src)
            }
        }
    }

    /** Pri novej verzii kategorizácie jednorazovo preradí všetky existujúce bločky (ručné zmeny ostanú). */
    suspend fun migrateIfNeeded() {
        if (prefs.getInt("catVersion", 0) < CATEGORIZER_VERSION || prefs.getString("aiVersion", "") != aiVersionKey()) {
            recategorizeReceipts(dao.allReceiptsWithItems())
            prefs.edit().putInt("catVersion", CATEGORIZER_VERSION).putString("aiVersion", aiVersionKey()).apply()
        }
        if (prefs.getInt("discountFold", 0) < 1) {
            foldExistingDiscounts()
            prefs.edit().putInt("discountFold", 1).apply()
        }
        if (prefs.getInt("discountFold", 0) < 2) {
            // v2: zľavy podľa názvu produktu (Tesco Clubcard) + stornované položky
            try { refoldFromRaw() } catch (e: Exception) { }
            prefs.edit().putInt("discountFold", 2).apply()
            if (sync.enabled) sync.schedule()
        }
    }

    /** Jednorazovo priradí zľavy k produktom aj v už uložených bločkoch. */
    private suspend fun foldExistingDiscounts() {
        for (rw in dao.allReceiptsWithItems()) {
            if (rw.receipt.source == "MANUAL") continue
            val items = rw.items.sortedBy { it.id }
            if (items.none { it.priceCents < 0 }) continue
            val folded = DiscountFolder.fold(items.map { DiscountFolder.Line(it.name, it.priceCents, it.itemType) })
            items.zip(folded).forEach { (item, f) ->
                when {
                    f == null -> dao.deleteItem(item.id)
                    f.priceCents != item.priceCents || f.discountCents != item.discountCents ->
                        dao.setItemPrice(item.id, f.priceCents, item.discountCents + f.discountCents)
                }
            }
        }
    }

    /** Právo na výmaz: zmaže všetky bločky, rozpočty, pravidlá, prevádzky, profil a exporty. */
    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        sync.leave()   // odíde z domácnosti; údaje ostatných členov na serveri ostanú
        dao.clearAll()
        val keepVersion = prefs.getInt("catVersion", 0)
        prefs.edit().clear().putInt("catVersion", keepVersion).apply()
        Period.payDay = 1
        File(context.cacheDir, "exports").deleteRecursively()
        File(context.cacheDir, "updates").deleteRecursively()
    }

    suspend fun setBudget(c: Category, cents: Long?) {
        if (cents == null || cents <= 0) dao.deleteBudget(c.name) else dao.upsertBudget(BudgetEntity(c.name, cents))
        if (sync.enabled) { sync.queue("budget", c.name, cents?.takeIf { it > 0 }?.toString()); sync.schedule() }
    }

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
