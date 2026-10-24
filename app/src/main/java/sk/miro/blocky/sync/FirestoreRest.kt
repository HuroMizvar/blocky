package sk.miro.blocky.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncException(message: String, val code: Int = 0) : IOException(message)

/**
 * Minimálny klient Firebase (REST, bez Firebase SDK – žiadna analytika, žiadne reklamné ID).
 * - prihlásenie: anonymný účet Firebase (žiadne meno, e-mail ani Google účet),
 * - dáta: Cloud Firestore; na server ide len zašifrovaný text (pozri [SyncCrypto]).
 */
class FirestoreRest(
    private val projectId: String,
    private val apiKey: String,
    private val tokens: TokenStore
) {
    /** Uloženie prihlasovacích tokenov (v SharedPreferences aplikácie). */
    interface TokenStore {
        var uid: String?
        var idToken: String?
        var refreshToken: String?
        var expiresAt: Long
    }

    private val json = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val base = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"
    val dbPrefix = "projects/$projectId/databases/(default)/documents"

    // ---------- prihlásenie ----------

    private fun authHeaders(b: Request.Builder) = b
        // ak je API kľúč obmedzený na aplikáciu Android, Google overí balík a podpis
        .header("X-Android-Package", "sk.miro.blocky")
        .header("X-Android-Cert", "B01328725379E57B6FF23C4DD01EA4B5034333B3")

    /** Vráti platný token; v prípade potreby obnoví alebo vytvorí anonymný účet. */
    fun token(): String {
        val t = tokens.idToken
        if (t != null && System.currentTimeMillis() < tokens.expiresAt - 60_000) return t
        tokens.refreshToken?.let { rt ->
            try { return refresh(rt) } catch (e: SyncException) { if (e.code !in 400..499) throw e }
        }
        return signUp()
    }

    private fun signUp(): String {
        val body = JSONObject().put("returnSecureToken", true).toString().toRequestBody(json)
        val req = authHeaders(Request.Builder().url("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey")).post(body).build()
        val o = JSONObject(exec(req))
        tokens.uid = o.getString("localId")
        tokens.refreshToken = o.getString("refreshToken")
        tokens.expiresAt = System.currentTimeMillis() + o.optString("expiresIn", "3600").toLong() * 1000
        return o.getString("idToken").also { tokens.idToken = it }
    }

    private fun refresh(rt: String): String {
        val body = "grant_type=refresh_token&refresh_token=$rt".toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val req = authHeaders(Request.Builder().url("https://securetoken.googleapis.com/v1/token?key=$apiKey")).post(body).build()
        val o = JSONObject(exec(req))
        tokens.uid = o.getString("user_id")
        tokens.refreshToken = o.getString("refresh_token")
        tokens.expiresAt = System.currentTimeMillis() + o.optString("expires_in", "3600").toLong() * 1000
        return o.getString("id_token").also { tokens.idToken = it }
    }

    // ---------- dokumenty ----------

    private fun req(url: String) = Request.Builder().url(url).header("Authorization", "Bearer ${token()}")

    /** Dokument alebo null, ak neexistuje. [path] napr. "households/abc/receipts/r123". */
    fun get(path: String): JSONObject? = try {
        JSONObject(exec(req("$base/$path").get().build()))
    } catch (e: SyncException) { if (e.code == 404) null else throw e }

    /** Zoznam dokumentov kolekcie (všetky strany). */
    fun list(path: String): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        var page: String? = null
        do {
            val url = "$base/$path?pageSize=300" + (page?.let { "&pageToken=$it" } ?: "")
            val o = JSONObject(exec(req(url).get().build()))
            o.optJSONArray("documents")?.let { a -> for (i in 0 until a.length()) out += a.getJSONObject(i) }
            page = o.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (page != null)
        return out
    }

    /** Dokumenty kolekcie [collection] pod [parent], ktoré sa zmenili po [since] (RFC 3339) – podľa poľa updatedAt. */
    fun changedSince(parent: String, collection: String, since: String?): List<JSONObject> {
        val q = JSONObject().put("from", JSONArray().put(JSONObject().put("collectionId", collection)))
        if (since != null) q.put("where", JSONObject().put("fieldFilter", JSONObject()
            .put("field", JSONObject().put("fieldPath", "updatedAt"))
            .put("op", "GREATER_THAN")
            .put("value", JSONObject().put("timestampValue", since))))
        q.put("orderBy", JSONArray().put(JSONObject().put("field", JSONObject().put("fieldPath", "updatedAt"))))
        val body = JSONObject().put("structuredQuery", q).toString().toRequestBody(json)
        val arr = JSONArray(exec(req("$base/$parent:runQuery").post(body).build()))
        return (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optJSONObject("document") }
    }

    /** Zápis viacerých zmien naraz (max. 500). */
    fun commit(writes: List<JSONObject>) {
        writes.chunked(400).forEach { chunk ->
            val body = JSONObject().put("writes", JSONArray(chunk)).toString().toRequestBody(json)
            exec(req("$base:commit").post(body).build())
        }
    }

    /** Zápis dokumentu so serverovým časom v poli updatedAt. [mustNotExist] = len vytvoriť. */
    fun writeDoc(path: String, fields: JSONObject, mustNotExist: Boolean = false, stamp: Boolean = true,
                 mask: List<String>? = null): JSONObject =
        JSONObject().apply {
            put("update", JSONObject().put("name", "$dbPrefix/$path").put("fields", fields))
            if (mask != null) put("updateMask", JSONObject().put("fieldPaths", JSONArray(mask)))
            if (stamp) put("updateTransforms", JSONArray().put(
                JSONObject().put("fieldPath", "updatedAt").put("setToServerValue", "REQUEST_TIME")))
            if (mustNotExist) put("currentDocument", JSONObject().put("exists", false))
        }

    fun deleteDoc(path: String): JSONObject = JSONObject().put("delete", "$dbPrefix/$path")

    private fun exec(r: Request): String = http.newCall(r).execute().use { resp ->
        val text = resp.body?.string() ?: ""
        if (!resp.isSuccessful) {
            val msg = try { JSONObject(text).optJSONObject("error")?.optString("message") } catch (e: Exception) { null }
            val human = when (resp.code) {
                403 -> "Prístup zamietnutý (nie ste členom domácnosti alebo chýbajú pravidlá Firestore)."
                404 -> "Nenájdené."
                409 -> "Už existuje."
                else -> "Chyba servera ${resp.code}"
            }
            throw SyncException(if (msg.isNullOrBlank()) human else "$human ($msg)", resp.code)
        }
        text
    }

    companion object {
        fun str(v: String) = JSONObject().put("stringValue", v)
        fun bool(v: Boolean) = JSONObject().put("booleanValue", v)
        fun int(v: Long) = JSONObject().put("integerValue", v.toString())

        fun fieldStr(doc: JSONObject, name: String): String? =
            doc.optJSONObject("fields")?.optJSONObject(name)?.optString("stringValue")?.takeIf { it.isNotEmpty() }
        fun fieldBool(doc: JSONObject, name: String): Boolean =
            doc.optJSONObject("fields")?.optJSONObject(name)?.optBoolean("booleanValue", false) ?: false
        fun fieldTime(doc: JSONObject, name: String): String? =
            doc.optJSONObject("fields")?.optJSONObject(name)?.optString("timestampValue")?.takeIf { it.isNotEmpty() }
        /** posledná časť názvu dokumentu = jeho ID */
        fun docId(doc: JSONObject): String = doc.getString("name").substringAfterLast('/')
    }
}
