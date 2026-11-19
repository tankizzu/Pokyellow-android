package it.giallotouch.bridge

/** Fotografia dello stato del gioco letta dalla memoria, usata dall'interfaccia nativa. */
data class GameSnapshot(
    val playerName: String,
    val party: List<PartyMember>,
    val money: Int,
    val mapId: Int,
    val x: Int,
    val y: Int,
    /** 0 = nessuna lotta, 1 = selvatico, 2 = allenatore. */
    val battleType: Int = 0,
    /** Creatura del giocatore in campo; presente solo durante la lotta. */
    val battleMon: BattleMon? = null,
    /** Menu attivo sullo schermo (lotta, domanda Sì/No), se riconosciuto. */
    val menu: MenuState = MenuState.NONE,
    /** true quando il giocatore cammina liberamente: nessuna scena, lotta, testo o menu. */
    val freeRoam: Boolean = false,
    /** Schermata dei nomi del gioco, se è aperta (per mostrare la tastiera del telefono). */
    val naming: NamingScreen? = null,
    /** Strumenti nella borsa, nello stesso ordine del gioco. */
    val bag: List<BagItem> = emptyList(),
    /** Strumenti depositati nel PC del giocatore (D-055). */
    val pcItems: List<BagItem> = emptyList(),
    /** Numeri del Pokédex visti e presi, in ordine. */
    val pokedexSeen: List<Int> = emptyList(),
    val pokedexOwned: List<Int> = emptyList(),
    /** Sullo schermo c'è il cursore ▶ di un menu del gioco (per mostrare SELECT solo nei menu, non nei dialoghi). */
    val cursorOnScreen: Boolean = false,
) {
    val inBattle: Boolean get() = battleType != 0

    companion object {
        val EMPTY = GameSnapshot("", emptyList(), 0, 0, 0, 0)
    }
}

data class BattleMon(
    val species: Int,
    val level: Int,
    val hp: Int,
    val maxHp: Int,
    val moves: List<MoveSlot>,
)

/** Mossa con PP attuali e numero di PP Su usati (bit alti del byte dei PP). */
data class BagItem(val id: Int, val quantity: Int)

data class MoveSlot(val id: Int, val pp: Int, val ppUps: Int = 0)

data class PartyMember(
    val species: Int,
    val name: String,
    val level: Int,
    val hp: Int,
    val maxHp: Int,
    val status: Status,
    /** Tipi (indici del gioco); uguali se la specie ha un solo tipo. */
    val types: List<Int> = emptyList(),
    val moves: List<MoveSlot> = emptyList(),
    val stats: Stats? = null,
    /** Punti esperienza totali (struttura +14, 3 byte). */
    val exp: Int = 0,
)

/** Statistiche calcolate dal gioco (struttura della squadra +36…+43). */
data class Stats(val attack: Int, val defense: Int, val speed: Int, val special: Int)

/** Stato alterato del byte "Status" (bit 0-2 sonno, 3 veleno, 4 scottatura, 5 congelamento, 6 paralisi). */
enum class Status(val label: String) {
    NONE(""), SLEEP("Addormentato"), POISON("Avvelenato"), BURN("Scottato"), FREEZE("Congelato"), PARALYSIS("Paralizzato");

    companion object {
        fun fromByte(b: Int): Status = when {
            b and 0x07 != 0 -> SLEEP
            b and 0x08 != 0 -> POISON
            b and 0x10 != 0 -> BURN
            b and 0x20 != 0 -> FREEZE
            b and 0x40 != 0 -> PARALYSIS
            else -> NONE
        }
    }
}

/** Pokémon nel box del PC (D-058): specie (indice del gioco), livello, soprannome. */
data class BoxMon(val species: Int, val level: Int, val name: String)

object BoxReader {
    /** [block] copiato da [MemoryMapIt.BOX_COUNT] per [MemoryMapIt.BOX_BLOCK_SIZE] byte; lista vuota se non plausibile. */
    fun read(block: ByteArray): List<BoxMon> {
        val m = MemoryMapIt
        fun u8(a: Int) = block[a - m.BOX_COUNT].toInt() and 0xFF
        val n = u8(m.BOX_COUNT)
        if (n > m.BOX_CAPACITY || u8(m.BOX_SPECIES + n) != 0xFF) return emptyList()
        return (0 until n).map { i ->
            val base = m.BOX_MONS + i * m.BOX_STRUCT_LENGTH
            BoxMon(u8(base), u8(base + 3), TextCodec.decode(block, m.BOX_MON_NICKS + i * m.NAME_LENGTH - m.BOX_COUNT, m.NAME_LENGTH))
        }.takeIf { l -> l.all { it.species != 0 && it.level in 1..100 } } ?: emptyList()
    }
}
