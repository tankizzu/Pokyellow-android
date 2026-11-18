/*
 * Esecutore di sequenze di tasti sul motore SameBoy (lo stesso dell'app), per analisi e test.
 * Uso: runner ROM SCRIPT CARTELLA_USCITA
 *
 * Comandi dello script (uno per riga, '#' per i commenti):
 *   wait N                      fa avanzare N fotogrammi
 *   press TASTO [TENUTA] [ATTESA]  preme e rilascia (predefiniti 6 e 30 fotogrammi)
 *   walk DIREZIONE N            N passi, controllando le coordinate (indirizzi della versione italiana)
 *   mash TASTO N ATTESA [IND VAL]  preme N volte; si ferma prima se mem[IND] == VAL (esadecimali)
 *   dump NOME                   salva immagine PPM e memoria C000-DFFF in CARTELLA/NOME.ppm/.ram
 *   print IND N                 stampa N byte da IND (esadecimale)
 *   where                       stampa mappa e coordinate
 *   save NOME | load NOME       salva / carica lo stato completo in CARTELLA/NOME.state
 *   ext NOME ALTEZZA            disegna la vista estesa (160 × ALTEZZA) in CARTELLA/NOME.ppm
 *   hold TASTO N                tiene premuto un tasto per N fotogrammi (senza rilasciarlo dopo)
 * Le immagini restano nella cartella di uscita (temporanea): mai nel repository.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include "gb.h"
#include "extended_view.h"

#define ADDR_MAP 0xD362
#define ADDR_Y   0xD365
#define ADDR_X   0xD366

extern const unsigned char giallo_boot_rom[];
extern const size_t giallo_boot_rom_size;
static uint32_t pixels[160 * 144];
static GB_gameboy_t *gb;
static const char *outdir;

static uint32_t rgb(GB_gameboy_t *g, uint8_t r, uint8_t gg, uint8_t b) { (void)g; return 0xFF000000u | (r << 16) | (gg << 8) | b; }
static void frames(int n) { while (n-- > 0) GB_run_frame(gb); }
static uint8_t rd(uint16_t a) { return GB_safe_read_memory(gb, a); }

static int key_of(const char *s) {
    const char *names[] = { "right", "left", "up", "down", "a", "b", "select", "start" };
    for (int i = 0; i < 8; i++) if (!strcasecmp(s, names[i])) return i;
    return -1;
}

static void press(int key, int hold, int wait) {
    GB_set_key_state(gb, key, true); frames(hold);
    GB_set_key_state(gb, key, false); frames(wait);
}

static void walk(int key, int n) {
    for (int i = 0; i < n; i++) {
        uint8_t m = rd(ADDR_MAP), x = rd(ADDR_X), y = rd(ADDR_Y);
        GB_set_key_state(gb, key, true);
        int moved = 0;
        for (int f = 0; f < 40; f++) {
            GB_run_frame(gb);
            if (rd(ADDR_MAP) != m || rd(ADDR_X) != x || rd(ADDR_Y) != y) { moved = 1; break; }
        }
        GB_set_key_state(gb, key, false);
        frames(12);
        if (!moved) { printf("  bloccato al passo %d (mappa %d x %d y %d)\n", i + 1, m, x, y); return; }
    }
}

static void dump(const char *name) {
    char path[1024];
    snprintf(path, sizeof path, "%s/%s.ppm", outdir, name);
    FILE *f = fopen(path, "wb");
    if (f) {
        fprintf(f, "P6\n160 144\n255\n");
        for (int i = 0; i < 160 * 144; i++) { unsigned char c[3] = { pixels[i] >> 16, pixels[i] >> 8, pixels[i] }; fwrite(c, 1, 3, f); }
        fclose(f);
    }
    snprintf(path, sizeof path, "%s/%s.ram", outdir, name);
    f = fopen(path, "wb");
    if (f) { for (int a = 0xC000; a < 0xE000; a++) fputc(rd(a), f); fclose(f); }
}

static EVState ev;
static uint32_t ext_out[160 * 512];

static void ext_dump(const char *name, int h) {
    if (h < 144 || h > 512) return;
    int ok = ev_render(gb, pixels, ext_out, h, &ev);
    printf("  vista estesa %s: base %d,%d\n", ok ? "ATTIVA" : "spenta", ev.base_x, ev.base_y);
    if (!ok) return;
    char path[1024]; snprintf(path, sizeof path, "%s/%s.ppm", outdir, name);
    FILE *f = fopen(path, "wb"); if (!f) return;
    fprintf(f, "P6\n160 %d\n255\n", h);
    for (int i = 0; i < 160 * h; i++) { unsigned char c[3] = { ext_out[i] >> 16, ext_out[i] >> 8, ext_out[i] }; fwrite(c, 1, 3, f); }
    fclose(f);
}

static void state_io(const char *name, int save) {
    char path[1024]; snprintf(path, sizeof path, "%s/%s.state", outdir, name);
    if (save) {
        size_t sz = GB_get_save_state_size(gb); uint8_t *b = malloc(sz);
        GB_save_state_to_buffer(gb, b);
        FILE *f = fopen(path, "wb"); if (f) { fwrite(b, 1, sz, f); fclose(f); }
        free(b);
    } else {
        FILE *f = fopen(path, "rb"); if (!f) { printf("  stato %s mancante\n", name); return; }
        fseek(f, 0, SEEK_END); long sz = ftell(f); fseek(f, 0, SEEK_SET);
        uint8_t *b = malloc(sz); fread(b, 1, sz, f); fclose(f);
        if (GB_load_state_from_buffer(gb, b, sz)) printf("  caricamento di %s non riuscito\n", name);
        free(b);
    }
}

int main(int argc, char **argv) {
    if (argc < 4) { fprintf(stderr, "uso: %s ROM SCRIPT CARTELLA\n", argv[0]); return 2; }
    outdir = argv[3];
    FILE *f = fopen(argv[1], "rb"); if (!f) return 1;
    fseek(f, 0, SEEK_END); long len = ftell(f); fseek(f, 0, SEEK_SET);
    uint8_t *rom = malloc(len); fread(rom, 1, len, f); fclose(f);

    ev_reset(&ev);
    gb = GB_init(GB_alloc(), GB_MODEL_CGB_E);
    GB_load_boot_rom_from_buffer(gb, giallo_boot_rom, giallo_boot_rom_size);
    GB_set_pixels_output(gb, pixels);
    GB_set_rgb_encode_callback(gb, rgb);
    GB_load_rom_from_buffer(gb, rom, len);
    GB_reset(gb);

    FILE *s = fopen(argv[2], "r"); if (!s) return 1;
    char line[256];
    while (fgets(line, sizeof line, s)) {
        char cmd[32] = "", a1[64] = "", a2[64] = "", a3[64] = "", a4[64] = "", a5[64] = "";
        if (line[0] == '#' || sscanf(line, "%31s %63s %63s %63s %63s %63s", cmd, a1, a2, a3, a4, a5) < 1) continue;
        if (!strcmp(cmd, "wait")) frames(atoi(a1));
        else if (!strcmp(cmd, "press")) press(key_of(a1), *a2 ? atoi(a2) : 6, *a3 ? atoi(a3) : 30);
        else if (!strcmp(cmd, "walk")) walk(key_of(a1), atoi(a2));
        else if (!strcmp(cmd, "mash")) {
            int k = key_of(a1), n = atoi(a2), w = atoi(a3);
            long addr = *a4 ? strtol(a4, NULL, 16) : -1; long val = *a5 ? strtol(a5, NULL, 16) : -1;
            int i = 0;
            for (; i < n; i++) { if (addr >= 0 && rd((uint16_t)addr) == val) break; press(k, 6, w); }
            printf("  mash %s: %d pressioni\n", a1, i);
        }
        else if (!strcmp(cmd, "dump")) dump(a1);
        else if (!strcmp(cmd, "print")) {
            long addr = strtol(a1, NULL, 16); int n = atoi(a2);
            printf("  %04lX:", addr); for (int i = 0; i < n; i++) printf(" %02X", rd((uint16_t)(addr + i))); printf("\n");
        }
        else if (!strcmp(cmd, "where")) printf("  mappa %d x %d y %d\n", rd(ADDR_MAP), rd(ADDR_X), rd(ADDR_Y));
        else if (!strcmp(cmd, "save")) state_io(a1, 1);
        else if (!strcmp(cmd, "ext")) ext_dump(a1, atoi(a2));
        else if (!strcmp(cmd, "hold")) { GB_set_key_state(gb, key_of(a1), true); frames(atoi(a2)); GB_set_key_state(gb, key_of(a1), false); }
        else if (!strcmp(cmd, "load")) state_io(a1, 0);
        else printf("  comando sconosciuto: %s\n", cmd);
        fflush(stdout);
    }
    GB_free(gb); GB_dealloc(gb); free(rom);
    return 0;
}
