package it.giallotouch.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icone a tratto dell'app (24×24), le stesse disegnate per il Laboratorio (docs/demo): grafica dell'app,
 * non del gioco. Il colore si sceglie con il tint dell'Icon/Image.
 */
object AppIcons {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 0f) =
        if (r == 0f) "M$x ${y}h${w}v${h}h${-w}z"
        else "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

    private fun icon(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in paths) {
            b.addPath(
                pathData = addPathNodes(p),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val Team = icon(
        "squadra", circle(8f, 8f, 3f), circle(16f, 8f, 3f),
        "M2.5 19c.4-3 2.7-5 5.5-5s5.1 2 5.5 5", "M13 14.6c.9-.4 1.9-.6 3-.6 2.8 0 5.1 2 5.5 5"
    )
    val Bag = icon("borsa", "M6 8h12l1 12H5L6 8z", "M9 8V6a3 3 0 0 1 6 0v2", "M9 13h6")
    val Dex = icon(
        "pokedex", rect(5f, 3f, 14f, 18f, 2f), rect(8f, 6f, 8f, 6f, 1f), circle(9.5f, 16.5f, 1.4f), "M13.5 15.5h2.5M13.5 18h2.5"
    )
    val Card = icon("scheda", rect(4f, 3f, 16f, 18f, 2f), "M8 13a4 4 0 0 1 8 0v1H8z", "M8 17.5h8", "M10 7h4")
    val Save = icon("salva", "M5 4h11l3 3v13H5z", "M8 4v5h7V4", rect(8f, 13f, 8f, 5f))
    val Menu = icon("menu", "M4 6h16", "M4 12h16", "M4 18h16")
    val Text = icon("testo", "M4 5h16v11H9l-5 4z", "M8 9h8", "M8 12h5")
    val Select = icon("select", "M7 4v16", "M3 8l4-4 4 4", "M17 20V4", "M13 16l4 4 4-4")
    val Run = icon("fuga", "M10 4H5v16h5", "M14 8l4 4-4 4", "M18 12H9")
    val Close = icon("chiudi", "M6 6l12 12M18 6L6 18")
    val Back = icon("indietro", "M15 5l-7 7 7 7")
}
