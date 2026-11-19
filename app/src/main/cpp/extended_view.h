#pragma once
#include <stdbool.h>
#include <stdint.h>
#include "gb.h"

/* Stato della vista estesa tra un fotogramma e l'altro. */
typedef struct {
    bool valid;
    int map_id;
    int base_x, base_y;           /* pixel della mappa (con il bordo) nell'angolo in alto a sinistra dell'inquadratura */
    uint8_t attr_by_tile[256];    /* attributi a colori imparati dai tile visibili */
    bool attr_known[256];
    uint8_t obj_attr_by_tile[256]; /* attributi degli sprite visti nella memoria degli sprite (OAM) */
    bool obj_attr_known[256];
    uint8_t bgidx[160 * 512];     /* colore dello sfondo (0–3, bit 7 = priorità) delle righe disegnate, per gli sprite */
} EVState;

void ev_reset(EVState *st);

/*
 * Disegna in out (160 × out_h, ARGB) la mappa estesa con il fotogramma originale (160 × 144) alla riga frame_top
 * (−1 = al centro).
 * Restituisce 1 se la vista estesa è attiva, 0 se lo schermo non mostra la mappa (out non modificato).
 */
int ev_render(GB_gameboy_t *gb, const uint32_t *frame, uint32_t *out, int out_h, int frame_top, EVState *st);

/* Sprite anche fuori dalle 144 righe (introduzione): riga 0 del gioco alla riga top di out (160 × out_h). */
int ev_sprites(GB_gameboy_t *gb, uint32_t *out, int out_h, int top);
