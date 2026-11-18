package it.giallotouch.bridge

/**
 * Cammina lungo un percorso premendo le frecce del gioco, un passo alla volta, e controlla in memoria
 * che ogni passo sia avvenuto (circuito chiuso). Alla fine può girarsi verso un bersaglio e premere A.
 * Si ferma subito se il gioco prende il controllo (scena, lotta, testo) o se un passo non riesce:
 * le collisioni le decide sempre il gioco.
 */
class WalkExecutor(private val maxFramesPerStep: Int = 50) {
    private enum class Phase { IDLE, WALK, SETTLE, TURN, PAUSE, PRESS_A }

    data class Position(val map: Int, val x: Int, val y: Int)

    private var phase = Phase.IDLE
    private val steps = ArrayDeque<MapGrid.Dir>()
    private var face: MapGrid.Dir? = null
    private var stepStart: Position? = null
    private var counter = 0

    val busy: Boolean get() = phase != Phase.IDLE

    fun start(path: List<MapGrid.Dir>, faceAtEnd: MapGrid.Dir?) {
        steps.clear(); steps.addAll(path)
        face = faceAtEnd
        stepStart = null
        counter = 0
        phase = if (steps.isNotEmpty()) Phase.WALK else if (face != null) Phase.TURN else Phase.IDLE
    }

    fun cancel() { phase = Phase.IDLE; steps.clear() }

    /** Una chiamata per fotogramma. [gameBusy] = scena, lotta, testo o menu in corso. */
    fun step(pos: Position, gameBusy: Boolean): Int {
        if (phase == Phase.IDLE) return 0
        if (gameBusy) { cancel(); return 0 }
        return when (phase) {
            Phase.WALK -> {
                val start = stepStart ?: pos.also { stepStart = it; counter = 0 }
                if (pos != start) {
                    if (pos.map != start.map) { cancel(); return 0 }   // porta o scala: si è cambiato luogo
                    steps.removeFirst()
                    stepStart = null
                    if (steps.isEmpty()) { phase = if (face != null) Phase.SETTLE else Phase.IDLE; counter = 0; return 0 }
                    return step(pos, gameBusy)
                }
                if (++counter > maxFramesPerStep) { cancel(); return 0 }  // passo non riuscito: ci si ferma
                steps.first().key
            }
            Phase.SETTLE -> { if (++counter >= 18) { phase = Phase.TURN; counter = 0 }; 0 }
            Phase.TURN -> { if (++counter >= 4) { phase = Phase.PAUSE; counter = 0 }; face!!.key }
            Phase.PAUSE -> { if (++counter >= 8) { phase = Phase.PRESS_A; counter = 0 }; 0 }
            Phase.PRESS_A -> { if (++counter >= 4) { phase = Phase.IDLE }; Keys.A }
            Phase.IDLE -> 0
        }
    }
}
