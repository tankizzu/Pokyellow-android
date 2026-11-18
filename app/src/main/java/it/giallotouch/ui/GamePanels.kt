package it.giallotouch.ui

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
            // Squadra in alto: riquadro normale, contenuto a metà grandezza ("in piccolo").
            TileImage(sheet, 20, 8, unit, key = "squadra-box", modifier = Modifier.offset { g.at(0f, 2f) }) { box(0, 0, 19, 7) }
            TileImage(
                sheet, 36, 12, unit / 2, key = party,
                modifier = Modifier
                    .offset { g.at(1f, 3f) }
                    .pointerInput(party) {
                        detectTapGestures { pos ->
                            val half = g.tilePx / 2
                            val i = (pos.y / half / 4).toInt() * 2 + (pos.x / half / 18).toInt()
                            if (i in party.indices) onMember(i)
                        }
                    }
            ) { drawTeam(this, party, romData) }
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
    // Per ogni compagno (D-046): icona del gioco, nome e sotto livello e vita; scritte a metà grandezza, icona intera.
    party.take(6).forEachIndexed { i, m ->
        val col = (i % 2) * 18
        val row = (i / 2) * 4
        romData.partyIcon(m.species)?.let { c.icon(it, col * 8, row * 8, scale = 2) }
        c.text(m.name.take(10), col + 4, row)
        c.text("L${m.level} ${m.hp}/${m.maxHp}", col + 4, row + 2)
    }
}

/**
 * OPZIONI (D-040), nello stile del gioco: testo automatico (tocco = cambia velocità) e INDIETRO.
 */
@Composable
fun GameOptions(sheet: IntArray, autoText: Int, onAutoText: () -> Unit, volume: Int, onVolume: (Int) -> Unit, onBack: () -> Unit,
                blackWhite: Boolean = false, onBlackWhite: () -> Unit = {}) {
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
        // Riquadro grande (D-046): COMPARSA TESTO (tocco = cambia) e VOLUME con 6 livelli (tocco sul livello).
        TileImage(
            sheet, 20, 28, unit, key = Triple(autoText, volume, blackWhite),
            modifier = Modifier
                .offset { g.at(0f, (top + 4).toFloat()) }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val row = (pos.y / g.tilePx).toInt(); val col = (pos.x / g.tilePx).toInt()
                        when {
                            row in 1..5 -> onAutoText()
                            row in 7..12 -> onVolume((((pos.x / unit) - 18) / 22).toInt().coerceIn(0, 5))
                            row in 15..19 -> onBlackWhite()
                        }
                    }
                }
        ) {
            box(0, 0, 19, 27)
            text("COMPARSA TESTO", 2, 2); text(speeds[autoText], 2, 4)
            text("VOLUME", 2, 8)
            for (i in 0..5) {
                val x = 18 + 22 * i; val y = 11 * 8
                fill(x, y, 14, 8, ink)
                if (volume == 0 || i > volume) fill(x + 1, y + 1, 12, 6, paper)   // 6 scelte: tocco sulla prima = muto
            }
            text(if (volume == 0) "MUTO" else "", 2, 13)
            text("GIOCO", 2, 16); text(if (blackWhite) "BIANCO E NERO" else "A COLORI", 2, 18)
        }
        TileImage(
            sheet, 11, 4, unit, key = "opz-indietro",
            modifier = Modifier
                .offset { g.at(5f, 39f) }
                .pointerInput(Unit) { detectTapGestures { onBack() } }
        ) {
            box(0, 0, 10, 3); textAt("INDIETRO", 13, 12)
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
                box(0, backRow, 9, backRow + 3); textAt("INDIETRO", 7, backRow * 8 + 12)
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
private fun BackBox(sheet: IntArray, unit: Float, col: Int, row: Int, textX: Int = 9, onBack: () -> Unit) {
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
fun GameMonScreen(sheet: IntArray, m: PartyMember, romData: RomData, onClose: () -> Unit) {
    BackHandler { onClose() }
    BoxWithConstraints(Modifier.fillMaxSize().background(paperOf(sheet))) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        TileImage(sheet, 20, 35, unit, key = m) {
            // Immagine del Pokémon dalla ROM, in basso e al centro di un riquadro 7×7 come nel gioco (D-047).
            val pic = romData.frontPic(m.species)
            if (pic != null) pic(pic, 8 + (56 - pic.width + 1) / 2, 24 + 56 - pic.height)
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
fun GameDexScreen(sheet: IntArray, seen: List<Int>, owned: List<Int>, romData: RomData, onClose: () -> Unit) {
    var open by remember { mutableStateOf<Int?>(null) }
    val o = open
    if (o != null) { GameDexEntryScreen(sheet, o, seen, owned, romData, onMove = { open = it }, onClose = { open = null }); return }
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
fun GameDexEntryScreen(sheet: IntArray, dex: Int, seen: List<Int>, owned: List<Int>, romData: RomData, onMove: (Int) -> Unit, onClose: () -> Unit) {
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
        TileImage(sheet, 20, 30, unit, key = Pair(dex, entry != null), modifier = Modifier.offset { IntOffset(0, (13 * tilePx).toInt()) }) {
            // righe relative alla riga 13 dello schermo
            romData.frontPic(index)?.let { p -> pic(p, 8 + (56 - p.width + 1) / 2, 8 + 56 - p.height) }
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
    onBack: () -> Unit,
) {
    BackHandler { if (!busy) onBack() }
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
        TileImage(
            sheet, 10, 4, unit, key = "lotta-indietro",
            modifier = Modifier
                .offset { IntOffset((5 * tilePx).toInt(), (39 * tilePx).toInt()) }
                .pointerInput(busy) { detectTapGestures { if (!busy) onBack() } }
        ) { box(0, 0, 9, 3); textAt("INDIETRO", 8, 12) }
    }
}
