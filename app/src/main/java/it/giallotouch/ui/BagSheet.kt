package it.giallotouch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.giallotouch.bridge.BagItem
import it.giallotouch.bridge.Items
import it.giallotouch.bridge.RomData

/**
 * Menu Borsa nativo, a tutto schermo: un'unica lista nello stesso ordine del gioco, con la quantità
 * (come nel gioco, gli strumenti chiave non la mostrano). Toccando uno strumento compaiono "Usa" e, se non è
 * uno strumento chiave, "Getta" con la quantità: l'app li esegue passando dal menu originale del gioco.
 */
@Composable
fun BagSheet(
    bag: List<BagItem>,
    romData: RomData,
    onUse: (slot: Int, item: BagItem) -> Unit,
    onToss: (slot: Int, item: BagItem, quantity: Int) -> Unit,
    onClose: () -> Unit,
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    var quantity by remember { mutableStateOf(1) }
    FullSheet(title = "Borsa", onBack = null, onClose = onClose) {
        if (bag.isEmpty()) {
            Text("La borsa è vuota.", color = Cream, fontSize = 16.sp)
        }
        bag.forEachIndexed { slot, item ->
            val key = Items.isKeyItem(item.id)
            val isSelected = selected == slot
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .background(if (isSelected) Color(0xFF353058) else SheetRow, RoundedCornerShape(16.dp))
                    .border(1.dp, if (isSelected) Yellow else SheetLine, RoundedCornerShape(16.dp))
                    .clickable { selected = if (isSelected) null else slot; quantity = 1 }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(romData.itemName(item.id), color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (key) Text("chiave", color = SheetDim, fontSize = 12.sp)
                else Text("×${item.quantity}", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (isSelected) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("Usa", primary = true, modifier = Modifier.weight(1f)) { onUse(slot, item) }
                        if (!key) ActionButton("Getta ×$quantity", primary = false, modifier = Modifier.weight(1f)) { onToss(slot, item, quantity) }
                    }
                    if (!key && item.quantity > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Quanti da gettare", color = SheetDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            StepButton("−") { if (quantity > 1) quantity-- }
                            Text("$quantity", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                            StepButton("+") { if (quantity < item.quantity) quantity++ }
                        }
                    }
                }
            }
        }
        Text(
            "${bag.size}/20 strumenti, nello stesso ordine del gioco. «Usa» e «Getta» passano dal menu del gioco: " +
                "se il gioco chiede qualcosa (per esempio su chi usarlo), rispondi sullo schermo di gioco.",
            color = SheetDim, fontSize = 12.sp
        )
    }
}

@Composable
private fun ActionButton(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(50.dp)
            .background(if (primary) Yellow else SheetRow, RoundedCornerShape(14.dp))
            .border(1.dp, if (primary) Yellow else SheetLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (primary) Ink else Cream, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .background(SheetRow, RoundedCornerShape(12.dp))
            .border(1.dp, SheetLine, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
