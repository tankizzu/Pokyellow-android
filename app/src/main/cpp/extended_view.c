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

#define FRAME_W 160
#define FRAME_H 144

typedef struct {
    const uint8_t *rom;
    size_t rom_size;
    uint8_t map[1300];
    int w, h;               /* in blocchi, senza bordo */
    size_t blockset;        /* offset nella ROM */
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
    return mt->blockset + 256 * 16 <= mt->rom_size;
}

/* Tile della mappa alla posizione (tx, ty) in tile, contando anche il bordo di 3 blocchi (12 tile). */
static uint8_t map_tile(const MapTiles *mt, int tx, int ty) {
    int bw = mt->w + 6, bh = mt->h + 6;
    int bx = floordiv(tx, 4), by = floordiv(ty, 4);
    uint8_t block = (bx >= 0 && by >= 0 && bx < bw && by < bh) ? mt->map[by * bw + bx] : mt->map[0];
    return mt->rom[mt->blockset + block * 16 + posmod(ty, 4) * 4 + posmod(tx, 4)];
}

void ev_reset(EVState *st) { memset(st, 0, sizeof(*st)); st->map_id = -1; }

int ev_render(GB_gameboy_t *gb, const uint32_t *frame, uint32_t *out, int out_h, EVState *st) {
    if (out_h < FRAME_H) return 0;
    int off_top = (out_h - FRAME_H) / 2;
    uint8_t lcdc = rd(gb, 0xFF40);
    if (!(lcdc & 0x80)) { st->valid = false; return 0; }

    MapTiles mt;
    if (!load_map(gb, &mt)) { st->valid = false; return 0; }
    int map_id = rd(gb, CUR_MAP);
    if (map_id != st->map_id) { memset(st->attr_known, 0, sizeof(st->attr_known)); st->map_id = map_id; }

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
    int best = -1, best_tx = 0, best_ty = 0;
    for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
        int score = 0;
        for (int j = 0; j < 18; j++) for (int i = 0; i < 20; i++) {
            uint8_t v = bank0[bg_map + (((scy >> 3) + j) & 31) * 32 + (((scx >> 3) + i) & 31)];
            if (v == map_tile(&mt, est_tx + dx + i, est_ty + dy + j)) score++;
        }
        if (score > best) { best = score; best_tx = est_tx + dx; best_ty = est_ty + dy; }
    }
    if (best < 300) { st->valid = false; return 0; }   /* meno dell'83%: lo sfondo non è la mappa */

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

    /* 3. Disegno: righe sopra e sotto dalla mappa, al centro l'immagine originale. */
    for (int row = 0; row < out_h; row++) {
        int f = row - off_top;
        uint32_t *dst = out + (size_t)row * FRAME_W;
        if (f >= 0 && f < FRAME_H) { memcpy(dst, frame + (size_t)f * FRAME_W, FRAME_W * sizeof(uint32_t)); continue; }
        int my = st->base_y + f;
        int ty = floordiv(my, 8), py = posmod(my, 8);
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
            dst[col] = colors[attr & 7][c];
        }
    }
    return 1;
}
