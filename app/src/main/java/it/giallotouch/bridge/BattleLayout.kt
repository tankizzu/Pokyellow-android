package it.giallotouch.bridge

/**
 * Lotta a tutto schermo (D-018): la schermata di lotta del gioco si divide in scena (righe 0–11 di tile,
 * 96 pixel: creature e barre) e riquadro del testo (righe 12–17, 48 pixel). L'app le mostra separate, senza deformarle,
 * con lo sfondo della lotta esteso a tutto lo schermo e i pulsanti nativi sopra.
 *
 * Si divide solo quando il riquadro del testo occupa davvero le righe 12–17 (angoli a destra in (19,12) e (19,17),
 * angolo in basso a sinistra in (0,17)): così anche il menu delle mosse e la scelta della squadra restano leggibili.
 * Schermate a tutto schermo senza riquadro (statistiche) restano intere.
 */
object BattleLayout {
    const val SCENE_ROWS = 96
    const val TEXT_ROWS = 48
    /** Posizioni scelte dal committente (D-045), in righe da 8 pixel dall'alto dello schermo. */
    const val TEXT_TOP_ROW = 4
    const val SCENE_TOP_ROW = 10
    const val ACTIONS_TOP_ROW = 22
    const val MOVES_TOP_ROW = 27

    private const val CORNER_TR = 0x7B
    private const val CORNER_BL = 0x7D
    private const val CORNER_BR = 0x7E

    fun textBoxAtBottom(tileMap: ByteArray): Boolean {
        if (tileMap.size < MenuReader.SCREEN_SIZE) return false
        fun tile(x: Int, y: Int) = tileMap[y * MenuReader.SCREEN_WIDTH + x].toInt() and 0xFF
        return tile(19, 12) == CORNER_TR && tile(0, 17) == CORNER_BL && tile(19, 17) == CORNER_BR
    }

    /** Colore più frequente in un campione della scena: lo sfondo della lotta. */
    fun backgroundColor(frame: IntArray, width: Int = 160): Int {
        val colors = IntArray(16)
        val counts = IntArray(16)
        var used = 0
        var y = 2
        while (y < SCENE_ROWS) {
            var x = 1
            while (x < width) {
                val c = frame[y * width + x]
                var i = 0
                while (i < used && colors[i] != c) i++
                if (i == used && used < colors.size) { colors[used] = c; used++ }
                if (i < used) counts[i]++
                x += 6
            }
            y += 5
        }
        var best = 0
        for (i in 1 until used) if (counts[i] > counts[best]) best = i
        return if (used == 0) 0xFF000000.toInt() else colors[best]
    }
}
