package it.giallotouch.bridge

import it.giallotouch.bridge.MemoryMapIt as M

/**
 * Struttura di una creatura in squadra (44 byte, valori a 2 byte in big-endian): specie +0, PS +1, stato +4,
 * tipi +5/+6, mosse +8…+11, PP +29…+32 (6 bit bassi), livello +33, PS massimi +34, attacco +36, difesa +38,
 * velocità +40, speciale +42.
 *
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
        val battleType = u8(M.IS_IN_BATTLE).takeIf { it in 0..2 } ?: 0
        return GameSnapshot(
            playerName = TextCodec.decode(block, M.PLAYER_NAME - M.BLOCK_START, M.NAME_LENGTH),
            party = party,
            money = money,
            mapId = u8(M.CUR_MAP),
            x = u8(M.X_COORD),
            y = u8(M.Y_COORD),
            battleType = battleType,
            battleMon = if (battleType != 0) readBattleMon(::u8, ::u16be) else null,
            bag = readBag(::u8),
            pokedexSeen = readFlags(::u8, M.POKEDEX_SEEN),
            pokedexOwned = readFlags(::u8, M.POKEDEX_OWNED),
        )
    }

    private fun readFlags(u8: (Int) -> Int, start: Int): List<Int> =
        (1..M.NUM_POKEDEX).filter { n -> (u8(start + (n - 1) / 8) shr ((n - 1) % 8)) and 1 == 1 }

    private fun readBag(u8: (Int) -> Int): List<BagItem> {
        val count = u8(M.BAG_COUNT)
        if (count > M.BAG_CAPACITY || u8(M.BAG_ITEMS + 2 * count) != 0xFF) return emptyList()
        return (0 until count).map { BagItem(u8(M.BAG_ITEMS + 2 * it), u8(M.BAG_ITEMS + 2 * it + 1)) }
            .takeIf { items -> items.all { it.id != 0 && it.id != 0xFF } } ?: emptyList()
    }

    private fun readBattleMon(u8: (Int) -> Int, u16be: (Int) -> Int): BattleMon? {
        val b = M.BATTLE_MON
        val species = u8(b)
        val level = u8(b + 14)
        val hp = u16be(b + 1)
        val maxHp = u16be(b + 15)
        if (species == 0 || level !in 1..100 || maxHp == 0 || hp > maxHp) return null
        val moves = (0 until 4).mapNotNull { i ->
            val id = u8(b + 8 + i)
            if (id in 1..M.NUM_MOVES) MoveSlot(id, u8(b + 25 + i) and 0x3F, u8(b + 25 + i) shr 6) else null
        }
        return BattleMon(species, level, hp, maxHp, moves)
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
                types = listOf(u8(base + 5), u8(base + 6)).distinct(),
                moves = (0 until 4).mapNotNull { k ->
                    val id = u8(base + 8 + k)
                    if (id in 1..M.NUM_MOVES) MoveSlot(id, u8(base + 29 + k) and 0x3F, u8(base + 29 + k) shr 6) else null
                },
                stats = Stats(u16be(base + 36), u16be(base + 38), u16be(base + 40), u16be(base + 42)),
            )
        }
        return members
    }

    private fun bcd(b: Int): Int = ((b shr 4) and 0xF) * 10 + (b and 0xF)
}
