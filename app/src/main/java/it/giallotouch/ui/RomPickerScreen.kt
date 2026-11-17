package it.giallotouch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Primo avvio: si sceglie il file del gioco dal telefono. Il file non lascia mai il telefono. */
@Composable
fun RomPickerScreen(error: String?, warning: String?, onPick: () -> Unit, onContinueAnyway: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF171624))
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Giallo Touch", color = Color(0xFFF6C933), fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Prototipo 0.1", color = Color(0xFFAAA4C8), fontSize = 15.sp)
        Spacer(Modifier.height(28.dp))
        Text(
            "Scegli il file del gioco (la tua copia della versione italiana di Giallo). " +
                "Resta solo in questo telefono: l'app non usa Internet.",
            color = Color(0xFFFFF7DC), fontSize = 16.sp, lineHeight = 23.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF6C933), contentColor = Color(0xFF171624))
        ) { Text("Scegli il file del gioco", fontSize = 17.sp, fontWeight = FontWeight.Bold) }

        if (warning != null && onContinueAnyway != null) {
            Spacer(Modifier.height(20.dp))
            Text(warning, color = Color(0xFFF2B23A), fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onContinueAnyway,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2B49), contentColor = Color(0xFFFFF7DC))
            ) { Text("Usa comunque questo file", fontSize = 15.sp) }
        }
        if (error != null) {
            Spacer(Modifier.height(20.dp))
            Text(error, color = Color(0xFFEF5B5B), fontSize = 14.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(36.dp))
        Text(
            "Motore di emulazione: SameBoy © Lior Halphon, licenza MIT.",
            color = Color(0xFF7D78A0), fontSize = 12.sp
        )
    }
}
