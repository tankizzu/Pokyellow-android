package it.giallotouch.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class RomDataTest {

    @Test
    fun datiMosse_daRomSintetica() {
        // Tre voci sintetiche da 6 byte (id, effetto, potenza, tipo, precisione, PP) dopo un byte iniziale.
        val e1 = listOf(1, 0, 0, 0x0A, 0, 0x14)
        val e2 = listOf(2, 0, 0x28, 0x17, 0xFF, 0x1E)
        val e3 = listOf(3, 0, 0, 0, 0, 5)
        val rom = (listOf(9) + e1 + e2 + e3).map { it.toByte() }.toByteArray()
        assertEquals(
            listOf(RomData.MoveInfo(0x0A, 0x14, 0), RomData.MoveInfo(0x17, 0x1E, 0x28), RomData.MoveInfo(0, 5, 0)),
            RomData.readMoveData(rom, 1, 3)
        )
        assertNull("dati insufficienti", RomData.readMoveData(rom, 1, 4))
        assertNull("id non consecutivi", RomData.readMoveData(rom, 7, 2))
    }

    @Test
    fun nomiPuntati_daRomSintetica() {
        // Banco 1 sintetico (offset 0x4000): due puntatori a 0x4004 e 0x4007, poi "AB" e "C".
        val rom = ByteArray(0x4010)
        val t = 0x4000
        rom[t] = 0x04; rom[t + 1] = 0x40; rom[t + 2] = 0x07; rom[t + 3] = 0x40
        rom[0x4004] = 0x80.toByte(); rom[0x4005] = 0x81.toByte(); rom[0x4006] = 0x50
        rom[0x4007] = 0x82.toByte(); rom[0x4008] = 0x50
        assertEquals(listOf("AB", "C"), RomData.readPointedNames(rom, t, 2))
    }

    /** Sulla ROM vera (fornita solo in locale): controlla i numeri, senza confrontare testi del gioco. */
    @Test
    fun romVera_tabelleCoerenti() {
        val file = System.getProperty("giallo.rom")?.let(::File)
        assumeTrue(file != null && file.isFile)
        val data = RomData.load(file!!.readBytes(), supported = true)
        assertEquals(RomData.MoveInfo(type = 0x17, maxPp = 30, power = 40), data.moveInfo(0x54))
        assertEquals(RomData.MoveInfo(type = 0x00, maxPp = 40, power = 0), data.moveInfo(0x2D))
        assertEquals(true, data.typeName(0x17).isNotEmpty() && data.moveName(0x54).isNotEmpty())
        // nomi delle specie: tutti presenti, al massimo 10 caratteri; PP massimi con i PP Su (30 + 6 × 3)
        assertEquals(true, (1..190).all { data.speciesName(it).length in 1..10 })
        assertEquals(48, data.maxPp(0x54, 3))
        // strumenti: 97 nomi dalla ROM; macchine con numero
        assertEquals(true, (1..0x61).all { data.itemName(it).length in 1..13 && !data.itemName(it).startsWith("STRUMENTO") })
        assertEquals("MN01", data.itemName(0xC4))
        assertEquals("MT50", data.itemName(0xFA))
        // Pokédex: Pikachu (indice interno 0x54) è il n. 25; tutti i 151 numeri hanno un nome
        assertEquals(25, data.dexNumber(0x54))
        assertEquals(data.speciesName(0x54), data.speciesNameForDex(25))
        assertEquals(true, (1..151).all { data.speciesNameForDex(it).isNotEmpty() })
        // velocità di crescita: Pikachu media veloce (0)
        assertEquals(0, data.growthRate(0x54))
        // icone della squadra: Pikachu ha la sua; Bulbasaur (n. 1, indice 0x99) e Ivysaur la stessa (pianta)
        val pika = data.partyIcon(0x54)!!
        assertEquals(256, pika.size)
        assertEquals(true, pika.count { it == 3 } in 20..200)
        val bulba = data.partyIcon(data.indexForDex(1))!!; val ivy = data.partyIcon(data.indexForDex(2))!!
        assertEquals(true, bulba.contentEquals(ivy) && !bulba.contentEquals(pika))
        // icone da mezza casella: la metà destra è lo specchio della sinistra
        assertEquals(true, (0 until 16).all { y -> (0 until 8).all { x -> bulba[y * 16 + x] == bulba[y * 16 + 15 - x] } })
        // immagini frontali: tutte le 151 si decodificano, con dimensioni da 5×5 a 7×7 caselle e qualche pixel scuro
        val pics = (1..151).map { data.frontPic(data.indexForDex(it)) }
        assertEquals(true, pics.all { p -> p != null && p.width in 40..56 && p.height in 40..56 && p.pixels.count { it == 3 } > 30 })
        assertEquals(40, data.frontPic(0x54)!!.width)
        // Pokédex: Pikachu 0,4 m e 6,0 kg, categoria e descrizione presenti; tutte le 151 voci leggibili
        val pika2 = data.dexEntry(0x54)!!
        assertEquals(4, pika2.heightDm); assertEquals(60, pika2.weightHg)
        assertEquals(true, pika2.category.size in 3..10 && pika2.lines.size in 3..6)
        assertEquals(true, (1..151).all { n -> data.dexEntry(data.indexForDex(n))?.let { it.lines.isNotEmpty() && it.heightDm > 0 } == true })
    }
}
