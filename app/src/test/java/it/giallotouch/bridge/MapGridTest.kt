package it.giallotouch.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Percorso sulla griglia dei passi: dislivelli delle grotte come nel gioco (D-120). */
class MapGridTest {

    /** Corridoio 3×1: tile 0x20, 0x05, 0x20; tutti calpestabili. */
    private fun corridoio(tileset: Int) =
        MapGrid(3, 1, BooleanArray(3) { true }, emptySet(), intArrayOf(0x20, 0x05, 0x20), tileset = tileset)

    @Test
    fun grotta_dislivelloNonSiAttraversa() {
        assertNull(corridoio(17).path(0, 0, setOf(2 to 0)))
    }

    @Test
    fun fuoriDallaGrotta_stessiTileSiAttraversano() {
        assertEquals(2, corridoio(0).path(0, 0, setOf(2 to 0))?.size)
    }

    @Test
    fun grotta_ilPercorsoGiraIntorno() {
        // 3×2: sopra 0x20 0x05 0x20 (dislivello), sotto pavimento 0x20: si passa da sotto.
        val g = MapGrid(3, 2, BooleanArray(6) { true }, emptySet(), intArrayOf(0x20, 0x05, 0x20, 0x20, 0x20, 0x20), tileset = 17)
        assertEquals(4, g.path(0, 0, setOf(2 to 0))?.size)
    }
}
