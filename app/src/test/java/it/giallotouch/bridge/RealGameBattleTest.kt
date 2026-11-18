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
            val vars = ByteArray(BattleMenuReader.MENU_VARS_SIZE)
            val tiles = ByteArray(BattleMenuReader.SCREEN_SIZE)
            fun pp(i: Int) = NativeCore.nativeReadMemory(h, MemoryMapIt.BATTLE_MON + 25 + i) and 0x3F
            val before = pp(1)

            val executor = BattleCommandExecutor(timeoutFrames = 1200)
            executor.start(BattleCommand.Fight(1))
            var frames = 0
            while (executor.busy && frames < 1500) {
                NativeCore.nativeReadRange(h, BattleMenuReader.MENU_VARS, vars)
                NativeCore.nativeReadRange(h, MemoryMapIt.TILE_MAP, tiles)
                val inBattle = NativeCore.nativeReadMemory(h, MemoryMapIt.IS_IN_BATTLE) != 0
                NativeCore.nativeSetKeys(h, executor.step(BattleMenuReader.read(vars, tiles), inBattle))
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
}
