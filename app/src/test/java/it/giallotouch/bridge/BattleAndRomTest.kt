package it.giallotouch.bridge

import it.giallotouch.bridge.MemoryMapIt as M
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleAndRomTest {

    private fun block() = ByteArray(GameStateReader.BLOCK_SIZE)
    private fun ByteArray.put(address: Int, vararg values: Int) {
        values.forEachIndexed { i, v -> this[address - M.BLOCK_START + i] = v.toByte() }
    }

    @Test
    fun lottaConAllenatore_leggeCreaturaInCampo() {
        // Valori osservati nella prima lotta sulla ROM italiana (una mossa già usata: PP 30 -> 29).
        val b = block()
        b.put(M.IS_IN_BATTLE, 2)
        b.put(M.BATTLE_MON, 0x54, 0x00, 0x13, 0x00, 0x00, 0x17, 0x17, 0xA3, 0x54, 0x2D, 0x00, 0x00,
            0x11, 0x92, 0x05, 0x00, 0x13, 0x00, 0x0A, 0x00, 0x08, 0x00, 0x0E, 0x00, 0x0A, 0x1D, 0x28, 0x00, 0x00)
        val s = GameStateReader.read(b)
        assertTrue(s.inBattle)
        val mon = s.battleMon!!
        assertEquals(5, mon.level)
        assertEquals(19, mon.hp)
        assertEquals(19, mon.maxHp)
        assertEquals(listOf(MoveSlot(0x54, 29), MoveSlot(0x2D, 40)), mon.moves)
    }

    @Test
    fun fuoriDallaLotta_nessunaCreaturaInCampo() {
        val s = GameStateReader.read(block())
        assertFalse(s.inBattle)
        assertNull(s.battleMon)
    }

    @Test
    fun listaNomi_daRomSintetica() {
        // ROM sintetica: "AB", "CÀ", "D" chiusi da 0x50 (nessun dato del gioco).
        val rom = byteArrayOf(0x00, 0x80.toByte(), 0x81.toByte(), 0x50, 0x82.toByte(), 0xBE.toByte(), 0x50, 0x83.toByte(), 0x50)
        assertEquals(listOf("AB", "CÀ", "D"), RomData.readNameList(rom, 1, 3))
        assertNull(RomData.readNameList(rom, 1, 4))
    }
}
