package it.giallotouch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.clickable
import it.giallotouch.emu.GameSession
import it.giallotouch.emu.NativeCore
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot

private val Ink = Color(0xFF171624)
private val Glass = Color(0xE0282540)
private val Yellow = Color(0xFFF6C933)
private val Cream = Color(0xFFFFF7DC)

/**
 * Schermo di gioco del prototipo:
 * - levetta che compare dove appoggi il dito (4 direzioni, come il Game Boy);
 * - tocco veloce senza trascinare = tasto A (parla, esamina, fa avanzare i dialoghi);
 * - gesto "indietro" di Android = tasto B;
 * - in basso: Menu (START), Select, Salva.
 */
@Composable
fun GameScreen(session: GameSession) {
    var held by remember { mutableStateOf(0) }       // direzione tenuta dalla levetta
    var tapped by remember { mutableStateOf(0) }     // tasti premuti brevemente (A, B, START, SELECT)
    var joyOrigin by remember { mutableStateOf<Offset?>(null) }
    var joyKnob by remember { mutableStateOf(Offset.Zero) }
    var message by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val radius = with(density) { 58.dp.toPx() }

    LaunchedEffect(held, tapped) { session.setKeys(held or tapped) }
    LaunchedEffect(tapped) {
        if (tapped != 0) { delay(110); tapped = 0 }
    }
    LaunchedEffect(message) {
        if (message != null) { delay(2000); message = null }
    }
    LaunchedEffect(Unit) {
        if (session.resumedFromState) message = "Partita ripresa da dove l'avevi lasciata"
    }

    BackHandler { tapped = NativeCore.KEY_B }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx -> GameView(ctx).also { it.attach(session) } },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.attach(null) }
        )

        // Livello dei tocchi: levetta e tasto A.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        val t0 = down.uptimeMillis
                        var moved = false
                        joyOrigin = start
                        joyKnob = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (!moved && change.uptimeMillis - t0 < 350) tapped = NativeCore.KEY_A
                                break
                            }
                            var d = change.position - start
                            val dist = hypot(d.x, d.y)
                            if (dist > 24f) moved = true
                            if (dist > radius) d = d * (radius / dist)
                            joyKnob = d
                            held = when {
                                hypot(d.x, d.y) < radius * 0.28f -> 0
                                abs(d.x) > abs(d.y) -> if (d.x > 0) NativeCore.KEY_RIGHT else NativeCore.KEY_LEFT
                                else -> if (d.y > 0) NativeCore.KEY_DOWN else NativeCore.KEY_UP
                            }
                            change.consume()
                        }
                        held = 0
                        joyOrigin = null
                    }
                }
        ) {
            val origin = joyOrigin
            if (origin != null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color(0x6B171624), radius, origin)
                    drawCircle(Color(0x99FFF7DC), radius, origin, style = Stroke(width = 2.dp.toPx()))
                    drawCircle(Yellow, radius * 0.46f, origin + joyKnob)
                }
            }
        }

        // Barra in basso (prototipo): i menu nativi arriveranno con il "ponte" verso il gioco.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BarButton("Menu", Modifier.weight(1f)) { tapped = NativeCore.KEY_START }
            BarButton("Select", Modifier.weight(1f)) { tapped = NativeCore.KEY_SELECT }
            BarButton("Salva", Modifier.weight(1f)) {
                message = if (session.saveNow()) "Partita salvata in questo punto" else "Salvataggio non riuscito"
            }
        }

        message?.let {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 48.dp)
                    .background(Cream, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(it, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun BarButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(56.dp)
            .background(Glass, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF403C62), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Cream, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
