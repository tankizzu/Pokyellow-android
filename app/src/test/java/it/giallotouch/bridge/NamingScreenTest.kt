package it.giallotouch.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Schermata dei nomi su dati sintetici con la stessa disposizione del gioco italiano. */
class NamingScreenTest {

    private val upperRows = listOf(
        (0 until 9).map { 0x80 + it },            // A–I
        (9 until 18).map { 0x80 + it },           // J–R
        (18 until 26).map { 0x80 + it } + 0x7F,   // S–Z, spazio
        listOf(0xF1, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F, 0xE1, 0xE2),
        listOf(0xE3, 0xE6, 0xE7, 0xEF, 0xF5, 0xF3, 0xF2, 0xF4, 0xF0),
    )

    private fun screen(lower: Boolean = false, maxLength: Int = 7, row: Int = 1, x: Int = 1, name: List<Int> = emptyList()): Triple<ByteArray, ByteArray, ByteArray> {
        val tiles = ByteArray(360) { 0x7F }
        fun put(x: Int, y: Int, v: Int) { tiles[y * 20 + x] = v.toByte() }
        "NOME TUO?".forEachIndexed { i, c -> put(i, 1, if (c == ' ') 0x7F else if (c == '?') 0xE6 else 0x80 + (c - 'A')) }
        for (i in 0 until maxLength) put(10 + i, 3, if (i == name.size) 0x77 else 0x76)
        put(0, 4, 0x79); put(19, 4, 0x7B); put(0, 14, 0x7D); put(19, 14, 0x7E)
        upperRows.forEachIndexed { r, codes ->
            codes.forEachIndexed { c, v -> put(2 + 2 * c, 5 + 2 * r, if (lower && v in 0x80..0x99) v + 0x20 else v) }
        }
        put(x, 3 + 2 * row, 0xED)
        val vars = byteArrayOf(3, x.toByte(), row.toByte(), 0x7F, 7, 0xFF.toByte(), 1)
        val buf = ByteArray(11) { 0x50 }
        name.forEachIndexed { i, v -> buf[i] = v.toByte() }
        return Triple(vars, tiles, buf)
    }

    private fun read(lower: Boolean = false, maxLength: Int = 7, row: Int = 1, x: Int = 1, name: String = ""): NamingScreen {
        val (v, t, b) = screen(lower, maxLength, row, x, NameCodec.encode(name)!!)
        return NamingReader.read(v, t, b)!!
    }

    @Test
    fun riconosceLaSchermata() {
        val s = read(name = "Ro")
        assertEquals("NOME TUO?", s.title)
        assertEquals(7, s.maxLength)
        assertEquals("Ro", NameCodec.decode(s.name))
        assertEquals(true, s.upperCase)
        assertEquals(1 to 1, s.find(0x80))
        assertEquals(3 to 17, s.find(0x7F))
        assertEquals(5 to 15, s.find(0xF4))
        assertEquals(10, read(maxLength = 10).maxLength)
    }

    @Test
    fun nonRiconosceAltriMenu() {
        val (v, t, b) = screen()
        v[5] = 0x03
        assertNull(NamingReader.read(v, t, b))
        val (v2, t2, b2) = screen()
        t2[4 * 20] = 0x7F
        assertNull(NamingReader.read(v2, t2, b2))
    }

    @Test
    fun puliziaDeiCaratteri() {
        assertEquals("Lucia", NameCodec.sanitize("Lùcìa", 7))
        assertEquals("Ash-K!", NameCodec.sanitize("Ash-K!@#", 7))
        assertEquals("ABCDEFG", NameCodec.sanitize("ABCDEFGHIJ", 7))
        assertEquals("Ro sa", NameCodec.decode(NameCodec.encode("Ro sa")!!))
        assertNull(NameCodec.encode("A1"))
    }

    @Test
    fun scelteDellEsecutore() {
        val goal = NameCodec.encode("Ro")!!
        // lettera sbagliata già scritta → cancella
        assertEquals(Keys.B, NameExecutor.decide(read(name = "X"), goal))
        // nome completo → conferma
        assertEquals(NameExecutor.START, NameExecutor.decide(read(name = "Ro", lower = true), goal))
        // R è nella riga 2, colonna X = 17: dalla colonna 1 conviene andare a sinistra (giro)
        assertEquals(Keys.LEFT, NameExecutor.decide(read(), goal))
        assertEquals(Keys.DOWN, NameExecutor.decide(read(x = 17), goal))
        assertEquals(Keys.A, NameExecutor.decide(read(row = 2, x = 17), goal))
        // "o" minuscola con la griglia maiuscola → SELECT
        assertEquals(NameExecutor.SELECT, NameExecutor.decide(read(name = "R"), goal))
        // sulla riga del cambio maiuscole si torna nella griglia
        assertEquals(Keys.UP, NameExecutor.decide(read(row = 6, name = "R", lower = true), NameCodec.encode("R,")!!))
        assertNotNull(read(row = 6))
    }
}
