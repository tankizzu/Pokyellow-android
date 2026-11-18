package it.giallotouch.bridge

import it.giallotouch.emu.NativeCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Sul gioco vero, nella cameretta con la Pozione nella borsa:
 * - "Getta" apre START → STRUM. → POZIONE → GETTA, quantità 1, conferma Sì, chiude i menu: la borsa resta vuota;
 * - "Usa" arriva fino a USA e lascia continuare il gioco (qui risponde che non ci sono compagni).
 */
class RealGameItemTest {
    private fun run(action: (RomData) -> ItemAction, check: (Long, ItemActionExecutor.Result?) -> Unit) {
        val rom = System.getProperty("giallo.rom")?.let(::File)
        val state = System.getProperty("giallo.state.bag")?.let(::File)
        assumeTrue(rom != null && rom.isFile && state != null && state.isFile)
        val h = NativeCore.nativeCreate(48000)
        try {
            val romBytes = rom!!.readBytes()
            NativeCore.nativeLoadRom(h, romBytes)
            assumeTrue(NativeCore.nativeLoadState(h, state!!.readBytes()))
            val data = RomData.load(romBytes, supported = true)
            val pixels = IntArray(160 * 144); val audio = ShortArray(8192)
            val vars = ByteArray(MenuReader.MENU_VARS_SIZE); val tiles = ByteArray(MenuReader.SCREEN_SIZE)
            repeat(10) { NativeCore.nativeRunFrame(h, pixels, audio) }
            val ex = ItemActionExecutor().apply { start(action(data)) }
            var frames = 0
            while (ex.busy && frames < 60 * 40) {
                NativeCore.nativeReadRange(h, MenuReader.MENU_VARS, vars)
                NativeCore.nativeReadRange(h, MemoryMapIt.TILE_MAP, tiles)
                val scroll = NativeCore.nativeReadMemory(h, MemoryMapIt.LIST_SCROLL_OFFSET)
                val free = NativeCore.nativeReadMemory(h, MemoryMapIt.JOY_IGNORE) == 0 && FreeRoam.screenIsMap(tiles)
                NativeCore.nativeSetKeys(h, ex.step(ItemMenus.read(vars, tiles, scroll), free, vars.contentHashCode() * 31 + tiles.contentHashCode()))
                NativeCore.nativeRunFrame(h, pixels, audio)
                frames++
            }
            println("azione completata in $frames fotogrammi: ${ex.result}")
            check(h, ex.result)
        } finally { NativeCore.nativeDestroy(h) }
    }

    @Test
    fun gettaLaPozione() = run({ ItemAction.Toss(0, it.itemName(0x14), 1) }) { h, result ->
        assertEquals(ItemActionExecutor.Result.DONE, result)
        assertEquals("borsa vuota", 0, NativeCore.nativeReadMemory(h, MemoryMapIt.BAG_COUNT))
        val tiles = ByteArray(MenuReader.SCREEN_SIZE).also { NativeCore.nativeReadRange(h, MemoryMapIt.TILE_MAP, it) }
        assertTrue("menu chiusi", FreeRoam.screenIsMap(tiles))
    }

    @Test
    fun usaArrivaAlGioco() = run({ ItemAction.Use(0, it.itemName(0x14)) }) { h, result ->
        assertEquals(ItemActionExecutor.Result.DONE, result)
        assertEquals("la Pozione resta", 1, NativeCore.nativeReadMemory(h, MemoryMapIt.BAG_COUNT))
    }

    @Test
    fun nomeSbagliatoSiFerma() = run({ ItemAction.Toss(0, "ALTRO", 1) }) { h, result ->
        assertEquals(ItemActionExecutor.Result.FAILED, result)
        assertEquals(1, NativeCore.nativeReadMemory(h, MemoryMapIt.BAG_COUNT))
    }
}
