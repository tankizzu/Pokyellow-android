package it.giallotouch.bridge

/**
 * Indirizzi di memoria della versione italiana (SHA-1 05bb8e99…), verificati con
 * gli strumenti in tools/rom-analysis. Dettagli e stato di verifica: docs/MEMORY_MAP_IT.md.
 */
object MemoryMapIt {
    const val SPRITE_STATE_DATA_1 = 0xC100
    const val SPRITE_STATE_DATA_2 = 0xC200
    /** Oggetti attivabili (persone/oggetti che il gioco può nascondere): bit = nascosto, per indice globale. */
    const val TOGGLE_FLAGS = 0xD5AA
    /** Coppie (sprite, indice globale) della mappa corrente, terminate da 0xFF. */
    const val TOGGLE_LIST = 0xD5D2
    const val TILE_MAP = 0xC3A0
    const val OVERWORLD_MAP = 0xC6E8
    const val CURRENT_MENU_ITEM = 0xCC26
    /** 0 = il giocatore ha il controllo; diverso da 0 durante scene e movimenti automatici. */
    const val JOY_IGNORE = 0xCD6B
    /** Scorrimento delle liste (strumenti, PC): primo elemento visibile. */
    const val LIST_SCROLL_OFFSET = 0xCC36
    /** 0 = nessuna lotta, 1 = lotta con creatura selvatica, 2 = lotta con allenatore. */
    const val IS_IN_BATTLE = 0xD05B
    /** Creatura del giocatore in campo durante la lotta (battle_struct): mosse +8, livello +14, PS max +15, PP +25. */
    const val BATTLE_MON = 0xD018
    /** PS del Pokémon avversario in lotta (2 byte, big-endian; specie a 0xCFE9), verificato sul gioco vero (D-069). */
    const val ENEMY_MON_HP = 0xCFEA

    /** Tabella dei nomi delle mosse nella ROM italiana: 165 nomi chiusi da 0x50, a partire da questo offset. */
    const val ROM_MOVE_NAMES = 0xBC000
    const val NUM_MOVES = 165
    /** Nomi degli strumenti 0x01–0x61: elenco chiuso da 0x50 (banco 1). MN (macchine nascoste) da 0xC4, MT da 0xC9. */
    const val ROM_ITEM_NAMES = 0x45B8
    const val NUM_ITEM_NAMES = 0x61
    /** Statistiche di base: 151 voci da 28 byte in ordine di Pokédex; +19 = velocità di crescita (0–5). */
    const val ROM_BASE_STATS = 0x383DE
    const val BASE_STATS_SIZE = 28
    /** Numero del Pokédex per ogni indice interno (190 byte, 0 = nessuno). */
    const val ROM_POKEDEX_ORDER = 0x41043
    const val HM01 = 0xC4
    const val TM01 = 0xC9
    /** Nomi delle specie: 190 voci da 10 byte (riempite con 0x50), in ordine di indice interno (1 = RHYDON). */
    const val ROM_SPECIES_NAMES = 0xE8000
    const val SPECIES_NAME_LENGTH = 10
    const val NUM_SPECIES_INDEXES = 190
    /** Dati delle mosse nella ROM italiana: 165 voci da 6 byte (id, effetto, potenza, tipo, precisione, PP). */
    const val ROM_MOVE_DATA = 0x38000
    const val MOVE_DATA_SIZE = 6
    /** Tabella di puntatori (banco 0x09) ai nomi dei tipi, indici 0x00–0x1A. */
    const val ROM_TYPE_NAME_POINTERS = 0x27D63
    const val NUM_TYPE_IDS = 0x1B

    const val PLAYER_NAME = 0xD15C
    const val PARTY_COUNT = 0xD167
    const val PARTY_SPECIES = 0xD168
    const val PARTY_MONS = 0xD16F
    const val PARTY_MON_OT = 0xD277
    const val PARTY_MON_NICKS = 0xD2B9
    /** Borsa: numero di strumenti (max 20), poi coppie (strumento, quantità) chiuse da 0xFF. Verificata con la Pozione del PC. */
    /** ROM: tabella delle icone della squadra (voci da 6 byte) e tipo d'icona per n. del Pokédex (mezzo byte). */
    const val ROM_PARTY_ICON_GFX = 0x7188D
    /** ROM: tavolozza di ogni specie per n. del Pokédex (1 byte) e tavolozze a colori (4 colori RGB555 ciascuna). */
    const val ROM_MON_PALETTE_IDS = 0x72961
    const val ROM_CGB_PALETTES = 0x72B39
    /** ROM: puntatori alle voci del Pokédex per indice interno (banco 0x10). */
    const val ROM_DEX_ENTRY_POINTERS = 0x4050D
    const val ROM_PARTY_ICON_TYPES = 0x719FA
    const val BAG_COUNT = 0xD321
    const val BAG_ITEMS = 0xD322
    const val BAG_CAPACITY = 20
    /** Pokédex: 151 bit (19 byte) per "presi" e per "visti"; bit n-1 = numero n. Verificati con Pikachu ed Eevee. */
    const val POKEDEX_OWNED = 0xD2FB
    const val POKEDEX_SEEN = 0xD30E
    const val POKEDEX_BYTES = 19
    const val NUM_POKEDEX = 151
    const val PLAYER_MONEY = 0xD34B
    const val OPTIONS = 0xD359
    /** Tempo di gioco: ore DA45, (limite DA46), minuti DA47, secondi DA48, fotogrammi DA49. Verificato facendo passare 1 minuto. */
    const val PLAY_TIME_HOURS = 0xDA45
    const val PLAY_TIME_MINUTES = 0xDA47
    /** Medaglie ottenute: un bit per medaglia (US wObtainedBadges + 5). */
    const val BADGES = 0xD35A
    const val CUR_MAP = 0xD362
    const val Y_COORD = 0xD365
    const val X_COORD = 0xD366
    const val CUR_MAP_TILESET = 0xD36B
    const val CUR_MAP_HEIGHT = 0xD36C
    const val CUR_MAP_WIDTH = 0xD36D
    /** Set di tile della mappa corrente: banco ROM, puntatore ai blocchi (16 tile ciascuno), puntatore alla
     *  lista dei tile calpestabili (nel banco 1, chiusa da 0xFF). */
    const val TILESET_BANK = 0xD52F
    const val TILESET_BLOCKS_PTR = 0xD530
    const val TILESET_COLLISION_PTR = 0xD534
    /** Tile "bancone": si può parlare attraverso (fino a 3 valori, 0xFF = nessuno). */
    const val TILESET_TALKING_OVER_TILES = 0xD536
    /** Cartelli della mappa corrente: numero, poi coppie (Y, X) in passi. */
    const val NUM_SIGNS = 0xD4B4
    const val SIGN_COORDS = 0xD4B5

    const val PARTY_LENGTH = 6
    const val PARTY_STRUCT_LENGTH = 44
    const val NAME_LENGTH = 11

    /** Primo e ultimo indirizzo da copiare per leggere lotta, squadra, borsa, soldi e mappa in un colpo solo. */
    const val BLOCK_START = BATTLE_MON
    /** Articoli del negozio aperto (numero, poi codici, 0xFF in fondo) e prezzi nella ROM (3 byte BCD per strumento). */
    const val SHOP_LIST = 0xCF7F
    const val ROM_ITEM_PRICES = 0x4495
    /** Strumenti nel PC del giocatore (come la borsa: numero di voci, poi coppie codice-quantità, 0xFF in fondo). */
    const val PC_ITEM_COUNT = 0xD53E
    const val PC_ITEMS = 0xD53F
    const val PC_CAPACITY = 50
    const val BLOCK_END = PC_ITEMS + 2 * PC_CAPACITY + 1
    /**
     * Box Pokémon attuale (D-058, verificato sul gioco vero: 4 byte dopo la versione inglese): numero, specie (0xFF in
     * fondo), strutture da 33 byte (specie +0, livello +3), allenatori e soprannomi da 11 caratteri.
     */
    const val BOX_COUNT = 0xDA84
    const val BOX_SPECIES = 0xDA85
    const val BOX_MONS = 0xDA9A
    const val BOX_STRUCT_LENGTH = 33
    const val BOX_MON_NICKS = 0xDE0A
    const val BOX_CAPACITY = 20
    const val BOX_BLOCK_SIZE = BOX_MON_NICKS + BOX_CAPACITY * NAME_LENGTH - BOX_COUNT
}
