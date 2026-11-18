package it.giallotouch.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleLayoutTest {
    private fun box(top: Int, left: Int = 0): ByteArray {
        val t = ByteArray(360) { 0x7F }
        fun put(x: Int, y: Int, v: Int) { t[y * 20 + x] = v.toByte() }
        put(left, top, 0x79); put(19, top, 0x7B); put(left, 17, 0x7D); put(19, 17, 0x7E)
        return t
    }

    @Test
    fun riquadroInBasso() {
        assertTrue(BattleLayout.textBoxAtBottom(box(12)))
        // menu delle mosse: l'angolo in alto a sinistra cambia ma il riquadro resta nelle righe 12–17
        assertTrue(BattleLayout.textBoxAtBottom(box(12).also { it[12 * 20] = 0x7D }))
        assertFalse(BattleLayout.textBoxAtBottom(ByteArray(360) { 0x7F }))
        assertFalse(BattleLayout.textBoxAtBottom(box(10)))
    }

    @Test
    fun coloreDiSfondo() {
        val white = 0xFFF8F8F8.toInt()
        val frame = IntArray(160 * 144) { white }
        for (i in 0 until 160 * 30) frame[i] = 0xFF101010.toInt()
        assertEquals(white, BattleLayout.backgroundColor(frame))
    }
}
