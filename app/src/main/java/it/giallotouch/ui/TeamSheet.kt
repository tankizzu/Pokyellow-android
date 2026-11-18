package it.giallotouch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import it.giallotouch.bridge.PartyMember
import it.giallotouch.bridge.RomData
import it.giallotouch.bridge.Status

private val SheetBg = Color(0xFF171624)
private val Row1 = Color(0xFF282540)
private val Line = Color(0xFF403C62)
private val Dim = Color(0xFFAAA4C8)

/**
 * Menu Squadra nativo, a tutto schermo (D-007, D-008, D-012): elenco dei compagni con livello, stato e PS;
 * toccandone uno si apre la sua scheda (tipi, statistiche, mosse con tipo e PP). Solo lettura: tutti i dati
 * vengono dalla memoria del gioco e dalla ROM dell'utente. [selected] = null mostra l'elenco.
 */
@Composable
fun TeamSheet(
    party: List<PartyMember>,
    romData: RomData,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { if (selected != null) onSelect(null) else onClose() }
    val member = selected?.let { party.getOrNull(it) }
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
                if (member != null) SheetIconButton(AppIcons.Back, "Indietro") { onSelect(null) }
                Text(
                    if (member != null) "Scheda" else "Squadra",
                    color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = if (member != null) 10.dp else 2.dp)
                )
                SheetIconButton(AppIcons.Close, "Chiudi", onClose)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    party.isEmpty() -> Text("Non hai ancora compagni di squadra.", color = Cream, fontSize = 16.sp)
                    member != null -> MemberCard(party, selected, member, romData, onSelect)
                    else -> {
                        party.forEachIndexed { i, m -> MemberRow(m, romData) { onSelect(i) } }
                        Text(
                            "Tocca un compagno per vedere la sua scheda. Per cambiare l'ordine o usare uno strumento " +
                                "usa per ora il menu del gioco (Menu → POKéMON).",
                            color = Dim, fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .background(Row1, RoundedCornerShape(14.dp))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Cream, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun MemberRow(m: PartyMember, romData: RomData, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .background(Row1, RoundedCornerShape(16.dp))
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                val species = romData.speciesName(m.species)
                if (species.isNotEmpty() && species != m.name) {
                    Text("  $species", color = Dim, fontSize = 12.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("L. ${m.level}", color = Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                StatusChip(m.status)
            }
            HpBar(m.hp, m.maxHp)
        }
        Text("${m.hp}/${m.maxHp}", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusChip(status: Status) {
    if (status == Status.NONE) return
    Text(
        status.label, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(Color(0xFFEF8B8B), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp)
    )
}

@Composable
private fun TypeChip(type: Int, romData: RomData) {
    val name = romData.typeName(type)
    if (name.isEmpty()) return
    Text(
        name, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(typeColor(type), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp)
    )
}

@Composable
private fun MemberCard(party: List<PartyMember>, selected: Int?, m: PartyMember, romData: RomData, onSelect: (Int?) -> Unit) {
    // Compagni in fila: un tocco cambia scheda.
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        party.forEachIndexed { i, p ->
            Text(
                p.name, color = if (i == selected) Ink else Cream, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(if (i == selected) Yellow else Row1, RoundedCornerShape(50))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
    Column(
        Modifier.fillMaxWidth().background(Row1, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(m.name, color = Cream, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        val species = romData.speciesName(m.species)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                (if (species.isNotEmpty() && species != m.name) "$species · " else "") + "L. ${m.level}",
                color = Dim, fontSize = 14.sp
            )
            m.types.forEach { TypeChip(it, romData) }
        }
        HpBar(m.hp, m.maxHp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PS ${m.hp}/${m.maxHp}", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            StatusChip(m.status)
        }
    }
    m.stats?.let { st ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("Attacco", st.attack, Modifier.weight(1f))
            StatBox("Difesa", st.defense, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("Velocità", st.speed, Modifier.weight(1f))
            StatBox("Speciale", st.special, Modifier.weight(1f))
        }
    }
    Text("Mosse", color = Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    for (move in m.moves) {
        val info = romData.moveInfo(move.id)
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Row1, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(romData.moveName(move.id), color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (info != null) TypeChip(info.type, romData)
            val maxPp = romData.maxPp(move.id, move.ppUps)
            Text(
                if (maxPp != null) "PP ${move.pp}/$maxPp" else "PP ${move.pp}",
                color = Dim, fontSize = 13.sp, modifier = Modifier.width(72.dp)
            )
        }
    }
}

@Composable
private fun StatBox(label: String, value: Int, modifier: Modifier) {
    Row(
        modifier.background(Row1, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("$value", color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
