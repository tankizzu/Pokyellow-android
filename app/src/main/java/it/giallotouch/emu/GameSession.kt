package it.giallotouch.emu

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import it.giallotouch.bridge.BattleLayout
import it.giallotouch.bridge.GameCommand
import it.giallotouch.bridge.ItemAction
import it.giallotouch.bridge.ItemActionExecutor
import it.giallotouch.bridge.ItemMenus
import it.giallotouch.bridge.BattleAction
import it.giallotouch.bridge.BattleActionExecutor
import it.giallotouch.bridge.BattleMenus
import it.giallotouch.bridge.CommandExecutor
import it.giallotouch.bridge.MenuReader
import it.giallotouch.bridge.MenuKind
import it.giallotouch.bridge.GameSnapshot
import it.giallotouch.bridge.GameStateReader
import it.giallotouch.bridge.FreeRoam
import it.giallotouch.bridge.MapGrid
import it.giallotouch.bridge.WalkExecutor
import it.giallotouch.bridge.MemoryMapIt
import it.giallotouch.bridge.NameCodec
import it.giallotouch.bridge.NameExecutor
import it.giallotouch.bridge.NamingReader
import it.giallotouch.bridge.RomData
import it.giallotouch.bridge.TextAssist
import it.giallotouch.bridge.TileCanvas
import it.giallotouch.storage.SaveSlot
import it.giallotouch.storage.SaveStorage
import it.giallotouch.storage.SlotEntry
import it.giallotouch.storage.SlotInfo
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Una partita in corso: un thread dedicato fa avanzare il gioco un fotogramma alla volta,
 * manda l'audio all'altoparlante (che fa anche da orologio) e pubblica l'immagine.
 */
class GameSession(private val storage: SaveStorage, private val rom: ByteArray) {

    private val handle: Long = NativeCore.nativeCreate(SAMPLE_RATE)
    private val tasks = ConcurrentLinkedQueue<Runnable>()
    @Volatile private var running = true
    @Volatile private var paused = false
    private var thread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private var lastBattery: ByteArray? = null

    /** Ultimo fotogramma completo (160×144 ARGB), letto dalla vista del gioco. */
    val latestFrame = AtomicReference(IntArray(NativeCore.WIDTH * NativeCore.HEIGHT))

    /** Vista estesa a tutto schermo: altezza richiesta dalla vista (0 = spenta) e ultimo fotogramma esteso. */
    class ExtFrame(val pixels: IntArray, var height: Int = 0, var active: Boolean = false)
    @Volatile var extendedHeight = 0
    /** Riga dell'inquadratura originale nella vista estesa: al centro più [MAP_SHIFT] pixel del gioco (scelta del committente). */
    val extendedFrameTop: Int get() = ((extendedHeight - NativeCore.HEIGHT) / 2 + MAP_SHIFT).coerceIn(0, maxOf(0, extendedHeight - NativeCore.HEIGHT))
    val latestExtended = AtomicReference(ExtFrame(IntArray(NativeCore.WIDTH * NativeCore.EXT_MAX_HEIGHT)))
    private var extBack = ExtFrame(IntArray(NativeCore.WIDTH * NativeCore.EXT_MAX_HEIGHT))
    /** true quando lo schermo mostra la mappa estesa (serve ai tocchi fuori dall'inquadratura originale). */
    @Volatile var extendedActive = false
        private set
    @Volatile var onFrame: (() -> Unit)? = null

    /** Tile del gioco (carattere, riquadri) per disegnare le aggiunte dell'app come il gioco (D-039); null all'inizio. */
    val tileSheet = AtomicReference<IntArray?>(null)
    private val sheetBuf = IntArray(TileCanvas.SHEET_SIZE)

    /** Lotta a tutto schermo: true quando la schermata di lotta va mostrata divisa (scena + riquadro del testo). */
    @Volatile var battleSplit = false
        private set
    private var splitHold = 0
    private var mainHold = 0
    private var mainTail = 0
    private var cardClosing = 0
    /** Immagine quasi tutta bianca (dissolvenza del gioco in corso). */
    private fun mostlyWhite(px: IntArray): Boolean {
        val white = px[0]
        var other = 0
        var i = 0
        while (i < px.size) { if (px[i] != white) other++; i += 37 }
        return other < px.size / 37 / 20
    }
    private var cardOpening = 0
    private var cardOpenCalm = 0
    /** La scheda allenatore si sta aprendo (l'app mostra ancora la mappa e il riquadro START, D-065). */
    @Volatile var cardOpeningActive = false
        private set
    /** L'app sta chiudendo da sola i menu del gioco (niente SELECT né altri pulsanti per quei menu). */
    @Volatile var menusClosing = false
        private set
    private var cardCalm = 0
    /** Mappa (gioco e vista estesa) salvata quando si apre la scheda allenatore (D-065). */
    private val savedMap = IntArray(NativeCore.WIDTH * NativeCore.HEIGHT)
    private val savedExt = ExtFrame(IntArray(NativeCore.WIDTH * NativeCore.EXT_MAX_HEIGHT))
    /** Immagine ferma (passaggio con grafica rovinata del gioco, D-065). */
    private var holdImage = false
    private var menuHold = 0
    private val HOLD_FRAMES = 12
    /** In lotta il gioco chiede quale Pokémon mandare in campo (dopo un KO). */
    @Volatile var battlePartyAsked = false
        private set
    /** In lotta è aperto un menu del gioco diverso da quello di lotta (squadra, borsa…). */
    @Volatile var battleGameMenu = false
        private set
    private val frameTiles = ByteArray(MenuReader.SCREEN_SIZE)
    private val frameVars = ByteArray(MenuReader.MENU_VARS_SIZE)

    /** Menu iniziale del gioco sullo schermo: numero di voci del gioco (2 o 3), 0 se non c'è (D-040). */
    @Volatile var mainMenuItems = 0
        private set
    /** Menu di lotta del gioco (LOTTA/PKMN/STRUM./FUGA o mosse) sullo schermo: l'app lo copre, ha i suoi riquadri (D-045). */
    @Volatile var battleMenuShown = false
        private set
    /**
     * Domanda Sì/No del gioco sullo schermo (D-046): riquadro del gioco in caselle (x0, y0, x1, y1), voce del cursore e
     * caselle delle due risposte (2 per riga), per ridisegnarle nei riquadri dell'app sotto il dialogo.
     */
    class YesNo(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val item: Int, val yes: IntArray, val no: IntArray)
    @Volatile var yesNo: YesNo? = null
        private set
    /** Lista dei nomi proposti dal Professore sullo schermo (caselle di ogni voce): l'app la mostra toccabile (D-052). */
    @Volatile var nameList: List<IntArray>? = null
        private set

    /**
     * Caselle utilizzabili: la lettera "A" (0x80) ha inchiostro e carta di colori diversi. Durante le dissolvenze (per
     * esempio all'uscita da una lotta) il gioco sbianca la tavolozza: quelle caselle non vanno prese, altrimenti i
     * riquadri dell'app diventerebbero invisibili.
     */
    private fun sheetReadable(sheet: IntArray): Boolean {
        val sx = (0x80 and 15) * 8; val sy = (0x80 shr 4) * 8
        val paper = sheet[(0x7F shr 4) * 8 * 128 + (0x7F and 15) * 8]
        var ink = 0
        for (y in 0 until 8) for (x in 0 until 8) if (sheet[(sy + y) * 128 + sx + x] != paper) ink++
        val c = sheet[(sy + 3) * 128 + sx + 3]
        fun lum(v: Int) = ((v shr 16) and 255) + ((v shr 8) and 255) + (v and 255)
        return ink >= 8 && kotlin.math.abs(lum(paper) - lum(sheet.firstOrNull { it != paper } ?: paper)) > 150
    }

    /**
     * Scritte e riquadri dell'app sempre in bianco e nero (D-044, D-053): in alcuni momenti (per esempio all'inizio del
     * gioco) la tavolozza del testo è colorata (gialla); le caselle si riportano a carta bianca e inchiostro nero,
     * con i grigi intermedi in base alla luminosità.
     */
    private fun normalizeSheet(sheet: IntArray) {
        for (i in sheet.indices) {
            val c = sheet[i]
            val l = (((c shr 16) and 255) * 30 + ((c shr 8) and 255) * 59 + (c and 255) * 11) / 100
            val g = when { l >= 170 -> 0xF8; l < 70 -> 0x18; l >= 120 -> 0xA8; else -> 0x58 }
            sheet[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
    }

    private val frameMask = BoxMask()
    private val extMask = BoxMask()

    /**
     * Dialogo del gioco (riquadro in basso, righe 12–17) mentre si cammina: l'app lo mostra più in alto (D-048).
     * [dialogStrip]: i pixel del riquadro (160×48) dell'ultimo fotogramma, null se non c'è un dialogo.
     */
    @Volatile var dialogStrip: IntArray? = null
        private set
    private var stripA = IntArray(NativeCore.WIDTH * 48)
    private var stripB = IntArray(NativeCore.WIDTH * 48)

    private fun dialogAtBottom(t: ByteArray): Boolean {
        fun tile(x: Int, y: Int) = t[y * 20 + x].toInt() and 0xFF
        return tile(0, 12) == 0x79 && tile(19, 12) == 0x7B && tile(0, 17) == 0x7D && tile(19, 17) == 0x7E
    }

    private fun yesNoInfo(vars: ByteArray, tiles: ByteArray, item: Int): YesNo? {
        fun t(x: Int, y: Int) = if (x in 0 until 20 && y in 0 until 18) tiles[y * 20 + x].toInt() and 0xFF else -1
        val topY = vars[0].toInt() and 0xFF; val topX = vars[1].toInt() and 0xFF
        val x0 = topX - 1
        // angolo in alto a sinistra: subito sopra la prima voce (SÌ/NO) o una riga più su (CURA/ESCI)
        val y0 = listOf(topY - 1, topY - 2).firstOrNull { t(x0, it) == 0x79 } ?: return null
        val x1 = (x0 + 1 until 20).firstOrNull { t(it, y0) == 0x7B } ?: return null
        val y1 = (y0 + 1 until 18).firstOrNull { t(x0, it) == 0x7D } ?: return null
        fun word(y: Int) = IntArray(x1 - topX - 1) { t(topX + 1 + it, y) }.dropLastWhile { it == 0x7F }.toIntArray()
        return YesNo(x0, y0, x1, y1, item, word(topY), word(topY + 2))
    }

    /** Schermata dei nomi del gioco sullo schermo: l'app non la mostra mai (copre con la sua, D-046). */
    @Volatile var namingShown = false
        private set
    /** Menu del negozio sullo schermo (D-056) e articoli in vendita (codici). */
    @Volatile var shopMenuShown = false
        private set
    @Volatile var shopItems: List<Int> = emptyList()
        private set
    /** Menu del PC del giocatore sullo schermo (D-055): l'app mostra la sua schermata STRUMENTI PC. */
    @Volatile var pcMenuShown = false
        private set
    /** Scelta del PC del Centro sullo schermo (D-058): voci del gioco (vuota se non c'è). */
    @Volatile var pcChoice: List<String> = emptyList()
        private set
    /** Menu del Box Pokémon del Centro sullo schermo (D-058), Pokémon nel box attuale e numero del box. */
    @Volatile var boxMenuShown = false
        private set
    @Volatile var boxMons: List<it.giallotouch.bridge.BoxMon> = emptyList()
        private set
    @Volatile var boxNumber = 1
        private set
    private val boxBlock = ByteArray(MemoryMapIt.BOX_BLOCK_SIZE)
    /** La scheda allenatore del gioco è sullo schermo: l'app la mostra ridisposta (D-045). */
    @Volatile var trainerCardShown = false
        private set
    /** Cursore ▶ del menu iniziale ridisegnato (indice nelle voci dell'app), mosso dalla levetta. */
    @Volatile var startMenuCursor = 0

    /** Ultimo stato letto dalla memoria del gioco (squadra, soldi, posizione), aggiornato 4 volte al secondo. */
    val latestSnapshot = AtomicReference(GameSnapshot.EMPTY)
    private val stateBlock = ByteArray(GameStateReader.BLOCK_SIZE)

    /** Dati letti dalla ROM dell'utente (nomi delle mosse), solo per la versione italiana supportata. */
    val romData: RomData = RomData.load(rom, SaveStorage.sha1(rom) == SaveStorage.SUPPORTED_SHA1)

    /**
     * Avvio come sul Game Boy (D-034): a ogni apertura il gioco riparte da capo, con il salvataggio del gioco
     * (batteria) già inserito, e mostra introduzione, titolo e menu iniziale. I salvataggi dell'app si caricano
     * da lì con "Carica partita".
     */
    init {
        NativeCore.nativeLoadRom(handle, rom)
        if (SaveStorage.sha1(rom) == SaveStorage.SUPPORTED_SHA1) NativeCore.nativeTrustRomFont(handle)
        storage.readWithBackup(storage.batteryFile)?.let {
            NativeCore.nativeLoadBattery(handle, it)
            lastBattery = it
        }
    }

    /**
     * true quando si sta giocando una partita (si è camminato sulla mappa almeno una volta, o si è caricato
     * un salvataggio): solo allora si salva in automatico all'uscita e si mostra la squadra.
     */
    @Volatile var inGame = false
        private set

    /** Tasti premuti dal giocatore (levetta, tocchi); applicati a ogni fotogramma dal thread del gioco. */
    @Volatile private var userKeys = 0
    fun setKeys(mask: Int) { userKeys = mask }

    /** Testo automatico (0 = spento, 1–3 = lento/medio/veloce) e dito tenuto premuto (testo più veloce). */
    private val textAssist = TextAssist()
    @Volatile var autoTextSpeed: Int = 0
        set(value) { field = value.coerceIn(0, 3) }

    /** Gioco in bianco e nero (opzione del committente, D-054): l'immagine si riporta a 4 grigi. */
    @Volatile var blackWhite: Boolean = false

    /** Volume 0–5 (D-046), applicato al suono del gioco. */
    @Volatile var volume: Int = 5
        set(value) { field = value.coerceIn(0, 5); runCatching { audioTrack?.setVolume(field / 5f) } }
    fun setHolding(holding: Boolean) { textAssist.holding = holding }

    /** Esecutore dei comandi di lotta: quando è occupato, i suoi tasti sostituiscono quelli del giocatore. */
    private val executor = CommandExecutor()
    @Volatile var commandBusy = false
        private set
    private val menuVars = ByteArray(MenuReader.MENU_VARS_SIZE)
    private val tileMap = ByteArray(MenuReader.SCREEN_SIZE)

    /** Chiede di eseguire un comando di lotta (scegliere una mossa, fuggire…). */
    fun command(cmd: GameCommand) {
        commandBusy = true
        tasks.add(Runnable { executor.start(cmd) })
    }

    /** Scrittura di un nome nella schermata dei nomi del gioco (tastiera del telefono, D-029). */
    private val nameExecutor = NameExecutor()
    private val nameBuffer = ByteArray(NamingReader.NAME_BUFFER_SIZE)
    @Volatile var nameBusy = false
        private set
    /** Esito dell'ultimo nome scritto: null finché lavora, true se il gioco l'ha accettato. */
    @Volatile var nameResult: Boolean? = null
        private set

    /** Chiede di scrivere [text] nella schermata dei nomi aperta; i caratteri non disponibili vengono scartati. */
    fun enterName(text: String) {
        val codes = NameCodec.encode(NameCodec.sanitize(text, 10)) ?: return
        if (codes.isEmpty()) return
        nameBusy = true
        nameResult = null
        tasks.add(Runnable { nameExecutor.start(codes) })
    }

    private fun readNaming() = run {
        NativeCore.nativeReadRange(handle, MenuReader.MENU_VARS, menuVars)
        NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
        NativeCore.nativeReadRange(handle, NamingReader.NAME_BUFFER, nameBuffer)
        NamingReader.read(menuVars, tileMap, nameBuffer)
    }

    /** Usa/Getta uno strumento passando dal menu originale STRUMENTI (menu Borsa nativo). */
    private val itemExecutor = ItemActionExecutor()
    @Volatile var itemBusy = false
        private set
    /** Esito dell'ultima azione: null mentre lavora, true se completata. */
    @Volatile var itemResult: Boolean? = null
        private set

    fun itemAction(action: ItemAction) {
        itemBusy = true
        itemResult = null
        if (action is ItemAction.OpenTrainer) {
            holdSheet = true
            // immagine della mappa di adesso, da mostrare quando si chiude la scheda (D-065)
            tasks.add(Runnable {
                System.arraycopy(latestFrame.get(), 0, savedMap, 0, savedMap.size)
                val e = latestExtended.get()
                System.arraycopy(e.pixels, 0, savedExt.pixels, 0, e.pixels.size); savedExt.height = e.height; savedExt.active = e.active
                cardOpening = 60 * 6; cardOpenCalm = 0; cardOpeningActive = true
            })
        }
        tasks.add(Runnable { walker.cancel(); itemExecutor.start(action); currentItemAction = action })
    }
    private var currentItemAction: ItemAction? = null

    /** SQUADRA e BORSA in lotta dalle liste dell'app (D-050): l'esecutore guida i menu del gioco senza mostrarli. */
    private val battleExecutor = BattleActionExecutor()
    @Volatile var battleBusy = false
        private set
    fun battleAction(a: BattleAction) {
        battleBusy = true
        tasks.add(Runnable { walker.cancel(); battleExecutor.start(a) })
    }
    private fun stepBattleAction(): Int {
        NativeCore.nativeReadRange(handle, MenuReader.MENU_VARS, menuVars)
        NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
        val scroll = NativeCore.nativeReadMemory(handle, MemoryMapIt.LIST_SCROLL_OFFSET)
        val scr = BattleMenus.read(menuVars, tileMap, scroll)
        val keys = battleExecutor.step(scr, menuVars.contentHashCode() * 31 + tileMap.contentHashCode())
        if (!battleExecutor.busy) battleBusy = false
        return keys
    }

    /**
     * Menu originali del gioco attraversati dall'app da sola (menu START, lista degli strumenti, chiusura della scheda):
     * non si vedono (D-049), resta l'immagine di prima. [trainerOpening]: fotogrammi di attesa della scheda allenatore.
     */
    private var trainerOpening = 0
    /** Dopo aver usato uno strumento su un Pokémon: si aspetta il ritorno ai menu del gioco per richiuderli. */
    @Volatile private var pendingClose = false
    private var pendingCloseFrames = 0
    private var pendingFree = 0
    private fun hidingOldMenus(): Boolean = !trainerCardShown &&
        (itemExecutor.busy || battleExecutor.busy || closingFrames > 0 || trainerOpening > 0 || (pendingClose && !textAtBottom(frameTiles)))

    private fun textAtBottom(t: ByteArray) = (t[12 * 20].toInt() and 0xFF) == 0x79 && (t[17 * 20].toInt() and 0xFF) == 0x7D

    /** Una volta per fotogramma: con [pendingClose], appena il gioco torna a un menu (lista, START) lo si chiude. */
    private fun checkPendingClose(free: Boolean) {
        if (!pendingClose) return
        // Si smette solo dopo un secondo di cammino libero: nei passaggi tra una schermata e l'altra il gioco può
        // sembrare per qualche fotogramma sulla mappa.
        pendingFree = if (free && closingFrames == 0) pendingFree + 1 else 0
        if (--pendingCloseFrames <= 0 || pendingFree >= 60) { pendingClose = false; return }
        if (closingFrames > 0) return
        val kind = ItemMenus.read(frameVars, frameTiles, 0).kind
        if (kind == ItemMenus.Kind.ITEM_LIST || kind == ItemMenus.Kind.START_MENU || kind == ItemMenus.Kind.USE_TOSS || kind == ItemMenus.Kind.PARTY) {
            closingFrames = 60 * 8; closingCalm = 0
        }
    }

    /**
     * Sulla scheda allenatore il gioco riusa le caselle dei riquadri per la cornice della scheda: finché la scheda è
     * aperta (o sta per aprirsi/chiudersi) l'app tiene le caselle lette prima (D-045).
     */
    @Volatile private var holdSheet = false

    /** INDIETRO della scheda allenatore: B finché si torna a camminare (chiude scheda e menu START). */
    fun closeMenus() { tasks.add(Runnable {
        closingFrames = 60 * 8; closingCalm = 0
        if (trainerCardShown && savedExt.height >= 0) { cardClosing = 60 * 3; cardCalm = 0 }
    }) }
    private var closingFrames = 0
    private var closingCalm = 0

    private fun stepItemAction(): Int {
        NativeCore.nativeReadRange(handle, MenuReader.MENU_VARS, menuVars)
        NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
        val scroll = NativeCore.nativeReadMemory(handle, MemoryMapIt.LIST_SCROLL_OFFSET)
        val freeRoam = NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE) == 0 && FreeRoam.screenIsMap(tileMap)
        val observation = menuVars.contentHashCode() * 31 + tileMap.contentHashCode()
        val keys = itemExecutor.step(ItemMenus.read(menuVars, tileMap, scroll), freeRoam, observation)
        if (!itemExecutor.busy) {
            itemResult = itemExecutor.result == ItemActionExecutor.Result.DONE
            itemBusy = false
            if (currentItemAction is ItemAction.OpenTrainer && itemResult == true) trainerOpening = 120
            // Usato su un Pokémon: dopo il messaggio del gioco ("…recupera 20 PS") si richiudono i menu vecchi (D-051).
            if ((currentItemAction as? ItemAction.Use)?.target != null && itemResult == true) { pendingClose = true; pendingCloseFrames = 60 * 60; pendingFree = 0 }
            currentItemAction = null
        }
        return keys
    }

    /** Camminata automatica verso un passo toccato sullo schermo (con eventuale interazione all'arrivo). */
    private val walker = WalkExecutor()

    /** Chiede di raggiungere il passo (x, y) della mappa; se è una persona o un oggetto, ci si parla. */
    fun walkTo(x: Int, y: Int) {
        tasks.add(Runnable { planWalk(x, y) })
    }

    private fun readBytes(address: Int, n: Int): ByteArray = ByteArray(n).also { NativeCore.nativeReadRange(handle, address, it) }

    /** Tocco sullo schermo mentre si cammina (D-049): parla con chi è vicino, altrimenti tasto A. */
    fun talkNearby() {
        tasks.add(Runnable { planTalk() })
    }

    private fun planTalk() {
        // Si controlla adesso, nel gioco, se si cammina davvero: lo stato letto dall'interfaccia può essere vecchio di
        // qualche fotogramma (per esempio appena parte un dialogo o il messaggio di un oggetto ricevuto). Se il gioco
        // è in un dialogo o in un menu, il tocco è sempre il tasto A: non va mai perso.
        NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
        val free = NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE) == 0 &&
            NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) == 0 && FreeRoam.screenIsMap(tileMap)
        if (!free) { pendingA = 8; return }
        val grid = MapGrid.build(::readBytes, rom) ?: run { pendingA = 8; return }
        val px = NativeCore.nativeReadMemory(handle, MemoryMapIt.X_COORD)
        val py = NativeCore.nativeReadMemory(handle, MemoryMapIt.Y_COORD)
        for (dy in -2..2) for (dx in -2..2) {
            val t = (px + dx) to (py + dy)
            if (!grid.isInteractive(t.first, t.second)) continue
            val face = grid.interactionSpots(t.first, t.second)[px to py] ?: continue
            walker.start(emptyList(), face)
            return
        }
        pendingA = 8
    }

    private fun planWalk(tx: Int, ty: Int) {
        val grid = MapGrid.build(::readBytes, rom) ?: return
        val px = NativeCore.nativeReadMemory(handle, MemoryMapIt.X_COORD)
        val py = NativeCore.nativeReadMemory(handle, MemoryMapIt.Y_COORD)
        if (grid.isInteractive(tx, ty)) {
            val spots = grid.interactionSpots(tx, ty)
            val path = grid.path(px, py, spots.keys) ?: return
            var end = px to py
            for (d in path) end = end.first + d.dx to end.second + d.dy
            walker.start(path, spots[end])
        } else {
            // Tocco nel vuoto: se accanto (o oltre un bancone) c'è qualcuno o un cartello, ci si gira e si parla;
            // altrimenti è il tasto A (D-044).
            for (dy in -2..2) for (dx in -2..2) {
                val t = (px + dx) to (py + dy)
                if (!grid.isInteractive(t.first, t.second)) continue
                val face = grid.interactionSpots(t.first, t.second)[px to py] ?: continue
                walker.start(emptyList(), face)
                return
            }
            if (TAP_WALKS_TO_GROUND && grid.isFree(tx, ty)) grid.path(px, py, setOf(tx to ty))?.let { walker.start(it, null) }
            else pendingA = 8
        }
    }

    /** Tasto A premuto da un tocco nel vuoto (fotogrammi rimasti: premuto, poi rilasciato). */
    private var pendingA = 0

    private fun currentKeys(): Int {
        if (itemExecutor.busy) return stepItemAction()
        if (battleExecutor.busy) return stepBattleAction()
        if (closingFrames > 0) {
            closingFrames--
            if (closingFrames % 24 == 0) {
                NativeCore.nativeReadRange(handle, MenuReader.MENU_VARS, menuVars)
                NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
                val menu = ItemMenus.read(menuVars, tileMap, 0).kind
                val free = NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE) == 0 && FreeRoam.screenIsMap(tileMap)
                // Il gioco torna alla mappa prima di ridisegnare il menu START: si smette dopo 3 controlli di fila in giro.
                closingCalm = if (!ItemMenus.trainerCard(tileMap) && menu == ItemMenus.Kind.OTHER && free) closingCalm + 1 else 0
                if (closingCalm >= 3) closingFrames = 0
            }
            return if (closingFrames % 24 >= 20 && closingCalm == 0) NativeCore.KEY_B else 0
        }
        // Dopo aver usato uno strumento: il giocatore manda avanti solo il messaggio; i menu del gioco li chiude l'app.
        if (pendingClose) {
            NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
            if (!textAtBottom(tileMap)) return 0
        }
        if (pendingA > 0) { pendingA--; return if (pendingA >= 4) NativeCore.KEY_A else 0 }
        if (nameExecutor.busy) {
            val keys = nameExecutor.step(readNaming())
            if (!nameExecutor.busy) {
                nameResult = nameExecutor.result == NameExecutor.Result.DONE
                nameBusy = false
            }
            return keys
        }
        if (walker.busy) {
            if (userKeys != 0) walker.cancel() else {
                val pos = WalkExecutor.Position(
                    NativeCore.nativeReadMemory(handle, MemoryMapIt.CUR_MAP),
                    NativeCore.nativeReadMemory(handle, MemoryMapIt.X_COORD),
                    NativeCore.nativeReadMemory(handle, MemoryMapIt.Y_COORD),
                )
                val gameBusy = NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE) != 0 ||
                    NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) != 0
                return walker.step(pos, gameBusy)
            }
        }
        if (!executor.busy) {
            commandBusy = false
            textAssist.autoSpeed = autoTextSpeed
            if (autoTextSpeed == 0 && !textAssist.holding) return userKeys
            NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
            return userKeys or textAssist.step(tileMap)
        }
        NativeCore.nativeReadRange(handle, MenuReader.MENU_VARS, menuVars)
        NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, tileMap)
        val inBattle = NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) != 0
        val keys = executor.step(MenuReader.read(menuVars, tileMap), inBattle)
        if (!executor.busy) commandBusy = false
        return keys
    }

    fun start() {
        if (thread != null) return
        thread = Thread({ loop() }, "gioco").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /** L'audio viene messo in pausa dal thread del gioco stesso, così non resta bloccato a metà scrittura. */
    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    /** Salvataggi dell'app presenti sul telefono (per "Carica partita"). */
    fun savedSlots(): List<SlotEntry> = storage.listSlots()

    /** Pulsante "Salva": salvataggio dell'app in questo punto. Attende fino a 3 secondi. */
    fun saveManual(): Boolean = inGame && runOnGameThread { persist(SaveSlot.MANUAL) }

    /** Uscita dall'app: salvataggio automatico (solo durante una partita) e salvataggio del gioco se cambiato. */
    fun saveNow(): Boolean = runOnGameThread { persist(if (inGame) SaveSlot.AUTO else null) }

    /** Salva, poi ferma definitivamente la sessione. */
    fun close() {
        runOnGameThread { persist(if (inGame) SaveSlot.AUTO else null) }
        running = false
        thread?.join(2000)
    }

    /**
     * "Carica partita": riporta il gioco al salvataggio scelto. Lo stato contiene anche il salvataggio del gioco
     * di quel momento (com'era la cartuccia): quello attuale viene prima copiato a parte, per sicurezza.
     */
    fun loadSlot(slot: SaveSlot): Boolean = runOnGameThread {
        val files = storage.slotFiles(slot)
        val state = storage.readWithBackup(files.state) ?: error("salvataggio mancante")
        val ok = NativeCore.nativeLoadState(handle, state) ||
            (storage.backupOf(files.state)?.let { NativeCore.nativeLoadState(handle, it) } ?: false)
        check(ok) { "salvataggio non leggibile" }
        lastBattery?.let { storage.writeAtomic(storage.batteryBeforeLoadFile, it) }
        walker.cancel(); executor.cancel(); nameExecutor.cancel(); itemExecutor.cancel(); battleExecutor.cancel(); battleBusy = false
        frameMask.reset(); extMask.reset()   // le immagini di prima appartengono a un'altra partita
        inGame = true
        readSnapshot()
    }

    private fun persist(slot: SaveSlot?) {
        if (slot != null) {
            val files = storage.slotFiles(slot)
            storage.writeAtomic(files.state, NativeCore.nativeSaveState(handle))
            val snap = latestSnapshot.get()
            val info = SlotInfo(
                savedAt = System.currentTimeMillis(),
                playerName = snap.playerName,
                money = snap.money,
                badges = Integer.bitCount(NativeCore.nativeReadMemory(handle, MemoryMapIt.BADGES)),
                party = snap.party.map { it.name to it.level },
                playMinutes = NativeCore.nativeReadMemory(handle, MemoryMapIt.PLAY_TIME_HOURS) * 60 +
                    NativeCore.nativeReadMemory(handle, MemoryMapIt.PLAY_TIME_MINUTES),
            )
            storage.writeAtomic(files.info, info.encode().toByteArray(Charsets.UTF_8))
            runCatching { storage.writeAtomic(files.thumbnail, thumbnailPng()) }
        }
        saveBatteryIfChanged()
    }

    private fun thumbnailPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(latestFrame.get(), NativeCore.WIDTH, NativeCore.HEIGHT, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun saveBatteryIfChanged() {
        val battery = NativeCore.nativeSaveBattery(handle)
        if (battery.isNotEmpty() && !battery.contentEquals(lastBattery)) {
            storage.writeAtomic(storage.batteryFile, battery)
            lastBattery = battery
        }
    }

    /** Solo per il banco di prova: mappa del luogo attuale (passaggi, persone, cartelli), letta nel filo del gioco. */
    fun mapGridForTest(): MapGrid? {
        var g: MapGrid? = null
        runOnGameThread { g = MapGrid.build(::readBytes, rom) }
        return g
    }

    /** Solo per il banco di prova: cammina fino a (x, y) con il camminatore dell'app (passi precisi). */
    fun walkPathForTest(x: Int, y: Int) {
        tasks.add(Runnable {
            val grid = MapGrid.build(::readBytes, rom) ?: return@Runnable
            val px = NativeCore.nativeReadMemory(handle, MemoryMapIt.X_COORD)
            val py = NativeCore.nativeReadMemory(handle, MemoryMapIt.Y_COORD)
            grid.path(px, py, setOf(x to y))?.let { walker.start(it, null) }
        })
    }

    /** Solo per il banco di prova: stato della chiusura automatica dei menu. */
    fun closingForTest() = "pendingClose=$pendingClose frames=$pendingCloseFrames closing=$closingFrames calm=$closingCalm item=${itemExecutor.busy}"

    /** Solo per il banco di prova: porte del luogo attuale (riga, colonna, mappa di arrivo): numero a D3B2, poi 4 byte. */
    fun warpsForTest(): List<Triple<Int, Int, Int>> {
        val b = ByteArray(1 + 4 * 32)
        runOnGameThread { NativeCore.nativeReadRange(handle, 0xD3B2, b) }
        val n = (b[0].toInt() and 0xFF).coerceAtMost(32)
        return (0 until n).map { i -> Triple(b[1 + 4 * i].toInt() and 0xFF, b[2 + 4 * i].toInt() and 0xFF, b[4 + 4 * i].toInt() and 0xFF) }
    }

    /** Solo per il banco di prova: salva lo stato del gioco in un file (partite di prova). */
    /**
     * Segnalazione (D-064): stato della partita in questo momento e una riga di informazioni (luogo, posizione,
     * schermate riconosciute) per riprodurre un difetto nel banco di prova. Null se il gioco non è avviato.
     */
    fun reportData(): Pair<ByteArray, String>? {
        var state: ByteArray? = null
        var info = ""
        runOnGameThread {
            state = NativeCore.nativeSaveState(handle)
            info = "luogo=${NativeCore.nativeReadMemory(handle, MemoryMapIt.CUR_MAP)} x=${NativeCore.nativeReadMemory(handle, MemoryMapIt.X_COORD)} " +
                "y=${NativeCore.nativeReadMemory(handle, MemoryMapIt.Y_COORD)} lotta=${NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE)}"
        }
        val st = state ?: return null
        val flags = "lottaDivisa=$battleSplit menuLotta=$battleMenuShown menuGiocoLotta=$battleGameMenu siNo=${yesNo != null} " +
            "dialogoSu=${dialogStrip != null} vistaEstesa=$extendedActive negozio=$shopMenuShown pc=$pcMenuShown box=$boxMenuShown " +
            "esecutore=${itemExecutor.busy}/${battleExecutor.busy}"
        return st to "$info\n$flags"
    }

    fun saveStateForTest(file: java.io.File): Boolean {
        var ok = false
        runOnGameThread { NativeCore.nativeSaveState(handle)?.let { file.writeBytes(it); ok = true } }
        return ok
    }

    /** Solo per il banco di prova: legge [n] byte di memoria da [address]. */
    fun memoryForTest(address: Int, n: Int): ByteArray {
        val b = ByteArray(n)
        runOnGameThread { NativeCore.nativeReadRange(handle, address, b) }
        return b
    }

    /** Solo per il banco di prova: variabili dei menu e mappa dello schermo. */
    /** Solo per il banco di prova, dal thread del gioco (onFrame): caselle dello schermo dell'ultimo fotogramma. */
    fun frameTilesForTest(): ByteArray = frameTiles.copyOf()

    /** Solo per il banco di prova: con [stepPermits] il gioco avanza di un fotogramma per ogni permesso. */
    @Volatile var stepPermits: java.util.concurrent.Semaphore? = null
    @Volatile var stepDone: java.util.concurrent.Semaphore? = null

    fun screenForTest(): Pair<ByteArray, ByteArray> {
        val v = ByteArray(MenuReader.MENU_VARS_SIZE); val t = ByteArray(MenuReader.SCREEN_SIZE)
        runOnGameThread { NativeCore.nativeReadRange(handle, MenuReader.MENU_VARS, v); NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, t) }
        return v to t
    }

    /** Solo per il banco di prova: luogo e posizione del giocatore (mappa, x, y). */
    fun positionForTest(): Triple<Int, Int, Int> {
        var p = Triple(0, 0, 0)
        runOnGameThread {
            p = Triple(NativeCore.nativeReadMemory(handle, MemoryMapIt.CUR_MAP), NativeCore.nativeReadMemory(handle, MemoryMapIt.X_COORD),
                NativeCore.nativeReadMemory(handle, MemoryMapIt.Y_COORD))
        }
        return p
    }

    private fun runOnGameThread(action: () -> Unit): Boolean {
        if (thread == null || !running) {
            return runCatching { action() }.isSuccess
        }
        val done = CountDownLatch(1)
        var ok = false
        tasks.add(Runnable {
            ok = runCatching { action() }.onFailure { Log.e(TAG, "Errore", it) }.isSuccess
            done.countDown()
        })
        return done.await(3, TimeUnit.SECONDS) && ok
    }

    private fun loop() {
        val track = createAudioTrack()
        audioTrack = track
        runCatching { track?.setVolume(volume / 5f) }
        val audio = ShortArray(8192)
        var back = IntArray(NativeCore.WIDTH * NativeCore.HEIGHT)
        var frames = 0L
        var nextFrameAt = SystemClock.elapsedRealtimeNanos()
        try {
            while (running) {
                while (true) tasks.poll()?.run() ?: break
                if (paused) {
                    if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
                    Thread.sleep(16)
                    nextFrameAt = SystemClock.elapsedRealtimeNanos()
                    continue
                }
                if (track != null && track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                // Banco di prova: un fotogramma alla volta, quando il test lo chiede (controllo dei lampeggi).
                stepPermits?.let { p -> if (!p.tryAcquire(200, java.util.concurrent.TimeUnit.MILLISECONDS)) continue }
                NativeCore.nativeSetKeys(handle, currentKeys())
                val count = NativeCore.nativeRunFrame(handle, back, audio)
                // Ogni fotogramma: schermata di lotta divisa e menu iniziale ridisegnato (in sincronia con l'immagine).
                NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, frameTiles)
                NativeCore.nativeReadRange(handle, MenuReader.MENU_VARS, frameVars)
                val inBattleRaw = NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) != 0
                var split = inBattleRaw && BattleLayout.textBoxAtBottom(frameTiles)
                val frameMenu = MenuReader.read(frameVars, frameTiles)
                // Menu iniziale ridisegnato: resta finché il riquadro del gioco è ancora sullo schermo (dopo la scelta il
                // menu del gioco perde il cursore per qualche fotogramma ma si vede ancora, D-065).
                val mainRaw = if (frameMenu.kind == MenuKind.MAIN_MENU) frameMenu.items else 0
                // L'immagine segue la memoria dello schermo con qualche fotogramma di ritardo: si aspettano altri 4 fotogrammi.
                if (mainRaw > 0) { mainMenuItems = mainRaw; mainHold = 30; mainTail = 4 }
                else if (mainMenuItems > 0 && mainHold > 0 && (frameTiles[0].toInt() and 0xFF) == 0x79) mainHold--
                else if (mainMenuItems > 0 && mainTail > 0) mainTail--
                else { mainMenuItems = 0; mainHold = 0 }
                val menuRaw = frameMenu.kind == MenuKind.BATTLE_MAIN || frameMenu.kind == MenuKind.BATTLE_MOVES
                // In lotta, un altro menu del gioco con il cursore (squadra, borsa, cambio): si mostra il gioco intero e
                // i riquadri di lotta dell'app spariscono (D-049).
                var gameMenu = inBattleRaw && !menuRaw && frameTiles.any { (it.toInt() and 0xFF) == 0xED }
                // Il gioco chiede un Pokémon (dopo un KO): l'app mostra la sua lista SQUADRA sopra la lotta (D-054).
                battlePartyAsked = gameMenu && !battleExecutor.busy &&
                    BattleMenus.read(frameVars, frameTiles, 0).kind == BattleMenus.Kind.PARTY
                // Mentre l'app guida i menu di lotta del gioco si continua a mostrare la lotta (immagine di prima).
                if (battleExecutor.busy || battlePartyAsked) { gameMenu = false; split = true }
                if (gameMenu) split = false
                // Niente lampeggi (D-061): quando il gioco ridisegna il riquadro del testo o il menu di lotta, per qualche
                // fotogramma non si riconoscono; la disposizione della lotta e i riquadri dell'app restano finché il
                // cambio non dura davvero (12 fotogrammi, 0,2 s). Un menu del gioco aperto vale subito.
                if (split) splitHold = HOLD_FRAMES else if (inBattleRaw && !gameMenu && splitHold > 0) { splitHold--; split = true } else splitHold = 0
                var menuShown = menuRaw
                if (menuRaw) menuHold = HOLD_FRAMES else if (inBattleRaw && !gameMenu && menuHold > 0) { menuHold--; menuShown = true } else menuHold = 0
                battleSplit = split
                battleMenuShown = menuShown
                battleGameMenu = gameMenu
                // Scheda allenatore: chiudendola il gioco ricarica per qualche fotogramma la grafica della mappa (immagine
                // rovinata). Si tiene ferma l'ultima immagine della scheda finché la mappa non è stabile (D-065).
                // Chiusura: si mostra subito la mappa salvata all'apertura della scheda, finché la mappa vera non è stabile.
                val cardRaw = ItemMenus.trainerCard(frameTiles)
                val mapNow = NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE) == 0 && FreeRoam.screenIsMap(frameTiles)
                if (cardClosing > 0) {
                    cardCalm = if (mapNow && !cardRaw && closingFrames == 0) cardCalm + 1 else 0
                    cardClosing = if (cardCalm >= 8) 0 else cardClosing - 1
                }
                // Apertura: la mappa salvata resta finché la scheda non è completa (4 fotogrammi di fila) o l'esecutore
                // non si è fermato senza riuscirci.
                if (cardOpening > 0) {
                    cardOpenCalm = if (cardRaw && !mostlyWhite(back)) cardOpenCalm + 1 else 0
                    cardOpening = if (cardOpenCalm >= 4 || (!itemExecutor.busy && itemResult == false)) 0 else cardOpening - 1
                }
                cardOpeningActive = cardOpening > 0
                menusClosing = closingFrames > 0 || cardClosing > 0 || pendingClose
                holdImage = cardClosing > 0 || cardOpening > 0
                trainerCardShown = cardRaw && !holdImage
                val itemKind = ItemMenus.read(frameVars, frameTiles, 0).kind
                pcMenuShown = itemKind == ItemMenus.Kind.PC_MENU && closingFrames == 0
                shopMenuShown = itemKind == ItemMenus.Kind.SHOP_MENU && closingFrames == 0
                boxMenuShown = itemKind == ItemMenus.Kind.BOX_MENU && closingFrames == 0
                pcChoice = if (itemKind == ItemMenus.Kind.PC_CHOICE && closingFrames == 0)
                    (0 until 4).map { i -> it.giallotouch.bridge.TextCodec.decode(ByteArray(13) { frameTiles[(2 + 2 * i) * 20 + 2 + it] }).trim() }
                        .filter { it.isNotEmpty() } else emptyList()
                if (boxMenuShown || itemExecutor.busy && (itemKind == ItemMenus.Kind.ITEM_LIST || itemKind == ItemMenus.Kind.BOX_SUB)) {
                    NativeCore.nativeReadRange(handle, MemoryMapIt.BOX_COUNT, boxBlock)
                    boxMons = it.giallotouch.bridge.BoxReader.read(boxBlock)
                    // "BOX N° n" nel riquadro in basso a destra (riga 16, colonne 17–18).
                    val d1 = frameTiles[16 * 20 + 17].toInt() and 0xFF; val d2 = frameTiles[16 * 20 + 18].toInt() and 0xFF
                    if (boxMenuShown && d2 in 0xF6..0xFF) boxNumber = (if (d1 in 0xF6..0xFF) (d1 - 0xF6) * 10 else 0) + d2 - 0xF6
                }
                if (shopMenuShown) {
                    val b = ByteArray(12); NativeCore.nativeReadRange(handle, MemoryMapIt.SHOP_LIST, b)
                    val n = (b[0].toInt() and 0xFF).coerceAtMost(10)
                    shopItems = (1..n).map { b[it].toInt() and 0xFF }.takeWhile { it != 0xFF }
                }
                yesNo = if (frameMenu.kind == MenuKind.YES_NO) yesNoInfo(frameVars, frameTiles, frameMenu.item) else null
                nameList = if (frameMenu.kind == MenuKind.NAME_LIST) MenuReader.nameListEntries(frameTiles, frameMenu.items) else null
                namingShown = NamingReader.looksLikeNaming(frameTiles)
                val yn = yesNo?.takeIf { !trainerCardShown }   // anche in lotta (riquadro del gioco sulla scena)
                var dialog = !battleSplit && !trainerCardShown && !namingShown && mainMenuItems == 0 &&
                    NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) == 0 && dialogAtBottom(frameTiles)
                // Senza un'immagine di prima del dialogo (partita caricata a dialogo aperto) il dialogo resta al suo posto.
                val onScreen = dialog || yn != null || nameList != null || shopMenuShown || pcChoice.isNotEmpty() || hidingOldMenus() ||
                    ((NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) != 0 || pendingClose) && (frameTiles[1 * 20 + 4].toInt() and 0xFF) == 0x71)
                if (dialog && !frameMask.ready) dialog = false
                if (dialog) {
                    System.arraycopy(back, 96 * NativeCore.WIDTH, stripA, 0, stripA.size)
                    dialogStrip = stripA
                    val t = stripA; stripA = stripB; stripB = t
                } else dialogStrip = null
                if (trainerOpening > 0) trainerOpening = if (trainerCardShown) 0 else trainerOpening - 1
                checkPendingClose(NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE) == 0 && FreeRoam.screenIsMap(frameTiles))
                val hide = hidingOldMenus()
                val boxes = buildList {
                    // In lotta si copre solo la scena (righe 0–11): il testo del gioco ("…si è ripreso di 8!") resta.
                    // Anche la schermata della squadra che il gioco mostra dopo una Pozione resta nascosta.
                    val inBattleNow = NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) != 0
                    val partyScreen = (frameTiles[1 * 20 + 4].toInt() and 0xFF) == 0x71 && (inBattleNow || pendingClose)
                    if ((hide || partyScreen) && frameMask.ready) add(if (inBattleNow) BoxMask.Box(0, 0, 19, 11) else BoxMask.Box(0, 0, 19, 17))
                    if (yn != null && frameMask.ready) add(BoxMask.Box(yn.x0, yn.y0, yn.x1, yn.y1))
                    if (nameList != null && frameMask.ready) add(BoxMask.Box(0, 0, 10, 11))
                    if (pcChoice.isNotEmpty() && frameMask.ready) add(BoxMask.Box(0, 0, 15, 9))
                    if (shopMenuShown && frameMask.ready) { add(BoxMask.Box(0, 0, 10, 6)); add(BoxMask.Box(11, 0, 19, 2)) }
                    if (dialog) add(BoxMask.Box(0, 12, 19, 17))
                }
                frameMask.apply(back, NativeCore.HEIGHT, 0, boxes, boxesOnScreen = onScreen || holdImage)
                if (holdImage) {
                    System.arraycopy(savedMap, 0, back, 0, back.size)
                    back = latestFrame.getAndSet(back)
                } else back = latestFrame.getAndSet(back)
                val eh = extendedHeight
                if (holdImage) {
                    if (savedExt.height > 0) {
                        System.arraycopy(savedExt.pixels, 0, extBack.pixels, 0, savedExt.pixels.size)
                        extBack.height = savedExt.height; extBack.active = savedExt.active; extendedActive = savedExt.active
                        extBack = latestExtended.getAndSet(extBack)
                    }
                } else if (eh > NativeCore.HEIGHT) {
                    val active = NativeCore.nativeRenderExtended(handle, extBack.pixels, eh, extendedFrameTop) == 1
                    if (active) extMask.apply(extBack.pixels, eh, extendedFrameTop, boxes, frameMask.older, boxesOnScreen = onScreen)
                    extBack.height = eh
                    extBack.active = active
                    extendedActive = active
                    extBack = latestExtended.getAndSet(extBack)
                } else extendedActive = false
                onFrame?.invoke()
                frames++
                if (frames % AUTOSAVE_FRAMES == 0L) runCatching { saveBatteryIfChanged() }
                if (frames % SNAPSHOT_FRAMES == 0L) readSnapshot()
                stepDone?.release()
                if (holdSheet && !trainerCardShown && !itemExecutor.busy && closingFrames == 0 && latestSnapshot.get().freeRoam) holdSheet = false
                if (frames % SHEET_FRAMES == 0L && !holdSheet && !trainerCardShown && NativeCore.nativeGameTiles(handle, sheetBuf) >= 0 &&
                    sheetReadable(sheetBuf)) {
                    normalizeSheet(sheetBuf)
                    val old = tileSheet.get()
                    if (old == null || !old.contentEquals(sheetBuf)) tileSheet.set(sheetBuf.copyOf())
                }
                if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    // La scrittura si blocca quando il buffer è pieno: tiene il gioco alla velocità giusta.
                    track.write(audio, 0, count)
                } else {
                    nextFrameAt += FRAME_NANOS
                    val wait = nextFrameAt - SystemClock.elapsedRealtimeNanos()
                    if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                    else nextFrameAt = SystemClock.elapsedRealtimeNanos()
                }
            }
            while (true) tasks.poll()?.run() ?: break
        } finally {
            track?.release()
            audioTrack = null
            NativeCore.nativeDestroy(handle)
        }
    }

    private fun readSnapshot() {
        runCatching {
            NativeCore.nativeReadRange(handle, MemoryMapIt.BLOCK_START, stateBlock)
            val naming = readNaming()
            val base = GameStateReader.read(stateBlock)
            val joyIgnore = NativeCore.nativeReadMemory(handle, MemoryMapIt.JOY_IGNORE)
            val freeRoam = joyIgnore == 0 && !base.inBattle && FreeRoam.screenIsMap(tileMap)
            // In gioco solo con la mappa vera riconosciuta (vista estesa attiva): durante l'animazione iniziale
            // e il titolo lo schermo può sembrare "libero" ma non è una partita.
            if (freeRoam && extendedActive) inGame = true
            val snapshot = base.copy(
                menu = MenuReader.read(menuVars, tileMap), freeRoam = freeRoam, naming = naming,
                cursorOnScreen = tileMap.any { (it.toInt() and 0xFF) == 0xED },
            )
            if (snapshot != latestSnapshot.get()) latestSnapshot.set(snapshot)
        }.onFailure { Log.e(TAG, "Lettura stato non riuscita", it) }
    }

    private fun createAudioTrack(): AudioTrack? = runCatching {
        val min = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(maxOf(min, SAMPLE_RATE * 4 / 12))
            .build()
    }.onFailure { Log.e(TAG, "Audio non disponibile", it) }.getOrNull()

    companion object {
        private const val TAG = "GameSession"
        const val SAMPLE_RATE = 48000
        /** Il Game Boy disegna circa 59,73 fotogrammi al secondo. */
        private const val FRAME_NANOS = 16_742_706L
        private const val AUTOSAVE_FRAMES = 60L * 20
        private const val SNAPSHOT_FRAMES = 15L
        private const val SHEET_FRAMES = 30L
        /** La mappa è 5 caselle (40 pixel del gioco) più in basso del centro, per lasciare posto alla squadra in alto. */
        const val MAP_SHIFT = 40
        /** Scelta del committente nel Laboratorio (D-012): toccare il terreno NON fa camminare. */
        private const val TAP_WALKS_TO_GROUND = false
    }
}
