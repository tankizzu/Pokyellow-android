#pragma once
#include <stdbool.h>
#include <stdint.h>
#include "gb.h"

/* Colori dei riquadri di testo del gioco, ricordati tra un fotogramma e l'altro. */
typedef struct {
    bool known;
    uint32_t colors[4];
} GTState;

/*
 * Foglio 128×128 pixel (16×16 tile da 8×8) con i tile 0x00–0xFF come li disegna il gioco in questo momento
 * (carattere, riquadri, cursore), con i colori dei riquadri di testo. Restituisce 1 se i colori vengono dallo
 * schermo attuale o da uno precedente, 0 se sono quelli predefiniti (bianco e nero).
 */
int gt_sheet(GB_gameboy_t *gb, uint32_t *out, GTState *st);
