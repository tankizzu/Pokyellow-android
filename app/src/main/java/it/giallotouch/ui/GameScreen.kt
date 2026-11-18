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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import it.giallotouch.bridge.TileCanvas
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import it.giallotouch.bridge.GameCommand
import it.giallotouch.bridge.ItemAction
import it.giallotouch.bridge.StartMenuLayout
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
import kotlinx.coroutines.launch
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
 * - prima di giocare nessun pulsante dell'app; durante il gioco un pulsante laterale (destra o sinistra) apre i pulsanti:
 *   Squadra, Borsa, Pokédex (menu nativi), Menu (START), Testo auto, Salva (salvataggio dell'app) (D-037);
 *   Select compare in basso solo quando è aperto un menu del gioco;
 * - nel menu iniziale del gioco: "Carica partita" per i salvataggi dell'app.
 */
@Composable
fun GameScreen(session: GameSession, settings: Settings) {
    var held by remember { mutableStateOf(0) }       // direzione tenuta dalla levetta
    var startCursor by remember { mutableStateOf(0) }   // cursore ▶ del menu iniziale ridisegnato (D-040)
    var panelCursor by remember { mutableStateOf(0) }   // cursore ▶ nel riquadro dei pulsanti (D-041)
    var panelOpen by remember { mutableStateOf(false) }   // pulsanti aperti con MENU
    var pressedSince by remember { mutableStateOf<Long?>(null) }  // dito appoggiato (per il testo più veloce)
    var autoText by remember { mutableStateOf(settings.autoTextSpeed) }
    var tapped by remember { mutableStateOf(0) }     // tasti premuti brevemente (A, B, START, SELECT)
    var joyOrigin by remember { mutableStateOf<Offset?>(null) }
    var joyKnob by remember { mutableStateOf(Offset.Zero) }
    var message by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val radius = with(density) { 58.dp.toPx() }

    LaunchedEffect(held, tapped) {
        val items = session.mainMenuItems
        if (panelOpen && held != 0) {
            // Pulsanti aperti: la levetta sposta il cursore nella griglia 2×3 invece di camminare.
            panelCursor = when (held) {
                NativeCore.KEY_LEFT, NativeCore.KEY_RIGHT -> panelCursor xor 1
                NativeCore.KEY_UP -> (panelCursor + 4) % 6
                else -> (panelCursor + 2) % 6
            }
            session.setKeys(tapped)
        } else if (items > 0 && held != 0) {
            // Menu iniziale ridisegnato: su/giù spostano il cursore ▶ nell'ordine delle voci sullo schermo.
            val n = StartMenuLayout.entries(items).size
            if (held == NativeCore.KEY_DOWN) startCursor = (startCursor + 1) % n
            if (held == NativeCore.KEY_UP) startCursor = (startCursor + n - 1) % n
            session.setKeys(tapped)
        } else session.setKeys(held or tapped)
    }
    LaunchedEffect(startCursor) { session.startMenuCursor = startCursor }
    LaunchedEffect(autoText) { session.autoTextSpeed = autoText; settings.autoTextSpeed = autoText }
    var volume by remember { mutableStateOf(settings.volume) }
    LaunchedEffect(volume) { session.volume = volume; settings.volume = volume }
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
    var teamSelected by remember { mutableStateOf<Int?>(null) }  // scheda aperta (null = elenco)
    var bagOpen by remember { mutableStateOf(false) }     // menu Borsa nativo
    var dexOpen by remember { mutableStateOf(false) }     // Pokédex nativo
    var itemRequested by remember { mutableStateOf(false) }  // Usa/Getta in corso nel menu del gioco
    var optionsOpen by remember { mutableStateOf(false) }   // OPZIONI nello stile del gioco
    var trainerCard by remember { mutableStateOf(false) }   // scheda allenatore del gioco sullo schermo (D-045)
    var yesNo by remember { mutableStateOf<GameSession.YesNo?>(null) }   // domanda Sì/No del gioco (D-046)
    var battleMenu by remember { mutableStateOf(false) }   // menu di lotta del gioco sullo schermo (si può scegliere)
    var battleGameMenu by remember { mutableStateOf(false) }   // in lotta: squadra/borsa del gioco aperte
    /** Voce del riquadro dei pulsanti (D-046): 0 POKéMON, 1 allenatore, 2 POKéDEX, 3 OPZIONI, 4 BORSA, 5 SALVA. */
    fun panelActivate(i: Int) {
        panelCursor = i
        panelOpen = false
        when (i) {
            0 -> { teamSelected = null; teamOpen = true }
            1 -> { itemRequested = true; session.itemAction(ItemAction.OpenTrainer) }
            2 -> dexOpen = true
            3 -> optionsOpen = true
            4 -> bagOpen = true
            else -> message = if (session.saveManual()) "Partita salvata" else "Salvataggio non riuscito"
        }
    }
    fun startMenuChoose(entry: StartMenuLayout.Entry, items: Int) {
        startCursor = StartMenuLayout.entries(items).indexOf(entry).coerceAtLeast(0)
        val game = StartMenuLayout.gameIndex(entry, items)
        if (game == null) { slots = session.savedSlots(); loadOpen = true } else session.command(GameCommand.MainMenu(game))
    }
    var sheet by remember { mutableStateOf<IntArray?>(null) }   // tile del gioco per le aggiunte "come il gioco"
    // Tastiera del telefono per i nomi (D-029): in attesa di risposta dal gioco, o nascosta dopo un errore.
    var nameRequested by remember { mutableStateOf(false) }
    var namingDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(session) {
        var lastMainMenu = 0
        while (true) {
            // All'apertura del menu iniziale il cursore ▶ parte dalla prima voce.
            val mm = session.mainMenuItems
            if (mm > 0 && lastMainMenu == 0) startCursor = 0
            lastMainMenu = mm
            snapshot = session.latestSnapshot.get()
            commandBusy = session.commandBusy
            inGame = session.inGame
            trainerCard = session.trainerCardShown
            yesNo = session.yesNo
            battleMenu = session.battleMenuShown
            battleGameMenu = session.battleGameMenu
            session.tileSheet.get()?.let { if (it !== sheet) sheet = it }
            if (itemRequested && !session.itemBusy) {
                itemRequested = false
                if (session.itemResult == false) message = "Non riuscito: usa il menu del gioco"
            }
            if (nameRequested && !session.nameBusy) {
                nameRequested = false
                if (session.nameResult == false) {
                    namingDismissed = true
                    message = "Nome non scritto: usa la tastiera del gioco"
                }
            }
            delay(100)
        }
    }
    val namingOpen = snapshot.naming != null
    LaunchedEffect(namingOpen) { if (!namingOpen) namingDismissed = false }

    BackHandler { if (panelOpen) panelOpen = false else tapped = NativeCore.KEY_B }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Immagine del gioco, ridisegnata a ogni fotogramma dello schermo dentro Compose (sotto i pulsanti dell'app).
        val renderer = remember(session) { GameRenderer(session) }
        var frameTick by remember { mutableStateOf(0L) }
        DisposableEffect(session) {
            // Il motore avvisa a ogni fotogramma nuovo; si chiede un ridisegno sul thread principale (al massimo uno in attesa).
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            val pending = java.util.concurrent.atomic.AtomicBoolean(false)
            session.onFrame = { if (pending.compareAndSet(false, true)) main.post { pending.set(false); frameTick++ } }
            onDispose { session.onFrame = null }
        }
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") frameTick
            renderer.setSize(size.width.toInt(), size.height.toInt(), this.density)
            renderer.battleReservedBottomPx = battleReserved
            drawIntoCanvas { renderer.draw(it.nativeCanvas) }
        }

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
                                // Tocco già usato da un riquadro dell'app (mossa, pulsante…): niente tasto A in più.
                                if (change.isConsumed) break
                                if (!moved && change.uptimeMillis - t0 < 350 && panelOpen) {
                                    panelActivate(panelCursor)   // tocco fuori dal riquadro = conferma (tasto A)
                                } else if (!moved && change.uptimeMillis - t0 < 350 && session.mainMenuItems > 0) {
                                    val items = session.mainMenuItems
                                    val w = size.width.toFloat()
                                    val unit = w / NativeCore.WIDTH
                                    val top = (size.height - w * NativeCore.HEIGHT / NativeCore.WIDTH) / 2f
                                    val p = change.position
                                    val row = kotlin.math.floor((p.y - top) / (8 * unit)).toInt()
                                    val col = (p.x / (8 * unit)).toInt()
                                    val entry = StartMenuLayout.entryAt(row, col, items)
                                        ?: StartMenuLayout.entries(items).getOrNull(startCursor)
                                    if (entry != null) startMenuChoose(entry, items)
                                } else if (!moved && change.uptimeMillis - t0 < 350) {
                                    // Tocco veloce: se si cammina liberamente e si tocca la mappa, il personaggio
                                    // raggiunge quel punto (o la persona/oggetto e ci parla); altrimenti è il tasto A.
                                    val s = snapshot
                                    val w = size.width.toFloat()
                                    val h = w * NativeCore.HEIGHT / NativeCore.WIDTH
                                    // Inquadratura originale: al centro, oppure dove la mette la vista estesa (spostata in basso).
                                    val top = if (session.extendedActive && session.extendedHeight > 0) {
                                        val unit = w / NativeCore.WIDTH
                                        (size.height - session.extendedHeight * unit) / 2f + session.extendedFrameTop * unit
                                    } else (size.height - h) / 2f
                                    val p = change.position
                                    // Con la vista estesa si possono toccare anche persone e cartelli sopra e sotto l'inquadratura originale.
                                    // Il personaggio si muove solo con la levetta (D-049): un tocco ovunque, vicino a una
                                    // persona o a un cartello, fa partire il dialogo; altrimenti è il tasto A.
                                    if (s.freeRoam) session.talkNearby() else tapped = NativeCore.KEY_A
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
        val yn = yesNo
        if (yn != null && sheet != null && !snapshot.inBattle) {
            // Domanda Sì/No (D-046): due riquadri del gioco sotto il dialogo, con le parole e il cursore del gioco.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val unit = with(density) { maxWidth.toPx() } / 160f
                val tilePx = 8 * unit
                listOf(true to yn.yes, false to yn.no).forEachIndexed { k, (answer, word) ->
                    val w = maxOf(6, word.size + 4)
                    TileImage(
                        sheet!!, w, 5, unit, key = Triple(word.toList(), yn.item, k),
                        modifier = Modifier
                            .offset { IntOffset(((if (k == 0) 2 else 12) * tilePx).toInt(), (37 * tilePx).toInt()) }
                            .pointerInput(answer, commandBusy) { detectTapGestures { if (!commandBusy) session.command(GameCommand.Answer(answer)) } }
                    ) {
                        box(0, 0, w - 1, 4)
                        word.forEachIndexed { i, t -> tile(t, 2 + i, 2) }
                    }
                }
            }
        } else if (snapshot.menu.kind == MenuKind.YES_NO) {
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
        val battleMon = snapshot.battleMon
        if (snapshot.inBattle && battleGameMenu) {
            // Squadra o borsa del gioco aperte in lotta: si usa il menu del gioco, senza i riquadri dell'app.
        } else if (snapshot.inBattle && battleMon != null && sheet != null) {
            GameBattlePanel(
                sheet = sheet!!,
                moves = battleMon.moves.map { slot ->
                    val max = session.romData.maxPp(slot.id, slot.ppUps)
                    session.romData.moveName(slot.id) to (if (max != null) "${slot.pp}/$max" else "${slot.pp}")
                },
                enabled = !commandBusy && battleMenu,
                onAdvance = { if (!commandBusy) tapped = NativeCore.KEY_A },
                onMove = { session.command(GameCommand.Fight(it)) },
                onParty = { session.command(GameCommand.Party) },
                onBag = { session.command(GameCommand.Item) },
                onRun = { session.command(GameCommand.Run) },
            )
        } else if (snapshot.inBattle && battleMon != null) {
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
        } else if (inGame && sheet != null && !trainerCard && (snapshot.freeRoam || panelOpen)) {
            // Pulsanti durante il gioco (D-040): MENU in basso; aperto, squadra in alto e POKéDEX/BORSA/OPZIONI/SALVA.
            GamePanel(
                sheet = sheet!!,
                open = panelOpen,
                cursor = panelCursor,
                party = snapshot.party,
                romData = session.romData,
                onToggle = { panelOpen = !panelOpen; panelCursor = 0 },
                onActivate = { panelActivate(it) },
                onMember = { i -> panelOpen = false; teamSelected = i; teamOpen = true },
                playerName = snapshot.playerName,
            )
        }
        if (optionsOpen && sheet != null) {
            BackHandler { optionsOpen = false }
            GameOptions(
                sheet = sheet!!,
                autoText = autoText,
                onAutoText = { autoText = (autoText + 1) % 4 },
                volume = volume,
                onVolume = { volume = it },
                onBack = { optionsOpen = false },
            )
        }

        // SELECT (D-043): solo quando sullo schermo c'è un menu del gioco con il cursore ▶ (non nei dialoghi),
        // disegnato con i tile del gioco, in basso a destra.
        if (inGame && snapshot.cursorOnScreen && yesNo == null && !snapshot.inBattle && snapshot.naming == null && sheet != null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val unit = with(density) { maxWidth.toPx() } / 160f
                val rows = (with(density) { maxHeight.toPx() } / (8 * unit)).toInt()
                TileImage(
                    sheet!!, 8, 4, unit, key = "select",
                    modifier = Modifier
                        .offset { IntOffset((12 * 8 * unit).toInt(), ((rows - 4) * 8 * unit).toInt()) }
                        .pointerInput(Unit) { detectTapGestures { tapped = NativeCore.KEY_SELECT } }
                ) { box(0, 0, 7, 3); text("SELECT", 1, 2) }
            }
        }

        if (loadOpen) {
            val tiles = sheet
            if (tiles != null) {
                GameLoadScreen(
                    sheet = tiles,
                    entries = slots,
                    onLoad = { entry ->
                        loadOpen = false
                        message = if (session.loadSlot(entry.slot)) "Partita caricata" else "Caricamento non riuscito"
                    },
                    onClose = { loadOpen = false }
                )
            } else {
                LoadScreen(
                    entries = slots,
                    onLoad = { entry ->
                        loadOpen = false
                        message = if (session.loadSlot(entry.slot)) "Partita caricata" else "Caricamento non riuscito"
                    },
                    onClose = { loadOpen = false }
                )
            }
        }

        // Menu Squadra nativo (sola lettura); in lotta "Squadra" resta il comando di lotta.
        if (teamOpen && !snapshot.inBattle && sheet != null) {
            // SQUADRA e scheda del Pokémon nello stile del gioco (D-046).
            val sel = teamSelected
            val member = sel?.let { snapshot.party.getOrNull(it) }
            if (member != null) GameMonScreen(sheet!!, member, session.romData) { teamSelected = null }
            else GameTeamScreen(
                sheet = sheet!!, party = snapshot.party, romData = session.romData,
                onOpen = { teamSelected = it },
                onSwap = { _, _ -> message = "Spostare i Pokémon arriverà presto" },
                onClose = { teamOpen = false },
            )
        } else if (teamOpen && !snapshot.inBattle) {
            TeamSheet(
                party = snapshot.party,
                romData = session.romData,
                selected = teamSelected,
                onSelect = { teamSelected = it },
                onClose = { teamOpen = false }
            )
        }

        if (bagOpen && !snapshot.inBattle && sheet != null) {
            GameBagScreen(
                sheet = sheet!!,
                bag = snapshot.bag,
                romData = session.romData,
                onUse = { slot, item ->
                    bagOpen = false
                    itemRequested = true
                    session.itemAction(ItemAction.Use(slot, session.romData.itemName(item.id)))
                },
                onToss = { slot, item ->
                    bagOpen = false
                    itemRequested = true
                    session.itemAction(ItemAction.Toss(slot, session.romData.itemName(item.id), 1))
                },
                onSwap = { from, to ->
                    bagOpen = false
                    itemRequested = true
                    session.itemAction(ItemAction.Swap(from, session.romData.itemName(snapshot.bag[from].id), to))
                },
                onClose = { bagOpen = false }
            )
        } else if (bagOpen && !snapshot.inBattle) {
            BagSheet(
                bag = snapshot.bag,
                romData = session.romData,
                onUse = { slot, item ->
                    bagOpen = false
                    itemRequested = true
                    session.itemAction(ItemAction.Use(slot, session.romData.itemName(item.id)))
                },
                onToss = { slot, item, quantity ->
                    bagOpen = false
                    itemRequested = true
                    session.itemAction(ItemAction.Toss(slot, session.romData.itemName(item.id), quantity))
                },
                onClose = { bagOpen = false }
            )
        }
        if (dexOpen && !snapshot.inBattle && sheet != null) {
            GameDexScreen(sheet!!, snapshot.pokedexSeen, snapshot.pokedexOwned, session.romData) { dexOpen = false }
        } else if (dexOpen && !snapshot.inBattle) {
            PokedexSheet(snapshot.pokedexSeen, snapshot.pokedexOwned, session.romData) { dexOpen = false }
        }

        // Scheda allenatore del gioco ridisposta: riquadri dell'app sopra; INDIETRO chiude scheda e menu START (B, B).
        if (trainerCard && sheet != null) {
            GameTrainerExtras(sheet!!, snapshot.pokedexSeen.size, snapshot.pokedexOwned.size, snapshot.party.size) { session.closeMenus() }
        }

        // Schermata dei nomi del gioco: coperta dalla tastiera del telefono.
        val naming = snapshot.naming
        if (naming != null && !namingDismissed) {
            val tiles = sheet
            if (tiles != null) {
                GameNameEntry(tiles, naming, busy = nameRequested) { name ->
                    nameRequested = true
                    session.enterName(name)
                }
            } else {
                NameEntryOverlay(naming, busy = nameRequested) { name ->
                    nameRequested = true
                    session.enterName(name)
                }
            }
        }

        message?.let { text ->
            val sh = sheet
            if (sh != null) {
                // Messaggi dell'app nello stile del gioco (D-049): riquadro alla riga del dialogo, righe da 18 lettere.
                val lines = wrapGameText(text, 18).take(2)
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val unit = with(density) { maxWidth.toPx() } / 160f
                    TileImage(
                        sh, 20, 6, unit, key = text,
                        modifier = Modifier.offset { IntOffset(0, (GameRenderer.DIALOG_ROW * 8 * unit).toInt()) }
                    ) {
                        box(0, 0, 19, 5)
                        lines.forEachIndexed { i, l -> text(l, 1, 2 + 2 * i) }
                    }
                }
            } else Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 48.dp)
                    .background(Cream, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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

/** Divide un testo in righe di al massimo [width] lettere, andando a capo tra le parole. */
fun wrapGameText(text: String, width: Int): List<String> {
    val lines = mutableListOf<String>()
    var cur = ""
    for (w in text.split(' ')) {
        cur = when {
            cur.isEmpty() -> w.take(width)
            cur.length + 1 + w.length <= width -> "$cur $w"
            else -> { lines += cur; w.take(width) }
        }
    }
    if (cur.isNotEmpty()) lines += cur
    return lines
}
