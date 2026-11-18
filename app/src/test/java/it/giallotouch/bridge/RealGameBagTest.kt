package it.giallotouch.bridge

import it.giallotouch.emu.NativeCore
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Sul gioco vero: dopo aver ritirato la Pozione dal PC della cameretta, la borsa contiene uno strumento 0x14 × 1. */
class RealGameBagTest {
    @Test
    fun pozioneDalPc() {
        val rom = System.getProperty("giallo.rom")?.let(::File)
        val state = System.getProperty("giallo.state.bag")?.let(::File)
        assumeTrue(rom != null && rom.isFile && state != null && state.isFile)
        val h = NativeCore.nativeCreate(48000)
        try {
            NativeCore.nativeLoadRom(h, rom!!.readBytes())
            assumeTrue(NativeCore.nativeLoadState(h, state!!.readBytes()))
            NativeCore.nativeRunFrame(h, IntArray(160 * 144), ShortArray(8192))
            val block = ByteArray(GameStateReader.BLOCK_SIZE).also { NativeCore.nativeReadRange(h, MemoryMapIt.BLOCK_START, it) }
            assertEquals(listOf(BagItem(0x14, 1)), GameStateReader.read(block).bag)
            val data = RomData.load(rom.readBytes(), supported = true)
            assertEquals(7, data.itemName(0x14).length)
        } finally { NativeCore.nativeDestroy(h) }
    }
}
