package it.giallotouch.bridge

import it.giallotouch.emu.NativeCore
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Sul gioco vero, con almeno due strumenti nella borsa (stato `giallo.state.squadra`, `giallo.state.bag` o
 * `giallo.state.items`): lo scambio passa dal menu originale (START → STRUM. → SELECT, SELECT) e inverte i primi due.
 */
class RealGameSwapTest {
    @Test
    fun scambiaIPrimiDue() {
        val rom = System.getProperty("giallo.rom")?.let(::File)
        assumeTrue(rom != null && rom.isFile)
        val romBytes = rom!!.readBytes()
        val state = listOf("giallo.state.items", "giallo.state.squadra", "giallo.state.bag", "giallo.state")
            .mapNotNull { System.getProperty(it)?.let(::File)?.takeIf(File::isFile) }
            .firstOrNull { f ->
                val h = NativeCore.nativeCreate(48000)
                try { NativeCore.nativeLoadRom(h, romBytes); NativeCore.nativeLoadState(h, f.readBytes()) && NativeCore.nativeReadMemory(h, MemoryMapIt.BAG_COUNT) >= 2 }
                finally { NativeCore.nativeDestroy(h) }
            }
        println("stato con almeno 2 strumenti: $state")
        assumeTrue(state != null)
        val h = NativeCore.nativeCreate(48000)
        try {
            NativeCore.nativeLoadRom(h, romBytes)
            NativeCore.nativeLoadState(h, state!!.readBytes())
            val data = RomData.load(romBytes, supported = true)
            val pixels = IntArray(160 * 144); val audio = ShortArray(8192)
            val vars = ByteArray(MenuReader.MENU_VARS_SIZE); val tiles = ByteArray(MenuReader.SCREEN_SIZE)
            repeat(10) { NativeCore.nativeRunFrame(h, pixels, audio) }
            val first = NativeCore.nativeReadMemory(h, MemoryMapIt.BAG_ITEMS)
            val second = NativeCore.nativeReadMemory(h, MemoryMapIt.BAG_ITEMS + 2)
            val ex = ItemActionExecutor().apply { start(ItemAction.Swap(0, data.itemName(first), 1)) }
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
            println("scambio in $frames fotogrammi: ${ex.result}")
            assertEquals(ItemActionExecutor.Result.DONE, ex.result)
            assertEquals("primo posto", second, NativeCore.nativeReadMemory(h, MemoryMapIt.BAG_ITEMS))
            assertEquals("secondo posto", first, NativeCore.nativeReadMemory(h, MemoryMapIt.BAG_ITEMS + 2))
        } finally { NativeCore.nativeDestroy(h) }
    }
}
