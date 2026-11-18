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
 * - scheda allenatore: riquadro da (0,0) e "NOME/" in (2,2) ([trainerCard]).
 */
object ItemMenus {
    private const val CURSOR = 0xED
    private const val MORE = 0xEE
    private val STRUM = intArrayOf(0x92, 0x93, 0x91, 0x94, 0x8C)   // "STRUM" nel menu START

    enum class Kind { START_MENU, ITEM_LIST, USE_TOSS, QUANTITY, YES_NO, TEXT_WAIT, PARTY, PC_MENU, OTHER }

    data class Screen(
        val kind: Kind,
        /** Voce del cursore: nel menu START e in USA/GETTA l'indice, nella lista lo strumento (scorrimento + riga). */
        val cursor: Int = 0,
        /** Menu START: indice della voce STRUM. (−1 se non c'è). Quantità: valore mostrato. */
        val value: Int = -1,
        /** Lista: nome mostrato sulla riga del cursore. */
        val name: String = "",
    )

    private val NOME = intArrayOf(0x8D, 0x8E, 0x8C, 0x84, 0xF3)   // "NOME/" della scheda allenatore

    /** La scheda allenatore del gioco è sullo schermo. */
    fun trainerCard(tiles: ByteArray): Boolean =
        tiles.size >= 360 && (tiles[0].toInt() and 0xFF) == 0x79 && NOME.indices.all { (tiles[2 * 20 + 2 + it].toInt() and 0xFF) == NOME[it] }

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
        // Menu del PC del giocatore (D-055): riquadro (0,0)–(17,9), PRELEVA / DEPOSITA / BUTTA / DISCONNETTI,
        // Y = 2, X = 1, massimo 3, tasti 0x03, cursore ▶ in (1, 2 + 2i).
        if (topY == 2 && topX == 1 && maxItem == 3 && watched == 0x03 && t(0, 0) == 0x79 && t(17, 0) == 0x7B && t(17, 9) == 0x7E) {
            (0..3).firstOrNull { t(1, 2 + 2 * it) == CURSOR }?.let { return Screen(Kind.PC_MENU, cursor = it) }
        }
        // Menu squadra "su chi usarlo?": Y = 1, X = 0, tasti 0x03, cursore ▶ in (0, 1 + 2i) (come in lotta).
        if (topY == 1 && topX == 0 && watched == 0x03) {
            (0..5).firstOrNull { t(0, 1 + 2 * it) == CURSOR }?.let { return Screen(Kind.PARTY, cursor = it) }
        }
        if (t(18, 16) == MORE && t(0, 12) == 0x79) return Screen(Kind.TEXT_WAIT)
        return Screen(Kind.OTHER)
    }
}

/** Azione su uno strumento della borsa: [slot] = posizione nella borsa, [name] = nome atteso (controllo). */
sealed interface ItemAction {
    val slot: Int
    val name: String

    /**
     * Usa: l'app sceglie lo strumento e "USA"; con [target] sceglie anche il Pokémon quando il gioco chiede su chi
     * usarlo (D-051), altrimenti il gioco continua da solo.
     */
    data class Use(override val slot: Int, override val name: String, val target: Int? = null) : ItemAction

    /** Getta [quantity] pezzi, con conferma "Sì", poi chiude i menu. */
    data class Toss(override val slot: Int, override val name: String, val quantity: Int) : ItemAction

    /** Apre la scheda allenatore del gioco: la voce col nome del giocatore, subito dopo STRUM. nel menu START (D-045). */
    data object OpenTrainer : ItemAction { override val slot = -1; override val name = "" }

    /** PC del giocatore (D-055): preleva un pezzo dello strumento [slot] del PC. */
    data class PcWithdraw(override val slot: Int, override val name: String) : ItemAction

    /** PC del giocatore (D-055): deposita un pezzo dello strumento [slot] della borsa. */
    data class PcDeposit(override val slot: Int, override val name: String) : ItemAction

    /** Sposta lo strumento [slot] al posto [to], come nel gioco: SELECT sul primo, SELECT sul secondo (scambio). */
    data class Swap(override val slot: Int, override val name: String, val to: Int) : ItemAction
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
    /** Usa con un Pokémon da scegliere: "USA" premuto, si aspetta il menu squadra del gioco. */
    private var awaitingTarget = false
    private var lastScreen: ItemMenus.Screen? = null
    private var stable = 0

    var result: Result? = null
        private set
    val busy: Boolean get() = action != null

    fun start(a: ItemAction) {
        action = a
        framesLeft = timeoutFrames
        heldKey = 0; holdFrames = 0; waitFrames = 0; minWait = 0
        before = null; unknownFrames = 0; tossed = false; useChosen = false; awaitingTarget = false; firstSelected = false; result = null; lastScreen = null; stable = 0
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
        // Un menu appena comparso (o cambiato) non ascolta subito i tasti: si aspetta che resti uguale per un po'.
        if (screen != lastScreen) { lastScreen = screen; stable = 0; return 0 }
        if (++stable < SETTLE) return 0
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

    /** Scambio: SELECT sullo strumento [ItemAction.slot], poi SELECT su [ItemAction.Swap.to], poi si chiudono i menu. */
    private var firstSelected = false
    private fun decideSwap(a: ItemAction.Swap, s: ItemMenus.Screen): Int = when {
        tossed -> Keys.B
        !firstSelected && s.cursor < a.slot -> Keys.DOWN
        !firstSelected && s.cursor > a.slot -> Keys.UP
        !firstSelected && a.name.isNotEmpty() && !a.name.startsWith("MT") && !a.name.startsWith("MN") && s.name != a.name -> { action = null; result = Result.FAILED; 0 }
        !firstSelected -> { firstSelected = true; SELECT }
        s.cursor < a.to -> Keys.DOWN
        s.cursor > a.to -> Keys.UP
        else -> { tossed = true; SELECT }
    }

    /** PC: menu → PRELEVA/DEPOSITA → strumento → quantità 1 → messaggio → di nuovo al menu del PC. */
    private fun decidePc(a: ItemAction, s: ItemMenus.Screen): Int = when (s.kind) {
        ItemMenus.Kind.PC_MENU -> {
            val target = if (a is ItemAction.PcWithdraw) 0 else 1
            when {
                tossed -> DONE
                s.cursor < target -> Keys.DOWN
                s.cursor > target -> Keys.UP
                else -> Keys.A
            }
        }
        ItemMenus.Kind.ITEM_LIST -> when {
            tossed -> Keys.B
            s.cursor < a.slot -> Keys.DOWN
            s.cursor > a.slot -> Keys.UP
            a.name.isNotEmpty() && !a.name.startsWith("MT") && !a.name.startsWith("MN") && s.name != a.name -> { action = null; result = Result.FAILED; 0 }
            else -> Keys.A
        }
        ItemMenus.Kind.QUANTITY -> when {
            s.value > 1 -> Keys.DOWN
            s.value < 1 -> Keys.UP
            else -> { tossed = true; Keys.A }
        }
        ItemMenus.Kind.TEXT_WAIT -> Keys.A
        else -> 0
    }

    private fun decide(a: ItemAction, s: ItemMenus.Screen, freeRoam: Boolean): Int = if (a is ItemAction.PcWithdraw || a is ItemAction.PcDeposit) decidePc(a, s) else when (s.kind) {
        ItemMenus.Kind.START_MENU -> if (a is ItemAction.OpenTrainer) when {
            s.value < 0 -> 0
            s.cursor < s.value + 1 -> Keys.DOWN
            s.cursor > s.value + 1 -> Keys.UP
            else -> { useChosen = true; Keys.A }
        } else when {
            tossed -> Keys.B
            s.value < 0 -> 0
            s.cursor < s.value -> Keys.DOWN
            s.cursor > s.value -> Keys.UP
            else -> Keys.A
        }
        ItemMenus.Kind.ITEM_LIST -> if (a is ItemAction.Swap) decideSwap(a, s) else when {
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
                awaitingTarget -> 0
                s.cursor < target -> Keys.DOWN
                s.cursor > target -> Keys.UP
                else -> { if (a is ItemAction.Use) { if (a.target == null) useChosen = true else awaitingTarget = true }; Keys.A }
            }
        }
        ItemMenus.Kind.PC_MENU -> 0
        ItemMenus.Kind.PARTY -> {
            val t = (a as? ItemAction.Use)?.target
            when {
                t == null || !awaitingTarget -> 0
                s.cursor < t -> Keys.DOWN
                s.cursor > t -> Keys.UP
                else -> { useChosen = true; Keys.A }
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
    private val SELECT = 64

    companion object {
        private const val DONE = -1
        private const val HOLD = 3
        private const val RELEASE = 3
        private const val CHANGE_WAIT = 45
        private const val MAX_UNKNOWN = 150
        private const val SETTLE = 8
    }
}
