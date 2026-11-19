package it.battito.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/** Strumenti del sintetizzatore. */
object Inst {
    const val KICK = 0
    const val CLAP = 1
    const val HAT = 2
    const val OHAT = 3
    const val CRASH = 4
    const val RISER = 5
    const val BASS = 6
    const val PAD = 7
    const val LEAD = 8
    const val ARP = 9

    // effetti sonori
    const val SFX_DEATH = 20
    const val SFX_BLIP = 21
    const val SFX_PHASE = 22
    const val SFX_WIN = 23
    const val SFX_CLICK = 24
}

/**
 * Sintetizzatore polifonico minimale, in Kotlin puro (nessuna dipendenza da Android),
 * così lo stesso codice suona sul telefono e si può provare/registrare sul computer.
 * Uscita stereo interlacciata in Float [-1, 1].
 *
 * Tre "bus": secco (batteria, effetti), compresso dalla cassa (basso, pad: effetto "pompa")
 * e mandata al delay ping-pong (melodia, arpeggio).
 */
class Synth(private val sr: Int) {

    private class Voice {
        var active = false
        var inst = 0
        var t = 0            // campioni trascorsi
        var dur = 0          // durata della nota in campioni
        var end = 0          // la voce si spegne qui (nota + rilascio)
        var freq = 0f
        var vel = 0f
        var param = 0f
        var ph1 = 0f; var ph2 = 0f; var ph3 = 0f
        var lp1 = 0f; var lp2 = 0f; var lp3 = 0f; var lp4 = 0f
        var seed = 22222
        var panL = 0.707f; var panR = 0.707f

        fun noise(): Float {
            var x = seed
            x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
            seed = x
            return (x and 0xFFFF) / 32768f - 1f
        }
    }

    private val voices = Array(64) { Voice() }
    private var seedCounter = 1

    private val block = 512
    private val dryL = FloatArray(block); private val dryR = FloatArray(block)
    private val duckL = FloatArray(block); private val duckR = FloatArray(block)
    private val sendL = FloatArray(block); private val sendR = FloatArray(block)

    // delay ping-pong a 3/16 di battuta (croma puntata a 128 BPM)
    private val delayLen = (sr * 60f / 128f * 0.75f).toInt()
    private val delL = FloatArray(delayLen); private val delR = FloatArray(delayLen)
    private var delIdx = 0
    private var delDampL = 0f; private var delDampR = 0f

    private var kickAge = Int.MAX_VALUE / 2

    var masterGain = 1f

    fun allOff() {
        for (v in voices) v.active = false
        kickAge = Int.MAX_VALUE / 2
    }

    fun clearTails() {
        delL.fill(0f); delR.fill(0f); delDampL = 0f; delDampR = 0f
    }

    /** midi: numero di nota MIDI (69 = La 440 Hz). dur in secondi. */
    fun trigger(inst: Int, midi: Float, vel: Float, durSec: Float, param: Float = 0f, pan: Float = 0f) {
        val v = voices.firstOrNull { !it.active } ?: voices.minByOrNull { if (it.inst == Inst.PAD) Int.MAX_VALUE else -it.t } ?: return
        v.active = true
        v.inst = inst
        v.t = 0
        v.dur = (durSec * sr).toInt().coerceAtLeast(1)
        v.end = v.dur + (release(inst) * sr).toInt()
        v.freq = (440.0 * 2.0.pow((midi - 69.0) / 12.0)).toFloat()
        v.vel = vel
        v.param = param
        v.ph1 = 0f; v.ph2 = 0.33f; v.ph3 = 0.66f
        v.lp1 = 0f; v.lp2 = 0f; v.lp3 = 0f; v.lp4 = 0f
        v.seed = 0x9E3779B1.toInt() xor (seedCounter++ * 7919)
        val a = (pan.coerceIn(-1f, 1f) + 1f) * (PI.toFloat() / 4f)
        v.panL = kotlin.math.cos(a); v.panR = kotlin.math.sin(a)
        if (inst == Inst.KICK) kickAge = 0
    }

    private fun release(inst: Int): Float = when (inst) {
        Inst.PAD -> 0.45f
        Inst.LEAD -> 0.08f
        Inst.BASS -> 0.02f
        else -> 0f
    }

    /** Scrive [frames] campioni stereo in [out] a partire dal frame [offset]. */
    fun render(out: FloatArray, offset: Int, frames: Int) {
        var done = 0
        while (done < frames) {
            val n = minOf(block, frames - done)
            renderBlock(out, offset + done, n)
            done += n
        }
    }

    private fun renderBlock(out: FloatArray, offset: Int, n: Int) {
        dryL.fill(0f, 0, n); dryR.fill(0f, 0, n)
        duckL.fill(0f, 0, n); duckR.fill(0f, 0, n)
        sendL.fill(0f, 0, n); sendR.fill(0f, 0, n)

        for (v in voices) if (v.active) renderVoice(v, n)

        val inv = 1f / sr
        for (i in 0 until n) {
            // compressione "a pompa" comandata dalla cassa
            val ka = kickAge * inv
            val duck = 1f - 0.6f * exp(-ka * 9f)
            kickAge++

            // delay ping-pong con smorzamento delle alte
            val rl = delL[delIdx]; val rr = delR[delIdx]
            val inMono = (sendL[i] + sendR[i]) * 0.5f
            delDampL += 0.35f * (rr - delDampL)
            delDampR += 0.35f * (rl - delDampR)
            delL[delIdx] = inMono + delDampL * 0.42f
            delR[delIdx] = delDampR * 0.42f
            delIdx++; if (delIdx >= delayLen) delIdx = 0

            var l = dryL[i] + duckL[i] * duck + sendL[i] + rl * 0.32f
            var r = dryR[i] + duckR[i] * duck + sendR[i] + rr * 0.32f
            l = softClip(l * masterGain); r = softClip(r * masterGain)
            out[(offset + i) * 2] = l
            out[(offset + i) * 2 + 1] = r
        }
    }

    private fun softClip(x: Float): Float {
        val a = abs(x)
        if (a < 0.75f) return x
        val y = 0.75f + 0.25f * tanh((a - 0.75f) / 0.25f)
        return if (x < 0) -y else y
    }

    // ───────────── oscillatori con correzione anti-aliasing (PolyBLEP) ─────────────

    private fun blep(t: Float, dt: Float): Float = when {
        t < dt -> { val x = t / dt; x + x - x * x - 1f }
        t > 1f - dt -> { val x = (t - 1f) / dt; x * x + x + x + 1f }
        else -> 0f
    }

    private fun saw(ph: Float, dt: Float) = 2f * ph - 1f - blep(ph, dt)

    private fun square(ph: Float, dt: Float): Float {
        var s = if (ph < 0.5f) 1f else -1f
        s += blep(ph, dt)
        var p2 = ph + 0.5f; if (p2 >= 1f) p2 -= 1f
        s -= blep(p2, dt)
        return s
    }

    private fun coef(hz: Float): Float = (2f * PI.toFloat() * hz / sr).coerceAtMost(0.99f)

    private fun renderVoice(v: Voice, n: Int) {
        val inv = 1f / sr
        val twoPi = 2f * PI.toFloat()
        for (i in 0 until n) {
            if (v.t >= v.end) { v.active = false; return }
            val ts = v.t * inv
            var l: Float
            var r: Float
            when (v.inst) {
                Inst.KICK -> {
                    if (ts > 0.42f) { v.active = false; return }
                    val f = 46f + 125f * exp(-ts * 32f)
                    v.ph1 += f * inv; if (v.ph1 >= 1f) v.ph1 -= 1f
                    var s = sin(twoPi * v.ph1) * exp(-ts * 6.5f)
                    if (ts < 0.006f) s += v.noise() * 0.25f * (1f - ts / 0.006f)
                    s *= v.vel * 0.85f
                    dryL[i] += s; dryR[i] += s
                    v.t++; continue
                }
                Inst.CLAP -> {
                    if (ts > 0.35f) { v.active = false; return }
                    val nz = v.noise()
                    v.lp1 += 0.18f * (nz - v.lp1)
                    val hp = nz - v.lp1
                    v.lp2 += 0.55f * (hp - v.lp2)
                    val env = if (ts < 0.033f) exp(-(ts % 0.011f) * 260f) else exp(-(ts - 0.033f) * 17f)
                    val tone = sin(twoPi * 185f * ts) * exp(-ts * 40f) * 0.35f
                    val s = (v.lp2 * env * 1.1f + tone) * v.vel * 0.5f
                    l = s * v.panL * 1.41f; r = s * v.panR * 1.41f
                    dryL[i] += l; dryR[i] += r
                    v.t++; continue
                }
                Inst.HAT, Inst.OHAT -> {
                    val decay = if (v.inst == Inst.HAT) 60f else 10f
                    if (ts > (if (v.inst == Inst.HAT) 0.1f else 0.45f)) { v.active = false; return }
                    val nz = v.noise()
                    v.lp1 += 0.45f * (nz - v.lp1)
                    val s = (nz - v.lp1) * exp(-ts * decay) * v.vel * 0.2f
                    dryL[i] += s * v.panL * 1.41f; dryR[i] += s * v.panR * 1.41f
                    v.t++; continue
                }
                Inst.CRASH -> {
                    if (ts > 2.4f) { v.active = false; return }
                    val a = v.noise(); val b = v.noise()
                    v.lp1 += 0.5f * (a - v.lp1); v.lp2 += 0.5f * (b - v.lp2)
                    val env = exp(-ts * 2.3f) * v.vel * 0.2f
                    dryL[i] += (a - v.lp1) * env; dryR[i] += (b - v.lp2) * env
                    v.t++; continue
                }
                Inst.RISER -> {
                    val p = (v.t.toFloat() / v.dur).coerceAtMost(1f)
                    val a = v.noise(); val b = v.noise()
                    val c = 0.01f + 0.5f * p * p
                    v.lp1 += c * (a - v.lp1); v.lp2 += c * (b - v.lp2)
                    val env = p * p * v.vel * 0.3f
                    dryL[i] += v.lp1 * env; dryR[i] += v.lp2 * env
                    v.t++; continue
                }
                Inst.BASS -> {
                    val dt1 = v.freq * inv
                    v.ph1 += dt1; if (v.ph1 >= 1f) v.ph1 -= 1f
                    val dt2 = v.freq * 1.004f * inv
                    v.ph2 += dt2; if (v.ph2 >= 1f) v.ph2 -= 1f
                    val raw = (saw(v.ph1, dt1) + saw(v.ph2, dt2)) * 0.5f
                    val c = coef(160f + 1300f * exp(-ts * 13f))
                    v.lp1 += c * (raw - v.lp1); v.lp2 += c * (v.lp1 - v.lp2)
                    val sub = sin(twoPi * v.ph1) * 0.6f
                    val s = (v.lp2 + sub) * env(v, 0.004f) * v.vel * 0.34f
                    duckL[i] += s; duckR[i] += s
                    v.t++; continue
                }
                Inst.PAD -> {
                    val dt1 = v.freq * 1.006f * inv; val dt2 = v.freq * 0.994f * inv
                    v.ph1 += dt1; if (v.ph1 >= 1f) v.ph1 -= 1f
                    v.ph2 += dt2; if (v.ph2 >= 1f) v.ph2 -= 1f
                    val c = coef(v.param)
                    val a = saw(v.ph1, dt1); val b = saw(v.ph2, dt2)
                    v.lp1 += c * (a - v.lp1); v.lp2 += c * (v.lp1 - v.lp2)
                    v.lp3 += c * (b - v.lp3); v.lp4 += c * (v.lp3 - v.lp4)
                    val e = env(v, 0.28f) * v.vel * 0.09f
                    duckL[i] += (v.lp2 * 0.8f + v.lp4 * 0.2f) * e
                    duckR[i] += (v.lp4 * 0.8f + v.lp2 * 0.2f) * e
                    v.t++; continue
                }
                Inst.LEAD -> {
                    val vib = if (ts > 0.16f) 1f + 0.005f * sin(twoPi * 5.5f * ts) else 1f
                    val f = v.freq * vib
                    val dt1 = f * inv; val dt2 = f * 1.006f * inv
                    v.ph1 += dt1; if (v.ph1 >= 1f) v.ph1 -= 1f
                    v.ph2 += dt2; if (v.ph2 >= 1f) v.ph2 -= 1f
                    val raw = square(v.ph1, dt1) * 0.45f + saw(v.ph2, dt2) * 0.55f
                    val c = coef(1800f + 2200f * exp(-ts * 7f))
                    v.lp1 += c * (raw - v.lp1); v.lp2 += c * (v.lp1 - v.lp2)
                    val s = v.lp2 * env(v, 0.006f) * v.vel * 0.2f
                    sendL[i] += s * v.panL * 1.41f; sendR[i] += s * v.panR * 1.41f
                    v.t++; continue
                }
                Inst.ARP -> {
                    if (ts > 0.3f) { v.active = false; return }
                    val dt1 = v.freq * inv
                    v.ph1 += dt1; if (v.ph1 >= 1f) v.ph1 -= 1f
                    val raw = square(v.ph1, dt1)
                    val c = coef(500f + 4000f * v.param * exp(-ts * 18f))
                    v.lp1 += c * (raw - v.lp1); v.lp2 += c * (v.lp1 - v.lp2)
                    val s = v.lp2 * exp(-ts * 13f) * minOf(1f, ts * 400f) * v.vel * 0.12f
                    sendL[i] += s * v.panL * 1.41f; sendR[i] += s * v.panR * 1.41f
                    v.t++; continue
                }
                // ───── effetti ─────
                Inst.SFX_DEATH -> {
                    if (ts > 0.7f) { v.active = false; return }
                    val f = 60f + 380f * exp(-ts * 7f)
                    v.ph1 += f * inv; if (v.ph1 >= 1f) v.ph1 -= 1f
                    val nz = v.noise()
                    v.lp1 += coef(300f + 5000f * exp(-ts * 9f)) * (nz - v.lp1)
                    val s = (square(v.ph1, f * inv) * 0.35f + v.lp1 * 0.9f) * exp(-ts * 5.5f) * v.vel * 0.55f
                    dryL[i] += s; dryR[i] += s
                    v.t++; continue
                }
                Inst.SFX_BLIP -> {
                    if (ts > 0.22f) { v.active = false; return }
                    val f = v.freq * (1f + 1.2f * (1f - exp(-ts * 25f)))
                    v.ph1 += f * inv; if (v.ph1 >= 1f) v.ph1 -= 1f
                    val s = (sin(twoPi * v.ph1) * 0.7f + square(v.ph1, f * inv) * 0.15f) * exp(-ts * 16f) * v.vel * 0.35f
                    dryL[i] += s; dryR[i] += s
                    v.t++; continue
                }
                Inst.SFX_PHASE -> {
                    if (ts > 0.14f) { v.active = false; return }
                    // param > 0: verso l'alto (ciano), < 0: verso il basso (magenta)
                    val f = v.freq * (1f + v.param * 0.8f * (ts / 0.14f))
                    v.ph1 += f * inv; if (v.ph1 >= 1f) v.ph1 -= 1f
                    v.ph2 += f * 1.5f * inv; if (v.ph2 >= 1f) v.ph2 -= 1f
                    val e = minOf(1f, ts * 300f) * exp(-ts * 22f) * v.vel * 0.25f
                    val s = (sin(twoPi * v.ph1) + sin(twoPi * v.ph2) * 0.5f) * e
                    dryL[i] += s * 0.9f; dryR[i] += s * 0.9f
                    v.t++; continue
                }
                Inst.SFX_WIN -> {
                    if (ts > 1.6f) { v.active = false; return }
                    val stepIdx = minOf(3, (ts / 0.09f).toInt())
                    val semis = intArrayOf(0, 3, 7, 12)[stepIdx]
                    val f = v.freq * 2f.pow(semis / 12f)
                    v.ph1 += f * inv; if (v.ph1 >= 1f) v.ph1 -= 1f
                    val local = ts - stepIdx * 0.09f
                    val e = exp(-local * (if (stepIdx == 3) 2.5f else 14f)) * v.vel * 0.3f
                    val s = (sin(twoPi * v.ph1) * 0.7f + square(v.ph1, f * inv) * 0.12f) * e
                    sendL[i] += s; sendR[i] += s
                    v.t++; continue
                }
                Inst.SFX_CLICK -> {
                    if (ts > 0.05f) { v.active = false; return }
                    v.ph1 += v.freq * inv; if (v.ph1 >= 1f) v.ph1 -= 1f
                    val s = sin(twoPi * v.ph1) * exp(-ts * 90f) * v.vel * 0.35f
                    dryL[i] += s; dryR[i] += s
                    v.t++; continue
                }
                else -> { v.active = false; return }
            }
        }
    }

    /** Inviluppo attacco / sostegno / rilascio lineare. */
    private fun env(v: Voice, attack: Float): Float {
        val ts = v.t.toFloat() / sr
        val a = if (ts < attack) ts / attack else 1f
        return if (v.t < v.dur) a else a * (1f - (v.t - v.dur).toFloat() / (v.end - v.dur).coerceAtLeast(1))
    }
}
