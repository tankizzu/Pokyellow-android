package it.giallotouch.bridge

/**
 * Riconoscimento dei menu di lotta della versione italiana, dalla memoria del gioco.
 *
 * Variabili dei menu (CC24–CC2A): Y e X del primo elemento, elemento corrente, (tile sotto il cursore),
 * elemento massimo, tasti osservati, ultimo elemento. Per evitare valori rimasti da un menu chiuso,
 * il menu è considerato attivo solo se nella mappa dello schermo (C3A0, 20×18) c'è davvero il cursore ▶ (0xED)
 * nella posizione attesa.
 *
 * Valori verificati:
 * - menu principale di lotta: Y = 0x0E, X = 0x07 (colonna sinistra: 0 lotta, 1 strumenti)
 *   oppure X = 0x0E (colonna destra: 0 squadra, 1 fuga); righe distanziate di 2.
 * - menu delle mosse: Y = 0x0C, X = 0x05; elemento 1 = prima mossa; righe distanziate di 1.
 */
enum class MenuKind { NONE, BATTLE_MAIN, BATTLE_MOVES }

data class MenuState(val kind: MenuKind, val column: Int = 0, val item: Int = 0) {
    companion object {
        val NONE = MenuState(MenuKind.NONE)
    }
}

object BattleMenuReader {
    const val MENU_VARS = 0xCC24
    const val MENU_VARS_SIZE = 7
    const val SCREEN_WIDTH = 20
    const val SCREEN_SIZE = 20 * 18
    private const val CURSOR = 0xED

    fun read(menuVars: ByteArray, tileMap: ByteArray): MenuState {
        val topY = menuVars[0].toInt() and 0xFF
        val topX = menuVars[1].toInt() and 0xFF
        val current = menuVars[2].toInt() and 0xFF
        fun tile(x: Int, y: Int): Int {
            val i = y * SCREEN_WIDTH + x
            return if (x in 0 until SCREEN_WIDTH && i in tileMap.indices) tileMap[i].toInt() and 0xFF else -1
        }
        if (topY == 0x0E && (topX == 0x07 || topX == 0x0E) && current <= 1 && tile(topX, topY + 2 * current) == CURSOR) {
            return MenuState(MenuKind.BATTLE_MAIN, column = if (topX == 0x07) 0 else 1, item = current)
        }
        if (topY == 0x0C && topX == 0x05 && current in 1..4 && tile(topX, topY + current) == CURSOR) {
            return MenuState(MenuKind.BATTLE_MOVES, item = current - 1)
        }
        return MenuState.NONE
    }
}

/** Comandi di lotta che l'interfaccia nativa può chiedere. */
sealed interface BattleCommand {
    data class Fight(val moveIndex: Int) : BattleCommand
    data object Item : BattleCommand
    data object Party : BattleCommand
    data object Run : BattleCommand
}

/** Bit dei tasti (stesso ordine del motore). */
object Keys {
    const val RIGHT = 1
    const val LEFT = 2
    const val UP = 4
    const val DOWN = 8
    const val A = 16
    const val B = 32
}

/**
 * Esegue un comando di lotta premendo i tasti del gioco uno alla volta e controllando
 * dopo ogni pressione che il menu sia cambiato come previsto (circuito chiuso).
 * Non scrive mai nella memoria del gioco. Se qualcosa non torna entro il tempo massimo, si ferma.
 */
class BattleCommandExecutor(private val timeoutFrames: Int = 360) {
    private var command: BattleCommand? = null
    private var framesLeft = 0
    private var heldKey = 0
    private var holdFrames = 0
    private var cooldown = 0
    private var finishing = false

    val busy: Boolean get() = command != null

    fun start(cmd: BattleCommand) {
        command = cmd
        framesLeft = timeoutFrames
        heldKey = 0; holdFrames = 0; cooldown = 0; finishing = false
    }

    fun cancel() { command = null; heldKey = 0 }

    /** Chiamata una volta per fotogramma: restituisce i tasti da tenere premuti in questo fotogramma. */
    fun step(menu: MenuState, inBattle: Boolean): Int {
        val cmd = command ?: return 0
        if (!inBattle || --framesLeft <= 0) { cancel(); return 0 }
        if (holdFrames > 0) { holdFrames--; return heldKey }
        if (cooldown > 0) { cooldown--; return 0 }
        if (finishing) { cancel(); return 0 }
        val decision = decide(cmd, menu) ?: return 0
        heldKey = decision.key
        holdFrames = HOLD - 1
        cooldown = RELEASE
        finishing = decision.done
        return heldKey
    }

    data class Decision(val key: Int, val done: Boolean = false)

    companion object {
        private const val HOLD = 3
        private const val RELEASE = 5

        /** Prossimo tasto da premere per avvicinarsi al comando; null = aspettare (testo in corso). */
        fun decide(cmd: BattleCommand, menu: MenuState): Decision? = when (menu.kind) {
            MenuKind.NONE -> null
            MenuKind.BATTLE_MOVES -> when (cmd) {
                is BattleCommand.Fight -> when {
                    menu.item < cmd.moveIndex -> Decision(Keys.DOWN)
                    menu.item > cmd.moveIndex -> Decision(Keys.UP)
                    else -> Decision(Keys.A, done = true)
                }
                else -> Decision(Keys.B)
            }
            MenuKind.BATTLE_MAIN -> {
                val (column, item) = when (cmd) {
                    is BattleCommand.Fight -> 0 to 0
                    BattleCommand.Item -> 0 to 1
                    BattleCommand.Party -> 1 to 0
                    BattleCommand.Run -> 1 to 1
                }
                when {
                    menu.column < column -> Decision(Keys.RIGHT)
                    menu.column > column -> Decision(Keys.LEFT)
                    menu.item < item -> Decision(Keys.DOWN)
                    menu.item > item -> Decision(Keys.UP)
                    else -> Decision(Keys.A, done = cmd !is BattleCommand.Fight)
                }
            }
        }
    }
}
