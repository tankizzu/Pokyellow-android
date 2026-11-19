package it.battito.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Audio del gioco: un thread dedicato genera in tempo reale musica (SongPlayer) ed effetti (Synth)
 * e li scrive su un AudioTrack. Il gioco manda comandi tramite una coda, senza bloccarsi.
 */
class AudioEngine {
    private val sr = 44100
    private val song = SongPlayer(sr)
    private val sfx = Synth(sr)

    /** 0..1, regolati dalle impostazioni. Applicati con curva quadratica (più naturale all'orecchio). */
    @Volatile var musicVolume = 0.8f
    @Volatile var sfxVolume = 0.8f

    private val commands = ConcurrentLinkedQueue<() -> Unit>()
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "battito-audio").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    // ───── comandi (chiamati dal thread del gioco) ─────

    fun playFromStart() = commands.add {
        song.clearLoop(); song.seek(0f); song.playing = true
    }

    /** Riprende il brano da un punto preciso (dopo la pausa), senza ripetizione. */
    fun playFrom(sec: Float) = commands.add {
        song.clearLoop(); song.seek(sec); song.playing = true
    }

    fun playMenuLoop() = commands.add {
        if (!song.looping || !song.playing) {
            val from = Song.MENU_LOOP_FROM_BAR * Song.BAR_SEC
            song.setLoop(from, Song.MENU_LOOP_TO_BAR * Song.BAR_SEC)
            song.seek(from); song.playing = true
        }
    }

    fun stopMusic() = commands.add { song.playing = false; song.synth.allOff() }
    fun pauseMusic() = commands.add { song.playing = false }
    fun resumeMusic() = commands.add { song.playing = true }

    fun death() = commands.add { sfx.trigger(Inst.SFX_DEATH, 0f, 1f, 0.7f) }
    fun blip() = commands.add { sfx.trigger(Inst.SFX_BLIP, 76f, 1f, 0.2f) }
    fun phase(toCyan: Boolean) = commands.add {
        sfx.trigger(Inst.SFX_PHASE, if (toCyan) 76f else 83f, 1f, 0.14f, if (toCyan) 1f else -0.5f)
    }
    fun win() = commands.add { sfx.trigger(Inst.SFX_WIN, 81f, 1f, 1.6f) }
    fun click() = commands.add { sfx.trigger(Inst.SFX_CLICK, 93f, 1f, 0.05f) }

    // ───── thread audio ─────

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val frames = 512
        val minBytes = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sr)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBytes, frames * 2 * 4 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: Exception) {
            running = false
            return
        }

        val music = FloatArray(frames * 2)
        val fx = FloatArray(frames * 2)
        track.play()
        try {
            while (running) {
                while (true) { (commands.poll() ?: break).invoke() }
                song.render(music, frames)
                sfx.render(fx, 0, frames)
                val mv = musicVolume * musicVolume
                val sv = sfxVolume * sfxVolume
                for (i in music.indices) music[i] = (music[i] * mv + fx[i] * sv).coerceIn(-1f, 1f)
                track.write(music, 0, music.size, AudioTrack.WRITE_BLOCKING)
            }
        } finally {
            track.pause(); track.flush(); track.release()
        }
    }
}
