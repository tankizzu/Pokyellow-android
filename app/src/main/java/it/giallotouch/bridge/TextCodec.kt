package it.giallotouch.bridge

/**
 * Decodifica dei testi del gioco (nomi) nel set di caratteri del Game Boy.
 * Copre lettere, cifre e i simboli usati nei nomi; i caratteri non riconosciuti diventano "?".
 * Lettere accentate della versione italiana trovate finora: 0xBC = é (POKé), 0xBE = À, 0xC6 = É.
 */
object TextCodec {
    const val TERMINATOR = 0x50

    fun decode(bytes: ByteArray, offset: Int = 0, maxLength: Int = bytes.size - offset): String {
        val sb = StringBuilder()
        for (i in 0 until maxLength) {
            val index = offset + i
            if (index >= bytes.size) break
            val b = bytes[index].toInt() and 0xFF
            if (b == TERMINATOR) break
            sb.append(charFor(b))
        }
        return sb.toString()
    }

    fun charFor(b: Int): Char = when (b) {
        in 0x80..0x99 -> 'A' + (b - 0x80)
        in 0xA0..0xB9 -> 'a' + (b - 0xA0)
        in 0xF6..0xFF -> '0' + (b - 0xF6)
        0x7F -> ' '
        0x9A -> '('
        0x9B -> ')'
        0x9C -> ':'
        0x9D -> ';'
        0xBC -> 'é'
        0xBE -> 'À'
        0xC6 -> 'É'
        0xE0 -> '\''
        0xE3 -> '-'
        0xE6 -> '?'
        0xE7 -> '!'
        0xE8 -> '.'
        0xEF -> '♂'
        0xF4 -> ','
        0xF5 -> '♀'
        else -> '?'
    }
}
