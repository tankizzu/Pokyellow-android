package it.giallotouch.bridge

/** Fotografia dello stato del gioco letta dalla memoria, usata dall'interfaccia nativa. */
data class GameSnapshot(
    val playerName: String,
    val party: List<PartyMember>,
    val money: Int,
    val mapId: Int,
    val x: Int,
    val y: Int,
) {
    companion object {
        val EMPTY = GameSnapshot("", emptyList(), 0, 0, 0, 0)
    }
}

data class PartyMember(
    val species: Int,
    val name: String,
    val level: Int,
    val hp: Int,
    val maxHp: Int,
    val status: Status,
)

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
