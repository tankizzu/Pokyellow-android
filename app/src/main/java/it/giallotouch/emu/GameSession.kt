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
    private val frameTiles = ByteArray(MenuReader.SCREEN_SIZE)
    private val frameVars = ByteArray(MenuReader.MENU_VARS_SIZE)

    /** Menu iniziale del gioco sullo schermo: numero di voci del gioco (2 o 3), 0 se non c'è (D-040). */
    @Volatile var mainMenuItems = 0
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
        tasks.add(Runnable { walker.cancel(); itemExecutor.start(action) })
    }

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
        } else if (TAP_WALKS_TO_GROUND && grid.isFree(tx, ty)) {
            grid.path(px, py, setOf(tx to ty))?.let { walker.start(it, null) }
        }
    }

    private fun currentKeys(): Int {
        if (itemExecutor.busy) return stepItemAction()
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
        walker.cancel(); executor.cancel(); nameExecutor.cancel(); itemExecutor.cancel()
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
                NativeCore.nativeSetKeys(handle, currentKeys())
                val count = NativeCore.nativeRunFrame(handle, back, audio)
                back = latestFrame.getAndSet(back)
                // Ogni fotogramma: schermata di lotta divisa e menu iniziale ridisegnato (in sincronia con l'immagine).
                NativeCore.nativeReadRange(handle, MemoryMapIt.TILE_MAP, frameTiles)
                NativeCore.nativeReadRange(handle, MenuReader.MENU_VARS, frameVars)
                battleSplit = NativeCore.nativeReadMemory(handle, MemoryMapIt.IS_IN_BATTLE) != 0 && BattleLayout.textBoxAtBottom(frameTiles)
                mainMenuItems = MenuReader.read(frameVars, frameTiles).let { if (it.kind == MenuKind.MAIN_MENU) it.items else 0 }
                val eh = extendedHeight
                if (eh > NativeCore.HEIGHT) {
                    val active = NativeCore.nativeRenderExtended(handle, extBack.pixels, eh, extendedFrameTop) == 1
                    extBack.height = eh
                    extBack.active = active
                    extendedActive = active
                    extBack = latestExtended.getAndSet(extBack)
                } else extendedActive = false
                onFrame?.invoke()
                frames++
                if (frames % AUTOSAVE_FRAMES == 0L) runCatching { saveBatteryIfChanged() }
                if (frames % SNAPSHOT_FRAMES == 0L) readSnapshot()
                if (frames % SHEET_FRAMES == 0L && NativeCore.nativeGameTiles(handle, sheetBuf) >= 0) {
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
            if (freeRoam) inGame = true
            val snapshot = base.copy(menu = MenuReader.read(menuVars, tileMap), freeRoam = freeRoam, naming = naming)
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
