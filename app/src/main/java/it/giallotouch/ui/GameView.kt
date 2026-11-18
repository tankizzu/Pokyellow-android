package it.giallotouch.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import it.giallotouch.emu.GameSession
import it.giallotouch.emu.NativeCore
import kotlin.math.roundToInt

/**
 * Disegna il gioco a tutta larghezza con pixel nitidi, senza deformarlo.
 * - In esplorazione usa la vista estesa: la mappa riempie tutto lo schermo (sopra e sotto l'inquadratura
 *   originale), disegnata dal motore con i tile e i colori del gioco.
 * - Nelle altre schermate (lotta, menu a tutto schermo, titolo) mostra l'immagine originale 10:9 al centro.
 */
class GameView(context: Context) : View(context) {
    private val frameBitmap = Bitmap.createBitmap(NativeCore.WIDTH, NativeCore.HEIGHT, Bitmap.Config.ARGB_8888)
    private var extBitmap: Bitmap? = null
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val dst = Rect()
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
        val h = (w * NativeCore.HEIGHT) / NativeCore.WIDTH
        val top = (height - h) / 2
        dst.set(0, top, w, top + h)
        canvas.drawBitmap(frameBitmap, null, dst, paint)
    }
}
