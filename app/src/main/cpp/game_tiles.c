/*
 * Tile del gioco per l'interfaccia dell'app (D-039): le aggiunte (voci di menu, riquadri, linguetta) si disegnano
 * con il carattere e i riquadri veri del gioco, presi dalla memoria video, così non si distinguono dal gioco.
 * Solo lettura: non scrive mai nella memoria del gioco.
 */
#include <string.h>
#include "game_tiles.h"

static uint8_t rd(GB_gameboy_t *gb, uint16_t a) { return GB_safe_read_memory(gb, a); }

/*
 * Carattere del gioco nella ROM (versione italiana): 128 tile a 1 bit per pixel per i codici 0x80–0xFF.
 * Serve perché sulla mappa il gioco usa la stessa zona della memoria video (0x8800) per i fotogrammi dei personaggi
 * che camminano: lì le lettere maiuscole non ci sono. Si usa solo dopo aver verificato che coincida con le lettere
 * minuscole (0xA0–0xBF) che il gioco tiene in memoria video.
 */
#define FONT_ROM 0x10600

static bool font_matches(const uint8_t *rom, size_t rom_size, const uint8_t *vram) {
    if (rom_size < FONT_ROM + 128 * 8) return false;
    int same = 0;
    for (int t = 0xA0; t < 0xC0; t++) {
        const uint8_t *r = rom + FONT_ROM + (t - 0x80) * 8;
        const uint8_t *v = vram + 0x0800 + (t - 0x80) * 16;
        bool ok = true;
        for (int y = 0; y < 8; y++) if ((uint8_t)(v[y * 2] | v[y * 2 + 1]) != r[y]) { ok = false; break; }
        if (ok) same++;
    }
    return same >= 28;
}

static bool is_text_tile(uint8_t t) { return (t >= 0x79 && t <= 0x7E) || (t >= 0x80 && t <= 0xBF) || t == 0xED; }

int gt_sheet(GB_gameboy_t *gb, uint32_t *out, GTState *st) {
    size_t vram_size = 0, bgp_size = 0;
    const uint8_t *vram = GB_get_direct_access(gb, GB_DIRECT_ACCESS_VRAM, &vram_size, NULL);
    const uint8_t *bgp = GB_get_direct_access(gb, GB_DIRECT_ACCESS_BGP, &bgp_size, NULL);
    if (!vram || vram_size < 0x4000 || !bgp || bgp_size < 64) return 0;
    uint8_t lcdc = rd(gb, 0xFF40);

    /* Palette dei riquadri di testo: la più usata dai tile di testo nella mappa dello sfondo e in quella della
     * finestra (il gioco disegna menu e testi sulla finestra, mappa scelta dal bit 6 di LCDC). */
    if (lcdc & 0x80) {
        int maps[2] = { (lcdc & 0x08) ? 0x1C00 : 0x1800, (lcdc & 0x20) ? ((lcdc & 0x40) ? 0x1C00 : 0x1800) : -1 };
        int count[8] = {0}, total = 0;
        for (int m = 0; m < 2; m++) {
            if (maps[m] < 0 || (m == 1 && maps[1] == maps[0])) continue;
            for (int i = 0; i < 32 * 32; i++) {
                if (is_text_tile(vram[maps[m] + i])) { count[vram[0x2000 + maps[m] + i] & 7]++; total++; }
            }
        }
        if (total >= 8) {
            int p = 0;
            for (int q = 1; q < 8; q++) if (count[q] > count[p]) p = q;
            for (int c = 0; c < 4; c++) {
                uint16_t rgb = bgp[p * 8 + c * 2] | (bgp[p * 8 + c * 2 + 1] << 8);
                st->colors[c] = GB_convert_rgb15(gb, rgb & 0x7FFF, false);
            }
            st->known = true;
        }
    }
    if (!st->known) {
        st->colors[0] = GB_convert_rgb15(gb, 0x7FFF, false);
        st->colors[1] = st->colors[2] = st->colors[3] = GB_convert_rgb15(gb, 0x0000, false);
    }

    size_t rom_size = 0;
    const uint8_t *rom = GB_get_direct_access(gb, GB_DIRECT_ACCESS_ROM, &rom_size, NULL);
    if (!st->font_ok && rom && font_matches(rom, rom_size, vram)) st->font_ok = 1;

    for (int t = 0; t < 256; t++) {
        int ox0 = (t % 16) * 8, oy0 = (t / 16) * 8;
        if (t < 0x70) {   /* tile della mappa (animati): non servono all'app, restano vuoti */
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) out[(oy0 + y) * 128 + ox0 + x] = st->colors[0];
            continue;
        }
        if (t >= 0x80 && st->font_ok) {
            const uint8_t *r = rom + FONT_ROM + (t - 0x80) * 8;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                out[(oy0 + y) * 128 + ox0 + x] = st->colors[((r[y] >> (7 - x)) & 1) ? 3 : 0];
            continue;
        }
        int base;
        if (t >= 0x80) base = 0x0800 + (t - 0x80) * 16;
        else if (lcdc & 0x10) base = t * 16;
        else base = 0x1000 + t * 16;
        int ox = (t % 16) * 8, oy = (t / 16) * 8;
        for (int y = 0; y < 8; y++) {
            uint8_t lo = vram[base + y * 2], hi = vram[base + y * 2 + 1];
            for (int x = 0; x < 8; x++) {
                int c = (((hi >> (7 - x)) & 1) << 1) | ((lo >> (7 - x)) & 1);
                out[(oy + y) * 128 + ox + x] = st->colors[c];
            }
        }
    }
    return st->known ? 1 : 0;
}
