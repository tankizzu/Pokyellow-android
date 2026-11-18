package it.giallotouch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.giallotouch.bridge.RomData

/**
 * Pokédex nativo, a tutto schermo: visti e presi, poi l'elenco fino al numero più alto visto, come nel gioco.
 * Le specie non ancora viste restano senza nome; quelle prese hanno il segno pieno. Solo lettura.
 */
@Composable
fun PokedexSheet(seen: List<Int>, owned: List<Int>, romData: RomData, onClose: () -> Unit) {
    FullSheet(title = "Pokédex", onBack = null, onClose = onClose) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountBox("Visti", seen.size, Modifier.weight(1f))
            CountBox("Presi", owned.size, Modifier.weight(1f))
        }
        val last = seen.maxOrNull() ?: 0
        if (last == 0) Text("Non hai ancora visto nessun POKéMON.", color = Cream, fontSize = 16.sp)
        val seenSet = seen.toHashSet()
        val ownedSet = owned.toHashSet()
        for (n in 1..last) {
            val isSeen = n in seenSet
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(SheetRow, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("%03d".format(n), color = SheetDim, fontSize = 13.sp, modifier = Modifier.width(36.dp))
                Text(
                    if (isSeen) romData.speciesNameForDex(n).ifEmpty { "?" } else "----------",
                    color = if (isSeen) Cream else SheetDim, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .size(14.dp)
                        .background(if (n in ownedSet) Yellow else Color.Transparent, CircleShape)
                        .then(if (n in ownedSet || !isSeen) Modifier else Modifier.background(SheetLine, CircleShape))
                )
            }
        }
    }
}

@Composable
private fun CountBox(label: String, value: Int, modifier: Modifier) {
    Row(
        modifier.background(SheetRow, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = SheetDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("$value", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
