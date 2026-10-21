package sk.miro.blocky

import org.junit.Assert.*
import org.junit.Test
import sk.miro.blocky.data.Backup

class BackupTest {
    @Test fun encryptDecryptRoundTrip() {
        val data = "Bločky – záloha 123".toByteArray()
        val enc = Backup.encrypt(data, "tajne-heslo".toCharArray())
        assertFalse(String(enc, Charsets.ISO_8859_1).contains("záloha"))
        assertArrayEquals(data, Backup.decrypt(enc, "tajne-heslo".toCharArray()))
    }

    @Test(expected = Backup.WrongPassword::class)
    fun wrongPasswordFails() {
        val enc = Backup.encrypt("x".toByteArray(), "spravne".toCharArray())
        Backup.decrypt(enc, "zle".toCharArray())
    }

    @Test(expected = Backup.BadFile::class)
    fun otherFileIsRejected() {
        Backup.decrypt("%PDF-1.4 nieco".toByteArray(), "x".toCharArray())
    }

    @Test fun prefsSerialization() {
        val o = Backup.prefsToJson(mapOf("a" to "x", "b" to 3, "c" to 5L, "d" to true, "e" to setOf("q")))
        assertEquals("s", o.getJSONObject("a").getString("t"))
        assertEquals(3, o.getJSONObject("b").getInt("v"))
        assertEquals("l", o.getJSONObject("c").getString("t"))
        assertTrue(o.getJSONObject("d").getBoolean("v"))
        assertEquals("q", o.getJSONObject("e").getJSONArray("v").getString(0))
    }
}
