/*
 * Prova del motore su computer (senza Android): stessa configurazione dell'app,
 * stesso programma di avvio. Fa girare la ROM, preme alcuni tasti, salva immagini PPM
 * e verifica che salvataggio e ripristino dello stato funzionino.
 * Uso: harness ROM CARTELLA_USCITA
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "gb.h"

extern const unsigned char giallo_boot_rom[];
extern const size_t giallo_boot_rom_size;
static uint32_t pixels[160 * 144];
static unsigned long samples;

static uint32_t rgb(GB_gameboy_t *gb, uint8_t r, uint8_t g, uint8_t b) { (void)gb; return 0xFF000000u | (r << 16) | (g << 8) | b; }
static void on_sample(GB_gameboy_t *gb, GB_sample_t *s) { (void)gb; (void)s; samples++; }

static void dump(const char *dir, const char *name) {
    char path[512]; snprintf(path, sizeof path, "%s/%s.ppm", dir, name);
    FILE *f = fopen(path, "wb"); if (!f) return;
    fprintf(f, "P6\n160 144\n255\n");
    for (int i = 0; i < 160 * 144; i++) { unsigned char c[3] = { pixels[i] >> 16, pixels[i] >> 8, pixels[i] }; fwrite(c, 1, 3, f); }
    fclose(f);
}
static void frames(GB_gameboy_t *gb, int n) { while (n--) GB_run_frame(gb); }
static void tap(GB_gameboy_t *gb, GB_key_t k) { GB_set_key_state(gb, k, true); frames(gb, 6); GB_set_key_state(gb, k, false); frames(gb, 30); }

int main(int argc, char **argv) {
    if (argc < 3) { fprintf(stderr, "uso: %s ROM CARTELLA\n", argv[0]); return 2; }
    FILE *f = fopen(argv[1], "rb"); if (!f) return 1;
    fseek(f, 0, SEEK_END); long len = ftell(f); fseek(f, 0, SEEK_SET);
    uint8_t *rom = malloc(len); fread(rom, 1, len, f); fclose(f);

    GB_gameboy_t *gb = GB_init(GB_alloc(), GB_MODEL_CGB_E);
    GB_load_boot_rom_from_buffer(gb, giallo_boot_rom, giallo_boot_rom_size);
    GB_set_pixels_output(gb, pixels);
    GB_set_rgb_encode_callback(gb, rgb);
    GB_set_color_correction_mode(gb, GB_COLOR_CORRECTION_MODERN_BALANCED);
    GB_apu_set_sample_callback(gb, on_sample);
    GB_set_sample_rate(gb, 48000);
    GB_load_rom_from_buffer(gb, rom, len);
    GB_reset(gb);

    frames(gb, 300); dump(argv[2], "t05s");
    frames(gb, 600); dump(argv[2], "t15s");
    tap(gb, GB_KEY_START); frames(gb, 120); tap(gb, GB_KEY_START); frames(gb, 90); dump(argv[2], "menu");
    printf("campioni audio: %lu (attesi ~%d)\n", samples, (int)(48000.0 * (300 + 600 + 72 + 120 + 90) / 59.73));

    size_t sz = GB_get_save_state_size(gb); uint8_t *st = malloc(sz); GB_save_state_to_buffer(gb, st);
    frames(gb, 200);
    int r = GB_load_state_from_buffer(gb, st, sz);
    GB_run_frame(gb); dump(argv[2], "dopo_ripristino");
    printf("stato: %zu byte, ripristino=%s\n", sz, r == 0 ? "ok" : "ERRORE");
    printf("batteria: %d byte\n", GB_save_battery_size(gb));
    GB_free(gb); GB_dealloc(gb); free(st); free(rom);
    return 0;
}
