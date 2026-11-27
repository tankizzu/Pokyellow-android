#include <stdio.h>
#include <stdlib.h>
/*
 * Vista estesa a tutto schermo (D-015, D-026).
 *
 * Il gioco disegna solo 160×144 pixel. Qui si disegna la mappa anche sopra e sotto l'inquadratura,
 * con gli stessi tile, palette e colori del gioco, allineata al pixel:
 * 1. si costruiscono i tile della mappa corrente dalla memoria (mappa dei blocchi con il bordo di 3 blocchi)
 *    e dalla ROM dell'utente (blocchi da 4×4 tile del set corrente);
 * 2. si trova dove cade l'inquadratura confrontando lo sfondo in memoria video (con lo scorrimento SCX/SCY)
 *    con i tile della mappa intorno alla posizione del giocatore;
 * 3. si disegnano le righe mancanti leggendo i pixel dei tile dalla memoria video e i colori dalle palette
 *    a colori del gioco, convertiti come fa il motore; al centro si copia l'immagine originale.
 * 4. si aggiungono le persone e gli oggetti fuori dall'inquadratura (e le parti tagliate di quelli sul bordo):
 *    quelli visibili dalla memoria degli sprite (OAM), quelli che il gioco non disegna perché fuori schermo
 *    dalla tabella degli sprite (posizione, direzione, grafica già in memoria video). Gli oggetti che il gioco
 *    ha nascosto (raccolti, persone non presenti) restano nascosti: si legge la stessa lista che usa il gioco.
 * Se lo sfondo non è la mappa (lotta, schermate del titolo, menu a tutto schermo) la vista estesa si spegne.
 * Solo lettura: non scrive mai nella memoria del gioco.
 */
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include "gb.h"
#include "extended_view.h"

/* Indirizzi della versione italiana (vedi docs/MEMORY_MAP_IT.md). */
#define OVERWORLD_MAP 0xC6E8
#define CUR_MAP       0xD362
#define Y_COORD       0xD365
#define X_COORD       0xD366
#define MAP_HEIGHT    0xD36C
#define MAP_WIDTH     0xD36D
#define TILESET_BANK  0xD52F
#define BLOCKS_PTR    0xD530
#define SPRITE_DATA1  0xC100   /* 16 sprite × 16 byte: immagine, posizione sullo schermo, direzione */
#define SPRITE_DATA2  0xC200   /* 16 sprite × 16 byte: posizione sulla mappa (+4), erba, grafica */
#define TOGGLE_FLAGS  0xD5AA   /* bit = oggetto nascosto (indice globale) */
#define TOGGLE_LIST   0xD5D2   /* coppie (sprite, indice globale) della mappa corrente, fine 0xFF */

#define MAP_CONNECTIONS 0xD374  /* bit 3 = nord, bit 2 = sud; intestazioni da 11 byte a 0xD375 (nord) e 0xD380 (sud) */
#define NORTH_HEADER    0xD375
#define SOUTH_HEADER    0xD380
#define WEST_HEADER     0xD38B
#define EAST_HEADER     0xD396
/* Tavolozze a colori del gioco nella ROM italiana (4 colori RGB555 per voce): città = numero del luogo + 1, strade = 0. */
#define CGB_PALETTES    0x72B39

#define FRAME_W 160
#define FRAME_H 144

typedef struct {
    const uint8_t *rom;
    size_t rom_size;
    uint8_t map[1300];
    int w, h;               /* in blocchi, senza bordo */
    size_t blockset;        /* offset nella ROM */
    /* Luoghi collegati a nord e a sud (D-059): oltre il bordo di 3 blocchi si leggono i loro blocchi dalla ROM. */
    struct { bool ok; size_t src; int width, len, row0, col0; } conn[2];   /* 0 = nord, 1 = sud */
    /* Luogo collegato in ogni direzione (0 nord, 1 sud, 2 ovest, 3 est; −1 se nessuno) e dove sta la sua striscia. */
    int nb_map[4], nb_from[4], nb_len[4];
} MapTiles;

static uint8_t rd(GB_gameboy_t *gb, uint16_t a) { return GB_safe_read_memory(gb, a); }
static int floordiv(int a, int b) { return (a >= 0) ? a / b : -((-a + b - 1) / b); }
static int posmod(int a, int b) { int m = a % b; return m < 0 ? m + b : m; }

static bool load_map(GB_gameboy_t *gb, MapTiles *mt) {
    mt->h = rd(gb, MAP_HEIGHT);
    mt->w = rd(gb, MAP_WIDTH);
    if (mt->w == 0 || mt->h == 0 || (mt->w + 6) * (mt->h + 6) > 1300) return false;
    int bank = rd(gb, TILESET_BANK);
    int ptr = rd(gb, BLOCKS_PTR) | (rd(gb, BLOCKS_PTR + 1) << 8);
    if (ptr < 0x4000 || ptr > 0x7FFF) return false;
    mt->rom = GB_get_direct_access(gb, GB_DIRECT_ACCESS_ROM, &mt->rom_size, NULL);
    if (!mt->rom) return false;
    mt->blockset = (size_t)bank * 0x4000 + (size_t)(ptr - 0x4000);
    int n = (mt->w + 6) * (mt->h + 6);
    for (int i = 0; i < n; i++) mt->map[i] = rd(gb, (uint16_t)(OVERWORLD_MAP + i));
    if (mt->blockset + 256 * 16 > mt->rom_size) return false;
    /* Collegamenti: la striscia del vicino (3 file di blocchi) è già nel bordo in memoria; il banco della ROM dove
     * stanno i blocchi del vicino si trova cercando quella stessa striscia (l'intestazione non lo dice). */
    uint8_t conns = rd(gb, MAP_CONNECTIONS);
    int bw = mt->w + 6, bh = mt->h + 6;
    static const uint16_t HEADERS[4] = { NORTH_HEADER, SOUTH_HEADER, WEST_HEADER, EAST_HEADER };
    for (int d = 0; d < 4; d++) {
        mt->nb_map[d] = -1;
        if (!(conns & (0x08 >> d))) continue;
        int dest = (rd(gb, HEADERS[d] + 3) | (rd(gb, HEADERS[d] + 4) << 8)) - OVERWORLD_MAP;
        if (dest < 0 || dest >= bw * bh) continue;
        mt->nb_map[d] = rd(gb, HEADERS[d]);
        mt->nb_from[d] = d < 2 ? dest % bw : dest / bw;      /* colonna (nord/sud) o riga (ovest/est) d'inizio */
        mt->nb_len[d] = rd(gb, HEADERS[d] + 5);
    }
    for (int d = 0; d < 2; d++) {
        mt->conn[d].ok = false;
        if (!(conns & (d == 0 ? 0x08 : 0x04))) continue;
        uint16_t h = d == 0 ? NORTH_HEADER : SOUTH_HEADER;
        int src = rd(gb, h + 1) | (rd(gb, h + 2) << 8);
        int dest = (rd(gb, h + 3) | (rd(gb, h + 4) << 8)) - OVERWORLD_MAP;
        int len = rd(gb, h + 5), width = rd(gb, h + 6);
        if (src < 0x4000 || src > 0x7FFF || dest < 0 || len <= 0 || width <= 0 || width > 64) continue;
        int row0 = dest / bw, col0 = dest % bw;
        if (col0 + len > bw || (d == 0 && row0 != 0) || (d == 1 && row0 != bh - 3)) continue;
        for (size_t bank = 1; bank * 0x4000 < mt->rom_size; bank++) {
            size_t base = bank * 0x4000 + (size_t)(src - 0x4000);
            if (base + 3 * (size_t)width + (size_t)len > mt->rom_size) break;
            bool same = true;
            for (int r = 0; r < 3 && same; r++)
                for (int c = 0; c < len && same; c++)
                    same = mt->rom[base + (size_t)r * width + c] == mt->map[(row0 + r) * bw + col0 + c];
            if (same) {
                mt->conn[d].ok = true; mt->conn[d].src = base; mt->conn[d].width = width; mt->conn[d].len = len;
                mt->conn[d].row0 = row0; mt->conn[d].col0 = col0;
                break;
            }
        }
    }
    return true;
}

/* Tavolozza a colori di un luogo all'aperto (come fa il gioco): città 0–10 → numero + 1, strade 12–36 → 0. */
static int map_palette(int map) {
    if (map >= 0 && map <= 0x0A) return map + 1;
    if (map >= 0x0C && map <= 0x24) return 0;
    return -1;
}

/* Luogo a cui appartiene il tile (tx, ty): quello attuale o uno collegato (strisce del bordo e oltre). */
static int map_of_tile(const MapTiles *mt, int cur, int tx, int ty) {
    int bw = mt->w + 6, bh = mt->h + 6;
    int bx = floordiv(tx, 4), by = floordiv(ty, 4);
    if (by < 3 && mt->nb_map[0] >= 0 && bx >= mt->nb_from[0] && bx < mt->nb_from[0] + mt->nb_len[0]) return mt->nb_map[0];
    if (by >= bh - 3 && mt->nb_map[1] >= 0 && bx >= mt->nb_from[1] && bx < mt->nb_from[1] + mt->nb_len[1]) return mt->nb_map[1];
    if (bx < 3 && mt->nb_map[2] >= 0 && by >= mt->nb_from[2] && by < mt->nb_from[2] + mt->nb_len[2]) return mt->nb_map[2];
    if (bx >= bw - 3 && mt->nb_map[3] >= 0 && by >= mt->nb_from[3] && by < mt->nb_from[3] + mt->nb_len[3]) return mt->nb_map[3];
    return cur;
}

/* Colore schiarito (t > 0, verso il bianco) o scurito (t < 0, verso il nero) della stessa misura della dissolvenza. */
static uint32_t fade_col(uint32_t c, float t) {
    if (t == 0) return c;
    uint32_t out = c & 0xFF000000u;
    for (int sh = 0; sh <= 16; sh += 8) {
        int v = (c >> sh) & 255;
        v = t > 0 ? v + (int)((255 - v) * t + 0.5f) : (int)(v * (1 + t) + 0.5f);
        out |= (uint32_t)(v < 0 ? 0 : v > 255 ? 255 : v) << sh;
    }
    return out;
}

static const uint32_t *place_colors_for(GB_gameboy_t *gb, const MapTiles *mt, uint32_t pc[64][4], bool known[64], int m) {
    if (!known[m]) {
        /* D-069: tutti i luoghi all'aperto con i colori delle strade (Biancavilla e le città come la strada):
         * nessun cambio di colore ai confini. */
        size_t o = CGB_PALETTES;   /* voce 0 = strade */
        for (int c = 0; c < 4; c++) {
            uint16_t rgb = mt->rom[o + 2 * c] | (mt->rom[o + 2 * c + 1] << 8);
            pc[m][c] = GB_convert_rgb15(gb, rgb & 0x7FFF, false);
        }
        known[m] = true;
    }
    return pc[m];
}

/* Blocco del luogo collegato sopra (by < 0) o sotto (by >= bh) il bordo; −1 se non c'è. */
static int connected_block(const MapTiles *mt, int bx, int by) {
    int bh = mt->h + 6;
    int d = by < 0 ? 0 : 1;
    if (!mt->conn[d].ok) return -1;
    int k = by - mt->conn[d].row0;            /* file rispetto alla prima della striscia */
    if (k < -5 || k > 7) return -1;           /* oltre qualche blocco si resta sul bordo */
    if (d == 1 && by < bh) return -1;
    int c = bx - mt->conn[d].col0;
    if (c < 0 || c >= mt->conn[d].len) return -1;
    long a = (long)mt->conn[d].src + (long)k * mt->conn[d].width + c;
    size_t bank_start = mt->conn[d].src & ~(size_t)0x3FFF;
    if (a < (long)bank_start || a >= (long)(bank_start + 0x4000) || (size_t)a >= mt->rom_size) return -1;
    return mt->rom[a];
}

/* Tile della mappa alla posizione (tx, ty) in tile, contando anche il bordo di 3 blocchi (12 tile). */
static uint8_t map_tile(const MapTiles *mt, int tx, int ty) {
    int bw = mt->w + 6, bh = mt->h + 6;
    int bx = floordiv(tx, 4), by = floordiv(ty, 4);
    int block;
    if (bx >= 0 && by >= 0 && bx < bw && by < bh) block = mt->map[by * bw + bx];
    else {
        block = (by < 0 || by >= bh) && bx >= 0 && bx < bw ? connected_block(mt, bx, by) : -1;
        /* senza luogo collegato: il blocco del bordo della stessa colonna (riga più vicina), come fa il gioco */
        if (block < 0) {
            int cx = bx < 0 ? 0 : (bx >= bw ? bw - 1 : bx), cy = by < 0 ? 0 : (by >= bh ? bh - 1 : by);
            block = mt->map[cy * bw + cx];
        }
    }
    return mt->rom[mt->blockset + block * 16 + posmod(ty, 4) * 4 + posmod(tx, 4)];
}

/* true se il gioco ha nascosto lo sprite (oggetto raccolto, persona non presente): stessa regola del gioco. */
static bool toggled_off(GB_gameboy_t *gb, int slot) {
    for (int i = 0; i < 32; i++) {
        uint8_t s = rd(gb, (uint16_t)(TOGGLE_LIST + 2 * i));
        if (s == 0xFF) return false;
        if (s == slot) {
            uint8_t g = rd(gb, (uint16_t)(TOGGLE_LIST + 2 * i + 1));
            return (rd(gb, (uint16_t)(TOGGLE_FLAGS + (g >> 3))) >> (g & 7)) & 1;
        }
    }
    return false;
}

/* Un tile 8×8 di sprite, solo nelle righe fuori dall'inquadratura originale (il centro resta l'immagine del gioco). */
static int draw_obj_tile_in(uint32_t *out, int out_h, int off_top, const EVState *st, const uint8_t *data,
                             int tile, int sx, int sy, uint8_t attr, const uint32_t pal[4], bool inside);
static void draw_obj_tile(uint32_t *out, int out_h, int off_top, const EVState *st, const uint8_t *data,
                          int tile, int sx, int sy, uint8_t attr, const uint32_t pal[4]) {
    draw_obj_tile_in(out, out_h, off_top, st, data, tile, sx, sy, attr, pal, false);
}
/* [inside]: anche dentro l'inquadratura (persone che il gioco non disegna perché per lui sono fuori, D-100). */
static int draw_obj_tile_in(uint32_t *out, int out_h, int off_top, const EVState *st, const uint8_t *data,
                             int tile, int sx, int sy, uint8_t attr, const uint32_t pal[4], bool inside) {
    int written = 0;
    for (int py = 0; py < 8; py++) {
        int row = off_top + sy + py;
        bool in_frame = row >= off_top && row < off_top + FRAME_H;
        if (row < 0 || row >= out_h || (in_frame && !inside)) continue;
        int yy = (attr & 0x40) ? 7 - py : py;
        uint8_t lo = data[tile * 16 + yy * 2], hi = data[tile * 16 + yy * 2 + 1];
        for (int px = 0; px < 8; px++) {
            int col = sx + px;
            if (col < 0 || col >= FRAME_W) continue;
            int xx = (attr & 0x20) ? px : 7 - px;
            int c = (((hi >> xx) & 1) << 1) | ((lo >> xx) & 1);
            if (!c) continue;
            if (in_frame) { if ((attr & 0x80) && out[(size_t)row * FRAME_W + col] != st->bg0) continue; }
            else {
                uint8_t bg = st->bgidx[(size_t)row * FRAME_W + col];
                if ((bg & 3) && ((attr & 0x80) || (bg & 0x80))) continue;   /* dietro l'erba o dietro lo sfondo */
            }
            out[(size_t)row * FRAME_W + col] = pal[c];
            written++;
        }
    }
    return written;
}

/* Posizione a 8 bit sullo schermo (che si "avvolge" ogni 256 pixel) ricondotta a quella vicina alla stima. */
static int unwrap(int pix8, int estimate) {
    int k = floordiv(estimate - pix8 + 128, 256);
    return pix8 + 256 * k;
}

/* Disposizione dei 4 tile di una persona ferma: per direzione (giù, su, sinistra, destra) → (dy, dx, tile). */
static const int8_t STANDING[4][4][3] = {
    { {0, 0, 0}, {0, 8, 1}, {8, 0, 2}, {8, 8, 3} },
    { {0, 0, 4}, {0, 8, 5}, {8, 0, 6}, {8, 8, 7} },
    { {0, 0, 8}, {0, 8, 9}, {8, 0, 10}, {8, 8, 11} },
    { {0, 8, 8}, {0, 0, 9}, {8, 8, 10}, {8, 0, 11} },   /* destra = sinistra ribaltata */
};

static void draw_sprites(GB_gameboy_t *gb, uint32_t *out, int out_h, int off_top, uint8_t lcdc,
                         const uint8_t *bank0, const uint8_t *bank1, EVState *st) {
    if (!(lcdc & 0x02) || (lcdc & 0x04)) return;   /* sprite spenti o da 8×16 (non usati sulla mappa) */
    size_t oam_size = 0, obp_size = 0;
    const uint8_t *oam = GB_get_direct_access(gb, GB_DIRECT_ACCESS_OAM, &oam_size, NULL);
    const uint8_t *obp = GB_get_direct_access(gb, GB_DIRECT_ACCESS_OBP, &obp_size, NULL);
    if (!oam || oam_size < 160 || !obp || obp_size < 64) return;
    uint32_t pal[8][4];
    for (int p = 0; p < 8; p++) for (int c = 0; c < 4; c++) {
        uint16_t rgb = obp[p * 8 + c * 2] | (obp[p * 8 + c * 2 + 1] << 8);
        pal[p][c] = GB_convert_rgb15(gb, rgb & 0x7FFF, false);
    }

    /* Sprite visibili: si imparano gli attributi e si completano le parti tagliate dal bordo. */
    int visible = 0;
    for (int i = 0; i < 40; i++) {
        const uint8_t *o = oam + i * 4;
        if (o[0] < 9 || o[0] >= 160 || o[1] == 0 || o[1] >= 168) continue;
        visible++;
        st->obj_attr_by_tile[o[2]] = o[3];
        st->obj_attr_known[o[2]] = true;
    }
    if (visible == 0) { memset(st->drawn, 0, sizeof(st->drawn)); return; }   /* il gioco non sta mostrando gli sprite */
    st->people_ok = 1; st->people_top = off_top;

    /* Sprite che il gioco non disegna perché fuori dall'inquadratura (persone, oggetti a terra). */
    int px = rd(gb, X_COORD), py = rd(gb, Y_COORD);
    for (int slot = 1; slot < 15; slot++) {   /* 0 = giocatore, 15 = compagno che segue: sempre vicini */
        uint16_t d1 = (uint16_t)(SPRITE_DATA1 + slot * 16), d2 = (uint16_t)(SPRITE_DATA2 + slot * 16);
        int *pp = st->people[slot];
        bool was_drawn = st->drawn[slot];
        st->drawn[slot] = false;
        if (!rd(gb, d1)) continue;
        int base = rd(gb, (uint16_t)(d2 + 0x0E));
        bool onscreen = rd(gb, (uint16_t)(d1 + 2)) != 0xFF;
        pp[0] = 1;
        if (toggled_off(gb, slot) || base < 1 || base > 0x10) continue;
        int n = base - 1;
        int dir = (n << 4) >= 0xA0 ? 0 : (rd(gb, (uint16_t)(d1 + 9)) >> 2) & 3;
        int tile_base = n == 0x0B ? 0x7C : n * 12;
        /* Posizione dalla mappa (D-100), allineata come la mappa disegnata (base_x/base_y); quella sullo schermo del gioco
         * (8 bit) si usa solo se è vicina (passi in corso), altrimenti è vecchia e la persona finiva fuori posto o spariva. */
        int est_y = ((rd(gb, (uint16_t)(d2 + 4)) - 4) * 2 + 12) * 8 - st->base_y - 4;
        int est_x = ((rd(gb, (uint16_t)(d2 + 5)) - 4) * 2 + 12) * 8 - st->base_x;
        int sy = unwrap(rd(gb, (uint16_t)(d1 + 4)), est_y);
        int sx = unwrap(rd(gb, (uint16_t)(d1 + 6)), est_x);
        if (abs(sy - est_y) > 16) sy = est_y;
        if (abs(sx - est_x) > 16) sx = est_x;
        (void)px; (void)py;
        pp[0] = onscreen ? 2 : 3; pp[1] = sx; pp[2] = sy;
        /* Passaggio dal bordo (D-119): appena una persona a cavallo del bordo diventa "dentro" per il gioco, per un paio di
         * fotogrammi l'OAM non l'ha ancora e non la disegnava nessuno (Poké Ball del Monte Luna). Se nell'OAM non c'è nulla
         * nel suo riquadro, la si disegna ancora qui. */
        /* Lo stesso vale appena chiuso un riquadro di testo (D-121): il gioco la rimette "dentro" prima di averla nell'OAM. */
        if (onscreen && (sy < 0 || sy + 16 > FRAME_H || st->boxed[slot] > 0 || was_drawn)) {
            bool in_oam = false;
            for (int i = 0; i < 40 && !in_oam; i++) {
                const uint8_t *o = oam + i * 4;
                int oy = o[0] - 16, ox = o[1] - 8;
                if (o[1] != 0 && o[1] < 168 && o[0] != 0 && o[0] < 160 && ox > sx - 8 && ox < sx + 16 && oy > sy - 8 && oy < sy + 16) in_oam = true;
            }
            if (!in_oam) { onscreen = false; pp[0] = 4; }
        }
        if (onscreen) { if (st->boxed[slot] > 0) st->boxed[slot]--; continue; }
        /* Tutta dentro lo schermo del gioco ma per il gioco "fuori" (D-121): succede subito dopo una scala, prima che il
         * gioco aggiorni le persone; lì decide il gioco, che non la mostra ancora. Disegnarla la faceva comparire per un
         * fotogramma, sparire per due e ricomparire (Poké Ball del Monte Luna). */
        if (pp[0] == 3 && sy >= 0 && sy + 16 <= FRAME_H && sx >= 0 && sx + 16 <= FRAME_W) {
            /* Ma se il gioco l'ha nascosta perché è sotto un riquadro di testo (tile del riquadro, da 0x60 in su, nella
             * mappa dello schermo C3A0), nell'app quel riquadro non copre la mappa: la si disegna (palestra di Celeste). */
            bool under_box = false;
            for (int r = sy / 8; r <= (sy + 15) / 8 && r < 18; r++)
                for (int c = sx / 8; c <= (sx + 15) / 8 && c < 20; c++)
                    if (rd(gb, (uint16_t)(0xC3A0 + r * 20 + c)) >= 0x60) under_box = true;
            /* alla chiusura del riquadro il gioco la rimette "dentro" qualche fotogramma dopo: nel frattempo resta all'app */
            if (under_box) st->boxed[slot] = 8; else if (st->boxed[slot] > 0) { st->boxed[slot]--; under_box = true; }
            /* e se l'app la stava già disegnando (entra dal bordo con lo scorrimento) continua: niente buco finché la prende il gioco */
            if (!under_box && !was_drawn) continue;
        }
        if (sy + 16 <= -off_top || sy >= FRAME_H + (out_h - FRAME_H - off_top) || sx + 16 <= 0 || sx >= FRAME_W) continue;
        st->drawn[slot] = true;
        uint8_t grass = rd(gb, (uint16_t)(d2 + 7)) & 0x80;
        for (int t = 0; t < 4; t++) {
            int tile = tile_base + STANDING[dir][t][2];
            uint8_t learned = st->obj_attr_known[tile] ? (st->obj_attr_by_tile[tile] & 0x0F) : 0;
            uint8_t attr = (uint8_t)(learned | (dir == 3 ? 0x20 : 0) | (t >= 2 ? grass : 0));
            pp[3] += draw_obj_tile_in(out, out_h, off_top, st, (attr & 0x08) ? bank1 : bank0, tile,
                             sx + STANDING[dir][t][1], sy + STANDING[dir][t][0], attr, pal[attr & 7], true);
        }
    }

    /* Sprite visibili tagliati dal bordo superiore o inferiore: parti mancanti (prima i meno prioritari). */
    for (int i = 39; i >= 0; i--) {
        const uint8_t *o = oam + i * 4;
        if (o[1] == 0 || o[1] >= 168) continue;
        bool usable = o[0] >= 9 && o[0] < 160;
        /* Metà di una persona appena fuori dal bordo (Pikachu che segue, D-067): il gioco la mette a una y che
         * sembra "nascosta" (160–167 sotto, 1–8 sopra); vale se nell'OAM c'è l'altra metà attaccata e visibile. */
        if (!usable && ((o[0] >= 160 && o[0] < 168) || (o[0] >= 1 && o[0] < 9))) {
            int partner = o[0] >= 160 ? o[0] - 8 : o[0] + 8;
            for (int j = 0; j < 40 && !usable; j++) {
                const uint8_t *q = oam + j * 4;
                if (j != i && q[1] == o[1] && q[0] == partner && q[0] >= 9 && q[0] < 160) usable = true;
            }
        }
        if (!usable) continue;
        int y = o[0] - 16, x = o[1] - 8;
        if (y >= 0 && y + 8 <= FRAME_H) continue;
        draw_obj_tile(out, out_h, off_top, st, (o[3] & 0x08) ? bank1 : bank0, o[2], x, y, o[3], pal[o[3] & 7]);
    }
}

void ev_reset(EVState *st) { memset(st, 0, sizeof(*st)); st->map_id = -1; st->last_outdoor = -1; st->map_from_row = 1000; }

/*
 * Schermata del titolo a tutto schermo (D-069): il logo che scende dall'alto è già nella memoria video sopra la parte
 * visibile (il gioco fa scorrere lo sfondo con SCY), quindi si disegna anche nelle righe sopra l'inquadratura. A logo
 * fermo la scritta (C) GAME FREAK va in basso, alla riga 41 dello schermo (mockup del committente).
 * Il titolo si riconosce dalla riga 17 della mappa dello sfondo (lettere della scritta: E0 E1 E2).
 */
static int title_render(GB_gameboy_t *gb, const uint32_t *frame, uint32_t *out, int out_h, uint8_t lcdc) {
    size_t vram_size = 0, bgp_size = 0;
    const uint8_t *vram = GB_get_direct_access(gb, GB_DIRECT_ACCESS_VRAM, &vram_size, NULL);
    const uint8_t *bgp = GB_get_direct_access(gb, GB_DIRECT_ACCESS_BGP, &bgp_size, NULL);
    if (!vram || vram_size < 0x4000 || !bgp || bgp_size < 64) return 0;
    const uint8_t *bank0 = vram, *bank1 = vram + 0x2000;
    int bg_map = (lcdc & 0x08) ? 0x1C00 : 0x1800;
    if (bank0[bg_map + 17 * 32 + 2] != 0xE0 || bank0[bg_map + 17 * 32 + 3] != 0xE1 || bank0[bg_map + 17 * 32 + 4] != 0xE2) return 0;
    if (rd(gb, 0xC3A0) == 0x79) return 0;   /* un riquadro (menu iniziale) sopra: non è più il titolo */
    int scy = rd(gb, 0xFF42), scx = rd(gb, 0xFF43);
    bool signed_tiles = !(lcdc & 0x10);
    uint32_t colors[8][4];
    for (int p = 0; p < 8; p++) for (int c = 0; c < 4; c++) {
        uint16_t rgb = bgp[p * 8 + c * 2] | (bgp[p * 8 + c * 2 + 1] << 8);
        colors[p][c] = GB_convert_rgb15(gb, rgb & 0x7FFF, false);
    }
    int top = (out_h - FRAME_H) / 2;
    uint32_t paper = frame[0];
    for (int row = 0; row < out_h; row++) {
        uint32_t *dst = out + (size_t)row * FRAME_W;
        int f = row - top;
        if (f >= 0 && f < FRAME_H) { memcpy(dst, frame + (size_t)f * FRAME_W, FRAME_W * sizeof(uint32_t)); continue; }
        int y = scy + f;                       /* riga dello sfondo (sopra: righe del logo non ancora visibili) */
        if (f >= FRAME_H || y < 0 || y >= 256) { for (int x = 0; x < FRAME_W; x++) dst[x] = paper; continue; }
        for (int col = 0; col < FRAME_W; col++) {
            int bx = (scx + col) & 255;
            int idx = bg_map + (y >> 3) * 32 + (bx >> 3);
            uint8_t tile = bank0[idx], attr = bank1[idx];
            const uint8_t *data = (attr & 0x08) ? bank1 : bank0;
            int yy = (attr & 0x40) ? 7 - (y & 7) : (y & 7);
            int xx = (attr & 0x20) ? (bx & 7) : 7 - (bx & 7);
            int base = signed_tiles ? 0x1000 + (int8_t)tile * 16 : tile * 16;
            uint8_t lo = data[base + yy * 2], hi = data[base + yy * 2 + 1];
            int c = (((hi >> xx) & 1) << 1) | ((lo >> xx) & 1);
            dst[col] = colors[attr & 7][c];
        }
    }
    /* scritta (C) dalla riga 17 del gioco alla riga 41 dello schermo, una casella più a sinistra: da subito, anche
     * mentre il logo scende (la riga della scritta non scorre con il logo) */
    int dest = 41 * 8;
    if (dest + 8 <= out_h) {
        for (int y = 0; y < 8; y++) {
            uint32_t *from = out + (size_t)(top + 136 + y) * FRAME_W;
            uint32_t *to = out + (size_t)(dest + y) * FRAME_W;
            uint32_t line[FRAME_W];
            memcpy(line, from, sizeof(line));
            for (int x = 0; x < FRAME_W; x++) { from[x] = paper; }
            for (int x = 0; x < FRAME_W; x++) to[x] = (x + 8 < FRAME_W) ? line[x + 8] : paper;
        }
    }
    return 1;
}

int ev_render(GB_gameboy_t *gb, const uint32_t *frame, uint32_t *out, int out_h, int frame_top, EVState *st) {
    st->people_ok = 0; memset(st->people, 0, sizeof(st->people));
    if (out_h < FRAME_H) return 0;
    int off_top = (frame_top < 0 || frame_top > out_h - FRAME_H) ? (out_h - FRAME_H) / 2 : frame_top;
    uint8_t lcdc = rd(gb, 0xFF40);
    if (!(lcdc & 0x80)) { st->valid = false; return 0; }
    if (title_render(gb, frame, out, out_h, lcdc)) { st->valid = false; return 2; }   /* 2 = titolo */

    MapTiles mt;
    if (!load_map(gb, &mt)) { st->valid = false; return 0; }
    int map_id = rd(gb, CUR_MAP);
    if (map_id != st->map_id) {
        memset(st->attr_known, 0, sizeof(st->attr_known));
        memset(st->boxed, 0, sizeof(st->boxed));
        memset(st->drawn, 0, sizeof(st->drawn));
        memset(st->obj_attr_known, 0, sizeof(st->obj_attr_known));
        st->map_id = map_id;
    }

    size_t vram_size = 0;
    const uint8_t *vram = GB_get_direct_access(gb, GB_DIRECT_ACCESS_VRAM, &vram_size, NULL);
    size_t bgp_size = 0;
    const uint8_t *bgp = GB_get_direct_access(gb, GB_DIRECT_ACCESS_BGP, &bgp_size, NULL);
    if (!vram || vram_size < 0x4000 || !bgp || bgp_size < 64) { st->valid = false; return 0; }
    const uint8_t *bank0 = vram, *bank1 = vram + 0x2000;

    int scy = rd(gb, 0xFF42), scx = rd(gb, 0xFF43);
    int bg_map = (lcdc & 0x08) ? 0x1C00 : 0x1800;

    /* 2. Allineamento: confronto dello sfondo visibile (20×18 tile) con la mappa intorno al giocatore. */
    int est_tx = rd(gb, X_COORD) * 2 + 12 - 8;
    int est_ty = rd(gb, Y_COORD) * 2 + 12 - 8;
    /* A parità di punteggio vince lo spostamento più vicino alla stima (D-101): nelle stanze piccole circondate di nero
     * molti spostamenti combaciano uguale e prima vinceva il primo provato (riga della stanza fuori posto). */
    /* Righe coperte da un riquadro di testo del gioco (bordo nella colonna 0): non contano (D-101), altrimenti durante i
     * dialoghi l'allineamento sbagliava di qualche casella nelle stanze piccole. */
    bool skip_row[18] = {false};
    int rows_used = 18;
    for (int j = 0; j < 18; j++) {
        uint8_t v = bank0[bg_map + (((scy >> 3) + j) & 31) * 32 + ((scx >> 3) & 31)];
        if (v >= 0x79 && v <= 0x7E) { skip_row[j] = true; rows_used--; }
    }
    if (rows_used < 6) { for (int j = 0; j < 18; j++) skip_row[j] = false; rows_used = 18; }
    int best = -1, best_tx = 0, best_ty = 0, best_d = 99;
    /* 984 (D-122): fermo nello stesso posto, a parità di punteggio resta lo spostamento del fotogramma prima. Durante un
     * dialogo le righe coperte non contano e in una stanza piccola circondata di nero più spostamenti combaciano uguale:
     * la parte sotto il riquadro (disegnata dalla mappa) finiva 16 pixel più in là (casa di Bill, Market). */
    bool keep = st->al_ok && st->al_map == rd(gb, CUR_MAP) && st->al_tx == est_tx && st->al_ty == est_ty;
    bool best_prev = false;
    for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
        int score = 0;
        for (int j = 0; j < 18; j++) { if (skip_row[j]) continue; for (int i = 0; i < 20; i++) {
            uint8_t v = bank0[bg_map + (((scy >> 3) + j) & 31) * 32 + (((scx >> 3) + i) & 31)];
            if (v == map_tile(&mt, est_tx + dx + i, est_ty + dy + j)) score++;
        } }
        int d = abs(dx) + abs(dy);
        bool is_prev = keep && dx == st->al_dx && dy == st->al_dy;
        if (score > best || (score == best && (is_prev || (!best_prev && d < best_d)))) {
            best = score; best_tx = est_tx + dx; best_ty = est_ty + dy; best_d = d; best_prev = is_prev;
        }
    }
    st->dbg_score = best; st->dbg_dx = best_tx - est_tx; st->dbg_dy = best_ty - est_ty; st->dbg_rows = rows_used;
    st->al_ok = false;
    if (best * 100 < 83 * 20 * rows_used) { st->valid = false; return 0; }   /* meno dell'83%: lo sfondo non è la mappa */
    st->al_ok = true; st->al_map = rd(gb, CUR_MAP); st->al_tx = est_tx; st->al_ty = est_ty;
    st->al_dx = best_tx - est_tx; st->al_dy = best_ty - est_ty;

    /* Finestra a tutto schermo (riquadri del gioco): se mostra un menu a schermo pieno e non la mappa, niente estensione. */
    if ((lcdc & 0x20) && rd(gb, 0xFF4A) == 0 && rd(gb, 0xFF4B) <= 7) {
        int win_map = (lcdc & 0x40) ? 0x1C00 : 0x1800, same = 0;
        for (int j = 0; j < 12; j++) for (int i = 0; i < 20; i++)
            if (bank0[win_map + j * 32 + i] == map_tile(&mt, best_tx + i, best_ty + j)) same++;
        if (same < 96) { st->valid = false; return 0; }   /* meno del 40% delle prime 12 righe */
    }

    st->valid = true;
    st->base_x = best_tx * 8 + (scx & 7);
    st->base_y = best_ty * 8 + (scy & 7);

    /* Attributi (palette, banco, ribaltamenti) imparati dai tile visibili, per tipo di tile. */
    int pal_count[8] = {0};
    for (int j = 0; j < 18; j++) for (int i = 0; i < 20; i++) {
        int idx = bg_map + (((scy >> 3) + j) & 31) * 32 + (((scx >> 3) + i) & 31);
        uint8_t tile = bank0[idx], attr = bank1[idx];
        st->attr_by_tile[tile] = attr;
        st->attr_known[tile] = true;
        pal_count[attr & 7]++;
    }
    int fallback = 0;
    for (int p = 1; p < 8; p++) if (pal_count[p] > pal_count[fallback]) fallback = p;

    uint32_t colors[8][4];
    for (int p = 0; p < 8; p++) for (int c = 0; c < 4; c++) {
        uint16_t rgb = bgp[p * 8 + c * 2] | (bgp[p * 8 + c * 2 + 1] << 8);
        colors[p][c] = GB_convert_rgb15(gb, rgb & 0x7FFF, false);
    }
    bool signed_tiles = !(lcdc & 0x10);

    /* Colori per luogo (D-060): ogni luogo all'aperto si disegna sempre con la sua tavolozza, anche quando si vede da
     * quello accanto; così passando il confine i colori non cambiano di colpo. Valgono i colori 1 e 2 (0 e 3, bianco e
     * nero, sono uguali per tutti). */
    uint32_t place_colors[64][4];
    bool place_known[64] = {false};
    int cur_pal = map_palette(map_id);
    /* Porta (D-090): entrando in un edificio il gioco cambia subito il luogo, ma per qualche fotogramma si vede ancora
     * fuori (dissolvenza) coi colori originali del luogo di fuori. Si continua a ricolorare come il luogo di prima. */
    /* Solo finché i blocchi caricati sono ancora quelli di fuori (stesse misure): dentro gli edifici niente. */
    if (cur_pal >= 0) { st->last_outdoor = map_id; st->last_w = mt.w; st->last_h = mt.h; }
    else if (st->last_outdoor >= 0 && map_palette(st->last_outdoor) >= 0 && mt.w == st->last_w && mt.h == st->last_h) {
        map_id = st->last_outdoor; cur_pal = map_palette(map_id);
    }
    bool recolor = cur_pal >= 0 && mt.rom_size > CGB_PALETTES + 8 * 16;
    uint32_t src1 = 0, src2 = 0;   /* colori del luogo nel gioco (ROM): si ricolorano dovunque compaiano */
    #define PLACE_COLORS(m) (((m) >= 0 && (m) < 64 && map_palette(m) >= 0) ? place_colors_for(gb, &mt, place_colors, place_known, (m)) : NULL)
    /* Solo con i colori normali del luogo: durante una dissolvenza (tavolozza che cambia) non si ricolora niente,
     * altrimenti tutto il bianco diventa il colore del luogo (riquadro viola, D-067). */
    if (recolor) {
        /* tavolozza che il gioco usa adesso per questo luogo (città = numero + 1): se in memoria video c'è altro, è una
         * dissolvenza */
        size_t o = CGB_PALETTES + 8 * (size_t)cur_pal;
        uint32_t game1 = GB_convert_rgb15(gb, (mt.rom[o + 2] | (mt.rom[o + 3] << 8)) & 0x7FFF, false);
        uint32_t game2 = GB_convert_rgb15(gb, (mt.rom[o + 4] | (mt.rom[o + 5] << 8)) & 0x7FFF, false);
        /* la tavolozza del luogo può non essere la più usata (tendina nera prima della lotta, D-083): si cerca quella
         * con i colori del luogo */
        for (int p = 0; p < 8; p++)
            if (colors[p][1] == game1 && colors[p][2] == game2 && colors[p][0] != colors[p][1]) { fallback = p; break; }
        recolor = game1 == colors[fallback][1] && game2 == colors[fallback][2] && colors[fallback][0] != colors[fallback][1];
        if (!recolor) {
            /* Dissolvenze, lampi e tendina (D-083): nel gioco a colori la dissolvenza sposta i colori della tavolozza da un
             * posto all'altro (il viola del luogo passa al posto 2, poi 3...). Basta che il colore del luogo ci sia
             * ancora: ogni pixel con quel colore prende il colore del luogo a cui appartiene. */
            bool has = false;
            for (int c = 0; c < 4; c++) if (colors[fallback][c] == game1 || colors[fallback][c] == game2) has = true;
            recolor = has;
        }
        src1 = game1; src2 = game2;
    }

    /* 3. Disegno: righe sopra e sotto dalla mappa, al centro l'immagine originale. */
    for (int row = 0; row < out_h; row++) {
        int f = row - off_top;
        uint32_t *dst = out + (size_t)row * FRAME_W;
        int my = st->base_y + f;
        int ty = floordiv(my, 8), py = posmod(my, 8);
        if (f >= 0 && f < FRAME_H && f < st->map_from_row) {
            memcpy(dst, frame + (size_t)f * FRAME_W, FRAME_W * sizeof(uint32_t));
            if (!recolor) continue;
            /* immagine del gioco: i colori 1 e 2 della mappa si riportano a quelli del luogo a cui appartiene il tile */
            for (int col = 0; col < FRAME_W; col++) {
                if (dst[col] != src1 && dst[col] != src2) continue;
                int tx = floordiv(st->base_x + col, 8);
                const uint32_t *pc = PLACE_COLORS(map_of_tile(&mt, map_id, tx, ty));
                if (!pc) continue;
                dst[col] = dst[col] == src1 ? pc[1] : pc[2];
            }
            continue;
        }
        for (int col = 0; col < FRAME_W; col++) {
            int mx = st->base_x + col;
            int tx = floordiv(mx, 8), px = posmod(mx, 8);
            uint8_t tile = map_tile(&mt, tx, ty);
            uint8_t attr = st->attr_known[tile] ? st->attr_by_tile[tile] : (uint8_t)fallback;
            const uint8_t *data = (attr & 0x08) ? bank1 : bank0;
            int yy = (attr & 0x40) ? 7 - py : py;
            int xx = (attr & 0x20) ? px : 7 - px;
            int base = signed_tiles ? 0x1000 + (int8_t)tile * 16 : tile * 16;
            uint8_t lo = data[base + yy * 2], hi = data[base + yy * 2 + 1];
            int c = (((hi >> xx) & 1) << 1) | ((lo >> xx) & 1);
            uint32_t v = colors[attr & 7][c];
            const uint32_t *pc = recolor && (v == src1 || v == src2) && (attr & 7) == fallback ? PLACE_COLORS(map_of_tile(&mt, map_id, tx, ty)) : NULL;
            dst[col] = pc ? (v == src1 ? pc[1] : pc[2]) : v;
            st->bgidx[(size_t)row * FRAME_W + col] = (uint8_t)(c | (attr & 0x80));
        }
    }

    st->bg0 = colors[fallback][0];
    draw_sprites(gb, out, out_h, off_top, lcdc, bank0, bank1, st);
    return 1;
}

/*
 * Sprite di tutta la memoria degli sprite (OAM) anche fuori dalle 144 righe e senza la priorità dello sfondo
 * (introduzione, D-076): out = 160 × out_h ARGB, la riga 0 del gioco alla riga top; 0 = trasparente.
 * Si saltano gli sprite nascosti dal gioco (Y = 0 o 160, X = 0 o ≥ 168). Restituisce quanti sprite ha disegnato.
 */
static struct { int x, y, gy, last_game, last_own, every, oam_seen, sat; uint8_t c[8][8]; uint32_t pal[4]; } star_t[40];
static int nstar_t = 0;
static bool falling = false;

int ev_sprites(GB_gameboy_t *gb, uint32_t *out, int out_h, int top) {
    memset(out, 0, (size_t)FRAME_W * out_h * sizeof(uint32_t));
    uint8_t lcdc = rd(gb, 0xFF40);
    bool on = (lcdc & 0x80) && (lcdc & 0x02);
    if (!on && !falling) return 0;
    size_t oam_size = 0, obp_size = 0, vram_size = 0;
    const uint8_t *oam = GB_get_direct_access(gb, GB_DIRECT_ACCESS_OAM, &oam_size, NULL);
    const uint8_t *obp = GB_get_direct_access(gb, GB_DIRECT_ACCESS_OBP, &obp_size, NULL);
    const uint8_t *vram = GB_get_direct_access(gb, GB_DIRECT_ACCESS_VRAM, &vram_size, NULL);
    if (!oam || oam_size < 160 || !obp || obp_size < 64 || !vram || vram_size < 0x4000) return 0;
    uint32_t pal[8][4];
    for (int p = 0; p < 8; p++) for (int c = 0; c < 4; c++) {
        uint16_t rgb = obp[p * 8 + c * 2] | (obp[p * 8 + c * 2 + 1] << 8);
        pal[p][c] = GB_convert_rgb15(gb, rgb & 0x7FFF, false);
    }
    int tall = (lcdc & 0x04) ? 16 : 8, n = 0;
    /* Scena di GAME FREAK (logo fatto di sprite, tile 0x8D): le stelline (tile 0xA2) scendendo diventano bianche e
     * sembrano tagliate; si tiene il colore che avevano per colonna, così restano visibili fino in fondo. */
    static uint32_t star_pal[FRAME_W + 8][4];
    static bool star_known[FRAME_W + 8];
    static int star_sat[FRAME_W + 8];
    bool gf = false;
    if (on) for (int i = 0; i < 40; i++) if (oam[i * 4 + 2] == 0x8D && oam[i * 4] != 0) gf = true;
    if (!gf) memset(star_known, 0, sizeof(star_known));
    /* Stelline (D-081): ognuna si segue per conto suo (colonna, riga, ogni quanti fotogrammi scende di un pixel).
     * Quando il gioco la ferma o la toglie, continua a scendere col suo ritmo finché arriva in fondo all'immagine del
     * gioco o finisce la scena; la copia ferma del gioco si copre di bianco. */
    static int fc = 0;
    fc++;
    if (!gf) nstar_t = 0;
    else if (tall == 8) {
        bool used[40] = { false };
        for (int i = 0; i < 40; i++) {
            const uint8_t *o = oam + i * 4;
            if (o[2] != 0xA2 || o[0] == 0 || o[1] == 0 || o[1] >= 168) continue;
            int gx = o[1] - 8, gy = o[0] - 16, k = -1;
            for (int t = 0; t < nstar_t; t++)
                if (!used[t] && star_t[t].x == gx && gy - star_t[t].gy >= 0 && gy - star_t[t].gy <= 2) { k = t; break; }
            if (k < 0 && nstar_t < 40) {
                k = nstar_t++;
                star_t[k].x = gx; star_t[k].gy = gy; star_t[k].y = gy; star_t[k].last_game = fc; star_t[k].last_own = fc; star_t[k].every = 3; star_t[k].sat = -1;
                const uint8_t *data = (o[3] & 0x08) ? vram + 0x2000 : vram;
                for (int py = 0; py < 8; py++) {
                    int yy = (o[3] & 0x40) ? 7 - py : py;
                    uint8_t lo = data[0xA2 * 16 + yy * 2], hi = data[0xA2 * 16 + yy * 2 + 1];
                    for (int px = 0; px < 8; px++) { int xx = (o[3] & 0x20) ? px : 7 - px; star_t[k].c[py][px] = (((hi >> xx) & 1) << 1) | ((lo >> xx) & 1); }
                }
            }
            if (k < 0) continue;
            used[k] = true;
            if (gy != star_t[k].gy) {   /* il gioco l'ha mossa: si impara il suo ritmo */
                int e = fc - star_t[k].last_game;
                if (e >= 1 && e <= 8) star_t[k].every = e;
                star_t[k].last_game = fc; star_t[k].last_own = fc; star_t[k].gy = gy;
                if (gy > star_t[k].y) star_t[k].y = gy;
            }
            star_t[k].oam_seen = fc;
            /* colore più vivo visto per questa stellina: non sbiadisce */
            const uint32_t *pp = pal[o[3] & 7];
            int sat = 0;
            for (int c = 1; c < 4; c++) {
                uint32_t v = pp[c]; int r = (v >> 16) & 255, g = (v >> 8) & 255, b = v & 255;
                int mx = r > g ? (r > b ? r : b) : (g > b ? g : b), mn = r < g ? (r < b ? r : b) : (g < b ? g : b);
                if (mx - mn > sat) sat = mx - mn;
            }
            if (sat >= star_t[k].sat) { memcpy(star_t[k].pal, pp, sizeof(star_t[k].pal)); star_t[k].sat = sat; }
        }
        /* ferma (o tolta) da più del suo ritmo: scende da sola */
        for (int t = 0; t < nstar_t; t++)
            if (fc - star_t[t].last_game >= star_t[t].every && fc - star_t[t].last_own >= star_t[t].every) { star_t[t].y++; star_t[t].last_own = fc; }
    }
    falling = gf && nstar_t > 0;
    if (falling) {
        /* prima si coprono le copie del gioco rimaste indietro, poi si disegnano tutte alla loro riga */
        for (int t = 0; t < nstar_t; t++) {
            if (star_t[t].y == star_t[t].gy || star_t[t].oam_seen != fc) continue;
            for (int py = 0; py < 8; py++) for (int px = 0; px < 8; px++) {
                if (!star_t[t].c[py][px]) continue;
                int col = star_t[t].x + px, row = top + star_t[t].gy + py;
                if (col >= 0 && col < FRAME_W && row >= 0 && row < out_h) out[(size_t)row * FRAME_W + col] = 0xFFFFFFFFu;
            }
        }
        for (int t = 0; t < nstar_t; t++) {
            if (star_t[t].y + 8 > FRAME_H) continue;   /* arrivata in fondo all'immagine: sparisce intera, non tagliata */
            for (int py = 0; py < 8; py++) for (int px = 0; px < 8; px++) {
                int c = star_t[t].c[py][px]; if (!c) continue;
                int col = star_t[t].x + px, gyy = star_t[t].y + py, row = top + gyy;
                if (gyy >= FRAME_H) continue;   /* restano dentro l'immagine del gioco */
                if (col >= 0 && col < FRAME_W && row >= 0 && row < out_h) out[(size_t)row * FRAME_W + col] = star_t[t].pal[c] | 0xFF000000u;
            }
        }
        n++;
    }
    for (int i = 39; on && i >= 0; i--) {   /* il primo sprite sta sopra gli altri */
        const uint8_t *o = oam + i * 4;
        if (o[0] == 0 || o[1] == 0 || o[1] >= 168) continue;
        if (o[0] == 160) {
            /* Y = 160 è il valore con cui il gioco nasconde gli sprite, ma può essere anche la riga dei piedi di
             * Pikachu che sale: si tiene se c'è uno sprite nella stessa colonna subito sopra */
            bool part = false;
            for (int j = 0; j < 40; j++) if (j != i && oam[j * 4 + 1] == o[1] && oam[j * 4] == 160 - tall) part = true;
            if (!part) continue;
        }
        if (falling && gf && o[2] == 0xA2) continue;   /* al loro posto le copie che cadono */
        int y = o[0] - 16, x = o[1] - 8;
        if (o[0] >= 200) y -= 256;   /* sopra lo schermo (il gioco scrive -Y): non in basso (testa di Pikachu sul surf) */
        uint8_t attr = o[3];
        const uint8_t *data = (attr & 0x08) ? vram + 0x2000 : vram;
        int tile = tall == 16 ? (o[2] & 0xFE) : o[2];
        n++;
        const uint32_t *use = pal[attr & 7];
        if (gf && o[2] == 0xA2 && o[1] < FRAME_W + 8) {
            /* colore più vivo visto in quella colonna: la stellina non sbiadisce */
            int sat = 0;
            for (int c = 1; c < 4; c++) {
                uint32_t v = use[c]; int r = (v >> 16) & 255, g = (v >> 8) & 255, b = v & 255;
                int mx = r > g ? (r > b ? r : b) : (g > b ? g : b), mn = r < g ? (r < b ? r : b) : (g < b ? g : b);
                if (mx - mn > sat) sat = mx - mn;
            }
            if (!star_known[o[1]] || sat >= star_sat[o[1]]) { memcpy(star_pal[o[1]], use, sizeof(star_pal[0])); star_sat[o[1]] = sat; star_known[o[1]] = true; }
            else use = star_pal[o[1]];
        }
        for (int py = 0; py < tall; py++) {
            int row = top + y + py;
            if (row < 0 || row >= out_h) continue;
            int yy = (attr & 0x40) ? tall - 1 - py : py;
            uint8_t lo = data[tile * 16 + yy * 2], hi = data[tile * 16 + yy * 2 + 1];
            for (int px = 0; px < 8; px++) {
                int col = x + px;
                if (col < 0 || col >= FRAME_W) continue;
                int xx = (attr & 0x20) ? px : 7 - px;
                int c = (((hi >> xx) & 1) << 1) | ((lo >> xx) & 1);
                if (c) out[(size_t)row * FRAME_W + col] = use[c] | 0xFF000000u;
            }
        }
    }
    return (gf || falling) ? n | 0x10000 : n;   /* 0x10000: sprite da disegnare anche sopra l'immagine del gioco */
}
