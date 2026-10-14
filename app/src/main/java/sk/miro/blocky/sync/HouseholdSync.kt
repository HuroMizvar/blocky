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

data class Member(val uid: String, val name: String, val me: Boolean)

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
        writeMember(r, h, k, name)
        saveMembership(BuildConfig.FB_PROJECT_ID, BuildConfig.FB_API_KEY, h, k, name)
        afterJoin(name)
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
        saveMembership(invite.projectId, invite.apiKey, invite.householdId, invite.key, name)
        afterJoin(name)
    }

    private fun writeMember(r: FirestoreRest, h: String, k: ByteArray, name: String) {
        val info = JSONObject().put("name", name).put("joinedAt", System.currentTimeMillis()).toString()
        r.commit(listOf(r.writeDoc("households/$h/members/${tokenStore.uid}", JSONObject()
            .put("joinSecret", FirestoreRest.str(SyncCrypto.joinSecret(k)))
            .put("enc", FirestoreRest.str(SyncCrypto.encrypt(k, info))))))
    }

    private fun saveMembership(p: String, a: String, h: String, k: ByteArray, name: String) {
        prefs.edit().putString("projectId", p).putString("apiKey", a).putString("hid", h)
            .putString("key", SyncCrypto.keyToText(k)).putString("name", name)
            .remove("lastPullR").remove("lastPullK").remove("lastError").apply()
    }

    /** Po vstupe: poslať všetky vlastné bločky a nastavenia, stiahnuť bločky ostatných. */
    private suspend fun afterJoin(name: String) {
        dao.clearSyncIds()
        dao.markAllDirty(name, tokenStore.uid ?: "")
        queueAllSettings()
        publish()
        schedulePeriodic(context)
        syncNow()
    }

    private suspend fun queueAllSettings() {
        val now = System.currentTimeMillis()
        dao.rules().forEach { queue("rule", it.normName, it.category, now) }
        dao.aiExamples().forEach { queue("aiex", it.normName, it.category, now) }
        dao.userMerchants().forEach { queue("merchant", it.mkey, JSONObject().put("name", it.name).put("type", it.type).toString(), now) }
        dao.budgetList().forEach { queue("budget", it.category, it.monthlyCents.toString(), now) }
        queue("profile", "household", hooks.profileJson(), now)
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
            val name = FirestoreRest.fieldStr(d, "enc")?.let {
                runCatching { JSONObject(SyncCrypto.decrypt(k, it)).getString("name") }.getOrNull()
            } ?: "?"
            Member(id, name, id == tokenStore.uid)
        }.sortedWith(compareBy({ !it.me }, { it.name }))
    }

    // ---------- kontrola duplicity u ostatných členov ----------

    /** Ak tento bloček už nahral iný člen, stiahne ho a vráti ho (null = nie je na serveri / offline). */
    suspend fun remoteDuplicate(receiptId: String): ReceiptEntity? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        try {
            val doc = rest().get("$home/receipts/${SyncCrypto.receiptDocId(key, receiptId)}") ?: return@withContext null
            if (FirestoreRest.fieldBool(doc, "deleted")) return@withContext null
            applyReceipt(doc)
            dao.receiptByReceiptId(receiptId)
        } catch (e: Exception) { null }
    }

    // ---------- synchronizácia ----------

    suspend fun syncNow(): Boolean = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext true
        lock.withLock {
            publish(running = true)
            val ok = try {
                runOnce()
                prefs.edit().putLong("lastSyncAt", System.currentTimeMillis()).remove("lastError").apply()
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
