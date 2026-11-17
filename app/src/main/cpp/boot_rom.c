/*
 * Mini programma di avvio originale (scritto per questo progetto, nessun codice Nintendo).
 *
 * Il Game Boy Color esegue un piccolo programma di avvio prima del gioco. Qui non serve
 * l'animazione del logo: ci limitiamo a lasciare la console nello stato in cui la lascia
 * l'avvio reale di un Game Boy Color con un gioco a colori, poi passiamo al gioco.
 *
 * Stato finale: A=0x11 (console a colori), F=0x80, B=0x00, C=0x00, D=0xFF, E=0x56,
 * H=0x00, L=0x0D, SP=0xFFFE, schermo acceso (LCDC=0x91), audio acceso.
 * L'ultima istruzione scrive in 0xFF50 all'indirizzo 0x00FE: il programma di avvio si
 * disattiva e l'esecuzione prosegue da 0x0100, l'ingresso della cartuccia.
 */
#include <stddef.h>

const size_t giallo_boot_rom_size = 0x900;

const unsigned char giallo_boot_rom[0x900] = {
    [0x00] = 0x31, 0xFE, 0xFF,   /* ld sp, $FFFE                   */
    [0x03] = 0x3E, 0x80,         /* ld a, $80                      */
    [0x05] = 0xE0, 0x26,         /* ldh [$FF26], a  ; audio acceso  */
    [0x07] = 0x3E, 0x77,         /* ld a, $77                      */
    [0x09] = 0xE0, 0x24,         /* ldh [$FF24], a  ; volume        */
    [0x0B] = 0x3E, 0xF3,         /* ld a, $F3                      */
    [0x0D] = 0xE0, 0x25,         /* ldh [$FF25], a  ; canali        */
    [0x0F] = 0x3E, 0xFC,         /* ld a, $FC                      */
    [0x11] = 0xE0, 0x47,         /* ldh [$FF47], a  ; palette BG    */
    [0x13] = 0x3E, 0x91,         /* ld a, $91                      */
    [0x15] = 0xE0, 0x40,         /* ldh [$FF40], a  ; schermo acceso*/
    [0x17] = 0x01, 0x00, 0x00,   /* ld bc, $0000                   */
    [0x1A] = 0x11, 0x56, 0xFF,   /* ld de, $FF56                   */
    [0x1D] = 0x21, 0x0D, 0x00,   /* ld hl, $000D                   */
    [0x20] = 0xC3, 0xFB, 0x00,   /* jp $00FB                       */
    [0xFB] = 0xAF,               /* xor a           ; F = $80      */
    [0xFC] = 0x3E, 0x11,         /* ld a, $11       ; console a colori */
    [0xFE] = 0xE0, 0x50,         /* ldh [$FF50], a  ; fine avvio   */
};
