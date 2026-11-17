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
    val stateFile = File(dir, "ripresa.state")

    fun hasRom(): Boolean = romFile.isFile && romFile.length() > 0

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
