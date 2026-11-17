package it.giallotouch.bridge

import it.giallotouch.bridge.MemoryMapIt as M

/**
 * Legge lo stato del gioco da un blocco di memoria copiato (da BLOCK_START a BLOCK_END).
 * Non scrive mai nella memoria del gioco. Se i valori non sono plausibili (per esempio durante
 * la schermata del titolo, quando la memoria non è ancora inizializzata) la squadra risulta vuota.
 */
object GameStateReader {
    val BLOCK_SIZE = M.BLOCK_END - M.BLOCK_START + 1

    fun read(block: ByteArray): GameSnapshot {
        require(block.size >= BLOCK_SIZE) { "blocco troppo corto" }
        fun u8(address: Int) = block[address - M.BLOCK_START].toInt() and 0xFF
        fun u16be(address: Int) = (u8(address) shl 8) or u8(address + 1)

        val party = readParty(block, ::u8, ::u16be)
        val money = bcd(u8(M.PLAYER_MONEY)) * 10000 + bcd(u8(M.PLAYER_MONEY + 1)) * 100 + bcd(u8(M.PLAYER_MONEY + 2))
        return GameSnapshot(
            playerName = TextCodec.decode(block, M.PLAYER_NAME - M.BLOCK_START, M.NAME_LENGTH),
            party = party,
            money = money,
            mapId = u8(M.CUR_MAP),
            x = u8(M.X_COORD),
            y = u8(M.Y_COORD),
        )
    }

    private fun readParty(block: ByteArray, u8: (Int) -> Int, u16be: (Int) -> Int): List<PartyMember> {
        val count = u8(M.PARTY_COUNT)
        if (count !in 1..M.PARTY_LENGTH) return emptyList()
        if (u8(M.PARTY_SPECIES + count) != 0xFF) return emptyList()
        val members = ArrayList<PartyMember>(count)
        for (i in 0 until count) {
            val base = M.PARTY_MONS + i * M.PARTY_STRUCT_LENGTH
            val species = u8(base)
            val hp = u16be(base + 1)
            val status = u8(base + 4)
            val level = u8(base + 33)
            val maxHp = u16be(base + 34)
            if (species == 0 || species != u8(M.PARTY_SPECIES + i)) return emptyList()
            if (level !in 1..100 || maxHp == 0 || hp > maxHp) return emptyList()
            val nickOffset = M.PARTY_MON_NICKS + i * M.NAME_LENGTH - M.BLOCK_START
            members += PartyMember(
                species = species,
                name = TextCodec.decode(block, nickOffset, M.NAME_LENGTH),
                level = level,
                hp = hp,
                maxHp = maxHp,
                status = Status.fromByte(status),
            )
        }
        return members
    }

    private fun bcd(b: Int): Int = ((b shr 4) and 0xF) * 10 + (b and 0xF)
}
