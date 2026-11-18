package it.giallotouch.emu

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import it.giallotouch.bridge.GameCommand
import it.giallotouch.bridge.CommandExecutor
import it.giallotouch.bridge.MenuReader
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
import it.giallotouch.storage.SaveStorage
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
    val latestExtended = AtomicReference(ExtFrame(IntArray(NativeCore.WIDTH * NativeCore.EXT_MAX_HEIGHT)))
    private var extBack = ExtFrame(IntArray(NativeCore.WIDTH * NativeCore.EXT_MAX_HEIGHT))
    /** true quando lo schermo mostra la mappa estesa (serve ai tocchi fuori dall'inquadratura originale). */
    @Volatile var extendedActive = false
        private set
    @Volatile var onFrame: (() -> Unit)? = null

    /** Ultimo stato letto dalla memoria del gioco (squadra, soldi, posizione), aggiornato 4 volte al secondo. */
    val latestSnapshot = AtomicReference(GameSnapshot.EMPTY)
    private val stateBlock = ByteArray(GameStateReader.BLOCK_SIZE)

    /** Dati letti dalla ROM dell'utente (nomi delle mosse), solo per la versione italiana supportata. */
    val romData: RomData = RomData.load(rom, SaveStorage.sha1(rom) == SaveStorage.SUPPORTED_SHA1)

    /** true se all'avvio è stata ripresa la partita dal punto esatto in cui era stata lasciata. */
    var resumedFromState = false
        private set

    init {
        NativeCore.nativeLoadRom(handle, rom)
        storage.readWithBackup(storage.batteryFile)?.let {
            NativeCore.nativeLoadBattery(handle, it)
            lastBattery = it
        }
        val state = storage.readWithBackup(storage.stateFile)
        if (state != null) {
            resumedFromState = NativeCore.nativeLoadState(handle, state)
            if (!resumedFromState) {
                storage.backupOf(storage.stateFile)?.let { resumedFromState = NativeCore.nativeLoadState(handle, it) }
            }
        }
    }

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

    /** Salva subito stato completo e salvataggio del gioco. Attende fino a 3 secondi. */
    fun saveNow(): Boolean = runOnGameThread {
        persist()
    }

    /** Salva, poi ferma definitivamente la sessione. */
    fun close() {
        runOnGameThread { persist() }
        running = false
        thread?.join(2000)
    }

    private fun persist() {
        storage.writeAtomic(storage.stateFile, NativeCore.nativeSaveState(handle))
        saveBatteryIfChanged()
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
                val eh = extendedHeight
                if (eh > NativeCore.HEIGHT) {
                    val active = NativeCore.nativeRenderExtended(handle, extBack.pixels, eh) == 1
                    extBack.height = eh
                    extBack.active = active
                    extendedActive = active
                    extBack = latestExtended.getAndSet(extBack)
                } else extendedActive = false
                onFrame?.invoke()
                frames++
                if (frames % AUTOSAVE_FRAMES == 0L) runCatching { saveBatteryIfChanged() }
                if (frames % SNAPSHOT_FRAMES == 0L) readSnapshot()
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
        /** Scelta del committente nel Laboratorio (D-012): toccare il terreno NON fa camminare. */
        private const val TAP_WALKS_TO_GROUND = false
    }
}
