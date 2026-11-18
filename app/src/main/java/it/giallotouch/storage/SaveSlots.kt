package it.giallotouch.storage

import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

/**
 * Salvataggi dell'app (D-034), separati dal salvataggio del gioco (quello fatto dal menu del gioco, "CONTINUA"):
 * - MANUALE: il pulsante "Salva", in qualsiasi punto;
 * - AUTOMATICO: quando si esce dall'app durante una partita, per non perdere nulla.
 * Si caricano dal menu iniziale con "Carica partita". Ogni salvataggio ha lo stato completo, una scheda di
 * informazioni (data, nome, squadra, soldi) e una miniatura dello schermo, tutto solo nella memoria privata del telefono.
 */
enum class SaveSlot(val fileName: String, val label: String, val detail: String) {
    MANUAL("manuale", "Salvataggio", "pulsante Salva"),
    AUTO("ripresa", "Automatico", "all'uscita dall'app"),
}

/** Scheda di un salvataggio, mostrata nella schermata "Carica partita". */
data class SlotInfo(
    val savedAt: Long,
    val playerName: String = "",
    val money: Int = 0,
    val badges: Int = 0,
    /** Squadra come coppie (nome, livello). */
    val party: List<Pair<String, Int>> = emptyList(),
) {
    fun encode(): String {
        val p = Properties()
        p["savedAt"] = savedAt.toString()
        p["playerName"] = playerName
        p["money"] = money.toString()
        p["badges"] = badges.toString()
        p["party"] = party.joinToString("|") { "${it.first.replace("|", " ")}:${it.second}" }
        return StringWriter().also { p.store(it, null) }.toString()
    }

    companion object {
        fun decode(text: String): SlotInfo? = runCatching {
            val p = Properties().apply { load(StringReader(text)) }
            val party = (p.getProperty("party") ?: "").split("|").filter { it.isNotBlank() }.mapNotNull { entry ->
                val i = entry.lastIndexOf(':')
                if (i <= 0) null else entry.substring(0, i) to (entry.substring(i + 1).toIntOrNull() ?: return@mapNotNull null)
            }
            SlotInfo(
                savedAt = p.getProperty("savedAt")?.toLong() ?: return null,
                playerName = p.getProperty("playerName") ?: "",
                money = p.getProperty("money")?.toIntOrNull() ?: 0,
                badges = p.getProperty("badges")?.toIntOrNull() ?: 0,
                party = party,
            )
        }.getOrNull()
    }
}

/** Un salvataggio presente sul telefono, con scheda (se c'è) e miniatura (PNG, se c'è). */
data class SlotEntry(val slot: SaveSlot, val info: SlotInfo, val thumbnail: ByteArray?)

/** File di un salvataggio dentro la cartella della partita. */
class SlotFiles(dir: File, slot: SaveSlot) {
    val state = File(dir, slot.fileName + ".state")
    val info = File(dir, slot.fileName + ".info")
    val thumbnail = File(dir, slot.fileName + ".png")
}
