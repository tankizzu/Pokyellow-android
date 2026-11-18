package it.giallotouch.bridge

import it.giallotouch.emu.NativeCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Sul gioco vero: il nome scritto con la tastiera del telefono viene inserito nella schermata dei nomi del gioco
 * muovendo il cursore (anche cancellando una lettera già scritta e cambiando maiuscole/minuscole).
 * Saltata se mancano ROM, stati (giallo.state.naming, giallo.state.text) e libreria del motore.
 */
class RealGameNamingTest {

    private class Game(val h: Long) {
        val pixels = IntArray(NativeCore.WIDTH * NativeCore.HEIGHT)
        val audio = ShortArray(8192)
        val vars = ByteArray(MenuReader.MENU_VARS_SIZE)
        val tiles = ByteArray(MenuReader.SCREEN_SIZE)
        val name = ByteArray(NamingReader.NAME_BUFFER_SIZE)
        fun frame(keys: Int = 0) { NativeCore.nativeSetKeys(h, keys); NativeCore.nativeRunFrame(h, pixels, audio) }
        fun naming(): NamingScreen? {
            NativeCore.nativeReadRange(h, MenuReader.MENU_VARS, vars)
            NativeCore.nativeReadRange(h, MemoryMapIt.TILE_MAP, tiles)
            NativeCore.nativeReadRange(h, NamingReader.NAME_BUFFER, name)
            return NamingReader.read(vars, tiles, name)
        }
        fun menu(): MenuState = naming().let { MenuReader.read(vars, tiles) }
        fun write(text: String): Int {
            val ex = NameExecutor().apply { start(NameCodec.encode(text)!!) }
            var frames = 0
            while (ex.busy && frames < 60 * 60) { frame(ex.step(naming())); frames++ }
            assertEquals(NameExecutor.Result.DONE, ex.result)
            return frames
        }
        fun read(address: Int, n: Int) = ByteArray(n).also { NativeCore.nativeReadRange(h, address, it) }
    }

    private fun withGame(stateKey: String, body: (Game) -> Unit) {
        val rom = System.getProperty("giallo.rom")?.let(::File)
        val state = System.getProperty(stateKey)?.let(::File)
        assumeTrue(rom != null && rom.isFile && state != null && state.isFile)
        val h = NativeCore.nativeCreate(48000)
        try {
            NativeCore.nativeLoadRom(h, rom!!.readBytes())
            assumeTrue(NativeCore.nativeLoadState(h, state!!.readBytes()))
            body(Game(h))
        } finally { NativeCore.nativeDestroy(h) }
    }

    @Test
    fun nomeDelGiocatore() = withGame("giallo.state.naming") { g ->
        g.frame()
        val start = g.naming()
        assertNotNull("schermata dei nomi", start)
        assertEquals(7, start!!.maxLength)
        // una lettera già scritta a mano, che l'esecutore deve cancellare
        repeat(3) { g.frame(Keys.A) }; repeat(20) { g.frame() }
        assertEquals(listOf(0x80), g.naming()!!.name)
        val frames = g.write("Ro sa!")
        println("nome del giocatore scritto in $frames fotogrammi")
        repeat(30) { g.frame() }
        assertEquals("Ro sa!", TextCodec.decode(g.read(MemoryMapIt.PLAYER_NAME, 11)))
    }

    @Test
    fun soprannome() = withGame("giallo.state.text") { g ->
        // Testo fino alla domanda, poi "Sì" e attesa della schermata dei nomi.
        val assist = TextAssist().apply { autoSpeed = 3 }
        var f = 0
        while (g.menu().kind != MenuKind.YES_NO && f++ < 1200) g.frame(assist.step(g.tiles))
        val ex = CommandExecutor().apply { start(GameCommand.Answer(true)) }
        f = 0
        while (g.naming() == null && f++ < 600) g.frame(ex.step(MenuReader.read(g.vars, g.tiles), false))
        val screen = g.naming()
        assertNotNull("schermata del soprannome", screen)
        assertEquals(10, screen!!.maxLength)
        val frames = g.write("Pika Max.")
        println("soprannome scritto in $frames fotogrammi; titolo: ${screen.title}")
    }
}
