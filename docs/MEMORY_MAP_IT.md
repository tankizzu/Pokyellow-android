# Mappa della memoria – versione italiana

ROM: `POKEMON YELAPSI`, SHA-1 `05bb8e99f24d498613930949730afa8024e77d08`.
Gli indirizzi sono **ricavati sperimentalmente** su questa ROM (strumenti in `tools/rom-analysis/`).
I nomi delle variabili seguono quelli del progetto pubblico pret/pokeyellow (versione inglese),
usato solo come documentazione dell'ordine delle variabili.

Stato: ✅ verificato · 🟡 dedotto dall'ordine delle variabili, da verificare · ⏳ da trovare

## Mappa e posizione (trovati il 2026-11-17)
| Variabile | Indirizzo | Stato | Note |
|---|---|---|---|
| wCurMap | D362 | 🟡 | valore 0x26 nella camera iniziale |
| wCurrentTileBlockMapViewPointer | D363–D364 | 🟡 | cambia insieme a X |
| wYCoord | D365 | ✅ | cambia di ±1 camminando su/giù |
| wXCoord | D366 | ✅ | cambia di ±1 camminando destra/sinistra |
| wYBlockCoord | D367 | 🟡 | alterna 0/1 |
| wXBlockCoord | D368 | 🟡 | alterna 0/1 |
| wLastMap | D369 | 🟡 | |
| wCurMapTileset | D36B | 🟡 | 0x04 nella camera iniziale |
| wCurMapHeight | D36C | 🟡 | 4 blocchi nella camera iniziale |
| wCurMapWidth | D36D | 🟡 | 4 blocchi nella camera iniziale |

## Da trovare per il prototipo
| Variabile | Uso | Stato |
|---|---|---|
| wOverworldMap (1300 byte) | disegnare la mappa estesa, calcolare percorsi | ⏳ |
| wSpriteStateData1/2 | posizione dei personaggi (NPC, compagno) | ⏳ |
| wPlayerDirection / wPlayerMovingDirection | direzione del giocatore | ⏳ |
| wJoyIgnore, wTextBoxID, wCurrentMenuItem | capire se c'è un dialogo o un menu | ⏳ |
| wIsInBattle | capire se si è in lotta | ⏳ |
| wPartyCount e dati squadra | squadra in alto, menu Squadra | ⏳ |
| wNumBagItems e strumenti | menu Borsa | ⏳ |
| wPlayerMoney (BCD) | soldi | ⏳ |
| wWalkBikeSurfState | a piedi / bici / surf | ⏳ |

## Metodo
1. Nuova partita automatica con nomi predefiniti (`newgame.py`).
2. Passi in direzioni note e confronto della memoria prima/dopo (`findpos2.py`, `dumppos.py`).
3. Le variabili vicine si deducono dall'ordine in pret/pokeyellow e poi si verificano.
