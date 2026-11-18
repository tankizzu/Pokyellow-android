package it.giallotouch.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BattleMenuTest {

    private fun vars(y: Int, x: Int, cur: Int) = byteArrayOf(y.toByte(), x.toByte(), cur.toByte(), 0x7F, 1, 0x11, 0)
    private fun screenWithCursor(x: Int, y: Int) = ByteArray(BattleMenuReader.SCREEN_SIZE).also { it[y * 20 + x] = 0xED.toByte() }

    @Test
    fun riconosceMenuPrincipaleEMosse() {
        assertEquals(MenuState(MenuKind.BATTLE_MAIN, 0, 1), BattleMenuReader.read(vars(0x0E, 0x07, 1), screenWithCursor(7, 16)))
        assertEquals(MenuState(MenuKind.BATTLE_MAIN, 1, 0), BattleMenuReader.read(vars(0x0E, 0x0E, 0), screenWithCursor(14, 14)))
        assertEquals(MenuState(MenuKind.BATTLE_MOVES, item = 1), BattleMenuReader.read(vars(0x0C, 0x05, 2), screenWithCursor(5, 14)))
    }

    @Test
    fun valoriRimastiSenzaCursore_nessunMenu() {
        // Variabili del menu delle mosse rimaste in memoria, ma sullo schermo c'è solo testo.
        assertEquals(MenuState.NONE, BattleMenuReader.read(vars(0x0C, 0x05, 1), ByteArray(BattleMenuReader.SCREEN_SIZE)))
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
                MenuKind.NONE -> s
            }
        }
    }

    private fun run(cmd: BattleCommand, menu: FakeMenu): String? {
        val ex = BattleCommandExecutor()
        ex.start(cmd)
        repeat(500) { if (ex.busy) menu.apply(ex.step(menu.state, inBattle = true)) }
        assertFalse(ex.busy)
        return menu.chosen
    }

    @Test
    fun scegliMosse() {
        assertEquals("mossa 1", run(BattleCommand.Fight(1), FakeMenu(moves = 2)))
        assertEquals("mossa 0", run(BattleCommand.Fight(0), FakeMenu(moves = 4, lastMove = 3)))
        assertEquals("mossa 3", run(BattleCommand.Fight(3), FakeMenu(moves = 4)))
    }

    @Test
    fun fugaSquadraStrumenti() {
        assertEquals("fuga", run(BattleCommand.Run, FakeMenu(2)))
        assertEquals("squadra", run(BattleCommand.Party, FakeMenu(2)))
        assertEquals("strumenti", run(BattleCommand.Item, FakeMenu(2)))
    }

    @Test
    fun fuoriDallaLotta_siFerma() {
        val ex = BattleCommandExecutor()
        ex.start(BattleCommand.Run)
        assertEquals(0, ex.step(MenuState(MenuKind.BATTLE_MAIN), inBattle = false))
        assertFalse(ex.busy)
    }

    @Test
    fun senzaMenuPerTroppoTempo_siFerma() {
        val ex = BattleCommandExecutor(timeoutFrames = 30)
        ex.start(BattleCommand.Fight(0))
        repeat(40) { ex.step(MenuState.NONE, inBattle = true) }
        assertFalse(ex.busy)
    }
}
