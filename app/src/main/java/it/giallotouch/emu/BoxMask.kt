package it.giallotouch.emu

/**
 * Riquadri del gioco che l'app ridisegna altrove (D-046, D-048): il riquadro Sì/No e il riquadro del dialogo in basso.
 * Al loro posto si rimette l'immagine di qualche fotogramma prima (durante un dialogo la mappa è ferma).
 * Si tengono due copie che si scambiano ogni 8 fotogrammi, così quella usata ha 8–16 fotogrammi: il riquadro del gioco
 * si disegna in più fotogrammi prima che compaia il cursore.
 */
class BoxMask(private val width: Int = NativeCore.WIDTH) {
    private var before: IntArray? = null
    private var recent: IntArray? = null
    private var beforeTop = -1
    private var recentTop = -1
    private var frames = 0

    /** C'è un'immagine di prima (senza i riquadri da spostare). */
    val ready: Boolean get() = before != null

    fun reset() { before = null; recent = null; beforeTop = -1; recentTop = -1; frames = 0 }

    /** Immagine del gioco di 8–16 fotogrammi fa (160×144), se c'è. */
    val older: IntArray? get() = before?.takeIf { beforeTop == 0 && it.size == width * NativeCore.HEIGHT }

    /**
     * [fallback]: immagine del gioco 160×144 da usare se la vista estesa non ha una copia di prima della domanda
     * (durante i dialoghi la vista estesa può spegnersi): va messa all'altezza dell'inquadratura ([frameTop]).
     */
    /** Riquadro in caselle dell'inquadratura del gioco, angoli compresi. */
    class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int)

    /** [boxesOnScreen]: ci sono riquadri da spostare (anche se non si possono coprire): l'immagine non si conserva. */
    private fun blank(pixels: IntArray, height: Int, frameTop: Int): Boolean {
        val end = minOf(pixels.size, (frameTop + 144).coerceAtMost(height) * width)
        val start = frameTop * width
        if (start >= end) return false
        val first = pixels[start]
        var i = start
        while (i < end) { if (pixels[i] != first) return false; i += 7 }
        return true
    }

    fun apply(pixels: IntArray, height: Int, frameTop: Int, boxes: List<Box>, fallback: IntArray? = null, boxesOnScreen: Boolean = boxes.isNotEmpty()) {
        if (boxes.isEmpty()) {
            if (boxesOnScreen) return
            // Le schermate tutte di un colore (passaggi tra scene, es. prima della lista dopo un KO) non si tengono:
            // sotto i riquadri resterebbe un'immagine bianca.
            if (blank(pixels, height, frameTop)) return
            if (frames++ % 8 == 0) {
                val old = before
                before = recent; beforeTop = recentTop
                recent = (if (old != null && old.size == pixels.size) old else IntArray(pixels.size)).also { System.arraycopy(pixels, 0, it, 0, pixels.size) }
                recentTop = frameTop
            }
            return
        }
        val b = before?.takeIf { it.size == pixels.size && beforeTop == frameTop }
        for (yn in boxes) {
        val x = yn.x0 * 8
        val w = ((yn.x1 + 1 - yn.x0) * 8).coerceAtMost(width - x)
        for (y in (frameTop + yn.y0 * 8) until minOf(height, frameTop + (yn.y1 + 1) * 8)) {
            when {
                b != null -> System.arraycopy(b, y * width + x, pixels, y * width + x, w)
                fallback != null -> System.arraycopy(fallback, (y - frameTop) * width + x, pixels, y * width + x, w)
            }
        }
        }
    }
}
