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
        // scorci di livello in vari punti
        for ((i, pos) in listOf(Triple(170f, 0f, 1), Triple(232f, 2f, 0), Triple(410f, 3f, 0), Triple(515f, 0f, 0)).withIndex()) {
            v.debugSim.debugPlace(pos.first, pos.second, pos.third)
            v.debugUpdate(0f)
            shot(v, dir, "3-livello-${i + 1}")
        }
        tap(v, w - tile * 1.1f, tile * 1.1f)          // pausa
        shot(v, dir, "4-pausa")
    }
}
