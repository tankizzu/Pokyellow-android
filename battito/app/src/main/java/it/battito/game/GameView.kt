package it.battito.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Vista di gioco: ciclo a passo fisso (240 Hz) agganciato al Choreographer, disegno su Canvas.
 * Comandi: metà DESTRA dello schermo = salto (tieni premuto per saltare di continuo),
 *          metà SINISTRA = cambio colore (ciano ↔ magenta).
 */
@SuppressLint("ViewConstructor")
class GameView(context: Context) : View(context), Choreographer.FrameCallback {

    private enum class Mode { MENU, PLAY, DEAD, WON }

    private val level = Levels.primo
    private val sim = Sim(level)
    private var mode = Mode.MENU

    private val prefs = context.getSharedPreferences("battito", Context.MODE_PRIVATE)
    private var best = prefs.getInt("best_" + level.name, 0)
    private var attempts = 0
    private var stateTimer = 0f
    private var time = 0f
    private var acc = 0f
    private var lastNs = 0L
    private var running = false

    // Effetti
    private var rot = 0f
    private var shake = 0f
    private var flash = 0f
    private var phaseFlash = 0f
    private val trail = ArrayDeque<FloatArray>()
    private val particles = ArrayList<Particle>()
    private val rnd = Random(7)

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int, val size: Float)

    // Input
    private val jumpPointers = HashSet<Int>()
    private val ignoredPointers = HashSet<Int>()
    private var keyJump = false

    // Geometria schermo
    private var tile = 1f
    private var groundY = 0f

    // Colori
    private val cyan = Color.rgb(25, 230, 255)
    private val magenta = Color.rgb(255, 47, 210)
    private val yellow = Color.rgb(255, 216, 74)
    private val blockFill = Color.rgb(20, 16, 48)
    private val blockLine = Color.rgb(140, 124, 255)
    private fun phaseColor(p: Int) = if (p == 0) cyan else magenta

    // Pennelli
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bg = Paint()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val rect = RectF()
    private val path = Path()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    // ───────────────────────── ciclo di vita ─────────────────────────

    fun resume() {
        if (running) return
        running = true
        lastNs = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        jumpPointers.clear(); keyJump = false
        sim.setHold(false)
        if (mode == Mode.PLAY || mode == Mode.DEAD) mode = Mode.MENU
    }

    /** true se il "indietro" è stato gestito dal gioco. */
    fun onBack(): Boolean {
        if (mode == Mode.MENU) return false
        mode = Mode.MENU
        return true
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastNs == 0L) 0f else ((frameTimeNanos - lastNs) / 1e9f).coerceAtMost(0.05f)
        lastNs = frameTimeNanos
        update(dt)
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        tile = h / 10.8f
        groundY = h - 1.5f * tile
        bg.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            Color.rgb(9, 6, 28), Color.rgb(34, 16, 78), Shader.TileMode.CLAMP)
        stroke.strokeWidth = tile * 0.07f
        dashed.strokeWidth = tile * 0.05f
        dashed.pathEffect = DashPathEffect(floatArrayOf(tile * 0.18f, tile * 0.12f), 0f)
    }

    // ───────────────────────── logica ─────────────────────────

    private fun startRun() {
        sim.reset()
        sim.setHold(jumpPointers.isNotEmpty() || keyJump)
        attempts++
        mode = Mode.PLAY
        acc = 0f; rot = 0f
        trail.clear()
    }

    private fun update(dt: Float) {
        time += dt
        shake = (shake - dt * 3f).coerceAtLeast(0f)
        flash = (flash - dt * 2.5f).coerceAtLeast(0f)
        phaseFlash = (phaseFlash - dt * 4f).coerceAtLeast(0f)

        when (mode) {
            Mode.PLAY -> {
                acc += dt
                while (acc >= Sim.STEP) {
                    acc -= Sim.STEP
                    sim.step()
                    when (sim.lastEvent) {
                        Sim.Event.PAD, Sim.Event.ORB -> burst(sim.x + Sim.SIZE / 2, sim.y, yellow, 10, 4f)
                        else -> {}
                    }
                    if (!sim.alive) { onDeath(); break }
                    if (sim.won) { onWin(); break }
                }
                if (mode == Mode.PLAY) {
                    if (sim.grounded) {
                        val target = (rot / 90f).roundToInt() * 90f
                        rot += (target - rot) * (dt * 25f).coerceAtMost(1f)
                    } else {
                        rot += 430f * dt
                    }
                    trail.addLast(floatArrayOf(sim.x, sim.y))
                    while (trail.size > 14) trail.removeFirst()
                }
            }
            Mode.DEAD -> {
                stateTimer -= dt
                if (stateTimer <= 0f) startRun()
            }
            Mode.WON -> stateTimer -= dt
            Mode.MENU -> {}
        }

        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.vy -= 30f * dt
            p.x += p.vx * dt; p.y += p.vy * dt
        }
    }

    private fun onDeath() {
        mode = Mode.DEAD
        stateTimer = 0.75f
        shake = 1f
        flash = 0.6f
        burst(sim.x + Sim.SIZE / 2, sim.y + Sim.SIZE / 2, phaseColor(sim.phase), 36, 12f)
        burst(sim.x + Sim.SIZE / 2, sim.y + Sim.SIZE / 2, Color.WHITE, 12, 8f)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        saveBest((sim.progress * 100).toInt())
    }

    private fun onWin() {
        mode = Mode.WON
        stateTimer = 0.8f
        flash = 1f
        for (i in 0 until 5) burst(sim.x - i * 2f, 3f + i % 3, if (i % 2 == 0) cyan else magenta, 20, 14f)
        saveBest(100)
    }

    private fun saveBest(pct: Int) {
        if (pct > best) {
            best = pct
            prefs.edit().putInt("best_" + level.name, best).apply()
        }
    }

    private fun burst(wx: Float, wy: Float, color: Int, n: Int, speed: Float) {
        repeat(n) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val s = speed * (0.3f + rnd.nextFloat() * 0.7f)
            particles += Particle(wx, wy, cos(a) * s, sin(a) * s + speed * 0.3f,
                0.4f + rnd.nextFloat() * 0.5f, color, 0.12f + rnd.nextFloat() * 0.18f)
        }
    }

    private fun togglePhase() {
        if (mode != Mode.PLAY) return
        sim.togglePhase()
        phaseFlash = 1f
        burst(sim.x + Sim.SIZE / 2, sim.y + Sim.SIZE / 2, phaseColor(sim.phase), 8, 5f)
        if (!sim.alive) onDeath()
        else performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // ───────────────────────── input ─────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val idx = e.actionIndex
        val id = e.getPointerId(idx)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                when (mode) {
                    Mode.MENU -> { ignoredPointers += id; startRun() }
                    Mode.WON -> { ignoredPointers += id; if (stateTimer <= 0f) mode = Mode.MENU }
                    else -> {
                        if (e.getX(idx) < width / 2f) togglePhase() else jumpPointers += id
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                jumpPointers -= id; ignoredPointers -= id
            }
            MotionEvent.ACTION_CANCEL -> { jumpPointers.clear(); ignoredPointers.clear() }
        }
        sim.setHold(jumpPointers.isNotEmpty() || keyJump)
        return true
    }

    // Tastiera (utile su emulatore/Chromebook): Spazio/↑ = salto, Shift/X/← = colore
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return true
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> {
                if (mode == Mode.MENU) startRun()
                else if (mode == Mode.WON) { if (stateTimer <= 0f) mode = Mode.MENU }
                else { keyJump = true; sim.setHold(true) }
            }
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_DPAD_LEFT -> togglePhase()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_W) {
            keyJump = false
            sim.setHold(jumpPointers.isNotEmpty())
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // ───────────────────────── disegno ─────────────────────────

    private val beat: Float get() = (1f - ((time * 128f / 60f) % 1f)).pow(3)

    private var camX = 0f
    private fun sx(wx: Float) = (wx - camX) * tile
    private fun sy(wy: Float) = groundY - wy * tile

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        camX = sim.x - (w * 0.28f) / tile

        c.drawRect(0f, 0f, w, h, bg)

        c.save()
        if (shake > 0f) {
            val m = shake * shake * tile * 0.35f
            c.translate((rnd.nextFloat() - 0.5f) * m, (rnd.nextFloat() - 0.5f) * m)
        }

        drawBackdrop(c, w, h)
        drawLevel(c, w)
        drawGround(c, w, h)
        drawFinish(c)
        if (mode == Mode.PLAY || mode == Mode.MENU) drawPlayer(c)
        drawParticles(c)
        c.restore()

        if (flash > 0f) {
            fill.color = Color.argb((flash * 120).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawRect(0f, 0f, w, h, fill)
        }

        when (mode) {
            Mode.MENU -> drawMenu(c, w, h)
            Mode.WON -> drawWin(c, w, h)
            else -> drawHud(c, w, h)
        }
    }

    private fun drawBackdrop(c: Canvas, w: Float, h: Float) {
        // Tinta della fase attuale + impulso sul ritmo
        val pc = phaseColor(sim.phase)
        fill.color = Color.argb((18 + beat * 22 + phaseFlash * 40).toInt(), Color.red(pc), Color.green(pc), Color.blue(pc))
        c.drawRect(0f, 0f, w, groundY, fill)

        // Quadrati lontani (parallasse lenta)
        stroke.color = Color.argb(28, 200, 180, 255)
        val far = camX * 0.2f
        val spacing = 6f
        var i = floor(far / spacing).toInt() - 1
        while (true) {
            val wx = i * spacing
            val px = (wx - far) * tile
            if (px > w + tile * 4) break
            val hsh = ((i * 73856093) xor 0x5bd1e995).let { abs(it % 1000) / 1000f }
            val size = tile * (1.2f + hsh * 2.2f)
            val py = groundY - tile * (3f + hsh * 5f)
            c.save()
            c.rotate(time * 12f + hsh * 90f, px, py)
            rect.set(px - size / 2, py - size / 2, px + size / 2, py + size / 2)
            c.drawRect(rect, stroke)
            c.restore()
            i++
        }

        // Griglia verticale (parallasse media)
        fill.color = Color.argb(16, 255, 255, 255)
        val mid = camX * 0.5f
        var gx = floor(mid / 2f) * 2f
        while ((gx - mid) * tile < w) {
            val px = (gx - mid) * tile
            c.drawRect(px, 0f, px + 2f, groundY, fill)
            gx += 2f
        }
    }

    private fun drawLevel(c: Canvas, w: Float) {
        val from = floor(camX).toInt() - 1
        val to = (camX + w / tile).toInt() + 1
        for (tx in from..to) for (ty in 0 until level.height) {
            val ch = level.at(tx, ty)
            if (ch == '.') continue
            val l = sx(tx.toFloat()); val r = sx(tx + 1f)
            val t = sy(ty + 1f); val b = sy(ty.toFloat())
            when (ch) {
                '#' -> drawBlock(c, l, t, r, b, blockFill, blockLine, true)
                'C', 'M' -> {
                    val col = if (ch == 'C') cyan else magenta
                    if (sim.isSolid(ch)) drawBlock(c, l, t, r, b, col, Color.WHITE, true)
                    else {
                        dashed.color = Color.argb(90, Color.red(col), Color.green(col), Color.blue(col))
                        val ins = tile * 0.12f
                        c.drawRect(l + ins, t + ins, r - ins, b - ins, dashed)
                    }
                }
                '^' -> drawSpike(c, l, t, r, b, up = true)
                'v' -> drawSpike(c, l, t, r, b, up = false)
                'p' -> {
                    fill.color = yellow
                    rect.set(l + tile * 0.1f, b - tile * 0.22f, r - tile * 0.1f, b)
                    c.drawRoundRect(rect, tile * 0.1f, tile * 0.1f, fill)
                    fill.color = Color.argb((60 + beat * 80).toInt(), 255, 216, 74)
                    c.drawRect(l + tile * 0.2f, b - tile * 0.9f, r - tile * 0.2f, b - tile * 0.22f, fill)
                }
                'o' -> {
                    val cx = (l + r) / 2; val cy = (t + b) / 2
                    stroke.color = yellow
                    c.drawCircle(cx, cy, tile * (0.34f + beat * 0.06f), stroke)
                    fill.color = Color.argb(120, 255, 216, 74)
                    c.drawCircle(cx, cy, tile * 0.2f, fill)
                }
            }
        }
    }

    private fun drawBlock(c: Canvas, l: Float, t: Float, r: Float, b: Float, fillC: Int, lineC: Int, glow: Boolean) {
        val ins = tile * 0.04f
        fill.color = fillC
        if (fillC != blockFill) fill.alpha = 210
        c.drawRect(l, t, r, b, fill)
        if (glow) {
            fill.color = Color.argb((25 + beat * 35).toInt(), 255, 255, 255)
            c.drawRect(l + tile * 0.25f, t + tile * 0.25f, r - tile * 0.25f, b - tile * 0.25f, fill)
        }
        stroke.color = lineC
        c.drawRect(l + ins, t + ins, r - ins, b - ins, stroke)
    }

    private fun drawSpike(c: Canvas, l: Float, t: Float, r: Float, b: Float, up: Boolean) {
        path.reset()
        val m = tile * 0.06f
        if (up) {
            path.moveTo(l + m, b); path.lineTo((l + r) / 2, t + m * 2); path.lineTo(r - m, b)
        } else {
            path.moveTo(l + m, t); path.lineTo((l + r) / 2, b - m * 2); path.lineTo(r - m, t)
        }
        path.close()
        fill.color = Color.rgb(16, 12, 36)
        c.drawPath(path, fill)
        stroke.color = Color.rgb(245, 240, 255)
        c.drawPath(path, stroke)
    }

    private fun drawGround(c: Canvas, w: Float, h: Float) {
        fill.color = Color.rgb(12, 8, 30)
        c.drawRect(0f, groundY, w, h, fill)
        // scanalature che scorrono
        fill.color = Color.argb(30, 255, 255, 255)
        var gx = floor(camX)
        while ((gx - camX) * tile < w) {
            val px = (gx - camX) * tile
            c.drawRect(px, groundY + tile * 0.15f, px + 2f, h, fill)
            gx += 1f
        }
        val pc = phaseColor(sim.phase)
        fill.color = Color.argb((90 + beat * 120).toInt(), Color.red(pc), Color.green(pc), Color.blue(pc))
        c.drawRect(0f, groundY - tile * 0.04f, w, groundY + tile * 0.12f, fill)
        fill.color = Color.WHITE
        c.drawRect(0f, groundY - 1.5f, w, groundY + 1.5f, fill)
    }

    private fun drawFinish(c: Canvas) {
        val px = sx(level.width.toFloat())
        if (px > width + tile) return
        for (i in 0 until 6) {
            fill.color = Color.argb(160 - i * 25, 255, 255, 255)
            c.drawRect(px + i * tile * 0.15f, 0f, px + i * tile * 0.15f + tile * 0.08f, groundY, fill)
        }
    }

    private fun drawPlayer(c: Canvas) {
        val s = Sim.SIZE * tile
        val pc = phaseColor(sim.phase)
        // scia
        trail.forEachIndexed { i, p ->
            val a = (i + 1f) / trail.size
            fill.color = Color.argb((a * 90).toInt(), Color.red(pc), Color.green(pc), Color.blue(pc))
            val q = s * 0.35f * a
            val cx = sx(p[0] + Sim.SIZE / 2); val cy = sy(p[1] + Sim.SIZE / 2)
            c.drawRect(cx - q / 2, cy - q / 2, cx + q / 2, cy + q / 2, fill)
        }
        val l = sx(sim.x); val t = sy(sim.y + Sim.SIZE)
        val cx = l + s / 2; val cy = t + s / 2
        c.save()
        c.rotate(rot, cx, cy)
        // alone
        fill.color = Color.argb((60 + phaseFlash * 120).toInt(), Color.red(pc), Color.green(pc), Color.blue(pc))
        val g = s * (0.18f + phaseFlash * 0.3f)
        c.drawRect(l - g, t - g, l + s + g, t + s + g, fill)
        fill.color = pc
        c.drawRect(l, t, l + s, t + s, fill)
        fill.color = Color.rgb(10, 8, 26)
        c.drawRect(l + s * 0.2f, t + s * 0.2f, l + s * 0.8f, t + s * 0.8f, fill)
        fill.color = Color.WHITE
        c.drawRect(l + s * 0.36f, t + s * 0.36f, l + s * 0.64f, t + s * 0.64f, fill)
        c.restore()
    }

    private fun drawParticles(c: Canvas) {
        for (p in particles) {
            val a = (p.life * 2f).coerceAtMost(1f)
            fill.color = p.color
            fill.alpha = (a * 255).toInt()
            val q = p.size * tile
            val px = sx(p.x); val py = sy(p.y)
            c.drawRect(px - q / 2, py - q / 2, px + q / 2, py + q / 2, fill)
        }
    }

    private fun drawHud(c: Canvas, w: Float, h: Float) {
        // barra di avanzamento
        val bw = w * 0.4f; val bh = tile * 0.16f
        val bx = (w - bw) / 2; val by = tile * 0.45f
        fill.color = Color.argb(70, 255, 255, 255)
        rect.set(bx, by, bx + bw, by + bh)
        c.drawRoundRect(rect, bh, bh, fill)
        fill.color = phaseColor(sim.phase)
        rect.set(bx, by, bx + bw * sim.progress, by + bh)
        c.drawRoundRect(rect, bh, bh, fill)
        text.textSize = tile * 0.36f
        text.color = Color.WHITE
        c.drawText("${(sim.progress * 100).toInt()}%", w / 2, by + bh + tile * 0.45f, text)

        text.textAlign = Paint.Align.LEFT
        text.textSize = tile * 0.3f
        text.color = Color.argb(180, 255, 255, 255)
        c.drawText("TENTATIVO $attempts", tile * 0.5f, tile * 0.65f, text)
        text.textAlign = Paint.Align.CENTER

        // indicatore di fase (in basso a sinistra)
        val r = tile * 0.32f
        val ix = tile * 0.8f; val iy = h - tile * 0.75f
        fill.color = phaseColor(sim.phase)
        c.drawCircle(ix, iy, r * (1f + phaseFlash * 0.4f), fill)
        stroke.color = phaseColor(1 - sim.phase)
        c.drawCircle(ix + r * 2.6f, iy, r * 0.7f, stroke)

        // suggerimenti comandi nei primi tentativi
        if (attempts <= 3) {
            val a = if (attempts <= 1) 110 else 55
            text.textSize = tile * 0.42f
            text.color = Color.argb(a, 255, 255, 255)
            c.drawText("◀ CAMBIA COLORE", w * 0.25f, h - tile * 0.55f, text)
            c.drawText("SALTA ▶", w * 0.75f, h - tile * 0.55f, text)
            fill.color = Color.argb(a / 4, 255, 255, 255)
            c.drawRect(w / 2 - 1f, groundY + tile * 0.3f, w / 2 + 1f, h - tile * 0.2f, fill)
        }
    }

    private fun drawMenu(c: Canvas, w: Float, h: Float) {
        fill.color = Color.argb(150, 6, 4, 18)
        c.drawRect(0f, 0f, w, h, fill)

        val ty = h * 0.34f
        text.textSize = tile * 1.7f
        val off = tile * 0.06f * (1f + beat)
        text.color = Color.argb(200, Color.red(cyan), Color.green(cyan), Color.blue(cyan))
        c.drawText("BATTITO", w / 2 - off, ty, text)
        text.color = Color.argb(200, Color.red(magenta), Color.green(magenta), Color.blue(magenta))
        c.drawText("BATTITO", w / 2 + off, ty, text)
        text.color = Color.WHITE
        c.drawText("BATTITO", w / 2, ty, text)

        text.textSize = tile * 0.36f
        text.color = Color.argb(220, 255, 255, 255)
        c.drawText(level.name.uppercase() + "  ·  MIGLIORE $best%", w / 2, ty + tile * 0.9f, text)

        text.textSize = tile * 0.3f
        text.color = Color.argb(170, 255, 255, 255)
        c.drawText("DESTRA: salta (tieni premuto per saltare di continuo)", w / 2, ty + tile * 1.9f, text)
        c.drawText("SINISTRA: cambia colore. Il tuo colore è solido, l'altro è un fantasma.", w / 2, ty + tile * 2.4f, text)

        val pulse = 0.55f + 0.45f * sin(time * 4f)
        text.textSize = tile * 0.48f
        text.color = Color.argb((pulse * 255).toInt(), 255, 255, 255)
        c.drawText("TOCCA PER GIOCARE", w / 2, ty + tile * 3.7f, text)
    }

    private fun drawWin(c: Canvas, w: Float, h: Float) {
        fill.color = Color.argb(140, 6, 4, 18)
        c.drawRect(0f, 0f, w, h, fill)
        text.textSize = tile * 1.2f
        text.color = Color.WHITE
        c.drawText("COMPLETATO!", w / 2, h * 0.4f, text)
        text.textSize = tile * 0.42f
        c.drawText("Tentativi: $attempts", w / 2, h * 0.4f + tile * 1.1f, text)
        if (stateTimer <= 0f) {
            text.color = Color.argb(200, 255, 255, 255)
            c.drawText("Tocca per tornare al menu", w / 2, h * 0.4f + tile * 2.1f, text)
        }
    }
}
