package it.giallotouch.storage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Segnalazione di un difetto (D-064): un file zip con la foto dello schermo, lo stato della partita in quel momento e
 * una nota con luogo, posizione e schermate riconosciute dall'app. Il giocatore lo condivide (per esempio con Claude)
 * dal menu di Android; resta nella cache dell'app e non va mai nel repository.
 */
object BugReport {
    fun write(context: Context, screen: Bitmap?, state: ByteArray, info: String, version: String): File {
        val dir = File(context.cacheDir, "segnalazioni").apply { mkdirs() }
        dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(4)?.forEach { it.delete() }   // tiene le ultime 5
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ITALY).format(Date())
        val file = File(dir, "segnalazione-$stamp.zip")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            if (screen != null) {
                zip.putNextEntry(ZipEntry("schermo.png")); screen.compress(Bitmap.CompressFormat.PNG, 100, zip); zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("partita.state")); zip.write(state); zip.closeEntry()
            zip.putNextEntry(ZipEntry("info.txt"))
            zip.write("GialloTouch $version\n$stamp\n$info\n".toByteArray()); zip.closeEntry()
        }
        return file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".segnalazioni", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Segnalazione GialloTouch")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Invia la segnalazione").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
