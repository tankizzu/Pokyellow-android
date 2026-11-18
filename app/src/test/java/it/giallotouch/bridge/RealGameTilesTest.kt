package it.giallotouch.bridge

import it.giallotouch.emu.NativeCore
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Sul gioco vero, al menu iniziale: il riquadro "NUOVO GIOCO / OPZIONI" ridisegnato dall'app con i tile del gioco
 * è identico, pixel per pixel, a quello che disegna il gioco. Saltata senza ROM, stato e libreria.
 */
class RealGameTilesTest {
    @Test
    fun menuIdenticoAlGioco() {
        val rom = System.getProperty("giallo.rom")?.let(::File)
        val state = System.getProperty("giallo.state.title")?.let(::File)
        assumeTrue(rom != null && rom.isFile && state != null && state.isFile)
        val h = NativeCore.nativeCreate(48000)
        try {
            NativeCore.nativeLoadRom(h, rom!!.readBytes())
            assumeTrue(NativeCore.nativeLoadState(h, state!!.readBytes()))
            val frame = IntArray(160 * 144)
            repeat(3) { NativeCore.nativeRunFrame(h, frame, ShortArray(8192)) }
            val sheet = IntArray(TileCanvas.SHEET_SIZE)
            assertEquals("colori presi dallo schermo", 1, NativeCore.nativeGameTiles(h, sheet))
            val c = TileCanvas(15, 6, sheet)
            c.box(0, 0, 14, 5)
            c.text("▶NUOVO GIOCO", 1, 2)
            c.text("OPZIONI", 2, 4)
            var diff = 0
            for (y in 0 until c.height) for (x in 0 until c.width) if (c.pixels[y * c.width + x] != frame[y * 160 + x]) diff++
            assertEquals("pixel diversi dal gioco", 0, diff)
        } finally { NativeCore.nativeDestroy(h) }
    }
}
