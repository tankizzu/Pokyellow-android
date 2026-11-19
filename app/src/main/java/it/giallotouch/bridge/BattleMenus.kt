package it.giallotouch.bridge

/**
 * SQUADRA e BORSA in lotta nello stile dell'app (D-050, versione C del mockup): l'app mostra le sue liste sopra la
 * lotta e, alla scelta, guida i menu originali del gioco senza mostrarli. Schermate (verificate sul gioco vero):
 * - menu di lotta: [MenuReader] (LOTTA in colonna 0 voce 0, STRUM. colonna 0 voce 1, PKMN colonna 1 voce 0);
 * - squadra: variabili Y = 1, X = 0, tasti 0x03; cursore ▶ in (0, 1 + 2i);
 * - sottomenu del Pokémon: Y = X = 0x0C, massimo 2, tasti 0x03; CAMBIA / STAT. / ESCI, cursore in (12, 12 + 2c);
 * - lista degli strumenti: come fuori dalla lotta ([ItemMenus], Y = 4, X = 5, tasti 0x07).
 */
object BattleMenus {
    enum class Kind { BATTLE_MAIN, PARTY, PARTY_SUB, ITEM_LIST, OTHER }

    data class Screen(val kind: Kind, val cursor: Int = 0, val column: Int = 0, val name: String = "")

    fun read(vars: ByteArray, tiles: ByteArray, scrollOffset: Int): Screen {
        fun t(x: Int, y: Int) = tiles[y * 20 + x].toInt() and 0xFF
        val topY = vars[0].toInt() and 0xFF
        val topX = vars[1].toInt() and 0xFF
        val maxItem = vars[4].toInt() and 0xFF
        val watched = vars[5].toInt() and 0xFF
        val menu = MenuReader.read(vars, tiles)
        if (menu.kind == MenuKind.BATTLE_MAIN) return Screen(Kind.BATTLE_MAIN, menu.item, menu.column)
        if (topY == 0x0C && topX == 0x0C && maxItem == 2 && watched == 0x03) {
            (0..2).firstOrNull { t(12, 12 + 2 * it) == CURSOR }?.let { return Screen(Kind.PARTY_SUB, it) }
        }
        if (topY == 1 && topX == 0 && watched == 0x03) {
            (0..5).firstOrNull { t(0, 1 + 2 * it) == CURSOR }?.let { return Screen(Kind.PARTY, it) }
        }
        val item = ItemMenus.read(vars, tiles, scrollOffset)
        if (item.kind == ItemMenus.Kind.ITEM_LIST) return Screen(Kind.ITEM_LIST, item.cursor, name = item.name)
        return Screen(Kind.OTHER)
    }

    private const val CURSOR = 0xED
}

/** Azione di lotta scelta nelle liste dell'app. */
sealed interface BattleAction {
    /** Manda in campo il Pokémon in posizione [slot] della squadra. */
    data class Switch(val slot: Int) : BattleAction
    /** Usa lo strumento [slot] della borsa ([name] per controllo); se il gioco chiede su chi, sceglie [target]. */
    data class UseItem(val slot: Int, val name: String, val target: Int) : BattleAction
}

/**
 * Esecutore a circuito chiuso per [BattleAction]: dopo ogni pressione aspetta che lo schermo cambi; se resta troppo
 * in una schermata che non conosce, si ferma e lascia il gioco al giocatore. Non scrive mai in memoria.
 */
class BattleActionExecutor(private val timeoutFrames: Int = 60 * 20) {
    enum class Result { DONE, FAILED }

    private var action: BattleAction? = null
    private var framesLeft = 0
    private var heldKey = 0
    private var holdFrames = 0
    private var minWait = 0
    private var waitFrames = 0
    private var before: Any? = null
    private var unknownFrames = 0
    private var itemChosen = false
    private var finishing = false
    private var lastScreen: BattleMenus.Screen? = null
    private var stable = 0
    private var settleNeeded = 8

    var result: Result? = null
        private set
    val busy: Boolean get() = action != null

    fun start(a: BattleAction) {
        action = a; framesLeft = timeoutFrames; heldKey = 0; holdFrames = 0; minWait = RELEASE; waitFrames = 0
        before = null; unknownFrames = 0; itemChosen = false; finishing = false; result = null; lastScreen = null; stable = 0
    }

    fun cancel() = finish(Result.FAILED)
    private fun finish(r: Result) { action = null; heldKey = 0; result = r }

    fun step(s: BattleMenus.Screen, observation: Any): Int {
        val a = action ?: return 0
        if (--framesLeft <= 0) { finish(Result.FAILED); return 0 }
        if (holdFrames > 0) { holdFrames--; return heldKey }
        if (minWait > 0) { minWait--; return 0 }
        if (waitFrames > 0) {
            if (observation == before) { waitFrames--; return 0 }
            waitFrames = 0
        }
        if (finishing) { finish(Result.DONE); return 0 }
        // Un menu appena comparso (o cambiato) non ascolta subito i tasti: si aspetta che resti uguale per un po'.
        // Un menu appena comparso aspetta SETTLE fotogrammi; nello stesso menu (cursore spostato) basta poco (D-065).
        if (s != lastScreen) { settleNeeded = if (lastScreen?.kind == s.kind) SETTLE_MOVE else SETTLE; lastScreen = s; stable = 0; return 0 }
        if (++stable < settleNeeded) return 0
        val key = decide(a, s)
        if (key == 0) {
            // Strumento scelto e nessuna domanda "su chi?": il gioco lo sta già usando.
            if (itemChosen && s.kind == BattleMenus.Kind.OTHER && ++unknownFrames > ITEM_SETTLE) { finish(Result.DONE); return 0 }
            if (!itemChosen && ++unknownFrames > MAX_UNKNOWN) finish(Result.FAILED)
            return 0
        }
        unknownFrames = 0
        before = observation
        heldKey = key; holdFrames = HOLD - 1; minWait = RELEASE; waitFrames = CHANGE_WAIT
        return key
    }

    private fun decide(a: BattleAction, s: BattleMenus.Screen): Int = when (s.kind) {
        BattleMenus.Kind.BATTLE_MAIN -> {
            val (col, item) = if (a is BattleAction.Switch) 1 to 0 else 0 to 1
            when {
                itemChosen -> { finishing = true; 0 }
                s.column < col -> Keys.RIGHT
                s.column > col -> Keys.LEFT
                s.cursor < item -> Keys.DOWN
                s.cursor > item -> Keys.UP
                else -> Keys.A
            }
        }
        BattleMenus.Kind.PARTY -> {
            val target = when (a) { is BattleAction.Switch -> a.slot; is BattleAction.UseItem -> a.target }
            when {
                a is BattleAction.UseItem && !itemChosen -> Keys.B
                s.cursor < target -> Keys.DOWN
                s.cursor > target -> Keys.UP
                // Cambio: scelto il Pokémon il lavoro è fatto (dopo un KO il gioco lo manda subito in campo, senza
                // sottomenu): se poi ricompare il menu di lotta non si ricomincia ("…è già in campo!").
                else -> { if (a is BattleAction.UseItem) finishing = true else itemChosen = true; Keys.A }
            }
        }
        BattleMenus.Kind.PARTY_SUB -> when {
            a !is BattleAction.Switch -> Keys.B
            s.cursor > 0 -> Keys.UP
            else -> { finishing = true; Keys.A }
        }
        BattleMenus.Kind.ITEM_LIST -> when {
            a !is BattleAction.UseItem -> Keys.B
            itemChosen -> Keys.B   // strumento che ora non si può usare: il gioco torna alla lista, si chiude
            s.cursor < a.slot -> Keys.DOWN
            s.cursor > a.slot -> Keys.UP
            a.name.isNotEmpty() && s.name != a.name -> { finish(Result.FAILED); 0 }
            else -> { itemChosen = true; Keys.A }
        }
        BattleMenus.Kind.OTHER -> 0
    }

    companion object {
        private const val HOLD = 3
        private const val RELEASE = 5
        private const val CHANGE_WAIT = 45
        private const val MAX_UNKNOWN = 150
        private const val ITEM_SETTLE = 90
        private const val SETTLE = 8
        private const val SETTLE_MOVE = 2
    }
}
