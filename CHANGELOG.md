# CHANGELOG

## 2026-11-18 (promemoria 05:16 UTC)
- Trovati set di tile corrente (D52F–D539), lista dei tile calpestabili nella ROM (banco 1) e cartelli (D4B4).
- Nuova griglia dei passi calpestabili (`MapGrid`), percorso più breve, posti da cui parlare (anche attraverso i banconi).
- Nuovo esecutore di camminata a circuito chiuso (`WalkExecutor`): un passo alla volta, si ferma se il gioco prende il controllo, se un passo non riesce o se si usa la levetta.
- App: toccare una persona, un oggetto a terra, un cartello o un bancone porta lì e fa parlare/esaminare. Toccare il terreno non fa camminare (scelta del Laboratorio, D-012); toccare il proprio personaggio = tasto A. Fuori dal movimento libero (testi, menu, lotta) il tocco resta il tasto A.
- Test: 28 superati, di cui 4 sul gioco vero (camminata fino al passo scelto nel paese iniziale).

## 2026-11-18 (promemoria 04:16 UTC)
- Trovati nella ROM italiana i dati delle mosse (0x38000: tipo e PP massimi) e i nomi dei tipi (puntatori a 0x27D63).
- Pulsanti di lotta completi: nome della mossa, tipo con colore e PP attuali/massimi, tutto letto dalla ROM dell'utente all'avvio.
- Test: 20 superati (17 su dati sintetici, 3 con la ROM vera in locale: mossa scelta, risposta Sì/No, tabelle coerenti).

## 2026-11-18 (promemoria 03:16 UTC)
- Domande Sì/No riconosciute dalla memoria e dal cursore sullo schermo; pulsanti nativi "Sì" e "No" sopra il riquadro originale, a destra dell'immagine di gioco.
- Codice generalizzato: `GameMenu.kt` con `MenuReader`, `GameCommand` (lotta + risposte) e `CommandExecutor`.
- Lo stato letto 4 volte al secondo include ora il menu attivo.
- Test: 15 su dati simulati + 2 sul gioco vero (scelta della seconda mossa; risposta "No" al soprannome con dati del compagno completati). Tutti superati. APK ricompilato.

## 2026-11-18 (promemoria 02:16 UTC)
- Mappati i menu di lotta della versione italiana (menu principale a due colonne, menu delle mosse) e la regola "menu attivo solo se il cursore è sullo schermo".
- Nuovo esecutore a circuito chiuso (`BattleMenu.kt`): sceglie mossa, strumenti, squadra o fuga premendo un tasto alla volta e controllando la memoria dopo ogni pressione; si ferma da solo se la lotta finisce o se non vede il menu entro 6 s.
- App: durante la lotta compaiono i pulsanti delle mosse (nome letto dalla ROM dell'utente, PP) e Squadra / Borsa / Fuga; la barra Menu/Select/Salva si nasconde. Squadra e Borsa aprono per ora i menu originali del gioco.
- Test: 13 su menu simulato + 1 prova sul gioco vero (motore compilato per il computer, ROM fuori dal repository): l'esecutore sceglie la seconda mossa e il suo PP scende di 1. Tutti superati.

## 2026-11-18 (promemoria 01:16 UTC)
- Trovati e verificati: creatura in campo durante la lotta (wBattleMon D018: mosse, PP, PS, livello) e tabella dei nomi delle mosse nella ROM italiana (0xBC000, 165 voci).
- Ponte: legge tipo di lotta e creatura in campo; `RomData` legge i nomi delle mosse dalla ROM dell'utente all'avvio.
- La squadra in alto si nasconde durante la lotta.
- 7 test automatici, tutti superati (solo dati sintetici, nessun contenuto del gioco). APK ricompilato.

## 2026-11-18 (promemoria 00:16 UTC)
- Nuovo strumento di prova sul motore SameBoy (`tools/native-test/runner.c`): esegue sequenze di tasti, salva stati, immagini e memoria (solo in cartelle temporanee).
- Lettura della squadra **verificata su partita vera**: il primo compagno risulta a D16F (livello 5, PS 19/19) con soprannome a D2B9.
- Trovati wIsInBattle (D05B) e wJoyIgnore (CD6B); aggiornati `docs/MEMORY_MAP_IT.md` e `MemoryMapIt.kt`.

## 2026-11-18 (promemoria 23:16 UTC)
- Nuovo "ponte" verso il gioco (`app/src/main/java/it/giallotouch/bridge/`): indirizzi della versione italiana, decodifica dei nomi, lettura di squadra (nome, livello, PS, stato), soldi, mappa e posizione. Solo lettura, mai scrittura nella memoria del gioco.
- Il motore copia la memoria a blocchi (`nativeReadRange`); la partita aggiorna lo stato 4 volte al secondo.
- Squadra in alto nell'app con i dati veri (nome, livello, barra PS, pallino rosso se ha uno stato alterato). Compare solo quando la squadra ha almeno un membro.
- 4 test automatici del ponte: tutti superati. APK di debug ricompilato.

## 2026-11-17 (6) – Prototipo 0.1 (APK di prova)
- Progetto Android: Gradle 9.9.1, AGP 9.5.1, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37 (richiesto da Compose 1.12), targetSdk 36, minSdk 26, NDK 30, solo arm64-v8a.
- Motore SameBoy 1.0.5 copiato in `third_party/SameBoy` (MIT) e compilato con l'NDK (`libgbcore.so`).
- Programma di avvio originale scritto per il progetto (`app/src/main/cpp/boot_rom.c`): niente RGBDS, niente codice Nintendo.
- App: scelta del file del gioco dal telefono con controllo dell'impronta, gioco in verticale con pixel nitidi, levetta che compare ovunque, tocco = A, gesto indietro = B, pulsanti Menu (START), Select, Salva.
- Salvataggi: punto esatto salvato all'uscita e ripreso all'avvio, salvataggio del gioco ogni 20 s se cambiato, scritture atomiche con copia di riserva.
- Prova su computer (`tools/native-test/harness.c`): il gioco parte, audio alla velocità giusta, salvataggio/ripristino dello stato corretto.

## 2026-11-17 (5)
- Avviata la Fase 1b: primi indirizzi di memoria della versione italiana (`docs/MEMORY_MAP_IT.md`).
- Aggiunti gli strumenti di analisi `tools/rom-analysis/` (nessun contenuto del gioco).
- Ambiente cloud dedicato con Android SDK/NDK installati; versioni di riferimento in DECISIONS (D-021).
- Promemoria orario per continuare il lavoro.

## 2026-11-17 (4)
- Laboratorio v3: gioco a tutto schermo con mappa estesa (10 × 22 caselle), squadra in alto e pulsanti in basso sovrapposti, levetta che compare ovunque (tolta la zona dedicata), lotta a tutto schermo con comandi sovrapposti allo sfondo.
- Nuova opzione per la mappa fuori dall'inquadratura originale: visibile, attenuata, nascosta.
- "Sposta e ridimensiona" aggiornata ai nuovi elementi (squadra, pulsanti, dialoghi, Sì/No; scena, testo, mosse, azioni in lotta) con l'inquadratura originale evidenziata.
- ARCHITECTURE.md §7: vista estesa.

## 2026-11-17 (3)
- Laboratorio: nuova modalità "Sposta e ridimensiona" (riquadri trascinabili, cerchio per ridimensionare, allineamento automatico ai bordi, Annulla, Ripristina), per esplorazione e lotta.
- Predefiniti aggiornati alla configurazione inviata dal committente (menu a tutto schermo, Sì/No sul gioco).
- Tolta la velocità del testo dalle regolazioni del Laboratorio: resta nel menu Opzioni del gioco.
- Se il dispositivo non ha una configurazione salvata, il Laboratorio riprende l'ultima inviata.

## 2026-11-17 (2)
- Registrate le decisioni del committente: verticale principale, levetta dinamica, barra di pulsanti con icone, menu a scorrimento, salvataggio ovunque, CMF Phone 2 Pro.
- Aggiunto il Laboratorio UX interattivo `docs/demo/laboratorio-giallo-touch.html`
  (levetta, barra riordinabile, menu Squadra/Borsa/Pokédex/Scheda/Negozio/PC/Allenatore/Salva/Opzioni, lotta).
  Grafica e nomi sono segnaposto originali, nessun contenuto del gioco.

## 2026-11-17
- Analisi iniziale (Fase 1): header ROM, confronto architetture, rischi, roadmap.
- Creata la documentazione di progetto.
- Aggiunto `.gitignore` che impedisce di committare ROM, salvataggi e file derivati.
