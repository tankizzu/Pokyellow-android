package it.giallotouch.bridge

/**
 * Menu iniziale ridisegnato come ha scelto il committente (D-040), con i tile del gioco. Si tocca la voce col dito
 * oppure, alla vecchia maniera, si sposta il cursore ▶ con la levetta e si conferma con un tocco (tasto A):
 * riquadro 18 caselle di larghezza in alto a sinistra dell'immagine del gioco, voci ogni 3 righe dalla riga 2,
 * nell'ordine CONTINUA (se c'è un salvataggio del gioco), CARICA PARTITA, OPZIONI, NUOVO GIOCO.
 * Coordinate in caselle rispetto all'immagine originale del gioco (che il riquadro copre).
 */
object StartMenuLayout {
    const val WIDTH = 18

    enum class Entry(val label: String) { CONTINUA("CONTINUA"), CARICA("CARICA PARTITA"), OPZIONI("OPZIONI"), NUOVO("NUOVO GIOCO") }

    /** Voci per il menu del gioco con [gameItems] voci (3 con CONTINUA, 2 senza). */
    fun entries(gameItems: Int): List<Entry> =
        if (gameItems >= 3) listOf(Entry.CONTINUA, Entry.CARICA, Entry.OPZIONI, Entry.NUOVO)
        else listOf(Entry.CARICA, Entry.OPZIONI, Entry.NUOVO)

    fun row(i: Int) = 2 + 3 * i
    fun height(gameItems: Int) = row(entries(gameItems).size - 1) + 3

    /** Voce del gioco corrispondente (indice del menu originale), null per CARICA PARTITA. */
    fun gameIndex(entry: Entry, gameItems: Int): Int? = when (entry) {
        Entry.CARICA -> null
        Entry.CONTINUA -> 0
        Entry.NUOVO -> if (gameItems >= 3) 1 else 0
        Entry.OPZIONI -> if (gameItems >= 3) 2 else 1
    }

    /** Voce toccata alla riga [row] (in caselle dell'immagine), o null fuori dalle voci. */
    fun entryAt(row: Int, col: Int, gameItems: Int): Entry? {
        if (col !in 0 until WIDTH) return null
        val list = entries(gameItems)
        return list.indices.firstOrNull { row in row(it) - 1..row(it) + 1 }?.let { list[it] }
    }

    fun draw(c: TileCanvas, gameItems: Int, cursor: Int) {
        c.box(0, 0, WIDTH - 1, height(gameItems) - 1)
        entries(gameItems).forEachIndexed { i, e -> c.text(e.label, 2, row(i)) }
        if (cursor in entries(gameItems).indices) c.tile(TileCanvas.CURSOR, 1, row(cursor))
    }
}
