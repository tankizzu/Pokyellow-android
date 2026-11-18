package it.giallotouch.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import it.giallotouch.bridge.BattleLayout
import it.giallotouch.emu.GameSession
import it.giallotouch.emu.NativeCore
import kotlin.math.roundToInt

/**
 * Disegna il gioco a tutta larghezza con pixel nitidi, senza deformarlo.
 * - In esplorazione usa la vista estesa: la mappa riempie tutto lo schermo (sopra e sotto l'inquadratura
 *   originale), disegnata dal motore con i tile e i colori del gioco.
 * - In lotta (D-018) lo sfondo della lotta riempie lo schermo: la scena (96 righe) sta in alto e il riquadro
 *   del testo (48 righe) subito sopra i pulsanti nativi, alla stessa scala e senza deformazioni.
 * - Nelle altre schermate (menu a tutto schermo, titolo) mostra l'immagine originale 10:9 al centro.
 */
class GameView(context: Context) : View(context) {
    private val frameBitmap = Bitmap.createBitmap(NativeCore.WIDTH, NativeCore.HEIGHT, Bitmap.Config.ARGB_8888)
    private var extBitmap: Bitmap? = null
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val dst = Rect()
    private val src = Rect()
    /** Spazio in basso occupato dai pulsanti di lotta (pixel dello schermo), impostato dall'interfaccia. */
    var battleReservedBottomPx = 0
    private var session: GameSession? = null

    init {
        setBackgroundColor(Color.BLACK)
    }

    fun attach(s: GameSession?) {
        session?.onFrame = null
        session = s
        s?.onFrame = { postInvalidateOnAnimation() }
        updateExtendedHeight()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateExtendedHeight()
    }

    /** Altezza della vista estesa in pixel del gioco: quanto serve per coprire lo schermo con larghezza 160. */
    private fun updateExtendedHeight() {
        val s = session ?: return
        if (width <= 0 || height <= 0) return
        val h = (NativeCore.WIDTH.toFloat() * height / width).roundToInt().coerceIn(NativeCore.HEIGHT, NativeCore.EXT_MAX_HEIGHT)
        s.extendedHeight = h
        if (extBitmap?.height != h) extBitmap = Bitmap.createBitmap(NativeCore.WIDTH, h, Bitmap.Config.ARGB_8888)
    }

    override fun onDraw(canvas: Canvas) {
        val s = session ?: return
        val ext = s.latestExtended.get()
        val eb = extBitmap
        val w = width
        if (ext.active && eb != null && ext.height == eb.height) {
            eb.setPixels(ext.pixels, 0, NativeCore.WIDTH, 0, 0, NativeCore.WIDTH, ext.height)
            val h = (w.toLong() * ext.height / NativeCore.WIDTH).toInt()
            val top = (height - h) / 2
            dst.set(0, top, w, top + h)
            canvas.drawBitmap(eb, null, dst, paint)
            return
        }
        val frame = s.latestFrame.get()
        frameBitmap.setPixels(frame, 0, NativeCore.WIDTH, 0, 0, NativeCore.WIDTH, NativeCore.HEIGHT)
        if (s.battleSplit) {
            canvas.drawColor(BattleLayout.backgroundColor(frame))
            val sceneH = w * BattleLayout.SCENE_ROWS / NativeCore.WIDTH
            val textH = w * BattleLayout.TEXT_ROWS / NativeCore.WIDTH
            val gap = (8 * resources.displayMetrics.density).toInt()
            val textTop = (height - battleReservedBottomPx - gap - textH).coerceAtLeast(sceneH)
            val sceneTop = ((textTop - sceneH) / 2).coerceAtLeast(0)
            src.set(0, 0, NativeCore.WIDTH, BattleLayout.SCENE_ROWS)
            dst.set(0, sceneTop, w, sceneTop + sceneH)
            canvas.drawBitmap(frameBitmap, src, dst, paint)
            src.set(0, BattleLayout.SCENE_ROWS, NativeCore.WIDTH, NativeCore.HEIGHT)
            dst.set(0, textTop, w, textTop + textH)
            canvas.drawBitmap(frameBitmap, src, dst, paint)
            return
        }
        val h = (w * NativeCore.HEIGHT) / NativeCore.WIDTH
        val top = (height - h) / 2
        dst.set(0, top, w, top + h)
        canvas.drawBitmap(frameBitmap, null, dst, paint)
    }
}
