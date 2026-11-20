package it.giallotouch.bridge

import it.giallotouch.bridge.MemoryMapIt as M

/**
 * Dati letti dalla ROM dell'utente all'avvio (mai inclusi nell'app): nomi, tipi e PP massimi delle mosse,
 * nomi dei tipi. Offset verificati solo sulla versione italiana supportata; con altre ROM i dati restano vuoti.
 */
class RomData private constructor(
    /** ROM tenuta in memoria per le immagini dei Pokémon (decodificate quando servono). */
    private val rom: ByteArray? = null,
    private val moveNames: List<String>,
    private val moveInfo: List<MoveInfo>,
    private val typeNames: List<String>,
    private val speciesNames: List<String> = emptyList(),
    private val itemNames: List<String> = emptyList(),
    /** Numero del Pokédex per indice interno (posizione 0 = indice 1). */
    private val dexOrder: List<Int> = emptyList(),
    /** Velocità di crescita per numero del Pokédex (posizione 0 = n. 1). */
    private val growthRates: List<Int> = emptyList(),
    /** Icone della squadra (16×16, indici di colore 0–3) per tipo d'icona del gioco. */
    private val icons: List<IntArray> = emptyList(),
    /** Tipo d'icona per numero del Pokédex (posizione 0 = n. 1). */
    private val iconTypes: List<Int> = emptyList(),
    /** Secondo fotogramma delle icone (animazione della squadra). */
    private val icons2: List<IntArray> = emptyList(),
) {
    data class MoveInfo(val type: Int, val maxPp: Int, val power: Int = 0)

    fun moveName(id: Int): String = moveNames.getOrNull(id - 1) ?: "MOSSA $id"
    fun moveInfo(id: Int): MoveInfo? = moveInfo.getOrNull(id - 1)
    /** PP massimi con i PP Su usati: ognuno aggiunge un quinto dei PP di base (come nel gioco). */
    fun maxPp(id: Int, ppUps: Int): Int? = moveInfo(id)?.let { it.maxPp + it.maxPp / 5 * ppUps }
    fun typeName(type: Int): String = typeNames.getOrNull(type) ?: ""
    /** Nome dello strumento; le macchine nascoste e tecniche hanno nome e numero (MN01, MT01…). */
    fun itemName(id: Int): String = when {
        id >= M.TM01 -> "MT%02d".format(id - M.TM01 + 1)
        id >= M.HM01 -> "MN%02d".format(id - M.HM01 + 1)
        else -> itemNames.getOrNull(id - 1) ?: "STRUMENTO $id"
    }

    /** Nome della specie dal numero del Pokédex (1–151), vuoto se sconosciuto. */
    fun speciesNameForDex(dex: Int): String {
        val i = dexOrder.indexOf(dex)
        return if (dex in 1..M.NUM_POKEDEX && i >= 0) speciesName(i + 1) else ""
    }

    /** Velocità di crescita della specie (indice interno), null se sconosciuta. */
    fun growthRate(index: Int): Int? = growthRates.getOrNull(dexNumber(index) - 1)

    /** Numero del Pokédex di una specie (indice interno), 0 se sconosciuto. */
    fun dexNumber(index: Int): Int = dexOrder.getOrNull(index - 1) ?: 0

    /**
     * Icona piccola della specie (indice interno) come nel menu POKéMON del gioco: 16×16 indici di colore 0 (chiaro)–3
     * (scuro), null se sconosciuta.
     */
    fun partyIcon(index: Int): IntArray? = iconTypes.getOrNull(dexNumber(index) - 1)?.let { icons.getOrNull(it) }

    /** Icona nel fotogramma [frame] (0 o 1) dell'animazione della squadra, come nel gioco (D-071). */
    fun partyIconFrame(index: Int, frame: Int): IntArray? =
        if (frame == 0) partyIcon(index) else iconTypes.getOrNull(dexNumber(index) - 1)?.let { icons2.getOrNull(it) } ?: partyIcon(index)

    private val pics = HashMap<Int, MonPic?>()

    /** Immagine frontale della specie (indice interno), come nella scheda del gioco; null se non disponibile. */
    fun frontPic(index: Int): MonPic? = synchronized(pics) {
        pics.getOrPut(index) {
            val r = rom ?: return@getOrPut null
            val dex = dexNumber(index)
            if (dex !in 1..151) return@getOrPut null
            val st = M.ROM_BASE_STATS + (dex - 1) * M.BASE_STATS_SIZE
            if (st + 13 > r.size) return@getOrPut null
            val ptr = u8(r, st + 11) or (u8(r, st + 12) shl 8)
            val off = MonPic.bank(index) * 0x4000 + ptr - 0x4000
            if (ptr !in 0x4000..0x7FFF || off >= r.size || u8(r, off) != u8(r, st + 10)) return@getOrPut null
            MonPic.decode(r, off)
        }
    }

    /**
     * Voce del Pokédex della specie (indice interno), dalla ROM (D-054): categoria e righe della descrizione come
     * caselle del carattere del gioco, altezza in decimetri e peso in ettogrammi. null se non disponibile.
     */
    class DexEntry(val category: IntArray, val heightDm: Int, val weightHg: Int, val lines: List<IntArray>)

    fun dexEntry(index: Int): DexEntry? = runCatching {
        val r = rom ?: return null
        val p = M.ROM_DEX_ENTRY_POINTERS + 2 * (index - 1)
        val ptr = u8(r, p) or (u8(r, p + 1) shl 8)
        if (ptr !in 0x4000..0x7FFF) return null
        var o = 0x40000 + ptr - 0x4000
        val cat = ArrayList<Int>()
        while (u8(r, o) != 0x50 && cat.size < 12) cat += u8(r, o++)
        o++
        val h = u8(r, o); val w = u8(r, o + 1) or (u8(r, o + 2) shl 8)
        if (u8(r, o + 3) != 0x17) return DexEntry(cat.toIntArray(), h, w, emptyList())
        val addr = u8(r, o + 4) or (u8(r, o + 5) shl 8); val bank = u8(r, o + 6)
        var t = bank * 0x4000 + addr - 0x4000
        if (u8(r, t) == 0x00) t++
        val lines = ArrayList<IntArray>(); var cur = ArrayList<Int>()
        while (lines.size < 8) {
            val b = u8(r, t++)
            when (b) {
                0x50, 0x5F -> { if (cur.isNotEmpty()) lines += cur.toIntArray(); break }
                0x4E, 0x49, 0x51, 0x55 -> { lines += cur.toIntArray(); cur = ArrayList() }
                else -> if (cur.size < 18) cur += b
            }
        }
        DexEntry(cat.toIntArray(), h, w, lines)
    }.getOrNull()

    /** Colori dell'immagine della specie (indice interno) come nel gioco a colori (D-054): 4 colori ARGB, dal chiaro allo scuro. */
    fun monPalette(index: Int): IntArray? {
        val r = rom ?: return null
        val dex = dexNumber(index)
        if (dex !in 1..151 || M.ROM_MON_PALETTE_IDS + dex >= r.size) return null
        val pal = u8(r, M.ROM_MON_PALETTE_IDS + dex)
        val o = M.ROM_CGB_PALETTES + 8 * pal
        if (o + 8 > r.size) return null
        return IntArray(4) { i ->
            val c = u8(r, o + 2 * i) or (u8(r, o + 2 * i + 1) shl 8)
            fun ch(v: Int) = (v * 255 / 31)
            (0xFF shl 24) or (ch(c and 31) shl 16) or (ch((c shr 5) and 31) shl 8) or ch((c shr 10) and 31)
        }
    }

    /** Prezzo dello strumento al negozio (BCD dalla ROM), null se sconosciuto. MT/MN: null. */
    fun itemPrice(id: Int): Int? {
        val r = rom ?: return null
        if (id !in 1..0x61) return null
        val o = M.ROM_ITEM_PRICES + 3 * (id - 1)
        if (o + 3 > r.size) return null
        fun bcd(b: Int) = (b shr 4) * 10 + (b and 15)
        return bcd(u8(r, o)) * 10000 + bcd(u8(r, o + 1)) * 100 + bcd(u8(r, o + 2))
    }

    /** Indice interno della specie con quel numero del Pokédex (0 se sconosciuto). */
    fun indexForDex(dex: Int): Int = dexOrder.indexOf(dex) + 1

    /** Nome della specie dall'indice interno del gioco (quello della squadra), vuoto se sconosciuto. */
    fun speciesName(index: Int): String = speciesNames.getOrNull(index - 1) ?: ""

    companion object {
        val EMPTY = RomData(moveNames = emptyList(), moveInfo = emptyList(), typeNames = emptyList())

        fun load(rom: ByteArray, supported: Boolean): RomData {
            if (!supported) return EMPTY
            return RomData(
                rom = rom,
                moveNames = readNameList(rom, M.ROM_MOVE_NAMES, M.NUM_MOVES) ?: emptyList(),
                moveInfo = readMoveData(rom, M.ROM_MOVE_DATA, M.NUM_MOVES) ?: emptyList(),
                typeNames = readPointedNames(rom, M.ROM_TYPE_NAME_POINTERS, M.NUM_TYPE_IDS) ?: emptyList(),
                dexOrder = if (M.ROM_POKEDEX_ORDER + M.NUM_SPECIES_INDEXES <= rom.size)
                    List(M.NUM_SPECIES_INDEXES) { u8(rom, M.ROM_POKEDEX_ORDER + it) } else emptyList(),
                growthRates = if (M.ROM_BASE_STATS + 151 * M.BASE_STATS_SIZE <= rom.size)
                    List(151) { u8(rom, M.ROM_BASE_STATS + it * M.BASE_STATS_SIZE + 19) }.takeIf { r -> r.all { it in 0..5 } } ?: emptyList()
                    else emptyList(),
                itemNames = readNameList(rom, M.ROM_ITEM_NAMES, M.NUM_ITEM_NAMES) ?: emptyList(),
                icons = readIcons(rom) ?: emptyList(),
                icons2 = readIcons(rom, first = 15, base = 0x8400) ?: emptyList(),
                iconTypes = if (M.ROM_PARTY_ICON_TYPES + 76 <= rom.size)
                    List(151) { i -> u8(rom, M.ROM_PARTY_ICON_TYPES + i / 2).let { if (i % 2 == 0) it shr 4 else it and 15 } }.takeIf { t -> t.all { it <= 10 } } ?: emptyList()
                    else emptyList(),
                speciesNames = readFixedNames(rom, M.ROM_SPECIES_NAMES, M.NUM_SPECIES_INDEXES, M.SPECIES_NAME_LENGTH) ?: emptyList(),
            )
        }

        internal fun u8(rom: ByteArray, i: Int) = rom[i].toInt() and 0xFF

        /**
         * Icone della squadra: la tabella del gioco (sorgente, caselle, banco, destinazione) copia i disegni nella
         * memoria video, 4 caselle per icona (in alto a sinistra, in alto a destra, in basso a sinistra, in basso a
         * destra). Le icone da una casella per riga (insetto, pianta, serpente, quadrupede) hanno la metà destra
         * specchiata, come fa il gioco.
         */
        fun readIcons(rom: ByteArray, first: Int = 0, base: Int = 0x8000): List<IntArray>? {
            val vram = ByteArray(0x400)
            // 14 voci per il primo fotogramma (0x8000…), le stesse 15 voci più avanti per il secondo (0x8400…)
            for (e in first until first + (if (first == 0) 14 else 15)) {
                val p = M.ROM_PARTY_ICON_GFX + 6 * e
                if (p + 6 > rom.size) return null
                val src = u8(rom, p) or (u8(rom, p + 1) shl 8)
                val n = u8(rom, p + 2); val bank = u8(rom, p + 3)
                val dest = (u8(rom, p + 4) or (u8(rom, p + 5) shl 8)) - base
                val off = bank * 0x4000 + src - 0x4000
                if (dest !in 0 until 0x400 || dest + 16 * n > 0x400 || off < 0 || off + 16 * n > rom.size) return null
                System.arraycopy(rom, off, vram, dest, 16 * n)
            }
            fun px(tile: Int, x: Int, y: Int): Int {
                val lo = vram[tile * 16 + 2 * y].toInt(); val hi = vram[tile * 16 + 2 * y + 1].toInt()
                return (((hi shr (7 - x)) and 1) shl 1) or ((lo shr (7 - x)) and 1)
            }
            return List(11) { k ->
                val mirrored = k in 6..9
                IntArray(256) { i ->
                    val x = i % 16; val y = i / 16
                    val row = y / 8
                    if (mirrored) px(k * 4 + 2 * row, if (x < 8) x else 15 - x, y % 8)
                    else px(k * 4 + row * 2 + x / 8, x % 8, y % 8)
                }
            }
        }

        /** Legge [count] nomi a lunghezza fissa (riempiti con 0x50); null se la tabella non è nella ROM. */
        fun readFixedNames(rom: ByteArray, start: Int, count: Int, length: Int): List<String>? {
            if (start + count * length > rom.size) return null
            return List(count) { k -> TextCodec.decode(rom, start + k * length, length) }
        }

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
                MoveInfo(type = u8(rom, e + 3), maxPp = u8(rom, e + 5), power = u8(rom, e + 2))
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
