package it.giallotouch.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.giallotouch.storage.SlotEntry
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val LoadInk = Color(0xFF171624)
private val LoadCard = Color(0xFF282540)
private val LoadYellow = Color(0xFFF6C933)
private val LoadCream = Color(0xFFFFF7DC)
private val LoadMuted = Color(0xFFAAA4C8)

/**
 * "Carica partita" (D-034): elenco dei salvataggi dell'app con miniatura, data, nome, squadra e soldi.
 * Toccare un salvataggio lo carica; "Indietro" torna al menu iniziale del gioco.
 */
@Composable
fun LoadScreen(entries: List<SlotEntry>, onLoad: (SlotEntry) -> Unit, onClose: () -> Unit) {
    BackHandler { onClose() }
    Box(
        Modifier
            .fillMaxSize()
            .background(LoadInk)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 22.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Carica partita", color = LoadCream, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "Salvataggi fatti con l'app. Il salvataggio del gioco si riprende con «CONTINUA» nel menu del gioco.",
                color = LoadMuted, fontSize = 13.sp
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (entries.isEmpty()) {
                    Text(
                        "Nessuna partita salvata con l'app. Durante il gioco usa «Salva» in basso: " +
                            "ritroverai qui quel punto. Anche uscendo dall'app viene salvato un punto automatico.",
                        color = LoadCream, fontSize = 15.sp
                    )
                }
                for (entry in entries) SlotCard(entry) { onLoad(entry) }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(LoadCard, RoundedCornerShape(16.dp))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text("Indietro", color = LoadCream, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SlotCard(entry: SlotEntry, onClick: () -> Unit) {
    val thumb = remember(entry) {
        entry.thumbnail?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
    }
    val info = entry.info
    Row(
        Modifier
            .fillMaxWidth()
            .background(LoadCard, RoundedCornerShape(18.dp))
            .border(1.dp, Color(0xFF403C62), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(112.dp).height(101.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (thumb != null) {
                Image(
                    thumb, contentDescription = null, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit, filterQuality = FilterQuality.None
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.slot.label, color = LoadYellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.size(6.dp))
                Text(entry.slot.detail, color = LoadMuted, fontSize = 12.sp)
            }
            Text(formatDate(info.savedAt), color = LoadCream, fontSize = 13.sp)
            if (info.playerName.isNotEmpty()) {
                Text(
                    info.playerName + if (info.badges > 0) " · medaglie ${info.badges}" else "",
                    color = LoadCream, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
            }
            if (info.party.isNotEmpty()) {
                Text(info.party.joinToString(", ") { "${it.first} L.${it.second}" }, color = LoadMuted, fontSize = 12.sp, maxLines = 2)
            }
            if (info.playerName.isNotEmpty()) Text("Soldi: ${info.money}", color = LoadMuted, fontSize = 12.sp)
        }
    }
}

private fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.ITALY).format(Date(millis))
