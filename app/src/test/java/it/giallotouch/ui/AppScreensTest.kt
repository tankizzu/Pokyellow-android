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
    private var controller: org.robolectric.android.controller.ActivityController<MainActivity>? = null

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

    protected fun tap(x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        root.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        pump(48)
        root.dispatchTouchEvent(MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0))
        pump(600)
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
        assertTrue("START nascosto nel dialogo", shotUntil("05b-dialogo") { !boxAt(it, 7, rows() - 4, 7, 4) })
        var k = 0
        while (!s.latestSnapshot.get().freeRoam && k++ < 20) press(s, NativeCore.KEY_B)
        waitFor(5_000) { s.latestSnapshot.get().freeRoam }
        assertTrue("START di nuovo visibile", shotUntil("05c-dopo-dialogo") { boxAt(it, 7, rows() - 4, 7, 4) })
        tapTile(10f, rows() - 2f)
        assertTrue("riquadro dei pulsanti aperto", shotUntil("06-menu-aperto") { boxAt(it, 0, rows() - 13, 20, 9) })
        tapTile(4f, rows() - 7f)     // BORSA
        assertTrue("Borsa aperta", shotUntil("06b-borsa") { boxAt(it, 0, 3, 20, 3) })
        tapTile(4f, 3f)              // fuori dalle voci: niente
        root.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
        root.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK))
        pump(800)
        tapTile(10f, rows() - 2f)    // MENU di nuovo
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
        assertTrue("azioni e mosse", shotUntil("09-lotta") { boxAt(it, 0, 22, 20, 5) && boxAt(it, 0, 27, 20, 11) })
    }

    /** Scheda allenatore (D-045): START → nome del giocatore apre la scheda del gioco ridisposta; INDIETRO chiude. */
    fun schedaAllenatore() {
        val s = start(auto = "giallo.state.squadra") ?: return assumeTrue(false)
        pump(1500)
        assertTrue(s.loadSlot(SaveSlot.AUTO))
        assertTrue("in partita", waitFor(20_000) { s.inGame && s.latestSnapshot.get().freeRoam })
        tapTile(10f, rows() - 2f)
        assertTrue("menu aperto", shotUntil("10-menu-allenatore") { boxAt(it, 0, rows() - 15, 20, 11) })
        tapTile(9f, rows() - 13f)    // riga del nome del giocatore
        assertTrue("scheda del gioco aperta", waitFor(20_000) { s.trainerCardShown })
        assertTrue("riquadri della scheda", shotUntil("11-scheda-allenatore") { boxAt(it, 0, 30, 20, 9) && boxAt(it, 5, 40, 10, 4) })
        tapTile(9f, 41.5f)           // INDIETRO
        assertTrue("di nuovo in giro", waitFor(15_000) { !s.trainerCardShown && s.latestSnapshot.get().freeRoam })
        shot("12-dopo-scheda")
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

class AppTrainerScreenTest : AppScreensHarness() {
    @Test fun scheda() = schedaAllenatore()
}
