package it.giallotouch.bridge

/** Riconosce dallo schermo (wTileMap) se si vede solo la mappa, senza riquadri di testo o menu. */
object FreeRoam {
    private const val CURSOR = 0xED
    private const val BOX_TOP_LEFT = 0x79
    private const val BOX_BOTTOM_LEFT = 0x7D

    fun screenIsMap(tileMap: ByteArray): Boolean {
        if (tileMap.size < MenuReader.SCREEN_SIZE) return false
        fun tile(x: Int, y: Int) = tileMap[y * 20 + x].toInt() and 0xFF
        // Riquadro di testo in basso (righe 12–17).
        if (tile(0, 12) == BOX_TOP_LEFT && tile(0, 17) == BOX_BOTTOM_LEFT) return false
        // Un cursore ▶ visibile significa un menu aperto (per esempio il menu START).
        for (i in 0 until MenuReader.SCREEN_SIZE) if ((tileMap[i].toInt() and 0xFF) == CURSOR) return false
        return true
    }
}
