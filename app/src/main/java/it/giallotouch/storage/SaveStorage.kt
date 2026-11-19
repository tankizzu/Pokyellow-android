package it.giallotouch.storage

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * File dell'app nella memoria privata del telefono.
 * Ogni scrittura è atomica: file temporaneo → sincronizzazione su disco → sostituzione.
 * Il file precedente resta come copia di riserva (.bak).
 */
class SaveStorage(context: Context) {
    private val dir = File(context.filesDir, "partita").apply { mkdirs() }

    val romFile = File(dir, "gioco.rom")
    val batteryFile = File(dir, "salvataggio.sav")
    /** Copia del salvataggio del gioco fatta prima di caricare una partita dell'app (che lo riporta a quel momento). */
    val batteryBeforeLoadFile = File(dir, "salvataggio.prima-di-carica.sav")

    fun hasRom(): Boolean = romFile.isFile && romFile.length() > 0

    /**
     * Riquadri e lettere del gioco (D-066), presi dal file del giocatore mentre il gioco gira e tenuti solo in questo
     * telefono: servono alle schermate di scelta del file, che così sono identiche al gioco. Fuori da "partita".
     */
    private val tilesFile = File(context.filesDir, "grafica-del-gioco.bin")

    fun saveGameTiles(sheet: IntArray) {
        val buf = java.nio.ByteBuffer.allocate(sheet.size * 4); buf.asIntBuffer().put(sheet)
        runCatching { writeAtomic(tilesFile, buf.array()) }
    }

    /** Ultima scheda allenatore vista (D-068): per mostrarla subito alla volta dopo. Solo in questo telefono. */
    private val cardFile = File(dir, "scheda-allenatore.bin")

    fun saveCardImage(frame: IntArray) {
        val buf = java.nio.ByteBuffer.allocate(frame.size * 4); buf.asIntBuffer().put(frame)
        runCatching { writeAtomic(cardFile, buf.array()) }
    }

    fun loadCardImage(): IntArray? = runCatching {
        val b = cardFile.takeIf { it.isFile }?.readBytes() ?: return null
        if (b.size != 160 * 144 * 4) return null
        IntArray(160 * 144).also { java.nio.ByteBuffer.wrap(b).asIntBuffer().get(it) }
    }.getOrNull()

    fun loadGameTiles(): IntArray? = runCatching {
        val b = tilesFile.takeIf { it.isFile }?.readBytes() ?: return null
        if (b.size != 128 * 128 * 4) return null
        IntArray(128 * 128).also { java.nio.ByteBuffer.wrap(b).asIntBuffer().get(it) }
    }.getOrNull()

    fun slotFiles(slot: SaveSlot) = SlotFiles(dir, slot)

    /** Cancella un salvataggio dell'app (stato, scheda, miniatura e copie di riserva). */
    fun deleteSlot(slot: SaveSlot) {
        val f = slotFiles(slot)
        for (base in listOf(f.state, f.info, f.thumbnail)) {
            base.delete()
            File(dir, base.name + ".bak").delete()
        }
    }

    /** Salvataggi dell'app presenti, dal più recente. Senza scheda (versioni precedenti) si usa la data del file. */
    fun listSlots(): List<SlotEntry> = SaveSlot.entries.mapNotNull { slot ->
        val files = slotFiles(slot)
        val stateFile = listOf(files.state, File(dir, files.state.name + ".bak")).firstOrNull { it.isFile && it.length() > 0 }
            ?: return@mapNotNull null
        val info = readWithBackup(files.info)?.let { SlotInfo.decode(String(it, Charsets.UTF_8)) } ?: SlotInfo(stateFile.lastModified())
        SlotEntry(slot, info, readWithBackup(files.thumbnail))
    }.sortedByDescending { it.info.savedAt }

    /** Dimentica un file del gioco non valido, con i salvataggi creati da quel file. */
    fun forgetGameFile() {
        val slotFiles = SaveSlot.entries.flatMap { slotFiles(it).let { f -> listOf(f.state, f.info, f.thumbnail) } }
        for (base in listOf(romFile, batteryFile) + slotFiles) {
            base.delete()
            File(dir, base.name + ".bak").delete()
            File(dir, base.name + ".tmp").delete()
        }
    }

    fun writeAtomic(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        if (target.exists()) {
            val bak = File(target.parentFile, target.name + ".bak")
            bak.delete()
            target.renameTo(bak)
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    /** Legge il file; se manca o è vuoto prova la copia di riserva. */
    fun readWithBackup(target: File): ByteArray? {
        val bak = File(target.parentFile, target.name + ".bak")
        for (f in listOf(target, bak)) {
            if (f.isFile && f.length() > 0) runCatching { return f.readBytes() }
        }
        return null
    }

    fun backupOf(target: File): ByteArray? {
        val bak = File(target.parentFile, target.name + ".bak")
        return if (bak.isFile && bak.length() > 0) runCatching { bak.readBytes() }.getOrNull() else null
    }

    companion object {
        /** Impronta della versione italiana supportata. */
        const val SUPPORTED_SHA1 = "05bb8e99f24d498613930949730afa8024e77d08"

        fun sha1(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
