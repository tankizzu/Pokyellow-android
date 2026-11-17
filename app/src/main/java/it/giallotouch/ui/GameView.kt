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

/**
 * Disegna l'immagine del gioco a tutta larghezza, senza deformarla (rapporto 10:9)
 * e con pixel nitidi. Per ora l'immagine è quella originale 160×144, centrata in verticale:
 * la "vista estesa" a tutto schermo arriverà quando la mappa della memoria sarà completa.
 */
class GameView(context: Context) : View(context) {
    private val bitmap = Bitmap.createBitmap(NativeCore.WIDTH, NativeCore.HEIGHT, Bitmap.Config.ARGB_8888)
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
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val s = session ?: return
        val frame = s.latestFrame.get()
        bitmap.setPixels(frame, 0, NativeCore.WIDTH, 0, 0, NativeCore.WIDTH, NativeCore.HEIGHT)
        val w = width
        val h = (w * NativeCore.HEIGHT) / NativeCore.WIDTH
        val top = (height - h) / 2
        dst.set(0, top, w, top + h)
        canvas.drawBitmap(bitmap, null, dst, paint)
    }
}
