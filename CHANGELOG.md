# CHANGELOG

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
