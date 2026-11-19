package it.battito.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Foto delle schermate: ./gradlew testDebugUnitTest -Pbattito.shots=/cartella */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    private fun shot(v: View, dir: File, name: String) {
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tap(v: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0))
    }

    @Test
    fun schermate() {
        val dir = File(System.getProperty("battito.shots") ?: return).apply { mkdirs() }
        val v = GameView(RuntimeEnvironment.getApplication(), AudioEngine())
        val w = 2392; val h = 1080
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val tile = h / 10.8f

        shot(v, dir, "1-menu")
        tap(v, w - tile * 1.1f, tile * 1.1f)          // ingranaggio
        shot(v, dir, "2-impostazioni")
        tap(v, w / 2f, tile * 3.6f + 3 * tile * 1.7f + tile * 0.4f)   // indietro
        tap(v, w / 2f, h / 2f)                         // gioca
        // scorci di livello: (colonna, altezza, colore, navicella, specchio, gravità dei portali, tasto gravità)
        data class P(val x: Float, val y: Float, val ph: Int, val ship: Boolean = false, val mir: Boolean = false, val g: Int = 1, val gh: Boolean = false)
        val punti = listOf(
            "gravita" to P(268f, 2.1f, 0, gh = true),
            "portale-nave" to P(377f, 0f, 0),
            "navicella" to P(408f, 5.2f, 0, ship = true),
            "navicella-muri" to P(436f, 3f, 1, ship = true),
            "portali-cubo-specchio" to P(494f, 0f, 0),
            "specchio" to P(536f, 0f, 0, mir = true),
            "sottosopra" to P(680f, 6.1f, 0, g = -1),
        )
        for ((nome, p) in punti) {
            v.debugSim.debugPlace(p.x, p.y, p.ph, p.ship, p.mir, p.g)
            v.debugSim.setGravityHeld(p.gh)
            v.debugSnapMirror()
            shot(v, dir, "3-$nome")
        }
        v.debugSim.setGravityHeld(false)
        tap(v, w - tile * 1.1f, tile * 1.1f)          // pausa
        shot(v, dir, "4-pausa")
    }
}
