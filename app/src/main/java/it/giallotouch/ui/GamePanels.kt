package it.giallotouch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import it.giallotouch.bridge.Experience
import it.giallotouch.bridge.PartyMember
import it.giallotouch.bridge.RomData
import it.giallotouch.bridge.TileCanvas

/** Colori delle barre (palette del gioco PAL_GREENBAR/YELLOWBAR/REDBAR) e della barra dell'esperienza. */
private const val BAR_GREEN = 0xFF00A800.toInt()
private const val BAR_YELLOW = 0xFFE0B848.toInt()
private const val BAR_RED = 0xFFD04830.toInt()
private const val BAR_EXP = 0xFF4080E0.toInt()

/** Griglia di caselle da 8 pixel del gioco alla scala dello schermo (larghezza = 20 caselle). */
class TileGrid(val unit: Float, val rows: Int) {
    val tilePx = 8 * unit
    fun at(col: Float, row: Float) = IntOffset((col * tilePx).toInt(), (row * tilePx).toInt())
}

/**
 * Pulsanti durante il gioco, scelti dal committente nel mockup (D-040), tutti disegnati con i tile del gioco:
 * MENU in basso al centro; aperto: squadra in alto (piccola, con vita ed esperienza) e riquadro con
 * POKéDEX, BORSA, OPZIONI, SALVA. Nessun cursore: si tocca direttamente la voce.
 */
@Composable
fun GamePanel(
    sheet: IntArray,
    open: Boolean,
    party: List<PartyMember>,
    romData: RomData,
    onToggle: () -> Unit,
    onPokedex: () -> Unit,
    onBag: () -> Unit,
    onOptions: () -> Unit,
    onSave: () -> Unit,
    onMember: (Int) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val g = TileGrid(unit, (with(density) { maxHeight.toPx() } / (8 * unit)).toInt())
        val bottom = g.rows - 1
        if (open) {
            // Squadra in alto: riquadro normale, contenuto a metà grandezza ("in piccolo").
            TileImage(sheet, 20, 8, unit, key = "squadra-box", modifier = Modifier.offset { g.at(0f, 4f) }) { box(0, 0, 19, 7) }
            TileImage(
                sheet, 36, 12, unit / 2, key = party,
                modifier = Modifier
                    .offset { g.at(1f, 5f) }
                    .pointerInput(party) {
                        detectTapGestures { pos ->
                            val half = g.tilePx / 2
                            val i = (pos.y / half / 4).toInt() * 2 + (pos.x / half / 18).toInt()
                            if (i in party.indices) onMember(i)
                        }
                    }
            ) { drawTeam(this, party, romData) }
            // Riquadro dei pulsanti sopra MENU.
            val top = bottom - 12
            TileImage(
                sheet, 20, 9, unit, key = "pulsanti",
                modifier = Modifier
                    .offset { g.at(0f, top.toFloat()) }
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            val right = pos.x >= 10 * g.tilePx
                            val lower = pos.y >= 5 * g.tilePx
                            when {
                                !right && !lower -> onPokedex()
                                !right && lower -> onBag()
                                right && !lower -> onOptions()
                                else -> onSave()
                            }
                        }
                    }
            ) {
                box(0, 0, 19, 8)
                text("POKéDEX", 2, 3); text("BORSA", 2, 6)
                text("OPZIONI", 11, 3); text("SALVA", 11, 6)
            }
        }
        TileImage(
            sheet, 7, 4, unit, key = "menu",
            modifier = Modifier
                .offset { g.at(7f, (bottom - 3).toFloat()) }
                .pointerInput(Unit) { detectTapGestures { onToggle() } }
        ) {
            box(0, 0, 6, 3)
            textAt("MENU", 12, 12)
        }
    }
}

/** Squadra in piccolo: 2 colonne × 3 righe; nome e livello, barra della vita con i PS, barra dell'esperienza. */
private fun drawTeam(c: TileCanvas, party: List<PartyMember>, romData: RomData) {
    party.take(6).forEachIndexed { i, m ->
        val col = (i % 2) * 18
        val row = (i / 2) * 4
        c.text(m.name.take(10), col, row)
        c.text("L" + m.level, col + 12, row)
        // vita
        val hpFrac = if (m.maxHp > 0) m.hp.toFloat() / m.maxHp else 0f
        val bx = col * 8; val by = (row + 1) * 8 + 1
        c.fill(bx, by, 80, 6, c.ink)
        c.fill(bx + 1, by + 1, 78, 4, c.paper)
        c.fill(bx + 1, by + 1, (78 * hpFrac).toInt(), 4, when {
            hpFrac > 0.5f -> BAR_GREEN
            hpFrac > 0.2f -> BAR_YELLOW
            else -> BAR_RED
        })
        c.text("${m.hp}/${m.maxHp}", col + 11, row + 1)
        // esperienza
        val exp = Experience.progress(romData.growthRate(m.species), m.level, m.exp)
        val ey = (row + 2) * 8 + 2
        c.fill(bx, ey, 80, 4, c.ink)
        c.fill(bx + 1, ey + 1, 78, 2, c.paper)
        c.fill(bx + 1, ey + 1, (78 * exp).toInt(), 2, BAR_EXP)
    }
}

/**
 * OPZIONI (D-040), nello stile del gioco: testo automatico (tocco = cambia velocità), menu originale del gioco
 * (per la scheda dell'allenatore, il salvataggio e le opzioni del gioco), INDIETRO.
 */
@Composable
fun GameOptions(sheet: IntArray, autoText: Int, onAutoText: () -> Unit, onGameMenu: () -> Unit, onBack: () -> Unit) {
    val speeds = listOf("NO", "LENTO", "MEDIO", "VELOCE")
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(sheet[(0x7F shr 4) * 8 * 128 + (0x7F and 15) * 8]))
        .pointerInput(Unit) { detectTapGestures { } }) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val g = TileGrid(unit, (with(density) { maxHeight.toPx() } / (8 * unit)).toInt())
        val top = 6
        TileImage(sheet, 20, 3, unit, key = "opz-titolo", modifier = Modifier.offset { g.at(0f, top.toFloat()) }) {
            box(0, 0, 19, 2); text("OPZIONI", 6, 1)
        }
        TileImage(
            sheet, 20, 8, unit, key = autoText,
            modifier = Modifier
                .offset { g.at(0f, (top + 4).toFloat()) }
                .pointerInput(Unit) { detectTapGestures { pos -> if (pos.y < 4 * g.tilePx) onAutoText() else onGameMenu() } }
        ) {
            box(0, 0, 19, 7)
            text("TESTO AUTO", 2, 2); text(speeds[autoText], 18 - speeds[autoText].length + 1, 2)
            text("MENU DEL GIOCO", 2, 5)
        }
        TileImage(
            sheet, 20, 4, unit, key = "opz-indietro",
            modifier = Modifier
                .offset { g.at(0f, (top + 13).toFloat()) }
                .pointerInput(Unit) { detectTapGestures { onBack() } }
        ) {
            box(0, 0, 19, 3); text("INDIETRO", 2, 2)
        }
    }
}
