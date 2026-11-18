package it.giallotouch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.giallotouch.bridge.NameCodec
import it.giallotouch.bridge.NamingScreen

private val NameInk = Color(0xFF171624)
private val NamePanel = Color(0xFF221F38)
private val NameYellow = Color(0xFFF6C933)
private val NameCream = Color(0xFFFFF7DC)
private val NameMuted = Color(0xFFAAA4C8)

/**
 * Tastiera del telefono al posto di quella del gioco (D-029).
 * Copre la schermata dei nomi del gioco; il nome scritto viene poi inserito nel gioco dall'esecutore,
 * che muove il cursore sulla griglia originale. Solo i caratteri disponibili nel gioco sono accettati.
 */
@Composable
fun NameEntryOverlay(screen: NamingScreen, busy: Boolean, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(NameCodec.decode(screen.name)) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val canConfirm = text.isNotEmpty() && !busy
    fun confirm() {
        if (!canConfirm) return
        keyboard?.hide()
        onConfirm(text)
    }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    // Il gesto indietro non deve arrivare al gioco (lì cancellerebbe una lettera).
    BackHandler { }

    Box(
        Modifier
            .fillMaxSize()
            .background(NameInk)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focus.requestFocus(); keyboard?.show() }
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (screen.maxLength == 10) "Soprannome · il gioco chiede" else "Il gioco chiede", color = NameMuted, fontSize = 14.sp)
            Text(screen.title.ifEmpty { "Nome" }, color = NameCream, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(NameCream, RoundedCornerShape(16.dp))
                    .border(2.dp, NameYellow, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { if (!busy) text = NameCodec.sanitize(it, screen.maxLength) },
                    enabled = !busy,
                    singleLine = true,
                    textStyle = TextStyle(color = NameInk, fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(NameInk),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lettere senza accenti, spazio e  ${NameCodec.SYMBOLS}",
                    color = NameMuted, fontSize = 12.sp, modifier = Modifier.weight(1f)
                )
                Text("${text.length}/${screen.maxLength}", color = NameCream, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(if (canConfirm) NameYellow else NamePanel, RoundedCornerShape(16.dp))
                    .clickable(enabled = canConfirm) { confirm() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (busy) "Scrivo il nome nel gioco…" else "Conferma",
                    color = if (canConfirm) NameInk else NameMuted, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
