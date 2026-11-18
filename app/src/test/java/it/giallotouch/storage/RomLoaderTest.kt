package it.giallotouch.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RomLoaderTest {

    /** ROM sintetica da 32 KiB con titolo e somma di controllo dell'intestazione corretti (nessun dato reale). */
    private fun fakeRom(title: String = "PROVA"): ByteArray {
        val rom = ByteArray(0x8000)
        title.forEachIndexed { i, c -> rom[0x134 + i] = c.code.toByte() }
        var x = 0
        for (i in 0x134..0x14C) x = (x - (rom[i].toInt() and 0xFF) - 1) and 0xFF
        rom[0x14D] = x.toByte()
        return rom
    }

    private fun zip(name: String, data: ByteArray): ByteArray = ByteArrayOutputStream().also { bos ->
        ZipOutputStream(bos).use { z -> z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry() }
    }.toByteArray()

    @Test
    fun romDentroZip_vieneEstratta() {
        val rom = fakeRom()
        val r = RomLoader.prepare(zip("Gioco (Italia).gb", rom))
        assertTrue(r is RomLoader.Result.OtherGame)
        r as RomLoader.Result.OtherGame
        assertTrue(r.rom.contentEquals(rom))
        assertEquals("PROVA", r.title)
    }

    @Test
    fun zipSenzaGioco_eFileNonValidi() {
        assertTrue(RomLoader.prepare(zip("leggimi.txt", ByteArray(10))) is RomLoader.Result.Invalid)
        assertTrue(RomLoader.prepare(ByteArray(0x8000).also { it[0x14D] = 1 }) is RomLoader.Result.Invalid)
        assertTrue(RomLoader.prepare(ByteArray(100)) is RomLoader.Result.Invalid)
    }

    @Test
    fun romDiretta_valida() {
        assertTrue(RomLoader.prepare(fakeRom()) is RomLoader.Result.OtherGame)
    }
}
