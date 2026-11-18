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
        b.put(m1, 0x54, 0x00, 0x12, 5, 0x00, 0x17, 0x17) // specie, PS 18, livello box, stato, tipi (uguali)
        b.put(m1 + 8, 0x54, 0x2D, 0x00, 0x00)            // mosse: 0x54, 0x2D
        b.put(m1 + 29, 0x1E or 0x40, 40)                 // PP 30 con un PP Su, 40
        b.put(m1 + 33, 5, 0x00, 0x14, 0, 11, 0, 9, 0, 14, 0, 10)  // livello 5, PS massimi 20, statistiche
        val m2 = M.PARTY_MONS + M.PARTY_STRUCT_LENGTH
        b.put(m2, 0x24, 0x00, 0x05, 4, 0x08)           // PS 5, avvelenato
        b.put(m2 + 33, 4, 0x00, 0x11)                   // livello 4, PS massimi 17
        b.put(M.PARTY_MON_NICKS, *encode("ALFA"))
        b.put(M.PARTY_MON_NICKS + M.NAME_LENGTH, *encode("BETA"))
        val party = GameStateReader.read(b).party
        assertEquals(2, party.size)
        assertEquals(
            PartyMember(
                0x54, "ALFA", 5, 18, 20, Status.NONE, types = listOf(0x17),
                moves = listOf(MoveSlot(0x54, 30, 1), MoveSlot(0x2D, 40, 0)), stats = Stats(11, 9, 14, 10)
            ),
            party[0]
        )
        assertEquals(PartyMember(0x24, "BETA", 4, 5, 17, Status.POISON), party[1].copy(types = emptyList(), stats = null))
    }

    @Test
    fun borsa() {
        val b = block()
        b.put(M.BAG_COUNT, 2, 0x14, 3, 0x46, 1, 0xFF)
        assertEquals(listOf(BagItem(0x14, 3), BagItem(0x46, 1)), GameStateReader.read(b).bag)
        val c = block()
        c.put(M.BAG_COUNT, 2, 0x14, 3, 0x46, 1, 0x00)   // manca la chiusura 0xFF: memoria non pronta
        assertTrue(GameStateReader.read(c).bag.isEmpty())
        assertTrue(Items.isKeyItem(0x46) && Items.isKeyItem(0xC4) && !Items.isKeyItem(0x14) && !Items.isKeyItem(0xC9))
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

class PokedexReadTest {
    @org.junit.Test
    fun vistiEPresi() {
        val b = ByteArray(GameStateReader.BLOCK_SIZE)
        fun put(a: Int, v: Int) { b[a - M.BLOCK_START] = v.toByte() }
        put(M.POKEDEX_SEEN + 3, 0x01)       // n. 25
        put(M.POKEDEX_SEEN + 16, 0x10)      // n. 133
        put(M.POKEDEX_OWNED + 3, 0x01)
        put(M.POKEDEX_SEEN + 18, 0xFF)      // bit oltre il 151 ignorati
        val s = GameStateReader.read(b)
        org.junit.Assert.assertEquals(listOf(25, 133, 145, 146, 147, 148, 149, 150, 151), s.pokedexSeen)
        org.junit.Assert.assertEquals(listOf(25), s.pokedexOwned)
    }
}
