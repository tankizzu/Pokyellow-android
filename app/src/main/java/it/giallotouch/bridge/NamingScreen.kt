package it.giallotouch.bridge

import java.text.Normalizer

/**
 * Schermata dei nomi del gioco (nome del giocatore, del rivale, soprannome) nella versione italiana.
 *
 * Riconosciuta dalla mappa dello schermo (C3A0, 20×18) e dalle variabili dei menu (CC24–CC2A):
 * - riquadro delle lettere da (0,4) a (19,14): angoli 0x79, 0x7B, 0x7D, 0x7E;
 * - trattini sotto il nome dalla casella (10,3): 0x76, o 0x77 per quello alzato; 7 per giocatore e rivale, 10 per i soprannomi;
 * - Y del primo elemento 3, elemento massimo 7, tasti osservati 0xFF.
 * Griglia: 5 righe × 9 colonne. La lettera della riga r (1–5) e della colonna X (1, 3, …, 17) è nella casella (X+1, 3+2r);
 * la riga 6 è il cambio maiuscole/minuscole. Cursore: X = CC25; la riga si ricava dal ▶ (0xED) nella colonna X,
 * perché CC26 vale 0 per gran parte del tempo (il gioco lo usa per animare l'icona e poi lo ripristina).
 * Nome in corso: CF4F (terminato da 0x50); lunghezza CEE9; minuscole CEEB (verificati sulla ROM italiana).
 * Il titolo ("NOME TUO?", …) è la riga 1 dello schermo, letta così com'è.
 */
data class NamingScreen(
    val title: String,
    val maxLength: Int,
    /** Nome già scritto, in codici del gioco. */
    val name: List<Int>,
    val cursorRow: Int,
    val cursorX: Int,
    /** 45 codici della griglia (5 righe × 9 colonne) così come sono sullo schermo. */
    val grid: List<Int>,
) {
    /** Posizione di una lettera nella griglia attuale: (riga 1–5, X 1–17), oppure null. */
    fun find(code: Int): Pair<Int, Int>? {
        val i = grid.indexOf(code)
        return if (i < 0) null else (i / COLUMNS + 1) to (i % COLUMNS * 2 + 1)
    }

    val upperCase: Boolean get() = grid.firstOrNull() == NameCodec.UPPER_A

    companion object {
        const val ROWS = 5
        const val COLUMNS = 9
    }
}

object NamingReader {
    const val NAME_BUFFER = 0xCF4F
    const val NAME_BUFFER_SIZE = 11

    private const val CORNER_TL = 0x79
    private const val CORNER_TR = 0x7B
    private const val CORNER_BL = 0x7D
    private const val CORNER_BR = 0x7E
    private const val UNDERSCORE = 0x76
    private const val UNDERSCORE_RAISED = 0x77
    private const val CURSOR = 0xED

    fun read(menuVars: ByteArray, tileMap: ByteArray, nameBuffer: ByteArray): NamingScreen? {
        if (menuVars.size < MenuReader.MENU_VARS_SIZE || tileMap.size < MenuReader.SCREEN_SIZE) return null
        val topY = menuVars[0].toInt() and 0xFF
        val topX = menuVars[1].toInt() and 0xFF
        val maxItem = menuVars[4].toInt() and 0xFF
        val watched = menuVars[5].toInt() and 0xFF
        if (topY != 3 || maxItem != 7 || watched != 0xFF || topX !in 1..17 || topX % 2 == 0) return null
        fun tile(x: Int, y: Int) = tileMap[y * MenuReader.SCREEN_WIDTH + x].toInt() and 0xFF
        // La riga del cursore si legge dallo schermo: durante l'animazione dell'icona il gioco azzera
        // temporaneamente l'elemento corrente (CC26) e lo ripristina dopo.
        val current = (1..6).firstOrNull { tile(topX, 3 + 2 * it) == CURSOR } ?: return null
        if (tile(0, 4) != CORNER_TL || tile(19, 4) != CORNER_TR || tile(0, 14) != CORNER_BL || tile(19, 14) != CORNER_BR) return null
        var maxLength = 0
        while (10 + maxLength < MenuReader.SCREEN_WIDTH && tile(10 + maxLength, 3).let { it == UNDERSCORE || it == UNDERSCORE_RAISED }) maxLength++
        if (maxLength != 7 && maxLength != 10) return null
        val grid = ArrayList<Int>(NamingScreen.ROWS * NamingScreen.COLUMNS)
        for (r in 0 until NamingScreen.ROWS) for (c in 0 until NamingScreen.COLUMNS) grid += tile(2 + 2 * c, 5 + 2 * r)
        val name = ArrayList<Int>()
        for (b in nameBuffer) {
            val v = b.toInt() and 0xFF
            if (v == TextCodec.TERMINATOR || name.size >= maxLength) break
            name += v
        }
        // Titolo: riga 1 (es. "NOME TUO?" o il nome della creatura) più l'inizio della riga 3 ("SOPRANNOME?").
        val row1 = ByteArray(MenuReader.SCREEN_WIDTH) { tileMap[MenuReader.SCREEN_WIDTH + it] }
        val row3 = ByteArray(10) { tileMap[3 * MenuReader.SCREEN_WIDTH + it] }
        val title = (TextCodec.decode(row1).trim() + " " + TextCodec.decode(row3).trim()).trim()
        return NamingScreen(title, maxLength, name, current, topX, grid)
    }
}

/**
 * Conversione tra il testo scritto con la tastiera del telefono e i caratteri disponibili nella griglia del gioco.
 * Le lettere accentate diventano senza accento (il gioco non le ha nella griglia); gli altri caratteri vengono scartati.
 */
object NameCodec {
    const val UPPER_A = 0x80
    const val LOWER_A = 0xA0

    private val symbols = mapOf(
        ' ' to 0x7F, '×' to 0xF1, '(' to 0x9A, ')' to 0x9B, ':' to 0x9C, ';' to 0x9D, '[' to 0x9E, ']' to 0x9F,
        '-' to 0xE3, '?' to 0xE6, '!' to 0xE7, '♂' to 0xEF, '♀' to 0xF5, '/' to 0xF3, '.' to 0xF2, ',' to 0xF4,
    )

    /** Caratteri accettati oltre alle lettere, da mostrare come aiuto. */
    val SYMBOLS: String = symbols.keys.filter { it != ' ' }.joinToString(" ")

    fun code(c: Char): Int? = when (c) {
        in 'A'..'Z' -> UPPER_A + (c - 'A')
        in 'a'..'z' -> LOWER_A + (c - 'a')
        else -> symbols[c]
    }

    /** Tiene solo i caratteri scrivibili nel gioco (accenti tolti), al massimo [maxLength]. */
    fun sanitize(text: String, maxLength: Int): String {
        val plain = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return plain.filter { code(it) != null }.take(maxLength)
    }

    fun encode(text: String): List<Int>? = text.map { code(it) ?: return null }

    fun decode(codes: List<Int>): String = codes.joinToString("") { c ->
        when (c) {
            in UPPER_A until UPPER_A + 26 -> ('A' + (c - UPPER_A)).toString()
            in LOWER_A until LOWER_A + 26 -> ('a' + (c - LOWER_A)).toString()
            else -> symbols.entries.firstOrNull { it.value == c }?.key?.toString() ?: "?"
        }
    }

    /** Stessa lettera nell'altra griglia (maiuscole ↔ minuscole), se è una lettera. */
    fun isLetter(code: Int): Boolean = code in UPPER_A until UPPER_A + 26 || code in LOWER_A until LOWER_A + 26
}

/**
 * Scrive un nome nella schermata dei nomi del gioco muovendo il cursore e premendo i tasti, come farebbe il giocatore.
 * Circuito chiuso: dopo ogni pressione aspetta che lo schermo cambi (cursore, nome o griglia) prima di decidere la
 * successiva; se nulla cambia entro il tempo previsto ripete la valutazione. Non scrive mai nella memoria del gioco.
 * Cancella con B le lettere che non corrispondono, cambia maiuscole/minuscole con SELECT, conferma con START.
 */
class NameExecutor(private val timeoutFrames: Int = 60 * 40) {
    enum class Result { DONE, FAILED }

    private var target: List<Int>? = null
    private var framesLeft = 0
    private var heldKey = 0
    private var holdFrames = 0
    private var waitFrames = 0
    private var minWait = 0
    private var before: NamingScreen? = null
    private var submitted = false
    private var missing = 0

    /** Esito dell'ultimo nome (null mentre lavora o se non è mai partito). */
    var result: Result? = null
        private set

    val busy: Boolean get() = target != null

    fun start(name: List<Int>) {
        target = name
        framesLeft = timeoutFrames
        heldKey = 0; holdFrames = 0; waitFrames = 0; minWait = 0
        before = null; submitted = false; missing = 0; result = null
    }

    fun cancel() = finish(Result.FAILED)

    private fun finish(r: Result) { target = null; heldKey = 0; result = r }

    /** Chiamata una volta per fotogramma con la schermata letta (null se non c'è): restituisce i tasti da premere. */
    fun step(screen: NamingScreen?): Int {
        val goal = target ?: return 0
        if (--framesLeft <= 0) { finish(Result.FAILED); return 0 }
        if (holdFrames > 0) { holdFrames--; return heldKey }
        if (submitted) {
            if (screen == null) { finish(Result.DONE); return 0 }
            // Il gioco non ha ancora chiuso la schermata: aspetta, poi riprova a confermare.
            if (waitFrames > 0) { waitFrames--; return 0 }
            submitted = false
        }
        if (screen == null) {
            // Schermata non riconosciuta per un attimo (ridisegno): tollerato per mezzo secondo.
            if (++missing > MAX_MISSING) finish(Result.FAILED)
            return 0
        }
        missing = 0
        if (minWait > 0) { minWait--; return 0 }
        if (waitFrames > 0) {
            val changed = before?.let { it.cursorRow != screen.cursorRow || it.cursorX != screen.cursorX || it.name != screen.name || it.grid != screen.grid } ?: true
            if (!changed) { waitFrames--; return 0 }
            waitFrames = 0
        }
        val key = decide(screen, goal) ?: run { finish(Result.FAILED); return 0 }
        if (key == START) submitted = true
        before = screen
        heldKey = key
        holdFrames = HOLD - 1
        minWait = RELEASE
        waitFrames = if (key == START) SUBMIT_WAIT else CHANGE_WAIT
        return key
    }

    companion object {
        const val START = 128
        const val SELECT = 64
        private const val HOLD = 2
        private const val RELEASE = 2
        private const val CHANGE_WAIT = 40
        private const val SUBMIT_WAIT = 120
        private const val MAX_MISSING = 30

        /** Prossimo tasto per avvicinarsi al nome voluto; null se una lettera non si può scrivere. */
        fun decide(screen: NamingScreen, goal: List<Int>): Int? {
            val name = screen.name
            val prefix = name.size <= goal.size && name == goal.subList(0, name.size)
            if (!prefix) return Keys.B
            if (name.size == goal.size) return START
            val next = goal[name.size]
            val pos = screen.find(next) ?: return if (NameCodec.isLetter(next)) SELECT else null
            val (row, x) = pos
            if (screen.cursorRow == 6) return if (row <= 3) Keys.DOWN else Keys.UP
            if (screen.cursorX != x) {
                val from = (screen.cursorX - 1) / 2
                val to = (x - 1) / 2
                return if ((to - from + NamingScreen.COLUMNS) % NamingScreen.COLUMNS <= NamingScreen.COLUMNS / 2) Keys.RIGHT else Keys.LEFT
            }
            if (screen.cursorRow != row) return if (row > screen.cursorRow) Keys.DOWN else Keys.UP
            return Keys.A
        }
    }
}
