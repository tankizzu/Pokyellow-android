package it.giallotouch.bridge

import it.giallotouch.bridge.MemoryMapIt as M
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateReaderTest {

    private fun block() = ByteArray(GameStateReader.BLOCK_SIZE)
    private fun ByteArray.put(address: Int, vararg values: Int) {
        values.forEachIndexed { i, v -> this[address - M.BLOCK_START + i] = v.toByte() }
    }
    private fun encode(text: String): IntArray = text.map { c ->
        when (c) { in 'A'..'Z' -> 0x80 + (c - 'A'); else -> 0x7F }
    }.toIntArray() + TextCodec.TERMINATOR

    @Test
    fun nuovaPartita_squadraVuotaESoldiIniziali() {
        // Valori osservati sulla ROM italiana appena iniziata la partita.
        val b = block()
        b.put(M.PLAYER_NAME, 0x86, 0x88, 0x80, 0x8B, 0x8B, 0x8E, 0x50)
        b.put(M.PARTY_COUNT, 0x00, 0xFF)
        b.put(M.PLAYER_MONEY, 0x00, 0x30, 0x00)
        b.put(M.CUR_MAP, 0x26)
        b.put(M.Y_COORD, 7, 3)
        val s = GameStateReader.read(b)
        assertEquals("GIALLO", s.playerName)
        assertTrue(s.party.isEmpty())
        assertEquals(3000, s.money)
        assertEquals(0x26, s.mapId)
        assertEquals(3, s.x)
        assertEquals(7, s.y)
    }

    @Test
    fun squadraDiDue_letturaCampi() {
        val b = block()
        b.put(M.PARTY_COUNT, 2, 0x54, 0x24, 0xFF)
        val m1 = M.PARTY_MONS
        b.put(m1, 0x54, 0x00, 0x12, 5, 0x00)           // specie, PS 18, livello box, stato
        b.put(m1 + 33, 5, 0x00, 0x14)                   // livello 5, PS massimi 20
        val m2 = M.PARTY_MONS + M.PARTY_STRUCT_LENGTH
        b.put(m2, 0x24, 0x00, 0x05, 4, 0x08)           // PS 5, avvelenato
        b.put(m2 + 33, 4, 0x00, 0x11)                   // livello 4, PS massimi 17
        b.put(M.PARTY_MON_NICKS, *encode("ALFA"))
        b.put(M.PARTY_MON_NICKS + M.NAME_LENGTH, *encode("BETA"))
        val party = GameStateReader.read(b).party
        assertEquals(2, party.size)
        assertEquals(PartyMember(0x54, "ALFA", 5, 18, 20, Status.NONE), party[0])
        assertEquals(PartyMember(0x24, "BETA", 4, 5, 17, Status.POISON), party[1])
    }

    @Test
    fun datiNonPlausibili_squadraVuota() {
        val b = block()
        b.put(M.PARTY_COUNT, 9)                          // più di 6: memoria non inizializzata
        assertTrue(GameStateReader.read(b).party.isEmpty())
        val c = block()
        c.put(M.PARTY_COUNT, 1, 0x54, 0xFF)
        c.put(M.PARTY_MONS, 0x54, 0x00, 0x30)            // PS 48
        c.put(M.PARTY_MONS + 33, 5, 0x00, 0x14)          // PS massimi 20 < PS: non plausibile
        assertTrue(GameStateReader.read(c).party.isEmpty())
    }

    @Test
    fun stati_dalByte() {
        assertEquals(Status.SLEEP, Status.fromByte(0x03))
        assertEquals(Status.PARALYSIS, Status.fromByte(0x40))
        assertEquals(Status.NONE, Status.fromByte(0x00))
    }
}
