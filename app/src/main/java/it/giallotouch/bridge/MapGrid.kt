package it.giallotouch.bridge

import it.giallotouch.bridge.MemoryMapIt as M

/**
 * Griglia dei passi della mappa corrente (ogni passo = 2×2 tile), costruita come fa il gioco:
 * un passo è calpestabile se il tile in basso a sinistra è nella lista dei tile calpestabili del set corrente.
 * I personaggi (sprite 1–14) occupano il loro passo. Il compagno che segue (sprite 15) non blocca.
 * Gli oggetti che il gioco ha nascosto (lista D5D2 + bit D5AA) non contano: né ostacolo né da toccare.
 * Dislivelli (grotte, foresta): tra certe coppie di tile il gioco non lascia passare anche se entrambi sono
 * calpestabili (tabella TilePairCollisionsLand), e il percorso ne tiene conto (D-120).
 * Casi non ancora gestiti (sporgenze da saltare, acqua con Surf) risultano non
 * calpestabili: il percorso li evita, e comunque è sempre il gioco a decidere se un passo riesce.
 */
class MapGrid(
    val width: Int,
    val height: Int,
    private val passable: BooleanArray,
    val people: Set<Pair<Int, Int>>,
    private val tiles: IntArray = IntArray(width * height),
    private val talkingOver: Set<Int> = emptySet(),
    val signs: Set<Pair<Int, Int>> = emptySet(),
    private val tileset: Int = -1,
    /** Porte e scale (x, y): il percorso non ci passa sopra, tranne quando sono l'arrivo (D-123). */
    val warps: Set<Pair<Int, Int>> = emptySet(),
) {

    fun inside(x: Int, y: Int) = x in 0 until width && y in 0 until height
    fun isPassable(x: Int, y: Int) = inside(x, y) && passable[y * width + x]
    fun isFree(x: Int, y: Int) = isPassable(x, y) && (x to y) !in people
    private fun isCounter(x: Int, y: Int) = inside(x, y) && tiles[y * width + x] in talkingOver

    /** Persona, oggetto a terra (sprite), cartello o bancone: toccarlo porta lì e fa parlare/esaminare. */
    fun isInteractive(x: Int, y: Int) = (x to y) in people || (x to y) in signs || isCounter(x, y)

    /**
     * Dove fermarsi per interagire con il passo (tx, ty): i passi liberi accanto, oppure dall'altra parte
     * di un bancone. Restituisce posizione → direzione verso cui guardare.
     */
    fun interactionSpots(tx: Int, ty: Int): Map<Pair<Int, Int>, Dir> {
        val out = HashMap<Pair<Int, Int>, Dir>()
        for (d in Dir.entries) {
            val n1 = tx - d.dx to ty - d.dy
            if (isFree(n1.first, n1.second)) out[n1] = d
            val n2 = tx - 2 * d.dx to ty - 2 * d.dy
            if (isCounter(n1.first, n1.second) && isFree(n2.first, n2.second)) out[n2] = d
        }
        return out
    }

    /** Passo vietato dal gioco tra due tile calpestabili a quote diverse (stessa tabella del gioco). */
    fun stepBlocked(a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean {
        if (!inside(a.first, a.second) || !inside(b.first, b.second)) return false
        val ta = tiles[a.second * width + a.first]; val tb = tiles[b.second * width + b.first]
        return TILE_PAIRS.any { it.first == tileset && (it.second == ta && it.third == tb || it.second == tb && it.third == ta) }
    }

    /** Percorso più breve (direzioni) da (sx, sy) a uno degli obiettivi; null se irraggiungibile. */
    fun path(sx: Int, sy: Int, goals: Set<Pair<Int, Int>>): List<Dir>? {
        if ((sx to sy) in goals) return emptyList()
        val prev = HashMap<Pair<Int, Int>, Pair<Pair<Int, Int>, Dir>>()
        val queue = ArrayDeque(listOf(sx to sy))
        val seen = hashSetOf(sx to sy)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (d in Dir.entries) {
                val next = cur.first + d.dx to cur.second + d.dy
                if (next in seen || !isFree(next.first, next.second) || stepBlocked(cur, next)) continue
                // passando su una porta o una scala il gioco cambia luogo: solo se è lì che si vuole andare
                if (next in warps && next !in goals) continue
                seen += next
                prev[next] = cur to d
                if (next in goals) {
                    val out = ArrayList<Dir>()
                    var c = next
                    while (c != (sx to sy)) { val (p, dir) = prev.getValue(c); out += dir; c = p }
                    return out.reversed()
                }
                queue.addLast(next)
            }
        }
        return null
    }

    enum class Dir(val dx: Int, val dy: Int, val key: Int) {
        UP(0, -1, Keys.UP), DOWN(0, 1, Keys.DOWN), LEFT(-1, 0, Keys.LEFT), RIGHT(1, 0, Keys.RIGHT)
    }

    companion object {
        /** Numero di porte del luogo, poi 4 byte per porta: riga, colonna, porta e luogo di arrivo. */
        private const val WARPS = 0xD3B2
        private const val CAVERN = 17
        private const val FOREST = 3
        /** TilePairCollisionsLand del gioco: set, tile, tile. */
        private val TILE_PAIRS = listOf(
            Triple(CAVERN, 0x20, 0x05), Triple(CAVERN, 0x41, 0x05), Triple(FOREST, 0x30, 0x2E), Triple(CAVERN, 0x2A, 0x05),
            Triple(CAVERN, 0x05, 0x21), Triple(FOREST, 0x52, 0x2E), Triple(FOREST, 0x55, 0x2E), Triple(FOREST, 0x56, 0x2E),
            Triple(FOREST, 0x20, 0x2E), Triple(FOREST, 0x5E, 0x2E), Triple(FOREST, 0x5F, 0x2E),
        )
        /**
         * @param read legge [n] byte di memoria a partire da un indirizzo.
         * @param rom la ROM dell'utente (per blocchi e lista dei tile calpestabili).
         */
        fun build(read: (address: Int, n: Int) -> ByteArray, rom: ByteArray): MapGrid? {
            val dims = read(M.CUR_MAP_HEIGHT, 2)
            val h = dims[0].toInt() and 0xFF
            val w = dims[1].toInt() and 0xFF
            if (w == 0 || h == 0 || (w + 6) * (h + 6) > 1300) return null
            val ts = read(M.TILESET_BANK, 7)
            val bank = ts[0].toInt() and 0xFF
            val blocksPtr = (ts[1].toInt() and 0xFF) or ((ts[2].toInt() and 0xFF) shl 8)
            val collPtr = (ts[5].toInt() and 0xFF) or ((ts[6].toInt() and 0xFF) shl 8)
            if (blocksPtr !in 0x4000..0x7FFF) return null
            val collOffset = if (collPtr < 0x4000) collPtr else 0x4000 + (collPtr - 0x4000)
            val walkable = HashSet<Int>()
            var i = collOffset
            while (i < rom.size && (rom[i].toInt() and 0xFF) != 0xFF && walkable.size < 64) { walkable += rom[i].toInt() and 0xFF; i++ }
            if (walkable.isEmpty()) return null
            val blockset = bank * 0x4000 + (blocksPtr - 0x4000)
            val map = read(M.OVERWORLD_MAP, (w + 6) * (h + 6))
            val gw = w * 2
            val gh = h * 2
            val passable = BooleanArray(gw * gh)
            val tiles = IntArray(gw * gh) { -1 }
            for (y in 0 until gh) for (x in 0 until gw) {
                val block = map[(y / 2 + 3) * (w + 6) + x / 2 + 3].toInt() and 0xFF
                val t = blockset + block * 16 + ((y % 2) * 2 + 1) * 4 + (x % 2) * 2
                if (t !in rom.indices) continue
                tiles[y * gw + x] = rom[t].toInt() and 0xFF
                passable[y * gw + x] = tiles[y * gw + x] in walkable
            }
            val talking = read(M.TILESET_TALKING_OVER_TILES, 3).map { it.toInt() and 0xFF }.filter { it != 0xFF }.toSet()
            val s1 = read(M.SPRITE_STATE_DATA_1, 256)
            val s2 = read(M.SPRITE_STATE_DATA_2, 256)
            val people = HashSet<Pair<Int, Int>>()
            val hidden = hiddenSprites(read(M.TOGGLE_LIST, 64), read(M.TOGGLE_FLAGS, 32))
            for (n in 1..14) {
                if (s1[n * 16].toInt() == 0 || n in hidden) continue
                val py = (s2[n * 16 + 4].toInt() and 0xFF) - 4
                val px = (s2[n * 16 + 5].toInt() and 0xFF) - 4
                if (px in 0 until gw && py in 0 until gh) people += px to py
            }
            val signData = read(M.NUM_SIGNS, 1 + 2 * 16)
            val signCount = (signData[0].toInt() and 0xFF).coerceAtMost(16)
            val signs = (0 until signCount).map { k ->
                (signData[2 + 2 * k].toInt() and 0xFF) to (signData[1 + 2 * k].toInt() and 0xFF)
            }.toSet()
            val tileset = read(M.CUR_MAP_TILESET, 1)[0].toInt() and 0xFF
            val warpData = read(WARPS, 1 + 4 * 32)
            val warpCount = (warpData[0].toInt() and 0xFF).coerceAtMost(32)
            val warps = (0 until warpCount).map { k -> (warpData[2 + 4 * k].toInt() and 0xFF) to (warpData[1 + 4 * k].toInt() and 0xFF) }.toSet()
            return MapGrid(gw, gh, passable, people, tiles, talking, signs, tileset, warps)
        }

        /** Sprite che il gioco ha nascosto (oggetti raccolti, persone non presenti): stessa regola del gioco. */
        fun hiddenSprites(list: ByteArray, flags: ByteArray): Set<Int> {
            val out = HashSet<Int>()
            var i = 0
            while (i + 1 < list.size) {
                val slot = list[i].toInt() and 0xFF
                if (slot == 0xFF) break
                val g = list[i + 1].toInt() and 0xFF
                if (g / 8 < flags.size && (flags[g / 8].toInt() shr (g % 8)) and 1 == 1) out += slot
                i += 2
            }
            return out
        }
    }
}
