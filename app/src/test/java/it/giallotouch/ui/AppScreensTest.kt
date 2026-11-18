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
        assertTrue("START nascosto e dialogo in alto (riga 9)", shotUntil("05b-dialogo") { !boxAt(it, 7, rows() - 4, 7, 4) && boxAt(it, 0, 9, 20, 6) })
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
            tapTile(10f, rows() - 2f); pump(800)
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
        tapTile(10f, rows() - 2f)
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
        tapTile(10f, rows() - 2f)
        assertTrue("START aperto", shotUntil("13-start") { boxAt(it, 0, 28, 20, 11) && boxAt(it, 0, 2, 20, 8) })
        tapTile(15f, 36f)            // SALVA
        assertTrue("messaggio nello stile del gioco", shotUntil("13b-salvata") { boxAt(it, 0, 9, 20, 6) })
        pump(2500)
        tapTile(10f, rows() - 2f)
        assertTrue("START aperto di nuovo", shotUntil("13c-start") { boxAt(it, 0, 28, 20, 11) })
        tapTile(4f, 30f)             // POKéMON
        assertTrue("squadra", shotUntil("14-squadra") { boxAt(it, 0, 7, 20, 32) && boxAt(it, 0, 39, 10, 4) && boxAt(it, 10, 39, 10, 4) })
        tapTile(4f, 9f)              // primo compagno
        assertTrue("scheda del Pokémon", shotUntil("15-scheda-pokemon") { boxAt(it, 0, 12, 20, 11) && boxAt(it, 0, 24, 20, 11) && boxAt(it, 5, 39, 10, 4) })
        tapTile(9f, 40.5f)           // INDIETRO → squadra
        assertTrue("di nuovo la squadra", shotUntil("16-squadra-2") { boxAt(it, 10, 39, 10, 4) })
        tapTile(4f, 40.5f)           // INDIETRO → gioco
        pump(800)
        tapTile(10f, rows() - 2f)
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
        tapTile(10f, rows() - 2f)
        pump(800)
        tapTile(15f, 33f)            // OPZIONI
        assertTrue("OPZIONI", shotUntil("18-opzioni") { boxAt(it, 0, 10, 20, 28) && boxAt(it, 5, 39, 11, 4) })
        tapTile(4f, 27f)             // GIOCO: bianco e nero
        assertTrue("opzione bianco e nero", waitFor(3_000) { s.blackWhite })
        tapTile(9f, 40.5f)           // INDIETRO
        shot("18b-bianco-e-nero")
        tapTile(10f, rows() - 2f); pump(800); tapTile(15f, 33f); pump(800)
        tapTile(4f, 27f)             // di nuovo a colori
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
        assertTrue("STRUMENTI PC dell'app", shotUntil("32-pc") { b -> boxAt(b, 0, 3, 20, 3) && boxAt(b, 0, 7, 20, 31) && boxAt(b, 10, 39, 10, 4) })
        tapTile(15f, 41f)                                   // DEPOSITA
        assertTrue("borsa da depositare", shotUntil("33-pc-deposita") { b -> !boxAt(b, 10, 39, 10, 4) })
        tapTile(4f, 9.5f)                                   // POZIONE
        assertTrue("Pozione nel PC", waitFor(30_000) { !s.itemBusy && s.latestSnapshot.get().pcItems.any { it.id == 0x14 } })
        assertTrue("borsa vuota", waitFor(5_000) { s.latestSnapshot.get().bag.isEmpty() })
        shot("34-pc-dopo-deposito")
        tapTile(4f, 40.5f)                                  // INDIETRO → strumenti del PC
        pump(800)
        tapTile(4f, 9.5f)                                   // POZIONE: PRELEVA
        assertTrue("Pozione di nuovo nella borsa", waitFor(30_000) { !s.itemBusy && s.latestSnapshot.get().bag.any { it.id == 0x14 } })
        shot("35-pc-dopo-prelievo")
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
    private fun pos() = s.positionForTest()
    private fun goTo(x: Int, y: Int) {
        val map = pos().first
        s.walkPathForTest(x, y)
        waitFor(40_000) { pos().let { it.first != map || (it.second == x && it.third == y) } || s.latestSnapshot.get().inBattle }
        fightIfNeeded()
    }
    private fun fightIfNeeded() {
        var g = 0
        while (s.latestSnapshot.get().inBattle && g++ < 400) {
            if (s.battleMenuShown && !s.commandBusy) tapTile(4f, it.giallotouch.bridge.BattleLayout.MOVES_TOP_ROW + 2.5f) else tapTile(10f, 2f)
        }
        textThrough()
    }
    private fun textThrough(max: Int = 80) {
        var k = 0
        while (!s.latestSnapshot.get().freeRoam && !s.latestSnapshot.get().inBattle && k++ < max) tapTile(10f, 2f)
        if (s.latestSnapshot.get().inBattle) fightIfNeeded()
    }
    private fun save(name: String) {
        val dir = java.io.File(System.getProperty("giallo.scenario")!!)
        assertTrue(s.saveStateForTest(java.io.File(dir, name)))
        shot("scen-" + name.removeSuffix(".state"))
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
    /** Esce dal bordo nord (o sud) del luogo. */
    private fun edge(north: Boolean): Boolean {
        val map = pos().first
        repeat(6) {
            val g = s.mapGridForTest() ?: return false
            val ys = if (north) (0 until g.height) else (g.height - 1 downTo 0)
            val target = ys.firstNotNullOfOrNull { y -> (0 until g.width).sortedBy { kotlin.math.abs(it - pos().second) }.firstOrNull { g.isFree(it, y) }?.let { it to y } } ?: return false
            goTo(target.first, target.second)
            var k = 0
            while (pos().first == map && k++ < 6) { s.setKeys(if (north) NativeCore.KEY_UP else NativeCore.KEY_DOWN); pump(300); s.setKeys(0); pump(200) }
            if (pos().first != map) { pump(1500); return true }
            fightIfNeeded()
        }
        return false
    }

    @Test fun costruisci() {
        assumeTrue(System.getProperty("giallo.scenario") != null)
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
