package it.giallotouch.bridge

import it.giallotouch.emu.NativeCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Prova sul gioco vero: alla domanda sul soprannome del primo compagno rispondiamo "No"
 * con l'esecutore; il gioco deve completare i dati del compagno senza aprire la tastiera dei nomi.
 * Saltata se mancano ROM, stato salvato alla domanda (giallo.state.yesno) e libreria del motore.
 */
class RealGameDialogTest {

    @Test
    fun rispondeNoAllaDomandaDelSoprannome() {
        val rom = System.getProperty("giallo.rom")?.let(::File)
        val state = System.getProperty("giallo.state.yesno")?.let(::File)
        assumeTrue(rom != null && rom.isFile && state != null && state.isFile)

        val h = NativeCore.nativeCreate(48000)
        try {
            NativeCore.nativeLoadRom(h, rom!!.readBytes())
            assertTrue(NativeCore.nativeLoadState(h, state!!.readBytes()))
            val pixels = IntArray(NativeCore.WIDTH * NativeCore.HEIGHT)
            val audio = ShortArray(8192)
            val vars = ByteArray(MenuReader.MENU_VARS_SIZE)
            val tiles = ByteArray(MenuReader.SCREEN_SIZE)
            fun menu(): MenuState {
                NativeCore.nativeReadRange(h, MenuReader.MENU_VARS, vars)
                NativeCore.nativeReadRange(h, MemoryMapIt.TILE_MAP, tiles)
                return MenuReader.read(vars, tiles)
            }
            assertEquals(MenuKind.YES_NO, menu().kind)

            val executor = CommandExecutor()
            executor.start(GameCommand.Answer(yes = false))
            var frames = 0
            while (executor.busy && frames < 600) {
                NativeCore.nativeSetKeys(h, executor.step(menu(), inBattle = false))
                NativeCore.nativeRunFrame(h, pixels, audio)
                frames++
            }
            assertFalse(executor.busy)
            NativeCore.nativeSetKeys(h, 0)
            repeat(180) { NativeCore.nativeRunFrame(h, pixels, audio) }
            // Dati del compagno completati: specie nella struttura e soprannome chiuso da 0x50.
            assertEquals(0x54, NativeCore.nativeReadMemory(h, MemoryMapIt.PARTY_MONS))
            assertTrue(NativeCore.nativeReadMemory(h, MemoryMapIt.PARTY_MON_NICKS) in 0x80..0x99)
        } finally {
            NativeCore.nativeDestroy(h)
        }
    }
}
