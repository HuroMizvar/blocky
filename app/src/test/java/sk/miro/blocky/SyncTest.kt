package sk.miro.blocky

import org.junit.Assert.*
import org.junit.Test
import sk.miro.blocky.data.ItemEntity
import sk.miro.blocky.data.ReceiptEntity
import sk.miro.blocky.sync.Invite
import sk.miro.blocky.sync.SyncCrypto
import sk.miro.blocky.sync.SyncPayload

class SyncTest {
    private val key = SyncCrypto.newKey()

    @Test fun encryptRoundTrip() {
        val enc = SyncCrypto.encrypt(key, "Lidl · Maslo 2,49 € ľščťžýáíé")
        assertFalse(enc.contains("Maslo"))
        assertEquals("Lidl · Maslo 2,49 € ľščťžýáíé", SyncCrypto.decrypt(key, enc))
    }

    @Test(expected = Exception::class) fun wrongKeyFails() {
        SyncCrypto.decrypt(SyncCrypto.newKey(), SyncCrypto.encrypt(key, "tajné"))
    }

    @Test fun sameReceiptSameDocIdForAllMembers() {
        val a = SyncCrypto.receiptDocId(key, "O-1234567890ABCDEF")
        assertEquals(a, SyncCrypto.receiptDocId(key.copyOf(), "O-1234567890ABCDEF"))
        assertNotEquals(a, SyncCrypto.receiptDocId(key, "O-1234567890ABCDEG"))
        assertFalse(a.contains("1234567890"))
    }

    @Test fun inviteRoundTrip() {
        val inv = Invite("blocky-123", "AIzaKey", SyncCrypto.newHouseholdId(), key)
        val code = inv.encode()
        assertTrue(Invite.isInvite(code))
        assertEquals(inv, Invite.decode(code))
        assertNull(Invite.decode("BLOCKY1:nezmysel"))
        assertFalse(Invite.isInvite("https://ekasa.financnasprava.sk/mdu/api/v1/opd/receipt/find"))
    }

    @Test fun payloadRoundTrip() {
        val r = ReceiptEntity(id = 7, receiptId = "O-ABC", merchantName = "Lidl", merchantIco = null, storeAddress = "Ulica 1",
            issuedAt = 1_760_000_000_000, totalCents = 549, rawJson = "{veľký json}", source = "EKASA",
            merchantKey = "N:lidl", merchantType = "GROCERY", addedBy = "Jana", addedByUid = "u1", dirty = true, editedAt = 99)
        val items = listOf(
            ItemEntity(id = 1, receiptDbId = 7, name = "Káva", normName = "kava", quantity = 1.0, priceCents = 180,
                vatRate = 20.0, itemType = "K", category = "NAPOJE", origQuantity = 2.0, origPriceCents = 360, origDiscountCents = 0),
            ItemEntity(id = 2, receiptDbId = 7, name = "Rožok", normName = "rozok", quantity = 3.0, priceCents = 369,
                vatRate = null, itemType = null, category = "PECIVO", categoryManual = true, excluded = true, discountCents = 30)
        )
        val back = SyncPayload.fromJson(SyncPayload.toJson(r, items), "rDOC")
        assertEquals("rDOC", back.receipt.syncId)
        assertEquals("Jana", back.receipt.addedBy)
        assertNull(back.receipt.rawJson)
        assertNull(back.receipt.merchantIco)
        assertEquals(99L, back.receipt.editedAt)
        assertFalse(back.receipt.dirty)
        assertEquals(2, back.items.size)
        assertEquals(2.0, back.items[0].origQuantity!!, 0.0)
        assertNull(back.items[1].vatRate)
        assertNull(back.items[1].itemType)
        assertTrue(back.items[1].excluded)
        assertTrue(back.items[1].categoryManual)
        assertEquals(30L, back.items[1].discountCents)
    }

    @Test fun kvRoundTrip() {
        val kv = SyncPayload.Kv("budget", "MASO", null, 5)
        assertEquals(kv, SyncPayload.kvFromJson(SyncPayload.kvToJson(kv)))
    }
}
