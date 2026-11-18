package it.giallotouch.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkTest {

    /** Griglia sintetica da righe di testo: '.' libero, '#' bloccato, 'P' persona, 'C' bancone. */
    private fun grid(vararg rows: String): MapGrid {
        val w = rows[0].length; val h = rows.size
        val passable = BooleanArray(w * h); val tiles = IntArray(w * h)
        val people = HashSet<Pair<Int, Int>>()
        rows.forEachIndexed { y, r -> r.forEachIndexed { x, c ->
            passable[y * w + x] = c == '.' || c == 'P'
            tiles[y * w + x] = if (c == 'C') 0x42 else 0
            if (c == 'P') people += x to y
        } }
        return MapGrid(w, h, passable, people, tiles, setOf(0x42))
    }

    @Test
    fun percorsoPiuBreveEvitandoOstacoli() {
        val g = grid(
            ".....",
            ".###.",
            ".....",
        )
        val path = g.path(0, 2, setOf(4 to 0))!!
        assertEquals(6, path.size)
        assertNull(grid("..#..").path(0, 0, setOf(4 to 0)))
    }

    @Test
    fun personaBloccaEPostiPerParlarci() {
        val g = grid(
            ".....",
            "..P..",
            ".....",
        )
        assertFalse(g.isFree(2, 1))
        val spots = g.interactionSpots(2, 1)
        assertEquals(MapGrid.Dir.DOWN, spots[2 to 0])   // sopra la persona, guardando in giù
        assertEquals(MapGrid.Dir.UP, spots[2 to 2])
        assertEquals(4, spots.size)
    }

    @Test
    fun cosaSiPuoToccare() {
        val g = MapGrid(3, 1, booleanArrayOf(true, true, false), setOf(0 to 0), IntArray(3), emptySet(), setOf(2 to 0))
        assertTrue(g.isInteractive(0, 0))   // persona
        assertFalse(g.isInteractive(1, 0))  // terreno libero
        assertTrue(g.isInteractive(2, 0))   // cartello
    }

    @Test
    fun parlareAttraversoIlBancone() {
        val g = grid(
            "..P..",
            "##C##",
            ".....",
        )
        val spots = g.interactionSpots(2, 0)
        assertEquals(MapGrid.Dir.UP, spots[2 to 2])     // dall'altra parte del bancone
    }

    /** Simula il gioco: un passo riesce dopo alcuni fotogrammi se la casella è libera. */
    @Test
    fun camminataACircuitoChiusoEInterazione() {
        val g = grid("......", ".....P")
        var pos = WalkExecutor.Position(0, 0, 0)
        val ex = WalkExecutor()
        val spots = g.interactionSpots(5, 1)
        val path = g.path(0, 0, spots.keys)!!
        ex.start(path, spots[path.fold(0 to 0) { p, d -> p.first + d.dx to p.second + d.dy }])
        var held = 0; var pressedA = false; var lastFacing = 0
        repeat(600) {
            if (!ex.busy) return@repeat
            val keys = ex.step(pos, gameBusy = false)
            if (keys and Keys.A != 0) pressedA = true
            val dir = MapGrid.Dir.entries.firstOrNull { it.key == keys }
            if (dir != null) {
                lastFacing = keys
                held++
                if (held >= 3 && g.isFree(pos.x + dir.dx, pos.y + dir.dy)) { pos = pos.copy(x = pos.x + dir.dx, y = pos.y + dir.dy); held = 0 }
            } else held = 0
        }
        assertFalse(ex.busy)
        assertTrue(pressedA)
        // Arrivato in uno dei posti accanto alla persona (sono equidistanti) e girato verso di lei.
        val arrived = pos.x to pos.y
        assertTrue(arrived in spots.keys)
        assertEquals(spots.getValue(arrived).key, lastFacing)
    }

    @Test
    fun siFermaSeIlGiocoPrendeIlControllo() {
        val ex = WalkExecutor()
        ex.start(listOf(MapGrid.Dir.RIGHT, MapGrid.Dir.RIGHT), null)
        assertEquals(0, ex.step(WalkExecutor.Position(0, 0, 0), gameBusy = true))
        assertFalse(ex.busy)
    }

    @Test
    fun schermoConTestoOMenu_nonELiberoMovimento() {
        val map = ByteArray(MenuReader.SCREEN_SIZE) { 0x10 }
        assertTrue(FreeRoam.screenIsMap(map))
        val text = map.copyOf().also { it[12 * 20] = 0x79.toByte(); it[17 * 20] = 0x7D.toByte() }
        assertFalse(FreeRoam.screenIsMap(text))
        val menu = map.copyOf().also { it[2 * 20 + 11] = 0xED.toByte() }
        assertFalse(FreeRoam.screenIsMap(menu))
    }
}

class HiddenSpritesTest {
    @org.junit.Test
    fun nascostiSecondoLaListaDelGioco() {
        // sprite 1 → oggetto globale 0 (nascosto), sprite 2 → oggetto 0x2B (visibile), poi fine lista
        val list = byteArrayOf(1, 0, 2, 0x2B, 0xFF.toByte(), 0)
        val flags = ByteArray(32).also { it[0] = 0x01 }
        org.junit.Assert.assertEquals(setOf(1), MapGrid.hiddenSprites(list, flags))
        flags[5] = 0x08 // bit 0x2B = byte 5, bit 3
        org.junit.Assert.assertEquals(setOf(1, 2), MapGrid.hiddenSprites(list, flags))
    }
}
