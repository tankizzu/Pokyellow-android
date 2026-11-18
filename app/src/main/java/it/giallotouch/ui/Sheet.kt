package it.giallotouch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val SheetBg = Color(0xFF171624)
internal val SheetRow = Color(0xFF282540)
internal val SheetLine = Color(0xFF403C62)
internal val SheetDim = Color(0xFFAAA4C8)

/**
 * Cornice comune dei menu nativi a tutto schermo (D-012): titolo, "indietro" (se [onBack]) e "chiudi",
 * contenuto a scorrimento verticale col dito (D-008). Il gesto indietro di Android fa lo stesso dei pulsanti.
 */
@Composable
fun FullSheet(
    title: String,
    onBack: (() -> Unit)?,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler { (onBack ?: onClose)() }
    Box(
        Modifier
            .fillMaxSize()
            .background(SheetBg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) SheetIconButton(AppIcons.Back, "Indietro", onBack)
                Text(
                    title, color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = if (onBack != null) 10.dp else 2.dp)
                )
                SheetIconButton(AppIcons.Close, "Chiudi", onClose)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
internal fun SheetIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .background(SheetRow, RoundedCornerShape(14.dp))
            .border(1.dp, SheetLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Cream, modifier = Modifier.size(22.dp))
    }
}
