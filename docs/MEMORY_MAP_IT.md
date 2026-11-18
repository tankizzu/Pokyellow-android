# Mappa della memoria – versione italiana

ROM: `POKEMON YELAPSI`, SHA-1 `05bb8e99f24d498613930949730afa8024e77d08`.
Gli indirizzi sono **verificati sperimentalmente** su questa ROM (strumenti in `tools/rom-analysis/`).
I nomi delle variabili seguono quelli del progetto pubblico pret/pokeyellow (versione inglese),
usato solo come documentazione dell'ordine e della dimensione delle variabili.

Stato: ✅ verificato · 🟡 dedotto dall'ordine delle variabili, coerente con le verifiche · ⏳ da trovare

## Regola trovata: spostamento rispetto alla versione inglese
- Zona **C000–CC2A**: stessi indirizzi della versione inglese (verificati wTileMap, wOverworldMap, menu, sprite).
- Zona **D15C–D36D**: indirizzi della versione inglese **+5 byte** (verificati nome, squadra, borsa, soldi, mappa, coordinate).
- **CD6B** (wJoyIgnore) è ancora senza spostamento; **D05B** (wIsInBattle) è già spostato di +5:
  lo spostamento inizia tra CD6C e D05A. Le variabili in quella zona vanno verificate una per una.

## Schermo, mappa, personaggi
| Variabile | Indirizzo | Stato | Note |
|---|---|---|---|
| wSpriteStateData1 | C100 (16 × 16 byte) | ✅ | sprite 0 = giocatore (y 60, x 64 in pixel schermo); sprite 15 = compagno che segue |
| wSpriteStateData2 | C200 (16 × 16 byte) | 🟡 | +4 = Y mappa, +5 = X mappa (con offset +4) |
| wTileMap | C3A0 (20 × 18) | ✅ | trovato dal bordo del menu |
| wOverworldMap | C6E8 (1300 byte) | ✅ | mappa corrente con bordo di 3 blocchi; riga = larghezza + 6 |
| wCurrentMenuItem | CC26 | ✅ | voce selezionata nei menu |
| wJoyIgnore | CD6B | ✅ | 0 quando il giocatore ha il controllo; FC durante la scena di Oak (tasti bloccati) |
| wIsInBattle | D05B | ✅ | 0 fuori dalla lotta, 2 nella lotta con allenatore (1 = selvatico, da confermare) |
| wBattleMon | D018 (battle_struct) | ✅ | creatura in campo: specie +0, PS +1, mosse +8, livello +14, PS max +15, PP +25 (bit 0-5) |
| wTopMenuItemY / wTopMenuItemX | CC24 / CC25 | ✅ | posizione del primo elemento del menu attivo |
| wMaxMenuItem | CC28 | 🟡 | 6 nel menu START (7 voci) |
| wMenuWatchedKeys | CC29 | 🟡 | |
| wLastMenuItem | CC2A | ✅ | |

## Giocatore, squadra, borsa
| Variabile | Indirizzo | Stato | Note |
|---|---|---|---|
| wPlayerName | D15C (11) | ✅ | testo nel set di caratteri del gioco, chiuso da 0x50 |
| wPartyCount | D167 | ✅ | 0 a inizio partita |
| wPartySpecies | D168 (7) | ✅ | chiusa da 0xFF |
| wPartyMons | D16F (6 × 44) | ✅ | verificato col primo compagno: specie +0, PS +1 (2 byte), stato +4, mosse +8, livello +33, PS max +34 |
| wPartyMonOT | D277 (6 × 11) | ✅ | nome dell'allenatore originale |
| wPartyMonNicks | D2B9 (6 × 11) | ✅ | scritto dopo la domanda sul soprannome |
| wPokedexOwned | D2FB (19) | ✅ | |
| wPokedexSeen | D30E (19) | 🟡 | |
| wNumBagItems | D321 | ✅ | 0 a inizio partita |
| wBagItems | D322 (41) | ✅ | coppie strumento/quantità, chiusa da 0xFF |
| wPlayerMoney | D34B (3, BCD) | ✅ | 00 30 00 = 3000 a inizio partita |
| wRivalName | D34E (11) | ✅ | |
| wOptions | D359 | ✅ | |
| wObtainedBadges | D35A | 🟡 | |
| wPlayerID | D35D (2) | 🟡 | |

## Mappa e posizione
| Variabile | Indirizzo | Stato | Note |
|---|---|---|---|
| wCurMap | D362 | ✅ | 0x26 nella camera iniziale |
| wCurrentTileBlockMapViewPointer | D363–D364 | 🟡 | cambia insieme a X |
| wYCoord | D365 | ✅ | |
| wXCoord | D366 | ✅ | |
| wYBlockCoord / wXBlockCoord | D367 / D368 | 🟡 | |
| wLastMap | D369 | 🟡 | |
| wCurMapTileset | D36B | 🟡 | |
| wCurMapHeight / wCurMapWidth | D36C / D36D | ✅ | in blocchi; confermati con i dati della mappa |
| wNumSigns / wSignCoords | D4B4 / D4B5 | ✅ | numero di cartelli, poi coppie (Y, X); verificato con i 4 cartelli del paese iniziale |
| wTilesetBank | D52F | ✅ | banco ROM del set di tile |
| wTilesetBlocksPtr | D530 | ✅ | blocchi da 16 tile |
| wTilesetGfxPtr | D532 | 🟡 | |
| wTilesetCollisionPtr | D534 | ✅ | lista dei tile calpestabili nel **banco 1** della ROM, chiusa da 0xFF |
| wTilesetTalkingOverTiles | D536 (3) | 🟡 | tile "bancone" |
| wGrassTile | D539 | 🟡 | 0x52 all'esterno |

## Da trovare
| Variabile | Uso | Stato |
|---|---|---|
| wTextBoxID, stato del menu di lotta, dati dell'avversario | dialoghi e comandi di lotta | ⏳ |
| wWalkBikeSurfState | a piedi / bici / surf | ⏳ (probabile +5) |
| Tabelle nella ROM: tipi e PP massimi delle mosse, nomi creature e strumenti, collisioni | menu nativi, percorsi | ⏳ |

## Menu di lotta (verificati sul gioco vero)
| Menu | CC24 (Y) | CC25 (X) | Elementi | Cursore ▶ (0xED) nella mappa dello schermo |
|---|---|---|---|---|
| Principale, colonna sinistra | 0x0E | 0x07 | 0 = lotta, 1 = strumenti | colonna X, riga 14 + 2 × elemento |
| Principale, colonna destra | 0x0E | 0x0E | 0 = squadra, 1 = fuga | colonna X, riga 14 + 2 × elemento |
| Mosse | 0x0C | 0x05 | 1 = prima mossa … 4 | colonna 5, riga 12 + elemento |
| Domanda Sì/No (soprannome) | 0x08 | 0x0F | 0 = Sì, 1 = No | colonna X, riga Y + 2 × elemento; tasti osservati 0x03; lettere S (0x92) e N (0x8D) accanto |

Testo in attesa di un tocco: freccia ▼ (0xEE) nell'angolo in basso a destra del riquadro (colonna 18, riga 16).

Le variabili restano in memoria anche a menu chiuso: il menu è attivo solo se c'è il cursore sullo schermo.

## Schermata dei nomi (verificata sul gioco vero)
| Cosa | Indirizzo / posizione | Note |
|---|---|---|
| Nome in corso | CF4F | codici del gioco, terminato da 0x50 (US: wStringBuffer) |
| Lunghezza del nome | CEE9 | aggiornata a ogni ridisegno |
| Minuscole attive | CEEB | 0 = maiuscole, 1 = minuscole (SELECT o riga 6) |
| Colonna del cursore | CC25 | X = 1, 3, …, 17 |
| Riga del cursore | ▶ (0xED) nella colonna X, righe 5, 7, …, 15 | CC26 vale 0 quasi sempre: il gioco lo usa per animare l'icona |
| Variabili del menu | CC24 = 3, CC28 = 7, CC29 = 0xFF | firma della schermata |
| Riquadro | (0,4)–(19,14): angoli 0x79 0x7B 0x7D 0x7E | |
| Trattini | da (10,3): 0x76, alzato 0x77 | 7 = giocatore/rivale, 10 = soprannome |
| Griglia | lettera (riga r 1–5, colonna X) in (X+1, 3+2r) | A–Z 0x80–0x99, a–z 0xA0–0xB9, spazio 0x7F, × 0xF1, ( ) : ; [ ] 0x9A–0x9F, PK 0xE1, MN 0xE2, - 0xE3, ? 0xE6, ! 0xE7, ♂ 0xEF, ♀ 0xF5, / 0xF3, . 0xF2, , 0xF4, FINE 0xF0 |
| Titolo | riga 1 ("NOME TUO?", nome della creatura) + inizio riga 3 | |
Tasti: A aggiunge la lettera, B cancella l'ultima, SELECT cambia maiuscole/minuscole, START conferma.

## Oggetti attivabili (persone e oggetti che il gioco può nascondere)
| Cosa | Indirizzo | Note |
|---|---|---|
| Bit "nascosto" per indice globale | D5AA (32 byte) | bit = indice & 7 del byte indice >> 3; a Pallet il bit 0 (Prof. Oak) è 1 |
| Lista della mappa corrente | D5D2 | coppie (sprite, indice globale), fine 0xFF; Pallet: 01 00 FF |
Sprite (C100 + 16·n): +0 immagine, +2 indice immagine (0xFF = non disegnato), +4 Y pixel, +6 X pixel, +9 direzione
(0 giù, 4 su, 8 sinistra, 12 destra). C200 + 16·n: +4 Y mappa (+4), +5 X mappa (+4), +7 erba (0x80), +0x0E grafica (base).
Tile della grafica: (base − 1) × 12 (0x7C per base 0x0C); ferma giù 0–3, su 4–7, sinistra 8–11, destra = sinistra ribaltata.

## Borsa e Pokédex (verificati sul gioco vero)
| Cosa | Indirizzo | Note |
|---|---|---|
| Numero di strumenti | D321 | max 20 |
| Strumenti | D322 | coppie (strumento, quantità), chiuse da 0xFF; Pozione = 0x14 |
| Pokédex presi | D2FB (19 byte) | bit n−1 = numero n; Pikachu = byte 3 bit 0 |
| Pokédex visti | D30E (19 byte) | Eevee (133) = byte 16 bit 4 dopo la lotta col rivale |

## Livelli dello schermo per menu e testi
I menu e i testi sono disegnati sulla **finestra** (LCDC bit 5 acceso, mappa del bit 6: 0x9C00 al menu iniziale),
non sullo sfondo. I tile del carattere 0x80–0xFF stanno a 0x8800; i riquadri 0x79–0x7F seguono l'indirizzamento di LCDC bit 4.

## Menu START e STRUMENTI (verificati sul gioco vero)
| Schermata | Riconoscimento | Note |
|---|---|---|
| Menu START | CC24 = 2, CC25 = 0x0B, CC29 = 0xCB; ▶ in (11, 2+2i) | voce "STRUM." trovata dalle lettere (12…16, riga): l'ordine cambia col Pokédex |
| Lista strumenti | CC24 = 4, CC25 = 5, CC29 = 0x07; ▶ in (5, 4+2c) | 4 righe visibili; strumento = CC36 (scorrimento) + c; nome da colonna 6; ESCI in fondo |
| USA / GETTA | CC24 = 0x0B, CC25 = 0x0D, CC28 = 1, CC29 = 3; "U" in (14,11) | ▶ in (13, 11+2c) |
| Quantità | riquadro (15,9)–(19,11), "×" in (16,10), cifre (17,10)–(18,10) | SU/GIÙ cambiano la quantità |
| Conferma | testo con ▼ (18,16), poi Sì/No (CC24 = 8, CC25 = 0x0F) | |
Altre variabili: CC2B ultima voce squadra/PC, CC2C ultima voce della borsa, CC2D ultima voce START, CC36 scorrimento.

## Struttura di una creatura in squadra (D16F + 44·n, verificata con Pikachu L5/L6)
specie +0, PS +1 (2 byte, big-endian), stato +4, tipi +5/+6, mosse +8…+11, esperienza +14 (3 byte), PP +29…+32 (bit 0–5 PP, bit 6–7 PP Su),
livello +33, PS massimi +34, attacco +36, difesa +38, velocità +40, speciale +42.

## Tabelle nella ROM (lette a ogni avvio dalla ROM dell'utente, mai copiate nell'app)
- Statistiche di base: 0x383DE, 151 voci da 28 byte in ordine di Pokédex (+19 = velocità di crescita 0–5).
- Nomi degli strumenti: 0x45B8 (banco 1), 97 nomi chiusi da 0x50 (0x01–0x61); 0xBC = "é" (POKé).
- Numero del Pokédex per indice interno: 0x41043, 190 byte (Pikachu 0x54 → 25).
- Nomi delle specie: 0xE8000, 190 voci da 10 byte riempite con 0x50, per indice interno (1 = primo).
| Tabella | Offset nella ROM | Stato | Note |
|---|---|---|---|
| Nomi delle mosse | 0xBC000 (banco 0x2F) | ✅ | 165 nomi chiusi da 0x50; verificati alle posizioni 45 e 84 |
| Dati delle mosse | 0x38000 (banco 0x0E) | ✅ | 165 voci da 6 byte: id, effetto, potenza, tipo, precisione, PP |
| Nomi dei tipi | puntatori a 0x27D63 (banco 0x09) | ✅ | 27 puntatori (tipi 0x00–0x1A) verso nomi chiusi da 0x50 |

Caratteri accentati trovati: 0xBE = À, 0xC6 = É.

## Passi calpestabili (come li calcola il gioco)
Passo (x, y) → blocco `wOverworldMap[(y/2 + 3) × (larghezza + 6) + x/2 + 3]` → tile in basso a sinistra del
quadrato 2×2: `blocchi[blocco × 16 + ((y%2)×2 + 1) × 4 + (x%2)×2]` → calpestabile se è nella lista dei tile
calpestabili. Verificato sulla camera e sul paese iniziale confrontando con i passi provati nel gioco.

## Sequenza di prova riproducibile (motore SameBoy, `tools/native-test/runner.c`)
Nuova partita con nomi predefiniti → camera → scendere → uscire → nord fino all'erba alta (scena di Oak)
→ nel laboratorio **esaminare la Poké Ball sul tavolo** (avvia la consegna del compagno) → rispondere NO al soprannome
→ scendere di 3 passi: lotta con il rivale. Gli script di prova restano fuori dal repository insieme alla ROM.

## Metodo
1. Nuova partita automatica con nomi predefiniti (`newgame.py`).
2. Passi e menu in condizioni note, confronto della memoria prima/dopo.
3. Le variabili vicine si deducono dall'ordine in pret/pokeyellow e si verificano con valori noti
   (soldi iniziali, nome scelto, dati della mappa).
