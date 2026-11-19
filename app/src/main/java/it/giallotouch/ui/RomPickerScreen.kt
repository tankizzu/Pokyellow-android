package it.giallotouch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp

/**
 * Scelta del file del gioco (D-066, proposta A del committente): titolo, riquadro con la spiegazione, SCEGLI CARTELLA
 * (l'app cerca il file da sola) e SCEGLI FILE, note in basso. Con [tiles] (riquadri e lettere presi dal file del
 * giocatore la prima volta che il gioco è girato) la schermata è identica al gioco; senza (primissima apertura, quando
 * il giocatore annulla il selettore di Android) è disegnata dall'app nello stesso stile.
 * Avvisi ([error], [warning]) nel riquadro al posto della spiegazione; con [onContinueAnyway] compare USA COMUNQUE.
 */
@Composable
fun RomPickerScreen(
    tiles: IntArray?,
    version: String,
    busy: Boolean,
    error: String?,
    warning: String?,
    onPickFolder: () -> Unit,
    onPickFile: () -> Unit,
    onContinueAnyway: (() -> Unit)?,
) {
    val message = when {
        busy -> "Cerco il file del gioco..."
        warning != null -> warning
        error != null -> error
        else -> "Per giocare scegli la cartella con il file del gioco: la tua copia di GIALLO (italiano)."
    }
    val lines = wrapGameText(message, 17).take(7)
    val buttons = buildList {
        if (onContinueAnyway != null) add("USA COMUNQUE" to onContinueAnyway)
        add("SCEGLI CARTELLA" to onPickFolder)
        add("SCEGLI FILE" to onPickFile)
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFF8F8F8))) {
        val density = LocalDensity.current
        val unit = with(density) { maxWidth.toPx() } / 160f
        val tilePx = 8 * unit
        val rows = (with(density) { maxHeight.toPx() } / tilePx).toInt()
        val boxTop = 10
        val boxRows = 2 * lines.size + 3
        val firstButton = boxTop + boxRows + 2
        if (tiles != null) {
            TileImage(tiles, 20, boxTop + boxRows, unit, key = Triple(lines, version, busy)) {
                text("GIALLO TOUCH", 1, 5); text(version.uppercase().take(18), 1, 7)
                box(0, boxTop, 19, boxTop + boxRows - 1)
                lines.forEachIndexed { i, l -> text(l, 1, boxTop + 2 + 2 * i) }
            }
            buttons.forEachIndexed { i, (label, act) ->
                TileImage(tiles, 18, 4, unit, key = "scelta-$label",
                    modifier = Modifier.offset { IntOffset((1 * tilePx).toInt(), ((firstButton + 5 * i) * tilePx).toInt()) }
                        .pointerInput(busy, label) { detectTapGestures { if (!busy) act() } }
                ) { box(0, 0, 17, 3); textAt(label, (18 * 8 - label.length * 8) / 2, 12) }
            }
            val noteRow = firstButton + 5 * buttons.size
            TileImage(tiles, 20, 2, unit, key = "note",
                modifier = Modifier.offset { IntOffset(0, (noteRow * tilePx).toInt()) }) {
                text("Resta nel telefono.", 1, 0)
            }
        } else {
            // primissima apertura: stesso stile, disegnato dall'app
            val ink = Color(0xFF181818)
            @Composable
            fun T(s: String, col: Float, row: Float, size: Float = 1f, color: Color = ink) = Text(
                s, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = with(density) { (7.2f * unit * size).toSp() }, maxLines = 1,
                modifier = Modifier.offset { IntOffset((col * tilePx).toInt(), (row * tilePx).toInt()) })
            @Composable
            fun Frame(col: Int, row: Int, w: Int, h: Int, onTap: (() -> Unit)? = null) = Canvas(
                Modifier.offset { IntOffset((col * tilePx).toInt(), (row * tilePx).toInt()) }
                    .size(with(density) { (w * tilePx).toDp() }, with(density) { (h * tilePx).toDp() })
                    .let { m -> if (onTap != null) m.pointerInput(busy) { detectTapGestures { if (!busy) onTap() } } else m }
            ) {
                val s = unit * 1.5f
                drawRect(ink, Offset(s, s), Size(size.width - 2 * s, size.height - 2 * s), style = Stroke(s))
                drawRect(ink, Offset(4 * s, 4 * s), Size(size.width - 8 * s, size.height - 8 * s), style = Stroke(s * 0.7f))
            }
            T("GIALLO TOUCH", 1f, 5f, 1.2f); T(version.uppercase(), 1f, 7f, 0.9f, Color(0xFF707070))
            Frame(0, boxTop, 20, boxRows)
            lines.forEachIndexed { i, l -> T(l, 1f, boxTop + 1.6f + 2 * i) }
            buttons.forEachIndexed { i, (label, act) ->
                Frame(1, firstButton + 5 * i, 18, 4, act)
                T(label, 1 + (18 - label.length) / 2f, firstButton + 5 * i + 1.4f)
            }
            T("Resta nel telefono.", 1f, firstButton + 5f * buttons.size, 1f, Color(0xFF707070))
        }
        Box(Modifier.offset { IntOffset((1 * tilePx).toInt(), ((rows - 2) * tilePx).toInt()) }) {
            Text("SameBoy © Lior Halphon, MIT", color = Color(0xFF8A8A8A), fontSize = 9.sp)
        }
    }
}
