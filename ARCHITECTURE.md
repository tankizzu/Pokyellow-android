# ARCHITECTURE — Proposta tecnica

> **Stato: PROPOSTA — non ancora approvata.** Nessuna implementazione finché
> la decisione D-001 in `DECISIONS.md` non è approvata.

## 1. Alternative valutate

### A. Motore Game Boy compatibile + app Android nativa
Il codice originale del gioco gira dentro un core di emulazione; l'app fornisce
schermo, audio, controlli e interfaccia.

### B. Ricostruzione completa in un motore nativo
Riscrivere in Kotlin logica, mappe, eventi, combattimenti, IA, ecc.

### Confronto

| Criterio | A (emulazione + ponte nativo) | B (ricostruzione) |
|---|---|---|
| Fattibilità | Alta | Bassa: ~150 Pokémon, 165 mosse, ~250 mappe, centinaia di script/eventi, specificità di Giallo (Pikachu, amicizia, animazioni) |
| Fedeltà | Totale: gira il codice originale (inclusi bug e casualità originali) | A rischio: ogni regola (formula danni, bug storici, IA, eventi) va riscritta e ogni differenza tradisce il gioco |
| Prestazioni | Ottime (un GBC usa una frazione minima di un telefono moderno) | Ottime |
| Complessità | Media-alta, concentrata nel "ponte" | Altissima |
| Compatibilità Android | Alta (core in C compilato con NDK) | Alta |
| Personalizzazione UI | Alta: menu, battaglie, inventario, dialoghi nativi. Il mondo di gioco resta 160×144 | Totale |
| Manutenibilità | Buona: si migliorano schermate una per volta senza toccare le regole | Pesante: anni di correzioni di logica |
| Aspetti legali | La ROM resta dell'utente, mai distribuita | Grafica/testi/musica non distribuibili: andrebbero comunque estratti dalla ROM dell'utente, quindi serve decodificare tutta la ROM italiana |

## 2. Soluzione proposta: **A potenziata ("ibrida")**

Il gioco originale fa da **arbitro** (regole, collisioni, combattimenti, eventi).
L'app Android fa da **schermo e telecomando intelligente**: capisce cosa sta
succedendo leggendo la memoria del gioco e traduce i gesti touch in sequenze di
tasti, verificando ogni passaggio.

```
┌──────────────────────────── App Android (Kotlin) ────────────────────────────┐
│ UI nativa (Jetpack Compose)                                                   │
│  Home · Dialoghi · Battaglia · Squadra · Borsa · Pokédex · Scheda · Opzioni   │
│        ▲ stato di gioco                         │ "intenzioni" (vai lì,        │
│        │                                        ▼  usa mossa 2, usa oggetto)  │
│ Ponte di gioco (Kotlin puro, testabile)                                       │
│  • Lettore di stato: RAM → modello (posizione, mappa, squadra, menu attivo…)  │
│  • Pathfinding sulla griglia di gioco con le regole di collisione del gioco   │
│  • Esecutore a circuito chiuso: tasto → verifica in RAM → tasto successivo    │
│        ▲ lettura RAM/VRAM/ROM                   │ tasti virtuali              │
│ Core di emulazione GB/GBC (SameBoy, C via NDK) ◄── ROM scelta dall'utente     │
│        │ immagini 160×144                       │ audio                        │
│        ▼                                        ▼                             │
│ Renderer (OpenGL ES, scala nitida)       Audio (AAudio / AudioTrack)          │
│ Salvataggi: SRAM .sav (atomico, con copie) + istantanea di ripresa rapida     │
└───────────────────────────────────────────────────────────────────────────────┘
```

### Principio di sicurezza della fedeltà
L'app **non scrive mai** nella memoria di gioco per cambiare lo stato: si limita a
**premere tasti** come farebbe un giocatore. Collisioni, regole e risultati
restano decisi dal gioco originale → è impossibile attraversare ostacoli o
alterare meccaniche per errore dell'app.

### Situazioni non riconosciute
Se il ponte non riconosce una schermata, l'app non blocca il giocatore: mostra
la schermata originale con controlli contestuali temporanei (da definire in
Fase 2). Ogni schermata coperta nativamente riduce questi casi.

## 3. Core di emulazione
Candidato: **SameBoy** (C, molto accurato, licenza Expat/MIT verificata sul file
LICENSE del progetto; include boot ROM open source, quindi nessun file Nintendo
oltre alla ROM). La compilazione per Android non è documentata dal progetto:
va verificata come primo passo del prototipo.
Alternative considerate: Gambatte (GPLv2: obbligherebbe a rilasciare l'app sotto
GPL), mGBA (MPL-2.0), core scritto da zero (più controllo, più rischio).

## 4. Mappa della memoria — versione italiana
Il progetto pubblico pret/pokeyellow documenta la versione inglese (US/UE).
Per la versione italiana gli indirizzi **non vanno dati per scontati**: saranno
ricostruiti e verificati (confronto di schemi di codice + osservazione della
memoria durante il gioco) con test automatici. È un prerequisito del ponte.

## 5. Struttura moduli (bozza, da confermare in Fase 2)
```
app/        Activity, navigazione, schermate Compose, renderer, audio
core-emu/   SameBoy (C) + wrapper JNI
bridge/     lettore stato, mappa memoria IT, pathfinding, esecutore comandi
storage/    salvataggi .sav, ripresa rapida, import/export
tools/      strumenti di analisi ROM per lo sviluppo (mai inclusi nell'APK)
```

## 7. Vista estesa a tutto schermo (D-015, realizzata in 0.3.0 – D-031)
Il Game Boy produce un'immagine 160×144 (10×9 caselle). Per riempire uno schermo verticale
senza deformare né tagliare i lati, l'app disegna da sé la mappa sopra e sotto l'inquadratura originale:
- blocchi della mappa corrente letti dalla memoria del gioco (il gioco tiene in RAM la mappa intera con un bordo);
- grafica delle caselle letta dalla memoria video (VRAM) o dalla ROM dell'utente sul telefono;
- personaggi fuori inquadratura disegnati dalle loro posizioni in RAM.
Le regole restano del gioco: la vista estesa è solo presentazione.
Conseguenze: si vede più mappa dell'originale (opzione per attenuarla o nasconderla);
lavoro aggiuntivo nel ponte; casi limite ai confini tra mappe e nelle schermate speciali
(titolo, sequenze animate), dove si mostra l'immagine originale a tutta larghezza con lo sfondo riempito.
La lotta non ha bisogno della vista estesa: lo sfondo originale è a tinta unita e viene esteso,
scena e riquadro del testo vengono ritagliati dall'immagine originale e posizionati separatamente.
Gli indirizzi di memoria esatti per la versione italiana sono in docs/MEMORY_MAP_IT.md.

**Come funziona (0.3.0, `app/src/main/cpp/extended_view.c`):**
1. Mappa: blocchi da `wOverworldMap` (C6E8, con bordo di 3 blocchi), blockset dalla ROM dell'utente (banco D52F, puntatore D530).
2. Posizione: stima dalle coordinate del giocatore, poi confronto delle 20 × 18 caselle visibili nella memoria video
   (con lo scorrimento SCX/SCY) con la mappa, spostamenti −3…+3; serve un accordo ≥ 300/360, altrimenti la vista si spegne.
3. Colori: per ogni tile si impara l'attributo (palette, banco, specchiatura) dalle caselle visibili; palette CGB dalla
   memoria del motore, convertite con la stessa correzione colore dello schermo.
4. Al centro si copia il fotogramma originale; sopra e sotto si disegna la mappa. Se una finestra copre lo schermo
   (menu a tutto schermo) e non mostra la mappa, la vista si spegne.
5. L'app chiede un'altezza (160 × altezza dello schermo / larghezza, max 512) e disegna il risultato a tutta larghezza.
6. Persone e oggetti: quelli visibili dalla memoria degli sprite (OAM), solo per le parti fuori dal centro;
   quelli che il gioco non disegna (immagine 0xFF) dalla tabella degli sprite: posizione sullo schermo a 8 bit
   ricondotta a quella stimata dalle coordinate sulla mappa, direzione, grafica già in memoria video, erba alta.
   Gli oggetti nascosti dal gioco (lista D5D2 + bit D5AA) non vengono disegnati.
Da fare: lotta a tutto schermo, opzione mappa attenuata/nascosta.

## 6. Tecnologie
Kotlin · Jetpack Compose · Android NDK (C) · OpenGL ES · AAudio/AudioTrack ·
file locali con scrittura atomica (nessun database necessario al momento).
L'app **non richiederà il permesso Internet**.
Versioni esatte di SDK/AGP/Compose: da fissare in Fase 2 consultando la
documentazione ufficiale.
