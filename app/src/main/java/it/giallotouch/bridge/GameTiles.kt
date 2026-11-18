package it.giallotouch.bridge

/**
 * Disegno "come il gioco" (D-039): scritte e riquadri composti con i tile veri del gioco (foglio 128×128 ARGB
 * dal motore, [it.giallotouch.emu.NativeCore.nativeGameTiles]), sulla stessa griglia di 8 pixel.
 * Le aggiunte dell'app così non si distinguono dal gioco. Le coordinate sono in tile (caselle da 8 pixel).
 */
class TileCanvas(val widthTiles: Int, val heightTiles: Int, private val sheet: IntArray) {
    val width = widthTiles * 8
    val height = heightTiles * 8
    val pixels = IntArray(width * height) { sheet[blankOffset()] }

    private fun blankOffset(): Int = (BLANK / 16) * 8 * 128 + (BLANK % 16) * 8

    fun tile(t: Int, tx: Int, ty: Int) {
        if (tx !in 0 until widthTiles || ty !in 0 until heightTiles) return
        val sx = (t and 15) * 8
        val sy = ((t shr 4) and 15) * 8
        for (y in 0 until 8) {
            System.arraycopy(sheet, (sy + y) * 128 + sx, pixels, (ty * 8 + y) * width + tx * 8, 8)
        }
    }

    fun text(s: String, tx: Int, ty: Int) = GameText.encode(s).forEachIndexed { i, t -> tile(t, tx + i, ty) }

    /** Riquadro del gioco da (x0, y0) a (x1, y1), angoli compresi. */
    fun box(x0: Int, y0: Int, x1: Int, y1: Int) {
        for (ty in y0..y1) for (tx in x0..x1) {
            tile(
                when {
                    ty == y0 -> if (tx == x0) TL else if (tx == x1) TR else HORIZONTAL
                    ty == y1 -> if (tx == x0) BL else if (tx == x1) BR else HORIZONTAL
                    else -> if (tx == x0 || tx == x1) VERTICAL else BLANK
                }, tx, ty
            )
        }
    }

    companion object {
        const val TL = 0x79
        const val HORIZONTAL = 0x7A
        const val TR = 0x7B
        const val VERTICAL = 0x7C
        const val BL = 0x7D
        const val BR = 0x7E
        const val BLANK = 0x7F
        const val CURSOR = 0xED
        const val SHEET_SIZE = 128 * 128
    }
}

/** Testo nei codici del carattere del gioco (lettere, cifre e simboli dei menu). */
object GameText {
    private val symbols = mapOf(
        ' ' to 0x7F, ':' to 0x9C, '/' to 0xF3, '.' to 0xE8, '?' to 0xE6, '!' to 0xE7, '▶' to 0xED, '▼' to 0xEE,
        'é' to 0xBC, '-' to 0xE3, '×' to 0xF1, ',' to 0xF4, '(' to 0x9A, ')' to 0x9B, '═' to 0x7A,
    )

    fun encode(s: String): IntArray = IntArray(s.length) { i ->
        val c = s[i]
        when (c) {
            in 'A'..'Z' -> 0x80 + (c - 'A')
            in 'a'..'z' -> 0xA0 + (c - 'a')
            in '0'..'9' -> 0xF6 + (c - '0')
            else -> symbols[c] ?: 0x7F
        }
    }
}
