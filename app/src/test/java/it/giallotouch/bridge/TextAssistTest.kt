package it.giallotouch.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAssistTest {

    /** Schermo sintetico con riquadro di testo in basso; opzionalmente freccia ▼ e cursore ▶. */
    private fun screen(arrow: Boolean, cursor: Boolean = false) = ByteArray(TextAssist.SCREEN_SIZE).also {
        it[12 * 20] = 0x79.toByte(); it[17 * 20] = 0x7D.toByte()
        if (arrow) it[16 * 20 + 18] = 0xEE.toByte()
        if (cursor) it[8 * 20 + 15] = 0xED.toByte()
    }

    private fun framesUntilA(t: TextAssist, s: ByteArray, max: Int = 300): Int {
        repeat(max) { i -> if (t.step(s) and Keys.A != 0) return i + 1 }
        return -1
    }

    @Test
    fun automatico_premeADopoLaPausa() {
        for ((speed, expected) in listOf(1 to 121, 2 to 61, 3 to 25)) {
            val t = TextAssist().apply { autoSpeed = speed }
            assertEquals(expected, framesUntilA(t, screen(arrow = true)))
        }
    }

    @Test
    fun spentoOSenzaFreccia_nonPreme() {
        assertEquals(-1, framesUntilA(TextAssist(), screen(arrow = true)))
        assertEquals(-1, framesUntilA(TextAssist().apply { autoSpeed = 3 }, screen(arrow = false)))
    }

    @Test
    fun conMenuODomanda_nonPremeMai() {
        val t = TextAssist().apply { autoSpeed = 3; holding = true }
        repeat(200) { assertEquals(0, t.step(screen(arrow = true, cursor = true))) }
    }

    @Test
    fun ditoPremuto_impulsiDiB() {
        val t = TextAssist().apply { holding = true }
        val keys = (0 until 12).map { t.step(screen(arrow = false)) }
        assertTrue(keys.count { it == Keys.B } in 5..7)
        assertTrue(keys.none { it and Keys.A != 0 })
    }

    @Test
    fun senzaRiquadroDiTesto_nienteTasti() {
        val t = TextAssist().apply { autoSpeed = 3; holding = true }
        repeat(100) { assertEquals(0, t.step(ByteArray(TextAssist.SCREEN_SIZE))) }
    }
}
