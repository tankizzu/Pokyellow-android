package it.giallotouch.bridge

import it.giallotouch.emu.NativeCore
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Sul gioco vero: dopo l'avvio da capo (introduzione e titolo) il menu iniziale viene riconosciuto,
 * così l'app può mostrare "Carica partita" sotto le voci del gioco. Saltata senza ROM e libreria.
 */
class RealGameMainMenuTest {
    @Test
    fun avvioFinoAlMenuIniziale() {
        val rom = System.getProperty("giallo.rom")?.let(::File)
        assumeTrue(rom != null && rom.isFile)
        val h = NativeCore.nativeCreate(48000)
        try {
            NativeCore.nativeLoadRom(h, rom!!.readBytes())
            val pixels = IntArray(160 * 144); val audio = ShortArray(8192)
            val vars = ByteArray(MenuReader.MENU_VARS_SIZE); val tiles = ByteArray(MenuReader.SCREEN_SIZE)
            fun menu(): MenuState {
                NativeCore.nativeReadRange(h, MenuReader.MENU_VARS, vars)
                NativeCore.nativeReadRange(h, MemoryMapIt.TILE_MAP, tiles)
                return MenuReader.read(vars, tiles)
            }
            // Introduzione e titolo: come un giocatore, START ogni secondo finché compare il menu.
            var frames = 0
            while (menu().kind != MenuKind.MAIN_MENU && frames < 60 * 40) {
                NativeCore.nativeSetKeys(h, if (frames > 600 && frames % 60 < 6) NativeCore.KEY_START else 0)
                NativeCore.nativeRunFrame(h, pixels, audio)
                frames++
            }
            val m = menu()
            assertEquals(MenuKind.MAIN_MENU, m.kind)
            assertEquals("senza salvataggio del gioco: NUOVO GIOCO e OPZIONI", 2, m.items)
            assertEquals(0, m.item)
        } finally { NativeCore.nativeDestroy(h) }
    }
}
