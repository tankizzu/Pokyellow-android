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

## Da trovare
| Variabile | Uso | Stato |
|---|---|---|
| wTextBoxID, stato del menu di lotta, dati dell'avversario | dialoghi e comandi di lotta | ⏳ |
| wWalkBikeSurfState | a piedi / bici / surf | ⏳ (probabile +5) |
| Tabelle nella ROM: tipi e PP massimi delle mosse, nomi creature e strumenti, collisioni | menu nativi, percorsi | ⏳ |

## Tabelle nella ROM (lette a ogni avvio dalla ROM dell'utente, mai copiate nell'app)
| Tabella | Offset nella ROM | Stato | Note |
|---|---|---|---|
| Nomi delle mosse | 0xBC000 (banco 0x2F) | ✅ | 165 nomi chiusi da 0x50; verificati alle posizioni 45 e 84 |

Caratteri accentati trovati: 0xBE = À, 0xC6 = É.

## Sequenza di prova riproducibile (motore SameBoy, `tools/native-test/runner.c`)
Nuova partita con nomi predefiniti → camera → scendere → uscire → nord fino all'erba alta (scena di Oak)
→ nel laboratorio **esaminare la Poké Ball sul tavolo** (avvia la consegna del compagno) → rispondere NO al soprannome
→ scendere di 3 passi: lotta con il rivale. Gli script di prova restano fuori dal repository insieme alla ROM.

## Metodo
1. Nuova partita automatica con nomi predefiniti (`newgame.py`).
2. Passi e menu in condizioni note, confronto della memoria prima/dopo.
3. Le variabili vicine si deducono dall'ordine in pret/pokeyellow e si verificano con valori noti
   (soldi iniziali, nome scelto, dati della mappa).
