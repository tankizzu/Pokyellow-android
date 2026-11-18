package it.giallotouch.emu

/**
 * Il riquadro Sì/No del gioco non si vede (D-046): l'app mostra le risposte nei suoi riquadri sotto il dialogo.
 * Al suo posto si rimette l'immagine di qualche fotogramma prima della domanda (durante la domanda la mappa è ferma).
 * Si tengono due copie che si scambiano ogni 8 fotogrammi, così quella usata ha 8–16 fotogrammi: il riquadro del gioco
 * si disegna in più fotogrammi prima che compaia il cursore.
 */
class YesNoMask(private val width: Int = NativeCore.WIDTH) {
    private var before: IntArray? = null
    private var recent: IntArray? = null
    private var beforeTop = -1
    private var recentTop = -1
    private var frames = 0

    /** Immagine del gioco di 8–16 fotogrammi fa (160×144), se c'è. */
    val older: IntArray? get() = before?.takeIf { beforeTop == 0 && it.size == width * NativeCore.HEIGHT }

    /**
     * [fallback]: immagine del gioco 160×144 da usare se la vista estesa non ha una copia di prima della domanda
     * (durante i dialoghi la vista estesa può spegnersi): va messa all'altezza dell'inquadratura ([frameTop]).
     */
    fun apply(pixels: IntArray, height: Int, frameTop: Int, yn: GameSession.YesNo?, fallback: IntArray? = null) {
        if (yn == null) {
            if (frames++ % 8 == 0) {
                val old = before
                before = recent; beforeTop = recentTop
                recent = (if (old != null && old.size == pixels.size) old else IntArray(pixels.size)).also { System.arraycopy(pixels, 0, it, 0, pixels.size) }
                recentTop = frameTop
            }
            return
        }
        val x = yn.x0 * 8
        val w = ((yn.x1 + 1 - yn.x0) * 8).coerceAtMost(width - x)
        val b = before?.takeIf { it.size == pixels.size && beforeTop == frameTop }
        for (y in (frameTop + yn.y0 * 8) until minOf(height, frameTop + (yn.y1 + 1) * 8)) {
            when {
                b != null -> System.arraycopy(b, y * width + x, pixels, y * width + x, w)
                fallback != null -> System.arraycopy(fallback, (y - frameTop) * width + x, pixels, y * width + x, w)
            }
        }
    }
}
