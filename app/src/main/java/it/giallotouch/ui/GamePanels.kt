package it.giallotouch.ui

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
    /** Cursore ▶ nel riquadro: 0 POKéMON, 1 allenatore, 2 POKéDEX, 3 OPZIONI, 4 BORSA, 5 SALVA (D-041, D-046). */
    cursor: Int,
    party: List<PartyMember>,
    romData: RomData,
    onToggle: () -> Unit,
    onActivate: (Int) -> Unit,
    onMember: (Int) -> Unit,
    playerName: String = "",
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val g = TileGrid(unit, (with(density) { maxHeight.toPx() } / (8 * unit)).toInt())
        val bottom = g.rows - 1
        if (open) {
            // Squadra in alto (D-071, mockup del committente): riquadro 20×12 in (0,0), 3 compagni alla volta (si scorre
            // col dito per vederli tutti e 6), icona animata come nel gioco, nome, livello e PS.
            var first by remember(party.size) { mutableStateOf(0) }
            var frame by remember { mutableStateOf(0) }
            androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(270); frame = 1 - frame } }
            val maxFirst = (party.size - 3).coerceAtLeast(0)
            TileImage(sheet, 20, 12, unit, key = "squadra-box", modifier = Modifier.offset { g.at(0f, 0f) }) { box(0, 0, 19, 11) }
            TileImage(
                sheet, 18, 9, unit, key = listOf(party, first, frame),
                modifier = Modifier
                    .offset { g.at(1f, 2f) }
                    .pointerInput(party, first) {
                        detectTapGestures { pos ->
                            val i = first + (pos.y / g.tilePx / 3).toInt()
                            if (i in party.indices) onMember(i)
                        }
                    }
                    .pointerInput(party.size) {
                        var acc = 0f
                        detectVerticalDragGestures { _, dy ->
                            acc -= dy
                            val steps = (acc / (3 * g.tilePx)).toInt()
                            if (steps != 0) { acc -= steps * 3 * g.tilePx; first = (first + steps).coerceIn(0, maxFirst) }
                        }
                    }
            ) {
                for (j in 0 until 3) {
                    val m = party.getOrNull(first + j) ?: break
                    val r = 3 * j
                    romData.partyIconFrame(m.species, frame)?.let { icon(it, 0, r * 8) }
                    text(m.name.take(10), 3, r)
                    val lv = "L${m.level}"; text(lv, 14, r + 1)
                    text("PS ${m.hp}/${m.maxHp}", 3, r + 1)
                }
                if (first < maxFirst) tile(0xEE, 17, 8)     // altri compagni sotto: ▼ come nel gioco
            }
            // Riquadro dei pulsanti sopra MENU.
            val top = bottom - 15
            TileImage(
                sheet, 20, 11, unit, key = Pair(cursor, playerName),
                modifier = Modifier
                    .offset { g.at(0f, top.toFloat()) }
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            val right = if (pos.x >= 10 * g.tilePx) 1 else 0
                            val row = (pos.y / g.tilePx).toInt()
                            onActivate(right + when { row <= 3 -> 0; row <= 6 -> 2; else -> 4 })
                        }
                    }
            ) {
                box(0, 0, 19, 10)
                text("POKéMON", 2, 2); text(playerName.take(8), 12, 2)
                text("POKéDEX", 2, 5); text("OPZIONI", 11, 5)
                text("BORSA", 2, 8); text("SALVA", 13, 8)
                tile(TileCanvas.CURSOR, when (cursor) { 1 -> 11; 3 -> 10; 5 -> 12; else -> 1 }, 2 + 3 * (cursor / 2))
            }
        }
        TileImage(
            sheet, 8, 4, unit, key = "menu",
            modifier = Modifier
                .offset { g.at(6f, (bottom - 3).toFloat()) }
                .pointerInput(Unit) { detectTapGestures { onToggle() } }
        ) {
            box(0, 0, 7, 3)
            textAt("START", 12, 12)   // centrato sullo schermo (D-062)
        }
    }
}

/**
 * OPZIONI (D-040, D-075): una sola schermata nello stile del gioco, dal menu iniziale e dal menu START.
 * Un solo riquadro GIOCO: ANIMAZIONE, STILE LOTTA (byte [gameOptions] a 0xD359 del gioco), COMPARSA TESTO, VOLUME,
 * COLORI, VIBRAZIONE (dell'app). Tocco su una voce = cambia valore.
 */
@Composable
fun GameOptions(sheet: IntArray, autoText: Int, onAutoText: () -> Unit, volume: Int, onVolume: (Int) -> Unit, onBack: () -> Unit,
                blackWhite: Boolean = false, onBlackWhite: () -> Unit = {},
                gameOptions: Int = 0x03, onGameOption: (Int) -> Unit = {}, vibration: Boolean = true, onVibration: () -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(sheet[(0x7F shr 4) * 8 * 128 + (0x7F and 15) * 8]))
        .pointerInput(Unit) { detectTapGestures { } }) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val rows = (with(density) { maxHeight.toPx() } / (8 * unit)).toInt()
        val g = TileGrid(unit, rows)
        val top = 2
        TileImage(sheet, 20, 3, unit, key = "opz-titolo", modifier = Modifier.offset { g.at(0f, top.toFloat()) }) {
            box(0, 0, 19, 2); text("OPZIONI", 6, 1)
        }
        // Un solo riquadro GIOCO (D-080, D-082): ANIMAZIONE, STILE LOTTA, COMPARSA TESTO (velocità del testo del gioco:
        // VELOCE / MEDIO / LENTO), AUTO-TESTO (il testo va avanti da solo: SÌ / NO), VOLUME, COLORI, VIBRAZIONE.
        val textSpeed = when (gameOptions and 0x0F) { 1 -> "VELOCE"; 3 -> "MEDIO"; else -> "LENTO" }
        TileImage(
            sheet, 20, 24, unit, key = listOf(autoText, volume, blackWhite, gameOptions, vibration),
            modifier = Modifier
                .offset { g.at(0f, (top + 4).toFloat()) }
                .pointerInput(volume) {
                    detectTapGestures { pos ->
                        val row = (pos.y / g.tilePx).toInt()
                        when (row) {
                            in 1..3 -> onGameOption(1); in 4..6 -> onGameOption(2)
                            in 7..9 -> onGameOption(0)
                            in 10..12 -> onAutoText()
                            in 13..15 -> {
                                // 5 tacche da 20%: tocco sulla tacca i = (i+1)×20%; sulla prima quando è già l'unica = muto
                                val i = (((pos.x / unit) - 26) / 22).toInt().coerceIn(0, 4)
                                onVolume(if (i == 0 && volume == 1) 0 else i + 1)
                            }
                            in 16..18 -> onBlackWhite()
                            in 19..21 -> onVibration()
                        }
                    }
                }
        ) {
            box(0, 0, 19, 23)
            text("GIOCO", 7, 0)
            text("ANIMAZIONE", 2, 2); text(if (gameOptions and 0x80 == 0) "SÌ" else "NO", 3, 3)
            text("STILE LOTTA", 2, 5); text(if (gameOptions and 0x40 == 0) "SCEGLI" else "FISSO", 3, 6)
            text("COMPARSA TESTO", 2, 8); text(textSpeed, 3, 9)
            text("AUTO-TESTO", 2, 11); text(if (autoText > 0) "SÌ" else "NO", 3, 12)
            text("VOLUME", 2, 14); text(if (volume == 0) "MUTO" else "", 13, 14)
            for (i in 0..4) {
                val x = 26 + 22 * i; val y = 15 * 8
                fill(x, y, 16, 8, ink)
                if (i >= volume) fill(x + 1, y + 1, 14, 6, paper)
            }
            text("COLORI", 2, 17); text(if (blackWhite) "BIANCO E NERO" else "A COLORI", 3, 18)
            text("VIBRAZIONE", 2, 20); text(if (vibration) "SÌ" else "NO", 3, 21)
        }
        TileImage(
            sheet, 11, 4, unit, key = "opz-indietro",
            modifier = Modifier
                .offset { g.at(4.5f, (rows - 5).toFloat()) }
                .pointerInput(Unit) { detectTapGestures { onBack() } }
        ) {
            box(0, 0, 10, 3); textAt("INDIETRO", 12, 12)
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
            box(0, backRow, 19, backRow + 3); text("INDIETRO", 6, backRow + 2)
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
    // Disposizione del committente (D-046): lista alta fino alla riga 38, strumenti ogni 3 righe (2 o 1 se sono tanti),
    // INDIETRO e SELECT alla riga 39; con uno strumento scelto la lista si accorcia e sotto compare USA / GETTA.
    val step = when { bag.size <= 9 -> 3; bag.size <= 14 -> 2; else -> 1 }
    val listTop = 8
    val backRow = 39
    val listRows = if (selected != null) 27 else backRow - listTop
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
                                val i = (row - listTop - 2 + step / 2) / step
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
                        val i = (row - listTop - 2 + step / 2) / step
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
            if (sel == null) {
                box(0, backRow, 9, backRow + 3); textAt("INDIETRO", 8, backRow * 8 + 12)
                box(10, backRow, 19, backRow + 3); textAt("SELECT", 97, backRow * 8 + 12)
                if (swapMode) tileAt(TileCanvas.CURSOR, 89, backRow * 8 + 12)
            }
        }
        if (sel != null && sel in bag.indices) {
            val item = bag[sel]
            val key = it.giallotouch.bridge.Items.isKeyItem(item.id)
            val top = 35
            TileImage(
                sheet, 20, 8, unit, key = Pair(sel, key),
                modifier = Modifier
                    .offset { IntOffset(0, (top * tilePx).toInt()) }
                    .pointerInput(sel, key) {
                        detectTapGestures { pos ->
                            val row = (pos.y / tilePx).toInt(); val col = (pos.x / tilePx).toInt()
                            when {
                                row >= 4 -> selected = null
                                col < 10 -> onUse(sel, item)
                                !key -> onToss(sel, item)
                            }
                        }
                    }
            ) {
                box(0, 0, 19, 7)
                text("USA", 3, 2)
                if (!key) text("GETTA", 12, 2)
                textAt("ANNULLA", 49, 38)
            }
        }
    }
}


/**
 * Comandi di lotta nello stile del gioco (D-045): riquadro SQUADRA / BORSA / FUGA sotto la scena e lista delle mosse
 * con i PP. Ogni tocco sceglie la voce nel menu originale del gioco (esecutore a circuito chiuso).
 */
@Composable
fun GameBattlePanel(
    sheet: IntArray,
    moves: List<Pair<String, String>>,
    enabled: Boolean,
    onMove: (Int) -> Unit,
    onParty: () -> Unit,
    onBag: () -> Unit,
    onRun: () -> Unit,
    /** Tocco su un riquadro mentre il gioco mostra un testo (nessun menu di lotta): manda avanti il testo (tasto A). */
    onAdvance: () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val tilePx = 8 * unit
        val a = it.giallotouch.bridge.BattleLayout.ACTIONS_TOP_ROW
        val m = it.giallotouch.bridge.BattleLayout.MOVES_TOP_ROW
        TileImage(
            sheet, 20, 5, unit, key = "azioni",
            modifier = Modifier
                .offset { IntOffset(0, (a * tilePx).toInt()) }
                .pointerInput(enabled) {
                    detectTapGestures { pos ->
                        if (!enabled) { onAdvance(); return@detectTapGestures }
                        val col = (pos.x / tilePx).toInt()
                        if (col < 10) onParty() else onBag()
                    }
                }
        ) { box(0, 0, 19, 4); text("SQUADRA", 1, 2); text("BORSA", 13, 2) }
        // FUGA nel suo riquadro in basso (D-046).
        TileImage(
            sheet, 8, 4, unit, key = "fuga",
            modifier = Modifier
                .offset { IntOffset((6 * tilePx).toInt(), (it.giallotouch.bridge.BattleLayout.RUN_TOP_ROW * tilePx).toInt()) }
                .pointerInput(enabled) { detectTapGestures { if (enabled) onRun() else onAdvance() } }
        ) { box(0, 0, 7, 3); textAt("FUGA", 17, 13) }
        TileImage(
            sheet, 20, 11, unit, key = moves,
            modifier = Modifier
                .offset { IntOffset(0, (m * tilePx).toInt()) }
                .pointerInput(moves, enabled) {
                    detectTapGestures { pos ->
                        if (!enabled) { onAdvance(); return@detectTapGestures }
                        val i = (((pos.y / tilePx).toInt() - 1) / 2).coerceIn(0, 3)
                        if (i < moves.size) onMove(i)
                    }
                }
        ) {
            box(0, 0, 19, 10)
            for (i in 0 until 4) {
                val mv = moves.getOrNull(i)
                text(mv?.first?.take(11) ?: "-", 2, 2 + 2 * i)
                if (mv != null && mv.second.isNotEmpty()) text(mv.second, 19 - mv.second.length, 2 + 2 * i)
            }
        }
    }
}


/** Riquadri dell'app sopra la scheda allenatore del gioco (D-045): Pokédex e squadra, INDIETRO. */
@Composable
fun GameTrainerExtras(sheet: IntArray, seen: Int, owned: Int, party: Int, onBack: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val tilePx = 8 * unit
        TileImage(sheet, 20, 9, unit, key = Pair(seen, owned), modifier = Modifier.offset { IntOffset(0, (28 * tilePx).toInt()) }) {
            box(0, 0, 19, 8)
            text("POKéDEX", 6, 3)
            text("VISTI $seen  PRESI $owned", 2, 6)
        }
        TileImage(
            sheet, 10, 4, unit, key = "indietro",
            modifier = Modifier
                .offset { IntOffset((5 * tilePx).toInt(), (39 * tilePx).toInt()) }
                .pointerInput(Unit) { detectTapGestures { onBack() } }
        ) { box(0, 0, 9, 3); textAt("INDIETRO", 8, 12) }
    }
}

/** Riquadro INDIETRO 10×4 nello stile del gioco, in (col, riga) dello schermo. */
@Composable
private fun BackBox(sheet: IntArray, unit: Float, col: Int, row: Int, textX: Int = 8, onBack: () -> Unit) {
    val tilePx = 8 * unit
    TileImage(
        sheet, 10, 4, unit, key = "indietro-$textX",
        modifier = Modifier
            .offset { IntOffset((col * tilePx).toInt(), (row * tilePx).toInt()) }
            .pointerInput(Unit) { detectTapGestures { onBack() } }
    ) { box(0, 0, 9, 3); textAt("INDIETRO", textX, 12) }
}

private fun paperOf(sheet: IntArray) = Color(sheet[(0x7F shr 4) * 8 * 128 + (0x7F and 15) * 8])

/**
 * SQUADRA nello stile del gioco (D-046): per ogni compagno nome, icona, livello e vita; tocco = scheda del Pokémon.
 * INDIETRO e SELECT in basso; spostare (SELECT o tenere premuto e trascinare) chiama [onSwap].
 */
@Composable
fun GameTeamScreen(
    sheet: IntArray,
    party: List<PartyMember>,
    romData: RomData,
    onOpen: (Int) -> Unit,
    onSwap: (Int, Int) -> Unit,
    onClose: () -> Unit,
    /** Scelta del Pokémon su cui usare uno strumento (D-051): titolo diverso, niente SELECT né spostamenti. */
    title: String = "SQUADRA",
    pickMode: Boolean = false,
) {
    var swapFrom by remember { mutableStateOf<Int?>(null) }
    var swapMode by remember { mutableStateOf(false) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragTo by remember { mutableStateOf<Int?>(null) }
    BackHandler { if (swapMode) { swapMode = false; swapFrom = null } else onClose() }
    BoxWithConstraints(Modifier.fillMaxSize().background(paperOf(sheet))) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val tilePx = 8 * unit
        fun memberAt(y: Float): Int? {
            val row = (y / tilePx).toInt()
            val i = (row - 8) / 5
            return if (row >= 8 && i in party.indices) i else null
        }
        TileImage(
            sheet, 20, 39, unit, key = listOf(party, swapMode, swapFrom, dragFrom, dragTo),
            modifier = Modifier
                .pointerInput(party, swapMode, swapFrom) {
                    detectTapGestures { pos ->
                        val i = memberAt(pos.y) ?: return@detectTapGestures
                        when {
                            !swapMode -> onOpen(i)
                            swapFrom == null -> swapFrom = i
                            swapFrom != i -> { val f = swapFrom!!; swapMode = false; swapFrom = null; onSwap(f, i) }
                        }
                    }
                }
                .pointerInput(party, pickMode) {
                    if (pickMode) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { p -> dragFrom = memberAt(p.y); dragTo = dragFrom },
                        onDrag = { change, _ -> memberAt(change.position.y)?.let { dragTo = it } },
                        onDragEnd = {
                            val f = dragFrom; val t = dragTo
                            dragFrom = null; dragTo = null
                            if (f != null && t != null && f != t) onSwap(f, t)
                        },
                        onDragCancel = { dragFrom = null; dragTo = null },
                    )
                }
        ) {
            box(0, 3, 19, 5); text(title, (20 - title.length) / 2, 4)
            box(0, 7, 19, 38)
            party.take(6).forEachIndexed { i, m ->
                val r = 9 + 5 * i
                val name = m.name.take(10)
                text(name, 2, r)
                romData.partyIcon(m.species)?.let { icon(it, maxOf(9, 3 + name.length) * 8, r * 8) }
                val lv = "L${m.level}"; text(lv, 19 - lv.length, r)
                text("PS", 2, r + 1)
                val hp = "${m.hp}/${m.maxHp}"; text(hp, 19 - hp.length, r + 1)
                when {
                    dragFrom == i || swapFrom == i -> tile(0xEC, 1, r)
                    dragTo == i -> tile(TileCanvas.CURSOR, 1, r)
                }
            }
        }
        if (pickMode) { BackBox(sheet, unit, 5, 39, textX = 8) { onClose() }; return@BoxWithConstraints }
        BackBox(sheet, unit, 0, 39, textX = 8) { if (swapMode) { swapMode = false; swapFrom = null } else onClose() }
        TileImage(
            sheet, 10, 4, unit, key = Pair("select", swapMode),
            modifier = Modifier
                .offset { IntOffset((10 * tilePx).toInt(), (39 * tilePx).toInt()) }
                .pointerInput(Unit) { detectTapGestures { swapMode = !swapMode; swapFrom = null } }
        ) { box(0, 0, 9, 3); textAt("SELECT", 16, 13); if (swapMode) tileAt(TileCanvas.CURSOR, 8, 13) }
    }
}

/** Scheda del Pokémon nello stile del gioco (D-046): icona, nome, livello, vita, tipi, statistiche e mosse con PP. */
@Composable
fun GameMonScreen(sheet: IntArray, m: PartyMember, romData: RomData, onClose: () -> Unit, color: Boolean = false) {
    BackHandler { onClose() }
    BoxWithConstraints(Modifier.fillMaxSize().background(paperOf(sheet))) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        TileImage(sheet, 20, 35, unit, key = Pair(m, color)) {
            // Immagine del Pokémon dalla ROM, in basso e al centro di un riquadro 7×7 come nel gioco (D-047).
            val pic = romData.frontPic(m.species)
            if (pic != null) pic(pic, 8 + (56 - pic.width + 1) / 2, 24 + 56 - pic.height, if (color) romData.monPalette(m.species) else null)
            else romData.partyIcon(m.species)?.let { icon(it, 8, 24, scale = 3) }
            text(m.name.take(10), 9, 4)
            text("L${m.level}", 9, 6)
            text("PS ${m.hp}/${m.maxHp}", 9, 8)
            text(m.types.distinct().joinToString("/") { romData.typeName(it) }.take(10), 9, 10)
            box(0, 12, 19, 22)
            val st = m.stats
            listOf("ATTACCO" to st?.attack, "DIFESA" to st?.defense, "VELOCITA" to st?.speed, "SPECIALE" to st?.special)
                .forEachIndexed { k, (label, v) ->
                    text(label, 2, 14 + 2 * k); val t = v?.toString() ?: "-"; text(t, 18 - t.length, 14 + 2 * k)
                }
            box(0, 24, 19, 34)
            for (k in 0 until 4) {
                val mv = m.moves.getOrNull(k)
                text(mv?.let { romData.moveName(it.id).take(11) } ?: "-", 2, 26 + 2 * k)
                if (mv != null) {
                    val max = romData.maxPp(mv.id, mv.ppUps)
                    val pp = if (max != null) "${mv.pp}/$max" else "${mv.pp}"
                    text(pp, 19 - pp.length, 26 + 2 * k)
                }
            }
        }
        BackBox(sheet, unit, 5, 39, onBack = onClose)
    }
}

/** POKéDEX nello stile del gioco (D-046): lista dei numeri fino all'ultimo visto (scorre col dito), visti e presi;
 *  tocco su una specie vista = sua scheda (D-054). */
@Composable
fun GameDexScreen(sheet: IntArray, seen: List<Int>, owned: List<Int>, romData: RomData, onClose: () -> Unit, color: Boolean = false) {
    var open by remember { mutableStateOf<Int?>(null) }
    val o = open
    if (o != null) { GameDexEntryScreen(sheet, o, seen, owned, romData, onMove = { open = it }, onClose = { open = null }, color = color); return }
    BackHandler { onClose() }
    val last = seen.maxOrNull() ?: 0
    val visible = 12
    // la lista parte dalla prima specie vista
    var first by remember { mutableStateOf(((seen.minOrNull() ?: 1) - 1).coerceIn(0, maxOf(0, last - visible))) }
    BoxWithConstraints(Modifier.fillMaxSize().background(paperOf(sheet))) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val tilePx = 8 * unit
        TileImage(
            sheet, 20, 39, unit, key = listOf(seen, owned, first),
            modifier = Modifier
                .pointerInput(last, first) {
                    detectTapGestures { pos ->
                        val row = (pos.y / tilePx).toInt()
                        val k = (row - 8) / 2
                        val n = first + k + 1
                        if (row in 8..32 && k in 0 until visible && n in seen) open = n
                    }
                }
                .pointerInput(last) {
                    var acc = 0f
                    detectVerticalDragGestures { _, dy ->
                        acc -= dy
                        val step = (acc / (2 * tilePx)).toInt()
                        if (step != 0) { acc -= step * 2 * tilePx; first = (first + step).coerceIn(0, maxOf(0, last - visible)) }
                    }
                }
        ) {
            box(0, 3, 19, 5); text("POKéDEX", 6, 4)
            box(0, 7, 19, 33)
            if (last == 0) text("NESSUNO", 2, 9)
            for (k in 0 until visible) {
                val n = first + k + 1
                if (n > last) break
                val r = 9 + 2 * k
                text("%03d".format(n), 2, r)
                text(if (n in seen) romData.speciesNameForDex(n).take(10) else "----------", 6, r)
                if (n in owned) tile(TileCanvas.CURSOR, 18, r)
            }
            box(0, 34, 19, 38)
            text("VISTI ${seen.size}", 2, 36); text("PRESI ${owned.size}", 11, 36)
        }
        BackBox(sheet, unit, 5, 39, textX = 8, onBack = onClose)
    }
}

/**
 * Scheda della specie nel Pokédex (D-054, disposizione del committente): PRECEDENTE e SUCCESSIVO in alto, immagine,
 * numero e nome, categoria, altezza e peso, descrizione del gioco. Una specie solo vista mostra immagine e nome.
 */
@Composable
fun GameDexEntryScreen(sheet: IntArray, dex: Int, seen: List<Int>, owned: List<Int>, romData: RomData, onMove: (Int) -> Unit, onClose: () -> Unit, color: Boolean = false) {
    BackHandler { onClose() }
    val sorted = seen.sorted()
    val prev = sorted.lastOrNull { it < dex }
    val next = sorted.firstOrNull { it > dex }
    val index = romData.indexForDex(dex)
    val entry = if (dex in owned) romData.dexEntry(index) else null
    BoxWithConstraints(Modifier.fillMaxSize().background(paperOf(sheet))) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val tilePx = 8 * unit
        for ((col, target) in listOf(2 to prev, 12 to next)) {
            TileImage(
                sheet, 6, 6, unit, key = Pair(col, target != null),
                modifier = Modifier
                    .offset { IntOffset((col * tilePx).toInt(), (7 * tilePx).toInt()) }
                    .pointerInput(target) { detectTapGestures { target?.let(onMove) } }
            ) {
                box(0, 0, 5, 5)
                if (target != null) { if (col == 12) tileAt(TileCanvas.CURSOR, 20, 20) else tileMirroredAt(TileCanvas.CURSOR, 20, 20) }
            }
        }
        val lines = entry?.lines ?: emptyList()
        TileImage(sheet, 20, 30, unit, key = Triple(dex, entry != null, color), modifier = Modifier.offset { IntOffset(0, (13 * tilePx).toInt()) }) {
            // righe relative alla riga 13 dello schermo
            romData.frontPic(index)?.let { p -> pic(p, 8 + (56 - p.width + 1) / 2, 8 + 56 - p.height, if (color) romData.monPalette(index) else null) }
            text("%03d".format(dex) + " " + romData.speciesNameForDex(dex).take(10), 9, 1)
            if (entry != null) {
                entry.category.forEachIndexed { i, t -> tile(t, 9 + i, 3) }
                text("ALT %d,%d m".format(entry.heightDm / 10, entry.heightDm % 10), 9, 5)
                text("PESO %d,%d kg".format(entry.weightHg / 10, entry.weightHg % 10), 9, 7)
            }
            val h = maxOf(12, 2 * lines.size + 3)
            box(0, 9, 19, 9 + h - 1)
            if (entry == null) text("?????", 1, 11)
            lines.forEachIndexed { k, l -> l.forEachIndexed { x, t -> tile(t, 1 + x, 11 + 2 * k) } }
        }
        BackBox(sheet, unit, 5, 39, textX = 8, onBack = onClose)
    }
}

/**
 * SQUADRA o BORSA in lotta, versione C del mockup (D-050): sopra la lotta, al posto dei riquadri delle azioni e delle
 * mosse; il testo in alto dice cosa fare. Tocco su una voce = scelta (l'app la esegue nel menu del gioco).
 */
@Composable
fun GameBattleListPanel(
    sheet: IntArray,
    party: List<PartyMember>?,
    bag: List<it.giallotouch.bridge.BagItem>?,
    romData: RomData,
    busy: Boolean,
    onPick: (Int) -> Unit,
    /** null = scelta obbligata (Pokémon KO): niente INDIETRO, il gioco non permette di annullare. */
    onBack: (() -> Unit)?,
) {
    BackHandler { if (!busy) onBack?.invoke() }
    val count = party?.size ?: bag?.size ?: 0
    val step = when {
        party != null -> if (count <= 3) 4 else 2
        count <= 4 -> 3
        count <= 6 -> 2
        else -> 1
    }
    val visible = (13 / step).coerceAtLeast(1)
    var first by remember(count) { mutableStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val tilePx = 8 * unit
        TileImage(sheet, 20, 6, unit, key = Pair(party != null, busy), modifier = Modifier.offset { IntOffset(0, (it.giallotouch.bridge.BattleLayout.TEXT_TOP_ROW * tilePx).toInt()) }) {
            box(0, 0, 19, 5)
            text(if (party != null) "Scegli un POKéMON" else "Usa uno strumento", 1, 2)
        }
        TileImage(
            sheet, 20, 16, unit, key = listOf(party, bag, first, busy),
            modifier = Modifier
                .offset { IntOffset(0, (23 * tilePx).toInt()) }
                .pointerInput(count, first, busy) {
                    detectTapGestures { pos ->
                        if (busy) return@detectTapGestures
                        val row = (pos.y / tilePx).toInt()
                        val k = (row - 2 + step / 2).floorDiv(step)
                        val i = first + k
                        if (row >= 1 && k in 0 until visible && i in 0 until count) onPick(i)
                    }
                }
                .pointerInput(count) {
                    var acc = 0f
                    detectVerticalDragGestures { _, dy ->
                        acc -= dy
                        val st = (acc / (step * tilePx)).toInt()
                        if (st != 0) { acc -= st * step * tilePx; first = (first + st).coerceIn(0, maxOf(0, count - visible)) }
                    }
                }
        ) {
            box(0, 0, 19, 15)
            if (count == 0) text(if (party != null) "-" else "VUOTA", 2, 2)
            for (k in 0 until visible) {
                val i = first + k
                if (i >= count) break
                val r = 2 + step * k
                if (party != null) {
                    val m = party[i]
                    if (step >= 4) {
                        romData.partyIcon(m.species)?.let { icon(it, 8, r * 8) }
                        text(m.name.take(10), 4, r); val lv = "L${m.level}"; text(lv, 19 - lv.length, r)
                        text("PS ${m.hp}/${m.maxHp}", 4, r + 1)
                    } else {
                        text(m.name.take(10), 2, r); val hp = "${m.hp}/${m.maxHp}"; text(hp, 19 - hp.length, r)
                    }
                } else {
                    val item = bag!![i]
                    text(romData.itemName(item.id).take(12), 2, r)
                    if (!it.giallotouch.bridge.Items.isKeyItem(item.id)) { val q = "×" + item.quantity; text(q, 19 - q.length, r) }
                }
            }
        }
        if (onBack != null) TileImage(
            sheet, 10, 4, unit, key = "lotta-indietro",
            modifier = Modifier
                .offset { IntOffset((5 * tilePx).toInt(), (39 * tilePx).toInt()) }
                .pointerInput(busy) { detectTapGestures { if (!busy) onBack() } }
        ) { box(0, 0, 9, 3); textAt("INDIETRO", 8, 12) }
    }
}

/** Testo centrato in un riquadro largo [w] caselle (posizione in pixel). */
private fun TileCanvas.centered(s: String, x0: Int, w: Int, py: Int) = textAt(s, x0 * 8 + (w * 8 - s.length * 8) / 2, py)

/**
 * Riquadri in basso di box e PC (D-062, mockup del committente): a sinistra 11×4 ([left], INDIETRO o ANNULLA), a destra
 * 9×4 ([right], AZIONI o OK), testi centrati. Con [right] = null resta solo [left], centrato (10×4 in (5,39)).
 */
@Composable
private fun BottomButtons(sheet: IntArray, unit: Float, left: String, right: String?, enabled: Boolean, onLeft: () -> Unit, onRight: () -> Unit = {}) {
    val tilePx = 8 * unit
    if (right == null) {
        TileImage(sheet, 10, 4, unit, key = "basso-$left",
            modifier = Modifier.offset { IntOffset((5 * tilePx).toInt(), (39 * tilePx).toInt()) }
                .pointerInput(enabled, left) { detectTapGestures { if (enabled) onLeft() } }) { box(0, 0, 9, 3); centered(left, 0, 10, 12) }
        return
    }
    TileImage(sheet, 11, 4, unit, key = "basso-sx-$left",
        modifier = Modifier.offset { IntOffset(0, (39 * tilePx).toInt()) }
            .pointerInput(enabled, left) { detectTapGestures { if (enabled) onLeft() } }) { box(0, 0, 10, 3); centered(left, 0, 11, 12) }
    TileImage(sheet, 9, 4, unit, key = "basso-dx-$right",
        modifier = Modifier.offset { IntOffset((11 * tilePx).toInt(), (39 * tilePx).toInt()) }
            .pointerInput(enabled, right) { detectTapGestures { if (enabled) onRight() } }) { box(0, 0, 8, 3); centered(right, 0, 9, 12) }
}

/** Riquadro AZIONI (D-062): 13×12 in (7,27), due voci (righe 30 e 35); un tocco fuori lo chiude. */
@Composable
private fun ActionsBox(sheet: IntArray, unit: Float, first: String, second: String, onFirst: () -> Unit, onSecond: () -> Unit, onClose: () -> Unit) {
    val tilePx = 8 * unit
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onClose() } })
    TileImage(sheet, 13, 12, unit, key = "azioni-$first-$second",
        modifier = Modifier.offset { IntOffset((7 * tilePx).toInt(), (27 * tilePx).toInt()) }
            .pointerInput(first, second) { detectTapGestures { pos -> if (pos.y / tilePx < 6f) onFirst() else onSecond() } }
    ) { box(0, 0, 12, 11); centered(first, 0, 13, 3 * 8); centered(second, 0, 13, 8 * 8) }
}

/**
 * Domanda di conferma (D-062, mockup "Box: liberare"): riquadro 20×9 in (0,27) con due righe centrate, SÌ 8×5 in (1,38)
 * e NO 8×5 in (11,38).
 */
@Composable
private fun ConfirmBox(sheet: IntArray, unit: Float, line1: String, line2: String, onYes: () -> Unit, onNo: () -> Unit) {
    val tilePx = 8 * unit
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } })
    TileImage(sheet, 20, 9, unit, key = "domanda-$line1-$line2",
        modifier = Modifier.offset { IntOffset(0, (27 * tilePx).toInt()) }) { box(0, 0, 19, 8); centered(line1, 0, 20, 2 * 8); centered(line2, 0, 20, 5 * 8) }
    for ((col, word, act) in listOf(Triple(1, "SÌ", onYes), Triple(11, "NO", onNo))) TileImage(sheet, 8, 5, unit, key = "domanda-$word",
        modifier = Modifier.offset { IntOffset((col * tilePx).toInt(), (38 * tilePx).toInt()) }
            .pointerInput(word) { detectTapGestures { act() } }) { box(0, 0, 7, 4); centered(word, 0, 8, 2 * 8) }
}

/**
 * STRUMENTI PC (D-055, D-062, mockup del committente): lista degli strumenti nel PC, tocco = PRELEVA un pezzo. In basso
 * INDIETRO e AZIONI (DEPOSITA: la borsa, tocco = deposita un pezzo; BUTTA: tocco su uno strumento = quantità con le
 * frecce, poi OK). Tenere premuto uno strumento = BUTTA.
 */
@Composable
fun GamePcScreen(
    sheet: IntArray,
    pcItems: List<it.giallotouch.bridge.BagItem>,
    bag: List<it.giallotouch.bridge.BagItem>,
    romData: RomData,
    busy: Boolean,
    onWithdraw: (Int) -> Unit,
    onDeposit: (Int) -> Unit,
    onToss: (Int, Int) -> Unit,
    onClose: () -> Unit,
) {
    var depositing by remember { mutableStateOf(false) }
    var tossing by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf(false) }
    var tossItem by remember { mutableStateOf<Int?>(null) }
    var qty by remember(tossItem) { mutableStateOf(1) }
    BackHandler { if (busy) Unit else if (tossItem != null) tossItem = null else if (actions) actions = false else if (depositing || tossing) { depositing = false; tossing = false } else onClose() }
    val list = if (depositing) bag else pcItems
    val step = when { list.size <= 9 -> 3; list.size <= 14 -> 2; else -> 1 }
    BoxWithConstraints(Modifier.fillMaxSize().background(paperOf(sheet))) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val tilePx = 8 * unit
        fun rowItem(y: Float): Int? { val row = (y / tilePx).toInt(); val i = (row - 9 + step / 2).floorDiv(step); return if (row in 8..37 && i in list.indices) i else null }
        TileImage(
            sheet, 20, 39, unit, key = listOf(list, depositing, tossing, busy),
            modifier = Modifier.pointerInput(list, depositing, tossing, busy) {
                detectTapGestures(
                    onLongPress = { pos -> if (!busy && !depositing) rowItem(pos.y)?.let { tossItem = it } },
                    onTap = { pos ->
                        if (busy) return@detectTapGestures
                        val i = rowItem(pos.y) ?: return@detectTapGestures
                        when { depositing -> onDeposit(i); tossing -> tossItem = i; else -> onWithdraw(i) }
                    })
            }
        ) {
            box(0, 3, 19, 5); val title = when { depositing -> "DEPOSITA"; tossing -> "BUTTA"; else -> "STRUMENTI PC" }; centered(title, 0, 20, 4 * 8)
            box(0, 7, 19, 37)
            if (list.isEmpty()) text("VUOTO", 2, 9)
            list.take(28 / step + 1).forEachIndexed { i, item ->
                val r = 9 + step * i
                if (r > 36) return@forEachIndexed
                text(romData.itemName(item.id).take(12), 2, r)
                if (!it.giallotouch.bridge.Items.isKeyItem(item.id)) { val q = "×" + item.quantity; text(q, 19 - q.length, r) }
            }
            if (busy) text("...", 16, 4)
        }
        val sel = tossItem
        if (sel != null && sel in pcItems.indices) {
            // quantità da buttare: riquadro 20×9 in (0,29), frecce a sinistra e a destra del numero
            val item = pcItems[sel]
            val max = item.quantity.coerceIn(1, 99)
            TileImage(sheet, 20, 9, unit, key = Triple("butta", sel, qty),
                modifier = Modifier.offset { IntOffset(0, (29 * tilePx).toInt()) }.pointerInput(sel, max) {
                    detectTapGestures { pos -> val col = (pos.x / tilePx).toInt(); if (col <= 7) qty = if (qty <= 1) max else qty - 1 else if (col >= 12) qty = if (qty >= max) 1 else qty + 1 }
                }
            ) {
                box(0, 0, 19, 8); centered("BUTTA " + romData.itemName(item.id).take(12), 0, 20, 2 * 8)
                tileMirroredAt(0xED, 5 * 8, 5 * 8); centered("×%02d".format(qty), 0, 20, 5 * 8); tileAt(0xED, 14 * 8, 5 * 8)
            }
            BottomButtons(sheet, unit, "ANNULLA", "OK", !busy, onLeft = { tossItem = null }, onRight = { onToss(sel, qty); tossItem = null })
        } else {
            BottomButtons(sheet, unit, "INDIETRO", if (depositing || tossing) null else "AZIONI", !busy,
                onLeft = { if (depositing || tossing) { depositing = false; tossing = false } else onClose() }, onRight = { actions = true })
        }
        if (actions) ActionsBox(sheet, unit, "DEPOSITA", "BUTTA",
            onFirst = { actions = false; depositing = true }, onSecond = { actions = false; tossing = true }, onClose = { actions = false })
    }
}

/**
 * Scelta del PC del Centro (D-058): le voci del gioco in riquadri da toccare in basso (come la scelta del negozio);
 * il menu del gioco sulla mappa resta nascosto.
 */
@Composable
fun GamePcChoice(sheet: IntArray, entries: List<String>, busy: Boolean, onChoose: (Int) -> Unit) {
    BackHandler { if (!busy) onChoose(entries.lastIndex) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val tilePx = 8 * unit
        val first = 40 - 4 * entries.size
        entries.forEachIndexed { i, label ->
            TileImage(
                sheet, 18, 4, unit, key = "pc-$i-$label",
                modifier = Modifier.offset { IntOffset((1 * tilePx).toInt(), ((first + 4 * i) * tilePx).toInt()) }
                    .pointerInput(busy, i) { detectTapGestures { if (!busy) onChoose(i) } }
            ) { box(0, 0, 17, 3); text(label.take(16), (18 - label.length.coerceAtMost(16)) / 2, 2) }
        }
    }
}

/**
 * BOX POKéMON del Centro (D-058, D-062, mockup del committente): riquadro posti 20×5 in alto, lista dei Pokémon del box
 * (tocco = RITIRA; tenere premuto = LIBERA). Scorrere a lato = box precedente / successivo. In basso INDIETRO e AZIONI
 * (LIBERA: tocco su un Pokémon e conferma; DEPOSITA: la squadra, tocco = deposita). L'ultimo Pokémon della squadra non
 * si deposita e con 6 in squadra non si ritira, come nel gioco.
 */
@Composable
fun GameBoxScreen(
    sheet: IntArray,
    boxNumber: Int,
    boxMons: List<it.giallotouch.bridge.BoxMon>,
    party: List<it.giallotouch.bridge.PartyMember>,
    romData: RomData,
    busy: Boolean,
    onWithdraw: (Int) -> Unit,
    onDeposit: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    onChangeBox: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var depositing by remember { mutableStateOf(false) }
    var releasing by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Int?>(null) }
    BackHandler { if (busy) Unit else if (confirm != null) confirm = null else if (actions) actions = false else if (depositing || releasing) { depositing = false; releasing = false } else onClose() }
    val entries: List<Triple<Int, String, Int>> =
        if (depositing) party.map { Triple(it.species, it.name, it.level) } else boxMons.map { Triple(it.species, it.name, it.level) }
    val full = !depositing && party.size >= 6
    val step = 3
    BoxWithConstraints(Modifier.fillMaxSize().background(paperOf(sheet))) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val tilePx = 8 * unit
        fun rowEntry(y: Float): Int? { val row = (y / tilePx).toInt(); val i = (row - 10 + step / 2).floorDiv(step); return if (row in 8..37 && i in entries.indices) i else null }
        TileImage(
            sheet, 20, 39, unit, key = listOf(entries, depositing, releasing, busy, boxNumber),
            modifier = Modifier
                .pointerInput(entries, depositing, releasing, busy) {
                    detectTapGestures(
                        onLongPress = { pos -> if (!busy && !depositing) rowEntry(pos.y)?.let { confirm = it } },
                        onTap = { pos ->
                            if (busy) return@detectTapGestures
                            val i = rowEntry(pos.y) ?: return@detectTapGestures
                            when {
                                depositing -> if (party.size > 1) onDeposit(i)
                                releasing -> confirm = i
                                !full -> onWithdraw(i)
                            }
                        })
                }
                .pointerInput(boxNumber, busy, depositing) {
                    // scorrere a lato: box precedente o successivo (1–12, in giro)
                    var dx = 0f
                    detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                        if (!busy && !depositing && kotlin.math.abs(dx) > 6 * tilePx) {
                            val next = if (dx < 0) boxNumber % 12 + 1 else (boxNumber + 10) % 12 + 1
                            onChangeBox(next - 1)
                        }
                    }) { _, d -> dx += d }
                }
        ) {
            box(0, 2, 19, 6)
            val title = when { depositing -> "DEPOSITA"; releasing -> "LIBERA"; else -> "BOX $boxNumber" }
            text(title, 1, 3); if (busy) text("...", 16, 3)
            text(if (depositing) "SQUADRA ${party.size}/6" else "POSTI ${boxMons.size}/20", 1, 5)
            box(0, 7, 19, 37)
            if (entries.isEmpty()) text("VUOTO", 2, 9)
            entries.take(9).forEachIndexed { i, (species, name, level) ->
                val r = 9 + step * i
                romData.partyIcon(species)?.let { icon(it, 8, r * 8) }
                text(name.take(10), 4, r); text("L$level", 16, r)
            }
            if (depositing && party.size <= 1) text("ULTIMO POKéMON", 2, 36)
            else if (full) text("SQUADRA PIENA", 2, 36)
        }
        val c = confirm
        if (c != null && c in boxMons.indices) {
            ConfirmBox(sheet, unit, "Liberare", boxMons[c].name + "?", onYes = { confirm = null; releasing = false; onRelease(c) }, onNo = { confirm = null })
        } else {
            BottomButtons(sheet, unit, "INDIETRO", if (depositing || releasing) null else "AZIONI", !busy,
                onLeft = { if (depositing || releasing) { depositing = false; releasing = false } else onClose() }, onRight = { actions = true })
        }
        if (actions) ActionsBox(sheet, unit, "LIBERA", "DEPOSITA",
            onFirst = { actions = false; releasing = true }, onSecond = { actions = false; depositing = true }, onClose = { actions = false })
    }
}

/**
 * Negozio (D-056, mockup del committente). [mode]: 0 = scelta sopra la mappa (COMPRA / VENDI / ESCI, soldi in alto),
 * 1 = COMPRA (lista con prezzi), 2 = VENDI (borsa con ricavo). Scelto un articolo, la quantità si regola col cursore a
 * scorrimento (riquadro 13×4 in basso) e si conferma con OK.
 */
@Composable
fun GameShopScreen(
    sheet: IntArray,
    mode: Int,
    money: Int,
    shopItems: List<Int>,
    bag: List<it.giallotouch.bridge.BagItem>,
    romData: RomData,
    busy: Boolean,
    onMode: (Int) -> Unit,
    onBuy: (Int, Int) -> Unit,
    onSell: (Int, Int) -> Unit,
    onExit: () -> Unit,
) {
    var chosen by remember(mode) { mutableStateOf<Int?>(null) }
    var qty by remember(mode, chosen) { mutableStateOf(1) }
    BackHandler { if (busy) Unit else if (chosen != null) chosen = null else if (mode != 0) onMode(0) else onExit() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val tilePx = 8 * unit
        if (mode == 0) {
            TileImage(sheet, 9, 4, unit, key = Pair("soldi", money), modifier = Modifier.offset { IntOffset((11 * tilePx).toInt(), (2 * tilePx).toInt()) }) {
                box(0, 0, 8, 3); text("SOLDI", 1, 1); val m = "$money"; text(m, 8 - m.length, 2)
            }
            for ((c, r, w, label, act) in listOf(
                Quint(1, 33, 9, "COMPRA", { onMode(1) }), Quint(10, 33, 9, "VENDI", { onMode(2) }), Quint(5, 38, 10, "ESCI", onExit))) {
                TileImage(
                    sheet, w, 4, unit, key = label,
                    modifier = Modifier.offset { IntOffset((c * tilePx).toInt(), (r * tilePx).toInt()) }
                        .pointerInput(busy) { detectTapGestures { if (!busy) act() } }
                ) { box(0, 0, w - 1, 3); text(label, (w - label.length) / 2, 2) }
            }
            return@BoxWithConstraints
        }
        val selling = mode == 2
        val entries: List<Triple<Int, Int, Int>> = if (selling)
            bag.map { Triple(it.id, it.quantity, (romData.itemPrice(it.id) ?: 0) / 2) }
        else shopItems.map { Triple(it, 0, romData.itemPrice(it) ?: 0) }
        val sel = chosen
        Box(Modifier.fillMaxSize().background(paperOf(sheet)))
        TileImage(
            sheet, 20, 39, unit, key = listOf(entries, money, sel, qty, busy, mode),
            modifier = Modifier.pointerInput(entries, busy, sel) {
                detectTapGestures { pos ->
                    if (busy || sel != null) return@detectTapGestures
                    val row = (pos.y / tilePx).toInt()
                    val i = (row - 14 + 1) / 3
                    if (row in 13..37 && i in entries.indices) {
                        val e = entries[i]
                        if (selling && (e.third == 0 || it.giallotouch.bridge.Items.isKeyItem(e.first))) return@detectTapGestures
                        chosen = i; qty = 1
                    }
                }
            }
        ) {
            box(0, 3, 19, 5); val title = if (selling) "VENDI" else "COMPRA"; text(title, (20 - title.length) / 2, 4)
            box(0, 7, 19, 11); text("SOLDI", 2, 9); val m = "$money"; text(m, 18 - m.length, 9)
            val listBottom = if (sel != null) 29 else 37
            box(0, 12, 19, listBottom)
            if (entries.isEmpty()) text("VUOTO", 2, 14)
            entries.forEachIndexed { i, (id, q, price) ->
                val r = 14 + 3 * i
                if (r > listBottom - 1) return@forEachIndexed
                text(romData.itemName(id).take(11), 2, r)
                if (selling) { val qs = "×$q"; text(qs, 13 - qs.length + 1, r) }
                val ps = "$price"; text(ps, 19 - ps.length, r)
                if (sel == i) tile(TileCanvas.CURSOR, 1, r)
            }
            if (sel != null) {
                val (id, _, price) = entries[sel]
                box(0, 30, 19, 38)
                text(romData.itemName(id).take(12), 2, 32)
                text("×", 2, 34); text("%02d".format(qty), 4, 34)
                val tot = "${price * qty}"; text(tot, 18 - tot.length, 34)
                if (busy) text("...", 16, 32)
            }
        }
        if (sel == null) {
            BackBox(sheet, unit, 5, 39, textX = 8) { if (!busy) onMode(0) }
        } else {
            val (id, owned, price) = entries[sel]
            val max = if (selling) owned.coerceIn(1, 99) else (if (price > 0) (money / price) else 99).coerceIn(1, 99)
            // cursore a scorrimento: trascinando (o toccando) si sceglie la quantità da 1 a [max]
            TileImage(
                sheet, 13, 4, unit, key = Triple(qty, max, sel),
                modifier = Modifier.offset { IntOffset(0, (39 * tilePx).toInt()) }
                    .pointerInput(max, busy) {
                        fun set(x: Float) { if (!busy) qty = (1 + ((x / (13 * tilePx) - 0.1f) / 0.8f * (max - 1)).toInt()).coerceIn(1, max) }
                        detectTapGestures { set(it.x) }
                    }
                    .pointerInput(max, busy) {
                        detectHorizontalDragGestures { change, _ -> if (!busy) qty = (1 + ((change.position.x / (13 * tilePx) - 0.1f) / 0.8f * (max - 1)).toInt()).coerceIn(1, max) }
                    }
            ) {
                box(0, 0, 12, 3)
                val x0 = 12; val x1 = 13 * 8 - 12
                fill(x0, 15, x1 - x0, 2, ink)
                val kx = if (max <= 1) x0 else x0 + (x1 - x0) * (qty - 1) / (max - 1)
                fill(kx - 3, 11, 6, 10, ink)
            }
            TileImage(
                sheet, 7, 4, unit, key = Pair("ok", busy),
                modifier = Modifier.offset { IntOffset((13 * tilePx).toInt(), (39 * tilePx).toInt()) }
                    .pointerInput(busy, qty, sel) { detectTapGestures { if (!busy) { if (selling) onSell(sel, qty) else onBuy(sel, qty); chosen = null } } }
            ) { box(0, 0, 6, 3); text("OK", 2, 2) }
        }
    }
}

private data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)


/**
 * Mossa da dimenticare (D-085, mockup approvato): in alto NUOVA MOSSA (nome e PP) e NON IMPARARE; sotto il testo del
 * gioco le 4 mosse con i PP. Tocco su una mossa = la dimentica; NON IMPARARE = tasto B nel gioco (che poi chiede conferma).
 */
@Composable
fun GameForgetMove(
    sheet: IntArray,
    newMove: Pair<String, String>,
    /** Tipo e potenza della mossa nuova (D-088): "NORMALE" e "40" (potenza vuota per le mosse di stato). */
    newMoveInfo: Pair<String, String> = "" to "",
    moves: List<Pair<String, String>>,
    enabled: Boolean,
    onForget: (Int) -> Unit,
    onKeep: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val tilePx = 8 * unit
        TileImage(sheet, 20, 9, unit, key = newMove to newMoveInfo, modifier = Modifier.offset { IntOffset(0, (1 * tilePx).toInt()) }) {
            box(0, 0, 19, 8); text("NUOVA MOSSA", 1, 1); text(newMove.first.take(11), 2, 3)
            if (newMove.second.isNotEmpty()) text(newMove.second, 19 - newMove.second.length, 3)
            if (newMoveInfo.first.isNotEmpty()) text("TIPO/" + newMoveInfo.first.take(12), 2, 5)
            text("POTENZA", 2, 7)
            val pot = newMoveInfo.second.ifEmpty { "-" }
            text(pot, 19 - pot.length, 7)
        }
        TileImage(
            sheet, 14, 4, unit, key = "non-imparare",
            modifier = Modifier
                .offset { IntOffset((3 * tilePx).toInt(), (11 * tilePx).toInt()) }
                .pointerInput(enabled) { detectTapGestures { if (enabled) onKeep() } }
        ) { box(0, 0, 13, 3); text("NON IMPARARE", 1, 2) }
        TileImage(
            sheet, 20, 10, unit, key = moves,
            modifier = Modifier
                .offset { IntOffset(0, (32 * tilePx).toInt()) }
                .pointerInput(moves, enabled) {
                    detectTapGestures { pos ->
                        if (!enabled) return@detectTapGestures
                        val i = (((pos.y / tilePx).toInt() - 1) / 2).coerceIn(0, 3)
                        if (i < moves.size) onForget(i)
                    }
                }
        ) {
            box(0, 0, 19, 9)
            moves.forEachIndexed { i, mv ->
                text(mv.first.take(11), 2, 1 + 2 * i)
                if (mv.second.isNotEmpty()) text(mv.second, 19 - mv.second.length, 1 + 2 * i)
            }
        }
    }
}


/**
 * Etere / Elisir / PP-su (D-093), disposizione B scelta dal committente: in alto strumento, Pokémon e cosa fa (righe
 * 1–7); il testo del gioco alla riga 9; le 4 mosse con i PP alla riga 28 (tocco = quella mossa); INDIETRO in basso al
 * centro (riga 39) = tasto B nel gioco.
 */
@Composable
fun GameEther(
    sheet: IntArray,
    itemName: String,
    monName: String,
    level: Int,
    effect: String,
    text: List<String>,
    moves: List<Pair<String, String>>,
    enabled: Boolean,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val tilePx = 8 * unit
        TileImage(sheet, 20, 7, unit, key = Triple(itemName, monName, effect), modifier = Modifier.offset { IntOffset(0, (1 * tilePx).toInt()) }) {
            box(0, 0, 19, 6)
            if (itemName.isNotEmpty()) text(itemName.take(18), 1, 1)
            text(monName.take(10), 2, 3)
            val l = ":L$level"; text(l, 19 - l.length, 3)
            if (effect.isNotEmpty()) text(effect.take(17), 2, 5)
        }
        TileImage(sheet, 20, 6, unit, key = text, modifier = Modifier.offset { IntOffset(0, (9 * tilePx).toInt()) }) {
            box(0, 0, 19, 5)
            text.getOrNull(0)?.let { text(it.take(18), 1, 2) }
            text.getOrNull(1)?.let { text(it.take(18), 1, 4) }
        }
        TileImage(
            sheet, 20, 10, unit, key = moves,
            modifier = Modifier
                .offset { IntOffset(0, (28 * tilePx).toInt()) }
                .pointerInput(moves, enabled) {
                    detectTapGestures { pos ->
                        if (!enabled) return@detectTapGestures
                        val i = (((pos.y / tilePx).toInt() - 1) / 2).coerceIn(0, 3)
                        if (i < moves.size) onPick(i)
                    }
                }
        ) {
            box(0, 0, 19, 9)
            moves.forEachIndexed { i, mv ->
                text(mv.first.take(11), 2, 1 + 2 * i)
                if (mv.second.isNotEmpty()) text(mv.second, 19 - mv.second.length, 1 + 2 * i)
            }
        }
        TileImage(
            sheet, 14, 4, unit, key = "etere-indietro",
            modifier = Modifier
                .offset { IntOffset((3 * tilePx).toInt(), (39 * tilePx).toInt()) }
                .pointerInput(enabled) { detectTapGestures { if (enabled) onBack() } }
        ) { box(0, 0, 13, 3); text("INDIETRO", 3, 2) }
    }
}
