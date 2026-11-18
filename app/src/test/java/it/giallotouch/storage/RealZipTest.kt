package it.giallotouch.storage

import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Con lo ZIP vero dell'utente (solo in locale): deve essere riconosciuto come la versione supportata. */
class RealZipTest {
    @Test
    fun zipDellUtente_riconosciuto() {
        val f = System.getProperty("giallo.zip")?.let(::File)
        assumeTrue(f != null && f.isFile)
        assertTrue(RomLoader.prepare(f!!.readBytes()) is RomLoader.Result.Supported)
    }
}
