package it.giallotouch.bridge

/**
 * Riconoscimento dei menu del gioco (lotta, domande Sì/No) nella versione italiana, dalla memoria.
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
 * - domanda Sì/No: tasti osservati = A|B (0x03), elemento massimo 1, righe distanziate di 2;
 *   accanto al cursore le lettere "S" (0x92) e "N" (0x8D). Nella domanda del soprannome: Y = 0x08, X = 0x0F.
 * - menu iniziale (CONTINUA / NUOVO GIOCO / OPZIONI): Y = 2, X = 1, tasti osservati A|B|START (0x0B),
 *   elemento massimo 1 o 2; riquadro dall'angolo (0,0) 0x79 a (14, 2·max+3) 0x7E.
 */
enum class MenuKind { NONE, BATTLE_MAIN, BATTLE_MOVES, YES_NO, MAIN_MENU }

/** [items]: numero di voci, usato per il menu iniziale (2 senza salvataggio del gioco, 3 con "CONTINUA"). */
data class MenuState(val kind: MenuKind, val column: Int = 0, val item: Int = 0, val items: Int = 0) {
    companion object {
        val NONE = MenuState(MenuKind.NONE)
    }
}

object MenuReader {
    const val MENU_VARS = 0xCC24
    const val MENU_VARS_SIZE = 7
    const val SCREEN_WIDTH = 20
    const val SCREEN_SIZE = 20 * 18
    private const val CURSOR = 0xED
    private const val LETTER_S = 0x92
    private const val LETTER_N = 0x8D

    fun read(menuVars: ByteArray, tileMap: ByteArray): MenuState {
        val topY = menuVars[0].toInt() and 0xFF
        val topX = menuVars[1].toInt() and 0xFF
        val current = menuVars[2].toInt() and 0xFF
        val maxItem = menuVars[4].toInt() and 0xFF
        val watchedKeys = menuVars[5].toInt() and 0xFF
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
        if (watchedKeys == 0x03 && maxItem == 1 && current <= 1 && tile(topX, topY + 2 * current) == CURSOR &&
            tile(topX + 1, topY) == LETTER_S && tile(topX + 1, topY + 2) == LETTER_N) {
            return MenuState(MenuKind.YES_NO, item = current)
        }
        if (watchedKeys == 0x0B && topY == 2 && topX == 1 && maxItem in 1..2 && current <= maxItem &&
            tile(0, 0) == 0x79 && tile(14, 0) == 0x7B && tile(14, 2 * maxItem + 3) == 0x7E && tile(1, 2 + 2 * current) == CURSOR) {
            return MenuState(MenuKind.MAIN_MENU, item = current, items = maxItem + 1)
        }
        return MenuState.NONE
    }
}

/** Comandi che l'interfaccia nativa può chiedere al gioco. */
sealed interface GameCommand {
    /** true per i comandi che hanno senso solo durante la lotta. */
    val battleOnly: Boolean get() = true

    data class Fight(val moveIndex: Int) : GameCommand
    data object Item : GameCommand
    data object Party : GameCommand
    data object Run : GameCommand
    data class Answer(val yes: Boolean) : GameCommand {
        override val battleOnly: Boolean get() = false
    }
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
 * Esegue un comando premendo i tasti del gioco uno alla volta e controllando
 * dopo ogni pressione che il menu sia cambiato come previsto (circuito chiuso).
 * Non scrive mai nella memoria del gioco. Se qualcosa non torna entro il tempo massimo, si ferma.
 */
class CommandExecutor(private val timeoutFrames: Int = 360) {
    private var command: GameCommand? = null
    private var framesLeft = 0
    private var heldKey = 0
    private var holdFrames = 0
    private var cooldown = 0
    private var finishing = false

    val busy: Boolean get() = command != null

    fun start(cmd: GameCommand) {
        command = cmd
        framesLeft = timeoutFrames
        heldKey = 0; holdFrames = 0; cooldown = 0; finishing = false
    }

    fun cancel() { command = null; heldKey = 0 }

    /** Chiamata una volta per fotogramma: restituisce i tasti da tenere premuti in questo fotogramma. */
    fun step(menu: MenuState, inBattle: Boolean): Int {
        val cmd = command ?: return 0
        if ((cmd.battleOnly && !inBattle) || --framesLeft <= 0) { cancel(); return 0 }
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
        fun decide(cmd: GameCommand, menu: MenuState): Decision? = when (menu.kind) {
            MenuKind.NONE, MenuKind.MAIN_MENU -> null
            MenuKind.YES_NO -> if (cmd is GameCommand.Answer) {
                val target = if (cmd.yes) 0 else 1
                when {
                    menu.item < target -> Decision(Keys.DOWN)
                    menu.item > target -> Decision(Keys.UP)
                    else -> Decision(Keys.A, done = true)
                }
            } else null
            MenuKind.BATTLE_MOVES -> when (cmd) {
                is GameCommand.Fight -> when {
                    menu.item < cmd.moveIndex -> Decision(Keys.DOWN)
                    menu.item > cmd.moveIndex -> Decision(Keys.UP)
                    else -> Decision(Keys.A, done = true)
                }
                else -> Decision(Keys.B)
            }
            MenuKind.BATTLE_MAIN -> if (cmd is GameCommand.Answer) null else {
                val (column, item) = when (cmd) {
                    is GameCommand.Fight -> 0 to 0
                    GameCommand.Item -> 0 to 1
                    GameCommand.Party -> 1 to 0
                    else -> 1 to 1
                }
                when {
                    menu.column < column -> Decision(Keys.RIGHT)
                    menu.column > column -> Decision(Keys.LEFT)
                    menu.item < item -> Decision(Keys.DOWN)
                    menu.item > item -> Decision(Keys.UP)
                    else -> Decision(Keys.A, done = cmd !is GameCommand.Fight)
                }
            }
        }
    }
}
