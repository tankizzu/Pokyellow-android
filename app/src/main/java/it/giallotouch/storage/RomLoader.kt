package it.giallotouch.storage

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Prepara il file scelto dall'utente: se è uno ZIP estrae il gioco (.gb/.gbc), poi controlla che sia
 * davvero un gioco per Game Boy (somma di controllo dell'intestazione) e se è la versione supportata.
 */
object RomLoader {
    private const val MAX_ROM_SIZE = 8 * 1024 * 1024

    sealed interface Result {
        data class Supported(val rom: ByteArray) : Result
        data class OtherGame(val rom: ByteArray, val title: String) : Result
        data class Invalid(val message: String) : Result
    }

    fun prepare(picked: ByteArray): Result {
        val rom = if (isZip(picked)) {
            extractFromZip(picked) ?: return Result.Invalid("Lo ZIP non contiene il file del gioco (.gb o .gbc).")
        } else picked
        if (!isValidGameBoyRom(rom)) return Result.Invalid("Questo file non è un gioco per Game Boy valido.")
        return if (SaveStorage.sha1(rom) == SaveStorage.SUPPORTED_SHA1) Result.Supported(rom) else Result.OtherGame(rom, title(rom))
    }

    fun isZip(b: ByteArray) = b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() && b[2] == 3.toByte() && b[3] == 4.toByte()

    fun extractFromZip(zip: ByteArray): ByteArray? = runCatching {
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val entry = z.nextEntry ?: return@use null
                val name = entry.name.lowercase()
                if (!entry.isDirectory && (name.endsWith(".gb") || name.endsWith(".gbc"))) {
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = z.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_ROM_SIZE) return@use null
                    }
                    return@use out.toByteArray()
                }
            }
            @Suppress("UNREACHABLE_CODE") null
        }
    }.getOrNull()

    /** Intestazione presente e somma di controllo corretta (byte 0x134–0x14C confrontati con 0x14D). */
    fun isValidGameBoyRom(rom: ByteArray): Boolean {
        if (rom.size < 0x8000 || rom.size > MAX_ROM_SIZE) return false
        var x = 0
        for (i in 0x134..0x14C) x = (x - (rom[i].toInt() and 0xFF) - 1) and 0xFF
        return x == (rom[0x14D].toInt() and 0xFF)
    }

    fun title(rom: ByteArray): String =
        (0x134 until 0x143).map { rom[it].toInt() and 0xFF }.takeWhile { it in 0x20..0x7E }.map { it.toChar() }.joinToString("").trim()
}
