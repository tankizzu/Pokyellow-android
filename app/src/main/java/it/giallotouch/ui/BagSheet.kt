package it.giallotouch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.giallotouch.bridge.BagItem
import it.giallotouch.bridge.Items
import it.giallotouch.bridge.RomData

/**
 * Menu Borsa nativo, a tutto schermo: un'unica lista nello stesso ordine del gioco, con la quantità
 * (come nel gioco, gli strumenti chiave non la mostrano). Solo lettura: nomi dalla ROM dell'utente.
 */
@Composable
fun BagSheet(bag: List<BagItem>, romData: RomData, onClose: () -> Unit) {
    FullSheet(title = "Borsa", onBack = null, onClose = onClose) {
        if (bag.isEmpty()) {
            Text("La borsa è vuota.", color = Cream, fontSize = 16.sp)
        }
        for (item in bag) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .background(SheetRow, RoundedCornerShape(16.dp))
                    .border(1.dp, SheetLine, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(romData.itemName(item.id), color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (Items.isKeyItem(item.id)) {
                    Text("chiave", color = SheetDim, fontSize = 12.sp)
                } else {
                    Text("×${item.quantity}", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(
            "${bag.size}/20 strumenti, nello stesso ordine del gioco. Per usarli o gettarli usa per ora il menu del gioco " +
                "(Menu → STRUMENTI).",
            color = SheetDim, fontSize = 12.sp
        )
    }
}
