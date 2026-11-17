package it.giallotouch.bridge

/**
 * Indirizzi di memoria della versione italiana (SHA-1 05bb8e99…), verificati con
 * gli strumenti in tools/rom-analysis. Dettagli e stato di verifica: docs/MEMORY_MAP_IT.md.
 */
object MemoryMapIt {
    const val SPRITE_STATE_DATA_1 = 0xC100
    const val SPRITE_STATE_DATA_2 = 0xC200
    const val TILE_MAP = 0xC3A0
    const val OVERWORLD_MAP = 0xC6E8
    const val CURRENT_MENU_ITEM = 0xCC26

    const val PLAYER_NAME = 0xD15C
    const val PARTY_COUNT = 0xD167
    const val PARTY_SPECIES = 0xD168
    const val PARTY_MONS = 0xD16F
    const val PARTY_MON_OT = 0xD277
    const val PARTY_MON_NICKS = 0xD2B9
    const val NUM_BAG_ITEMS = 0xD321
    const val BAG_ITEMS = 0xD322
    const val PLAYER_MONEY = 0xD34B
    const val OPTIONS = 0xD359
    const val CUR_MAP = 0xD362
    const val Y_COORD = 0xD365
    const val X_COORD = 0xD366
    const val CUR_MAP_TILESET = 0xD36B
    const val CUR_MAP_HEIGHT = 0xD36C
    const val CUR_MAP_WIDTH = 0xD36D

    const val PARTY_LENGTH = 6
    const val PARTY_STRUCT_LENGTH = 44
    const val NAME_LENGTH = 11

    /** Primo e ultimo indirizzo da copiare per leggere squadra, borsa, soldi e mappa in un colpo solo. */
    const val BLOCK_START = PLAYER_NAME
    const val BLOCK_END = CUR_MAP_WIDTH
}
