package it.giallotouch.bridge

import it.giallotouch.emu.NativeCore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Vista estesa sul gioco vero: nel paese iniziale è attiva e al centro contiene esattamente il fotogramma
 * originale, anche durante un passo; durante la lotta si spegne. Saltata senza ROM, stati e libreria.
 */
class RealGameExtendedViewTest {

    private fun open(stateKey: String): Pair<Long, Boolean>? {
        val rom = System.getProperty("giallo.rom")?.let(::File)
        val state = System.getProperty(stateKey)?.let(::File)
        if (rom == null || !rom.isFile || state == null || !state.isFile) return null
        val h = NativeCore.nativeCreate(48000)
        NativeCore.nativeLoadRom(h, rom.readBytes())
        return h to NativeCore.nativeLoadState(h, state.readBytes())
    }

    @Test
    fun attivaInEsplorazioneConFotogrammaAlCentro() {
        val (h, ok) = open("giallo.state.pallet") ?: return assumeTrue(false)
        try {
            assumeTrue(ok)
            val frame = IntArray(160 * 144); val audio = ShortArray(8192)
            val height = 354
            val out = IntArray(160 * height)
            for (step in 0 until 3) {
                // fotogramma da fermo, poi a metà di un passo verso destra
                NativeCore.nativeSetKeys(h, if (step == 1) Keys.RIGHT else 0)
                repeat(if (step == 1) 5 else 20) { NativeCore.nativeRunFrame(h, frame, audio) }
                assertEquals("vista estesa attiva", 1, NativeCore.nativeRenderExtended(h, out, height, -1))
                val top = (height - 144) / 2
                // al centro l'immagine del gioco; i luoghi all'aperto possono avere i colori delle strade (D-069):
                // ogni colore del gioco corrisponde sempre allo stesso colore mostrato (stessa immagine)
                val center = out.copyOfRange(top * 160, (top + 144) * 160)
                val map = HashMap<Int, Int>()
                for (i in frame.indices) assertEquals("stessa immagine", map.getOrPut(frame[i]) { center[i] }, center[i])
            }
        } finally { NativeCore.nativeDestroy(h) }
    }

    @Test
    fun spentaInLotta() {
        val (h, ok) = open("giallo.state") ?: return assumeTrue(false)
        try {
            assumeTrue(ok)
            val frame = IntArray(160 * 144); val audio = ShortArray(8192)
            NativeCore.nativeRunFrame(h, frame, audio)
            assertEquals(0, NativeCore.nativeRenderExtended(h, IntArray(160 * 354), 354, -1))
        } finally { NativeCore.nativeDestroy(h) }
    }
}
