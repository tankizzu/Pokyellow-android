package it.giallotouch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import it.giallotouch.bridge.GameCommand
import it.giallotouch.bridge.TextAssist
import it.giallotouch.storage.Settings
import it.giallotouch.storage.SlotEntry
import it.giallotouch.bridge.MenuKind
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import it.giallotouch.bridge.PartyMember
import it.giallotouch.bridge.Status
import it.giallotouch.emu.GameSession
import it.giallotouch.emu.NativeCore
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot

internal val Ink = Color(0xFF171624)
internal val Glass = Color(0xE0282540)
internal val Yellow = Color(0xFFF6C933)
internal val Cream = Color(0xFFFFF7DC)

/**
 * Schermo di gioco del prototipo:
 * - levetta che compare dove appoggi il dito (4 direzioni, come il Game Boy);
 * - tocco veloce senza trascinare = tasto A (parla, esamina, fa avanzare i dialoghi);
 * - gesto "indietro" di Android = tasto B;
 * - in basso, icona e nome: Squadra, Borsa, Pokédex (menu nativi), Menu (START), Testo auto, Salva (salvataggio dell'app);
 *   Select compare sopra la barra solo quando è aperto un menu del gioco;
 * - nel menu iniziale del gioco: "Carica partita" per i salvataggi dell'app.
 */
@Composable
fun GameScreen(session: GameSession, settings: Settings) {
    var held by remember { mutableStateOf(0) }       // direzione tenuta dalla levetta
    var pressedSince by remember { mutableStateOf<Long?>(null) }  // dito appoggiato (per il testo più veloce)
    var autoText by remember { mutableStateOf(settings.autoTextSpeed) }
    var tapped by remember { mutableStateOf(0) }     // tasti premuti brevemente (A, B, START, SELECT)
    var joyOrigin by remember { mutableStateOf<Offset?>(null) }
    var joyKnob by remember { mutableStateOf(Offset.Zero) }
    var message by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val radius = with(density) { 58.dp.toPx() }

    LaunchedEffect(held, tapped) { session.setKeys(held or tapped) }
    LaunchedEffect(autoText) { session.autoTextSpeed = autoText; settings.autoTextSpeed = autoText }
    // Dito tenuto premuto per più di 0,3 s: durante i testi il gioco scorre più veloce.
    LaunchedEffect(pressedSince) {
        if (pressedSince == null) session.setHolding(false) else { delay(300); session.setHolding(true) }
    }
    LaunchedEffect(tapped) {
        if (tapped != 0) { delay(110); tapped = 0 }
    }
    LaunchedEffect(message) {
        if (message != null) { delay(2000); message = null }
    }
    var snapshot by remember { mutableStateOf(session.latestSnapshot.get()) }
    var commandBusy by remember { mutableStateOf(false) }
    var battleReserved by remember { mutableStateOf(0) }   // altezza dei pulsanti di lotta, per la lotta a tutto schermo
    var inGame by remember { mutableStateOf(false) }       // partita in corso (non titolo o menu iniziale)
    var loadOpen by remember { mutableStateOf(false) }     // schermata "Carica partita"
    var slots by remember { mutableStateOf(emptyList<SlotEntry>()) }
    var teamOpen by remember { mutableStateOf(false) }    // menu Squadra nativo
    var bagOpen by remember { mutableStateOf(false) }     // menu Borsa nativo
    var dexOpen by remember { mutableStateOf(false) }     // Pokédex nativo
    var teamSelected by remember { mutableStateOf<Int?>(null) }  // scheda aperta (null = elenco)
    // Tastiera del telefono per i nomi (D-029): in attesa di risposta dal gioco, o nascosta dopo un errore.
    var nameRequested by remember { mutableStateOf(false) }
    var namingDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(session) {
        while (true) {
            snapshot = session.latestSnapshot.get()
            commandBusy = session.commandBusy
            inGame = session.inGame
            if (nameRequested && !session.nameBusy) {
                nameRequested = false
                if (session.nameResult == false) {
                    namingDismissed = true
                    message = "Non sono riuscito a scrivere il nome: usa la tastiera del gioco"
                }
            }
            delay(100)
        }
    }
    val namingOpen = snapshot.naming != null
    LaunchedEffect(namingOpen) { if (!namingOpen) namingDismissed = false }

    BackHandler { tapped = NativeCore.KEY_B }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx -> GameView(ctx).also { it.attach(session) } },
            modifier = Modifier.fillMaxSize(),
            update = { it.battleReservedBottomPx = battleReserved },
            onRelease = { it.attach(null) }
        )

        // Livello dei tocchi: levetta e tasto A.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        val t0 = down.uptimeMillis
                        pressedSince = t0
                        var moved = false
                        joyOrigin = start
                        joyKnob = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (!moved && change.uptimeMillis - t0 < 350) {
                                    // Tocco veloce: se si cammina liberamente e si tocca la mappa, il personaggio
                                    // raggiunge quel punto (o la persona/oggetto e ci parla); altrimenti è il tasto A.
                                    val s = snapshot
                                    val w = size.width.toFloat()
                                    val h = w * NativeCore.HEIGHT / NativeCore.WIDTH
                                    val top = (size.height - h) / 2f
                                    val p = change.position
                                    // Con la vista estesa si possono toccare anche persone e cartelli sopra e sotto l'inquadratura originale.
                                    if (s.freeRoam && (session.extendedActive || (p.y >= top && p.y < top + h))) {
                                        val tx = s.x + (p.x / (w / 10f)).toInt() - 4
                                        // floor: sopra l'inquadratura originale i valori sono negativi
                                        val ty = s.y + kotlin.math.floor((p.y - top) / (h / 9f)).toInt() - 4
                                        if (tx == s.x && ty == s.y) tapped = NativeCore.KEY_A else session.walkTo(tx, ty)
                                    } else tapped = NativeCore.KEY_A
                                }
                                break
                            }
                            var d = change.position - start
                            val dist = hypot(d.x, d.y)
                            if (dist > 24f) moved = true
                            if (dist > radius) d = d * (radius / dist)
                            joyKnob = d
                            held = when {
                                hypot(d.x, d.y) < radius * 0.28f -> 0
                                abs(d.x) > abs(d.y) -> if (d.x > 0) NativeCore.KEY_RIGHT else NativeCore.KEY_LEFT
                                else -> if (d.y > 0) NativeCore.KEY_DOWN else NativeCore.KEY_UP
                            }
                            change.consume()
                        }
                        held = 0
                        joyOrigin = null
                        pressedSince = null
                    }
                }
        ) {
            val origin = joyOrigin
            if (origin != null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color(0x6B171624), radius, origin)
                    drawCircle(Color(0x99FFF7DC), radius, origin, style = Stroke(width = 2.dp.toPx()))
                    drawCircle(Yellow, radius * 0.46f, origin + joyKnob)
                }
            }
        }

        // Domanda Sì/No del gioco: pulsanti nativi sopra il riquadro originale (a destra dell'immagine).
        if (snapshot.menu.kind == MenuKind.YES_NO) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val imageHeight = maxWidth * 0.9f
                val imageTop = (maxHeight - imageHeight) / 2
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = imageTop + imageHeight * 0.30f, end = 10.dp)
                        .width(132.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnswerButton("Sì", primary = true, enabled = !commandBusy) { session.command(GameCommand.Answer(true)) }
                    AnswerButton("No", primary = false, enabled = !commandBusy) { session.command(GameCommand.Answer(false)) }
                }
            }
        }

        // Squadra in alto, con i dati letti dalla memoria del gioco (nascosta durante la lotta).
        if (inGame && snapshot.party.isNotEmpty() && !snapshot.inBattle) {
            TeamStrip(
                snapshot.party,
                onOpen = { i -> teamSelected = i; teamOpen = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(start = 10.dp, end = 10.dp, top = 30.dp)
            )
        }

        val battleMon = snapshot.battleMon
        if (snapshot.inBattle && battleMon != null) {
            BattlePanel(
                moves = battleMon.moves.map { slot ->
                    val info = session.romData.moveInfo(slot.id)
                    MoveButtonData(
                        name = session.romData.moveName(slot.id),
                        typeName = info?.let { session.romData.typeName(it.type) } ?: "",
                        type = info?.type ?: -1,
                        pp = slot.pp,
                        maxPp = session.romData.maxPp(slot.id, slot.ppUps),
                    )
                },
                enabled = !commandBusy,
                onCommand = { session.command(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { c ->
                        val parentHeight = c.parentLayoutCoordinates?.size?.height ?: return@onGloballyPositioned
                        battleReserved = (parentHeight - c.positionInParent().y).toInt().coerceAtLeast(0)
                    }
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            )
        } else
        // Barra in basso (prototipo): i menu nativi arriveranno con il "ponte" verso il gioco.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BarButton("Squadra", AppIcons.Team, Modifier.weight(1f)) {
                if (inGame) { teamSelected = null; teamOpen = true } else message = "Prima inizia o carica una partita"
            }
            BarButton("Borsa", AppIcons.Bag, Modifier.weight(1f)) {
                if (inGame) bagOpen = true else message = "Prima inizia o carica una partita"
            }
            BarButton("Pokédex", AppIcons.Dex, Modifier.weight(1f)) {
                if (inGame) dexOpen = true else message = "Prima inizia o carica una partita"
            }
            BarButton("Menu", AppIcons.Menu, Modifier.weight(1f)) { tapped = NativeCore.KEY_START }
            AutoTextButton(autoText, Modifier.weight(1f)) { autoText = (autoText + 1) % 4 }
            BarButton("Salva", AppIcons.Save, Modifier.weight(1f)) {
                message = when {
                    !inGame -> "Prima inizia o carica una partita"
                    session.saveManual() -> "Partita salvata in questo punto"
                    else -> "Salvataggio non riuscito"
                }
            }
        }

        // SELECT serve solo dentro i menu del gioco (per spostare strumenti e mosse): compare solo lì, sopra la barra.
        if (!snapshot.freeRoam && !snapshot.inBattle && snapshot.naming == null && snapshot.menu.kind != MenuKind.MAIN_MENU) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 14.dp, bottom = 86.dp)
                    .background(Glass, RoundedCornerShape(50))
                    .border(1.dp, Color(0xFF403C62), RoundedCornerShape(50))
                    .clickable { tapped = NativeCore.KEY_SELECT }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Select, contentDescription = null, tint = Cream, modifier = Modifier.size(16.dp))
                    Text("  Select", color = Cream, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Menu iniziale del gioco (NUOVO GIOCO, OPZIONI, ev. CONTINUA): sotto il riquadro la voce "Carica partita" (D-034).
        if (snapshot.menu.kind == MenuKind.MAIN_MENU && !loadOpen) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val imageTop = (maxHeight - maxWidth * 0.9f) / 2
                val tile = maxWidth / 20
                val boxRows = 2 * (snapshot.menu.items - 1) + 4
                Box(
                    Modifier
                        .padding(top = imageTop + tile * boxRows + 10.dp, start = tile * 0.5f)
                        .width(tile * 14)
                        .height(48.dp)
                        .background(Cream, RoundedCornerShape(12.dp))
                        .border(3.dp, Ink, RoundedCornerShape(12.dp))
                        .clickable {
                            slots = session.savedSlots()
                            loadOpen = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("CARICA PARTITA", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
        if (loadOpen) {
            LoadScreen(
                entries = slots,
                onLoad = { entry ->
                    loadOpen = false
                    message = if (session.loadSlot(entry.slot)) "Partita caricata" else "Caricamento non riuscito"
                },
                onClose = { loadOpen = false }
            )
        }

        // Menu Squadra nativo (sola lettura); in lotta "Squadra" resta il comando di lotta.
        if (teamOpen && !snapshot.inBattle) {
            TeamSheet(
                party = snapshot.party,
                romData = session.romData,
                selected = teamSelected,
                onSelect = { teamSelected = it },
                onClose = { teamOpen = false }
            )
        }

        if (bagOpen && !snapshot.inBattle) {
            BagSheet(snapshot.bag, session.romData) { bagOpen = false }
        }
        if (dexOpen && !snapshot.inBattle) {
            PokedexSheet(snapshot.pokedexSeen, snapshot.pokedexOwned, session.romData) { dexOpen = false }
        }

        // Schermata dei nomi del gioco: coperta dalla tastiera del telefono.
        val naming = snapshot.naming
        if (naming != null && !namingDismissed) {
            NameEntryOverlay(naming, busy = nameRequested) { name ->
                nameRequested = true
                session.enterName(name)
            }
        }

        message?.let {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 48.dp)
                    .background(Cream, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(it, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

/**
 * Comandi di lotta sovrapposti: ogni pulsante sceglie la voce nel menu originale del gioco
 * tramite l'esecutore a circuito chiuso. Squadra e Borsa aprono i menu originali del gioco
 * (le schermate native arriveranno dopo).
 */
@Composable
private fun BattlePanel(
    moves: List<MoveButtonData>,
    enabled: Boolean,
    onCommand: (GameCommand) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 2) {
                    val index = row * 2 + col
                    val move = moves.getOrNull(index)
                    Column(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .background(Glass, RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFF403C62), RoundedCornerShape(16.dp))
                            .then(if (move != null && enabled) Modifier.clickable { onCommand(GameCommand.Fight(index)) } else Modifier)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(move?.name ?: "—", color = if (enabled) Cream else Color(0xFF7D78A0), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                        if (move != null) Row(verticalAlignment = Alignment.CenterVertically) {
                            if (move.typeName.isNotEmpty()) {
                                Text(
                                    move.typeName, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.background(typeColor(move.type), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp)
                                )
                                Box(Modifier.width(6.dp))
                            }
                            Text(if (move.maxPp != null) "PP ${move.pp}/${move.maxPp}" else "PP ${move.pp}", color = Color(0xFFAAA4C8), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BarButton("Squadra", AppIcons.Team, Modifier.weight(1f)) { if (enabled) onCommand(GameCommand.Party) }
            BarButton("Borsa", AppIcons.Bag, Modifier.weight(1f)) { if (enabled) onCommand(GameCommand.Item) }
            BarButton("Fuga", AppIcons.Run, Modifier.weight(1f)) { if (enabled) onCommand(GameCommand.Run) }
        }
    }
}

/** Dati di un pulsante mossa, letti dalla memoria (PP attuali) e dalla ROM dell'utente (nome, tipo, PP massimi). */
data class MoveButtonData(val name: String, val typeName: String, val type: Int, val pp: Int, val maxPp: Int?)

/** Colori dell'interfaccia per tipo (scelta grafica dell'app, indici dei tipi del gioco). */
internal fun typeColor(type: Int): Color = when (type) {
    0x00 -> Color(0xFFC9C3AD); 0x01 -> Color(0xFFE08A6A); 0x02 -> Color(0xFFA9B8F2); 0x03 -> Color(0xFFC48AE0)
    0x04 -> Color(0xFFD9B872); 0x05 -> Color(0xFFC9AD78); 0x07 -> Color(0xFFB5CC52); 0x08 -> Color(0xFF9A8CC8)
    0x14 -> Color(0xFFF58B5C); 0x15 -> Color(0xFF6DB3EF); 0x16 -> Color(0xFF7CC95A); 0x17 -> Color(0xFFF2C84B)
    0x18 -> Color(0xFFF08AB0); 0x19 -> Color(0xFF9EDCE6); 0x1A -> Color(0xFF8C7AE6)
    else -> Color(0xFFC9C3AD)
}

/** Testo automatico: ogni tocco passa a No → Lento → Medio → Veloce. */
@Composable
private fun AutoTextButton(speed: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(58.dp)
            .background(if (speed > 0) Yellow else Glass, RoundedCornerShape(16.dp))
            .border(1.dp, if (speed > 0) Yellow else Color(0xFF403C62), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(AppIcons.Text, contentDescription = null, tint = if (speed > 0) Ink else Cream, modifier = Modifier.size(22.dp))
        Text(
            if (speed > 0) TextAssist.LABELS[speed] else "Testo",
            color = if (speed > 0) Ink else Cream, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
        )
    }
}

@Composable
private fun AnswerButton(label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(if (primary) Yellow else Glass, RoundedCornerShape(16.dp))
            .border(1.dp, if (primary) Yellow else Color(0xFF403C62), RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (primary) Ink else Cream, fontWeight = FontWeight.Bold, fontSize = 19.sp)
    }
}

@Composable
private fun TeamStrip(party: List<PartyMember>, onOpen: (Int) -> Unit, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xC7171624), RoundedCornerShape(16.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (i in 0 until 6) {
            val m = party.getOrNull(i)
            Column(
                Modifier
                    .weight(1f)
                    .height(58.dp)
                    .background(if (m != null) Glass else Color(0x40282540), RoundedCornerShape(12.dp))
                    .then(if (m != null) Modifier.clickable { onOpen(i) } else Modifier)
                    .padding(horizontal = 5.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (m != null) {
                    Text(m.name, color = Cream, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Lv${m.level}", color = Color(0xFFAAA4C8), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        if (m.status != Status.NONE) {
                            Box(Modifier.padding(start = 4.dp).size(7.dp).background(Color(0xFFEF5B5B), CircleShape))
                        }
                    }
                    HpBar(m.hp, m.maxHp)
                }
            }
        }
    }
}

@Composable
internal fun HpBar(hp: Int, max: Int) {
    val fraction = if (max > 0) hp.toFloat() / max else 0f
    val color = when {
        fraction > 0.5f -> Color(0xFF58CF7C)
        fraction > 0.2f -> Color(0xFFF2B23A)
        else -> Color(0xFFEF5B5B)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(Color(0xFF0F0E19), RoundedCornerShape(3.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(5.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
    }
}

/** Pulsante della barra in basso (D-007, D-012): icona e nome, stessa grafica per tutti. */
@Composable
private fun BarButton(label: String, icon: ImageVector? = null, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(58.dp)
            .background(Glass, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF403C62), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Cream, modifier = Modifier.size(22.dp))
        Text(label, color = Cream, fontWeight = FontWeight.SemiBold, fontSize = if (icon != null) 11.sp else 15.sp, maxLines = 1)
    }
}
