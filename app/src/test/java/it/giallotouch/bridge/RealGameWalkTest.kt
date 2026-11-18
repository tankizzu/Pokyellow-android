package it.giallotouch.bridge

import it.giallotouch.emu.NativeCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Prova sul gioco vero: nel paese iniziale si costruisce la griglia dei passi, si calcola un percorso
 * e lo si percorre con l'esecutore. Saltata se mancano ROM, stato (giallo.state.pallet) e libreria.
 */
class RealGameWalkTest {

    @Test
    fun raggiungeIlPassoToccato() {
        val rom = System.getProperty("giallo.rom")?.let(::File)
        val state = System.getProperty("giallo.state.pallet")?.let(::File)
        assumeTrue(rom != null && rom.isFile && state != null && state.isFile)
        val romBytes = rom!!.readBytes()
        val h = NativeCore.nativeCreate(48000)
        try {
            NativeCore.nativeLoadRom(h, romBytes)
            NativeCore.nativeLoadState(h, state!!.readBytes())
            val pixels = IntArray(NativeCore.WIDTH * NativeCore.HEIGHT)
            val audio = ShortArray(8192)
            fun read(a: Int, n: Int) = ByteArray(n).also { NativeCore.nativeReadRange(h, a, it) }
            fun pos() = WalkExecutor.Position(
                NativeCore.nativeReadMemory(h, MemoryMapIt.CUR_MAP),
                NativeCore.nativeReadMemory(h, MemoryMapIt.X_COORD),
                NativeCore.nativeReadMemory(h, MemoryMapIt.Y_COORD))
            val start = pos()
            val grid = MapGrid.build(::read, romBytes)
            assertNotNull(grid)
            // Obiettivo: un passo libero lontano, scelto sulla griglia (a sinistra in basso del paese).
            val target = (1..grid!!.width - 2).flatMap { x -> (1..grid.height - 2).map { y -> x to y } }
                .filter { grid.isFree(it.first, it.second) && it.second > start.y + 3 }
                .minByOrNull { it.first + it.second }!!
            val path = grid.path(start.x, start.y, setOf(target))
            assertNotNull("percorso", path)
            val ex = WalkExecutor()
            ex.start(path!!, null)
            var frames = 0
            while (ex.busy && frames < 3000) {
                val busy = NativeCore.nativeReadMemory(h, MemoryMapIt.JOY_IGNORE) != 0
                NativeCore.nativeSetKeys(h, ex.step(pos(), busy))
                NativeCore.nativeRunFrame(h, pixels, audio)
                frames++
            }
            assertFalse(ex.busy)
            NativeCore.nativeSetKeys(h, 0)
            repeat(30) { NativeCore.nativeRunFrame(h, pixels, audio) }
            assertEquals(WalkExecutor.Position(start.map, target.first, target.second), pos())
        } finally {
            NativeCore.nativeDestroy(h)
        }
    }
}
