package it.giallotouch.bridge

/**
 * Aiuti per i testi del gioco, richiesti dal committente:
 * - testo automatico a 3 velocità: quando il gioco aspetta un tocco (freccia ▼ nel riquadro), dopo una pausa
 *   preme A da solo;
 * - tenendo premuto il dito (levetta) mentre c'è un testo, il testo scorre più veloce (B a impulsi: nel gioco
 *   tenere premuto un tasto accelera la scrittura e B fa anche andare avanti).
 * Non preme mai nulla se sullo schermo c'è un cursore ▶ (menu, domande Sì/No, tastiera dei nomi): le scelte
 * restano sempre del giocatore.
 */
class TextAssist {
    /** 0 = spento, 1 = lento, 2 = medio, 3 = veloce. */
    var autoSpeed: Int = 0
    @Volatile var holding: Boolean = false

    private var frame = 0L
    private var lastArrowFrame = -1000L
    private var waitingSince = -1L
    private var pressFrames = 0

    /** Una chiamata per fotogramma con la mappa dello schermo (wTileMap, 20×18). Restituisce i tasti da aggiungere. */
    fun step(tileMap: ByteArray): Int {
        frame++
        if (tileMap.size < SCREEN_SIZE) return 0
        fun tile(x: Int, y: Int) = tileMap[y * 20 + x].toInt() and 0xFF
        val textBox = tile(0, 12) == BOX_TOP_LEFT && tile(0, 17) == BOX_BOTTOM_LEFT
        var cursor = false
        for (i in 0 until SCREEN_SIZE) if ((tileMap[i].toInt() and 0xFF) == CURSOR) { cursor = true; break }
        if (!textBox || cursor) { waitingSince = -1; pressFrames = 0; return 0 }

        // La freccia ▼ lampeggia: la si considera presente se vista negli ultimi 40 fotogrammi.
        if (tile(18, 16) == ARROW) lastArrowFrame = frame
        val waiting = frame - lastArrowFrame < 40

        if (pressFrames > 0) { pressFrames--; return Keys.A }
        if (holding) return if (frame % 6 < 3) Keys.B else 0

        if (autoSpeed == 0 || !waiting) { waitingSince = -1; return 0 }
        if (waitingSince < 0) waitingSince = frame
        if (frame - waitingSince >= DELAYS[autoSpeed.coerceIn(1, 3)]) {
            waitingSince = -1
            lastArrowFrame = -1000L   // aspetta una nuova freccia prima di premere ancora
            pressFrames = 3
            return Keys.A
        }
        return 0
    }

    companion object {
        const val SCREEN_SIZE = 20 * 18
        private const val CURSOR = 0xED
        private const val ARROW = 0xEE
        private const val BOX_TOP_LEFT = 0x79
        private const val BOX_BOTTOM_LEFT = 0x7D
        /** Pausa prima di andare avanti, in fotogrammi (circa 60 al secondo): lento 2 s, medio 1 s, veloce 0,4 s. */
        private val DELAYS = intArrayOf(0, 120, 60, 24)
        val LABELS = listOf("No", "Lento", "Medio", "Veloce")
    }
}
