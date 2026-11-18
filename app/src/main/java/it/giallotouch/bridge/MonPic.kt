package it.giallotouch.bridge

/**
 * Immagine frontale di un Pokémon letta dalla ROM dell'utente (formato compresso della prima generazione), solo in
 * memoria e mai inclusa nell'app. [pixels]: indici di colore 0 (chiaro)–3 (scuro), riga per riga, [width]×[height].
 *
 * Formato: un byte con le dimensioni in caselle, poi due piani di bit compressi (sequenze di zeri e gruppi di 2 bit,
 * in colonne larghe 2 pixel), con codifica differenziale per riga e, nei modi 1 e 2, un piano unito all'altro (XOR).
 */
class MonPic(val width: Int, val height: Int, val pixels: IntArray) {
    companion object {
        private val DELTA = arrayOf(
            intArrayOf(0x0, 0x1, 0x3, 0x2, 0x7, 0x6, 0x4, 0x5, 0xF, 0xE, 0xC, 0xD, 0x8, 0x9, 0xB, 0xA),
            intArrayOf(0xF, 0xE, 0xC, 0xD, 0x8, 0x9, 0xB, 0xA, 0x0, 0x1, 0x3, 0x2, 0x7, 0x6, 0x4, 0x5),
        )

        /** Decodifica l'immagine che inizia a [offset]; null se i dati non sono validi. */
        fun decode(rom: ByteArray, offset: Int): MonPic? = runCatching { Reader(rom, offset).run() }.getOrNull()

        /** Banco della ROM con l'immagine, dall'indice interno della specie (come fa il gioco). */
        fun bank(index: Int): Int = when {
            index < 0x1F -> 0x9
            index < 0x4A -> 0xA
            index < 0x74 -> 0xB
            index < 0x99 -> 0xC
            else -> 0xD
        }
    }

    private class Reader(val d: ByteArray, offset: Int) {
        var p = offset * 8
        var rows = 0; var cols = 0
        fun bit(): Int { val b = (d[p shr 3].toInt() shr (7 - (p and 7))) and 1; p++; return b }
        fun num(n: Int): Int { var v = 0; repeat(n) { v = (v shl 1) or bit() }; return v }

        fun run(): MonPic? {
            rows = num(4) * 8; cols = num(4)
            if (rows == 0 || cols == 0 || rows > 64 || cols > 8) return null
            val r1 = bit(); val r2 = r1 xor 1
            val rams = arrayOfNulls<IntArray>(2)
            rams[r1] = fill()
            var mode = bit(); if (mode == 1) mode += bit()
            rams[r2] = fill()
            val a = rams[0]!!; val b = rams[1]!!
            when (mode) {
                0 -> { delta(a); delta(b) }
                1 -> { delta(rams[r1]!!); xor(rams[r1]!!, rams[r2]!!) }
                else -> { delta(rams[r2]!!); delta(rams[r1]!!); xor(rams[r1]!!, rams[r2]!!) }
            }
            val w = cols * 8
            val px = IntArray(w * rows)
            for (c in 0 until cols) for (y in 0 until rows) {
                val lo = a[c * rows + y]; val hi = b[c * rows + y]
                for (x in 0 until 8) px[y * w + c * 8 + x] = (((hi shr (7 - x)) and 1) shl 1) or ((lo shr (7 - x)) and 1)
            }
            return MonPic(w, rows, px)
        }

        fun fill(): IntArray {
            val size = rows * cols * 4
            val pairs = IntArray(size); var n = 0
            var data = bit() == 1
            while (n < size) {
                if (!data) {
                    var i = 0
                    while (bit() == 1) { i++; require(i < 16) }
                    var run = (2 shl i) - 1 + num(i + 1)
                    while (run-- > 0 && n < size) pairs[n++] = 0
                } else {
                    while (n < size) { val g = num(2); if (g == 0) break; pairs[n++] = g }
                }
                data = !data
            }
            val out = IntArray(rows * cols)
            var k = 0
            for (c in 0 until cols) for (y in 0 until rows) {
                var i = 4 * c * rows + y; var v = 0
                repeat(4) { v = (v shl 2) or pairs[i]; i += rows }
                out[k++] = v
            }
            return out
        }

        fun delta(ram: IntArray) {
            for (y in 0 until rows) {
                var last = 0
                for (c in 0 until cols) {
                    val i = c * rows + y
                    val hiN = DELTA[last][ram[i] shr 4]; last = hiN and 1
                    val loN = DELTA[last][ram[i] and 15]; last = loN and 1
                    ram[i] = (hiN shl 4) or loN
                }
            }
        }

        fun xor(a: IntArray, b: IntArray) { for (i in b.indices) b[i] = b[i] xor a[i] }
    }
}
