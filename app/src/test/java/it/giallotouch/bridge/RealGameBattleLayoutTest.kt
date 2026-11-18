package it.giallotouch.bridge

import it.giallotouch.emu.NativeCore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Sul gioco vero: la schermata di lotta si divide (scena + riquadro del testo), la mappa no. */
class RealGameBattleLayoutTest {
    private fun tilesOf(stateKey: String): ByteArray? {
        val rom = System.getProperty("giallo.rom")?.let(::File)
        val state = System.getProperty(stateKey)?.let(::File)
        if (rom == null || !rom.isFile || state == null || !state.isFile) return null
        val h = NativeCore.nativeCreate(48000)
        try {
            NativeCore.nativeLoadRom(h, rom.readBytes())
            if (!NativeCore.nativeLoadState(h, state.readBytes())) return null
            NativeCore.nativeRunFrame(h, IntArray(160 * 144), ShortArray(8192))
            return ByteArray(MenuReader.SCREEN_SIZE).also { NativeCore.nativeReadRange(h, MemoryMapIt.TILE_MAP, it) }
        } finally { NativeCore.nativeDestroy(h) }
    }

    @Test
    fun lottaDivisaMappaIntera() {
        val battle = tilesOf("giallo.state")
        val map = tilesOf("giallo.state.pallet")
        assumeTrue(battle != null && map != null)
        assertTrue(BattleLayout.textBoxAtBottom(battle!!))
        assertFalse(BattleLayout.textBoxAtBottom(map!!))
    }
}
