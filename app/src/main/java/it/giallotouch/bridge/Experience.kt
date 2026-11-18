package it.giallotouch.bridge

/**
 * Esperienza come la calcola il gioco: punti necessari per un livello n secondo la velocità di crescita,
 * (a/b)·n³ + c·n² + d·n − e (tabella del gioco: media veloce, leggermente veloce, leggermente lenta,
 * media lenta, veloce, lenta). Serve alla barra dell'esperienza della squadra.
 */
object Experience {
    private val RATES = arrayOf(
        intArrayOf(1, 1, 0, 0, 0),
        intArrayOf(3, 4, 10, 0, 30),
        intArrayOf(3, 4, 20, 0, 70),
        intArrayOf(6, 5, -15, 100, 140),
        intArrayOf(4, 5, 0, 0, 0),
        intArrayOf(5, 4, 0, 0, 0),
    )

    fun forLevel(rate: Int, n: Int): Int {
        val r = RATES.getOrNull(rate) ?: return 0
        val cube = r[0].toLong() * n * n * n / r[1]
        return (cube + r[2].toLong() * n * n + r[3].toLong() * n - r[4]).coerceAtLeast(0).toInt()
    }

    /** Avanzamento verso il livello successivo, da 0 a 1 (1 al livello 100). */
    fun progress(rate: Int?, level: Int, exp: Int): Float {
        if (rate == null) return 0f
        if (level >= 100) return 1f
        val from = forLevel(rate, level)
        val to = forLevel(rate, level + 1)
        if (to <= from) return 0f
        return ((exp - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    }
}
