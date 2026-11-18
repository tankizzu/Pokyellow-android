package it.giallotouch.bridge

/**
 * Dati letti dalla ROM dell'utente (mai inclusi nell'app): per ora i nomi delle mosse.
 * Offset verificati solo sulla versione italiana supportata; con altre ROM i nomi restano vuoti.
 */
class RomData private constructor(private val moveNames: List<String>) {

    fun moveName(id: Int): String = moveNames.getOrNull(id - 1) ?: "MOSSA $id"

    companion object {
        val EMPTY = RomData(emptyList())

        fun load(rom: ByteArray, supported: Boolean): RomData {
            if (!supported) return EMPTY
            val names = readNameList(rom, MemoryMapIt.ROM_MOVE_NAMES, MemoryMapIt.NUM_MOVES) ?: return EMPTY
            return RomData(names)
        }

        /** Legge [count] nomi consecutivi chiusi da 0x50; null se i dati non sono plausibili. */
        fun readNameList(rom: ByteArray, start: Int, count: Int): List<String>? {
            val out = ArrayList<String>(count)
            var p = start
            repeat(count) {
                val begin = p
                while (p < rom.size && (rom[p].toInt() and 0xFF) != TextCodec.TERMINATOR) {
                    if (p - begin > 13) return null
                    p++
                }
                if (p >= rom.size || p == begin) return null
                out += TextCodec.decode(rom, begin, p - begin)
                p++
            }
            return out
        }
    }
}
