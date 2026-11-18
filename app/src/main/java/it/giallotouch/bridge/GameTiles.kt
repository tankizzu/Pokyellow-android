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

    /** Tile in una posizione in pixel (anche fuori dalla griglia, per centrare una scritta). */
    fun tileAt(t: Int, px: Int, py: Int) {
        val sx = (t and 15) * 8
        val sy = ((t shr 4) and 15) * 8
        for (y in 0 until 8) for (x in 0 until 8) {
            val dx = px + x; val dy = py + y
            if (dx in 0 until width && dy in 0 until height) pixels[dy * width + dx] = sheet[(sy + y) * 128 + sx + x]
        }
    }

    fun textAt(s: String, px: Int, py: Int) = GameText.encode(s).forEachIndexed { i, t -> tileAt(t, px + i * 8, py) }

    /** Rettangolo pieno in pixel (barre di vita ed esperienza). */
    fun fill(x: Int, y: Int, w: Int, h: Int, color: Int) {
        for (yy in y until y + h) for (xx in x until x + w) if (xx in 0 until width && yy in 0 until height) pixels[yy * width + xx] = color
    }

    /** Colore "inchiostro" del gioco (colore 3 del carattere) e "carta" (colore 0). */
    val ink: Int get() = inkColor()
    private fun inkColor(): Int {
        // il primo pixel scuro della lettera "A" (0x80) è l'inchiostro
        val sx = (0x80 and 15) * 8; val sy = (0x80 shr 4) * 8
        val paper = sheet[blankOffset()]
        for (y in 0 until 8) for (x in 0 until 8) { val c = sheet[(sy + y) * 128 + sx + x]; if (c != paper) return c }
        return 0xFF000000.toInt()
    }
    val paper: Int get() = sheet[blankOffset()]

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
        trimOutside(x0 * 8, y0 * 8, (x1 + 1) * 8, (y1 + 1) * 8)
    }

    /**
     * Toglie il bianco esterno attorno alla cornice del riquadro (D-046): da ogni bordo verso l'interno i pixel "carta"
     * diventano trasparenti fino alla prima linea della cornice, così sulla mappa resta solo la cornice.
     */
    private fun trimOutside(px0: Int, py0: Int, px1: Int, py1: Int) {
        val p = paper
        val xa = px0.coerceAtLeast(0); val xb = px1.coerceAtMost(width); val ya = py0.coerceAtLeast(0); val yb = py1.coerceAtMost(height)
        for (y in ya until yb) {
            var x = xa; while (x < xb && pixels[y * width + x].let { it == p || it == 0 }) { pixels[y * width + x] = 0; x++ }
            x = xb - 1; while (x >= xa && pixels[y * width + x].let { it == p || it == 0 }) { pixels[y * width + x] = 0; x-- }
        }
        for (x in xa until xb) {
            var y = ya; while (y < yb && pixels[y * width + x].let { it == p || it == 0 }) { pixels[y * width + x] = 0; y++ }
            y = yb - 1; while (y >= ya && pixels[y * width + x].let { it == p || it == 0 }) { pixels[y * width + x] = 0; y-- }
        }
    }

    /** Icona della squadra 16×16 (indici di colore 0–3, [RomData.partyIcon]) in pixel; il colore 0 resta trasparente. */
    fun icon(data: IntArray, px: Int, py: Int, scale: Int = 1) {
        val i = ink; val pa = paper
        fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(sh: Int) = (((a shr sh) and 255) * (1 - t) + ((b shr sh) and 255) * t).toInt() shl sh
            return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
        }
        val colors = intArrayOf(pa, mix(pa, i, 0.33f), mix(pa, i, 0.66f), i)
        for (y in 0 until 16 * scale) for (x in 0 until 16 * scale) {
            val c = data[(y / scale) * 16 + x / scale]; val dx = px + x; val dy = py + y
            if (c != 0 && dx in 0 until width && dy in 0 until height) pixels[dy * width + dx] = colors[c]
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
