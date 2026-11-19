package it.battito.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Un bot prova tutte le combinazioni di input (salto/tocco sì/no, cambio colore sì/no, gravità sì/no)
 * a 60 decisioni al secondo e cerca una corsa che arrivi al traguardo.
 */
class LevelSolvableTest {

    private var furthest = 0f

    private fun solve(level: Level, framesPerDecision: Int = 1): IntArray? {
        furthest = 0f
        val frames = ((level.width / Sim.SPEED) * 60).toInt() / framesPerDecision + 120
        val path = IntArray(frames)
        val visited = HashSet<Long>()
        var found = false

        fun key(s: Sim, f: Int): Long {
            val yq = (s.y * 50).roundToInt().toLong() and 0x3FF
            val vq = ((s.vy * 4).roundToInt() + 256).toLong() and 0x3FF
            fun b(v: Boolean) = if (v) 1L else 0L
            return (f.toLong() shl 26) or (yq shl 16) or (vq shl 6) or
                (s.phase.toLong() shl 5) or (b(s.held) shl 4) or (b(s.grounded) shl 3) or
                (b(s.ship) shl 2) or (b(s.gravityHeld) shl 1) or b(s.baseGravity < 0)
        }

        fun dfs(s: Sim, f: Int): Boolean {
            if (s.won) return true
            if (s.x > furthest) furthest = s.x
            if (!s.alive || f >= frames) return false
            if (!visited.add(key(s, f))) return false
            // decisioni: bit 0 = salto/tocco tenuto, bit 1 = cambio colore, bit 2 = tasto gravità tenuto
            for (c in 0..7) {
                val n = Sim(level).also { it.copyFrom(s) }
                n.setHold(c and 1 != 0)
                n.setGravityHeld(c and 4 != 0)
                if (c and 2 != 0) n.togglePhase()
                repeat(4 * framesPerDecision) { n.step() }
                path[f] = c
                if (dfs(n, f + 1)) return true
            }
            return false
        }

        val t = Thread(null, { found = dfs(Sim(level), 0) }, "bot", 512L * 1024 * 1024)
        t.start(); t.join()
        return if (found) path else null
    }

    @Test
    fun ogniLivelloECompletabile() {
        for (level in Levels.all) {
            val path = solve(level)
            assertNotNull("Il livello '${level.name}' non è completabile: il bot arriva al massimo alla colonna ${furthest.toInt()} di ${level.width}", path)
            // il bot prova prima le mosse senza gravità: se la usa, è perché serve davvero
            org.junit.Assert.assertTrue("Il tasto gravità non serve mai", path!!.any { it and 4 != 0 })
        }
    }

    /** Decisioni solo ogni 4 fotogrammi (≈67 ms): niente passaggi "al fotogramma esatto". */
    @Test
    fun ogniLivelloECompletabileConRiflessiUmani() {
        for (level in Levels.all) {
            val path = solve(level, framesPerDecision = 4)
            assertNotNull("Il livello '${level.name}' richiede precisione al fotogramma: il bot (15 decisioni/s) arriva alla colonna ${furthest.toInt()} di ${level.width}", path)
        }
    }

    @Test
    fun laNavicellaSaleConITocchiEScendeSenza() {
        val lvl = Level("t", listOf("..N" + ".".repeat(200)))
        val s = Sim(lvl)
        repeat(240) { s.step() }                       // entra nel portale
        assertTrue(s.ship)
        repeat(30) { s.setHold(true); repeat(20) { s.step() }; s.setHold(false); repeat(20) { s.step() } }
        val alto = s.y
        assertTrue("con i tocchi deve salire (y=$alto)", alto > 4f)
        repeat(240) { s.step() }
        assertTrue("senza tocchi deve scendere", s.y < 0.5f)
    }

    @Test
    fun conLaGravitaInvertitaSenzaSoffittoSiPrecipita() {
        val s = Sim(Level("t", listOf(".".repeat(200))))
        s.setGravityHeld(true)
        repeat(240) { s.step() }
        assertFalse(s.alive)
    }

    @Test
    fun senzaToccareSiMuore() {
        val s = Sim(Levels.primo)
        repeat(60 * 240) { s.step() }
        assertFalse(s.won)
        assertFalse(s.alive)
    }

    @Test
    fun cambiareColoreDentroUnBloccoUccide() {
        val lvl = Level("t", listOf("M"))
        val s = Sim(lvl)
        s.togglePhase()
        assertFalse(s.alive)
    }
}
