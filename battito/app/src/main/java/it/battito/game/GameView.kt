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
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Vista di gioco: ciclo a passo fisso (240 Hz) agganciato al Choreographer, disegno su Canvas.
 * Comandi: metà DESTRA dello schermo = salto (tieni premuto per saltare di continuo),
 *          metà SINISTRA = cambio colore (ciano ↔ magenta). In alto a destra: pausa / impostazioni.
 */
@SuppressLint("ViewConstructor")
class GameView(context: Context, private val audio: AudioEngine) : View(context), Choreographer.FrameCallback {

    private enum class Mode { MENU, SETTINGS, PLAY, PAUSED, DEAD, WON }

    private val level = Levels.primo
    private val sim = Sim(level)
    private var mode = Mode.MENU
    /** Da dove si è aperta la schermata impostazioni (MENU o PAUSED). */
    private var settingsReturn = Mode.MENU

    private val settings = Settings(context)
    private var best = settings.best(level)
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
    private var keyJump = false
    private var dragSlider = -1      // 0 = musica, 1 = effetti
    private var dragPointer = -1

    // Geometria schermo
    private var tile = 1f
    private var groundY = 0f

    // Colori
    private val cyan = Color.rgb(25, 230, 255)
    private val magenta = Color.rgb(255, 47, 210)
    private val yellow = Color.rgb(255, 216, 74)
    private val blockFill = Color.rgb(20, 16, 48)
    private val blockLine = Color.rgb(140, 124, 255)
    private val panelFill = Color.argb(235, 14, 10, 34)
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
        audio.musicVolume = settings.musicVolume
        audio.sfxVolume = settings.sfxVolume
    }

    // Agganci per le foto di prova (test sul computer)
    internal val debugSim: Sim get() = sim
    internal fun debugUpdate(dt: Float) = update(dt)

    // ───────────────────────── ciclo di vita ─────────────────────────

    fun resume() {
        if (running) return
        running = true
        lastNs = 0L
        if (mode == Mode.MENU || (mode == Mode.SETTINGS && settingsReturn == Mode.MENU)) audio.playMenuLoop()
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        jumpPointers.clear(); keyJump = false
        sim.setHold(false)
        when (mode) {
            Mode.PLAY -> pauseGame()
            Mode.DEAD -> { mode = Mode.MENU }
            else -> {}
        }
    }

    /** true se il "indietro" è stato gestito dal gioco. */
    fun onBack(): Boolean = when (mode) {
        Mode.MENU -> false
        Mode.SETTINGS -> { closeSettings(); true }
        Mode.PLAY -> { pauseGame(); true }
        Mode.PAUSED, Mode.DEAD, Mode.WON -> { goToMenu(); true }
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

    // ───────────────────────── transizioni ─────────────────────────

    private fun startRun() {
        sim.reset()
        sim.setHold(jumpPointers.isNotEmpty() || keyJump)
        attempts++
        mode = Mode.PLAY
        acc = 0f; rot = 0f
        trail.clear()
        audio.playFromStart()
    }

    private fun pauseGame() {
        mode = Mode.PAUSED
        jumpPointers.clear(); sim.setHold(keyJump)
        audio.pauseMusic()
    }

    private fun resumeGame() {
        mode = Mode.PLAY
        acc = 0f
        // riparte esattamente dal punto della corsa: musica e livello restano allineati
        audio.playFrom(sim.x / Sim.SPEED)
    }

    private fun goToMenu() {
        mode = Mode.MENU
        jumpPointers.clear(); sim.setHold(false)
        particles.clear()
        audio.playMenuLoop()
    }

    private fun openSettings() {
        settingsReturn = mode
        mode = Mode.SETTINGS
        // in impostazioni la musica suona sempre, così si sente il volume mentre lo si regola
        audio.playMenuLoop()
        click()
    }

    private fun closeSettings() {
        mode = settingsReturn
        dragSlider = -1
        click()
        if (mode == Mode.PAUSED) audio.stopMusic()
    }

    private fun click() = audio.click()

    private fun vibrate(kind: Int) {
        if (settings.vibration) performHapticFeedback(kind, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }

    // ───────────────────────── logica ─────────────────────────

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
                        Sim.Event.PAD, Sim.Event.ORB -> {
                            burst(sim.x + Sim.SIZE / 2, sim.y, yellow, 10, 4f)
                            audio.blip()
                        }
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
            else -> {}
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
        audio.stopMusic()
        audio.death()
        vibrate(HapticFeedbackConstants.LONG_PRESS)
        saveBest((sim.progress * 100).toInt())
    }

    private fun onWin() {
        mode = Mode.WON
        stateTimer = 1.2f
        flash = 1f
        for (i in 0 until 5) burst(sim.x - i * 2f, 3f + i % 3, if (i % 2 == 0) cyan else magenta, 20, 14f)
        audio.win()
        saveBest(100)
    }

    private fun saveBest(pct: Int) {
        if (pct > best) {
            best = pct
            settings.saveBest(level, best)
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
        else {
            audio.phase(toCyan = sim.phase == 0)
            vibrate(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    // ───────────────────────── geometria dei pulsanti ─────────────────────────

    private fun cornerButtonX() = width - tile * 1.1f
    private fun cornerButtonY() = tile * 1.1f
    private fun cornerButtonR() = tile * 0.6f
    private fun inCornerButton(x: Float, y: Float) =
        hypot(x - cornerButtonX(), y - cornerButtonY()) < cornerButtonR() * 1.6f

    /** Pulsanti del menu di pausa: 0 riprendi, 1 impostazioni, 2 menu. */
    private fun pauseButton(i: Int, out: RectF): RectF {
        val bw = tile * 7f; val bh = tile * 1.15f
        val cy = height * 0.42f + i * tile * 1.55f
        return out.apply { set(width / 2f - bw / 2, cy - bh / 2, width / 2f + bw / 2, cy + bh / 2) }
    }

    private fun panel(out: RectF): RectF {
        val pw = minOf(width * 0.82f, tile * 17f)
        return out.apply { set(width / 2f - pw / 2, tile * 1.2f, width / 2f + pw / 2, height - tile * 0.8f) }
    }

    private fun settingsRowY(i: Int) = tile * 3.6f + i * tile * 1.7f

    private val tmpPanel = RectF()
    private fun trackLeft() = panel(tmpPanel).left + tile * 6.6f
    private fun trackRight() = panel(tmpPanel).right - tile * 3.6f
    private fun minusX() = trackLeft() - tile * 0.9f
    private fun plusX() = trackRight() + tile * 0.9f

    private fun backButton(out: RectF): RectF {
        val bw = tile * 5f; val bh = tile * 1.1f
        val cy = settingsRowY(3) + tile * 0.4f
        return out.apply { set(width / 2f - bw / 2, cy - bh / 2, width / 2f + bw / 2, cy + bh / 2) }
    }

    private fun setVolume(which: Int, v: Float) {
        val q = (v * 20f).roundToInt() / 20f   // passi del 5%
        if (which == 0) { settings.musicVolume = q; audio.musicVolume = settings.musicVolume }
        else { settings.sfxVolume = q; audio.sfxVolume = settings.sfxVolume }
    }

    private fun volume(which: Int) = if (which == 0) settings.musicVolume else settings.sfxVolume

    // ───────────────────────── input ─────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val idx = e.actionIndex
        val id = e.getPointerId(idx)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> onPointerDown(id, e.getX(idx), e.getY(idx))
            MotionEvent.ACTION_MOVE -> if (dragSlider >= 0) {
                val pi = e.findPointerIndex(dragPointer)
                if (pi >= 0) setVolume(dragSlider, (e.getX(pi) - trackLeft()) / (trackRight() - trackLeft()))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                jumpPointers -= id
                if (id == dragPointer) {
                    if (dragSlider == 1) audio.blip()   // anteprima del volume effetti
                    dragSlider = -1; dragPointer = -1
                }
            }
            MotionEvent.ACTION_CANCEL -> { jumpPointers.clear(); dragSlider = -1 }
        }
        sim.setHold(jumpPointers.isNotEmpty() || keyJump)
        return true
    }

    private fun onPointerDown(id: Int, x: Float, y: Float) {
        when (mode) {
            Mode.MENU -> if (inCornerButton(x, y)) openSettings() else startRun()
            Mode.PLAY -> when {
                inCornerButton(x, y) -> { pauseGame(); click() }
                x < width / 2f -> togglePhase()
                else -> jumpPointers += id
            }
            Mode.DEAD -> if (inCornerButton(x, y)) goToMenu()
            Mode.PAUSED -> {
                when {
                    pauseButton(0, rect).contains(x, y) || inCornerButton(x, y) -> { click(); resumeGame() }
                    pauseButton(1, rect).contains(x, y) -> openSettings()
                    pauseButton(2, rect).contains(x, y) -> { click(); goToMenu() }
                }
            }
            Mode.SETTINGS -> onSettingsDown(id, x, y)
            Mode.WON -> if (stateTimer <= 0f) { click(); goToMenu() }
        }
    }

    private fun onSettingsDown(id: Int, x: Float, y: Float) {
        if (backButton(rect).contains(x, y)) { closeSettings(); return }
        for (row in 0..1) {
            val ry = settingsRowY(row)
            if (abs(y - ry) > tile * 0.75f) continue
            when {
                abs(x - minusX()) < tile * 0.7f -> { setVolume(row, volume(row) - 0.1f); click() }
                abs(x - plusX()) < tile * 0.7f -> { setVolume(row, volume(row) + 0.1f); click() }
                x > trackLeft() - tile * 0.3f && x < trackRight() + tile * 0.3f -> {
                    dragSlider = row; dragPointer = id
                    setVolume(row, (x - trackLeft()) / (trackRight() - trackLeft()))
                }
            }
            return
        }
        val vy = settingsRowY(2)
        if (abs(y - vy) < tile * 0.75f && x > trackLeft() - tile && x < trackRight()) {
            settings.vibration = !settings.vibration
            click()
            vibrate(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    // Tastiera (utile su emulatore/Chromebook): Spazio/↑ = salto, Shift/X/← = colore, Esc/P = pausa
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return true
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> when (mode) {
                Mode.MENU -> startRun()
                Mode.WON -> if (stateTimer <= 0f) goToMenu()
                Mode.PAUSED -> resumeGame()
                else -> { keyJump = true; sim.setHold(true) }
            }
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_DPAD_LEFT -> togglePhase()
            KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_ESCAPE -> if (mode == Mode.PLAY) pauseGame() else if (mode == Mode.PAUSED) resumeGame()
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

    /** Impulso sul battito: in corsa segue la posizione nel livello (= posizione nel brano). */
    private val beat: Float
        get() {
            val t = when (mode) {
                Mode.PLAY, Mode.DEAD, Mode.PAUSED, Mode.WON -> sim.x / Sim.SPEED
                else -> time
            }
            return (1f - ((t * Song.BPM / 60f) % 1f)).pow(3)
        }

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

        drawBackdrop(c, w)
        drawLevel(c, w)
        drawGround(c, w, h)
        drawFinish(c)
        if (mode != Mode.DEAD && mode != Mode.WON) drawPlayer(c)
        drawParticles(c)
        c.restore()

        if (flash > 0f) {
            fill.color = Color.argb((flash * 120).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawRect(0f, 0f, w, h, fill)
        }

        when (mode) {
            Mode.MENU -> { drawMenu(c, w, h); drawGear(c) }
            Mode.SETTINGS -> drawSettings(c, w, h)
            Mode.PAUSED -> { drawHud(c, w, h); drawPauseMenu(c, w, h) }
            Mode.WON -> drawWin(c, w, h)
            else -> { drawHud(c, w, h); drawPauseIcon(c) }
        }
    }

    private fun drawBackdrop(c: Canvas, w: Float) {
        val b = beat
        val pc = phaseColor(sim.phase)
        fill.color = Color.argb((18 + b * 22 + phaseFlash * 40).toInt(), Color.red(pc), Color.green(pc), Color.blue(pc))
        c.drawRect(0f, 0f, w, groundY, fill)

        // Quadrati lontani (parallasse lenta)
        stroke.color = Color.argb(28, 200, 180, 255)
        val far = camX * 0.2f
        val spacing = 6f
        var i = floor(far / spacing).toInt() - 1
        while (true) {
            val px = (i * spacing - far) * tile
            if (px > w + tile * 4) break
            val hsh = ((i * 73856093) xor 0x5bd1e995).let { abs(it % 1000) / 1000f }
            val size = tile * (1.2f + hsh * 2.2f) * (1f + b * 0.06f)
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
        val b = beat
        val from = floor(camX).toInt() - 1
        val to = (camX + w / tile).toInt() + 1
        for (tx in from..to) for (ty in 0 until level.height) {
            val ch = level.at(tx, ty)
            if (ch == '.') continue
            val l = sx(tx.toFloat()); val r = sx(tx + 1f)
            val t = sy(ty + 1f); val bt = sy(ty.toFloat())
            when (ch) {
                '#' -> drawBlock(c, l, t, r, bt, blockFill, blockLine, b)
                'C', 'M' -> {
                    val col = if (ch == 'C') cyan else magenta
                    if (sim.isSolid(ch)) drawBlock(c, l, t, r, bt, col, Color.WHITE, b)
                    else {
                        dashed.color = Color.argb(90, Color.red(col), Color.green(col), Color.blue(col))
                        val ins = tile * 0.12f
                        c.drawRect(l + ins, t + ins, r - ins, bt - ins, dashed)
                    }
                }
                '^' -> drawSpike(c, l, t, r, bt, up = true, color = Color.rgb(245, 240, 255), ghost = false)
                'v' -> drawSpike(c, l, t, r, bt, up = false, color = Color.rgb(245, 240, 255), ghost = false)
                'c' -> drawSpike(c, l, t, r, bt, up = true, color = cyan, ghost = sim.phase != 0)
                'm' -> drawSpike(c, l, t, r, bt, up = true, color = magenta, ghost = sim.phase != 1)
                'p' -> {
                    fill.color = yellow
                    rect.set(l + tile * 0.1f, bt - tile * 0.22f, r - tile * 0.1f, bt)
                    c.drawRoundRect(rect, tile * 0.1f, tile * 0.1f, fill)
                    fill.color = Color.argb((60 + b * 80).toInt(), 255, 216, 74)
                    c.drawRect(l + tile * 0.2f, bt - tile * 0.9f, r - tile * 0.2f, bt - tile * 0.22f, fill)
                }
                'o' -> {
                    val cx = (l + r) / 2; val cy = (t + bt) / 2
                    stroke.color = yellow
                    c.drawCircle(cx, cy, tile * (0.34f + b * 0.06f), stroke)
                    fill.color = Color.argb(120, 255, 216, 74)
                    c.drawCircle(cx, cy, tile * 0.2f, fill)
                }
            }
        }
    }

    private fun drawBlock(c: Canvas, l: Float, t: Float, r: Float, b: Float, fillC: Int, lineC: Int, beatV: Float) {
        val ins = tile * 0.04f
        fill.color = fillC
        if (fillC != blockFill) fill.alpha = 210
        c.drawRect(l, t, r, b, fill)
        fill.color = Color.argb((25 + beatV * 35).toInt(), 255, 255, 255)
        c.drawRect(l + tile * 0.25f, t + tile * 0.25f, r - tile * 0.25f, b - tile * 0.25f, fill)
        stroke.color = lineC
        c.drawRect(l + ins, t + ins, r - ins, b - ins, stroke)
    }

    private fun drawSpike(c: Canvas, l: Float, t: Float, r: Float, b: Float, up: Boolean, color: Int, ghost: Boolean) {
        path.reset()
        val m = tile * 0.06f
        if (up) {
            path.moveTo(l + m, b); path.lineTo((l + r) / 2, t + m * 2); path.lineTo(r - m, b)
        } else {
            path.moveTo(l + m, t); path.lineTo((l + r) / 2, b - m * 2); path.lineTo(r - m, t)
        }
        path.close()
        if (ghost) {
            dashed.color = Color.argb(90, Color.red(color), Color.green(color), Color.blue(color))
            c.drawPath(path, dashed)
            return
        }
        fill.color = Color.rgb(16, 12, 36)
        c.drawPath(path, fill)
        stroke.color = color
        c.drawPath(path, stroke)
    }

    private fun drawGround(c: Canvas, w: Float, h: Float) {
        fill.color = Color.rgb(12, 8, 30)
        c.drawRect(0f, groundY, w, h, fill)
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

    // ───────────── interfaccia ─────────────

    private fun drawCornerCircle(c: Canvas) {
        fill.color = Color.argb(150, 10, 8, 28)
        c.drawCircle(cornerButtonX(), cornerButtonY(), cornerButtonR(), fill)
        stroke.color = Color.argb(200, 255, 255, 255)
        c.drawCircle(cornerButtonX(), cornerButtonY(), cornerButtonR(), stroke)
    }

    private fun drawPauseIcon(c: Canvas) {
        drawCornerCircle(c)
        fill.color = Color.WHITE
        val x = cornerButtonX(); val y = cornerButtonY(); val r = cornerButtonR()
        c.drawRect(x - r * 0.35f, y - r * 0.4f, x - r * 0.1f, y + r * 0.4f, fill)
        c.drawRect(x + r * 0.1f, y - r * 0.4f, x + r * 0.35f, y + r * 0.4f, fill)
    }

    /** Icona ingranaggio = impostazioni. */
    private fun drawGear(c: Canvas) {
        drawCornerCircle(c)
        val x = cornerButtonX(); val y = cornerButtonY(); val r = cornerButtonR()
        fill.color = Color.WHITE
        c.save()
        c.rotate(time * 20f, x, y)
        for (k in 0 until 8) {
            c.save()
            c.rotate(k * 45f, x, y)
            c.drawRect(x - r * 0.12f, y - r * 0.68f, x + r * 0.12f, y - r * 0.4f, fill)
            c.restore()
        }
        c.drawCircle(x, y, r * 0.45f, fill)
        fill.color = Color.rgb(10, 8, 28)
        c.drawCircle(x, y, r * 0.2f, fill)
        c.restore()
        text.textSize = tile * 0.24f
        text.color = Color.argb(200, 255, 255, 255)
        c.drawText("IMPOSTAZIONI", x, y + r + tile * 0.4f, text)
    }

    private fun drawButton(c: Canvas, r: RectF, label: String, accent: Int) {
        fill.color = Color.argb(200, 20, 14, 46)
        c.drawRoundRect(r, tile * 0.25f, tile * 0.25f, fill)
        stroke.color = accent
        c.drawRoundRect(r, tile * 0.25f, tile * 0.25f, stroke)
        text.textSize = tile * 0.45f
        text.color = Color.WHITE
        c.drawText(label, r.centerX(), r.centerY() + tile * 0.16f, text)
    }

    private fun drawHud(c: Canvas, w: Float, h: Float) {
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

        val r = tile * 0.32f
        val ix = tile * 0.8f; val iy = h - tile * 0.75f
        fill.color = phaseColor(sim.phase)
        c.drawCircle(ix, iy, r * (1f + phaseFlash * 0.4f), fill)
        stroke.color = phaseColor(1 - sim.phase)
        c.drawCircle(ix + r * 2.6f, iy, r * 0.7f, stroke)

        if (attempts <= 3 && mode != Mode.PAUSED) {
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

    private fun drawPauseMenu(c: Canvas, w: Float, h: Float) {
        fill.color = Color.argb(170, 6, 4, 18)
        c.drawRect(0f, 0f, w, h, fill)
        text.textSize = tile * 0.9f
        text.color = Color.WHITE
        c.drawText("PAUSA", w / 2, h * 0.27f, text)
        drawButton(c, pauseButton(0, rect), "RIPRENDI", cyan)
        drawButton(c, pauseButton(1, rect), "IMPOSTAZIONI", Color.WHITE)
        drawButton(c, pauseButton(2, rect), "MENU", magenta)
    }

    private fun drawSettings(c: Canvas, w: Float, h: Float) {
        fill.color = Color.argb(170, 6, 4, 18)
        c.drawRect(0f, 0f, w, h, fill)
        val p = panel(RectF())
        fill.color = panelFill
        c.drawRoundRect(p, tile * 0.35f, tile * 0.35f, fill)
        stroke.color = Color.argb(160, 140, 124, 255)
        c.drawRoundRect(p, tile * 0.35f, tile * 0.35f, stroke)

        text.textSize = tile * 0.7f
        text.color = Color.WHITE
        c.drawText("IMPOSTAZIONI", w / 2, p.top + tile * 1.1f, text)

        drawSliderRow(c, 0, "MUSICA", cyan)
        drawSliderRow(c, 1, "EFFETTI", magenta)

        // Vibrazione
        val vy = settingsRowY(2)
        drawRowLabel(c, "VIBRAZIONE", vy)
        val on = settings.vibration
        val tw = tile * 1.8f; val th = tile * 0.8f
        val tl = trackLeft()
        rect.set(tl, vy - th / 2, tl + tw, vy + th / 2)
        fill.color = if (on) cyan else Color.argb(80, 255, 255, 255)
        c.drawRoundRect(rect, th / 2, th / 2, fill)
        fill.color = Color.WHITE
        c.drawCircle(if (on) tl + tw - th / 2 else tl + th / 2, vy, th * 0.38f, fill)
        text.textAlign = Paint.Align.LEFT
        text.textSize = tile * 0.4f
        c.drawText(if (on) "SÌ" else "NO", tl + tw + tile * 0.4f, vy + tile * 0.14f, text)
        text.textAlign = Paint.Align.CENTER

        drawButton(c, backButton(rect), "INDIETRO", Color.WHITE)
    }

    private fun drawRowLabel(c: Canvas, label: String, y: Float) {
        text.textAlign = Paint.Align.LEFT
        text.textSize = tile * 0.42f
        text.color = Color.WHITE
        c.drawText(label, panel(tmpPanel).left + tile * 0.7f, y + tile * 0.15f, text)
        text.textAlign = Paint.Align.CENTER
    }

    private fun drawSliderRow(c: Canvas, row: Int, label: String, accent: Int) {
        val y = settingsRowY(row)
        drawRowLabel(c, label, y)
        val l = trackLeft(); val r = trackRight()
        val v = volume(row)
        val th = tile * 0.18f
        fill.color = Color.argb(70, 255, 255, 255)
        rect.set(l, y - th / 2, r, y + th / 2)
        c.drawRoundRect(rect, th, th, fill)
        fill.color = accent
        rect.set(l, y - th / 2, l + (r - l) * v, y + th / 2)
        c.drawRoundRect(rect, th, th, fill)
        val kx = l + (r - l) * v
        fill.color = Color.WHITE
        c.drawCircle(kx, y, tile * (if (dragSlider == row) 0.36f else 0.3f), fill)

        // pulsanti − e +
        for ((bx, sign) in listOf(minusX() to "−", plusX() to "+")) {
            stroke.color = Color.argb(200, 255, 255, 255)
            c.drawCircle(bx, y, tile * 0.38f, stroke)
            text.textSize = tile * 0.5f
            text.color = Color.WHITE
            c.drawText(sign, bx, y + tile * 0.17f, text)
        }
        text.textAlign = Paint.Align.LEFT
        text.textSize = tile * 0.42f
        text.color = Color.WHITE
        c.drawText("${(v * 100).roundToInt()}%", plusX() + tile * 0.6f, y + tile * 0.15f, text)
        text.textAlign = Paint.Align.CENTER
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
