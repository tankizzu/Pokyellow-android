package it.giallotouch.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import it.giallotouch.bridge.BattleLayout
import it.giallotouch.bridge.StartMenuLayout
import it.giallotouch.bridge.TileCanvas
import it.giallotouch.emu.GameSession
import it.giallotouch.emu.NativeCore
import kotlin.math.roundToInt

/**
 * Disegna il gioco a tutta larghezza con pixel nitidi, senza deformarlo. È disegnato da Compose (non è una View
 * a parte): così i pulsanti dell'app stanno sempre sopra l'immagine del gioco, anche mentre si aggiorna a ogni fotogramma.
 * - In esplorazione usa la vista estesa: la mappa riempie tutto lo schermo (sopra e sotto l'inquadratura
 *   originale), disegnata dal motore con i tile e i colori del gioco.
 * - In lotta (D-018) lo sfondo della lotta riempie lo schermo: la scena (96 righe) sta in alto e il riquadro
 *   del testo (48 righe) subito sopra i pulsanti nativi, alla stessa scala e senza deformazioni.
 * - Nelle altre schermate (menu a tutto schermo, titolo) mostra l'immagine originale 10:9 al centro.
 */
class GameRenderer(private val session: GameSession) {
    private val frameBitmap = Bitmap.createBitmap(NativeCore.WIDTH, NativeCore.HEIGHT, Bitmap.Config.ARGB_8888)
    private var extBitmap: Bitmap? = null
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val dst = Rect()
    private val bandPaint = Paint()

    /** Colore più frequente di una riga dell'immagine (campione ogni 4 pixel). */
    private fun edgeColor(frame: IntArray, row: Int): Int {
        var best = Color.BLACK; var bestN = 0
        for (i in 0 until NativeCore.WIDTH step 4) {
            val c = frame[row * NativeCore.WIDTH + i]
            var n = 0
            for (j in 0 until NativeCore.WIDTH step 4) if (frame[row * NativeCore.WIDTH + j] == c) n++
            if (n > bestN) { bestN = n; best = c }
        }
        return best
    }
    /**
     * Bande nere del gioco sopra e sotto (scene dell'introduzione): (prima riga della scena, prima riga della banda
     * sotto, colore dello sfondo della scena) oppure null. Le bande devono essere di almeno 8 righe, nere e uniformi.
     */
    private fun letterbox(frame: IntArray): Triple<Int, Int, Int>? {
        val black = frame[0]
        // riga della banda: quasi tutta nera (la stella cadente di GAME FREAK può attraversarla)
        fun band(row: Int): Boolean {
            var n = 0
            for (x in 0 until NativeCore.WIDTH) if (frame[row * NativeCore.WIDTH + x] == black) n++
            return n * 4 >= NativeCore.WIDTH * 3
        }
        if ((Color.red(black) + Color.green(black) + Color.blue(black)) > 120 || !band(0) || !band(NativeCore.HEIGHT - 1)) return null
        var r0 = 0
        while (r0 < NativeCore.HEIGHT && band(r0)) r0++
        var r1 = NativeCore.HEIGHT
        while (r1 > r0 && band(r1 - 1)) r1--
        if (r0 < 8 || NativeCore.HEIGHT - r1 < 8 || r1 - r0 < 16) return null
        val bg = edgeColor(frame, r0)
        if (bg == black || edgeColor(frame, r1 - 1) != bg) return null
        return Triple(r0, r1, bg)
    }
    private var bandBuf = IntArray(NativeCore.WIDTH * NativeCore.HEIGHT)

    private val src = Rect()
    /** Spazio in basso occupato dai pulsanti di lotta (pixel dello schermo), impostato dall'interfaccia. */
    var battleReservedBottomPx = 0
    private var width = 0
    private var height = 0
    private var density = 1f

    /** Dimensioni dell'area di disegno (pixel dello schermo): aggiorna l'altezza della vista estesa chiesta al motore. */
    fun setSize(w: Int, h: Int, density: Float) {
        this.density = density
        if (w == width && h == height) return
        width = w; height = h
        updateExtendedHeight()
    }

    /** Altezza della vista estesa in pixel del gioco: quanto serve per coprire lo schermo con larghezza 160. */
    private fun updateExtendedHeight() {
        val s = session
        if (width <= 0 || height <= 0) return
        val h = (NativeCore.WIDTH.toFloat() * height / width).roundToInt().coerceIn(NativeCore.HEIGHT, NativeCore.EXT_MAX_HEIGHT)
        s.extendedHeight = h
        if (extBitmap?.height != h) extBitmap = Bitmap.createBitmap(NativeCore.WIDTH, h, Bitmap.Config.ARGB_8888)
    }

    fun draw(canvas: Canvas) {
        val s = session
        canvas.drawColor(Color.BLACK)
        val ext = s.latestExtended.get()
        val eb = extBitmap
        val w = width
        if (ext.active && eb != null && ext.height == eb.height) {
            eb.setPixels(gray(s, ext.pixels, NativeCore.WIDTH * ext.height), 0, NativeCore.WIDTH, 0, 0, NativeCore.WIDTH, ext.height)
            val h = (w.toLong() * ext.height / NativeCore.WIDTH).toInt()
            val top = (height - h) / 2   // la mappa estesa copre lo schermo; l'inquadratura dentro è già spostata dal motore
            dst.set(0, top, w, top + h)
            canvas.drawBitmap(eb, null, dst, paint)
            drawDialog(canvas, s, w)
            return
        }
        val frame = s.latestFrame.get()
        frameBitmap.setPixels(gray(s, frame, NativeCore.WIDTH * NativeCore.HEIGHT), 0, NativeCore.WIDTH, 0, 0, NativeCore.WIDTH, NativeCore.HEIGHT)
        if (s.namingShown) {
            // Schermata dei nomi del gioco: mai visibile, nemmeno per un attimo prima della schermata dell'app (D-046).
            val sheet = s.tileSheet.get()
            canvas.drawColor(if (sheet != null) sheet[(0x7F shr 4) * 8 * 128 + (0x7F and 15) * 8] else frame[0])
            return
        }
        if (s.trainerCardShown) {
            // Scheda allenatore del gioco ridisposta (D-045): dati alla riga 6, medaglie alla riga 16 (D-046), sfondo della scheda.
            val row = w / 20
            canvas.drawColor(frame[70 * NativeCore.WIDTH])
            src.set(0, 0, NativeCore.WIDTH, 64)
            dst.set(0, 6 * row, w, 6 * row + 8 * row)
            canvas.drawBitmap(frameBitmap, src, dst, paint)
            src.set(0, 64, NativeCore.WIDTH, NativeCore.HEIGHT)
            dst.set(0, 16 * row, w, 16 * row + 10 * row)
            canvas.drawBitmap(frameBitmap, src, dst, paint)
            return
        }
        if (s.battleSplit) {
            canvas.drawColor(BattleLayout.backgroundColor(frame))
            val sceneH = w * BattleLayout.SCENE_ROWS / NativeCore.WIDTH
            val textH = w * BattleLayout.TEXT_ROWS / NativeCore.WIDTH
            // Disposizione scelta dal committente (D-045): riquadro del testo alla riga 4, scena alla riga 10
            // (righe da 8 pixel del gioco alla scala dello schermo).
            val row = w / 20
            val textTop = BattleLayout.TEXT_TOP_ROW * row
            val sceneTop = BattleLayout.SCENE_TOP_ROW * row
            src.set(0, 0, NativeCore.WIDTH, BattleLayout.SCENE_ROWS)
            dst.set(0, sceneTop, w, sceneTop + sceneH)
            canvas.drawBitmap(frameBitmap, src, dst, paint)
            src.set(0, BattleLayout.SCENE_ROWS, NativeCore.WIDTH, NativeCore.HEIGHT)
            dst.set(0, textTop, w, textTop + textH)
            val empty = if (s.battleMenuShown) emptyTextBox(s) else null
            canvas.drawBitmap(empty ?: frameBitmap, if (empty != null) null else src, dst, paint)
            return
        }
        val h = (w * NativeCore.HEIGHT) / NativeCore.WIDTH
        val top = (height - h) / 2
        // Spazio sopra e sotto: il colore del bordo dell'immagine (bianco su titolo e menu), così sembra a tutto schermo.
        // Scene "a striscia" dell'introduzione (bande nere del gioco sopra e sotto, D-067): le bande prendono il colore
        // dello sfondo della scena, così anche queste sono a tutto schermo.
        val lb = letterbox(frame)
        if (lb != null) {
            // bande nere della scena col colore dello sfondo; quello che le attraversa (la stella) resta
            System.arraycopy(frame, 0, bandBuf, 0, bandBuf.size)
            val black = frame[0]
            for (y in 0 until NativeCore.HEIGHT) if (y < lb.first || y >= lb.second)
                for (x in 0 until NativeCore.WIDTH) { val i = y * NativeCore.WIDTH + x; if (bandBuf[i] == black) bandBuf[i] = lb.third }
            frameBitmap.setPixels(gray(s, bandBuf, bandBuf.size), 0, NativeCore.WIDTH, 0, 0, NativeCore.WIDTH, NativeCore.HEIGHT)
        }
        bandPaint.color = if (lb != null) lb.third else edgeColor(frame, 0)
        canvas.drawRect(0f, 0f, w.toFloat(), top.toFloat(), bandPaint)
        bandPaint.color = if (lb != null) lb.third else edgeColor(frame, NativeCore.HEIGHT - 1)
        canvas.drawRect(0f, (top + h).toFloat(), w.toFloat(), height.toFloat(), bandPaint)
        dst.set(0, top, w, top + h)
        canvas.drawBitmap(frameBitmap, null, dst, paint)
        drawStartMenu(canvas, s, top, w)
        drawDialog(canvas, s, w)
    }

    /** Dialogo del gioco più in alto, alla riga [DIALOG_ROW] dello schermo (D-048). */
    private val dialogBitmap = Bitmap.createBitmap(NativeCore.WIDTH, 48, Bitmap.Config.ARGB_8888)
    private fun drawDialog(canvas: Canvas, s: GameSession, w: Int) {
        val strip = s.dialogStrip ?: return
        dialogBitmap.setPixels(strip, 0, NativeCore.WIDTH, 0, 0, NativeCore.WIDTH, 48)
        val row = w / 20f
        dst.set(0, (DIALOG_ROW * row).toInt(), w, (DIALOG_ROW * row + 6 * row).toInt())
        canvas.drawBitmap(dialogBitmap, null, dst, paint)
    }

    companion object {
        /** Riga dello schermo (caselle da 8 pixel del gioco) del riquadro del dialogo, scelta dal committente. */
        const val DIALOG_ROW = 9
    }

    /** Riquadro del testo vuoto (20×6 caselle) al posto del menu di lotta del gioco: le azioni sono nei riquadri dell'app. */
    private var emptyBox: Pair<IntArray, Bitmap>? = null
    private fun emptyTextBox(s: GameSession): Bitmap? {
        val sheet = s.tileSheet.get() ?: return null
        emptyBox?.let { if (it.first === sheet) return it.second }
        val c = TileCanvas(20, 6, sheet)
        c.box(0, 0, 19, 5)
        val b = Bitmap.createBitmap(c.pixels, c.width, c.height, Bitmap.Config.ARGB_8888)
        emptyBox = sheet to b
        return b
    }

    /** Gioco in bianco e nero (D-054): copia dell'immagine in 4 grigi; a colori l'immagine resta com'è. */
    private var grayBuf = IntArray(0)
    private fun gray(s: GameSession, px: IntArray, n: Int): IntArray {
        if (!s.blackWhite) return px
        if (grayBuf.size < n) grayBuf = IntArray(n)
        for (i in 0 until n) {
            val c = px[i]
            val l = (((c shr 16) and 255) * 30 + ((c shr 8) and 255) * 59 + (c and 255) * 11) / 100
            val g = when { l >= 190 -> 0xF8; l >= 120 -> 0xA8; l >= 60 -> 0x58; else -> 0x18 }
            grayBuf[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        return grayBuf
    }

    /** Menu iniziale ridisegnato con i tile del gioco (D-040), nello stesso fotogramma: copre il menu originale. */
    private var menuBitmap: Bitmap? = null
    private var menuKey: Triple<IntArray, Int, Int>? = null
    private fun drawStartMenu(canvas: Canvas, s: GameSession, frameTop: Int, w: Int) {
        val items = s.mainMenuItems
        val sheet = s.tileSheet.get() ?: return
        if (items <= 0) return
        val cursor = s.startMenuCursor
        if (menuKey?.first !== sheet || menuKey?.second != items || menuKey?.third != cursor) {
            val c = TileCanvas(StartMenuLayout.WIDTH, StartMenuLayout.height(items), sheet)
            StartMenuLayout.draw(c, items, cursor)
            menuBitmap = Bitmap.createBitmap(c.pixels, c.width, c.height, Bitmap.Config.ARGB_8888)
            menuKey = Triple(sheet, items, cursor)
        }
        val b = menuBitmap ?: return
        val unit = w.toFloat() / NativeCore.WIDTH
        dst.set(0, frameTop, (b.width * unit).toInt(), frameTop + (b.height * unit).toInt())
        canvas.drawBitmap(b, null, dst, paint)
    }
}
