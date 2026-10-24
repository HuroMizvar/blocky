package sk.miro.blocky.sync

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Šifrovanie údajov domácnosti (end-to-end).
 *
 * Kľúč (32 bajtov) vznikne v telefóne zakladateľa domácnosti a ďalší členovia ho dostanú
 * len cez pozývací QR kód. Na server ide iba zašifrovaný text (AES-256-GCM) – server ani Google
 * obsah bločkov neprečíta. Identifikátory dokumentov sú HMAC z čísla bločku, takže ani tie
 * neprezradia číslo dokladu, no rovnaký bloček má u všetkých členov rovnaké ID (kontrola duplicít).
 */
object SyncCrypto {
    private val rnd = SecureRandom()
    private val b64 = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    fun newKey(): ByteArray = ByteArray(32).also { rnd.nextBytes(it) }

    /** Náhodné ID domácnosti (bez významu, len adresa na serveri). */
    fun newHouseholdId(): String = ByteArray(12).also { rnd.nextBytes(it) }.toHex()

    fun encrypt(key: ByteArray, plain: String): String {
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return b64.encodeToString(iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    /** Vyhodí výnimku, ak údaje nepatria k tomuto kľúču alebo ich niekto zmenil. */
    fun decrypt(key: ByteArray, enc: String): String {
        val all = b64d.decode(enc)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, all, 0, 12))
        return String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
    }

    fun hmac(key: ByteArray, text: String): String {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key, "HmacSHA256"))
        return m.doFinal(text.toByteArray(Charsets.UTF_8)).toHex()
    }

    /** ID dokumentu bločku – rovnaké pre rovnaký bloček u všetkých členov. */
    fun receiptDocId(key: ByteArray, receiptId: String) = "r" + hmac(key, "receipt:$receiptId").take(40)

    /** ID dokumentu nastavenia (pravidlo kategórie, typ prevádzky, rozpočet, profil). */
    fun kvDocId(key: ByteArray, kind: String, k: String) = "k" + hmac(key, "kv:$kind:$k").take(40)

    /** Tajomstvo na vstup do domácnosti – odvodené z kľúča, kľúč samotný na server nejde. */
    fun joinSecret(key: ByteArray) = hmac(key, "join")

    fun keyToText(key: ByteArray): String = b64.encodeToString(key)
    fun keyFromText(s: String): ByteArray = b64d.decode(s)

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}

/**
 * Obsah pozývacieho QR kódu: server (projekt Firebase), domácnosť a kľúč.
 * Kľúč správcu ([adminSecret]) navyše obsahuje tajomstvo správcu – s ním sa nové zariadenie stane správcom.
 */
data class Invite(val projectId: String, val apiKey: String, val householdId: String, val key: ByteArray,
                  val adminSecret: ByteArray? = null) {
    fun encode(): String {
        val parts = listOf(projectId, apiKey, householdId, SyncCrypto.keyToText(key)) +
            listOfNotNull(adminSecret?.let { SyncCrypto.keyToText(it) })
        return (if (adminSecret != null) ADMIN_PREFIX else PREFIX) +
            Base64.getUrlEncoder().withoutPadding().encodeToString(parts.joinToString("|").toByteArray(Charsets.UTF_8))
    }

    override fun equals(other: Any?) = other is Invite && other.projectId == projectId && other.apiKey == apiKey &&
        other.householdId == householdId && other.key.contentEquals(key)
    override fun hashCode() = householdId.hashCode()

    companion object {
        const val PREFIX = "BLOCKY1:"
        const val ADMIN_PREFIX = "BLOCKY1A:"
        fun isInvite(raw: String) = raw.trim().let { it.startsWith(PREFIX) || it.startsWith(ADMIN_PREFIX) }

        fun decode(raw: String): Invite? = try {
            val t = raw.trim()
            val body = String(Base64.getUrlDecoder().decode(t.removePrefix(ADMIN_PREFIX).removePrefix(PREFIX)), Charsets.UTF_8)
            val p = body.split("|")
            when {
                p.size == 4 && t.startsWith(PREFIX) -> Invite(p[0], p[1], p[2], SyncCrypto.keyFromText(p[3]))
                p.size == 5 && t.startsWith(ADMIN_PREFIX) ->
                    Invite(p[0], p[1], p[2], SyncCrypto.keyFromText(p[3]), SyncCrypto.keyFromText(p[4]))
                else -> null
            }?.takeIf { it.key.size == 32 && p[2].isNotBlank() }
        } catch (e: Exception) { null }

        /** odtlačok tajomstva správcu uložený na serveri (samotné tajomstvo na server nejde) */
        fun adminHash(secret: ByteArray, householdId: String): String =
            java.security.MessageDigest.getInstance("SHA-256").digest(secret + householdId.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
