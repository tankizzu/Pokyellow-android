package it.giallotouch.bridge

import it.giallotouch.emu.NativeCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Prova dell'esecutore sul gioco vero (motore SameBoy compilato per il computer).
 * Viene saltata se non si forniscono ROM, stato salvato al menu di lotta e libreria del motore.
 */
class RealGameBattleTest {

    @Test
    fun sceglieLaSecondaMossaNelMenuOriginale() {
        val rom = System.getProperty("giallo.rom")?.let(::File)
        val state = System.getProperty("giallo.state")?.let(::File)
        assumeTrue(rom != null && rom.isFile && state != null && state.isFile)

        val h = NativeCore.nativeCreate(48000)
        try {
            NativeCore.nativeLoadRom(h, rom!!.readBytes())
            assertTrue(NativeCore.nativeLoadState(h, state!!.readBytes()))
            val pixels = IntArray(NativeCore.WIDTH * NativeCore.HEIGHT)
            val audio = ShortArray(8192)
            val vars = ByteArray(MenuReader.MENU_VARS_SIZE)
            val tiles = ByteArray(MenuReader.SCREEN_SIZE)
            fun pp(i: Int) = NativeCore.nativeReadMemory(h, MemoryMapIt.BATTLE_MON + 25 + i) and 0x3F
            val before = pp(1)

            val executor = CommandExecutor(timeoutFrames = 1200)
            executor.start(GameCommand.Fight(1))
            var frames = 0
            while (executor.busy && frames < 1500) {
                NativeCore.nativeReadRange(h, MenuReader.MENU_VARS, vars)
                NativeCore.nativeReadRange(h, MemoryMapIt.TILE_MAP, tiles)
                val inBattle = NativeCore.nativeReadMemory(h, MemoryMapIt.IS_IN_BATTLE) != 0
                NativeCore.nativeSetKeys(h, executor.step(MenuReader.read(vars, tiles), inBattle))
                NativeCore.nativeRunFrame(h, pixels, audio)
                frames++
            }
            assertFalse("l'esecutore non ha finito", executor.busy)
            NativeCore.nativeSetKeys(h, 0)
            repeat(240) { NativeCore.nativeRunFrame(h, pixels, audio) }
            assertEquals("la seconda mossa deve aver consumato 1 PP", before - 1, pp(1))
        } finally {
            NativeCore.nativeDestroy(h)
        }
    }

    /**
     * Due turni con mosse diverse: al secondo turno il gioco ricorda l'ultima mossa usata; l'esecutore deve comunque
     * scegliere quella chiesta, con un solo comando (segnalato dal committente: serviva toccare due volte).
     */
    @Test
    fun dueTurniConMosseDiverse() {
        val rom = System.getProperty("giallo.rom")?.let(::File)
        val state = System.getProperty("giallo.state")?.let(::File)
        assumeTrue(rom != null && rom.isFile && state != null && state.isFile)
        val h = NativeCore.nativeCreate(48000)
        try {
            NativeCore.nativeLoadRom(h, rom!!.readBytes())
            assertTrue(NativeCore.nativeLoadState(h, state!!.readBytes()))
            val pixels = IntArray(NativeCore.WIDTH * NativeCore.HEIGHT)
            val audio = ShortArray(8192)
            val vars = ByteArray(MenuReader.MENU_VARS_SIZE)
            val tiles = ByteArray(MenuReader.SCREEN_SIZE)
            fun pp(i: Int) = NativeCore.nativeReadMemory(h, MemoryMapIt.BATTLE_MON + 25 + i) and 0x3F
            fun menu(): MenuState {
                NativeCore.nativeReadRange(h, MenuReader.MENU_VARS, vars)
                NativeCore.nativeReadRange(h, MemoryMapIt.TILE_MAP, tiles)
                return MenuReader.read(vars, tiles)
            }
            fun turn(move: Int) {
                val ex = CommandExecutor(timeoutFrames = 1200)
                ex.start(GameCommand.Fight(move))
                var f = 0
                while (ex.busy && f < 1500) {
                    NativeCore.nativeSetKeys(h, ex.step(menu(), true)); NativeCore.nativeRunFrame(h, pixels, audio); f++
                }
                assertFalse(ex.busy)
                // testi del turno: A finché torna il menu di lotta (al massimo 40 secondi)
                NativeCore.nativeSetKeys(h, 0)
                var g = 0
                while (menu().kind != MenuKind.BATTLE_MAIN && g < 2400) {
                    NativeCore.nativeSetKeys(h, if (g % 20 < 3) Keys.A else 0); NativeCore.nativeRunFrame(h, pixels, audio); g++
                }
                NativeCore.nativeSetKeys(h, 0)
            }
            val a0 = pp(0); val a1 = pp(1)
            turn(1)
            assertEquals("primo turno: seconda mossa", a1 - 1, pp(1))
            assertEquals(a0, pp(0))
            turn(0)
            assertEquals("secondo turno: prima mossa", a0 - 1, pp(0))
            assertEquals("la seconda mossa non va usata di nuovo", a1 - 1, pp(1))
        } finally {
            NativeCore.nativeDestroy(h)
        }
    }
}
