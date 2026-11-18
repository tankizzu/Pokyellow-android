package it.giallotouch.emu

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import it.giallotouch.bridge.GameSnapshot
import it.giallotouch.bridge.GameStateReader
import it.giallotouch.bridge.MemoryMapIt
import it.giallotouch.bridge.RomData
import it.giallotouch.storage.SaveStorage
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Una partita in corso: un thread dedicato fa avanzare il gioco un fotogramma alla volta,
 * manda l'audio all'altoparlante (che fa anche da orologio) e pubblica l'immagine.
 */
class GameSession(private val storage: SaveStorage, rom: ByteArray) {

    private val handle: Long = NativeCore.nativeCreate(SAMPLE_RATE)
    private val tasks = ConcurrentLinkedQueue<Runnable>()
    @Volatile private var running = true
    @Volatile private var paused = false
    private var thread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private var lastBattery: ByteArray? = null

    /** Ultimo fotogramma completo (160×144 ARGB), letto dalla vista del gioco. */
    val latestFrame = AtomicReference(IntArray(NativeCore.WIDTH * NativeCore.HEIGHT))
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

    fun setKeys(mask: Int) = NativeCore.nativeSetKeys(handle, mask)

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
                val count = NativeCore.nativeRunFrame(handle, back, audio)
                back = latestFrame.getAndSet(back)
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
            val snapshot = GameStateReader.read(stateBlock)
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
    }
}
