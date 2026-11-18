package it.giallotouch.bridge

/**
 * Riconoscimento delle schermate del menu STRUMENTI del gioco (versione italiana, verificato sul gioco vero)
 * ed esecutore che usa o getta uno strumento passando dal menu originale, come farebbe il giocatore.
 *
 * Schermate (variabili dei menu CC24–CC2A, scorrimento delle liste CC36, mappa dello schermo C3A0):
 * - menu START: Y = 2, X = 0x0B, tasti osservati 0xCB; voci ogni 2 righe, cursore ▶ in (11, 2+2i).
 *   L'ordine cambia quando si ha il Pokédex: la voce "STRUM." si riconosce dalle lettere sullo schermo.
 * - lista degli strumenti: Y = 4, X = 5, tasti osservati 0x07; 4 voci visibili, cursore ▶ in (5, 4+2c),
 *   strumento numero CC36 + c della borsa; nome dalla colonna 6.
 * - scelta USA / GETTA: Y = 0x0B, X = 0x0D, massimo 1, tasti 0x03, "U" in (14,11), cursore in (13, 11+2c).
 * - quantità da gettare: riquadro (15,9)–(19,11) con "×" in (16,10) e due cifre in (17,10)–(18,10).
 * - domanda di conferma: riquadro Sì/No (MenuReader.YES_NO); testi in attesa: ▼ (0xEE) in (18,16).
 */
object ItemMenus {
    private const val CURSOR = 0xED
    private const val MORE = 0xEE
    private val STRUM = intArrayOf(0x92, 0x93, 0x91, 0x94, 0x8C)   // "STRUM" nel menu START

    enum class Kind { START_MENU, ITEM_LIST, USE_TOSS, QUANTITY, YES_NO, TEXT_WAIT, OTHER }

    data class Screen(
        val kind: Kind,
        /** Voce del cursore: nel menu START e in USA/GETTA l'indice, nella lista lo strumento (scorrimento + riga). */
        val cursor: Int = 0,
        /** Menu START: indice della voce STRUM. (−1 se non c'è). Quantità: valore mostrato. */
        val value: Int = -1,
        /** Lista: nome mostrato sulla riga del cursore. */
        val name: String = "",
    )

    fun read(vars: ByteArray, tiles: ByteArray, scrollOffset: Int): Screen {
        fun t(x: Int, y: Int) = tiles[y * 20 + x].toInt() and 0xFF
        val topY = vars[0].toInt() and 0xFF
        val topX = vars[1].toInt() and 0xFF
        val maxItem = vars[4].toInt() and 0xFF
        val watched = vars[5].toInt() and 0xFF
        val menu = MenuReader.read(vars, tiles)
        if (menu.kind == MenuKind.YES_NO) return Screen(Kind.YES_NO, cursor = menu.item)
        if (t(15, 9) == 0x79 && t(16, 10) == 0xF1 && t(17, 10) in 0xF6..0xFF && t(18, 10) in 0xF6..0xFF) {
            return Screen(Kind.QUANTITY, value = (t(17, 10) - 0xF6) * 10 + (t(18, 10) - 0xF6))
        }
        if (topY == 2 && topX == 0x0B && watched == 0xCB) {
            val cur = (0..7).firstOrNull { 2 + 2 * it < 18 && t(11, 2 + 2 * it) == CURSOR }
            if (cur != null) {
                val strum = (0..7).firstOrNull { i -> 2 + 2 * i < 18 && STRUM.indices.all { t(12 + it, 2 + 2 * i) == STRUM[it] } } ?: -1
                return Screen(Kind.START_MENU, cursor = cur, value = strum)
            }
        }
        if (topY == 0x0B && topX == 0x0D && maxItem == 1 && watched == 0x03 && t(14, 11) == 0x94) {
            val cur = (0..1).firstOrNull { t(13, 11 + 2 * it) == CURSOR }
            if (cur != null) return Screen(Kind.USE_TOSS, cursor = cur)
        }
        if (topY == 4 && topX == 5 && watched == 0x07) {
            val row = (0..3).firstOrNull { t(5, 4 + 2 * it) == CURSOR }
            if (row != null) {
                val name = TextCodec.decode(ByteArray(13) { tiles[(4 + 2 * row) * 20 + 6 + it] }).trim()
                return Screen(Kind.ITEM_LIST, cursor = scrollOffset + row, name = name)
            }
        }
        if (t(18, 16) == MORE && t(0, 12) == 0x79) return Screen(Kind.TEXT_WAIT)
        return Screen(Kind.OTHER)
    }
}

/** Azione su uno strumento della borsa: [slot] = posizione nella borsa, [name] = nome atteso (controllo). */
sealed interface ItemAction {
    val slot: Int
    val name: String

    /** Usa: l'app sceglie lo strumento e "USA", poi il gioco continua (per esempio chiedendo su chi usarlo). */
    data class Use(override val slot: Int, override val name: String) : ItemAction

    /** Getta [quantity] pezzi, con conferma "Sì", poi chiude i menu. */
    data class Toss(override val slot: Int, override val name: String, val quantity: Int) : ItemAction
}

/**
 * Esecutore a circuito chiuso per [ItemAction]: dopo ogni pressione aspetta che lo schermo cambi; se si trova
 * in una schermata che non conosce troppo a lungo, si ferma e lascia il gioco al giocatore. Non scrive mai in memoria.
 */
class ItemActionExecutor(private val timeoutFrames: Int = 60 * 30) {
    enum class Result { DONE, FAILED }

    private var action: ItemAction? = null
    private var framesLeft = 0
    private var heldKey = 0
    private var holdFrames = 0
    private var waitFrames = 0
    private var minWait = 0
    private var before: Any? = null
    private var unknownFrames = 0
    /** true dopo aver risposto "Sì" alla conferma: da lì si chiudono i menu. */
    private var tossed = false
    private var useChosen = false

    var result: Result? = null
        private set
    val busy: Boolean get() = action != null

    fun start(a: ItemAction) {
        action = a
        framesLeft = timeoutFrames
        heldKey = 0; holdFrames = 0; waitFrames = 0; minWait = 0
        before = null; unknownFrames = 0; tossed = false; useChosen = false; result = null
    }

    fun cancel() = finish(Result.FAILED)

    private fun finish(r: Result) { action = null; heldKey = 0; result = r }

    /**
     * Un fotogramma: [screen] dalla memoria, [freeRoam] se si cammina liberamente, [observation] qualcosa che cambia
     * quando il gioco reagisce (variabili dei menu + schermo). Restituisce i tasti da premere.
     */
    fun step(screen: ItemMenus.Screen, freeRoam: Boolean, observation: Any): Int {
        val a = action ?: return 0
        if (--framesLeft <= 0) { finish(Result.FAILED); return 0 }
        if (holdFrames > 0) { holdFrames--; return heldKey }
        if (minWait > 0) { minWait--; return 0 }
        if (waitFrames > 0) {
            if (observation == before) { waitFrames--; return 0 }
            waitFrames = 0
        }
        if (useChosen) { finish(Result.DONE); return 0 }
        val key = decide(a, screen, freeRoam)
        if (key == DONE) { finish(Result.DONE); return 0 }
        if (key == 0) {
            if (++unknownFrames > MAX_UNKNOWN) finish(Result.FAILED)
            return 0
        }
        unknownFrames = 0
        before = observation
        heldKey = key
        holdFrames = HOLD - 1
        minWait = RELEASE
        waitFrames = CHANGE_WAIT
        return key
    }

    private fun decide(a: ItemAction, s: ItemMenus.Screen, freeRoam: Boolean): Int = when (s.kind) {
        ItemMenus.Kind.START_MENU -> when {
            tossed -> Keys.B
            s.value < 0 -> 0
            s.cursor < s.value -> Keys.DOWN
            s.cursor > s.value -> Keys.UP
            else -> Keys.A
        }
        ItemMenus.Kind.ITEM_LIST -> when {
            tossed -> Keys.B
            s.cursor < a.slot -> Keys.DOWN
            s.cursor > a.slot -> Keys.UP
            // controllo: sulla riga deve esserci lo strumento atteso (le macchine MT/MN si riconoscono solo dalla posizione)
            a.name.isNotEmpty() && !a.name.startsWith("MT") && !a.name.startsWith("MN") && s.name != a.name -> { action = null; result = Result.FAILED; 0 }
            else -> Keys.A
        }
        ItemMenus.Kind.USE_TOSS -> {
            val target = if (a is ItemAction.Toss) 1 else 0
            when {
                s.cursor < target -> Keys.DOWN
                s.cursor > target -> Keys.UP
                else -> { if (a is ItemAction.Use) useChosen = true; Keys.A }
            }
        }
        ItemMenus.Kind.QUANTITY -> {
            val q = (a as? ItemAction.Toss)?.quantity ?: 1
            when {
                s.value < q -> Keys.UP
                s.value > q -> Keys.DOWN
                else -> Keys.A
            }
        }
        ItemMenus.Kind.YES_NO -> when {
            a !is ItemAction.Toss || tossed -> 0
            s.cursor > 0 -> Keys.UP
            else -> { tossed = true; Keys.A }
        }
        ItemMenus.Kind.TEXT_WAIT -> if (a is ItemAction.Toss) Keys.A else 0
        ItemMenus.Kind.OTHER -> when {
            freeRoam && tossed -> DONE
            freeRoam -> NativeKeys.START
            else -> 0
        }
    }

    private object NativeKeys { const val START = 128 }

    companion object {
        private const val DONE = -1
        private const val HOLD = 3
        private const val RELEASE = 3
        private const val CHANGE_WAIT = 45
        private const val MAX_UNKNOWN = 150
    }
}
