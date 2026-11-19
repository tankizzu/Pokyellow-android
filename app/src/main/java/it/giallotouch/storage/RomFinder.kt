package it.giallotouch.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Cerca il file del gioco nella cartella scelta dal giocatore (D-066): file .gb, .gbc o .zip fino a 3 livelli di
 * sottocartelle; il primo che è la versione italiana supportata vince, altrimenti il primo gioco per Game Boy.
 * Legge solo dentro la cartella scelta (permesso dato dal giocatore con il selettore di Android).
 */
object RomFinder {
    sealed interface Found {
        data class Rom(val result: RomLoader.Result) : Found
        data object Nothing : Found
    }

    fun find(context: Context, tree: Uri): Found {
        val resolver = context.contentResolver
        val candidates = ArrayList<Pair<Uri, Long>>()
        fun walk(docId: String, depth: Int) {
            if (depth > 3 || candidates.size > 60) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE)
            val dirs = ArrayList<String>()
            runCatching {
                resolver.query(children, cols, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = (c.getString(1) ?: "").lowercase()
                        val mime = c.getString(2) ?: ""
                        val size = if (c.isNull(3)) 0L else c.getLong(3)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) dirs += id
                        else if ((name.endsWith(".gb") || name.endsWith(".gbc") || name.endsWith(".zip")) && size in 0x8000..(16L shl 20))
                            candidates += DocumentsContract.buildDocumentUriUsingTree(tree, id) to size
                    }
                }
            }
            for (d in dirs) walk(d, depth + 1)
        }
        walk(DocumentsContract.getTreeDocumentId(tree), 0)
        var other: RomLoader.Result? = null
        for ((uri, _) in candidates.sortedBy { it.second }) {
            val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: continue
            when (val r = RomLoader.prepare(bytes)) {
                is RomLoader.Result.Supported -> return Found.Rom(r)
                is RomLoader.Result.OtherGame -> if (other == null) other = r
                is RomLoader.Result.Invalid -> Unit
            }
        }
        return other?.let { Found.Rom(it) } ?: Found.Nothing
    }
}
