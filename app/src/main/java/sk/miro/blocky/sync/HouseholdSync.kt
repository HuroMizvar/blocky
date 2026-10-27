package sk.miro.blocky.sync

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import sk.miro.blocky.BuildConfig
import sk.miro.blocky.data.*
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Stav synchronizácie pre obrazovku nastavení (jeden pre celú aplikáciu). */
data class SyncStatus(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val lastSyncAt: Long = 0,
    val error: String? = null,
    val memberName: String? = null
)

data class Member(val uid: String, val name: String, val me: Boolean, val admin: Boolean = false)

/**
 * Spoločná domácnosť: viac ľudí (max. 10) nahráva bločky do spoločných výdavkov.
 *
 * Voliteľné – bez domácnosti aplikácia funguje len v telefóne ako doteraz.
 * Na server (Google Firebase, EÚ) ide všetko zašifrované kľúčom, ktorý má len domácnosť.
 */
class HouseholdSync(private val context: Context, private val dao: BlockyDao, private val hooks: Hooks) {

    /** Profil domácnosti žije v Repository – synchronizácia ho číta a zapisuje cez tieto funkcie. */
    interface Hooks {
        fun profileJson(): String
        fun applyProfileJson(json: String)
        /** vlastné kategórie tohto telefónu: slot → JSON */
        fun customCategoryJsons(): Map<String, String>
        /** vlastná kategória z domácnosti (null = zmazaná) */
        suspend fun applyCustomCategory(slotName: String, json: String?)
        /** fixné výdavky: id → JSON */
        fun fixedJsons(): Map<String, String>
        fun applyFixed(id: String, json: String?)
    }

    companion object {
        const val MAX_MEMBERS = 10
        private val lock = Mutex()
        private val _status = MutableStateFlow(SyncStatus())
        val status: StateFlow<SyncStatus> = _status

        /** Server je nastavený v buildе (súbor google-services.json v repozitári). */
        val builtInConfigured: Boolean get() = BuildConfig.FB_PROJECT_ID.isNotBlank() && BuildConfig.FB_API_KEY.isNotBlank()
    }

    private val prefs = context.getSharedPreferences("household", Context.MODE_PRIVATE)

    private val tokenStore = object : FirestoreRest.TokenStore {
        override var uid: String?
            get() = prefs.getString("uid", null)
            set(v) { prefs.edit().putString("uid", v).apply() }
        override var idToken: String?
            get() = prefs.getString("idToken", null)
            set(v) { prefs.edit().putString("idToken", v).apply() }
        override var refreshToken: String?
            get() = prefs.getString("refreshToken", null)
            set(v) { prefs.edit().putString("refreshToken", v).apply() }
        override var expiresAt: Long
            get() = prefs.getLong("expiresAt", 0)
            set(v) { prefs.edit().putLong("expiresAt", v).apply() }
    }

    val enabled: Boolean get() = prefs.getString("hid", null) != null && prefs.getString("key", null) != null
    /** toto zariadenie je správca domácnosti (pozná tajomstvo správcu) */
    val isAdmin: Boolean get() = enabled && prefs.getString("adminSecret", null) != null
    private val adminSecret: ByteArray? get() = prefs.getString("adminSecret", null)?.let { SyncCrypto.keyFromText(it) }
    val memberName: String? get() = prefs.getString("name", null)
    val myUid: String? get() = tokenStore.uid
    private val hid: String get() = prefs.getString("hid", null)!!
    private val key: ByteArray get() = SyncCrypto.keyFromText(prefs.getString("key", null)!!)
    private val projectId: String get() = prefs.getString("projectId", null) ?: BuildConfig.FB_PROJECT_ID
    private val apiKey: String get() = prefs.getString("apiKey", null) ?: BuildConfig.FB_API_KEY

    private fun rest(p: String = projectId, a: String = apiKey) = FirestoreRest(p, a, tokenStore)
    private val home get() = "households/$hid"

    init { publish() }

    private fun publish(running: Boolean = false, error: String? = prefs.getString("lastError", null)) {
        _status.value = SyncStatus(enabled, running, prefs.getLong("lastSyncAt", 0), error, memberName)
    }

    fun invite(): Invite? = if (!enabled) null else Invite(projectId, apiKey, hid, key)

    /** Kľúč správcu: pozvánka + tajomstvo správcu. S ním sa nové zariadenie vráti do domácnosti ako správca. */
    fun adminKey(): Invite? = if (!isAdmin) null else Invite(projectId, apiKey, hid, key, adminSecret)

    // ---------- založenie / pripojenie / odchod ----------

    suspend fun create(name: String) = withContext(Dispatchers.IO) {
        check(builtInConfigured) { "Server nie je nastavený – pozrite návod (google-services.json)." }
        val k = SyncCrypto.newKey()
        val h = SyncCrypto.newHouseholdId()
        val r = rest(BuildConfig.FB_PROJECT_ID, BuildConfig.FB_API_KEY)
        r.token()
        r.commit(listOf(r.writeDoc("households/$h", JSONObject()
            .put("joinSecret", FirestoreRest.str(SyncCrypto.joinSecret(k)))
            .put("v", FirestoreRest.int(1)), mustNotExist = true)))
        val secret = SyncCrypto.newKey()
        prefs.edit().putString("adminSecret", SyncCrypto.keyToText(secret)).apply()
        writeMember(r, h, k, name, admin = true)
        r.commit(listOf(r.writeDoc("households/$h", JSONObject()
            .put("adminHash", FirestoreRest.str(Invite.adminHash(secret, h))), mask = listOf("adminHash"))))
        saveMembership(BuildConfig.FB_PROJECT_ID, BuildConfig.FB_API_KEY, h, k, name)
        afterJoin(name, joining = false)
    }

    suspend fun join(invite: Invite, name: String) = withContext(Dispatchers.IO) {
        val r = rest(invite.projectId, invite.apiKey)
        r.token()
        try {
            writeMember(r, invite.householdId, invite.key, name)
        } catch (e: SyncException) {
            throw SyncException(if (e.code == 403 || e.code == 404) "Pozvánka je neplatná alebo domácnosť už neexistuje." else e.message ?: "Chyba", e.code)
        }
        val count = r.list("households/${invite.householdId}/members").size
        if (count > MAX_MEMBERS) {
            runCatching { r.commit(listOf(r.deleteDoc("households/${invite.householdId}/members/${tokenStore.uid}"))) }
            throw SyncException("Domácnosť je plná (najviac $MAX_MEMBERS členov).")
        }
        // kľúč správcu: overiť tajomstvo s odtlačkom na serveri
        val adminOk = invite.adminSecret?.let { s ->
            val hh = r.get("households/${invite.householdId}")?.let { FirestoreRest.fieldStr(it, "adminHash") }
            hh != null && hh == Invite.adminHash(s, invite.householdId)
        } ?: false
        if (adminOk) {
            prefs.edit().putString("adminSecret", SyncCrypto.keyToText(invite.adminSecret!!)).apply()
            writeMember(r, invite.householdId, invite.key, name, admin = true)
        }
        saveMembership(invite.projectId, invite.apiKey, invite.householdId, invite.key, name)
        afterJoin(name, joining = true)
    }

    private fun writeMember(r: FirestoreRest, h: String, k: ByteArray, name: String, admin: Boolean = isAdmin,
                            joinedAt: Long = prefs.getLong("joinedAt", 0).takeIf { it > 0 } ?: System.currentTimeMillis()) {
        prefs.edit().putLong("joinedAt", joinedAt).apply()
        val info = JSONObject().put("name", name).put("joinedAt", joinedAt).put("admin", admin).toString()
        r.commit(listOf(r.writeDoc("households/$h/members/${tokenStore.uid}", JSONObject()
            .put("joinSecret", FirestoreRest.str(SyncCrypto.joinSecret(k)))
            .put("enc", FirestoreRest.str(SyncCrypto.encrypt(k, info))))))
    }

    private fun saveMembership(p: String, a: String, h: String, k: ByteArray, name: String) {
        prefs.edit().remove("removed").putString("projectId", p).putString("apiKey", a).putString("hid", h)
            .putString("key", SyncCrypto.keyToText(k)).putString("name", name)
            .remove("lastPullR").remove("lastPullK").remove("lastError").apply()
    }

    /** Po vstupe: poslať všetky vlastné bločky a nastavenia, stiahnuť bločky ostatných. */
    private suspend fun afterJoin(name: String, joining: Boolean) {
        dao.clearSyncIds()
        dao.markAllDirty(name, tokenStore.uid ?: "")
        // pri vstupe do existujúcej domácnosti majú prednosť jej nastavenia – inak by ich prepísali prázdne nastavenia nového telefónu
        val skip = if (joining) runCatching { adoptHouseholdSettings() }.getOrDefault(emptySet()) else emptySet()
        queueAllSettings(skip)
        publish()
        schedulePeriodic(context)
        syncNow()
    }

    private suspend fun queueAllSettings(skip: Set<String> = emptySet()) {
        val now = System.currentTimeMillis()
        suspend fun q(kind: String, k: String, v: String?) { if ("$kind:$k" !in skip) queue(kind, k, v, now) }
        dao.rules().forEach { q("rule", it.normName, it.category) }
        dao.aiExamples().forEach { q("aiex", it.normName, it.category) }
        dao.userMerchants().forEach { q("merchant", it.mkey, JSONObject().put("name", it.name).put("type", it.type).toString()) }
        dao.budgetList().forEach { q("budget", it.category, it.monthlyCents.toString()) }
        q("profile", "household", hooks.profileJson())
        hooks.customCategoryJsons().forEach { (slot, json) -> q("customcat", slot, json) }
        hooks.fixedJsons().forEach { (id, json) -> q("fixed", id, json) }
    }

    /**
     * Stiahne nastavenia domácnosti a použije ich v tomto telefóne. Vlastné kategórie sa zlúčia
     * (rovnaký názov = jedna kategória). Vráti kľúče, ktoré sa z tohto telefónu nemajú posielať.
     */
    private suspend fun adoptHouseholdSettings(): Set<String> {
        val r = rest()
        val k = key
        val skip = HashSet<String>()
        for (doc in r.list("$home/kv")) {
            val enc = FirestoreRest.fieldStr(doc, "enc") ?: continue
            val kv = runCatching { SyncPayload.kvFromJson(SyncCrypto.decrypt(k, enc)) }.getOrNull() ?: continue
            runCatching { applyKv(doc, k) }
            // vlastné kategórie sa zlúčia a pošlú znova; ostatné nastavenia domácnosti majú prednosť
            if (kv.kind != "customcat" && kv.kind != "customcats" && kv.kind != "fixed") skip += "${kv.kind}:${kv.key}"
        }
        return skip
    }

    /** Zmenené nastavenie – odošle sa pri najbližšej synchronizácii. */
    suspend fun queue(kind: String, k: String, value: String?, at: Long = System.currentTimeMillis()) {
        if (!enabled) return
        dao.addOutbox(SyncOutboxEntity("$kind:$k", kind, k, value, at))
    }

    /** Odísť z domácnosti: údaje v telefóne ostanú, synchronizácia sa vypne. */
    suspend fun leave() = withContext(Dispatchers.IO) {
        if (enabled) runCatching {
            val r = rest()
            r.commit(listOf(r.deleteDoc("$home/members/${tokenStore.uid}")))
        }
        resetLocal()
    }

    /** Zmazať všetky údaje domácnosti zo servera (pre všetkých členov). Údaje v telefóne ostanú. */
    suspend fun deleteServerData() = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext
        val r = rest()
        val docs = r.list("$home/receipts") + r.list("$home/kv")
        r.commit(docs.map { r.deleteDoc("$home/${it.getString("name").substringAfter("$home/")}") })
        val others = r.list("$home/members").map { FirestoreRest.docId(it) }.filter { it != tokenStore.uid }
        r.commit(others.map { r.deleteDoc("$home/members/$it") })
        r.commit(listOf(r.deleteDoc(home)))
        r.commit(listOf(r.deleteDoc("$home/members/${tokenStore.uid}")))
        resetLocal()
    }

    /** Správca nás odstránil: odpojiť sa (bločky v telefóne ostanú). */
    private suspend fun removedByAdmin() {
        runCatching { val r = rest(); r.commit(listOf(r.deleteDoc("$home/members/${tokenStore.uid}"))) }
        resetLocal()
        prefs.edit().putString("lastError", "Správca domácnosti vás z domácnosti odstránil. Bločky ostali v tomto telefóne.").apply()
        publish()
    }

    private suspend fun resetLocal() {
        prefs.edit().clear().apply()
        dao.clearSyncIds(); dao.clearTombstones(); dao.clearOutbox()
        WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.PERIODIC)
        publish(error = null)
    }

    suspend fun members(): List<Member> = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext emptyList()
        val k = key
        rest().list("$home/members").map { d ->
            val id = FirestoreRest.docId(d)
            val info = FirestoreRest.fieldStr(d, "enc")?.let { runCatching { JSONObject(SyncCrypto.decrypt(k, it)) }.getOrNull() }
            Member(id, info?.optString("name")?.ifBlank { null } ?: "?", id == tokenStore.uid, info?.optBoolean("admin") == true)
        }.sortedWith(compareBy({ !it.me }, { !it.admin }, { it.name }))
    }

    /** Má domácnosť správcu? (null = nepodarilo sa zistiť) */
    suspend fun householdHasAdmin(): Boolean? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        runCatching { rest().get(home)?.let { FirestoreRest.fieldStr(it, "adminHash") != null } }.getOrNull()
    }

    /**
     * Domácnosť bez správcu (zakladateľ preinštaloval aplikáciu, staršia verzia): správcom sa môže stať
     * ktorýkoľvek člen. Ak už správca existuje, nič sa nezmení.
     */
    suspend fun claimAdmin() = withContext(Dispatchers.IO) {
        check(enabled)
        val r = rest(); r.token()
        val doc = r.get(home)
        check(doc == null || FirestoreRest.fieldStr(doc, "adminHash") == null) { "Domácnosť už má správcu." }
        val secret = SyncCrypto.newKey()
        prefs.edit().putString("adminSecret", SyncCrypto.keyToText(secret)).putBoolean("adminChecked", true).apply()
        r.commit(listOf(r.writeDoc(home, JSONObject().put("adminHash", FirestoreRest.str(Invite.adminHash(secret, hid))),
            mask = listOf("adminHash"))))
        writeMember(r, hid, key, memberName ?: "Správca", admin = true)
        publish()
    }

    // ---------- správca, meno, odstránenie členov ----------

    /** Zmena mena člena – prepíše sa aj pri bločkoch, ktoré toto zariadenie nahralo. */
    suspend fun rename(newName: String) = withContext(Dispatchers.IO) {
        check(enabled)
        val r = rest(); r.token()
        writeMember(r, hid, key, newName)
        prefs.edit().putString("name", newName).apply()
        tokenStore.uid?.let { dao.renameMine(it, newName, System.currentTimeMillis()) }
        publish()
        syncNow()
    }

    /** Správca odstráni člena: zmaže jeho členstvo a pošle mu správu, aby sa odpojil. */
    suspend fun removeMember(m: Member) = withContext(Dispatchers.IO) {
        check(isAdmin) { "Členov môže odstrániť len správca domácnosti." }
        val r = rest(); r.token()
        queue("removed", m.uid, m.name)
        r.commit(listOf(r.deleteDoc("$home/members/${m.uid}")))
        syncNow()
    }

    /**
     * Nový kľúč domácnosti (napr. po strate či krádeži telefónu): staré údaje na serveri sa zmažú
     * a domácnosť sa založí znova s novým kľúčom; ostatní členovia sa pridajú novou pozvánkou.
     */
    suspend fun rotateKey() = withContext(Dispatchers.IO) {
        check(isAdmin) { "Kľúč môže vymeniť len správca domácnosti." }
        val name = memberName ?: "Správca"
        deleteServerData()
        create(name)
    }

    /**
     * Domácnosti založené pred zavedením správcu: správcom sa stane člen, ktorý sa pridal ako prvý
     * (zakladateľ). Volá sa pri synchronizácii, kým to nie je vyriešené.
     */
    private fun ensureAdmin(r: FirestoreRest) {
        if (prefs.getBoolean("adminChecked", false)) return
        val doc = r.get(home) ?: return
        if (FirestoreRest.fieldStr(doc, "adminHash") != null) {
            // ak náš kľúč správcu už neplatí, prestaneme byť správcom
            val s = adminSecret
            if (s != null && FirestoreRest.fieldStr(doc, "adminHash") != Invite.adminHash(s, hid))
                prefs.edit().remove("adminSecret").apply()
            prefs.edit().putBoolean("adminChecked", true).apply(); return
        }
        val k = key
        val first = r.list("$home/members").mapNotNull { d ->
            val info = FirestoreRest.fieldStr(d, "enc")?.let { runCatching { JSONObject(SyncCrypto.decrypt(k, it)) }.getOrNull() }
            info?.let { FirestoreRest.docId(d) to it.optLong("joinedAt", Long.MAX_VALUE) }
        }.minByOrNull { it.second }?.first
        if (first != null && first == tokenStore.uid) {
            val secret = SyncCrypto.newKey()
            prefs.edit().putString("adminSecret", SyncCrypto.keyToText(secret)).apply()
            r.commit(listOf(r.writeDoc(home, JSONObject().put("adminHash", FirestoreRest.str(Invite.adminHash(secret, hid))),
                mask = listOf("adminHash"))))
            writeMember(r, hid, k, memberName ?: "Správca", admin = true)
        }
        // správcu sme nenašli (napr. zakladateľ preinštaloval aplikáciu) – skúsime znova pri ďalšej synchronizácii
    }

    // ---------- kontrola duplicity u ostatných členov ----------

    /** Ak tento bloček už nahral iný člen, stiahne ho a vráti ho (null = nie je na serveri / offline). */
    suspend fun remoteDuplicate(receiptId: String): ReceiptEntity? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        try {
            val docId = SyncCrypto.receiptDocId(key, receiptId)
            if (dao.tombstones().any { it.docId == docId }) return@withContext null   // práve zmazaný v tomto zariadení
            val doc = rest().get("$home/receipts/$docId") ?: return@withContext null
            if (FirestoreRest.fieldBool(doc, "deleted")) return@withContext null
            applyReceipt(doc)
            dao.receiptByReceiptId(receiptId)
        } catch (e: Exception) { null }
    }

    /** Bloček bol znova naskenovaný po zmazaní – zmazanie sa už neposiela. */
    suspend fun forgetDeletion(receiptId: String) {
        if (enabled) dao.removeTombstone(SyncCrypto.receiptDocId(key, receiptId))
    }

    // ---------- synchronizácia ----------

    suspend fun syncNow(): Boolean = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext true
        lock.withLock {
            publish(running = true)
            val ok = try {
                runOnce()
                prefs.edit().putLong("lastSyncAt", System.currentTimeMillis()).remove("lastError").apply()
                if (prefs.getBoolean("removed", false)) removedByAdmin()
                true
            } catch (e: SyncException) {
                retryAfterRejoin(e)
            } catch (e: Exception) { fail(e) }
            publish(running = false)
            ok
        }
    }

    private suspend fun retryAfterRejoin(e: SyncException): Boolean {
        if (e.code != 403 || !rejoin()) return fail(e)
        return try {
            runOnce()
            prefs.edit().putLong("lastSyncAt", System.currentTimeMillis()).remove("lastError").apply()
            true
        } catch (e2: Exception) { fail(e2) }
    }

    private fun fail(e: Exception): Boolean {
        prefs.edit().putString("lastError", e.message ?: e.javaClass.simpleName).apply()
        return false
    }

    /** Anonymný účet sa mohol obnoviť (nová inštalácia, vymazané dáta) – znova sa zapíšeme ako člen. */
    private fun rejoin(): Boolean = try {
        val r = rest()
        r.token()
        writeMember(r, hid, key, memberName ?: "Člen")
        true
    } catch (e: Exception) { false }

    private suspend fun runOnce() {
        val r = rest()
        val k = key
        val uid = r.token().let { tokenStore.uid ?: "" }
        runCatching { ensureAdmin(r) }

        // 1) zmazané bločky
        val tombs = dao.tombstones()
        if (tombs.isNotEmpty()) {
            r.commit(tombs.map { r.writeDoc("$home/receipts/${it.docId}", JSONObject()
                .put("deleted", FirestoreRest.bool(true)).put("by", FirestoreRest.str(uid))) })
            tombs.forEach { dao.removeTombstone(it.docId) }
        }

        // 2) nové a upravené bločky
        for (chunk in dao.dirtyReceipts().chunked(100)) {
            val writes = ArrayList<JSONObject>()
            val done = ArrayList<Triple<ReceiptEntity, String, String?>>()
            for (rw in chunk) {
                val rec = rw.receipt
                val docId = rec.syncId ?: SyncCrypto.receiptDocId(k, rec.receiptId)
                if (rec.syncId == null) {
                    // rovnaký bloček mohol medzitým nahrať iný člen → platí jeho verzia
                    val existing = r.get("$home/receipts/$docId")
                    if (existing != null && !FirestoreRest.fieldBool(existing, "deleted")) { applyReceipt(existing); continue }
                }
                val who = rec.addedBy ?: memberName
                val whoUid = rec.addedByUid ?: uid
                val json = SyncPayload.toJson(rec.copy(addedBy = who, addedByUid = whoUid), rw.items)
                writes += r.writeDoc("$home/receipts/$docId", JSONObject()
                    .put("enc", FirestoreRest.str(SyncCrypto.encrypt(k, json)))
                    .put("by", FirestoreRest.str(uid))
                    .put("deleted", FirestoreRest.bool(false)))
                done += Triple(rec, docId, who)
            }
            if (writes.isNotEmpty()) r.commit(writes)
            done.forEach { (rec, docId, who) -> dao.markClean(rec.id, docId, who, rec.addedByUid ?: uid, rec.editedAt) }
        }

        // 3) nastavenia
        val out = dao.outbox()
        if (out.isNotEmpty()) {
            r.commit(out.map { o ->
                val json = SyncPayload.kvToJson(SyncPayload.Kv(o.kind, o.k, o.value, o.at))
                r.writeDoc("$home/kv/${SyncCrypto.kvDocId(k, o.kind, o.k)}", JSONObject()
                    .put("enc", FirestoreRest.str(SyncCrypto.encrypt(k, json))))
            })
            out.forEach { dao.removeOutbox(it.kindKey, it.at) }
        }

        // 4) stiahnuť zmeny ostatných
        pull(r, "receipts", "lastPullR") { applyReceipt(it) }
        pull(r, "kv", "lastPullK") { applyKv(it, k) }
    }

    private suspend fun pull(r: FirestoreRest, coll: String, pref: String, apply: suspend (JSONObject) -> Unit) {
        val since = prefs.getString(pref, null)
        var max: Instant? = if (since == null) null else runCatching { Instant.parse(since) }.getOrNull()
        for (doc in r.changedSince(home, coll, since)) {
            try { apply(doc) } catch (e: Exception) { /* poškodený záznam preskočíme */ }
            val t: Instant? = FirestoreRest.fieldTime(doc, "updatedAt")?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val cur = max
            if (t != null && (cur == null || t.isAfter(cur))) max = t
        }
        val last = max
        if (last != null) prefs.edit().putString(pref, last.toString()).apply()
    }

    private suspend fun applyReceipt(doc: JSONObject) {
        val id = FirestoreRest.docId(doc)
        if (FirestoreRest.fieldBool(doc, "deleted")) {
            dao.receiptBySyncId(id)?.let { dao.deleteReceipt(it.id) }
            return
        }
        val enc = FirestoreRest.fieldStr(doc, "enc") ?: return
        val p = SyncPayload.fromJson(SyncCrypto.decrypt(key, enc), id)
        val local = dao.receiptBySyncId(id) ?: dao.receiptByReceiptId(p.receipt.receiptId)
        when {
            local == null -> try {
                dao.insertReceiptWithItems(p.receipt, p.items)
            } catch (e: android.database.sqlite.SQLiteConstraintException) { }
            local.dirty && local.syncId != null && local.editedAt > p.receipt.editedAt -> Unit   // naša úprava je novšia
            local.syncId == id && !local.dirty && local.editedAt == p.receipt.editedAt -> Unit   // nič nové
            else -> dao.replaceReceipt(p.receipt.copy(id = local.id, rawJson = local.rawJson), p.items)
        }
    }

    private suspend fun applyKv(doc: JSONObject, k: ByteArray) {
        val enc = FirestoreRest.fieldStr(doc, "enc") ?: return
        val kv = SyncPayload.kvFromJson(SyncCrypto.decrypt(k, enc))
        if (dao.outbox().any { it.kindKey == "${kv.kind}:${kv.key}" }) return   // naša novšia zmena čaká na odoslanie
        when (kv.kind) {
            "rule" -> {
                if (kv.value == null) dao.deleteRule(kv.key) else dao.upsertRule(CategoryRuleEntity(kv.key, kv.value))
                InternalAi.invalidate()
            }
            "aiex" -> kv.value?.let { dao.upsertAiExample(AiExampleEntity(kv.key, it)); InternalAi.invalidate() }
            "merchant" -> kv.value?.let { v ->
                val o = JSONObject(v)
                dao.upsertMerchant(MerchantEntity(kv.key, o.getString("name"), o.getString("type"), true))
            }
            "budget" -> {
                val cents = kv.value?.toLongOrNull()
                if (cents == null || cents <= 0) dao.deleteBudget(kv.key) else dao.upsertBudget(BudgetEntity(kv.key, cents))
            }
            "profile" -> kv.value?.let { hooks.applyProfileJson(it) }
            "removed" -> if (kv.key == tokenStore.uid) prefs.edit().putBoolean("removed", true).apply()
            "customcat" -> hooks.applyCustomCategory(kv.key, kv.value)
            "fixed" -> hooks.applyFixed(kv.key, kv.value)
            // staršie verzie posielali celý zoznam naraz
            "customcats" -> kv.value?.let { v ->
                sk.miro.blocky.domain.CustomCategories.parse(v).values.forEach { d ->
                    hooks.applyCustomCategory(d.slot.name, sk.miro.blocky.domain.CustomCategories.defToJson(d))
                }
            }
        }
    }

    // ---------- plánovanie ----------
    fun schedule() { if (enabled) SyncWorker.now(context) }
}

/** Synchronizácia na pozadí: hneď po zmene (s malým oneskorením) a pravidelne každé 3 hodiny. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result =
        if (Repository(applicationContext).sync.syncNow()) Result.success() else Result.retry()

    companion object {
        const val ONCE = "household-sync"
        const val PERIODIC = "household-sync-periodic"
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun now(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(net)
                .setInitialDelay(3, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}

fun schedulePeriodic(context: Context) {
    val req = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(SyncWorker.PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
}
