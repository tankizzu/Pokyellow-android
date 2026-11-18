package it.giallotouch.ui

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import it.giallotouch.storage.SaveSlot
import it.giallotouch.storage.SlotEntry
import java.util.Calendar
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
 * START in basso al centro (D-043); aperto: squadra in alto (piccola, con vita ed esperienza) e riquadro con
 * POKéDEX, BORSA, OPZIONI, SALVA. Si tocca la voce, oppure si sposta il cursore ▶ con la levetta e si conferma
 * con un tocco fuori dal riquadro (D-041). MENU o il gesto indietro chiudono.
 */
@Composable
fun GamePanel(
    sheet: IntArray,
    open: Boolean,
    /** Cursore ▶ nel riquadro: 0 POKéDEX, 1 OPZIONI, 2 BORSA, 3 SALVA (D-041). */
    cursor: Int,
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
                sheet, 20, 9, unit, key = cursor,
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
                tile(TileCanvas.CURSOR, if (cursor % 2 == 0) 1 else 10, if (cursor < 2) 3 else 6)
            }
        }
        TileImage(
            sheet, 7, 4, unit, key = "menu",
            modifier = Modifier
                .offset { g.at(7f, (bottom - 3).toFloat()) }
                .pointerInput(Unit) { detectTapGestures { onToggle() } }
        ) {
            box(0, 0, 6, 3)
            textAt("START", 8, 12)
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

/**
 * "Carica partita" nello stile del gioco (D-040, D-041), con le posizioni del mockup del committente:
 * riquadro del titolo, un riquadro per ogni salvataggio dell'app (nome, data, giocatore, medaglie, tempo), INDIETRO.
 * Si tocca il riquadro da caricare; oppure si sposta il cursore ▶ trascinando su/giù e si conferma con un tocco.
 */
@Composable
fun GameLoadScreen(sheet: IntArray, entries: List<SlotEntry>, onLoad: (SlotEntry) -> Unit, onClose: () -> Unit) {
    var cursor by remember { mutableStateOf(0) }
    val count = entries.size + 1   // salvataggi + INDIETRO
    val backRow = if (entries.isEmpty()) 12 else 7 + 11 * entries.size
    BackHandler { onClose() }
    val paper = Color(sheet[(0x7F shr 4) * 8 * 128 + (0x7F and 15) * 8])
    BoxWithConstraints(Modifier.fillMaxSize().background(paper)) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val tilePx = 8 * unit
        fun choose(i: Int) { if (i < entries.size) onLoad(entries[i]) else onClose() }
        TileImage(
            sheet, 20, backRow + 4, unit, key = Pair(entries, cursor),
            modifier = Modifier.pointerInput(entries) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var dragged = 0f
                    var steps = 0
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        dragged = ch.position.y - down.position.y
                        val s = (dragged / (3 * tilePx)).toInt()
                        if (s != steps) { cursor = (cursor + (s - steps)).mod(count); steps = s }
                    }
                    if (kotlin.math.abs(dragged) < tilePx) {
                        val row = (down.position.y / tilePx).toInt()
                        val hit = when {
                            row in backRow until backRow + 4 -> entries.size
                            else -> entries.indices.firstOrNull { row in (7 + 11 * it) until (17 + 11 * it) }
                        }
                        if (hit != null) { cursor = hit; choose(hit) } else choose(cursor)
                    }
                }
            }
        ) {
            box(0, 3, 19, 5); text("CARICA PARTITA", 3, 4)
            if (entries.isEmpty()) {
                box(0, 7, 19, 10); text("NESSUN SALVATAGGIO", 1, 9)
            }
            entries.forEachIndexed { k, e ->
                val y0 = 7 + 11 * k
                box(0, y0, 19, y0 + 9)
                text(if (e.slot == SaveSlot.MANUAL) "SALVATAGGIO" else "AUTOMATICO", 2, y0 + 2)
                val cal = Calendar.getInstance().apply { timeInMillis = e.info.savedAt }
                text("%02d/%02d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1), 14, y0 + 2)
                val name = e.info.playerName.ifEmpty { "-" }.take(10)
                text("GIOCA", 2, y0 + 4); text(name, 19 - name.length, y0 + 4)
                text("MEDAGLIE", 2, y0 + 6); text("${e.info.badges}", 18, y0 + 6)
                val t = if (e.info.playMinutes >= 0) "%d:%02d".format(e.info.playMinutes / 60, e.info.playMinutes % 60) else "-"
                text("TEMPO", 2, y0 + 8); text(t, 19 - t.length, y0 + 8)
            }
            box(0, backRow, 19, backRow + 3); text("INDIETRO", 2, backRow + 2)
            val cursorRow = if (cursor < entries.size) 9 + 11 * cursor else backRow + 2
            tile(TileCanvas.CURSOR, 1, cursorRow)
        }
    }
}

/**
 * Borsa nello stile del gioco (D-038, D-040): riquadro BORSA, lista degli strumenti con quantità (gli strumenti chiave
 * senza, come nel gioco), INDIETRO. Toccando uno strumento compare in basso il riquadro USA / GETTA / ANNULLA
 * (Getta ne butta uno). Cursore ▶ sullo strumento scelto. Le azioni passano dal menu originale del gioco.
 */
@Composable
fun GameBagScreen(
    sheet: IntArray,
    bag: List<it.giallotouch.bridge.BagItem>,
    romData: RomData,
    onUse: (Int, it.giallotouch.bridge.BagItem) -> Unit,
    onToss: (Int, it.giallotouch.bridge.BagItem) -> Unit,
    onSwap: (Int, Int) -> Unit,
    onClose: () -> Unit,
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    var swapMode by remember { mutableStateOf(false) }          // SELECT premuto: si sceglie dove spostare
    var dragFrom by remember { mutableStateOf<Int?>(null) }     // tenuto premuto e trascinato
    var dragTo by remember { mutableStateOf<Int?>(null) }
    BackHandler { if (selected != null) selected = null else onClose() }
    val paper = Color(sheet[(0x7F shr 4) * 8 * 128 + (0x7F and 15) * 8])
    val step = if (bag.size <= 12) 2 else 1
    val listTop = 7
    val listRows = maxOf(1, bag.size) * step + 3
    val backRow = listTop + listRows + 1
    BoxWithConstraints(Modifier.fillMaxSize().background(paper)) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val tilePx = 8 * unit
        val rows = (with(density) { maxHeight.toPx() } / tilePx).toInt()
        val sel = selected
        TileImage(
            sheet, 20, backRow + 4, unit, key = listOf(bag, sel, step, swapMode, dragFrom, dragTo),
            modifier = Modifier
                .pointerInput(bag, step, swapMode, sel) {
                    detectTapGestures { pos ->
                        val row = (pos.y / tilePx).toInt()
                        val col = (pos.x / tilePx).toInt()
                        when {
                            row in backRow until backRow + 4 && col >= 10 -> {
                                // SELECT: come nel gioco, sposta lo strumento indicato in un altro posto
                                swapMode = !swapMode
                            }
                            row in backRow until backRow + 4 -> onClose()
                            row in listTop + 1 until listTop + listRows - 1 -> {
                                val i = (row - listTop - 2 + (step - 1)) / step
                                if (i in bag.indices) {
                                    val from = sel
                                    if (swapMode && from != null && from != i) { swapMode = false; onSwap(from, i) }
                                    else selected = i
                                }
                            }
                        }
                    }
                }
                .pointerInput(bag, step) {
                    // Tenere premuto e trascinare: sposta lo strumento dove si lascia il dito.
                    fun itemAt(y: Float): Int? {
                        val row = (y / tilePx).toInt()
                        val i = (row - listTop - 2 + (step - 1)) / step
                        return if (row in listTop + 1 until listTop + listRows - 1 && i in bag.indices) i else null
                    }
                    detectDragGesturesAfterLongPress(
                        onDragStart = { p -> dragFrom = itemAt(p.y); dragTo = dragFrom; selected = null },
                        onDrag = { change, _ -> itemAt(change.position.y)?.let { dragTo = it } },
                        onDragEnd = {
                            val f = dragFrom; val t = dragTo
                            dragFrom = null; dragTo = null
                            if (f != null && t != null && f != t) onSwap(f, t)
                        },
                        onDragCancel = { dragFrom = null; dragTo = null },
                    )
                }
        ) {
            box(0, 3, 19, 5); text("BORSA", 7, 4)
            box(0, listTop, 19, listTop + listRows - 1)
            if (bag.isEmpty()) text("VUOTA", 2, listTop + 2)
            bag.forEachIndexed { i, item ->
                val r = listTop + 2 + i * step
                text(romData.itemName(item.id).take(12), 2, r)
                if (!it.giallotouch.bridge.Items.isKeyItem(item.id)) {
                    val q = "×" + item.quantity
                    text(q, 19 - q.length, r)
                }
                if (dragFrom == i) tile(0xEC, 1, r)                       // ▷ strumento che si sposta
                else if (sel == i) tile(if (swapMode) 0xEC else TileCanvas.CURSOR, 1, r)
                if (dragTo == i && dragFrom != i) tile(TileCanvas.CURSOR, 1, r)
            }
            box(0, backRow, 9, backRow + 3); text("INDIETRO", 1, backRow + 2)
            box(10, backRow, 19, backRow + 3); text("SELECT", 12, backRow + 2)
            if (swapMode) tile(TileCanvas.CURSOR, 11, backRow + 2)
        }
        if (sel != null && sel in bag.indices) {
            val item = bag[sel]
            val key = it.giallotouch.bridge.Items.isKeyItem(item.id)
            val top = rows - 9
            TileImage(
                sheet, 20, 8, unit, key = Pair(sel, key),
                modifier = Modifier
                    .offset { IntOffset(0, (top * tilePx).toInt()) }
                    .pointerInput(sel, key) {
                        detectTapGestures { pos ->
                            when (((pos.y / tilePx).toInt() - 1) / 2) {
                                0 -> onUse(sel, item)
                                1 -> if (!key) onToss(sel, item) else selected = null
                                else -> selected = null
                            }
                        }
                    }
            ) {
                box(0, 0, 19, 7)
                text("USA", 2, 2)
                if (!key) { text("GETTA", 2, 4); text("ANNULLA", 2, 6) } else text("ANNULLA", 2, 4)
            }
        }
    }
}
