package it.giallotouch.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GameMenuTest {

    private fun vars(y: Int, x: Int, cur: Int) = byteArrayOf(y.toByte(), x.toByte(), cur.toByte(), 0x7F, 1, 0x11, 0)
    private fun screenWithCursor(x: Int, y: Int) = ByteArray(MenuReader.SCREEN_SIZE).also { it[y * 20 + x] = 0xED.toByte() }

    @Test
    fun riconosceMenuPrincipaleEMosse() {
        assertEquals(MenuState(MenuKind.BATTLE_MAIN, 0, 1), MenuReader.read(vars(0x0E, 0x07, 1), screenWithCursor(7, 16)))
        assertEquals(MenuState(MenuKind.BATTLE_MAIN, 1, 0), MenuReader.read(vars(0x0E, 0x0E, 0), screenWithCursor(14, 14)))
        assertEquals(MenuState(MenuKind.BATTLE_MOVES, item = 1), MenuReader.read(vars(0x0C, 0x05, 2), screenWithCursor(5, 14)))
    }

    @Test
    fun valoriRimastiSenzaCursore_nessunMenu() {
        // Variabili del menu delle mosse rimaste in memoria, ma sullo schermo c'è solo testo.
        assertEquals(MenuState.NONE, MenuReader.read(vars(0x0C, 0x05, 1), ByteArray(MenuReader.SCREEN_SIZE)))
    }

    /** Menu di lotta simulato che reagisce ai tasti come quello del gioco (solo al momento della pressione). */
    private class FakeMenu(val moves: Int, var lastMove: Int = 0) {
        var state = MenuState(MenuKind.BATTLE_MAIN, 0, 0)
        var chosen: String? = null
        private var previous = 0
        fun apply(keys: Int) {
            val pressed = keys and previous.inv()
            previous = keys
            if (pressed == 0) return
            val s = state
            state = when (s.kind) {
                MenuKind.BATTLE_MAIN -> when (pressed) {
                    Keys.RIGHT -> s.copy(column = 1)
                    Keys.LEFT -> s.copy(column = 0)
                    Keys.DOWN -> s.copy(item = 1)
                    Keys.UP -> s.copy(item = 0)
                    Keys.A -> if (s.column == 0 && s.item == 0) MenuState(MenuKind.BATTLE_MOVES, item = lastMove)
                              else { chosen = listOf("lotta", "strumenti", "squadra", "fuga")[s.column * 2 + s.item]; MenuState.NONE }
                    else -> s
                }
                MenuKind.BATTLE_MOVES -> when (pressed) {
                    Keys.DOWN -> s.copy(item = minOf(s.item + 1, moves - 1))
                    Keys.UP -> s.copy(item = maxOf(s.item - 1, 0))
                    Keys.A -> { chosen = "mossa ${s.item}"; MenuState.NONE }
                    Keys.B -> MenuState(MenuKind.BATTLE_MAIN, 0, 0)
                    else -> s
                }
                MenuKind.NONE, MenuKind.YES_NO, MenuKind.MAIN_MENU, MenuKind.NAME_LIST -> s
            }
        }
    }

    private fun run(cmd: GameCommand, menu: FakeMenu): String? {
        val ex = CommandExecutor()
        ex.start(cmd)
        repeat(500) { if (ex.busy) menu.apply(ex.step(menu.state, inBattle = true)) }
        assertFalse(ex.busy)
        return menu.chosen
    }

    @Test
    fun scegliMosse() {
        assertEquals("mossa 1", run(GameCommand.Fight(1), FakeMenu(moves = 2)))
        assertEquals("mossa 0", run(GameCommand.Fight(0), FakeMenu(moves = 4, lastMove = 3)))
        assertEquals("mossa 3", run(GameCommand.Fight(3), FakeMenu(moves = 4)))
    }

    @Test
    fun fugaSquadraStrumenti() {
        assertEquals("fuga", run(GameCommand.Run, FakeMenu(2)))
        assertEquals("squadra", run(GameCommand.Party, FakeMenu(2)))
        assertEquals("strumenti", run(GameCommand.Item, FakeMenu(2)))
    }

    private fun yesNoScreen(cursorRow: Int) = ByteArray(MenuReader.SCREEN_SIZE).also {
        it[8 * 20 + 16] = 0x92.toByte()   // "S" accanto alla prima voce
        it[10 * 20 + 16] = 0x8D.toByte()  // "N" accanto alla seconda voce
        it[cursorRow * 20 + 15] = 0xED.toByte()
    }
    private fun yesNoVars(cur: Int) = byteArrayOf(0x08, 0x0F, cur.toByte(), 0x7F, 1, 0x03, cur.toByte())

    @Test
    fun riconosceDomandaSiNo() {
        assertEquals(MenuState(MenuKind.YES_NO, item = 0), MenuReader.read(yesNoVars(0), yesNoScreen(8)))
        assertEquals(MenuState(MenuKind.YES_NO, item = 1), MenuReader.read(yesNoVars(1), yesNoScreen(10)))
        // Stesse variabili ma senza le lettere S/N: non è una domanda Sì/No.
        val other = yesNoScreen(8).also { it[8 * 20 + 16] = 0x80.toByte() }
        assertEquals(MenuState.NONE, MenuReader.read(yesNoVars(0), other))
    }

    @Test
    fun rispostaSiNo() {
        for ((yes, start) in listOf(true to 1, false to 0, false to 1, true to 0)) {
            val ex = CommandExecutor()
            var item = start
            var chosen: Boolean? = null
            var previous = 0
            ex.start(GameCommand.Answer(yes))
            repeat(200) {
                if (!ex.busy) return@repeat
                val menu = if (chosen == null) MenuState(MenuKind.YES_NO, item = item) else MenuState.NONE
                val keys = ex.step(menu, inBattle = false)
                val pressed = keys and previous.inv(); previous = keys
                when (pressed) { Keys.DOWN -> item = 1; Keys.UP -> item = 0; Keys.A -> chosen = item == 0 }
            }
            assertEquals(yes, chosen)
        }
    }

    @Test
    fun fuoriDallaLotta_siFerma() {
        val ex = CommandExecutor()
        ex.start(GameCommand.Run)
        assertEquals(0, ex.step(MenuState(MenuKind.BATTLE_MAIN), inBattle = false))
        assertFalse(ex.busy)
    }

    @Test
    fun senzaMenuPerTroppoTempo_siFerma() {
        val ex = CommandExecutor(timeoutFrames = 30)
        ex.start(GameCommand.Fight(0))
        repeat(40) { ex.step(MenuState.NONE, inBattle = true) }
        assertFalse(ex.busy)
    }
}

class MainMenuTest {
    private fun screen(maxItem: Int, cursor: Int): Pair<ByteArray, ByteArray> {
        val t = ByteArray(360) { 0x7F }
        fun put(x: Int, y: Int, v: Int) { t[y * 20 + x] = v.toByte() }
        put(0, 0, 0x79); put(14, 0, 0x7B); put(0, 2 * maxItem + 3, 0x7D); put(14, 2 * maxItem + 3, 0x7E)
        put(1, 2 + 2 * cursor, 0xED)
        return byteArrayOf(2, 1, cursor.toByte(), 0x7F, maxItem.toByte(), 0x0B, 0) to t
    }

    @org.junit.Test
    fun menuIniziale() {
        val (v, t) = screen(1, 0)
        org.junit.Assert.assertEquals(MenuState(MenuKind.MAIN_MENU, item = 0, items = 2), MenuReader.read(v, t))
        val (v3, t3) = screen(2, 1)
        org.junit.Assert.assertEquals(MenuState(MenuKind.MAIN_MENU, item = 1, items = 3), MenuReader.read(v3, t3))
        v3[5] = 0x03 // altri tasti osservati: non è il menu iniziale
        org.junit.Assert.assertEquals(MenuKind.NONE, MenuReader.read(v3, t3).kind)
    }
}

class StartMenuLayoutTest {
    @org.junit.Test
    fun vociEPosizioni() {
        val e = StartMenuLayout.entries(3)
        org.junit.Assert.assertEquals(listOf("CONTINUA", "CARICA PARTITA", "OPZIONI", "NUOVO GIOCO"), e.map { it.label })
        org.junit.Assert.assertEquals(14, StartMenuLayout.height(3))   // riquadro 18×14 come nel mockup
        org.junit.Assert.assertEquals(StartMenuLayout.Entry.OPZIONI, StartMenuLayout.entryAt(8, 3, 3))
        org.junit.Assert.assertEquals(null, StartMenuLayout.entryAt(13, 3, 3))
        org.junit.Assert.assertEquals(1, StartMenuLayout.gameIndex(StartMenuLayout.Entry.NUOVO, 3))
        org.junit.Assert.assertEquals(0, StartMenuLayout.gameIndex(StartMenuLayout.Entry.NUOVO, 2))
        // il comando scende fino alla voce e preme A
        val d = CommandExecutor.decide(GameCommand.MainMenu(2), MenuState(MenuKind.MAIN_MENU, item = 0, items = 3))
        org.junit.Assert.assertEquals(Keys.DOWN, d?.key)
        org.junit.Assert.assertEquals(Keys.A, CommandExecutor.decide(GameCommand.MainMenu(2), MenuState(MenuKind.MAIN_MENU, item = 2, items = 3))?.key)
    }
}

class ItemSwapTest {
    private fun list(cursor: Int, name: String) = ItemMenus.Screen(ItemMenus.Kind.ITEM_LIST, cursor = cursor, name = name)

    @org.junit.Test
    fun scambioConSelect() {
        val ex = ItemActionExecutor().apply { start(ItemAction.Swap(1, "POZIONE", 3)) }
        var obs = 0
        var prev = 0
        // nuovo tasto premuto dopo almeno un fotogramma senza tasti (lo schermo è cambiato: nuova osservazione)
        fun step(s: ItemMenus.Screen): Int {
            obs++
            repeat(200) { val k = ex.step(s, false, obs); if (k != 0 && prev == 0) { prev = k; return k }; prev = k }
            return 0
        }
        org.junit.Assert.assertEquals(Keys.DOWN, step(list(0, "ANTIDOTO")))
        org.junit.Assert.assertEquals(64, step(list(1, "POZIONE")))       // SELECT sul primo
        org.junit.Assert.assertEquals(Keys.DOWN, step(list(1, "POZIONE")))
        org.junit.Assert.assertEquals(64, step(list(3, "ESCA")))          // SELECT sul secondo
        org.junit.Assert.assertEquals(Keys.B, step(list(3, "POZIONE")))   // poi chiude
    }
}
