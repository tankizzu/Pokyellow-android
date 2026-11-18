package it.giallotouch.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaveSlotsTest {
    @Test
    fun schedaAndataERitorno() {
        val info = SlotInfo(1_763_450_000_000, "Ro sa!", 3175, 2, listOf("PIKACHU" to 7, "PID|GEY" to 4))
        val back = SlotInfo.decode(info.encode())
        assertEquals(info.copy(party = listOf("PIKACHU" to 7, "PID GEY" to 4)), back)
    }

    @Test
    fun schedaRovinata() {
        assertNull(SlotInfo.decode("niente di utile"))
        assertEquals(SlotInfo(42), SlotInfo.decode("savedAt=42\nparty=senzalivello"))
    }
}
