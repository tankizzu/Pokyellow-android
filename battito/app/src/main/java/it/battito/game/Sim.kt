package it.battito.game

import kotlin.math.floor

/**
 * Livello a griglia. Ogni carattere è una cella da 1×1 unità ("tile").
 * Riga 0 = appena sopra il pavimento, le righe crescono verso l'alto.
 *
 * Legenda:
 *  .  vuoto
 *  #  blocco neutro (sempre solido)
 *  C  blocco CIANO   (solido solo quando sei ciano)
 *  M  blocco MAGENTA (solido solo quando sei magenta)
 *  ^  spuntone a terra      v  spuntone appeso
 *  p  pedana di lancio      o  anello di salto (tocca mentre lo attraversi)
 */
class Level(val name: String, rowsTopToBottom: List<String>) {
    val height: Int = rowsTopToBottom.size
    val width: Int = rowsTopToBottom.maxOf { it.length }
    private val cells: Array<CharArray> = Array(height) { y ->
        val row = rowsTopToBottom[height - 1 - y]
        CharArray(width) { x -> if (x < row.length) row[x] else '.' }
    }

    fun at(x: Int, y: Int): Char =
        if (x < 0 || y < 0 || x >= width || y >= height) '.' else cells[y][x]

    companion object {
        /** Unisce pezzi di livello (ognuno: righe dall'alto in basso, allineate in basso). */
        fun fromChunks(name: String, chunks: List<List<String>>): Level {
            val h = chunks.maxOf { it.size }
            val rows = Array(h) { StringBuilder() }
            for (chunk in chunks) {
                val w = chunk.maxOf { it.length }
                val pad = h - chunk.size
                for (r in 0 until h) {
                    val s = if (r < pad) "" else chunk[r - pad]
                    rows[r].append(s.padEnd(w, '.'))
                }
            }
            return Level(name, rows.map { it.toString() })
        }
    }
}

/**
 * Simulazione deterministica a passo fisso. Nessuna dipendenza da Android:
 * la usa sia il gioco sia il "bot" dei test che verifica che il livello sia completabile.
 */
class Sim(val level: Level) {
    companion object {
        const val SPEED = 9f        // tile al secondo
        const val GRAVITY = 90f     // tile/s²
        const val JUMP_V = 20.3f    // salto ≈ 2,3 tile di altezza, ≈ 4 tile di lunghezza
        const val PAD_V = 26f       // pedana ≈ 3,7 tile
        const val ORB_V = 19f
        const val MAX_FALL = 28f
        const val SIZE = 0.9f       // lato del cubo
        const val STEP = 1f / 240f
        private const val EPS = 1e-4f
    }

    var x = 0f; private set
    var y = 0f; private set
    var vy = 0f; private set
    var grounded = true; private set
    /** 0 = ciano, 1 = magenta */
    var phase = 0; private set
    var alive = true; private set
    var won = false; private set
    var held = false; private set
    private var fresh = false

    /** Eventi dell'ultimo passo, per effetti grafici/sonori. */
    var lastEvent = Event.NONE; private set

    enum class Event { NONE, JUMP, PAD, ORB, LAND }

    val progress: Float get() = (x / level.width).coerceIn(0f, 1f)

    fun reset() {
        x = 0f; y = 0f; vy = 0f; grounded = true; phase = 0
        alive = true; won = false; held = false; fresh = false; lastEvent = Event.NONE
    }

    fun copyFrom(o: Sim) {
        x = o.x; y = o.y; vy = o.vy; grounded = o.grounded; phase = o.phase
        alive = o.alive; won = o.won; held = o.held; fresh = o.fresh; lastEvent = o.lastEvent
    }

    fun setHold(h: Boolean) {
        if (h && !held) fresh = true
        if (!h) fresh = false
        held = h
    }

    /** Cambia colore. Se così facendo ti ritrovi dentro un blocco diventato solido, muori. */
    fun togglePhase() {
        if (!alive || won) return
        phase = 1 - phase
        if (overlapsSolid()) alive = false
    }

    fun isSolid(c: Char): Boolean =
        c == '#' || (c == 'C' && phase == 0) || (c == 'M' && phase == 1)

    fun step(dt: Float = STEP) {
        lastEvent = Event.NONE
        if (!alive || won) return

        if (grounded && held) {
            vy = JUMP_V; grounded = false; fresh = false; lastEvent = Event.JUMP
        }
        vy = maxOf(vy - GRAVITY * dt, -MAX_FALL)
        val prevY = y
        val wasGrounded = grounded
        y += vy * dt
        grounded = false
        if (y <= 0f) {
            y = 0f; if (vy < 0f) vy = 0f; grounded = true
        }

        // Atterraggio sopra i blocchi solidi
        if (vy <= 0f) {
            val x0 = floor(x).toInt(); val x1 = floor(x + SIZE - EPS).toInt()
            val y0 = floor(y).toInt(); val y1 = floor(y + SIZE - EPS).toInt()
            for (tx in x0..x1) for (ty in y0..y1) {
                if (!isSolid(level.at(tx, ty))) continue
                val top = ty + 1f
                if (prevY >= top - 0.06f && y < top) {
                    y = top; vy = 0f; grounded = true
                }
            }
        }
        if (grounded && !wasGrounded && lastEvent == Event.NONE) lastEvent = Event.LAND

        x += SPEED * dt
        if (overlapsSolid()) { alive = false; return }

        // Pericoli e oggetti
        val x0 = floor(x).toInt(); val x1 = floor(x + SIZE - EPS).toInt()
        val y0 = floor(y).toInt(); val y1 = floor(y + SIZE - EPS).toInt()
        for (tx in x0..x1) for (ty in y0..y1) {
            when (level.at(tx, ty)) {
                '^' -> if (hits(tx + 0.3f, ty + 0f, tx + 0.7f, ty + 0.55f)) { alive = false; return }
                'v' -> if (hits(tx + 0.3f, ty + 0.45f, tx + 0.7f, ty + 1f)) { alive = false; return }
                'p' -> if (hits(tx + 0f, ty + 0f, tx + 1f, ty + 0.3f) && vy < PAD_V) {
                    vy = PAD_V; grounded = false; lastEvent = Event.PAD
                }
                'o' -> if (fresh && hits(tx + 0.05f, ty + 0.05f, tx + 0.95f, ty + 0.95f)) {
                    vy = ORB_V; grounded = false; fresh = false; lastEvent = Event.ORB
                }
            }
        }

        if (x >= level.width) won = true
    }

    fun overlapsSolid(): Boolean {
        val x0 = floor(x).toInt(); val x1 = floor(x + SIZE).toInt()
        val y0 = floor(y).toInt(); val y1 = floor(y + SIZE).toInt()
        for (tx in x0..x1) for (ty in y0..y1) {
            if (isSolid(level.at(tx, ty)) && hits(tx.toFloat(), ty.toFloat(), tx + 1f, ty + 1f)) return true
        }
        return false
    }

    private fun hits(bx0: Float, by0: Float, bx1: Float, by1: Float): Boolean =
        x < bx1 - EPS && x + SIZE > bx0 + EPS && y < by1 - EPS && y + SIZE > by0 + EPS
}
