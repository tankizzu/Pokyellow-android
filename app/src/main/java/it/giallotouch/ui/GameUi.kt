package it.giallotouch.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import it.giallotouch.bridge.TileCanvas

/**
 * Immagine composta con i tile del gioco (D-039), alla stessa scala del gioco sullo schermo:
 * [unitPx] = pixel del telefono per pixel del Game Boy (larghezza dello schermo / 160).
 */
@Composable
fun TileImage(
    sheet: IntArray,
    widthTiles: Int,
    heightTiles: Int,
    unitPx: Float,
    key: Any?,
    modifier: Modifier = Modifier,
    draw: TileCanvas.() -> Unit,
) {
    val bitmap: ImageBitmap = remember(sheet, widthTiles, heightTiles, key) {
        val c = TileCanvas(widthTiles, heightTiles, sheet).apply(draw)
        Bitmap.createBitmap(c.pixels, c.width, c.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    val density = LocalDensity.current
    Image(
        bitmap, contentDescription = null,
        modifier = modifier.size(
            with(density) { (widthTiles * 8 * unitPx).toDp() },
            with(density) { (heightTiles * 8 * unitPx).toDp() }
        ),
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None,
    )
}
