package it.giallotouch.bridge

import it.giallotouch.emu.NativeCore
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Sul gioco vero: con il testo automatico al massimo, il testo della domanda sul soprannome va avanti da solo
 * fino alla domanda Sì/No, e lì l'aiuto si ferma (la risposta resta al giocatore).
 * Saltata se mancano ROM, stato (giallo.state.text) e libreria del motore.
 */
class RealGameTextTest {
    @Test
    fun testoAutomaticoSiFermaAllaDomanda() {
        val rom = System.getProperty("giallo.rom")?.let(::File)
        val state = System.getProperty("giallo.state.text")?.let(::File)
        assumeTrue(rom != null && rom.isFile && state != null && state.isFile)
        val h = NativeCore.nativeCreate(48000)
        try {
            NativeCore.nativeLoadRom(h, rom!!.readBytes())
            NativeCore.nativeLoadState(h, state!!.readBytes())
            val pixels = IntArray(NativeCore.WIDTH * NativeCore.HEIGHT)
            val audio = ShortArray(8192)
            val tiles = ByteArray(MenuReader.SCREEN_SIZE)
            val vars = ByteArray(MenuReader.MENU_VARS_SIZE)
            val assist = TextAssist().apply { autoSpeed = 3 }
            repeat(900) {
                NativeCore.nativeReadRange(h, MemoryMapIt.TILE_MAP, tiles)
                NativeCore.nativeSetKeys(h, assist.step(tiles))
                NativeCore.nativeRunFrame(h, pixels, audio)
            }
            NativeCore.nativeReadRange(h, MemoryMapIt.TILE_MAP, tiles)
            NativeCore.nativeReadRange(h, MenuReader.MENU_VARS, vars)
            assertEquals(MenuKind.YES_NO, MenuReader.read(vars, tiles).kind)
            assertEquals("nessuna risposta automatica", 0, NativeCore.nativeReadMemory(h, MemoryMapIt.PARTY_MONS))
        } finally {
            NativeCore.nativeDestroy(h)
        }
    }
}
