package sk.miro.blocky.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.time.LocalDate
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Zašifrovaná záloha všetkých údajov do jedného súboru (.blocky).
 *
 * Obsah: databáza (bločky, položky, rozpočty, pravidlá, prevádzky), nastavenia, profil, vlastné kategórie
 * a – ak ste v domácnosti – členstvo v domácnosti vrátane jej kľúča.
 * Šifrovanie: heslo → PBKDF2-SHA256 (210 000 iterácií) → AES-256-GCM. Bez hesla sa súbor nedá prečítať;
 * heslo sa nikam neposiela a autor aplikácie ho nepozná – ak ho zabudnete, zálohu nie je možné obnoviť.
 */
object Backup {
    class WrongPassword : Exception("Nesprávne heslo alebo poškodený súbor.")
    class BadFile(msg: String) : Exception(msg)

    data class Info(val createdAt: Long, val appVersion: Int, val household: Boolean)

    private const val MAGIC = "BLOCKYBAK1"
    private const val ITER = 210_000
    private const val DB_NAME = "blocky.db"
    /** najvyššia verzia databázy, ktorú táto verzia aplikácie vie otvoriť (AppDatabase.version) */
    private const val MAX_DB_VERSION = 7
    private val PREF_FILES = listOf("settings", "household")
    const val MIME = "application/octet-stream"

    fun fileName(date: LocalDate = LocalDate.now()) = "blocky-zaloha-$date.blocky"

    // ---------- vytvorenie ----------

    suspend fun create(context: Context, password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        // dopísať WAL do hlavného súboru, aby bol súbor databázy úplný
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val dbBytes = context.getDatabasePath(DB_NAME).readBytes()
        val prefs = JSONObject()
        for (name in PREF_FILES) prefs.put(name, prefsToJson(context.getSharedPreferences(name, Context.MODE_PRIVATE).all))
        val payload = JSONObject()
            .put("v", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("app", sk.miro.blocky.BuildConfig.VERSION_CODE)
            .put("db", Base64.getEncoder().encodeToString(dbBytes))
            .put("prefs", prefs)
        encrypt(gzip(payload.toString().toByteArray(Charsets.UTF_8)), password)
    }

    // ---------- čítanie ----------

    /** Overí heslo a vráti obsah zálohy (ešte nič neprepíše). */
    suspend fun open(bytes: ByteArray, password: CharArray): JSONObject = withContext(Dispatchers.Default) {
        val json = JSONObject(String(gunzip(decrypt(bytes, password)), Charsets.UTF_8))
        if (json.optInt("v") != 1 || !json.has("db")) throw BadFile("Súbor nie je záloha Bločkov.")
        json
    }

    fun info(payload: JSONObject) = Info(
        payload.optLong("createdAt"), payload.optInt("app"),
        payload.optJSONObject("prefs")?.optJSONObject("household")?.has("hid") == true
    )

    /**
     * Pripraví obnovu: overí zálohu a uloží ju bokom. Samotná výmena databázy prebehne pri najbližšom
     * štarte aplikácie ([applyPendingRestore]), keď databáza ešte nie je otvorená – nič sa nepoškodí.
     */
    suspend fun restore(context: Context, payload: JSONObject) = withContext(Dispatchers.IO) {
        val dbBytes = Base64.getDecoder().decode(payload.getString("db"))
        if (dbBytes.size < 100 || String(dbBytes, 0, 15, Charsets.US_ASCII) != "SQLite format 3")
            throw BadFile("Záloha je poškodená.")
        val dbVersion = ((dbBytes[60].toInt() and 0xff) shl 24) or ((dbBytes[61].toInt() and 0xff) shl 16) or
            ((dbBytes[62].toInt() and 0xff) shl 8) or (dbBytes[63].toInt() and 0xff)
        if (dbVersion > MAX_DB_VERSION) throw BadFile("Záloha je z novšej verzie aplikácie. Najprv aplikáciu aktualizujte.")
        File(context.filesDir, "restore-prefs.json").writeText((payload.optJSONObject("prefs") ?: JSONObject()).toString())
        val tmp = File(context.filesDir, "restore.db.tmp")
        tmp.writeBytes(dbBytes)
        tmp.renameTo(File(context.filesDir, "restore.db"))   // až keď je všetko zapísané
    }

    /** Volá sa pred otvorením databázy: ak čaká obnova zo zálohy, vymení databázu a nastavenia. */
    fun applyPendingRestore(context: Context) {
        val ready = File(context.filesDir, "restore.db")
        if (!ready.exists()) return
        val path = context.getDatabasePath(DB_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(path.path + it).delete() }
        path.parentFile?.mkdirs()
        if (!ready.renameTo(path)) { path.writeBytes(ready.readBytes()); ready.delete() }
        val pf = File(context.filesDir, "restore-prefs.json")
        val prefs = runCatching { JSONObject(pf.readText()) }.getOrDefault(JSONObject())
        for (name in PREF_FILES) {
            val e = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            prefs.optJSONObject(name)?.let { jsonToPrefs(it, e) }
            e.commit()
        }
        pf.delete()
    }

    // ---------- automatická záloha do priečinka ----------

    private fun cfg(context: Context) = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    data class AutoState(val enabled: Boolean, val folder: String?, val lastAt: Long, val lastError: String?)

    fun autoState(context: Context): AutoState = cfg(context).let {
        AutoState(it.getString("tree", null) != null, it.getString("folderName", null), it.getLong("lastAt", 0), it.getString("lastError", null))
    }

    /** Heslo záloh uložené v telefóne (chránené Android Keystore) – používa sa pre každú zálohu. */
    fun savedPassword(context: Context): String? =
        cfg(context).getString("pwd", null)?.let { runCatching { String(unwrap(it), Charsets.UTF_8) }.getOrNull() }

    fun setPassword(context: Context, password: CharArray) {
        cfg(context).edit().putString("pwd", wrap(String(password).toByteArray(Charsets.UTF_8))).apply()
    }

    fun enableAuto(context: Context, tree: Uri, folderName: String?) {
        context.contentResolver.takePersistableUriPermission(tree,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        cfg(context).edit().putString("tree", tree.toString()).putString("folderName", folderName)
            .remove("lastError").apply()
        val req = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(BackupWorker.NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun disableAuto(context: Context) {
        val tree = cfg(context).getString("tree", null)
        if (tree != null) runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(tree),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        cfg(context).edit().remove("tree").remove("folderName").remove("lastAt").remove("lastError").apply()
        WorkManager.getInstance(context).cancelUniqueWork(BackupWorker.NAME)
    }

    /** Zapíše zálohu do zvoleného priečinka a ponechá 4 najnovšie. */
    suspend fun runAuto(context: Context) {
        val c = cfg(context)
        val tree = Uri.parse(c.getString("tree", null) ?: return)
        val pwd = (savedPassword(context) ?: return).toCharArray()
        try {
            val bytes = create(context, pwd)
            val cr = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val doc = DocumentsContract.createDocument(cr, parent, MIME, fileName()) ?: throw BadFile("Do priečinka sa nedá zapisovať.")
            cr.openOutputStream(doc, "w")?.use { it.write(bytes) } ?: throw BadFile("Do priečinka sa nedá zapisovať.")
            prune(context, tree)
            c.edit().putLong("lastAt", System.currentTimeMillis()).remove("lastError").apply()
        } catch (e: Exception) {
            c.edit().putString("lastError", e.message ?: e.javaClass.simpleName).apply()
            throw e
        }
    }

    private fun prune(context: Context, tree: Uri, keep: Int = 4) {
        val cr = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val files = ArrayList<Pair<String, String>>()   // (id, name)
        cr.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null)?.use { cur ->
            while (cur.moveToNext()) {
                val name = cur.getString(1) ?: continue
                if (name.startsWith("blocky-zaloha-") && name.endsWith(".blocky")) files += cur.getString(0) to name
            }
        }
        files.sortedByDescending { it.second }.drop(keep).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    // ---------- šifrovanie ----------

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, derive(password, salt, ITER), GCMParameterSpec(128, iv))
        c.updateAAD(MAGIC.toByteArray(Charsets.US_ASCII))
        val ct = c.doFinal(plain)
        val header = JSONObject().put("kdf", "PBKDF2-SHA256").put("iter", ITER)
            .put("salt", Base64.getEncoder().encodeToString(salt)).put("iv", Base64.getEncoder().encodeToString(iv))
        return ByteArrayOutputStream().apply {
            write("$MAGIC\n$header\n".toByteArray(Charsets.UTF_8)); write(ct)
        }.toByteArray()
    }

    fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        val nl1 = bytes.indexOf('\n'.code.toByte())
        if (nl1 <= 0 || String(bytes, 0, nl1, Charsets.US_ASCII) != MAGIC) throw BadFile("Súbor nie je záloha Bločkov.")
        var nl2 = -1
        for (i in nl1 + 1 until bytes.size) if (bytes[i] == '\n'.code.toByte()) { nl2 = i; break }
        if (nl2 < 0) throw BadFile("Záloha je poškodená.")
        val h = JSONObject(String(bytes, nl1 + 1, nl2 - nl1 - 1, Charsets.UTF_8))
        val salt = Base64.getDecoder().decode(h.getString("salt"))
        val iv = Base64.getDecoder().decode(h.getString("iv"))
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, derive(password, salt, h.optInt("iter", ITER)), GCMParameterSpec(128, iv))
        c.updateAAD(MAGIC.toByteArray(Charsets.US_ASCII))
        return try { c.doFinal(bytes, nl2 + 1, bytes.size - nl2 - 1) } catch (e: AEADBadTagException) { throw WrongPassword() }
    }

    private fun derive(password: CharArray, salt: ByteArray, iter: Int): SecretKey {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val raw = f.generateSecret(PBEKeySpec(password, salt, iter, 256)).encoded
        return SecretKeySpec(raw, "AES")
    }

    private fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()
    private fun gunzip(b: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }

    // ---------- heslo pre automatickú zálohu – chránené kľúčom v Android Keystore ----------

    private const val KS_ALIAS = "blocky-backup"

    private fun ksKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KS_ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(KS_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return g.generateKey()
    }

    private fun wrap(plain: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, ksKey())
        val ct = c.doFinal(plain)
        return Base64.getEncoder().encodeToString(c.iv) + ":" + Base64.getEncoder().encodeToString(ct)
    }

    private fun unwrap(s: String): ByteArray {
        val (iv, ct) = s.split(":").let { Base64.getDecoder().decode(it[0]) to Base64.getDecoder().decode(it[1]) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, ksKey(), GCMParameterSpec(128, iv))
        return c.doFinal(ct)
    }

    // ---------- SharedPreferences ↔ JSON ----------

    fun prefsToJson(all: Map<String, *>): JSONObject {
        val o = JSONObject()
        for ((k, v) in all) when (v) {
            is String -> o.put(k, JSONObject().put("t", "s").put("v", v))
            is Int -> o.put(k, JSONObject().put("t", "i").put("v", v))
            is Long -> o.put(k, JSONObject().put("t", "l").put("v", v))
            is Boolean -> o.put(k, JSONObject().put("t", "b").put("v", v))
            is Float -> o.put(k, JSONObject().put("t", "f").put("v", v.toDouble()))
            is Set<*> -> o.put(k, JSONObject().put("t", "ss").put("v", JSONArray(v.map { it.toString() })))
        }
        return o
    }

    private fun jsonToPrefs(o: JSONObject, e: android.content.SharedPreferences.Editor) {
        for (k in o.keys()) {
            val x = o.optJSONObject(k) ?: continue
            when (x.optString("t")) {
                "s" -> e.putString(k, x.getString("v"))
                "i" -> e.putInt(k, x.getInt("v"))
                "l" -> e.putLong(k, x.getLong("v"))
                "b" -> e.putBoolean(k, x.getBoolean("v"))
                "f" -> e.putFloat(k, x.getDouble("v").toFloat())
                "ss" -> x.getJSONArray("v").let { a -> e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
            }
        }
    }
}

/** Týždenná automatická záloha (len ak ju používateľ zapol a vybral priečinok). */
class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        Backup.runAuto(applicationContext); Result.success()
    } catch (e: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }

    companion object { const val NAME = "auto-backup" }
}
