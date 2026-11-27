package it.giallotouch.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import it.giallotouch.MainActivity
import it.giallotouch.emu.GameSession
import it.giallotouch.emu.NativeCore
import it.giallotouch.storage.SaveSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

/**
 * Banco di prova dell'interfaccia (D-042): l'app vera (MainActivity, Compose, motore vero con la ROM dell'utente)
 * su un telefono simulato con lo schermo del CMF Phone 2 Pro. Preme tasti e tocca lo schermo come una persona,
 * controlla lo stato e salva foto dello schermo nella cartella giallo.shots (fuori dal repository: contengono
 * immagini del gioco). Saltato senza ROM, libreria del motore per il computer e cartella delle foto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h912dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class AppScreensHarness {

    protected lateinit var shots: File
    protected lateinit var root: View
    protected var controller: org.robolectric.android.controller.ActivityController<MainActivity>? = null

    /** Chiude l'app alla fine di ogni prova: il motore non deve restare acceso durante la prova successiva. */
    @org.junit.After
    fun chiudi() {
        controller?.let { runCatching { it.pause().stop().destroy() } }
        controller = null
    }
    protected val unit get() = root.width / 160f

    protected fun pump(ms: Long) {
        var left = ms
        while (left > 0) {
            Thread.sleep(16)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            left -= 16
        }
    }

    protected fun shot(name: String): Bitmap {
        pump(300)
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        FileOutputStream(File(shots, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    /** Rifotografa finché [ok] è vera (l'interfaccia del banco di prova si aggiorna con ritardi variabili). */
    protected fun shotUntil(name: String, maxMs: Long = 15_000, ok: (Bitmap) -> Boolean): Boolean {
        var t = 0L
        while (true) {
            val b = shot(name)
            if (ok(b)) return true
            if (t >= maxMs) return false
            pump(500); t += 800
        }
    }

    /** Riquadro del gioco nel rettangolo di caselle: chiaro dentro, con bordo scuro. */
    protected fun boxAt(b: Bitmap, col0: Int, row0: Int, cols: Int, rows: Int): Boolean {
        val tp = 8 * unit
        var light = 0; var dark = 0; var n = 0
        val x0 = (col0 * tp).toInt(); val y0 = (row0 * tp).toInt()
        val x1 = ((col0 + cols) * tp).toInt().coerceAtMost(b.width - 1); val y1 = ((row0 + rows) * tp).toInt().coerceAtMost(b.height - 1)
        var y = y0
        while (y < y1) { var x = x0; while (x < x1) {
            val p = b.getPixel(x, y); val l = (android.graphics.Color.red(p) + android.graphics.Color.green(p) + android.graphics.Color.blue(p)) / 3
            if (l > 200) light++ else if (l < 80) dark++; n++; x += 3 }; y += 3 }
        return n > 0 && light > n * 0.5 && dark > n * 0.05
    }

    /** Tocco in una casella (colonna, riga) della griglia di 8 pixel del gioco sullo schermo. */
    protected fun tapTile(col: Float, row: Float) = tap(col * 8 * unit, row * 8 * unit)
    protected var lastSession: GameSession? = null
    /** START compare dopo 3 fotogrammi di fila (D-112) e solo con la vista estesa, la cui altezza si sa solo quando la vista è
     * disegnata (qui solo con una foto): si disegna e si aspetta che START ci sia prima di toccarlo. */
    protected fun tapStart() { shot("_start"); waitFor(20_000) { lastSession?.startOk != false }; pump(400); tapTile(10f, rows() - 2f) }

    protected fun tap(x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        pump(48)
        root.dispatchTouchEvent(MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0))
        pump(600)
    }

    /** Trascinamento da una casella a un'altra (scorrere col dito). */
    protected fun swipe(c0: Float, r0: Float, c1: Float, r1: Float) {
        val tp = 8 * unit; val t = SystemClock.uptimeMillis()
        root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, c0 * tp, r0 * tp, 0)); pump(32)
        for (k in 1..10) { val f = k / 10f; root.dispatchTouchEvent(MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, (c0 + (c1 - c0) * f) * tp, (r0 + (r1 - r0) * f) * tp, 0)); pump(24) }
        root.dispatchTouchEvent(MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, c1 * tp, r1 * tp, 0)); pump(600)
    }

    /** Tenere premuto in una casella. */
    protected fun longPressTile(col: Float, row: Float) {
        val tp = 8 * unit; val t = SystemClock.uptimeMillis()
        root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, col * tp, row * tp, 0)); pump(900)
        root.dispatchTouchEvent(MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, col * tp, row * tp, 0)); pump(600)
    }

    protected fun press(s: GameSession, key: Int) { s.setKeys(key); pump(120); s.setKeys(0); pump(300) }

    protected fun rows() = (root.height / (8 * unit)).toInt()

    /** Aspetta (al massimo [maxMs]) che [cond] sia vera, poi lascia all'interfaccia il tempo di aggiornarsi. */
    protected fun waitFor(maxMs: Long, cond: () -> Boolean): Boolean {
        var t = 0L
        while (!cond() && t < maxMs) { pump(100); t += 100 }
        pump(1200)
        return cond()
    }

    /** Avvia l'app con la ROM; [battery] = salvataggio del gioco, [auto] = stato nel salvataggio automatico. */
    protected fun start(battery: String? = null, auto: String? = null): GameSession? {
        val rom = System.getProperty("giallo.rom")?.let(::File)
        val dir = System.getProperty("giallo.shots")?.let(::File)
        if (rom == null || !rom.isFile || dir == null) return null
        // partita richiesta ma non indicata (-Pgiallo.state.X): la prova si salta
        if (auto != null && System.getProperty(auto)?.let(::File)?.isFile != true) return null
        shots = dir.apply { mkdirs() }
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val part = File(ctx.filesDir, "partita").apply { mkdirs() }
        File(part, "gioco.rom").writeBytes(rom.readBytes())
        battery?.let { System.getProperty(it) }?.let(::File)?.takeIf { it.isFile }?.let { File(part, "salvataggio.sav").writeBytes(it.readBytes()) }
        auto?.let { System.getProperty(it) }?.let(::File)?.takeIf { it.isFile }?.let { File(part, SaveSlot.AUTO.fileName + ".state").writeBytes(it.readBytes()) }
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller = c
        val activity = c.get()
        root = activity.window.decorView
        pump(500)
        lastSession = activity.sessionForTest
        return activity.sessionForTest
    }

    protected fun toMainMenu(s: GameSession) {
        pump(4000)
        var tries = 0
        while (s.mainMenuItems == 0 && tries++ < 30) { press(s, NativeCore.KEY_START); pump(400) }
        pump(600)
    }

    /** Nessun pulsante dell'app prima di giocare; menu iniziale ridisegnato; Carica partita nello stile del gioco. */
    fun avvioMenuECaricaPartita() {
        val s = start(battery = "giallo.battery", auto = "giallo.state.squadra") ?: return assumeTrue(false)
        pump(5000)
        shot("01-avvio")
        assertFalse("prima di giocare non si è in partita", s.inGame)
        toMainMenu(s)
        assertEquals("menu con CONTINUA", 3, s.mainMenuItems)
        val frameTop = (root.height - 144 * unit) / 2 / (8 * unit)
        assertTrue("menu ridisegnato 18 caselle", shotUntil("02-menu-iniziale") { boxAt(it, 0, frameTop.toInt() + 1, 18, 12) })
        assertFalse(s.inGame)
        // CARICA PARTITA: seconda voce (riga 5 dell'immagine del gioco, che parte da (altezza − 144·unit)/2)
        val frameTopRows = (root.height - 144 * unit) / 2 / (8 * unit)
        var opened = false
        for (attempt in 0 until 3) {
            tapTile(4f, frameTopRows + 5.5f)
            if (shotUntil("03-carica-partita", 8_000) { boxAt(it, 0, 3, 20, 3) }) { opened = true; break }
        }
        assertTrue("Carica partita aperta", opened)
        tapTile(4f, 7.5f)   // primo riquadro: il salvataggio automatico
        assertTrue("dopo il caricamento si è in partita", waitFor(20_000) { s.inGame })
        shot("04-partita-caricata")
    }

    /** In gioco: MENU visibile, pannello con squadra e pulsanti, OPZIONI. */
    fun giocoMenuEOpzioni() {
        val s = start(auto = "giallo.state.squadra") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue("in partita", waitFor(20_000) { s.inGame })
        assertTrue("pulsante MENU visibile", shotUntil("05-gioco") { boxAt(it, 7, rows() - 4, 7, 4) })
        // Il giocatore sale fino a stare sotto il Professore (5,2), poi un tocco nel vuoto (lontano, in basso a sinistra)
        // fa partire il dialogo; START sparisce durante il dialogo.
        var guard = 0
        while (s.latestSnapshot.get().y > 3 && guard++ < 20) press(s, NativeCore.KEY_UP)
        pump(800)
        tapTile(1f, rows() - 12f)
        assertTrue("dialogo avviato col tocco nel vuoto", waitFor(10_000) { !s.latestSnapshot.get().freeRoam })
        assertTrue("START nascosto e dialogo in alto (riga 9)", shotUntil("05b-dialogo") { !boxAt(it, 7, rows() - 4, 7, 4) && boxAt(it, 0, 9, 20, 6) })
        var k = 0
        while (!s.latestSnapshot.get().freeRoam && k++ < 20) press(s, NativeCore.KEY_B)
        waitFor(5_000) { s.latestSnapshot.get().freeRoam }
        assertTrue("START di nuovo visibile", shotUntil("05c-dopo-dialogo") { boxAt(it, 7, rows() - 4, 7, 4) })
        tapStart()
        assertTrue("riquadro dei pulsanti aperto", shotUntil("06-menu-aperto") { boxAt(it, 0, rows() - 13, 20, 9) })
        tapTile(4f, rows() - 7f)     // BORSA
        assertTrue("Borsa aperta", shotUntil("06b-borsa") { boxAt(it, 0, 3, 20, 3) })
        tapTile(4f, 3f)              // fuori dalle voci: niente
        root.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
        root.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK))
        pump(800)
        tapStart()    // MENU di nuovo
        pump(800)
        tapTile(15f, rows() - 10f)   // OPZIONI
        assertTrue("OPZIONI aperte", shotUntil("07-opzioni") { boxAt(it, 0, 10, 20, 5) })
    }

    /** Lotta (D-045): riquadro delle azioni alla riga 22 e delle mosse alla riga 27; tocco su FUGA arriva al gioco. */
    fun lotta() {
        val s = start(auto = "giallo.state") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue("in lotta", waitFor(20_000) { s.latestSnapshot.get().inBattle && s.latestSnapshot.get().battleMon != null })
        assertTrue("azioni e mosse prima", shotUntil("09-lotta") { boxAt(it, 0, 23, 20, 5) && boxAt(it, 0, 28, 20, 11) && boxAt(it, 6, 39, 8, 4) })
    }

    /**
     * Lotta col rivale fino alla fine, solo con tocchi (segnalazioni del committente): un tocco sulla mossa la usa
     * subito (anche al secondo turno con un'altra mossa); un tocco fuori dai riquadri manda avanti i testi della lotta;
     * dopo la vittoria i dialoghi vanno avanti e si torna a camminare.
     */
    fun lottaCompleta(onlyPanel: Boolean = false) {
        val s = start(auto = "giallo.state") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue("in lotta", waitFor(20_000) { s.latestSnapshot.get().battleMon != null })
        fun pp(i: Int) = s.latestSnapshot.get().battleMon?.moves?.getOrNull(i)?.pp ?: -1
        fun mainMenu() = s.battleMenuShown && !s.commandBusy
        assertTrue(waitFor(10_000) { mainMenu() })
        val moveRow = it.giallotouch.bridge.BattleLayout.MOVES_TOP_ROW + 2f
        var turn = 0
        var guard = 0
        while (s.latestSnapshot.get().inBattle && guard++ < 400) {
            if (mainMenu()) {
                val move = if (turn % 2 == 0) 0 else 1
                val before = pp(move)
                tapTile(4f, moveRow + 2 * move + 0.5f)
                assertTrue("turno $turn: un solo tocco usa la mossa $move", waitFor(15_000) { pp(move) == before - 1 })
                turn++
            } else if (onlyPanel) tapTile(10f, moveRow + 5f) else if (guard % 2 == 0) tapTile(10f, 2f)      // testo della lotta: tocco fuori dai riquadri = avanti
            else tapTile(10f, moveRow + 5f)                   // …e anche sui riquadri della lotta
        }
        shot("09c-dopo-lotta")
        assertFalse("la lotta è finita", s.latestSnapshot.get().inBattle)
        var k = 0
        while (!s.latestSnapshot.get().freeRoam && k++ < 200) tapTile(10f, 20f)
        assertTrue("dopo la lotta si torna a camminare", waitFor(10_000) { s.latestSnapshot.get().freeRoam })
        shot("09d-si-cammina")
    }

    /**
     * Ricevere un oggetto (segnalazione del committente: dialoghi bloccati, soprattutto quando si riceve un oggetto):
     * dopo la lotta col rivale si esce dal laboratorio, si va sulla Strada 1 e si parla con chi regala la Pozione,
     * poi si va avanti solo toccando lo schermo. Il dialogo deve finire e la Pozione essere nella borsa.
     */
    fun riceviOggetto(thenBattle: Boolean = false, usePotion: Boolean = false) {
        val s = start(auto = "giallo.state") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        // lotta: prima mossa e tocchi fino a tornare a camminare
        var guard = 0
        while (!(s.latestSnapshot.get().freeRoam && !s.latestSnapshot.get().inBattle) && guard++ < 500) {
            if (s.battleMenuShown && !s.commandBusy) tapTile(4f, it.giallotouch.bridge.BattleLayout.MOVES_TOP_ROW + 2.5f)
            else tapTile(10f, 2f)
        }
        assertTrue("di nuovo in giro", s.latestSnapshot.get().freeRoam)
        fun walkHeld(key: Int, until: () -> Boolean, max: Int = 40) {
            var k = 0
            while (!until() && k++ < max) { s.setKeys(key); pump(260); s.setKeys(0); pump(80) }
        }
        // Cammina lungo il percorso più breve con il camminatore dell'app (passi precisi a fotogrammi).
        fun goTo(x: Int, y: Int) {
            val map = s.positionForTest().first
            s.walkPathForTest(x, y)
            waitFor(30_000) { s.positionForTest().let { it.first != map || (it.second == x && it.third == y) } }
        }
        val lab = s.positionForTest().first
        // uscita del laboratorio: la casella percorribile più in basso, al centro
        val g = s.mapGridForTest()!!
        val door = (g.height - 1 downTo 0).firstNotNullOf { y -> (0 until g.width).sortedBy { kotlin.math.abs(it - g.width / 2) }.firstOrNull { g.isFree(it, y) }?.let { it to y } }
        goTo(door.first, door.second)
        walkHeld(NativeCore.KEY_DOWN, { s.positionForTest().first != lab })
        pump(1500)
        // Biancavilla: verso l'uscita a nord (casella percorribile più in alto)
        val town = s.positionForTest().first
        val g2 = s.mapGridForTest()!!
        // uscita a nord: il varco nella fila di alberi (riga 1), al centro
        val north = (0 until g2.width).sortedBy { kotlin.math.abs(it - g2.width / 2) }.first { g2.isFree(it, 1) && !g2.isFree(it - 1, 1).and(false) } to 1
        goTo(north.first, north.second)
        walkHeld(NativeCore.KEY_UP, { s.positionForTest().first != town })
        pump(1500)
        shot("20a-strada")
        // Strada 1: la persona più vicina; ci si va vicino e la si tocca (D-049: tocco = parla)
        val bag0 = s.latestSnapshot.get().bag.size
        // Sulla Strada 1 c'è l'erba alta: un Pokémon selvatico può attaccare. Si lotta e si riprova.
        fun fightIfNeeded() {
            var g = 0
            while (s.latestSnapshot.get().inBattle && g++ < 300) {
                if (s.battleMenuShown && !s.commandBusy) tapTile(4f, it.giallotouch.bridge.BattleLayout.MOVES_TOP_ROW + 2.5f) else tapTile(10f, 2f)
            }
            waitFor(10_000) { s.latestSnapshot.get().freeRoam }
        }
        var attempts = 0
        while (s.latestSnapshot.get().bag.size == bag0 && attempts++ < 4) {
            // La persona cammina: ci si mette accanto, girati verso di lei; poi un tocco sullo schermo.
            var tries = 0
            while (s.latestSnapshot.get().freeRoam && tries++ < 8) {
                val g4 = s.mapGridForTest()!!
                val here = s.positionForTest()
                val p = g4.people.minByOrNull { kotlin.math.abs(it.first - here.second) + kotlin.math.abs(it.second - here.third) } ?: break
                val spot = g4.interactionSpots(p.first, p.second).entries.minByOrNull { kotlin.math.abs(it.key.first - here.second) + kotlin.math.abs(it.key.second - here.third) } ?: break
                goTo(spot.key.first, spot.key.second)
                fightIfNeeded()
                s.setKeys(spot.value.key); pump(100); s.setKeys(0); pump(300)
                tapTile(10f, 20f)
                waitFor(3_000) { !s.latestSnapshot.get().freeRoam }
            }
            if (s.latestSnapshot.get().inBattle) { fightIfNeeded(); continue }
            shot("20-riceve-oggetto")
            // tocchi ravvicinati, anche mentre parte il messaggio dell'oggetto: il dialogo deve finire
            var taps = 0
            while (!s.latestSnapshot.get().freeRoam && taps++ < 60) tapTile(10f, 20f)
            assertTrue("il dialogo finisce toccando lo schermo", s.latestSnapshot.get().freeRoam || s.latestSnapshot.get().inBattle)
            fightIfNeeded()
        }
        shot("21-dopo-oggetto")
        assertTrue("oggetto nella borsa", s.latestSnapshot.get().bag.size > bag0)
        // Lotta con un selvatico nell'erba (mete a caso sulla Strada 1), fino al menu di lotta.
        fun wildBattle() {
            val rnd = java.util.Random(7)
            var walks = 0
            while (!s.latestSnapshot.get().inBattle && walks++ < 25) {
                val p = s.positionForTest()
                if (p.first != 12) break
                val g5 = s.mapGridForTest() ?: break
                val free = (0 until g5.width).flatMap { x -> (4 until g5.height - 4).map { y -> x to y } }.filter { g5.isFree(it.first, it.second) }
                val target = free[rnd.nextInt(free.size)]
                goTo(target.first, target.second)
                waitFor(500) { s.latestSnapshot.get().inBattle }
            }
            assertTrue("lotta con un selvatico", waitFor(10_000) { s.latestSnapshot.get().inBattle })
            var intro = 0
            while (!(s.battleMenuShown && !s.commandBusy) && intro++ < 30) tapTile(10f, 2f)
            assertTrue("menu di lotta", s.battleMenuShown)
        }
        fun growlUntilHurt() {
            var turns = 0
            while (s.latestSnapshot.get().battleMon!!.hp >= s.latestSnapshot.get().battleMon!!.maxHp && turns++ < 8) {
                tapTile(4f, it.giallotouch.bridge.BattleLayout.MOVES_TOP_ROW + 4.5f)
                var t = 0
                while (!(s.battleMenuShown && !s.commandBusy) && s.latestSnapshot.get().inBattle && t++ < 40) tapTile(10f, 2f)
            }
        }
        if (usePotion) {
            // Pikachu deve aver perso un po' di vita: lotta con un selvatico, RUGGITO finché viene colpito, poi FUGA.
            wildBattle(); growlUntilHurt()
            var runs = 0
            while (s.latestSnapshot.get().inBattle && runs++ < 10) {
                if (s.battleMenuShown && !s.commandBusy) tapTile(9f, 40.5f) else tapTile(10f, 2f)
            }
            assertTrue("fuggiti", waitFor(15_000) { s.latestSnapshot.get().freeRoam })
            pump(1500)
            // BORSA dell'app → POZIONE → USA → "SU CHI?" dell'app → PIKACHU (D-051): niente menu vecchi, si torna a camminare.
            tapStart(); pump(800)
            tapTile(4f, 36f)                      // BORSA
            assertTrue("borsa", shotUntil("26-borsa") { b -> boxAt(b, 0, 3, 20, 3) })
            tapTile(4f, 10f); pump(800)           // POZIONE
            tapTile(4f, 37f)                      // USA
            assertTrue("su chi? dell'app", shotUntil("27-su-chi") { b -> boxAt(b, 0, 3, 20, 3) && boxAt(b, 0, 7, 20, 32) && boxAt(b, 5, 39, 10, 4) })
            val hpBefore = s.latestSnapshot.get().party[0].hp
            tapTile(4f, 9f)                       // PIKACHU
            waitFor(20_000) { !s.itemBusy }

            shot("28-dopo-uso")
            var t = 0
            while (!s.latestSnapshot.get().freeRoam && t++ < 30) { tapTile(10f, 20f); if (t == 1) shot("28b-messaggio") }
            assertTrue("di nuovo a camminare", waitFor(15_000) { s.latestSnapshot.get().freeRoam })
            shot("29-di-nuovo-in-giro")
            assertTrue("Pikachu curato", s.latestSnapshot.get().party[0].hp > hpBefore)
            assertEquals("Pozione consumata", 0, s.latestSnapshot.get().bag.count { it.id == 0x14 })
            return
        }
        if (!thenBattle) return
        // Lotta con un selvatico, poi BORSA dell'app → POZIONE (D-050).
        wildBattle()
        val row = it.giallotouch.bridge.BattleLayout.ACTIONS_TOP_ROW + 2.5f
        tapTile(3f, row)                                           // SQUADRA
        assertTrue("lista SQUADRA dell'app", shotUntil("22-lotta-squadra") { b -> boxAt(b, 0, 23, 20, 16) && boxAt(b, 5, 39, 10, 4) })
        tapTile(9f, 40.5f)                                         // INDIETRO
        assertTrue("di nuovo azioni e mosse", shotUntil("22b-lotta") { b -> boxAt(b, 0, 28, 20, 11) })
        // la Pozione si usa solo se Pikachu non ha tutta la vita: qualche turno con RUGGITO (nessun danno al selvatico)
        growlUntilHurt()
        val hp0 = s.latestSnapshot.get().battleMon!!.hp; val max = s.latestSnapshot.get().battleMon!!.maxHp
        val potions0 = s.latestSnapshot.get().bag.first { it.id == 0x14 }.quantity
        tapTile(15f, row)                                          // BORSA
        assertTrue("lista BORSA dell'app", shotUntil("23-lotta-borsa") { b -> boxAt(b, 0, 23, 20, 16) })
        if (hp0 < max) {
            tapTile(4f, 25.5f)                                     // POZIONE (prima voce)
            assertTrue("Pozione usata", waitFor(30_000) { (s.latestSnapshot.get().bag.firstOrNull { it.id == 0x14 }?.quantity ?: 0) < potions0 })
            assertTrue("vita di Pikachu salita", waitFor(10_000) { (s.latestSnapshot.get().battleMon?.hp ?: 0) > hp0 })
            shot("24-dopo-pozione")
            var t2 = 0
            while (!s.battleMenuShown && s.latestSnapshot.get().inBattle && t2++ < 40) tapTile(10f, 2f)
            assertTrue("di nuovo il menu di lotta", s.battleMenuShown || !s.latestSnapshot.get().inBattle)
            shot("25-lotta-dopo-pozione")
        } else println("Pikachu ha tutta la vita: Pozione non provata")
    }

    fun riceviEUsaInLotta() = riceviOggetto(thenBattle = true)

    /** In lotta BORSA e SQUADRA aprono le liste dell'app sopra la lotta (D-050, versione C); INDIETRO torna alle mosse. */
    fun lottaBorsa() {
        val s = start(auto = "giallo.state") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue("menu di lotta", waitFor(20_000) { s.battleMenuShown && !s.commandBusy })
        val row = it.giallotouch.bridge.BattleLayout.ACTIONS_TOP_ROW + 2.5f
        val moves = it.giallotouch.bridge.BattleLayout.MOVES_TOP_ROW
        for ((col, name) in listOf(15f to "borsa", 3f to "squadra")) {
            tapTile(col, row)
            assertTrue("lista $name dell'app", shotUntil("09e-lotta-$name") { b -> boxAt(b, 0, 23, 20, 16) && boxAt(b, 5, 39, 10, 4) })
            assertFalse("nessun menu del gioco", s.battleGameMenu)
            tapTile(9f, 40.5f)   // INDIETRO
            assertTrue("di nuovo le mosse ($name)", shotUntil("09f-lotta-$name") { b -> boxAt(b, 0, moves, 20, 11) })
        }
    }

    /** Scheda allenatore (D-045): START → nome del giocatore apre la scheda del gioco ridisposta; INDIETRO chiude. */
    fun schedaAllenatore() {
        val s = start(auto = "giallo.state.squadra") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue("in partita", waitFor(20_000) { s.inGame && s.latestSnapshot.get().freeRoam })
        tapStart()
        assertTrue("menu aperto", shotUntil("10-menu-allenatore") { boxAt(it, 0, rows() - 15, 20, 11) })
        tapTile(14f, rows() - 13f)   // nome del giocatore (a destra di POKéMON)
        // Durante il passaggio dal menu START del gioco non si vede nessun menu vecchio (D-049): foto da guardare.
        for (i in 0 until 4) { pump(250); shot("10b-passaggio-$i") }
        assertTrue("scheda del gioco aperta", waitFor(20_000) { s.trainerCardShown })
        assertTrue("riquadri della scheda", shotUntil("11-scheda-allenatore", 30_000) { boxAt(it, 0, 28, 20, 9) && boxAt(it, 5, 39, 10, 4) })
        tapTile(9f, 40.5f)           // INDIETRO
        assertTrue("di nuovo in giro", waitFor(15_000) { !s.trainerCardShown && s.latestSnapshot.get().freeRoam })
        shot("12-dopo-scheda")
    }

    /** START → POKéMON (squadra con icone) → scheda del Pokémon → indietro; START → POKéDEX; START → OPZIONI (D-046). */
    fun menuSquadraPokedex() {
        val s = start(auto = "giallo.state.squadra") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue("in partita", waitFor(20_000) { s.inGame && s.latestSnapshot.get().freeRoam })
        tapStart()
        assertTrue("START aperto", shotUntil("13-start") { boxAt(it, 0, 28, 20, 11) && boxAt(it, 0, 2, 20, 8) })
        tapTile(15f, 36f)            // SALVA
        assertTrue("messaggio nello stile del gioco", shotUntil("13b-salvata") { boxAt(it, 0, 9, 20, 6) })
        pump(2500)
        tapStart()
        assertTrue("START aperto di nuovo", shotUntil("13c-start") { boxAt(it, 0, 28, 20, 11) })
        tapTile(4f, 30f)             // POKéMON
        assertTrue("squadra", shotUntil("14-squadra") { boxAt(it, 0, 7, 20, 32) && boxAt(it, 0, 39, 10, 4) && boxAt(it, 10, 39, 10, 4) })
        tapTile(4f, 9f)              // primo compagno
        assertTrue("scheda del Pokémon", shotUntil("15-scheda-pokemon") { boxAt(it, 0, 12, 20, 11) && boxAt(it, 0, 24, 20, 11) && boxAt(it, 5, 39, 10, 4) })
        tapTile(9f, 40.5f)           // INDIETRO → squadra
        assertTrue("di nuovo la squadra", shotUntil("16-squadra-2") { boxAt(it, 10, 39, 10, 4) })
        tapTile(4f, 40.5f)           // INDIETRO → gioco
        pump(800)
        tapStart()
        pump(800)
        tapTile(4f, 33f)             // POKéDEX
        assertTrue("Pokédex", shotUntil("17-pokedex") { boxAt(it, 0, 7, 20, 27) && boxAt(it, 0, 34, 20, 5) })
        // prima specie vista in cima alla lista: tocco = scheda (D-054); SUCCESSIVO porta alla specie vista dopo
        tapTile(8f, 9.5f)
        assertTrue("scheda della specie", shotUntil("17b-pokedex-scheda") { boxAt(it, 0, 22, 20, 12) && boxAt(it, 12, 7, 6, 6) })
        tapTile(15f, 10f)            // SUCCESSIVO
        shot("17c-pokedex-successivo")
        tapTile(9f, 40.5f)           // INDIETRO → lista
        tapTile(9f, 40.5f)
        pump(800)
        tapStart()
        pump(800)
        tapTile(15f, 33f)            // OPZIONI
        // OPZIONI uniche (D-075, D-080, D-082): un solo riquadro GIOCO dalla riga 6 (COLORI alle righe 23-24)
        assertTrue("OPZIONI", shotUntil("18-opzioni") { boxAt(it, 0, 6, 20, 24) })
        tapTile(4f, 23.5f)           // COLORI: bianco e nero
        assertTrue("opzione bianco e nero", waitFor(3_000) { s.blackWhite })
        tapTile(9f, 40.5f)           // INDIETRO
        shot("18b-bianco-e-nero")
        tapStart(); pump(800); tapTile(15f, 33f); pump(800)
        tapTile(4f, 23.5f)           // di nuovo a colori
        assertTrue("di nuovo a colori", waitFor(3_000) { !s.blackWhite })
    }

    /** Domanda Sì/No del gioco: due riquadri dell'app sotto il dialogo (D-046). */
    fun siNo() {
        // Partita caricata a dialogo già aperto: il dialogo resta al suo posto (non c'è un'immagine della mappa senza),
        // le risposte compaiono comunque nei riquadri dell'app.
        val s = start(auto = "giallo.state.text") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        var k = 0
        while (s.yesNo == null && k++ < 30) { press(s, NativeCore.KEY_A); pump(600) }
        assertTrue("domanda Sì/No", waitFor(20_000) { s.yesNo != null })
        assertTrue("riquadri SI e NO", shotUntil("19-si-no") { boxAt(it, 2, 37, 6, 5) && boxAt(it, 12, 37, 6, 5) })
    }

    /** La schermata del nome nello stile del gioco, sopra la tastiera del telefono. */
    fun nome() {
        val s = start(auto = "giallo.state.naming") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue("schermata del nome", waitFor(20_000) { s.latestSnapshot.get().naming != null })
        assertTrue("riquadro FINE", shotUntil("08-nome", 30_000) { boxAt(it, 12, rows() - 6, 8, 4) || boxAt(it, 12, rows() - 8, 8, 4) })
    }
}

class AppStartScreensTest : AppScreensHarness() {
    @Test fun avvio() = avvioMenuECaricaPartita()
}

class AppGameScreensTest : AppScreensHarness() {
    @Test fun gioco() = giocoMenuEOpzioni()
}

class AppNameScreenTest : AppScreensHarness() {
    @Test fun schermataNome() = nome()
}

class AppBattleScreenTest : AppScreensHarness() {
    @Test fun schermataLotta() = lotta()
}

/** Mossa senza PP (D-084): "Non hai più PP per questa mossa!" senza il riquadro TIPO/PP del gioco sulla scena. */
class AppNoPpTest : AppScreensHarness() {
    @Test fun senzaPP() {
        val s = start(auto = "giallo.state") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue("menu di lotta", waitFor(20_000) { s.battleMenuShown && !s.commandBusy })
        s.writeMemoryForTest(0xD018 + 25, 0)   // PP della prima mossa del Pokémon in campo
        pump(300)
        tapTile(4f, it.giallotouch.bridge.BattleLayout.MOVES_TOP_ROW + 2.5f)
        pump(2500)
        shot("50-senza-pp")
    }
}

class AppForgetMoveTest : AppScreensHarness() {
    /** Mossa da dimenticare (D-085): SÌ, poi tocco su RUGGITO nel riquadro dell'app → ATT. RAPIDO al suo posto. */
    @Test fun dimentica() {
        val s = start(auto = "giallo.state.mossa") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        pump(1500)
        shot("60-mossa-domanda")
        s.tileSheet.get()?.let { sh ->
            val b = Bitmap.createBitmap(sh, 128, 128, Bitmap.Config.ARGB_8888)
            FileOutputStream(File(shots, "foglio-tile.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        tapTile(5f, 39f)          // SÌ
        assertTrue("riquadro delle mosse dell'app", waitFor(5_000) { s.forgetMoves != null })
        pump(600)
        assertTrue("mosse sotto il testo e NUOVA MOSSA in alto", shotUntil("61-mossa-elenco") { boxAt(it, 0, 32, 20, 10) && boxAt(it, 0, 1, 20, 9) })
        tapTile(6f, 35.5f)        // RUGGITO (seconda mossa)
        var k = 0
        while (k++ < 30 && s.latestSnapshot.get().party.first().moves.none { it.id == 98 }) { pump(400); if (s.yesNo == null && s.forgetMoves == null && !s.battleMenuShown) press(s, NativeCore.KEY_A) }
        shot("62-mossa-dopo")
        val ids = s.latestSnapshot.get().party.first().moves.map { it.id }
        assertTrue("ATT. RAPIDO imparata al posto di RUGGITO: $ids", 98 in ids && 45 !in ids)
    }
}

/** NON IMPARARE (D-085): B nel gioco, poi SÌ alla conferma → le mosse restano quelle di prima. */
class AppKeepMovesTest : AppScreensHarness() {
    @Test fun nonImparare() {
        val s = start(auto = "giallo.state.mossa") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        pump(1500)
        tapTile(5f, 39f)          // SÌ (dimenticare una mossa)
        assertTrue(waitFor(5_000) { s.forgetMoves != null })
        pump(600)
        tapTile(10f, 12.5f)       // NON IMPARARE
        assertTrue("domanda di conferma", waitFor(8_000) { s.yesNo != null })
        shot("63-non-imparare-conferma")
        tapTile(5f, 39f)          // SÌ: non impara
        var k = 0
        while (k++ < 20 && !s.battleMenuShown) { pump(400); if (s.yesNo == null && s.forgetMoves == null) press(s, NativeCore.KEY_A) }
        shot("64-non-imparare-dopo")
        val ids = s.latestSnapshot.get().party.first().moves.map { it.id }
        assertEquals("mosse di prima", listOf(84, 45, 39, 86), ids)
    }
}

class AppTrainerScreenTest : AppScreensHarness() {
    @Test fun scheda() = schedaAllenatore()
}

class AppMenuScreensTest : AppScreensHarness() {
    @Test fun squadraPokedexOpzioni() = menuSquadraPokedex()
}

class AppYesNoScreenTest : AppScreensHarness() {
    @Test fun domandaSiNo() = siNo()
}

class AppBattleFullTest : AppScreensHarness() {
    @Test fun lottaFinoAllaFine() = lottaCompleta()
}

class AppBattlePanelTapTest : AppScreensHarness() {
    @Test fun testiAvantiToccandoIRiquadri() = lottaCompleta(onlyPanel = true)
}

class AppBattleBagTest : AppScreensHarness() {
    @Test fun borsaInLotta() = lottaBorsa()
}

class AppReceiveItemTest : AppScreensHarness() {
    @Test fun riceviPozione() = riceviOggetto()
}

class AppBattleItemTest : AppScreensHarness() {
    @Test fun pozioneInLotta() = riceviEUsaInLotta()
}

class AppUseItemTest : AppScreensHarness() {
    @Test fun pozioneSuPikachu() = riceviOggetto(usePotion = true)
}

/**
 * NUOVO GIOCO: le liste dei nomi proposti dal Professore (giocatore e rivale) sono riquadri dell'app sotto il dialogo;
 * un tocco sul nome lo sceglie (D-052). Il riquadro del gioco non si vede.
 */
class AppNameListTest : AppScreensHarness() {
    @Test fun nomiProposti() {
        val s = start(auto = "giallo.state.title") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        toMainMenu(s)
        val frameTop = (root.height - 144 * unit) / 2 / (8 * unit)
        tapTile(4f, frameTop + it.giallotouch.bridge.StartMenuLayout.row(it.giallotouch.bridge.StartMenuLayout.entries(s.mainMenuItems).size - 1) + 0.5f)
        for (round in 0 until 2) {
            var k = 0
            while (s.nameList == null && k++ < 80) tapTile(15f, 30f)
            val names = s.nameList
            if (names == null) shot("30-lista-nomi-manca-$round")
            assertTrue("lista dei nomi ${round + 1}", names != null)
            assertTrue("riquadri dell'app", shotUntil("30-lista-nomi-$round") { b -> boxAt(b, 0, 15, 11, 11) && boxAt(b, 3, 28, 14, 5) })
            tapTile(4f, 17.5f)                   // primo nome pronto
            assertTrue("scelto", waitFor(10_000) { s.nameList == null })
            assertTrue("nessuna tastiera dei nomi", s.latestSnapshot.get().naming == null)
        }
        shot("31-dopo-nomi")
    }
}


/**
 * PC della cameretta (D-055): tocco davanti al PC → schermata STRUMENTI PC dell'app; DEPOSITA → POZIONE (dalla borsa
 * al PC); INDIETRO; tocco su POZIONE nel PC (di nuovo nella borsa); INDIETRO → si torna a camminare.
 */
class AppPcTest : AppScreensHarness() {
    @Test fun strumentiPc() {
        val s = start(auto = "giallo.state.bag") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue(waitFor(10_000) { s.latestSnapshot.get().freeRoam })
        assertEquals("Pozione nella borsa", 1, s.latestSnapshot.get().bag.size)
        tapTile(10f, 20f)                                   // davanti al PC: tocco = A
        var k = 0
        while (!s.pcMenuShown && k++ < 10) tapTile(10f, 20f)   // "GIALLO accende il PC."
        assertTrue("STRUMENTI PC dell'app", shotUntil("32-pc") { b -> boxAt(b, 0, 3, 20, 3) && boxAt(b, 0, 7, 20, 31) && boxAt(b, 0, 39, 11, 4) && boxAt(b, 11, 39, 9, 4) })
        tapTile(15f, 41f); pump(500)                         // AZIONI
        assertTrue("riquadro AZIONI", shotUntil("32b-pc-azioni") { b -> boxAt(b, 7, 27, 13, 12) })
        tapTile(13f, 30f)                                   // DEPOSITA
        assertTrue("borsa da depositare", shotUntil("33-pc-deposita") { b -> !boxAt(b, 15, 39, 5, 4) && !boxAt(b, 7, 27, 13, 12) })
        tapTile(4f, 9.5f)                                   // POZIONE
        assertTrue("Pozione nel PC", waitFor(30_000) { !s.itemBusy && s.latestSnapshot.get().pcItems.any { it.id == 0x14 } })
        assertTrue("borsa vuota", waitFor(5_000) { s.latestSnapshot.get().bag.isEmpty() })
        shot("34-pc-dopo-deposito")
        tapTile(9f, 40.5f)                                  // INDIETRO (da solo, centrato) → strumenti del PC
        pump(800)
        tapTile(4f, 9.5f)                                   // POZIONE: PRELEVA
        assertTrue("Pozione di nuovo nella borsa", waitFor(30_000) { !s.itemBusy && s.latestSnapshot.get().bag.any { it.id == 0x14 } })
        shot("35-pc-dopo-prelievo")
        // BUTTA: deposita di nuovo, poi AZIONI → BUTTA → POZIONE → quantità → OK
        tapTile(15f, 41f); pump(500); tapTile(13f, 30f); pump(500); tapTile(4f, 9.5f)
        assertTrue("Pozione nel PC", waitFor(30_000) { !s.itemBusy && s.latestSnapshot.get().pcItems.any { it.id == 0x14 } })
        tapTile(9f, 40.5f); pump(800)
        tapTile(15f, 41f); pump(500); tapTile(13f, 35f); pump(500)    // AZIONI → BUTTA
        tapTile(4f, 9.5f); pump(500)                                    // POZIONE
        assertTrue("quantità da buttare", shotUntil("35b-pc-butta") { b -> boxAt(b, 0, 29, 20, 9) && boxAt(b, 0, 39, 11, 4) })
        tapTile(15f, 41f)                                               // OK
        assertTrue("Pozione buttata", waitFor(30_000) { !s.itemBusy && s.latestSnapshot.get().pcItems.isEmpty() })
        shot("35c-pc-dopo-butta")
        tapTile(9f, 40.5f); pump(800)                                   // INDIETRO → strumenti del PC
        tapTile(4f, 40.5f)                                  // INDIETRO → si spegne il PC
        assertTrue("di nuovo in giro", waitFor(20_000) { s.latestSnapshot.get().freeRoam })
        shot("36-pc-chiuso")
    }
}

/**
 * Partite di prova costruite giocando (solo su richiesta: -Pgiallo.scenario=CARTELLA): dalla lotta col rivale si va
 * a Smeraldopoli, si riceve il Pacco al Pokémon Market, lo si porta a Oak e si torna al negozio. A ogni tappa lo
 * stato del gioco si salva nella cartella (fuori dal repository: contiene il gioco).
 */
class ZzScenarioTest : AppScreensHarness() {
    private lateinit var s: GameSession
    /** Registro dei passi, scritto subito su file (si legge mentre la partita automatica gira). */
    private fun log(msg: String) {
        val dir = System.getProperty("giallo.scenario") ?: return
        java.io.File(dir, "registro.txt").appendText("${java.time.LocalTime.now()} $msg ${runCatching { pos() }.getOrNull()}\n")
        println("DBGSC $msg")
    }
    private fun pos() = s.positionForTest()
    private fun goTo(x: Int, y: Int) {
        log("vado a ($x,$y)")
        val map = pos().first
        s.walkPathForTest(x, y)
        waitFor(40_000) { pos().let { it.first != map || (it.second == x && it.third == y) } || s.latestSnapshot.get().inBattle }
        fightIfNeeded()
    }
    private fun fightIfNeeded() {
        var g = 0
        while (s.latestSnapshot.get().inBattle && g++ < 400) {
            val pp0 = s.latestSnapshot.get().party.firstOrNull()?.moves?.firstOrNull()?.pp ?: 1
            if (s.battleMenuShown && !s.commandBusy && pp0 == 0) tapTile(10f, 34f)   // PP finiti: FUGA
            else if (s.battleMenuShown && !s.commandBusy) tapTile(4f, it.giallotouch.bridge.BattleLayout.MOVES_TOP_ROW + 2.5f)
            else if (!s.commandBusy) press(s, NativeCore.KEY_A) else pump(200)
        }
        textThrough()
    }
    private fun textThrough(max: Int = 80) {
        var k = 0
        while (!s.latestSnapshot.get().freeRoam && !s.latestSnapshot.get().inBattle && k++ < max) {
            // domanda Sì/No (per esempio "Vuoi dare un soprannome?"): sempre NO; schermata del nome: FINE col nome proposto
            if (s.yesNo != null) tapTile(14f, 39f)
            else if (s.latestSnapshot.get().naming != null) skipNaming()
            else tapTile(10f, 2f)
            if (k % 20 == 0) log("testo, tocco $k")
        }
        if (s.latestSnapshot.get().inBattle) fightIfNeeded()
    }
    /** Schermata del nome (soprannome): START e poi A, come nel gioco: resta il nome della specie. */
    private fun skipNaming() {
        log("schermata del nome: START, A")
        press(s, NativeCore.KEY_START); pump(500); press(s, NativeCore.KEY_A); pump(1500)
    }

    private fun save(name: String) {
        val dir = java.io.File(System.getProperty("giallo.scenario")!!)
        assertTrue(s.saveStateForTest(java.io.File(dir, name)))
        shot("scen-" + name.removeSuffix(".state"))
        log("salvato $name")
        println("DBGSC salvato $name a ${pos()} squadra=${s.latestSnapshot.get().party.map { it.name }} borsa=${s.latestSnapshot.get().bag}")
    }
    /** Entra nella porta verso [dest] (o una qualsiasi se null): ci si va sopra, poi si spinge verso l'esterno. */
    private fun warpTo(dest: Int?): Boolean {
        val map = pos().first
        repeat(4) {
            val w = s.warpsForTest().firstOrNull { dest == null || it.third == dest } ?: return false
            val (wy, wx) = w.first to w.second
            goTo(wx, wy)
            for (d in listOf(NativeCore.KEY_DOWN, NativeCore.KEY_UP, NativeCore.KEY_LEFT, NativeCore.KEY_RIGHT)) {
                if (pos().first != map) break
                s.setKeys(d); pump(400); s.setKeys(0); pump(600)
            }
            if (waitFor(4_000) { pos().first != map }) { pump(1500); return true }
            // porta non raggiungibile: si prova dalla casella sotto
            goTo(wx, wy + 1); s.setKeys(NativeCore.KEY_UP); pump(500); s.setKeys(0)
            if (waitFor(4_000) { pos().first != map }) { pump(1500); return true }
        }
        return false
    }
    /** Esce dal bordo nord (o sud) del luogo: prova le caselle libere del bordo finché ne raggiunge una. */
    private fun edge(north: Boolean): Boolean {
        val map = pos().first
        val g = s.mapGridForTest() ?: return false
        val rows = if (north) (0 until 3) else (g.height - 1 downTo g.height - 3)
        val here = pos()
        val candidates = rows.flatMap { y -> (0 until g.width).filter { g.isFree(it, y) }.map { it to y } }
            .filter { g.path(here.second, here.third, setOf(it)) != null }      // solo caselle raggiungibili
            .sortedBy { kotlin.math.abs(it.first - here.second) }
        log("bordo ${if (north) "nord" else "sud"}: ${candidates.size} caselle raggiungibili")
        for (c in candidates.take(12)) {
            goTo(c.first, c.second)
            if (pos().first != map) { pump(1500); return true }
            if (pos().second != c.first || pos().third != c.second) continue
            var k = 0
            while (pos().first == map && k++ < 6) { s.setKeys(if (north) NativeCore.KEY_UP else NativeCore.KEY_DOWN); pump(300); s.setKeys(0); pump(200) }
            if (pos().first != map) { pump(1500); return true }
        }
        return false
    }

    /** Quarta tappa (da -Pgiallo.state.shop=two.state): al Centro Pokémon di Smeraldopoli. */
    @Test fun costruisci4() {
        assumeTrue(System.getProperty("giallo.scenario") != null && System.getProperty("giallo.state.shop") != null)
        s = start(auto = "giallo.state.shop") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        waitFor(10_000) { s.latestSnapshot.get().freeRoam }
        var k = 0
        while (pos().first != 1 && k++ < 6) { edge(north = true); log("verso nord") }
        assertEquals("Smeraldopoli", 1, pos().first)
        assertTrue("nel Centro Pokémon", warpTo(0x29))
        textThrough()
        save("center.state")
    }

    /**
     * Quinta tappa (da -Pgiallo.state.grind=center.state, Centro di Smeraldopoli): lotte nell'erba della Strada 1 col
     * primo Pokémon finché il gioco chiede di dimenticare una mossa; cura al Centro quando i PS scendono sotto metà.
     * Salva mossa.state appena compare la domanda (per il mockup della mossa da dimenticare) e livello-N.state ogni
     * livello guadagnato.
     */
    @Test fun costruisci5() {
        assumeTrue(System.getProperty("giallo.scenario") != null && System.getProperty("giallo.state.grind") != null)
        s = start(auto = "giallo.state.grind") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        waitFor(10_000) { s.latestSnapshot.get().freeRoam }
        val rnd = java.util.Random(23)
        fun lead() = s.latestSnapshot.get().party.firstOrNull()
        fun heal() {
            var k = 0
            while (pos().first != 1 && k++ < 6) edge(north = true)
            if (pos().first == 1) warpTo(0x29)
            textThrough()
            if (pos().first != 41) { log("Centro non raggiunto"); return }
            goTo(3, 3)
            s.setKeys(NativeCore.KEY_UP); pump(150); s.setKeys(0); pump(400)
            tapTile(10f, 20f)
            k = 0
            while (s.yesNo == null && k++ < 20) { tapTile(10f, 20f); pump(300) }
            tapTile(5f, 39f)
            k = 0
            while (!s.latestSnapshot.get().freeRoam && k++ < 30) { tapTile(10f, 20f); pump(400) }
            log("curato: ${lead()}")
        }
        fun toRoute() {
            if (pos().first == 41) warpTo(null)
            var k = 0
            while (pos().first != 12 && k++ < 4) edge(north = false)
        }
        var level = lead()?.level ?: 0
        var battles = 0
        if (pos().first == 41) { heal(); toRoute() }
        while (battles < 120) {
            if (pos().first != 12) { heal(); toRoute(); continue }
            var walks = 0
            while (!s.latestSnapshot.get().inBattle && walks++ < 25) {
                val gg = s.mapGridForTest() ?: break
                // erba della Strada 1 dove si incontrano i selvatici (x 8..16, y 9..24), solo caselle raggiungibili
                val free = (8..16).flatMap { x -> (9..24).map { y -> x to y } }
                    .filter { gg.isFree(it.first, it.second) && gg.path(pos().second, pos().third, setOf(it)) != null }
                if (free.isEmpty()) break
                val t = free[rnd.nextInt(free.size)]
                s.walkPathForTest(t.first, t.second)
                waitFor(15_000) { s.latestSnapshot.get().inBattle || pos().let { it.second == t.first && it.third == t.second } }
            }
            if (!s.latestSnapshot.get().inBattle) continue
            battles++
            pump(1500); shot("grind-%03d".format(battles))
            var g = 0
            while ((s.latestSnapshot.get().inBattle || !s.latestSnapshot.get().freeRoam) && g++ < 500) {
                if (s.yesNo != null) {
                    // "... vuole imparare ...; dimenticare una mossa?" (o un'altra domanda): si salva e ci si ferma
                    save("mossa.state")
                    log("domanda in lotta dopo $battles lotte: ${lead()}")
                    return
                }
                if (s.battleMenuShown && !s.commandBusy && (lead()?.moves?.firstOrNull()?.pp ?: 1) == 0) tapTile(10f, 34f)   // PP finiti: FUGA
                else if (s.battleMenuShown && !s.commandBusy) tapTile(4f, it.giallotouch.bridge.BattleLayout.MOVES_TOP_ROW + 2.5f)
                else if (!s.commandBusy) press(s, NativeCore.KEY_A) else pump(200)   // testi della lotta: tasto A
            }
            val m = lead() ?: break
            log("lotta $battles finita: ${m.name} L${m.level} PS ${m.hp}/${m.maxHp}")
            if (m.level > level) { level = m.level; save("livello-$level.state") }
            if (m.hp * 2 < m.maxHp || (m.moves.firstOrNull()?.pp ?: 30) < 10) {
                // solo per costruire la partita di prova: PS al massimo e PP di TUONOSHOCK pieni scritti in memoria,
                // senza andare al Centro (il viaggio si bloccava negli incontri lungo la strada)
                val base = 0xD16F
                s.writeMemoryForTest(base + 1, m.maxHp shr 8); s.writeMemoryForTest(base + 2, m.maxHp and 0xFF)
                s.writeMemoryForTest(base + 29, 30)
                pump(500)
                log("curato in memoria: ${lead()}")
            }
        }
        log("fine senza domanda dopo $battles lotte")
    }

    /** Terza tappa (da -Pgiallo.state.items=shop.state): 5 Poké Ball al negozio, cattura sulla Strada 1. */
    @Test fun costruisci3() {
        assumeTrue(System.getProperty("giallo.scenario") != null && System.getProperty("giallo.state.items") != null && System.getProperty("giallo.state.shop") == null)
        s = start(auto = "giallo.state.items") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        waitFor(10_000) { s.latestSnapshot.get().freeRoam }
        // dal commesso: COMPRA → POKé BALL → quantità 5 (cursore) → OK
        val g = s.mapGridForTest()!!
        val clerk = g.people.minByOrNull { it.first }!!
        val spot = g.interactionSpots(clerk.first, clerk.second).entries.first { g.path(pos().second, pos().third, setOf(it.key)) != null }
        goTo(spot.key.first, spot.key.second)
        s.setKeys(spot.value.key); pump(150); s.setKeys(0); pump(400)
        tapTile(10f, 20f)
        var k = 0
        while (!s.shopMenuShown && k++ < 10) tapTile(10f, 20f)
        pump(1500); tapTile(4f, 35f); pump(1500)            // COMPRA
        tapTile(4f, 14.5f); pump(1000)                       // POKé BALL
        tapTile(7f, 41f); pump(800)                          // cursore a metà: circa 7 Poké Ball
        log("quantità scelta, soldi ${s.latestSnapshot.get().money}")
        tapTile(16f, 41f)                                    // OK
        waitFor(40_000) { !s.itemBusy && s.latestSnapshot.get().bag.any { it.id == 0x04 } }
        log("borsa ${s.latestSnapshot.get().bag}")
        tapTile(9f, 40.5f); pump(800); tapTile(9f, 40f)       // INDIETRO, ESCI
        waitFor(20_000) { s.latestSnapshot.get().freeRoam }
        assertTrue("Poké Ball comprate", s.latestSnapshot.get().bag.any { it.id == 0x04 })
        assertTrue("fuori dal negozio", warpTo(null))
        k = 0
        while (pos().first != 12 && k++ < 4) edge(north = false)
        assertEquals("Strada 1", 12, pos().first)
        // nell'erba: lotta, BORSA → POKé BALL finché un Pokémon è catturato
        val rnd = java.util.Random(11)
        var tries = 0
        while (s.latestSnapshot.get().party.size < 2 && tries++ < 12) {
            var walks = 0
            while (!s.latestSnapshot.get().inBattle && walks++ < 20) {
                val gg = s.mapGridForTest() ?: break
                val free = (0 until gg.width).flatMap { x -> (6 until gg.height - 4).map { y -> x to y } }.filter { gg.isFree(it.first, it.second) }
                val t = free[rnd.nextInt(free.size)]
                s.walkPathForTest(t.first, t.second)
                waitFor(30_000) { s.latestSnapshot.get().inBattle || pos().let { it.second == t.first && it.third == t.second } }
            }
            if (!s.latestSnapshot.get().inBattle) continue
            log("lotta con un selvatico")
            var g2 = 0
            while (s.latestSnapshot.get().inBattle && g2++ < 300) {
                if (g2 % 15 == 0) log("lotta: giro $g2 menu=${s.battleMenuShown} busy=${s.commandBusy}/${s.battleBusy} siNo=${s.yesNo != null} nome=${s.latestSnapshot.get().naming != null} borsa=${s.latestSnapshot.get().bag}")
                if (s.battleMenuShown && !s.commandBusy && !s.battleBusy) {
                    val ball = s.latestSnapshot.get().bag.indexOfFirst { it.id == 0x04 }
                    if (ball < 0) { tapTile(4f, it.giallotouch.bridge.BattleLayout.MOVES_TOP_ROW + 2.5f); continue }
                    tapTile(15f, it.giallotouch.bridge.BattleLayout.ACTIONS_TOP_ROW + 2.5f)   // BORSA
                    pump(800)
                    tapTile(4f, 25.5f + 3 * ball)                                             // POKé BALL
                    waitFor(30_000) { !s.battleBusy }
                } else if (s.yesNo != null) tapTile(14f, 39f)        // soprannome? NO
                else if (s.latestSnapshot.get().naming != null) skipNaming()
                else tapTile(10f, 2f)
            }
            textThrough()
            if (s.latestSnapshot.get().naming != null) log("schermata del nome!")
            log("squadra ${s.latestSnapshot.get().party.map { it.name }}")
        }
        assertEquals("due Pokémon", 2, s.latestSnapshot.get().party.size)
        save("two.state")
    }

    /** Seconda tappa (da -Pgiallo.state.two=parcel.state): Pacco a Oak, ritorno al negozio aperto. */
    @Test fun costruisci2() {
        assumeTrue(System.getProperty("giallo.scenario") != null && System.getProperty("giallo.state.two") != null && System.getProperty("giallo.state.items") == null && System.getProperty("giallo.state.shop") == null)
        s = start(auto = "giallo.state.two") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        textThrough()
        println("DBGSC negozio ${pos()} porte=${s.warpsForTest()}")
        assertTrue("fuori dal negozio", warpTo(null))
        println("DBGSC città ${pos()}")
        var k = 0
        while (pos().first != 0 && k++ < 6) { edge(north = false); log("verso sud") }
        assertEquals("Biancavilla", 0, pos().first)
        assertTrue("nel laboratorio", warpTo(0x28))
        textThrough()
        println("DBGSC lab ${pos()} persone=${s.mapGridForTest()?.people}")
        // dal Professore: si parla con le persone del laboratorio, una alla volta, finché il Pacco è consegnato
        val people = s.mapGridForTest()!!.people.sortedBy { it.second }
        for (person in people) {
            if (s.latestSnapshot.get().bag.none { it.id == 70 }) break
            log("parlo con $person")
            val g = s.mapGridForTest()!!
            val p0 = g.people.minByOrNull { kotlin.math.abs(it.first - person.first) + kotlin.math.abs(it.second - person.second) } ?: continue
            val spot = g.interactionSpots(p0.first, p0.second).entries.filter { g.path(pos().second, pos().third, setOf(it.key)) != null }
                .minByOrNull { kotlin.math.abs(it.key.first - pos().second) + kotlin.math.abs(it.key.second - pos().third) } ?: continue
            goTo(spot.key.first, spot.key.second)
            s.setKeys(spot.value.key); pump(150); s.setKeys(0); pump(400)
            tapTile(10f, 20f)
            textThrough(250)
        }
        println("DBGSC dopo Oak borsa=${s.latestSnapshot.get().bag} pokedex=${s.latestSnapshot.get().pokedexOwned}")
        save("pokedex.state")
        assertTrue("fuori dal laboratorio", warpTo(null))
        k = 0
        while (pos().first != 1 && k++ < 6) { edge(north = true); println("DBGSC verso nord ${pos()}") }
        assertEquals("Smeraldopoli", 1, pos().first)
        assertTrue("nel negozio", warpTo(0x2A))
        textThrough()
        save("shop.state")
    }

    @Test fun costruisci() {
        assumeTrue(System.getProperty("giallo.scenario") != null && System.getProperty("giallo.state.two") == null && System.getProperty("giallo.state.items") == null && System.getProperty("giallo.state.shop") == null)
        s = start(auto = "giallo.state") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        fightIfNeeded()
        assertTrue("in giro nel laboratorio", waitFor(10_000) { s.latestSnapshot.get().freeRoam })
        println("DBGSC lab ${pos()} porte=${s.warpsForTest()}")
        assertTrue("fuori dal laboratorio", warpTo(null))
        println("DBGSC fuori ${pos()}")
        assertTrue("Strada 1", edge(north = true))
        println("DBGSC strada ${pos()}")
        save("route1.state")
        var k = 0
        while (pos().first == 12 && k++ < 4) edge(north = true)
        println("DBGSC città ${pos()} porte=${s.warpsForTest()}")
        assertEquals("Smeraldopoli", 1, pos().first)
        save("viridian.state")
        assertTrue("nel Pokémon Market", warpTo(0x2A))
        textThrough()
        save("parcel.state")
    }
}

/**
 * Negozio di Smeraldopoli (D-056, partita costruita dalla partita automatica): parlando col commesso compare la
 * scelta dell'app; COMPRA → POKé BALL → OK (1 pezzo): soldi −200 e Poké Ball nella borsa; VENDI → POKé BALL → OK:
 * soldi +100; INDIETRO, ESCI → di nuovo a camminare. I menu del gioco non si vedono.
 */
class AppShopTest : AppScreensHarness() {
    @Test fun compraEVendi() {
        val s = start(auto = "giallo.state.shop") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue(waitFor(10_000) { s.latestSnapshot.get().freeRoam })
        val clerk = s.mapGridForTest()!!.people.minByOrNull { it.first }!!
        val g = s.mapGridForTest()!!
        val here = s.positionForTest()
        val spot = g.interactionSpots(clerk.first, clerk.second).entries.filter { g.path(here.second, here.third, setOf(it.key)) != null }.first()
        s.walkPathForTest(spot.key.first, spot.key.second)
        waitFor(15_000) { s.positionForTest().let { it.second == spot.key.first && it.third == spot.key.second } }
        s.setKeys(spot.value.key); pump(150); s.setKeys(0); pump(400)
        tapTile(10f, 20f)
        var k = 0
        while (!s.shopMenuShown && k++ < 10) tapTile(10f, 20f)
        assertTrue("scelta del negozio", shotUntil("39-negozio") { b -> boxAt(b, 1, 33, 9, 4) && boxAt(b, 10, 33, 9, 4) && boxAt(b, 5, 38, 10, 4) })
        val money0 = s.latestSnapshot.get().money
        tapTile(4f, 35f)                                    // COMPRA
        assertTrue("lista con prezzi", shotUntil("40-negozio-compra") { b -> boxAt(b, 0, 12, 20, 26) && boxAt(b, 5, 39, 10, 4) })
        tapTile(4f, 14.5f)                                  // POKé BALL
        assertTrue("quantità", shotUntil("41-negozio-quantita") { b -> boxAt(b, 0, 30, 20, 9) && boxAt(b, 13, 39, 7, 4) })
        tapTile(16f, 41f)                                   // OK
        assertTrue("Poké Ball comprata", waitFor(40_000) { !s.itemBusy && s.latestSnapshot.get().bag.any { it.id == 0x04 } })
        assertTrue("soldi −200", waitFor(5_000) { s.latestSnapshot.get().money == money0 - 200 })
        shot("42-negozio-comprata")
        tapTile(9f, 40.5f)                                  // INDIETRO → scelta
        pump(800)
        tapTile(13f, 35f)                                   // VENDI
        assertTrue("borsa da vendere", shotUntil("43-negozio-vendi") { b -> boxAt(b, 0, 12, 20, 26) })
        val idx = s.latestSnapshot.get().bag.indexOfFirst { it.id == 0x04 }
        tapTile(4f, 14.5f + 3 * idx)
        tapTile(16f, 41f)                                   // OK
        assertTrue("Poké Ball venduta", waitFor(40_000) { !s.itemBusy && s.latestSnapshot.get().bag.none { it.id == 0x04 } })
        assertTrue("soldi +100", waitFor(5_000) { s.latestSnapshot.get().money == money0 - 100 })
        tapTile(9f, 40.5f); pump(800)                       // INDIETRO
        tapTile(9f, 40f)                                    // ESCI
        assertTrue("di nuovo in giro", waitFor(20_000) { s.latestSnapshot.get().freeRoam })
        shot("44-negozio-uscita")
    }
}


/**
 * SQUADRA con due Pokémon (D-057, partita costruita giocando): SELECT, primo, secondo → scambio nel menu del gioco;
 * poi tenere premuto e trascinare → di nuovo scambiati. I menu del gioco non si vedono.
 */
class AppPartySwapTest : AppScreensHarness() {
    @Test fun spostaSquadra() {
        val s = start(auto = "giallo.state.two") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue(waitFor(10_000) { s.latestSnapshot.get().freeRoam && s.latestSnapshot.get().party.size == 2 })
        val names0 = s.latestSnapshot.get().party.map { it.name }
        tapStart(); pump(800)
        tapTile(4f, 30f)                                  // POKéMON
        assertTrue("squadra", shotUntil("45-squadra-due") { b -> boxAt(b, 10, 39, 10, 4) })
        tapTile(15f, 41f)                                 // SELECT
        tapTile(4f, 9.5f)                                 // primo
        tapTile(4f, 14.5f)                                // secondo
        assertTrue("scambiati", waitFor(40_000) { !s.itemBusy && s.latestSnapshot.get().party.map { it.name } == names0.reversed() })
        shot("46-squadra-scambiata")
        // tenere premuto sul primo e trascinare sul secondo
        val tp = 8 * unit
        val t = SystemClock.uptimeMillis()
        root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 4 * tp, 9.5f * tp, 0))
        pump(900)
        for (k in 1..10) { root.dispatchTouchEvent(MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, 4 * tp, (9.5f + 0.5f * k) * tp, 0)); pump(40) }
        root.dispatchTouchEvent(MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 4 * tp, 14.5f * tp, 0))
        assertTrue("di nuovo come prima", waitFor(40_000) { !s.itemBusy && s.latestSnapshot.get().party.map { it.name } == names0 })
        shot("47-squadra-trascinata")
    }
}

/** Cambio Pokémon in lotta (D-050) con due Pokémon: SQUADRA → secondo → in campo il secondo. */
class AppBattleSwitchTest : AppScreensHarness() {
    @Test fun cambioInLotta() {
        val s = start(auto = "giallo.state.two") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue(waitFor(10_000) { s.latestSnapshot.get().freeRoam && s.latestSnapshot.get().party.size == 2 })
        val rnd = java.util.Random(5)
        var walks = 0
        while (!s.latestSnapshot.get().inBattle && walks++ < 30) {
            val g = s.mapGridForTest() ?: break
            val p = s.positionForTest()
            val free = (0 until g.width).flatMap { x -> (6 until g.height - 4).map { y -> x to y } }.filter { g.isFree(it.first, it.second) && g.path(p.second, p.third, setOf(it)) != null }
            val t = free[rnd.nextInt(free.size)]
            s.walkPathForTest(t.first, t.second)
            waitFor(30_000) { s.latestSnapshot.get().inBattle || s.positionForTest().let { it.second == t.first && it.third == t.second } }
        }
        assertTrue("lotta", waitFor(10_000) { s.latestSnapshot.get().inBattle })
        var k = 0
        while (!(s.battleMenuShown && !s.commandBusy) && k++ < 30) tapTile(10f, 2f)
        val first = s.latestSnapshot.get().battleMon!!.species
        val second = s.latestSnapshot.get().party.first { it.species != first }
        tapTile(3f, it.giallotouch.bridge.BattleLayout.ACTIONS_TOP_ROW + 2.5f)          // SQUADRA
        assertTrue("lista squadra", shotUntil("48-lotta-squadra-due") { b -> boxAt(b, 0, 23, 20, 16) })
        val idx = s.latestSnapshot.get().party.indexOf(second)
        tapTile(6f, 25.5f + 4 * idx)
        assertTrue("in campo il secondo", waitFor(40_000) { s.latestSnapshot.get().battleMon?.species == second.species })
        shot("49-lotta-cambio")
        if (System.getProperty("giallo.ko") == null) return
        // KO: solo COLPO-CODA (seconda mossa, nessun danno) finché il secondo va KO; poi la lista dell'app chiede chi
        val rows0 = it.giallotouch.bridge.BattleLayout.MOVES_TOP_ROW
        var turns = 0
        while (!s.battlePartyAsked && s.latestSnapshot.get().inBattle && turns++ < 200) {
            when {
                s.battleMenuShown && !s.commandBusy -> tapTile(4f, rows0 + 4.5f)
                s.yesNo != null || s.latestSnapshot.get().menu.kind == it.giallotouch.bridge.MenuKind.YES_NO -> { pump(600); shot("50a-lotta-si-no"); s.command(it.giallotouch.bridge.GameCommand.Answer(true)); pump(1500) }
                else -> tapTile(10f, 2f)
            }
        }
        assertTrue("il gioco chiede un Pokémon", waitFor(10_000) { s.battlePartyAsked })
        assertTrue("lista dell'app per il KO", shotUntil("50-lotta-ko") { b -> boxAt(b, 0, 23, 20, 16) })
        val alive = s.latestSnapshot.get().party.indexOfFirst { it.hp > 0 }
        tapTile(6f, 25.5f + 4 * alive)
        assertTrue("in campo il Pokémon scelto", waitFor(40_000) { !s.battlePartyAsked && s.latestSnapshot.get().battleMon?.species == s.latestSnapshot.get().party[alive].species })
        shot("51-lotta-dopo-ko")
    }
}

/**
 * BOX POKéMON del Centro (D-058) sul gioco vero, con lo stato del Centro di Smeraldopoli (-Pgiallo.state.shop=STATO,
 * squadra PIKACHU + RATTATA, box vuoto): deposita RATTATA e lo ritira dall'app.
 */
class AppBoxTest : AppScreensHarness() {
    @Test fun boxPokemon() {
        val s = start(auto = "giallo.state.shop") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue(waitFor(10_000) { s.latestSnapshot.get().freeRoam })
        assumeTrue("stato del Centro Pokémon", s.positionForTest().first == 41 && s.latestSnapshot.get().party.size == 2)
        s.walkPathForTest(13, 4)
        assertTrue(waitFor(15_000) { s.positionForTest().let { it.second == 13 && it.third == 4 } })
        s.setKeys(NativeCore.KEY_UP); pump(150); s.setKeys(0); pump(400)
        fun cursor() = s.screenForTest().second.any { (it.toInt() and 0xFF) == 0xED }
        tapTile(10f, 20f)
        var k = 0
        while (!cursor() && k++ < 10) tapTile(10f, 20f)                  // "accende il PC" → PC DI ???
        assertTrue("scelta del PC dell'app", shotUntil("37-pc-centro-scelta") { b -> boxAt(b, 1, 24, 18, 4) && boxAt(b, 1, 36, 18, 4) })
        assertEquals(4, s.pcChoice.size)
        tapTile(9f, 26f)                                                 // PC DI ??? (il box)
        assertTrue("BOX POKéMON dell'app", shotUntil("38-box") { b -> boxAt(b, 0, 2, 20, 5) && boxAt(b, 0, 7, 20, 31) && boxAt(b, 0, 39, 11, 4) && boxAt(b, 11, 39, 9, 4) })
        assertEquals(0, s.boxMons.size)
        tapTile(15f, 41f); pump(500)                                     // AZIONI
        assertTrue("riquadro AZIONI", shotUntil("38b-box-azioni") { b -> boxAt(b, 7, 27, 13, 12) })
        tapTile(13f, 35f)                                                // DEPOSITA → squadra
        assertTrue("squadra da depositare", shotUntil("39-box-deposita") { b -> !boxAt(b, 15, 39, 5, 4) && !boxAt(b, 7, 27, 13, 12) })
        tapTile(6f, 12.5f)                                               // RATTATA (seconda riga)
        assertTrue("RATTATA nel box", waitFor(40_000) { !s.itemBusy && s.latestSnapshot.get().party.size == 1 && s.boxMons.size == 1 })
        assertEquals("RATTATA", s.boxMons[0].name)
        pump(600); shot("40-box-dopo-deposito")
        tapTile(9f, 40.5f)                                               // INDIETRO → lista del box
        pump(800)
        tapTile(6f, 9.5f)                                                // RATTATA: RITIRA
        assertTrue("RATTATA di nuovo in squadra", waitFor(40_000) { !s.itemBusy && s.latestSnapshot.get().party.size == 2 && s.boxMons.isEmpty() })
        pump(600); shot("41-box-dopo-ritiro")
        // scorrere a lato: BOX 2, poi di nuovo BOX 1 (il gioco salva la partita a ogni cambio)
        swipe(16f, 20f, 3f, 20f)
        assertTrue("BOX 2", waitFor(60_000) { !s.itemBusy && s.boxNumber == 2 })
        pump(600); shot("41b-box-2")
        swipe(3f, 20f, 16f, 20f)
        assertTrue("BOX 1", waitFor(60_000) { !s.itemBusy && s.boxNumber == 1 })
        // LIBERA: deposita RATTATA, poi tenere premuto → Liberare RATTATA? → SÌ
        tapTile(15f, 41f); pump(500); tapTile(13f, 35f); pump(500); tapTile(6f, 12.5f)
        assertTrue("RATTATA nel box", waitFor(40_000) { !s.itemBusy && s.boxMons.size == 1 })
        tapTile(9f, 40.5f); pump(800)
        longPressTile(6f, 9.5f)
        assertTrue("domanda", shotUntil("41c-box-libera") { b -> boxAt(b, 0, 27, 20, 9) && boxAt(b, 1, 38, 8, 5) && boxAt(b, 11, 38, 8, 5) })
        tapTile(4f, 40f)                                                 // SÌ
        assertTrue("RATTATA liberato", waitFor(40_000) { !s.itemBusy && s.boxMons.isEmpty() && s.latestSnapshot.get().party.size == 1 })
        pump(600); shot("41d-box-dopo-libera")
        tapTile(4f, 40.5f)                                               // INDIETRO → si spegne il PC
        assertTrue("di nuovo in giro", waitFor(30_000) { s.latestSnapshot.get().freeRoam && s.pcChoice.isEmpty() && !s.boxMenuShown })
        pump(1500); shot("42-box-chiuso")
    }
}

/**
 * Niente lampeggi (D-061) sul gioco vero (-Pgiallo.state.two=two.state, Strada 1): lotta con un selvatico e dialoghi con
 * cartelli e persone; nessuna disposizione (lotta divisa, riquadri di lotta, dialogo in alto, SÌ/NO…) cambia per meno di
 * 9 fotogrammi e nessun dialogo resta in basso.
 */
class AppSenzaLampeggiTest : AppScreensHarness() {
    @Test fun lampeggi() {
        val s = start(auto = "giallo.state.two") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        waitFor(10_000) { s.latestSnapshot.get().freeRoam }
        val names = listOf("lottaDivisa", "dialogoSu", "menuLotta", "menuGiocoLotta", "siNo", "vistaEstesa", "inLotta", "libero", "squadraChiesta", "dialogoInBasso")
        val rec = java.util.Collections.synchronizedList(ArrayList<BooleanArray>())
        val old = s.onFrame
        s.onFrame = {
            old?.invoke()
            val t = s.frameTilesForTest()
            // riquadro nelle caselle e anche nell'immagine (a fine lotta il gioco è tutto bianco con le caselle ancora lì)
            val raw = s.latestFrame.get()
            // e il bordo del riquadro già disegnato nell'immagine (righe 96-103, come D-086)
            val drawn = (96 until 104).any { y -> (8 until 152).count { x -> val c = raw[y * 160 + x]; ((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255) < 200 } >= 120 }
            val boxBottom = (t[12 * 20].toInt() and 0xFF) == 0x79 && (t[17 * 20 + 19].toInt() and 0xFF) == 0x7E && raw.any { it != raw[0] } && drawn
            rec.add(booleanArrayOf(s.battleSplit, s.dialogStrip != null, s.battleMenuShown, s.battleGameMenu, s.yesNo != null,
                s.extendedActive, s.latestSnapshot.get().inBattle, s.latestSnapshot.get().freeRoam, s.battlePartyAsked,
                boxBottom && !s.latestSnapshot.get().inBattle && s.dialogStrip == null))
        }
        val rnd = java.util.Random(7)
        var walks = 0
        while (!s.latestSnapshot.get().inBattle && walks++ < 40) {
            val g = s.mapGridForTest() ?: break
            val free = (0 until g.width).flatMap { x -> (6 until g.height - 4).map { y -> x to y } }.filter { g.isFree(it.first, it.second) }
            val t = free[rnd.nextInt(free.size)]
            s.walkPathForTest(t.first, t.second)
            waitFor(20_000) { s.latestSnapshot.get().inBattle || s.positionForTest().let { it.second == t.first && it.third == t.second } }
        }
        var g2 = 0
        while (s.latestSnapshot.get().inBattle && g2++ < 300) {
            if (s.battleMenuShown && !s.commandBusy) tapTile(4f, it.giallotouch.bridge.BattleLayout.MOVES_TOP_ROW + 2.5f)
            else if (s.yesNo != null) tapTile(14f, 39f) else tapTile(10f, 2f)
            pump(300)
        }
        var k = 0
        while (!s.latestSnapshot.get().freeRoam && k++ < 40) { tapTile(10f, 2f); pump(300) }
        pump(2000)
        // dialoghi: cartelli e persone raggiungibili
        val g = s.mapGridForTest()!!
        for (target in (g.signs + g.people).take(3)) {
            val here = s.positionForTest()
            val spot = g.interactionSpots(target.first, target.second).entries.firstOrNull { g.path(here.second, here.third, setOf(it.key)) != null } ?: continue
            s.walkPathForTest(spot.key.first, spot.key.second)
            waitFor(30_000) { s.positionForTest().let { it.second == spot.key.first && it.third == spot.key.second } || s.latestSnapshot.get().inBattle }
            if (s.latestSnapshot.get().inBattle) break
            s.setKeys(spot.value.key); pump(150); s.setKeys(0); pump(300)
            tapTile(10f, 20f); pump(400)
            k = 0
            while (!s.latestSnapshot.get().freeRoam && k++ < 30) { if (s.yesNo != null) tapTile(14f, 39f) else tapTile(10f, 20f); pump(350) }
            println("DBGBL dialogo con $target finito")
        }
        s.onFrame = old
        val frames = ArrayList(rec)
        var blips = 0
        println("DBGBL fotogrammi ${frames.size} in lotta ${frames.count { it[6] }} con dialogo in alto ${frames.count { it[1] }} dialogo rimasto in basso ${frames.count { it[9] }}")
        for (f in names.indices) {
            var i = 1
            while (i < frames.size) {
                if (frames[i][f] != frames[i - 1][f]) {
                    var j = i
                    while (j < frames.size && frames[j][f] == frames[i][f]) j++
                    if (j < frames.size && j - i <= 8 && f != 5) blips++
                    if (j < frames.size && j - i <= 8) println("DBGBL ${names[f]}=${frames[i][f]} per ${j - i} fotogrammi al $i  stato: " + names.indices.joinToString(" ") { n -> if (frames[i][n]) names[n] else "" })
                    i = j
                } else i++
            }
        }
        assertTrue("una lotta", frames.count { it[6] } > 0)
        assertEquals("lampeggi", 0, blips)
        assertEquals("dialoghi rimasti in basso", 0, frames.count { it[9] })
    }
}

/** Cura al Centro Pokémon (D-060) sul gioco vero (-Pgiallo.state.shop=center.state): CURA / ESCI come SÌ/NO dell'app. */
class AppCuraTest : AppScreensHarness() {
    @Test fun cura() {
        val s = start(auto = "giallo.state.shop") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        waitFor(10_000) { s.latestSnapshot.get().freeRoam }
        assumeTrue("stato del Centro Pokémon", s.positionForTest().first == 41)
        s.walkPathForTest(3, 3)
        assertTrue(waitFor(20_000) { s.positionForTest().let { it.second == 3 && it.third == 3 } })
        s.setKeys(NativeCore.KEY_UP); pump(150); s.setKeys(0); pump(400)
        tapTile(10f, 20f)
        var k = 0
        while (s.yesNo == null && k++ < 20) { tapTile(10f, 20f); pump(300) }
        assertTrue("CURA / ESCI dell'app", shotUntil("43-cura") { b -> boxAt(b, 2, 37, 8, 5) && boxAt(b, 12, 37, 8, 5) })
        tapTile(5f, 39f)                                                 // CURA
        k = 0
        while (!s.latestSnapshot.get().freeRoam && k++ < 30) { tapTile(10f, 20f); pump(400) }
        assertTrue("di nuovo in giro", s.latestSnapshot.get().freeRoam)
        shot("44-dopo-cura")
    }
}

/** Segnala (D-064): tre dita sullo schermo → file zip con foto, stato della partita e note (nella cache dell'app). */
class AppSegnalaTest : AppScreensHarness() {
    @Test fun segnala() {
        val s = start(auto = "giallo.state.bag") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue(waitFor(10_000) { s.latestSnapshot.get().freeRoam })
        val dir = java.io.File(root.context.cacheDir, "segnalazioni")
        dir.deleteRecursively()
        val tp = 8 * unit; val t = SystemClock.uptimeMillis()
        val ids = intArrayOf(0, 1, 2)
        fun props(n: Int) = Array(n) { MotionEvent.PointerProperties().apply { id = ids[it]; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        fun coords(n: Int) = Array(n) { MotionEvent.PointerCoords().apply { x = (4f + 5 * it) * tp; y = 20 * tp; pressure = 1f; size = 1f } }
        root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 1, props(1), coords(1), 0, 0, 1f, 1f, 0, 0, 0, 0)); pump(30)
        root.dispatchTouchEvent(MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, props(2), coords(2), 0, 0, 1f, 1f, 0, 0, 0, 0)); pump(30)
        root.dispatchTouchEvent(MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 3, props(3), coords(3), 0, 0, 1f, 1f, 0, 0, 0, 0)); pump(30)
        root.dispatchTouchEvent(MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 3, props(3), coords(3), 0, 0, 1f, 1f, 0, 0, 0, 0)); pump(300)
        assertTrue("file della segnalazione", waitFor(10_000) { dir.listFiles()?.isNotEmpty() == true })
        val zip = java.util.zip.ZipFile(dir.listFiles()!!.first())
        val names = zip.entries().toList().map { it.name }
        println("DBGSG $names " + String(zip.getInputStream(zip.getEntry("info.txt")).readBytes()))
        assertTrue(names.containsAll(listOf("partita.state", "info.txt")))
        assertTrue(zip.getEntry("partita.state").size > 10_000)
    }
}

/** Temporaneo: camminata con fotogrammi fitti per cercare glitch grafici (-Pgiallo.state.shop=STATO). */
class ZzWalkCapture : AppScreensHarness() {
    @Test fun cammina() {
        val s = start(auto = "giallo.state.shop") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        waitFor(10_000) { s.latestSnapshot.get().freeRoam }
        var n = 0
        val keys = listOf(NativeCore.KEY_LEFT to 1500L, NativeCore.KEY_UP to 1500L, NativeCore.KEY_RIGHT to 2500L, NativeCore.KEY_DOWN to 1500L, NativeCore.KEY_LEFT to 1200L)
        for ((k, ms) in keys) {
            s.setKeys(k)
            var t = 0L
            while (t < ms) { shot("wk-%03d".format(n++)); pump(40); t += 60 }
            s.setKeys(0)
            if (!s.latestSnapshot.get().freeRoam) { var j = 0; while (!s.latestSnapshot.get().freeRoam && j++ < 20) { tapTile(10f, 2f) } }
        }
        println("DBGWK fine ${s.positionForTest()}")
    }
}

/**
 * Temporaneo: lampeggi visibili, fotogramma per fotogramma. Il gioco avanza di un fotogramma alla volta e ogni volta si
 * fotografa lo schermo intero (gioco + riquadri dell'app); un'immagine che compare per 1–2 fotogrammi e poi sparisce
 * (prima e dopo uguali) è un lampeggio. -Pgiallo.state.two=two.state
 */
class ZzGlitchTest : AppScreensHarness() {
    private lateinit var s: GameSession
    private val window = ArrayDeque<IntArray>()
    private val bitmaps = ArrayDeque<Bitmap>()
    private var n = 0
    private var blips = 0
    private var tag = ""
    private fun grab(): Pair<IntArray, Bitmap> {
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val w = 160; val h = (root.height / unit).toInt()
        val px = IntArray(w * h) { i -> bmp.getPixel(((i % w) * unit + unit / 2).toInt().coerceAtMost(root.width - 1), ((i / w) * unit + unit / 2).toInt().coerceAtMost(root.height - 1)) }
        return px to bmp
    }
    /** Tag per cui salvare un foglio con i primi fotogrammi (uno ogni [sheetStep]). */
    private val sheetTags = HashSet<String>()
    private var capture = true
    private val sheetStep = 2
    private fun frames(count: Int, label: String) {
        tag = label
        val sheet = if (label in sheetTags) ArrayList<Bitmap>() else null
        repeat(count) { idx ->
            if (sheet != null && idx % sheetStep == 0 && sheet.size < 24) {
                shadowOf(Looper.getMainLooper()).idle()
                val b = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888); root.draw(Canvas(b))
                sheet.add(Bitmap.createScaledBitmap(b, b.width / 4, b.height / 4, false))
            }
            s.stepPermits!!.release()
            s.stepDone!!.tryAcquire(3, java.util.concurrent.TimeUnit.SECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            if (!capture) return@repeat
            val (px, bmp) = grab()
            window.addLast(px); bitmaps.addLast(bmp); n++
            if (window.size > 4) { window.removeFirst(); bitmaps.removeFirst() }
            if (window.size >= 3) {
                val a = window[window.size - 3]; val b = window[window.size - 2]; val c = window[window.size - 1]
                var cnt = 0
                for (i in a.indices) if (b[i] != a[i] && b[i] != c[i] && a[i] == c[i]) cnt++
                var cnt2 = 0
                if (window.size == 4) { val z = window[0]
                    for (i in z.indices) if (a[i] != z[i] && b[i] != z[i] && c[i] == z[i] && a[i] == b[i]) cnt2++ }
                if ((cnt > 30 || cnt2 > 30) && tag != "cammina") {
                    blips++
                    println("DBGGL lampeggio al fotogramma ${n - 2} ($tag): ${cnt} pixel per 1 fotogramma, ${cnt2} per 2")
                    if (blips <= 25) {
                        val list = bitmaps.toList()
                        val out = Bitmap.createBitmap(list[0].width * list.size / 2, list[0].height / 2, Bitmap.Config.ARGB_8888)
                        val cv = Canvas(out)
                        list.forEachIndexed { k, bm -> cv.drawBitmap(Bitmap.createScaledBitmap(bm, bm.width / 2, bm.height / 2, false), (k * bm.width / 2).toFloat(), 0f, null) }
                        FileOutputStream(File(shots, "gl-%03d-%s.png".format(blips, tag))).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
            }
        }
        if (sheet != null && sheet.isNotEmpty()) {
            val cols = 12; val w = sheet[0].width; val h = sheet[0].height
            val out = Bitmap.createBitmap(w * cols, h * ((sheet.size + cols - 1) / cols), Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            sheet.forEachIndexed { k, b -> cv.drawBitmap(b, ((k % cols) * w).toFloat(), ((k / cols) * h).toFloat(), null) }
            FileOutputStream(File(shots, "fs-$label.png")).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    /**
     * Analisi dell'inizio fotogramma per fotogramma (richiesta del committente): dall'accensione al menu iniziale.
     * Salva in analisi/ un'immagine per ogni fotogramma diverso dal precedente (320 di larghezza) e indice.txt con
     * "fotogramma file lampeggio fase"; il visore HTML si costruisce poi fuori dal repository.
     */
    @Test fun analisi() = registra("analisi") { step, fase ->
        step(60)
        fase("introduzione")
        var k = 0
        while (!s.titleShown && k++ < 120) step(30)
        fase("titolo")
        step(240)
        s.setKeys(NativeCore.KEY_START); step(6); s.setKeys(0)
        fase("menu")
        k = 0
        while (s.mainMenuItems == 0 && k++ < 20) { step(20); if (s.mainMenuItems == 0 && s.titleShown) { s.setKeys(NativeCore.KEY_START); step(6); s.setKeys(0) } }
        step(90)
    }

    /**
     * Analisi di NUOVO GIOCO fotogramma per fotogramma: dal menu iniziale (introduzione saltata con START) al
     * discorso del Professore, ai nomi e alla cameretta, finché si cammina.
     */
    @Test fun analisiNuovoGioco() = registra("analisi2") { step, fase ->
        var k = 0
        // il video iniziale si guarda tutto (richiesta del committente): START solo sul titolo
        while (!s.titleShown && k++ < 400) step(30)
        fase("titolo"); step(120); k = 0
        while (s.mainMenuItems == 0 && k++ < 200) { s.setKeys(NativeCore.KEY_START); step(4); s.setKeys(0); step(26) }
        fase("menu")
        step(40)
        val frameTop = (root.height - 144 * unit) / 2 / (8 * unit)
        tapTile(4f, frameTop + it.giallotouch.bridge.StartMenuLayout.row(it.giallotouch.bridge.StartMenuLayout.entries(s.mainMenuItems).size - 1) + 0.5f)
        fase("professore")
        step(120)
        var named = 0
        for (i in 0 until 260) {
            if (s.inGame) break
            if (s.nameList != null) { fase("nomi"); tapTile(4f, 17.5f); named++; step(90) }
            else if (s.yesNo != null) { tapTile(14f, 39f); step(60) }
            else { fase(if (named >= 2) "partenza" else "professore"); s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(41) }
        }
        fase("cameretta")
        step(120)
        s.stepPermits = null; pump(300)
        s.saveStateForTest(File(shots, "cameretta.state"))
    }

    /**
     * Analisi dalla cameretta (-Pgiallo.state.cameretta=STATO, salvato da analisiNuovoGioco): scale, casa, Biancavilla,
     * il Professore che ferma il giocatore nell'erba, Pikachu, il laboratorio, finché si cammina nel laboratorio.
     */
    @Test fun analisiCameretta() = registra("analisi3", auto = "giallo.state.cameretta") { step, fase ->
        fun pos() = s.positionForTest()
        fun walkTo(x: Int, y: Int, max: Int = 900) {
            s.walkPathForTest(x, y)
            var k = 0
            val m = pos().first
            while (k++ < max && pos().let { it.first == m && (it.second != x || it.third != y) }) step(2)
        }
        fun textUntilFree(max: Int) {
            var k = 0
            while (k++ < max) {
                step(20)
                if (s.latestSnapshot.get().freeRoam) { step(30); if (s.latestSnapshot.get().freeRoam) return }
                if (s.yesNo != null) { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0) }
                else { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0) }
            }
        }
        fase("cameretta"); step(60)
        val stairs = s.warpsForTest().firstOrNull()
        if (stairs != null) {
            val m = pos().first
            walkTo(stairs.second, stairs.first)
            var k = 0
            while (pos().first == m && k++ < 300) step(2)
        }
        fase("casa"); step(90)
        textUntilFree(10)
        val door = s.warpsForTest().maxByOrNull { it.first }
        if (door != null) {
            val m = pos().first
            walkTo(door.second, door.first)
            var k = 0
            while (pos().first == m && k++ < 100) { s.setKeys(NativeCore.KEY_DOWN); step(4); s.setKeys(0); step(8) }
        }
        fase("biancavilla"); step(90)
        // verso l'erba a nord: il Professore ferma il giocatore
        val g = s.mapGridForTest()
        val top = g?.let { gg -> (0 until gg.width).map { it to 1 }.filter { gg.isFree(it.first, it.second) && gg.path(pos().second, pos().third, setOf(it)) != null }.minByOrNull { kotlin.math.abs(it.first - pos().second) } }
        if (top != null) walkTo(top.first, top.second)
        var k = 0
        while (s.latestSnapshot.get().freeRoam && k++ < 40) { s.setKeys(NativeCore.KEY_UP); step(8); s.setKeys(0); step(8) }
        fase("professore")
        textUntilFree(300)
        fase("laboratorio"); step(120)
        s.stepPermits = null; pump(300)
        s.saveStateForTest(File(shots, "laboratorio.state"))
    }

    /**
     * Analisi della lotta col rivale (-Pgiallo.state.laboratorio=STATO, salvato da analisiCameretta): verso l'uscita del
     * laboratorio il rivale sfida il giocatore; lotta con la prima mossa, testi col tasto A, fino a camminare di nuovo.
     */
    @Test fun analisiRivale() = registra("analisi4", auto = "giallo.state.laboratorio") { step, fase ->
        fun pos() = s.positionForTest()
        fase("laboratorio"); step(30)
        // testi in sospeso, poi verso l'uscita (in basso)
        var k = 0
        while (!s.latestSnapshot.get().freeRoam && k++ < 60) { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(26) }
        // Si parla con ogni persona del laboratorio, una alla volta, e dopo ognuna si prova a uscire: la lotta col
        // rivale parte quando la scena del laboratorio è completa.
        fun talkThrough() {
            var t = 0
            while (!s.latestSnapshot.get().freeRoam && !s.latestSnapshot.get().inBattle && t++ < 120) {
                if (s.yesNo != null) { s.command(it.giallotouch.bridge.GameCommand.Answer(false)); step(30) }
                else { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(26) }
            }
        }
        fun tryExit() {
            val ex = s.warpsForTest().maxByOrNull { it.first } ?: return
            s.walkPathForTest(ex.second, ex.first - 1)
            var t = 0
            while (t++ < 150 && !s.latestSnapshot.get().inBattle) {
                step(4)
                if (!s.latestSnapshot.get().freeRoam) { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0) }
                if (pos().first != 40 && pos().first != s.positionForTest().first) break
            }
        }
        val g0 = s.mapGridForTest()
        for (person in g0?.people.orEmpty().sortedBy { it.second }) {
            if (s.latestSnapshot.get().inBattle) break
            val g = s.mapGridForTest() ?: break
            val spot = g.interactionSpots(person.first, person.second).entries.firstOrNull { g.path(pos().second, pos().third, setOf(it.key)) != null } ?: continue
            s.walkPathForTest(spot.key.first, spot.key.second)
            k = 0
            while (k++ < 300 && pos().let { it.second != spot.key.first || it.third != spot.key.second }) { step(2); if (s.latestSnapshot.get().inBattle) break }
            s.setKeys(spot.value.key); step(6); s.setKeys(0); step(6)
            s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(30)
            talkThrough()
            tryExit()
        }
        val exit = s.warpsForTest().maxByOrNull { it.first }
        k = 0
        while (!s.latestSnapshot.get().inBattle && k++ < 400) {
            step(4)
            if (!s.latestSnapshot.get().freeRoam && k % 6 == 0) { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0) }
            if (s.latestSnapshot.get().freeRoam && k % 50 == 0 && exit != null) s.walkPathForTest(exit.second, exit.first - 1)
        }
        fase("lotta")
        k = 0
        while (s.latestSnapshot.get().inBattle && k++ < 600) {
            if (s.battleMenuShown && !s.commandBusy) { s.command(it.giallotouch.bridge.GameCommand.Fight(0)); step(30) }
            else { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(16) }
        }
        fase("dopo")
        k = 0
        while (!s.latestSnapshot.get().freeRoam && k++ < 80) { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(26) }
        step(120)
    }

    /** Analisi della Strada 1 verso Smeraldopoli (-Pgiallo.state.strada=STATO sulla Strada 1): cammino, lotte, confine. */
    @Test fun analisiStrada1() = registra("analisi5", auto = "giallo.state.strada") { step, fase ->
        fun pos() = s.positionForTest()
        fase("strada"); step(30)
        var k = 0
        while (pos().first != 1 && k++ < 30) {
            val g = s.mapGridForTest() ?: break
            val top = (0 until g.width).map { it to 0 }.filter { g.isFree(it.first, it.second) && g.path(pos().second, pos().third, setOf(it)) != null }
                .minByOrNull { kotlin.math.abs(it.first - pos().second) }
            val target = top ?: (0 until g.width).flatMap { x -> (0 until g.height).map { x to it } }
                .filter { g.isFree(it.first, it.second) && g.path(pos().second, pos().third, setOf(it)) != null }.minByOrNull { it.second } ?: break
            s.walkPathForTest(target.first, target.second)
            var t = 0
            while (t++ < 400 && pos().let { it.first == 12 && (it.second != target.first || it.third != target.second) }) {
                step(4)
                if (s.latestSnapshot.get().inBattle) {
                    fase("lotta")
                    var b = 0
                    while ((s.latestSnapshot.get().inBattle || !s.latestSnapshot.get().freeRoam) && b++ < 300) {
                        if (s.battleMenuShown && !s.commandBusy) { s.command(it.giallotouch.bridge.GameCommand.Fight(0)); step(30) }
                        else { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(16) }
                    }
                    fase("strada"); s.walkPathForTest(target.first, target.second)
                } else if (!s.latestSnapshot.get().freeRoam && t % 8 == 0) { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0) }
            }
            if (pos().first == 12 && pos().third == 0) { s.setKeys(NativeCore.KEY_UP); step(16); s.setKeys(0); step(30) }
        }
        fase("smeraldopoli"); step(180)
    }

    /** Analisi dei dialoghi sulla Strada 1 (-Pgiallo.state.two=two.state): cartelli e persone come AppSenzaLampeggiTest. */
    @Test fun analisiDialoghi() = registra("analisi7", auto = "giallo.state.two") { step0, fase ->
        fun pos() = s.positionForTest()
        var n = 0
        val step: (Int) -> Unit = { c -> repeat(c) { step0(1); n++
            val t = s.frameTilesForTest()
            val boxBottom = (t[12 * 20].toInt() and 0xFF) == 0x79 && (t[17 * 20 + 19].toInt() and 0xFF) == 0x7E
            val raw = s.latestFrame.get()
            if (boxBottom && raw.any { it != raw[0] } && !s.latestSnapshot.get().inBattle && s.dialogStrip == null) println("DBGDL in basso al passo $n libero=${s.latestSnapshot.get().freeRoam}")
        } }
        fun lotta() {
            fase("lotta"); var b = 0
            while ((s.latestSnapshot.get().inBattle || !s.latestSnapshot.get().freeRoam) && b++ < 300) {
                if (s.battleMenuShown && !s.commandBusy) { s.command(it.giallotouch.bridge.GameCommand.Fight(0)); step(30) }
                else { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(16) }
            }
            fase("strada"); step(30)
        }
        fase("strada"); step(30)
        if (s.latestSnapshot.get().inBattle) lotta()
        val g = s.mapGridForTest()!!
        for (target in (g.signs + g.people).take(3)) for (tent in 0 until 3) {
            val here = pos()
            val spot = g.interactionSpots(target.first, target.second).entries.firstOrNull { g.path(here.second, here.third, setOf(it.key)) != null } ?: continue
            s.walkPathForTest(spot.key.first, spot.key.second)
            var k = 0
            while (k++ < 600 && pos().let { it.second != spot.key.first || it.third != spot.key.second } && !s.latestSnapshot.get().inBattle) step(2)
            if (s.latestSnapshot.get().inBattle) { lotta(); continue }
            if (pos().let { it.second != spot.key.first || it.third != spot.key.second }) continue
            fase("dialogo$target")
            s.setKeys(spot.value.key); step(9); s.setKeys(0); step(18)
            s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(24)
            k = 0
            while (!s.latestSnapshot.get().freeRoam && k++ < 30) {
                if (s.yesNo != null) s.command(it.giallotouch.bridge.GameCommand.Answer(false)) else { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0) }
                step(20)
            }
            fase("strada"); step(40)
            break
        }
    }

    /**
     * Analisi di Smeraldopoli (-Pgiallo.state.citta=STATO a Smeraldopoli, prima del Pacco): cammino fino al Market,
     * ingresso, il commesso consegna il PACCO DI OAK, uscita.
     */
    @Test fun analisiSmeraldopoli() = registra("analisi8", auto = "giallo.state.citta") { step, fase ->
        fun pos() = s.positionForTest()
        fase("citta"); step(30)
        println("DBGSM posizione ${pos()} porte ${s.warpsForTest()}")
        val door = s.warpsForTest().firstOrNull { it.first == 19 && it.second == 29 } ?: s.warpsForTest().first()
        s.walkPathForTest(door.second, door.first + 1)
        var k = 0
        val m0 = pos().first
        while (k++ < 600 && pos().let { it.first == m0 && (it.second != door.second || it.third != door.first + 1) }) step(2)
        s.setKeys(NativeCore.KEY_UP); step(16); s.setKeys(0)
        k = 0
        while (k++ < 300 && pos().first == m0) step(2)
        fase("market"); step(60)
        k = 0
        var calm = 0
        while (k++ < 200 && calm < 6) {
            step(20)
            if (s.yesNo != null) { s.command(it.giallotouch.bridge.GameCommand.Answer(true)); calm = 0 }
            else if (s.dialogStrip != null || !s.latestSnapshot.get().freeRoam) { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); calm = 0 }
            else calm++
        }
        fase("pacco"); step(60)
        println("DBGSM borsa ${s.latestSnapshot.get().bag}")
        System.getProperty("giallo.scenario")?.let { s.saveStateForTest(File(it, "pacco.state")) }
        // uscita dal Market: verso la porta (la più in basso) e poi giù
        fun esci() {
            val m = pos().first
            val d = s.warpsForTest().maxByOrNull { it.first } ?: return
            s.walkPathForTest(d.second, d.first)
            var j = 0
            while (j++ < 400 && pos().first == m) { step(4); if (j % 20 == 0) { s.setKeys(NativeCore.KEY_DOWN); step(8); s.setKeys(0) } }
        }
        fase("uscita"); esci(); step(60)
        fun lotta() {
            fase("lotta"); var b = 0
            while ((s.latestSnapshot.get().inBattle || !s.latestSnapshot.get().freeRoam) && b++ < 300) {
                if (s.battleMenuShown && !s.commandBusy) { s.command(it.giallotouch.bridge.GameCommand.Fight(0)); step(30) }
                else { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(16) }
            }
        }
        // verso sud fino a Biancavilla (mappa 0)
        fase("strada1"); k = 0
        while (pos().first != 0 && k++ < 60) {
            val g = s.mapGridForTest() ?: break
            val bottom = (0 until g.width).map { it to g.height - 1 }.filter { g.isFree(it.first, it.second) && g.path(pos().second, pos().third, setOf(it)) != null }
                .minByOrNull { kotlin.math.abs(it.first - pos().second) }
            val target = bottom ?: (0 until g.width).flatMap { x -> (0 until g.height).map { x to it } }
                .filter { g.isFree(it.first, it.second) && g.path(pos().second, pos().third, setOf(it)) != null }.maxByOrNull { it.second } ?: break
            val m = pos().first
            s.walkPathForTest(target.first, target.second)
            var t = 0
            while (t++ < 400 && pos().first == m && (pos().second != target.first || pos().third != target.second)) {
                step(4)
                if (s.latestSnapshot.get().inBattle) { lotta(); fase("strada1"); s.walkPathForTest(target.first, target.second) }
                else if (!s.latestSnapshot.get().freeRoam && t % 8 == 0) { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0) }
            }
            if (pos().first == m && pos().third == target.second) { s.setKeys(NativeCore.KEY_DOWN); step(16); s.setKeys(0); step(30) }
        }
        fase("biancavilla"); step(60)
        System.getProperty("giallo.scenario")?.let { s.saveStateForTest(File(it, "biancavilla-pacco.state")) }
        // laboratorio: la porta più in basso di Biancavilla
        val lab = s.warpsForTest().maxByOrNull { it.first }
        if (lab != null) {
            s.walkPathForTest(lab.second, lab.first + 1)
            k = 0
            while (k++ < 600 && pos().first == 0 && (pos().second != lab.second || pos().third != lab.first + 1)) step(2)
            s.setKeys(NativeCore.KEY_UP); step(16); s.setKeys(0)
            k = 0
            while (k++ < 300 && pos().first == 0) step(2)
        }
        fase("laboratorio"); step(60)
        // verso il Professore (in alto al centro) e A
        k = 0
        while (k++ < 40 && s.latestSnapshot.get().bag.any { it.id == 70 }) {
            if (s.latestSnapshot.get().freeRoam) { s.setKeys(NativeCore.KEY_UP); step(10); s.setKeys(0); step(4) }
            s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(20)
            if (s.yesNo != null) s.command(it.giallotouch.bridge.GameCommand.Answer(true))
        }
        fase("consegna"); k = 0; var calm2 = 0
        while (k++ < 300 && calm2 < 6) {
            step(20)
            if (s.yesNo != null) { s.command(it.giallotouch.bridge.GameCommand.Answer(true)); calm2 = 0 }
            else if (s.dialogStrip != null || !s.latestSnapshot.get().freeRoam) { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); calm2 = 0 }
            else calm2++
        }
        println("DBGSM fine ${pos()} borsa ${s.latestSnapshot.get().bag}")
        System.getProperty("giallo.scenario")?.let { s.saveStateForTest(File(it, "pokedex2.state")) }
        fase("fine"); step(120)
    }

    /**
     * Analisi verso nord (-Pgiallo.state.citta=STATO, es. dopo la consegna del Pacco): esce dall'edificio, poi va sempre
     * verso il bordo nord di ogni luogo (Strada 1, Smeraldopoli, Strada 2, Bosco Smeraldo) fino a Plumbeopoli (luogo 2),
     * con lotte (PS ricaricati in memoria: solo prova) e dialoghi. Salva una partita a ogni luogo nuovo.
     */
    @Test fun analisiNord() = registra("analisi9", auto = "giallo.state.citta") { step, fase ->
        fun pos() = s.positionForTest()
        fun lotta() {
            fase("lotta"); var b = 0
            while ((s.latestSnapshot.get().inBattle || !s.latestSnapshot.get().freeRoam) && b++ < 400) {
                if (s.battleMenuShown && !s.commandBusy) { s.command(it.giallotouch.bridge.GameCommand.Fight(0)); step(30) }
                else if (s.yesNo != null) { s.command(it.giallotouch.bridge.GameCommand.Answer(false)); step(30) }
                else { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(16) }
            }
            // solo per la prova: PS e PP del primo Pokémon ricaricati, così la passeggiata non si ferma
            val m = s.latestSnapshot.get().party.firstOrNull()
            if (m != null) { s.writeMemoryForTest(0xD16F + 1, m.maxHp shr 8); s.writeMemoryForTest(0xD16F + 2, m.maxHp and 0xFF); s.writeMemoryForTest(0xD16F + 29, 30) }
        }
        fun testi() { var k = 0; while (!s.latestSnapshot.get().freeRoam && !s.latestSnapshot.get().inBattle && k++ < 60) {
            if (s.yesNo != null) s.command(it.giallotouch.bridge.GameCommand.Answer(false)) else { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0) }
            step(20) } }
        fase("uscita"); step(30)
        // fuori dall'edificio: porta più in basso
        run {
            val m = pos().first
            val d = s.warpsForTest().maxByOrNull { it.first }
            if (d != null && m >= 37) {
                s.walkPathForTest(d.second, d.first)
                var j = 0
                while (j++ < 400 && pos().first == m) { step(4); if (j % 20 == 0) { s.setKeys(NativeCore.KEY_DOWN); step(8); s.setKeys(0) } }
            }
        }
        var lastMap = -1
        var k = 0
        while (pos().first != 2 && k++ < 120) {
            val m = pos().first
            if (m != lastMap) {
                lastMap = m; fase("luogo$m"); step(30)
                System.getProperty("giallo.scenario")?.let { s.saveStateForTest(File(it, "nord-$m.state")) }
                println("DBGNO luogo $m a ${pos()}")
            }
            if (s.latestSnapshot.get().inBattle) { lotta(); continue }
            if (!s.latestSnapshot.get().freeRoam) { testi(); continue }
            val g = s.mapGridForTest()
            if (g == null) { step(10); continue }
            // bersaglio: la casella libera raggiungibile più a nord (a parità, più vicina in orizzontale)
            val reach = (0 until g.width).flatMap { x -> (0 until g.height).map { x to it } }
                .filter { g.isFree(it.first, it.second) && g.path(pos().second, pos().third, setOf(it)) != null }
            val target = reach.minWithOrNull(compareBy<Pair<Int, Int>> { it.second }.thenBy { kotlin.math.abs(it.first - pos().second) })
            if (target == null) { step(10); continue }
            if (target.first == pos().second && target.second == pos().third) {
                // già in cima: verso nord (bordo, porta del cancello o casella oltre)
                s.setKeys(NativeCore.KEY_UP); step(20); s.setKeys(0); step(30)
                if (pos().first == m && pos().third == target.second) {
                    // niente passaggio: prova a destra e a sinistra
                    s.setKeys(NativeCore.KEY_RIGHT); step(40); s.setKeys(0); s.setKeys(NativeCore.KEY_UP); step(20); s.setKeys(0); step(20)
                }
                continue
            }
            s.walkPathForTest(target.first, target.second)
            var t = 0
            while (t++ < 500 && pos().first == m && (pos().second != target.first || pos().third != target.second)) {
                step(4)
                if (s.latestSnapshot.get().inBattle) { lotta(); fase("luogo$m"); s.walkPathForTest(target.first, target.second) }
                else if (!s.latestSnapshot.get().freeRoam && t % 8 == 0) { testi(); s.walkPathForTest(target.first, target.second) }
            }
        }
        println("DBGNO fine ${pos()}")
        fase("fine"); step(120)
        System.getProperty("giallo.scenario")?.let { s.saveStateForTest(File(it, "nord-${pos().first}.state")) }
    }

    /**
     * Analisi della palestra di Plumbeopoli (-Pgiallo.state.citta=STATO a Plumbeopoli): entra nella palestra (luogo 54), sale
     * fino a Brock lottando con chi si incontra, lotta con Brock e riesce. Solo prova: PS del nostro Pokémon ricaricati e PS
     * dell'avversario portati a 1 e AZIONE al posto della seconda mossa (Pikachu non fa danni ai Pokémon roccia), così si guarda la scena e non la strategia.
     */
    @Test fun analisiPalestra() = registra("analisi10", auto = "giallo.state.citta") { step, fase ->
        fun pos() = s.positionForTest()
        // mossa nuova: sempre SÌ (dimentica la prima mossa); col NO il gioco richiede all'infinito
        fun lotta() {
            fase("lotta"); var b = 0
            while ((s.latestSnapshot.get().inBattle || !s.latestSnapshot.get().freeRoam) && b++ < 600) {
                if (s.latestSnapshot.get().inBattle && s.battleMenuShown && !s.commandBusy) { s.writeMemoryForTest(it.giallotouch.bridge.MemoryMapIt.ENEMY_MON_HP, 0); s.writeMemoryForTest(it.giallotouch.bridge.MemoryMapIt.ENEMY_MON_HP + 1, 1) }
                if (s.battleMenuShown && !s.commandBusy) { s.command(it.giallotouch.bridge.GameCommand.Fight(1)); step(30) }
                else if (s.yesNo != null) { s.command(it.giallotouch.bridge.GameCommand.Answer(true)); step(30) }
                else { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(16) }
            }
            val m = s.latestSnapshot.get().party.firstOrNull()
            if (m != null) { s.writeMemoryForTest(0xD16F + 1, m.maxHp shr 8); s.writeMemoryForTest(0xD16F + 2, m.maxHp and 0xFF); s.writeMemoryForTest(0xD16F + 29, 30) }
        }
        fun testi() { var k = 0; while (!s.latestSnapshot.get().freeRoam && !s.latestSnapshot.get().inBattle && k++ < 80) {
            if (s.yesNo != null) s.command(it.giallotouch.bridge.GameCommand.Answer(false)) else { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0) }
            step(20) } }
        fun vai(x: Int, y: Int, m: Int) {
            s.walkPathForTest(x, y); var t = 0
            while (t++ < 600 && pos().first == m && (pos().second != x || pos().third != y)) {
                step(4)
                if (s.latestSnapshot.get().inBattle) { lotta(); fase("palestra"); s.walkPathForTest(x, y) }
                else if (!s.latestSnapshot.get().freeRoam && t % 8 == 0) { testi(); s.walkPathForTest(x, y) }
            }
        }
        // solo prova: Pikachu ha solo mosse Elettro e di stato; la seconda mossa diventa AZIONE (0x21, PP 35)
        s.writeMemoryForTest(0xD16F + 9, 0x21); s.writeMemoryForTest(0xD16F + 30, 35)
        fase("città"); step(30)
        val porta = s.warpsForTest().firstOrNull { it.third == 54 }
        println("DBGPA porta $porta da ${pos()}")
        if (porta != null) {
            vai(porta.second, porta.first + 1, 2)
            var j = 0
            while (j++ < 200 && pos().first == 2) { s.setKeys(NativeCore.KEY_UP); step(8); s.setKeys(0); step(8) }
        }
        fase("palestra"); step(60)
        println("DBGPA dentro ${pos()}")
        // Brock in (4,1): si parla da (4,2) guardando in su
        vai(4, 2, pos().first)
        s.setKeys(NativeCore.KEY_UP); step(6); s.setKeys(0); step(6)
        s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0)
        fase("brock")
        var k = 0
        while (k++ < 150 && !s.latestSnapshot.get().inBattle) { step(20); s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0) }
        if (s.latestSnapshot.get().inBattle) lotta()
        fase("dopo"); testi(); step(60)
        println("DBGPA medaglie ${s.memoryForTest(it.giallotouch.bridge.MemoryMapIt.BADGES, 1)[0]} a ${pos()}")
        System.getProperty("giallo.scenario")?.let { s.saveStateForTest(File(it, "brock.state")) }
        fase("uscita")
        val door = s.warpsForTest().maxByOrNull { it.first }
        if (door != null) { vai(door.second, door.first, pos().first); var j = 0; while (j++ < 100 && pos().first == 54) { s.setKeys(NativeCore.KEY_DOWN); step(8); s.setKeys(0); step(8) } }
        fase("fine"); step(120)
    }

    /**
     * Analisi verso est (-Pgiallo.state.citta=STATO dopo Brock, nella palestra): esce, attraversa Plumbeopoli verso est,
     * la Strada 3 (14) fino in fondo, la Strada 4 (15) fino all'ingresso del Monte Luna (59) e gira un po' nella grotta.
     * Come nella palestra: solo prova, PS dell'avversario a 1 e i nostri ricaricati (si guardano le scene, non la strategia).
     * L'indice riporta le persone disegnate (pp=, D-118) per cercare chi sparisce.
     */
    @Test fun analisiEst() = registra("analisi11", auto = "giallo.state.citta") { step, fase ->
        fun pos() = s.positionForTest()
        fun lotta() {
            fase("lotta"); var b = 0
            while ((s.latestSnapshot.get().inBattle || !s.latestSnapshot.get().freeRoam) && b++ < 600) {
                if (s.latestSnapshot.get().inBattle && s.battleMenuShown && !s.commandBusy) { s.writeMemoryForTest(it.giallotouch.bridge.MemoryMapIt.ENEMY_MON_HP, 0); s.writeMemoryForTest(it.giallotouch.bridge.MemoryMapIt.ENEMY_MON_HP + 1, 1) }
                if (s.battleMenuShown && !s.commandBusy) { s.command(it.giallotouch.bridge.GameCommand.Fight(1)); step(30) }
                else if (s.yesNo != null) { s.command(it.giallotouch.bridge.GameCommand.Answer(true)); step(30) }
                else { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(16) }
            }
            val m = s.latestSnapshot.get().party.firstOrNull()
            if (m != null) { s.writeMemoryForTest(0xD16F + 1, m.maxHp shr 8); s.writeMemoryForTest(0xD16F + 2, m.maxHp and 0xFF); s.writeMemoryForTest(0xD16F + 29, 30); s.writeMemoryForTest(0xD16F + 30, 30) }
        }
        fun testi() { var k = 0; while (!s.latestSnapshot.get().freeRoam && !s.latestSnapshot.get().inBattle && k++ < 80) {
            if (s.yesNo != null) s.command(it.giallotouch.bridge.GameCommand.Answer(false)) else { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0) }
            step(20) } }
        /** Cammina fino a (x, y) lottando e leggendo i dialoghi; false se il luogo cambia prima. */
        fun vai(x: Int, y: Int) {
            val m = pos().first
            s.walkPathForTest(x, y); var t = 0
            while (t++ < 600 && pos().first == m && (pos().second != x || pos().third != y)) {
                step(4)
                if (s.latestSnapshot.get().inBattle) { lotta(); fase("luogo$m"); s.walkPathForTest(x, y) }
                else if (!s.latestSnapshot.get().freeRoam && t % 8 == 0) { testi(); s.walkPathForTest(x, y) }
                else if (t % 60 == 0) s.walkPathForTest(x, y)
            }
        }
        fun spingi(key: Int) { var j = 0; val m = pos().first; while (j++ < 30 && pos().first == m) { s.setKeys(key); step(8); s.setKeys(0); step(8)
            if (s.latestSnapshot.get().inBattle) lotta() else if (!s.latestSnapshot.get().freeRoam) testi() } }
        fase("palestra"); step(30)
        // fuori dalla palestra: porta più in basso
        run {
            val d = s.warpsForTest().maxByOrNull { it.first }
            if (d != null) { vai(d.second, d.first); spingi(NativeCore.KEY_DOWN) }
        }
        var lastMap = -1
        var k = 0
        var grotta = 0
        val evitate = HashSet<Triple<Int, Int, Int>>()
        val scadenza = System.currentTimeMillis() + 75 * 60_000L
        while (k++ < 200 && System.currentTimeMillis() < scadenza) {
            val m = pos().first
            if (m != lastMap) { lastMap = m; fase("luogo$m"); step(30); println("DBGES luogo $m a ${pos()}") }
            if (s.latestSnapshot.get().inBattle) { lotta(); continue }
            if (!s.latestSnapshot.get().freeRoam) { testi(); continue }
            if (m == 59) {
                // Monte Luna: qualche giro verso caselle lontane, poi basta
                if (grotta++ >= 4) break
                val g = s.mapGridForTest() ?: run { step(10); null } ?: continue
                val reach = (0 until g.width).flatMap { x -> (0 until g.height).map { x to it } }
                    .filter { g.isFree(it.first, it.second) && g.path(pos().second, pos().third, setOf(it)) != null }
                val far = reach.maxByOrNull { kotlin.math.abs(it.first - pos().second) + kotlin.math.abs(it.second - pos().third) } ?: break
                vai(far.first, far.second); continue
            }
            if (m == 15) {
                // Strada 4: porta del Monte Luna
                val w = s.warpsForTest().firstOrNull { it.third == 59 }
                if (w != null) { vai(w.second, w.first); spingi(NativeCore.KEY_UP); continue }
            }
            val g = s.mapGridForTest()
            if (g == null) { step(10); continue }
            // Plumbeopoli e Strada 3: verso est (a parità, più a nord); in fondo alla Strada 3 si sale alla Strada 4
            val reach = (0 until g.width).flatMap { x -> (0 until g.height).map { x to it } }
                .filter { g.isFree(it.first, it.second) && g.path(pos().second, pos().third, setOf(it)) != null && Triple(m, it.first, it.second) !in evitate }
            val target = if (m == 14) reach.minWithOrNull(compareBy<Pair<Int, Int>> { if (pos().second >= 56) it.second else -it.first / 4 }.thenBy { if (pos().second >= 56) -it.first else it.second }) else
                reach.minWithOrNull(compareBy<Pair<Int, Int>> { -it.first }.thenBy { kotlin.math.abs(it.second - pos().third) })
            if (target == null) { step(10); continue }
            if (target.first == pos().second && target.second == pos().third) {
                spingi(if (m == 14) NativeCore.KEY_UP else NativeCore.KEY_RIGHT)
                if (pos().first == m) spingi(if (m == 14) NativeCore.KEY_RIGHT else NativeCore.KEY_UP)
                if (pos().first == m) evitate += Triple(m, target.first, target.second)
                continue
            }
            val prima = pos()
            vai(target.first, target.second)
            // casella irraggiungibile in pratica (sporgenze, erba): la si scarta
            if (pos().first == m && (pos().second != target.first || pos().third != target.second)) {
                evitate += Triple(m, target.first, target.second); println("DBGES evito $target da $prima a ${pos()}")
            }
        }
        println("DBGES fine ${pos()}")
        fase("fine"); step(120)
        System.getProperty("giallo.scenario")?.let { s.saveStateForTest(File(it, "est-${pos().first}.state")) }
    }

    /**
     * Il resto del Monte Luna e Celestopoli (-Pgiallo.state.citta=STATO nel Monte Luna): esplorazione a scale e porte,
     * preferendo quelle verso luoghi non ancora visti. Lotte di sola prova come in [analisiEst]; alle domande si risponde SÌ.
     */
    @Test fun analisiMonte() = registra("analisi12", auto = "giallo.state.citta") { step, fase ->
        fun pos() = s.positionForTest()
        fun snap() = s.latestSnapshot.get()
        fun lotta() {
            fase("lotta"); var b = 0
            while ((snap().inBattle || !snap().freeRoam) && b++ < 600) {
                if (snap().inBattle && s.battleMenuShown && !s.commandBusy) { s.writeMemoryForTest(it.giallotouch.bridge.MemoryMapIt.ENEMY_MON_HP, 0); s.writeMemoryForTest(it.giallotouch.bridge.MemoryMapIt.ENEMY_MON_HP + 1, 1) }
                if (s.battleMenuShown && !s.commandBusy) { s.command(it.giallotouch.bridge.GameCommand.Fight(1)); step(30) }
                else if (s.yesNo != null) { s.command(it.giallotouch.bridge.GameCommand.Answer(true)); step(30) }
                else { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(16) }
            }
            val m = snap().party.firstOrNull()
            if (m != null) { s.writeMemoryForTest(0xD16F + 1, m.maxHp shr 8); s.writeMemoryForTest(0xD16F + 2, m.maxHp and 0xFF); s.writeMemoryForTest(0xD16F + 29, 30); s.writeMemoryForTest(0xD16F + 30, 30) }
        }
        fun testi() { var k = 0; while (!snap().freeRoam && !snap().inBattle && k++ < 80) {
            if (s.yesNo != null) s.command(it.giallotouch.bridge.GameCommand.Answer(true)) else { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0) }
            step(20) } }
        fun vai(x: Int, y: Int) {
            val m = pos().first
            s.walkPathForTest(x, y); var t = 0
            while (t++ < 600 && pos().first == m && (pos().second != x || pos().third != y)) {
                step(4)
                if (snap().inBattle) { lotta(); fase("luogo$m"); s.walkPathForTest(x, y) }
                else if (!snap().freeRoam && t % 8 == 0) { testi(); s.walkPathForTest(x, y) }
                else if (t % 60 == 0) s.walkPathForTest(x, y)
            }
        }
        fun spingi(key: Int) { var j = 0; val m = pos().first; while (j++ < 6 && pos().first == m) { s.setKeys(key); step(8); s.setKeys(0); step(8)
            if (snap().inBattle) lotta() else if (!snap().freeRoam) testi() } }
        val visti = HashSet<Int>()
        val usate = HashSet<Triple<Int, Int, Int>>()
        val evitate = HashSet<Triple<Int, Int, Int>>()
        val passi = HashSet<Triple<Int, Int, Int>>()
        val parlati = HashSet<Pair<Int, Int>>()
        /** Va accanto a una persona (o a un oggetto, come i fossili) non ancora sentita e preme A guardandola. */
        fun parla(m: Int): Boolean {
            val d1 = s.memoryForTest(0xC100, 256); val d2 = s.memoryForTest(0xC200, 256)
            val g = s.mapGridForTest() ?: return false
            val (_, px, py) = pos()
            for (sl in 1 until 16) {
                if ((d1[sl * 16].toInt() and 0xFF) == 0 || (m to sl) in parlati) continue
                val oy = (d2[sl * 16 + 4].toInt() and 0xFF) - 4; val ox = (d2[sl * 16 + 5].toInt() and 0xFF) - 4
                val lati = listOf(Triple(ox, oy + 1, NativeCore.KEY_UP), Triple(ox, oy - 1, NativeCore.KEY_DOWN),
                    Triple(ox + 1, oy, NativeCore.KEY_LEFT), Triple(ox - 1, oy, NativeCore.KEY_RIGHT))
                    .filter { g.isFree(it.first, it.second) && (it.first == px && it.second == py || g.path(px, py, setOf(it.first to it.second)) != null) }
                parlati += m to sl
                val l = lati.firstOrNull() ?: continue
                println("DBGMO parlo con $sl in ($ox,$oy) da $l")
                vai(l.first, l.second)
                if (pos().first != m) return true
                s.setKeys(l.third); step(3); s.setKeys(0); step(6)
                s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(20)
                if (snap().inBattle) lotta()
                testi(); if (snap().inBattle) lotta()
                return true
            }
            return false
        }
        var lastMap = -1
        val scadenza = System.currentTimeMillis() + 80 * 60_000L
        var k = 0
        while (k++ < 400 && System.currentTimeMillis() < scadenza) {
            val m = pos().first
            if (m != lastMap) { lastMap = m; visti += m; fase("luogo$m"); step(30); println("DBGMO luogo $m a ${pos()}") }
            if (snap().inBattle) { lotta(); continue }
            if (!snap().freeRoam) { testi(); continue }
            if (m == 3) {
                // Celestopoli: un giro verso nord e verso est, poi basta
                if (passi.count { it.first == 3 } > 3) break
            }
            if (m == 61 && parla(m)) continue
            val g = s.mapGridForTest() ?: run { step(10); null } ?: continue
            val (_, px, py) = pos()
            passi += Triple(m, px / 4, py / 4)
            fun raggiungibile(x: Int, y: Int) = g.path(px, py, setOf(x to y)) != null
            val target: Pair<Int, Int>? = if (m == 15) {
                // Strada 4 dopo il Monte Luna: verso est fino a Celestopoli
                (0 until g.width).flatMap { x -> (0 until g.height).map { x to it } }
                    .filter { g.isFree(it.first, it.second) && Triple(m, it.first, it.second) !in evitate && raggiungibile(it.first, it.second) }
                    .minWithOrNull(compareBy<Pair<Int, Int>> { -it.first }.thenBy { kotlin.math.abs(it.second - py) })
            } else {
                val ws = s.warpsForTest().filter { Triple(m, it.second, it.first) !in usate && Triple(m, it.second, it.first) !in evitate && raggiungibile(it.second, it.first) }
                    // da Monte Luna non si torna alla Strada 3/4 dall'ingresso
                    .filter { !(m == 59 && it.third == 0xFF && 61 !in visti) }
                val w = ws.minWithOrNull(compareBy<Triple<Int, Int, Int>> { if (it.third in visti) 1 else 0 }
                    .thenBy { kotlin.math.abs(it.second - px) + kotlin.math.abs(it.first - py) })
                if (w != null) w.second to w.first else
                    // nessuna porta nuova: verso la zona meno visitata
                    (0 until g.width).flatMap { x -> (0 until g.height).map { x to it } }
                        .filter { g.isFree(it.first, it.second) && Triple(m, it.first / 4, it.second / 4) !in passi && Triple(m, it.first, it.second) !in evitate && raggiungibile(it.first, it.second) }
                        .maxByOrNull { kotlin.math.abs(it.first - px) + kotlin.math.abs(it.second - py) }
            }
            if (target == null) { println("DBGMO fermo in $m a ${pos()}"); break }
            if (target.first == px && target.second == py) {
                usate += Triple(m, px, py)
                for (key in intArrayOf(NativeCore.KEY_DOWN, NativeCore.KEY_UP, NativeCore.KEY_LEFT, NativeCore.KEY_RIGHT)) if (pos().first == m) spingi(key)
                if (pos().first == m) evitate += Triple(m, px, py)
                continue
            }
            vai(target.first, target.second)
            if (pos().first == m) {
                if (pos().second != target.first || pos().third != target.second) { evitate += Triple(m, target.first, target.second); println("DBGMO evito $target in $m da ${pos()}") }
                // persone vicine: si parla (fossili, allenatori fermi)
                s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0); step(10); if (!snap().freeRoam) testi()
            } else usate += Triple(m, target.first, target.second)
        }
        println("DBGMO fine ${pos()} visti $visti")
        fase("fine"); step(120)
        System.getProperty("giallo.scenario")?.let { s.saveStateForTest(File(it, "monte-${pos().first}.state")) }
    }

    /** Analisi della cura al Centro Pokémon (-Pgiallo.state.centro=STATO nel Centro): infermiera, CURA, uscita. */
    @Test fun analisiCentro() = registra("analisi6", auto = "giallo.state.centro") { step, fase ->
        fun pos() = s.positionForTest()
        fase("centro"); step(30)
        s.walkPathForTest(3, 3)
        var k = 0
        while (k++ < 300 && pos().let { it.second != 3 || it.third != 3 }) step(2)
        s.setKeys(NativeCore.KEY_UP); step(6); s.setKeys(0); step(6)
        s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0)
        fase("cura")
        k = 0
        while (s.yesNo == null && k++ < 60) { step(20); if (s.yesNo == null) { s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0) } }
        step(30)
        s.command(it.giallotouch.bridge.GameCommand.Answer(true))
        k = 0
        while (!s.latestSnapshot.get().freeRoam && k++ < 80) { step(20); s.setKeys(NativeCore.KEY_A); step(4); s.setKeys(0) }
        fase("uscita")
        val door = s.warpsForTest().maxByOrNull { it.first }
        if (door != null) {
            s.walkPathForTest(door.second, door.first)
            k = 0
            val m = pos().first
            while (k++ < 300 && pos().first == m) { step(4); if (k % 20 == 0) { s.setKeys(NativeCore.KEY_DOWN); step(8); s.setKeys(0) } }
        }
        fase("città"); step(120)
    }

    /**
     * Registra ogni fotogramma (sessione in passo a passo dall'accensione) in [dir]: un'immagine per ogni fotogramma
     * diverso dal precedente (320 di larghezza) e indice.txt "fotogramma file lampeggio fase stato menu titolo".
     */
    private class Fermati : RuntimeException()

    private fun registra(dir: String, auto: String? = null, body: (step: (Int) -> Unit, fase: (String) -> Unit) -> Unit) {
        GameSession.testStepFromStart = true
        GameSession.debugPeople = true
        try {
            s = (if (auto != null) start(auto = auto) else start(battery = "giallo.battery")) ?: return assumeTrue(false)
        } finally { GameSession.testStepFromStart = false }
        if (auto != null) assertTrue(s.loadSlot(SaveSlot.AUTO))
        val out = File(shots, dir).apply { deleteRecursively(); mkdirs() }
        val index = StringBuilder()
        var last: IntArray? = null; var file = ""; var n = 0
        val win = ArrayDeque<IntArray>()
        var fase = "accensione"
        val step: (Int) -> Unit = { count ->
            repeat(count) {
                s.stepPermits!!.release()
                s.stepDone!!.tryAcquire(3, java.util.concurrent.TimeUnit.SECONDS)
                shadowOf(Looper.getMainLooper()).idle()
                n++
                val b = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888); root.draw(Canvas(b))
                val sm = Bitmap.createScaledBitmap(b, 320, root.height * 320 / root.width, false)
                val px = IntArray(sm.width * sm.height); sm.getPixels(px, 0, sm.width, 0, 0, sm.width, sm.height)
                if (last == null || !px.contentEquals(last)) {
                    file = "f%05d.png".format(n)
                    FileOutputStream(File(out, file)).use { sm.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    last = px
                }
                win.addLast(px); if (win.size > 3) win.removeFirst()
                var blip = 0
                if (win.size == 3) { val a = win[0]; val m = win[1]; val c = win[2]
                    for (i in a.indices) if (m[i] != a[i] && m[i] != c[i] && a[i] == c[i]) blip++ }
                val raw = s.latestFrame.get(); val rawWhite = raw.all { it == raw[0] }
                System.getProperty("giallo.sprdump")?.split("-")?.let { (a, b) -> if (n in a.toInt()..b.toInt()) {
                    val d1 = s.memoryForTest(0xC100, 256); val d2 = s.memoryForTest(0xC200, 256)
                    fun h(b: ByteArray, o: Int) = (0 until 16).joinToString("") { "%02x".format(b[o + it].toInt() and 0xFF) }
                    println("DBGSP $n " + (0 until 16).filter { (d1[it * 16].toInt() and 0xFF) != 0 }.joinToString(" | ") { "$it:${h(d1, it * 16)}/${h(d2, it * 16)}" })
                } }
                System.getProperty("giallo.raw")?.split(",")?.map { it.split("-") }?.firstOrNull { (a, b) -> n in a.toInt()..b.toInt() }?.let {
                    if (true) {
                    val rb = Bitmap.createBitmap(raw, 160, 144, Bitmap.Config.ARGB_8888)
                    FileOutputStream(File(out, "raw%05d.png".format(n))).use { rb.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
                index.append("$n $file $blip $fase ${if (rawWhite) "uniforme" else "immagine"} ${s.mainMenuItems} ${s.titleShown} " +
                    "lotta=${s.latestSnapshot.get().inBattle} divisa=${s.battleSplit} dialogo=${s.dialogStrip != null} estesa=${s.latestExtended.get().active} libero=${s.freeRoamNow} ${s.debugFrameInfo}\n")
                // indice salvato anche durante la registrazione: se la prova viene fermata per tempo non si perde
                if (n % 2000 == 0) File(out, "indice.txt").writeText(index.toString())
                // -Pgiallo.fermati=N: registrazione corta (per confrontare con le immagini del gioco)
                System.getProperty("giallo.fermati")?.toIntOrNull()?.let { if (n >= it) throw Fermati() }
            }
        }
        try { body(step) { fase = it } } catch (_: Fermati) {}
        File(out, "indice.txt").writeText(index.toString())
        println("DBGAN $dir fotogrammi $n, immagini ${out.list()!!.size - 1}")
    }

    /** OPZIONI dal menu iniziale: foto di com'è oggi (richiesta del committente, mockup). */
    @Test fun opzioniIniziali() {
        s = start(battery = "giallo.battery") ?: return assumeTrue(false)
        var k = 0
        while (!s.titleShown && k++ < 120) pump(500)
        pump(1500)
        val t0 = System.nanoTime()
        press(s, NativeCore.KEY_START)
        waitFor(10_000) { s.mainMenuItems > 0 }
        println("DBGOP menu iniziale dopo START in ${(System.nanoTime() - t0) / 1_000_000} ms, veloci ${s.fastFrames}, titolo ${s.titleShown} inGame ${s.inGame}")
        k = 0
        while (s.mainMenuItems == 0 && k++ < 10) { press(s, NativeCore.KEY_START); pump(1500) }
        pump(800)
        val menuShot = shot("op-menu")
        val entries = it.giallotouch.bridge.StartMenuLayout.entries(s.mainMenuItems)
        val frameTop = (root.height - 144 * unit) / 2 / (8 * unit)
        // nella prova completa il primo tocco a volte arriva mentre il menu compare: se lo schermo non cambia si ritocca
        var opTries = 0
        do {
            tapTile(4f, frameTop + it.giallotouch.bridge.StartMenuLayout.row(entries.indexOf(it.giallotouch.bridge.StartMenuLayout.Entry.OPZIONI)) + 0.5f)
            pump(1000)
        } while (opTries++ < 3 && shot("op-opzioni").sameAs(menuShot))
        // OPZIONI uniche dell'app (D-075, D-080): un tocco su ANIMAZIONE la cambia nella memoria del gioco
        assertTrue("OPZIONI dell'app", shotUntil("op-opzioni") { boxAt(it, 0, 6, 20, 24) })
        val before = s.gameOptions and 0x80
        var tries = 0
        while (tries++ < 3 && (s.gameOptions and 0x80) == before) { tapTile(4f, 8.5f); waitFor(2_000) { (s.gameOptions and 0x80) != before } }
        assertTrue("animazione cambiata", (s.gameOptions and 0x80) != before)
        val after = s.gameOptions
        pump(800)
        assertEquals("scritta nel gioco", after, s.gameOptions)
        // volume: 5 tacche da 20%; la prima tacca = 20%, toccata di nuovo = muto
        tapTile(3.6f, 21.5f); pump(300)
        shot("op-volume-1")
        tapTile(3.6f, 21.5f); pump(300)
        // COMPARSA TESTO: velocità del testo del gioco; AUTO-TESTO: sì/no
        val sp = s.gameOptions and 0x0F
        tapTile(4f, 14.5f)
        assertTrue("velocità del testo cambiata", waitFor(3_000) { (s.gameOptions and 0x0F) != sp })
        tapTile(4f, 17.5f); pump(300)
        shot("op-autotesto")
        shot("op-volume-0")
        shot("op-opzioni-dopo")
    }

    /** Introduzione dall'accensione (D-072): nessuna riga scura che attraversa lo schermo (bande nere o righine). */
    @Test fun video() {
        s = start() ?: return assumeTrue(false)
        s.stepDone = java.util.concurrent.Semaphore(0)
        s.stepPermits = java.util.concurrent.Semaphore(0)
        capture = false
        val pulses = ArrayList<Long>(); var stepNo = 0L
        s.onHit = { pulses.add(stepNo) }
        var saved = 0; var bad = 0
        for (q in 0 until 520) {
            frames(4, "video"); stepNo += 4
            val (px, bmp) = grab()
            val h = px.size / 160
            var dark = 0
            for (y in 0 until h) {
                var all = true
                for (x in 0 until 160) { val c = px[y * 160 + x]; if (((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255) > 150) { all = false; break } }
                if (all) dark++
            }
            // righine: una riga uniforme diversa dalle righe uniformi sopra e sotto
            for (y in 1 until h - 1) {
                fun uni(r: Int): Int? { val c = px[r * 160]; for (x in 1 until 160) if (px[r * 160 + x] != c) return null; return c }
                val c = uni(y) ?: continue; val a = uni(y - 1) ?: continue; val b = uni(y + 1) ?: continue
                if (a == b && c != a) dark++
            }
            if (dark in 1 until h) { bad++; if (saved < 40 && q % 3 == 0) { saved++; FileOutputStream(File(shots, "vd-%03d-%d.png".format(q, dark))).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
            if (q % 25 == 0 || (q in 118..142) || (q > 700 && q % 5 == 0)) FileOutputStream(File(shots, "vv-%03d.png".format(q))).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        println("DBGVD fotogrammi con righe scure: $bad; vibrazioni ai fotogrammi $pulses")
    }

    /** Inizio del gioco: titolo, menu iniziale, NUOVO GIOCO, discorso del Professore, nomi, fino alla cameretta. */
    @Test fun inizio() {
        s = start(auto = "giallo.state.title") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        pump(800)
        s.stepDone = java.util.concurrent.Semaphore(0)
        s.stepPermits = java.util.concurrent.Semaphore(0)
        sheetTags += listOf("menu-iniziale", "nuovo-gioco", "intro0", "intro1", "intro2", "intro5", "intro8", "sino") + (0 until 70).map { "nome$it" } + (0 until 70).map { "sino$it" }
        frames(240, "titolo")
        press(s, NativeCore.KEY_START); frames(120, "titolo-start")
        var k = 0
        while (s.mainMenuItems == 0 && k++ < 10) { press(s, NativeCore.KEY_START); frames(60, "titolo-start") }
        frames(30, "menu-iniziale")
        val frameTop = (root.height - 144 * unit) / 2 / (8 * unit)
        tapTile(4f, frameTop + it.giallotouch.bridge.StartMenuLayout.row(it.giallotouch.bridge.StartMenuLayout.entries(s.mainMenuItems).size - 1) + 0.5f)
        for (q in 0 until 60) {
            frames(1, "nuovo-gioco")
            val (v, t) = s.screenForTest()
            if (q in 28..42) shot("ng-%02d".format(q))
        }
        frames(60, "nuovo-gioco2")
        for (i in 0 until 70) {
            if (s.latestSnapshot.get().freeRoam && i > 20) break
            if (s.nameList != null) { tapTile(4f, 17.5f); frames(90, "nome$i") }
            else if (s.yesNo != null) { tapTile(14f, 39f); frames(60, "sino$i") }
            else { tapTile(15f, 30f); frames(45, "intro$i") }
        }
        s.stepPermits = null
        println("DBGGL fotogrammi $n lampeggi $blips")
    }

    /** Colori ai confini e nelle dissolvenze delle porte (-Pgiallo.state.shop=route1.state). */
    @Test fun colori() {
        s = start(auto = "giallo.state.shop") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        waitFor(10_000) { s.latestSnapshot.get().freeRoam }
        assumeTrue(s.positionForTest().first == 12)
        s.walkPathForTest(11, 34)
        waitFor(40_000) { s.positionForTest().third == 34 }
        pump(800)
        s.stepDone = java.util.concurrent.Semaphore(0)
        s.stepPermits = java.util.concurrent.Semaphore(0)
        sheetTags += listOf("confine", "pallet", "porta")
        s.setKeys(NativeCore.KEY_DOWN); frames(48, "confine"); s.setKeys(0); frames(20, "pallet")
        // porta di casa più vicina
        val w = s.warpsForTest().minByOrNull { kotlin.math.abs(it.second - s.positionForTest().second) + kotlin.math.abs(it.first - s.positionForTest().third) }
        if (w != null) {
            s.walkPathForTest(w.second, w.first + 1)
            var k = 0
            while (k++ < 900 && s.positionForTest().let { it.second != w.second || it.third != w.first + 1 }) frames(1, "cammina")
            s.setKeys(NativeCore.KEY_UP); frames(48, "porta"); s.setKeys(0); frames(48, "porta")
        }
        s.stepPermits = null
        println("DBGGL fotogrammi $n lampeggi $blips")
    }

    /** Ingresso in lotta, fotogramma per fotogramma (-Pgiallo.state.two=two.state). */
    @Test fun prelotta() {
        s = start(auto = "giallo.state.two") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        waitFor(10_000) { s.latestSnapshot.get().freeRoam }
        s.stepDone = java.util.concurrent.Semaphore(0)
        s.stepPermits = java.util.concurrent.Semaphore(0)
        val rnd = java.util.Random(5)
        var walks = 0
        var shotsLeft = 0
        val seq = ArrayList<Bitmap>()
        capture = false
        while (walks++ < 40 && seq.size < 72) {
            val g = s.mapGridForTest() ?: break
            val free = (0 until g.width).flatMap { x -> (6 until g.height - 4).map { y -> x to y } }.filter { g.isFree(it.first, it.second) }
            val t = free[rnd.nextInt(free.size)]
            s.walkPathForTest(t.first, t.second)
            var k = 0
            while (k++ < 600 && seq.size < 72) {
                frames(1, "cammina")
                if (shotsLeft == 0 && (s.frameTilesForTest().let { false } || NativeCoreBattle(s))) shotsLeft = 140
                if (shotsLeft > 0) { shotsLeft--; if (shotsLeft % 2 == 0) { val b = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888); root.draw(Canvas(b)); seq += Bitmap.createScaledBitmap(b, b.width / 4, b.height / 4, false) } }
                if (s.positionForTest().let { it.second == t.first && it.third == t.second } && shotsLeft == 0) break
            }
        }
        s.stepPermits = null
        val cols = 12; val w = seq.firstOrNull()?.width ?: return; val h = seq[0].height
        val out = Bitmap.createBitmap(w * cols, h * ((seq.size + cols - 1) / cols), Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        seq.forEachIndexed { k, b -> cv.drawBitmap(b, ((k % cols) * w).toFloat(), ((k / cols) * h).toFloat(), null) }
        FileOutputStream(File(shots, "fs-prelotta.png")).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun NativeCoreBattle(s: GameSession) = s.memoryForTest(0xD05B, 1)[0].toInt() != 0

    /** Pozione su Pikachu fuori dalla lotta, fotogramma per fotogramma (-Pgiallo.state.bag=bagpotion.state). */
    @Test fun pozione() {
        s = start(auto = "giallo.state.shop") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        waitFor(10_000) { s.latestSnapshot.get().freeRoam }
        pump(1000)
        if (s.latestSnapshot.get().bag.none { it.id == 0x14 }) {
            // al Pokémon Market: COMPRA → POZIONE (seconda voce) → OK
            val g = s.mapGridForTest()!!
            val clerk = g.people.minByOrNull { it.first }!!
            val here = s.positionForTest()
            val spot = g.interactionSpots(clerk.first, clerk.second).entries.first { g.path(here.second, here.third, setOf(it.key)) != null }
            s.walkPathForTest(spot.key.first, spot.key.second)
            waitFor(30_000) { s.positionForTest().let { it.second == spot.key.first && it.third == spot.key.second } }
            s.setKeys(spot.value.key); pump(150); s.setKeys(0); pump(400)
            tapTile(10f, 20f)
            var k = 0
            while (!s.shopMenuShown && k++ < 10) tapTile(10f, 20f)
            pump(1500); tapTile(4f, 35f); pump(1500)       // COMPRA
            tapTile(4f, 17.5f); pump(1000)                  // POZIONE
            tapTile(16f, 41f)                               // OK (quantità 1)
            waitFor(40_000) { !s.itemBusy && s.latestSnapshot.get().bag.any { it.id == 0x14 } }
            tapTile(9f, 40.5f); pump(800); tapTile(9f, 40f)  // INDIETRO, ESCI
            waitFor(20_000) { s.latestSnapshot.get().freeRoam }
            pump(1500)
        }
        println("DBGGL squadra ${s.latestSnapshot.get().party.map { it.name + " " + it.hp + "/" + it.maxHp }} borsa ${s.latestSnapshot.get().bag}")
        s.stepDone = java.util.concurrent.Semaphore(0)
        s.stepPermits = java.util.concurrent.Semaphore(0)
        sheetTags += listOf("p-start", "p-borsa", "p-pozione", "p-usa", "p-pikachu", "p-dopo", "p-dopo2", "p-tocco1", "p-tocco2", "p-fine")
        val r = rows().toFloat()
        frames(10, "fermo")
        tapTile(10f, r - 2f); frames(30, "p-start")
        tapTile(4f, 36f); frames(30, "p-borsa")
        tapTile(4f, 10f); frames(30, "p-pozione")
        tapTile(4f, 37f); frames(40, "p-usa")
        tapTile(4f, 9f); frames(48, "p-pikachu")
        frames(48, "p-dopo")
        for (i in 0 until 6) { s.setKeys(NativeCore.KEY_A); frames(4, "p-tasto"); s.setKeys(0); frames(40, if (i == 0) "p-dopo2" else "p-tocco$i") }
        frames(48, "p-fine")
        s.stepPermits = null
        println("DBGGL fotogrammi $n lampeggi $blips libero=${s.latestSnapshot.get().freeRoam}")
    }

    @Test fun lampeggi() {
        s = start(auto = "giallo.state.two") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        waitFor(10_000) { s.latestSnapshot.get().freeRoam }
        pump(1000)
        s.stepDone = java.util.concurrent.Semaphore(0)
        s.stepPermits = java.util.concurrent.Semaphore(0)
        sheetTags += listOf("scheda2", "scheda2-indietro", "start", "pokemon", "pokemon-indietro", "borsa", "borsa-indietro", "scheda", "scheda-indietro", "opzioni", "opzioni-indietro", "salva", "dialogo0", "dialogo1", "dialogo2")
        frames(20, "fermo")
        val r = rows().toFloat()
        tapTile(10f, r - 2f); frames(40, "start")
        tapTile(4f, 30f); frames(60, "pokemon")
        tapTile(4f, 40.5f); frames(60, "pokemon-indietro")
        tapTile(10f, r - 2f); frames(30, "start2")
        tapTile(4f, 36f); frames(60, "borsa")
        tapTile(4f, 40.5f); frames(60, "borsa-indietro")
        tapTile(10f, r - 2f); frames(30, "start3")
        tapTile(14f, 30f)
        var w = 0
        var lastK = ""
        while (!s.trainerCardShown && w < 400) {
            frames(1, "scheda"); w++
            val (v, t) = s.screenForTest()
            val k = it.giallotouch.bridge.ItemMenus.read(v, t, 0).let { "${it.kind}/${it.cursor}" } + " free=" + s.latestSnapshot.get().freeRoam
            if (k != lastK) { println("DBGGL   $w $k"); lastK = k }
        }
        println("DBGGL tempo START → scheda: $w fotogrammi")
        frames(60, "scheda")
        tapTile(9f, 40.5f)
        w = 0
        while (!(s.latestSnapshot.get().freeRoam && !s.itemBusy) && w < 400) { frames(1, "scheda-indietro"); w++ }
        println("DBGGL tempo scheda → mappa libera: $w fotogrammi")
        frames(60, "scheda-indietro")
        // seconda apertura: la scheda memorizzata compare subito
        tapTile(10f, r - 2f); frames(30, "start-bis")
        tapTile(14f, 30f)
        w = 0
        while (!s.trainerCardShown && w < 400) { frames(1, "scheda2"); w++ }
        println("DBGGL tempo START → scheda (seconda volta): $w fotogrammi")
        frames(150, "scheda2")
        tapTile(9f, 40.5f); frames(90, "scheda2-indietro")
        tapTile(10f, r - 2f); frames(30, "start4")
        tapTile(15f, 33f); frames(40, "opzioni")
        tapTile(9f, 40.5f); frames(40, "opzioni-indietro")
        tapTile(10f, r - 2f); frames(30, "start5")
        tapTile(15f, 36f); frames(120, "salva")
        // dialogo con un cartello o una persona vicina
        val g = s.mapGridForTest()!!
        val here = s.positionForTest()
        val target = (g.signs + g.people).mapNotNull { t -> g.interactionSpots(t.first, t.second).entries.firstOrNull { g.path(here.second, here.third, setOf(it.key)) != null } }
            .minByOrNull { kotlin.math.abs(it.key.first - here.second) + kotlin.math.abs(it.key.second - here.third) }
        if (target != null) {
            s.walkPathForTest(target.key.first, target.key.second)
            var k = 0
            while (k++ < 600 && s.positionForTest().let { it.second != target.key.first || it.third != target.key.second }) frames(1, "cammina")
            s.setKeys(target.value.key); frames(8, "girata"); s.setKeys(0); frames(10, "girata")
            for (i in 0 until 8) { tapTile(10f, 20f); frames(70, "dialogo$i") }
        }
        s.stepPermits = null
        println("DBGGL fotogrammi $n lampeggi $blips")
    }
}

/** Scelta del file (D-066): dopo una partita la schermata usa riquadri e lettere presi dal file; senza, disegnata dall'app. */
class AppPickerTest : AppScreensHarness() {
    @Test fun sceltaFile() {
        val s = start(auto = "giallo.state.bag") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue(waitFor(10_000) { s.latestSnapshot.get().freeRoam })
        val ctx = root.context
        val tiles = File(ctx.filesDir, "grafica-del-gioco.bin")
        // un dialogo (il PC) fa caricare al gioco i riquadri; poi si torna a camminare
        tapTile(10f, 20f); pump(1500)
        var k = 0
        while (!s.latestSnapshot.get().freeRoam && k++ < 20) { press(s, NativeCore.KEY_B); pump(500) }
        assertTrue("grafica salvata", waitFor(15_000) { tiles.isFile && tiles.length() == 128L * 128 * 4 })
        controller?.pause()?.stop()?.destroy()
        File(ctx.filesDir, "partita/gioco.rom").delete(); File(ctx.filesDir, "partita/gioco.rom.bak").delete()
        val c2 = Robolectric.buildActivity(MainActivity::class.java).setup(); controller = c2; root = c2.get().window.decorView
        pump(800)
        assertTrue("schermata con la grafica del gioco", shotUntil("60-scelta-file") { b -> boxAt(b, 0, 10, 20, 17) && boxAt(b, 1, 29, 18, 4) })
        c2.pause().stop().destroy()
        tiles.delete()
        val c3 = Robolectric.buildActivity(MainActivity::class.java).setup(); controller = c3; root = c3.get().window.decorView
        pump(800)
        shot("61-scelta-file-prima-volta")
    }
}

/**
 * Etere (D-093): borsa con ETERE scritto in memoria, PP della terza mossa a 5; BORSA → ETERE → USA → SU CHI? dell'app
 * → PIKACHU → riquadro dell'app con le mosse (disposizione B) → tocco sulla terza mossa → PP da 5 a 15.
 */
class AppEtereTest : EtereHarness() {
    @Test fun etere() {
        if (System.getProperty("giallo.state.two") == null) return assumeTrue(false)
        val s = apri(0x50)
        assertTrue("lista delle mosse del gioco", waitFor(20_000) { s.etherList })
        pump(600)
        assertTrue("riquadri dell'app (disposizione B)", shotUntil("72-etere-mosse") { b -> boxAt(b, 0, 28, 20, 10) && boxAt(b, 3, 39, 14, 4) && boxAt(b, 0, 1, 20, 7) })
        println("DBGET testo ${s.etherText} busy=${s.commandBusy} item=${s.itemBusy}")
        if (System.getProperty("giallo.manuale") != null) press(s, NativeCore.KEY_A) else tapTile(6f, 33.5f)   // terza mossa
        var k = 0
        while (k++ < 40 && s.etherList) pump(300)
        shot("73-etere-messaggio")
        k = 0
        while (k++ < 40 && !s.latestSnapshot.get().freeRoam) { tapTile(10f, 20f); pump(400) }
        pump(1000)
        shot("74-etere-fine")
        assertTrue("di nuovo a camminare", s.latestSnapshot.get().freeRoam)
        assertEquals("un ETERE usato", 2, s.latestSnapshot.get().bag.firstOrNull { it.id == 0x50 }?.quantity)
        pump(2000); assertTrue("PP ricaricati: ${s.latestSnapshot.get().party.map { it.name to it.moves }}", s.latestSnapshot.get().party.first().moves[2].pp == 15)
    }
}

/** INDIETRO del menu Etere (D-093) ed Elisir (tutte le mosse, senza scelta della mossa). */
abstract class EtereHarness : AppScreensHarness() {
    protected fun apri(item: Int): GameSession {
        val s = start(auto = "giallo.state.two")!!
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        waitFor(10_000) { s.latestSnapshot.get().freeRoam }
        s.writeMemoryForTest(it.giallotouch.bridge.MemoryMapIt.BAG_COUNT, 1)
        s.writeMemoryForTest(it.giallotouch.bridge.MemoryMapIt.BAG_ITEMS, item)
        s.writeMemoryForTest(it.giallotouch.bridge.MemoryMapIt.BAG_ITEMS + 1, 3)
        s.writeMemoryForTest(it.giallotouch.bridge.MemoryMapIt.BAG_ITEMS + 2, 0xFF)
        s.writeMemoryForTest(0xD16F + 29, 5); s.writeMemoryForTest(0xD16F + 31, 5)
        pump(800)
        var opened = false
        for (t in 0 until 3) {
            if (opened) break
            pump(800); tapStart(); pump(800)
            tapTile(4f, 36f)                  // BORSA
            opened = shotUntil("75-borsa") { b -> boxAt(b, 0, 3, 20, 3) }
            if (!opened) { s.closeMenus(); waitFor(10_000) { s.latestSnapshot.get().freeRoam && !s.menusClosing } }
        }
        assertTrue("borsa", opened)
        tapTile(4f, 10f); pump(800)
        tapTile(4f, 37f)                      // USA
        assertTrue("su chi? dell'app", shotUntil("76-su-chi") { b -> boxAt(b, 0, 7, 20, 32) })
        tapTile(4f, 9f)                       // PIKACHU
        return s
    }

    protected fun finoAllaMappa(s: GameSession) {
        var k = 0
        while (k++ < 40 && !s.latestSnapshot.get().freeRoam) { tapTile(10f, 20f); pump(400) }
        pump(1500)
        assertTrue("di nuovo a camminare", s.latestSnapshot.get().freeRoam)
    }

}

/** Una prova per classe: ogni classe parte da una sessione nuova. */
class AppEtereIndietroTest : EtereHarness() {
    @Test fun indietro() {
        if (System.getProperty("giallo.state.two") == null) return assumeTrue(false)
        val s = apri(0x50)
        assertTrue("lista delle mosse", waitFor(20_000) { s.etherList })
        pump(600)
        tapTile(10f, 41f)                     // INDIETRO
        assertTrue("lista chiusa", waitFor(10_000) { !s.etherList })
        shot("77-etere-indietro")
        finoAllaMappa(s)
        shot("78-etere-indietro-mappa")
        assertEquals("ETERE non usato", 3, s.latestSnapshot.get().bag.firstOrNull { it.id == 0x50 }?.quantity)
        assertEquals("PP invariati", 5, s.latestSnapshot.get().party.first().moves[0].pp)
    }

}

class AppElisirTest : EtereHarness() {
    @Test fun elisir() {
        if (System.getProperty("giallo.state.two") == null) return assumeTrue(false)
        val s = apri(0x52)
        pump(1500)
        shot("79-elisir")
        assertFalse("nessuna scelta della mossa", s.etherList)
        finoAllaMappa(s)
        val mv = s.latestSnapshot.get().party.first().moves
        assertTrue("PP ricaricati su tutte: $mv", mv[0].pp == 15 && mv[2].pp == 15)
        assertEquals("un ELISIR usato", 2, s.latestSnapshot.get().bag.firstOrNull { it.id == 0x52 }?.quantity)
    }
}

/** PP-su (D-093): stessa lista dell'Etere; tocco sulla prima mossa → PP massimi aumentati (ppUps 1). */
class AppPpSuTest : EtereHarness() {
    @Test fun ppSu() {
        if (System.getProperty("giallo.state.two") == null) return assumeTrue(false)
        val s = apri(0x4F)
        assertTrue("lista delle mosse", waitFor(20_000) { s.etherList })
        pump(600)
        shot("80-ppsu")
        println("DBGET testo ${s.etherText}")
        tapTile(6f, 29.5f)                    // prima mossa
        var k = 0
        while (k++ < 40 && s.etherList) pump(300)
        finoAllaMappa(s)
        pump(1500)
        val mv = s.latestSnapshot.get().party.first().moves
        assertEquals("PP-su sulla prima mossa: $mv", 1, mv[0].ppUps)
        assertEquals("un PP-su usato", 2, s.latestSnapshot.get().bag.firstOrNull { it.id == 0x4F }?.quantity)
    }
}

