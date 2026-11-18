package it.giallotouch.bridge

import it.giallotouch.bridge.MemoryMapIt as M

/**
 * Dati letti dalla ROM dell'utente all'avvio (mai inclusi nell'app): nomi, tipi e PP massimi delle mosse,
 * nomi dei tipi. Offset verificati solo sulla versione italiana supportata; con altre ROM i dati restano vuoti.
 */
class RomData private constructor(
    private val moveNames: List<String>,
    private val moveInfo: List<MoveInfo>,
    private val typeNames: List<String>,
) {
    data class MoveInfo(val type: Int, val maxPp: Int)

    fun moveName(id: Int): String = moveNames.getOrNull(id - 1) ?: "MOSSA $id"
    fun moveInfo(id: Int): MoveInfo? = moveInfo.getOrNull(id - 1)
    fun typeName(type: Int): String = typeNames.getOrNull(type) ?: ""

    companion object {
        val EMPTY = RomData(emptyList(), emptyList(), emptyList())

        fun load(rom: ByteArray, supported: Boolean): RomData {
            if (!supported) return EMPTY
            return RomData(
                moveNames = readNameList(rom, M.ROM_MOVE_NAMES, M.NUM_MOVES) ?: emptyList(),
                moveInfo = readMoveData(rom, M.ROM_MOVE_DATA, M.NUM_MOVES) ?: emptyList(),
                typeNames = readPointedNames(rom, M.ROM_TYPE_NAME_POINTERS, M.NUM_TYPE_IDS) ?: emptyList(),
            )
        }

        private fun u8(rom: ByteArray, i: Int) = rom[i].toInt() and 0xFF

        /** Legge [count] nomi consecutivi chiusi da 0x50; null se i dati non sono plausibili. */
        fun readNameList(rom: ByteArray, start: Int, count: Int): List<String>? {
            val out = ArrayList<String>(count)
            var p = start
            repeat(count) {
                val begin = p
                while (p < rom.size && u8(rom, p) != TextCodec.TERMINATOR) {
                    if (p - begin > 13) return null
                    p++
                }
                if (p >= rom.size || p == begin) return null
                out += TextCodec.decode(rom, begin, p - begin)
                p++
            }
            return out
        }

        /** Voci da 6 byte; la prima di ogni voce deve essere il numero della mossa (1, 2, 3…). */
        fun readMoveData(rom: ByteArray, start: Int, count: Int): List<MoveInfo>? {
            if (start + count * M.MOVE_DATA_SIZE > rom.size) return null
            return (0 until count).map { k ->
                val e = start + k * M.MOVE_DATA_SIZE
                if (u8(rom, e) != k + 1) return null
                MoveInfo(type = u8(rom, e + 3), maxPp = u8(rom, e + 5))
            }
        }

        /** Tabella di puntatori a 16 bit (stesso banco della tabella) verso nomi chiusi da 0x50. */
        fun readPointedNames(rom: ByteArray, table: Int, count: Int): List<String>? {
            val bankBase = table / 0x4000 * 0x4000
            if (table + count * 2 > rom.size) return null
            return (0 until count).map { k ->
                val ptr = u8(rom, table + 2 * k) or (u8(rom, table + 2 * k + 1) shl 8)
                if (ptr !in 0x4000..0x7FFF) return null
                val start = bankBase + (ptr - 0x4000)
                readNameList(rom, start, 1)?.first() ?: return null
            }
        }
    }
}
