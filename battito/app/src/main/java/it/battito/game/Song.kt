package it.battito.game

/**
 * "Primo Battito" — brano originale composto per il gioco (La minore, 128 BPM, 48 battute = 90 s).
 * Giro di accordi: Lam – Fa – Do – Sol. Ogni battuta corrisponde a 16 colonne del livello.
 *
 *  battute  0– 3  Intro        pad che si apre, arpeggio, charleston
 *  battute  4– 7  Crescendo    cassa, basso, rullata e "riser" finale
 *  battute  8–23  Drop A       tutto: cassa, clap, basso pompato, melodia
 *  battute 24–31  Pausa        pad, arpeggio, melodia morbida, nuova rullata
 *  battute 32–43  Drop B       come il drop A con melodia raddoppiata all'ottava
 *  battute 44–47  Outro        si spegne
 */
object Song {
    const val BPM = 128f
    const val BARS = 48
    const val STEP_SEC = 60f / BPM / 4f          // una semicroma
    const val BAR_SEC = STEP_SEC * 16f
    const val LENGTH_SEC = BARS * BAR_SEC + 2f    // + coda del riverbero

    /** Sezione da ripetere nel menu (la pausa melodica). */
    const val MENU_LOOP_FROM_BAR = 24
    const val MENU_LOOP_TO_BAR = 32

    class Note(val time: Float, val inst: Int, val midi: Float, val vel: Float, val dur: Float, val param: Float = 0f, val pan: Float = 0f)

    private enum class Part { INTRO, BUILD, DROP_A, BREAK, DROP_B, OUTRO }

    private fun part(bar: Int) = when {
        bar < 4 -> Part.INTRO
        bar < 8 -> Part.BUILD
        bar < 24 -> Part.DROP_A
        bar < 32 -> Part.BREAK
        bar < 44 -> Part.DROP_B
        else -> Part.OUTRO
    }

    // Accordi (note MIDI)          Lam            Fa             Do             Sol
    private val padChords = arrayOf(intArrayOf(57, 60, 64), intArrayOf(53, 57, 60), intArrayOf(55, 60, 64), intArrayOf(55, 59, 62))
    private val arpChords = arrayOf(intArrayOf(69, 72, 76, 81), intArrayOf(65, 69, 72, 77), intArrayOf(67, 72, 76, 79), intArrayOf(67, 71, 74, 79))
    private val bassRoots = intArrayOf(33, 29, 36, 31)

    /** Melodia di 8 battute: (battuta, semicroma d'inizio, durata in semicrome, nota). */
    private val melody: List<IntArray> = run {
        val am = listOf(intArrayOf(0, 2, 69), intArrayOf(2, 2, 64), intArrayOf(4, 2, 69), intArrayOf(6, 2, 71), intArrayOf(8, 4, 72), intArrayOf(12, 2, 71), intArrayOf(14, 2, 69))
        val f = listOf(intArrayOf(0, 2, 69), intArrayOf(2, 2, 65), intArrayOf(4, 2, 69), intArrayOf(6, 2, 72), intArrayOf(8, 4, 69), intArrayOf(12, 2, 67), intArrayOf(14, 2, 65))
        val c = listOf(intArrayOf(0, 2, 67), intArrayOf(2, 2, 64), intArrayOf(4, 2, 67), intArrayOf(6, 2, 72), intArrayOf(8, 4, 72), intArrayOf(12, 2, 74), intArrayOf(14, 2, 76))
        val g1 = listOf(intArrayOf(0, 4, 74), intArrayOf(4, 4, 71), intArrayOf(8, 4, 67), intArrayOf(12, 4, 71))
        val g2 = listOf(intArrayOf(0, 2, 74), intArrayOf(2, 2, 72), intArrayOf(4, 2, 71), intArrayOf(6, 2, 67), intArrayOf(8, 4, 71), intArrayOf(12, 4, 64))
        val bars = listOf(am, f, c, g1, am, f, c, g2)
        bars.flatMapIndexed { b, notes -> notes.map { intArrayOf(b, it[0], it[1], it[2]) } }
    }

    fun build(): List<Note> {
        val out = ArrayList<Note>()
        fun at(bar: Int, step: Float) = (bar * 16 + step) * STEP_SEC
        fun add(bar: Int, step: Float, inst: Int, midi: Float, vel: Float, durSteps: Float, param: Float = 0f, pan: Float = 0f) {
            out += Note(at(bar, step), inst, midi, vel, durSteps * STEP_SEC, param, pan)
        }

        for (bar in 0 until BARS) {
            val p = part(bar)
            val ch = bar % 4
            val isDrop = p == Part.DROP_A || p == Part.DROP_B
            val rollBar = bar == 7 || bar == 31

            // ── Pad ──
            val cutoff = when (p) {
                Part.INTRO -> 450f + bar * 300f
                Part.BUILD -> 1500f + (bar - 4) * 200f
                Part.DROP_A -> 2300f
                Part.BREAK -> 1300f + (bar - 24) * 120f
                Part.DROP_B -> 2700f
                Part.OUTRO -> 1600f - (bar - 44) * 300f
            }
            val padVel = when (p) {
                Part.OUTRO -> 1.4f - (bar - 44) * 0.25f
                Part.INTRO, Part.BREAK -> 1.5f
                else -> 1f
            }
            for (n in padChords[ch]) add(bar, 0f, Inst.PAD, n.toFloat(), padVel, 15.5f, cutoff)
            if (bar == BARS - 1) for (n in padChords[0]) add(bar + 1, 0f, Inst.PAD, n.toFloat(), 0.5f, 8f, 900f)

            // ── Arpeggio in semicrome ──
            val (arpVel, bright) = when (p) {
                Part.INTRO -> (0.9f + bar * 0.1f) to (0.2f + bar * 0.12f)
                Part.BUILD -> 0.75f to 0.65f
                Part.DROP_A -> 0.45f to 0.85f
                Part.BREAK -> 0.8f to 0.55f
                Part.DROP_B -> 0.5f to 1f
                Part.OUTRO -> (0.6f - (bar - 44) * 0.13f) to 0.4f
            }
            val pattern = intArrayOf(0, 1, 2, 3, 2, 1, 2, 3)
            for (s in 0 until 16) {
                val n = arpChords[ch][pattern[s % 8]]
                add(bar, s.toFloat(), Inst.ARP, n.toFloat(), arpVel * (if (s % 4 == 0) 1f else 0.75f), 1f, bright, if (s % 2 == 0) -0.45f else 0.45f)
            }

            // ── Basso in crome con salto d'ottava ──
            if (p == Part.BUILD || isDrop) {
                for (s in 0 until 16 step 2) {
                    if (rollBar && s >= 8) break
                    val n = bassRoots[ch] + if (s % 4 == 2) 12 else 0
                    add(bar, s.toFloat(), Inst.BASS, n.toFloat(), if (p == Part.BUILD) 0.8f else 1f, 1.7f)
                }
            }
            if (p == Part.BREAK && bar >= 28) {
                add(bar, 0f, Inst.BASS, bassRoots[ch].toFloat(), 0.7f, 15f)
            }

            // ── Cassa ──
            if (p == Part.BUILD || isDrop) {
                for (s in 0 until 16 step 4) {
                    if (rollBar && s >= 8) break
                    add(bar, s.toFloat(), Inst.KICK, 0f, 1f, 1f)
                }
            }
            if (bar == 44) add(bar, 0f, Inst.KICK, 0f, 1f, 1f)

            // ── Clap sui tempi 2 e 4 ──
            if (isDrop) {
                add(bar, 4f, Inst.CLAP, 0f, 0.9f, 1f, pan = -0.1f)
                add(bar, 12f, Inst.CLAP, 0f, 0.9f, 1f, pan = 0.1f)
                if (bar % 8 == 7) add(bar, 15f, Inst.CLAP, 0f, 0.5f, 1f, pan = 0.2f)
            }
            // rullata di preparazione
            if (rollBar) {
                for (s in 0 until 16) {
                    if (s < 8 && s % 2 == 1) continue
                    add(bar, s.toFloat(), Inst.CLAP, 0f, 0.3f + 0.7f * s / 15f, 1f, pan = if (s % 2 == 0) -0.2f else 0.2f)
                }
                add(bar, 0f, Inst.RISER, 0f, 1f, 16f)
            }

            // ── Charleston ──
            when (p) {
                Part.INTRO -> if (bar >= 2) for (s in 2 until 16 step 4) add(bar, s.toFloat(), Inst.HAT, 0f, 0.6f, 1f, pan = 0.3f)
                Part.BUILD -> for (s in 2 until 16 step 4) add(bar, s.toFloat(), Inst.HAT, 0f, 0.8f, 1f, pan = 0.3f)
                Part.DROP_A -> {
                    for (s in 2 until 16 step 4) add(bar, s.toFloat(), Inst.OHAT, 0f, 0.7f, 1f, pan = 0.25f)
                    for (s in 0 until 16 step 4) add(bar, s.toFloat() + 1f, Inst.HAT, 0f, 0.35f, 1f, pan = -0.3f)
                }
                Part.DROP_B -> {
                    for (s in 2 until 16 step 4) add(bar, s.toFloat(), Inst.OHAT, 0f, 0.75f, 1f, pan = 0.25f)
                    for (s in 0 until 16) if (s % 4 != 2) add(bar, s.toFloat(), Inst.HAT, 0f, if (s % 2 == 1) 0.45f else 0.25f, 1f, pan = -0.3f)
                }
                else -> {}
            }

            // ── Piatti ──
            if (bar == 8 || bar == 16 || bar == 32 || bar == 40) add(bar, 0f, Inst.CRASH, 0f, 1f, 1f)
            if (bar == 24 || bar == 44) add(bar, 0f, Inst.CRASH, 0f, 0.6f, 1f)
        }

        // ── Melodia ──
        fun playMelody(fromBar: Int, bars: Int, vel: Float, octaveDouble: Boolean) {
            for (m in melody) {
                if (m[0] >= bars) continue
                val bar = fromBar + m[0]
                add(bar, m[1].toFloat(), Inst.LEAD, m[3].toFloat(), vel, m[2] - 0.3f, pan = -0.1f)
                if (octaveDouble) add(bar, m[1].toFloat(), Inst.LEAD, m[3] + 12f, vel * 0.45f, m[2] - 0.3f, pan = 0.25f)
            }
        }
        playMelody(8, 8, 1f, false)
        playMelody(16, 8, 1f, false)
        playMelody(28, 4, 0.55f, false)
        playMelody(32, 8, 1f, true)
        playMelody(40, 4, 1f, true)

        out.sortBy { it.time }
        return out
    }
}

/**
 * Sequencer: legge la partitura e la suona sul [Synth] campione per campione.
 * Si può spostare in qualunque punto (ripartenza dopo la morte) e ripetere un tratto (menu).
 */
class SongPlayer(private val sr: Int) {
    val synth = Synth(sr).apply { masterGain = 0.82f }
    private val notes = Song.build()
    private val starts = IntArray(notes.size) { (notes[it].time * sr).toInt() }
    private val length = (Song.LENGTH_SEC * sr).toInt()

    var playing = false
    var pos = 0; private set
    private var idx = 0
    private var loopFrom = -1
    private var loopTo = -1

    val positionSec: Float get() = pos.toFloat() / sr

    /** hard = true: taglia le note che suonano (ripartenza); false: le lascia sfumare (ripetizione). */
    fun seek(sec: Float, hard: Boolean = true) {
        pos = (sec * sr).toInt().coerceIn(0, length)
        idx = starts.indexOfFirst { it >= pos }.let { if (it < 0) starts.size else it }
        if (hard) {
            synth.allOff()
            synth.clearTails()
        }
    }

    fun setLoop(fromSec: Float, toSec: Float) { loopFrom = (fromSec * sr).toInt(); loopTo = (toSec * sr).toInt() }
    fun clearLoop() { loopFrom = -1; loopTo = -1 }
    val looping: Boolean get() = loopFrom >= 0

    /** Scrive [frames] campioni stereo. Se il brano non suona, scrive silenzio. */
    fun render(out: FloatArray, frames: Int) {
        var done = 0
        while (done < frames) {
            if (!playing) {
                out.fill(0f, done * 2, frames * 2)
                return
            }
            while (idx < notes.size && starts[idx] <= pos) {
                val n = notes[idx]
                synth.trigger(n.inst, n.midi, n.vel, n.dur, n.param, n.pan)
                idx++
            }
            var n = frames - done
            if (idx < notes.size) n = minOf(n, starts[idx] - pos)
            if (loopTo >= 0) n = minOf(n, loopTo - pos)
            n = minOf(n, length - pos)
            if (n <= 0) {
                if (loopTo >= 0 && pos >= loopTo) { seek(loopFrom.toFloat() / sr, hard = false); continue }
                if (pos >= length) { playing = false; continue }
                n = 1
            }
            synth.render(out, done, n)
            pos += n
            done += n
        }
    }
}
