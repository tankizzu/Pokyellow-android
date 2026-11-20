/*
 * Collegamento tra il motore SameBoy (C) e l'app (Kotlin).
 * Tutte le funzioni, tranne nativeSetKeys, vanno chiamate dallo stesso thread (il thread del gioco).
 */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <stdint.h>
#include "gb.h"
#include "extended_view.h"
#include "game_tiles.h"

#define WIDTH 160
#define HEIGHT 144
#define AUDIO_CAPACITY 8192
#define EXT_MAX_HEIGHT 512 /* campioni int16 (stereo interlacciati) per fotogramma: abbondante */

extern const unsigned char giallo_boot_rom[];
extern const size_t giallo_boot_rom_size;

typedef struct {
    GB_gameboy_t *gb;
    uint32_t pixels[WIDTH * HEIGHT];
    int16_t audio[AUDIO_CAPACITY];
    size_t audio_len;
    volatile int keys;
    EVState ev;
    GTState gt;
    uint32_t ext[WIDTH * EXT_MAX_HEIGHT];
} Core;

static uint32_t rgb_encode(GB_gameboy_t *gb, uint8_t r, uint8_t g, uint8_t b) {
    (void)gb;
    return 0xFF000000u | ((uint32_t)r << 16) | ((uint32_t)g << 8) | b;
}

static void on_sample(GB_gameboy_t *gb, GB_sample_t *sample) {
    Core *core = GB_get_user_data(gb);
    if (core->audio_len + 2 <= AUDIO_CAPACITY) {
        core->audio[core->audio_len++] = sample->left;
        core->audio[core->audio_len++] = sample->right;
    }
}

static inline Core *as_core(jlong handle) { return (Core *)(intptr_t)handle; }

JNIEXPORT jlong JNICALL
Java_it_giallotouch_emu_NativeCore_nativeCreate(JNIEnv *env, jclass cls, jint sampleRate) {
    (void)env; (void)cls;
    Core *core = calloc(1, sizeof(Core));
    if (!core) return 0;
    core->gb = GB_init(GB_alloc(), GB_MODEL_CGB_E);
    GB_set_user_data(core->gb, core);
    ev_reset(&core->ev);
    GB_load_boot_rom_from_buffer(core->gb, giallo_boot_rom, giallo_boot_rom_size);
    GB_set_pixels_output(core->gb, core->pixels);
    GB_set_rgb_encode_callback(core->gb, rgb_encode);
    GB_set_color_correction_mode(core->gb, GB_COLOR_CORRECTION_MODERN_BALANCED);
    GB_apu_set_sample_callback(core->gb, on_sample);
    GB_set_sample_rate(core->gb, (unsigned)sampleRate);
    GB_set_highpass_filter_mode(core->gb, GB_HIGHPASS_ACCURATE);
    return (jlong)(intptr_t)core;
}

JNIEXPORT void JNICALL
Java_it_giallotouch_emu_NativeCore_nativeLoadRom(JNIEnv *env, jclass cls, jlong handle, jbyteArray rom) {
    (void)cls;
    Core *core = as_core(handle);
    jsize len = (*env)->GetArrayLength(env, rom);
    jbyte *data = (*env)->GetByteArrayElements(env, rom, NULL);
    GB_load_rom_from_buffer(core->gb, (const uint8_t *)data, (size_t)len);
    (*env)->ReleaseByteArrayElements(env, rom, data, JNI_ABORT);
    GB_reset(core->gb);
}

JNIEXPORT void JNICALL
Java_it_giallotouch_emu_NativeCore_nativeLoadBattery(JNIEnv *env, jclass cls, jlong handle, jbyteArray save) {
    (void)cls;
    Core *core = as_core(handle);
    jsize len = (*env)->GetArrayLength(env, save);
    jbyte *data = (*env)->GetByteArrayElements(env, save, NULL);
    GB_load_battery_from_buffer(core->gb, (const uint8_t *)data, (size_t)len);
    (*env)->ReleaseByteArrayElements(env, save, data, JNI_ABORT);
}

JNIEXPORT jbyteArray JNICALL
Java_it_giallotouch_emu_NativeCore_nativeSaveBattery(JNIEnv *env, jclass cls, jlong handle) {
    (void)cls;
    Core *core = as_core(handle);
    int size = GB_save_battery_size(core->gb);
    if (size <= 0) return (*env)->NewByteArray(env, 0);
    uint8_t *buffer = malloc((size_t)size);
    if (!buffer) return NULL;
    GB_save_battery_to_buffer(core->gb, buffer, (size_t)size);
    jbyteArray out = (*env)->NewByteArray(env, size);
    (*env)->SetByteArrayRegion(env, out, 0, size, (const jbyte *)buffer);
    free(buffer);
    return out;
}

JNIEXPORT jbyteArray JNICALL
Java_it_giallotouch_emu_NativeCore_nativeSaveState(JNIEnv *env, jclass cls, jlong handle) {
    (void)cls;
    Core *core = as_core(handle);
    size_t size = GB_get_save_state_size(core->gb);
    uint8_t *buffer = malloc(size);
    if (!buffer) return NULL;
    GB_save_state_to_buffer(core->gb, buffer);
    jbyteArray out = (*env)->NewByteArray(env, (jsize)size);
    (*env)->SetByteArrayRegion(env, out, 0, (jsize)size, (const jbyte *)buffer);
    free(buffer);
    return out;
}

JNIEXPORT jboolean JNICALL
Java_it_giallotouch_emu_NativeCore_nativeLoadState(JNIEnv *env, jclass cls, jlong handle, jbyteArray state) {
    (void)cls;
    Core *core = as_core(handle);
    jsize len = (*env)->GetArrayLength(env, state);
    jbyte *data = (*env)->GetByteArrayElements(env, state, NULL);
    int result = GB_load_state_from_buffer(core->gb, (const uint8_t *)data, (size_t)len);
    (*env)->ReleaseByteArrayElements(env, state, data, JNI_ABORT);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_it_giallotouch_emu_NativeCore_nativeSetKeys(JNIEnv *env, jclass cls, jlong handle, jint mask) {
    (void)env; (void)cls;
    as_core(handle)->keys = mask;
}

JNIEXPORT jint JNICALL
Java_it_giallotouch_emu_NativeCore_nativeRunFrame(JNIEnv *env, jclass cls, jlong handle, jintArray pixels, jshortArray audio) {
    (void)cls;
    Core *core = as_core(handle);
    int keys = core->keys;
    for (int i = 0; i < GB_KEY_MAX; i++) {
        GB_set_key_state(core->gb, (GB_key_t)i, (keys >> i) & 1);
    }
    core->audio_len = 0;
    GB_run_frame(core->gb);
    (*env)->SetIntArrayRegion(env, pixels, 0, WIDTH * HEIGHT, (const jint *)core->pixels);
    jsize cap = (*env)->GetArrayLength(env, audio);
    jsize count = (jsize)core->audio_len;
    if (count > cap) count = cap;
    (*env)->SetShortArrayRegion(env, audio, 0, count, core->audio);
    return count;
}

JNIEXPORT jint JNICALL
Java_it_giallotouch_emu_NativeCore_nativeReadMemory(JNIEnv *env, jclass cls, jlong handle, jint address) {
    (void)env; (void)cls;
    return GB_safe_read_memory(as_core(handle)->gb, (uint16_t)address);
}

/* Scrittura di un byte (opzioni del gioco dalla schermata OPZIONI dell'app, D-075). */
JNIEXPORT void JNICALL
Java_it_giallotouch_emu_NativeCore_nativeWriteMemory(JNIEnv *env, jclass cls, jlong handle, jint address, jint value) {
    (void)env; (void)cls;
    GB_write_memory(as_core(handle)->gb, (uint16_t)address, (uint8_t)value);
}

/* Vista estesa a tutto schermo: 1 se attiva (out riempito con 160 × height pixel), 0 altrimenti. */
JNIEXPORT jint JNICALL
Java_it_giallotouch_emu_NativeCore_nativeRenderExtended(JNIEnv *env, jclass cls, jlong handle, jintArray out, jint height, jint frameTop) {
    (void)cls;
    Core *core = as_core(handle);
    if (height < HEIGHT || height > EXT_MAX_HEIGHT || (*env)->GetArrayLength(env, out) < WIDTH * height) return 0;
    int r = ev_render(core->gb, core->pixels, core->ext, height, frameTop, &core->ev);
    if (!r) return 0;
    (*env)->SetIntArrayRegion(env, out, 0, WIDTH * height, (const jint *)core->ext);
    return r;   /* 1 = mappa, 2 = schermata del titolo */
}

/* Righe del gioco da [row] in giù disegnate dalla mappa invece che copiate (sotto il dialogo spostato, D-094). */
JNIEXPORT void JNICALL
Java_it_giallotouch_emu_NativeCore_nativeSetMapFromRow(JNIEnv *env, jclass cls, jlong handle, jint row) {
    (void)env; (void)cls;
    as_core(handle)->ev.map_from_row = row;
}

/* Diagnosi dell'allineamento della vista estesa: punteggio, spostamento scelto, righe usate. */
JNIEXPORT jint JNICALL
Java_it_giallotouch_emu_NativeCore_nativeEvDebug(JNIEnv *env, jclass cls, jlong handle, jint what) {
    (void)env; (void)cls;
    EVState *st = &as_core(handle)->ev;
    switch (what) { case 0: return st->dbg_score; case 1: return st->dbg_dx; case 2: return st->dbg_dy; case 3: return st->dbg_rows;
        case 4: return st->base_x; default: return st->base_y; }
}

/* Sprite anche fuori dalle 144 righe (introduzione, D-076): out = 160 × height, riga 0 del gioco alla riga top. */
JNIEXPORT jint JNICALL
Java_it_giallotouch_emu_NativeCore_nativeSprites(JNIEnv *env, jclass cls, jlong handle, jintArray out, jint height, jint top) {
    (void)cls;
    Core *core = as_core(handle);
    if (height <= 0 || height > EXT_MAX_HEIGHT || (*env)->GetArrayLength(env, out) < WIDTH * height) return 0;
    int n = ev_sprites(core->gb, core->ext, height, top);   /* + 0x10000: anche sopra l'immagine */
    (*env)->SetIntArrayRegion(env, out, 0, WIDTH * height, (const jint *)core->ext);
    return n;
}

/* Tile del gioco (carattere e riquadri) in un foglio 128×128 ARGB, con i colori dei riquadri di testo (D-039). */
JNIEXPORT jint JNICALL
Java_it_giallotouch_emu_NativeCore_nativeGameTiles(JNIEnv *env, jclass cls, jlong handle, jintArray out) {
    (void)cls;
    Core *core = as_core(handle);
    if ((*env)->GetArrayLength(env, out) < 128 * 128) return -1;
    uint32_t sheet[128 * 128];
    int known = gt_sheet(core->gb, sheet, &core->gt);
    (*env)->SetIntArrayRegion(env, out, 0, 128 * 128, (const jint *)sheet);
    return known;
}

/* La ROM è la versione italiana riconosciuta (SHA-1 verificato dall'app): il carattere nella ROM si può usare subito. */
JNIEXPORT void JNICALL
Java_it_giallotouch_emu_NativeCore_nativeTrustRomFont(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls;
    as_core(handle)->gt.font_ok = 1;
}

/* Copia un intervallo della memoria del gioco (senza effetti collaterali) per il "ponte" verso l'app. */
JNIEXPORT void JNICALL
Java_it_giallotouch_emu_NativeCore_nativeReadRange(JNIEnv *env, jclass cls, jlong handle, jint address, jbyteArray out) {
    (void)cls;
    Core *core = as_core(handle);
    jsize len = (*env)->GetArrayLength(env, out);
    if (len <= 0) return;
    if (len > 0x10000) len = 0x10000;
    uint8_t *tmp = malloc((size_t)len);
    if (!tmp) return;
    for (jsize i = 0; i < len; i++) {
        tmp[i] = GB_safe_read_memory(core->gb, (uint16_t)(address + i));
    }
    (*env)->SetByteArrayRegion(env, out, 0, len, (const jbyte *)tmp);
    free(tmp);
}

JNIEXPORT void JNICALL
Java_it_giallotouch_emu_NativeCore_nativeDestroy(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls;
    Core *core = as_core(handle);
    if (!core) return;
    GB_free(core->gb);
    GB_dealloc(core->gb);
    free(core);
}
