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
 *  c  spuntone CIANO   (punge solo se sei ciano)
 *  m  spuntone MAGENTA (punge solo se sei magenta)
 *  p  pedana di lancio      o  anello di salto (tocca mentre lo attraversi)
 *
 * Portali (agiscono su tutta la colonna, a qualunque altezza li attraversi):
 *  N  diventi NAVICELLA (ogni tocco la fa salire, senza tocchi scende)
 *  Q  torni CUBO
 *  <  SPECCHIO: il gioco si gira e corri da destra verso sinistra
 *  >  fine dello specchio
 *  U  SOTTOSOPRA: la gravità tira verso l'alto
 *  D  gravità normale
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

    private val portals = CharArray(width) { x ->
        (0 until height).map { cells[it][x] }.firstOrNull { it in PORTALS } ?: '.'
    }

    /** Il portale presente nella colonna [x] (o '.'). */
    fun portalAt(x: Int): Char = if (x < 0 || x >= width) '.' else portals[x]

    companion object {
        const val PORTALS = "NQ<>UD"

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
        const val BPM = 128f
        /** 4 tile per battito: una battuta (4/4) = 16 colonne del livello. */
        const val SPEED = 4f * BPM / 60f
        const val GRAVITY = 90f     // tile/s²
        const val JUMP_V = 20.3f    // salto ≈ 2,3 tile di altezza, ≈ 3,9 tile di lunghezza
        const val PAD_V = 26f       // pedana ≈ 3,7 tile
        const val ORB_V = 19f
        const val MAX_FALL = 28f
        // navicella: ogni tocco dà una spinta verso l'alto, senza tocchi scende
        const val SHIP_GRAVITY = 38f
        const val FLAP_V = 9f
        const val SHIP_MAX_V = 11f
        /** Tetto del mondo: la navicella ci sbatte contro, il cubo che "cade in alto" oltre si perde. */
        const val WORLD_TOP = 8f
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

    /** true = navicella, false = cubo */
    var ship = false; private set
    /** true = gioco a specchio (solo grafica: la fisica non cambia) */
    var mirror = false; private set
    /** Gravità impostata dai portali: 1 normale, -1 sottosopra. */
    var baseGravity = 1; private set
    /** Tasto gravità tenuto premuto: inverte la gravità finché resta premuto. */
    var gravityHeld = false; private set
    private var lastGravity = 1
    private var lastPortal = -1

    /** Direzione attuale della gravità: 1 verso il basso, -1 verso l'alto. */
    val gravity: Int get() = if (gravityHeld) -baseGravity else baseGravity

    /** Eventi dell'ultimo passo, per effetti grafici/sonori. */
    var lastEvent = Event.NONE; private set

    enum class Event { NONE, JUMP, PAD, ORB, LAND, FLAP, PORTAL }

    val progress: Float get() = (x / level.width).coerceIn(0f, 1f)

    fun reset() {
        x = 0f; y = 0f; vy = 0f; grounded = true; phase = 0
        alive = true; won = false; held = false; fresh = false; lastEvent = Event.NONE
        ship = false; mirror = false; baseGravity = 1; gravityHeld = false; lastGravity = 1; lastPortal = -1
    }

    fun copyFrom(o: Sim) {
        x = o.x; y = o.y; vy = o.vy; grounded = o.grounded; phase = o.phase
        alive = o.alive; won = o.won; held = o.held; fresh = o.fresh; lastEvent = o.lastEvent
        ship = o.ship; mirror = o.mirror; baseGravity = o.baseGravity; gravityHeld = o.gravityHeld
        lastGravity = o.lastGravity; lastPortal = o.lastPortal
    }

    /** Solo per le foto di prova: mette il giocatore in un punto del livello. */
    internal fun debugPlace(px: Float, py: Float, ph: Int, asShip: Boolean = false, mirrored: Boolean = false, grav: Int = 1) {
        x = px; y = py; phase = ph; ship = asShip; mirror = mirrored; baseGravity = grav
        lastGravity = grav; grounded = !asShip; lastPortal = px.toInt()
    }

    fun setHold(h: Boolean) {
        if (h && !held) fresh = true
        if (!h) fresh = false
        held = h
    }

    fun setGravityHeld(h: Boolean) { gravityHeld = h }

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

        val g = gravity
        if (g != lastGravity) { grounded = false; lastGravity = g }

        if (ship) {
            if (fresh) { vy = FLAP_V * g; fresh = false; lastEvent = Event.FLAP }
            vy = (vy - SHIP_GRAVITY * g * dt).coerceIn(-SHIP_MAX_V, SHIP_MAX_V)
        } else {
            if (grounded && held) {
                vy = JUMP_V * g; grounded = false; fresh = false; lastEvent = Event.JUMP
            }
            vy = (vy - GRAVITY * g * dt).coerceIn(-MAX_FALL, MAX_FALL)
        }
        val prevY = y
        val wasGrounded = grounded
        y += vy * dt
        grounded = false

        // pavimento
        if (y <= 0f) {
            y = 0f; if (vy < 0f) vy = 0f; if (g > 0) grounded = true
        }
        // tetto del mondo
        if (ship) {
            if (y + SIZE >= WORLD_TOP) { y = WORLD_TOP - SIZE; if (vy > 0f) vy = 0f; if (g < 0) grounded = true }
        } else if (y > WORLD_TOP + 0.5f) {
            alive = false; return
        }

        // Appoggio sui blocchi: sopra con gravità normale, sotto con gravità rovesciata.
        // La navicella può strisciare su entrambi i lati; il cubo che sbatte "di testa" muore.
        run {
            val x0 = floor(x).toInt(); val x1 = floor(x + SIZE - EPS).toInt()
            val y0 = floor(y).toInt(); val y1 = floor(y + SIZE - EPS).toInt()
            for (tx in x0..x1) for (ty in y0..y1) {
                if (!isSolid(level.at(tx, ty))) continue
                val top = ty + 1f
                val bottom = ty.toFloat()
                if (vy <= 0f && prevY >= top - 0.06f && y < top) {
                    if (g > 0 || ship) { y = top; vy = 0f; if (g > 0) grounded = true }
                } else if (vy >= 0f && prevY + SIZE <= bottom + 0.06f && y + SIZE > bottom) {
                    if (g < 0 || ship) { y = bottom - SIZE; vy = 0f; if (g < 0) grounded = true }
                }
            }
        }
        if (grounded && !wasGrounded && lastEvent == Event.NONE) lastEvent = Event.LAND

        x += SPEED * dt
        if (overlapsSolid()) { alive = false; return }

        // Portali: scattano quando il bordo destro entra nella loro colonna
        for (col in floor(x).toInt()..floor(x + SIZE).toInt()) {
            if (col <= lastPortal) continue
            val p = level.portalAt(col)
            if (p == '.') continue
            lastPortal = col
            lastEvent = Event.PORTAL
            when (p) {
                'N' -> { ship = true; vy *= 0.4f }
                'Q' -> ship = false
                '<' -> mirror = true
                '>' -> mirror = false
                'U' -> baseGravity = -1
                'D' -> baseGravity = 1
            }
        }

        // Pericoli e oggetti
        val gg = gravity
        val x0 = floor(x).toInt(); val x1 = floor(x + SIZE - EPS).toInt()
        val y0 = floor(y).toInt(); val y1 = floor(y + SIZE - EPS).toInt()
        for (tx in x0..x1) for (ty in y0..y1) {
            when (level.at(tx, ty)) {
                '^' -> if (hits(tx + 0.3f, ty + 0f, tx + 0.7f, ty + 0.55f)) { alive = false; return }
                'c' -> if (phase == 0 && hits(tx + 0.3f, ty + 0f, tx + 0.7f, ty + 0.55f)) { alive = false; return }
                'm' -> if (phase == 1 && hits(tx + 0.3f, ty + 0f, tx + 0.7f, ty + 0.55f)) { alive = false; return }
                'v' -> if (hits(tx + 0.3f, ty + 0.45f, tx + 0.7f, ty + 1f)) { alive = false; return }
                'p' -> if (hits(tx + 0f, ty + 0f, tx + 1f, ty + 0.3f) && vy * gg < PAD_V) {
                    vy = PAD_V * gg; grounded = false; lastEvent = Event.PAD
                }
                'o' -> if (!ship && fresh && hits(tx + 0.05f, ty + 0.05f, tx + 0.95f, ty + 0.95f)) {
                    vy = ORB_V * gg; grounded = false; fresh = false; lastEvent = Event.ORB
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
