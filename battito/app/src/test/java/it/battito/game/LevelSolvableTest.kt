package it.battito.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Un bot prova tutte le combinazioni di input (salto tenuto sì/no, cambio colore sì/no)
 * a 60 decisioni al secondo e cerca una corsa che arrivi al traguardo.
 */
class LevelSolvableTest {

    private var furthest = 0f

    private fun solve(level: Level): IntArray? {
        furthest = 0f
        val frames = ((level.width / Sim.SPEED) * 60).toInt() + 120
        val path = IntArray(frames)
        val visited = HashSet<Long>()
        var found = false

        fun key(s: Sim, f: Int): Long {
            val yq = (s.y * 50).roundToInt().toLong() and 0x3FF
            val vq = ((s.vy * 4).roundToInt() + 256).toLong() and 0x3FF
            return (f.toLong() shl 24) or (yq shl 14) or (vq shl 4) or
                (s.phase.toLong() shl 3) or ((if (s.held) 1L else 0L) shl 2) or (if (s.grounded) 1L else 0L)
        }

        fun dfs(s: Sim, f: Int): Boolean {
            if (s.won) return true
            if (s.x > furthest) furthest = s.x
            if (!s.alive || f >= frames) return false
            if (!visited.add(key(s, f))) return false
            for (c in 0..3) {
                val n = Sim(level).also { it.copyFrom(s) }
                n.setHold(c and 1 != 0)
                if (c and 2 != 0) n.togglePhase()
                repeat(4) { n.step() }
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
        }
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
