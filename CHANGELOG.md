# CHANGELOG

## 2026-11-18 – scheda allenatore, START e lotta dal mockup (D-045)
- **START aperto**: squadra in alto, riquadro in basso con il nome del giocatore, POKéDEX, OPZIONI, BORSA, SALVA.
- **Scheda allenatore**: tocco sul nome → l'app apre la scheda vera del gioco e la ridispone (dati, medaglie), con
  sotto un riquadro con Pokédex e squadra e il pulsante INDIETRO che chiude scheda e menu (verificato).
- **Lotta**: testo in alto, scena, riquadro SQUADRA/BORSA/FUGA e riquadro mosse con i PP (verificato con foto).
- In lotta il menu del gioco (LOTTA/PKMN/STRUM./FUGA) non compare più nel riquadro del testo: le azioni sono nei
  riquadri dell'app (verificato con foto).
- Banco di prova esteso: lotta e scheda allenatore (70 prove superate).

## 2026-11-18 – tocco nel vuoto per parlare, colori corretti, START nei dialoghi (D-044)
- Riquadri e scritte dell'app di nuovo in bianco e nero come il gioco (sulla mappa prendevano il rosso della palette).
- Tocco nel vuoto vicino a una persona o un cartello: il personaggio si gira e parla (verificato: dialogo col Professore).
- START sparisce durante i dialoghi e torna alla fine (verificato); in lotta resta nascosto.
- OPZIONI: tolto "menu del gioco". SELECT nello stile del gioco, solo quando c'è un menu del gioco con il cursore.

## 2026-11-18 (promemoria 10:16 UTC) – Borsa nello stile del gioco, START, spostare gli strumenti (D-043)
- Il pulsante in basso si chiama **START**.
- **Borsa nello stile del gioco**: lista con quantità, tocco su uno strumento → USA / GETTA / ANNULLA; INDIETRO e **SELECT**.
- **Spostare uno strumento**: tenere premuto e trascinare, oppure SELECT e poi toccare il nuovo posto; l'app esegue lo
  scambio nel menu originale del gioco (SELECT, SELECT). Verificata la logica; sul gioco vero manca ancora una prova con
  almeno due strumenti nella borsa.
- Banco di prova dell'interfaccia esteso alla Borsa.

## 2026-11-18 – 0.4.1-prototipo: correzioni verificate sull'app vera
- Corretti B-002 (lettere rovinate sulla mappa), B-003 (MENU e Select sul titolo), B-004 (MENU invisibile in gioco).
- Nuovo **banco di prova dell'interfaccia** (D-042): l'app intera gira sul computer su un telefono simulato e viene
  fotografata: accensione, menu iniziale con CONTINUA, Carica partita, partita caricata, MENU, MENU aperto con la
  squadra, OPZIONI, schermata del nome. Tutte verificate prima della consegna.
- Spazio sopra e sotto l'immagine del gioco del colore del suo bordo (bianco su titolo e menu).
- Tolta la vecchia striscia scura della squadra: la squadra è nel pannello MENU (D-040).
- 67 test superati (3 dell'interfaccia).

## 2026-11-18 – schermata del nome nello stile del gioco (D-040)
- La griglia delle lettere del gioco sparisce; restano la domanda del gioco (es. "NOME TUO?"), il nome scritto con la
  tastiera del telefono sopra i trattini del gioco e il riquadro FINE, con i tile del gioco, appoggiati sopra la tastiera.
- Tocco su FINE o "Fatto" sulla tastiera = conferma; il nome viene scritto nel gioco come prima (esecutore).

## 2026-11-18 – Carica partita e pulsanti con il cursore (D-040, D-041)
- **Carica partita** nello stile del gioco, posizioni del mockup: riquadro del titolo, un riquadro per salvataggio
  (SALVATAGGIO/AUTOMATICO e data, GIOCA nome, MEDAGLIE, TEMPO), INDIETRO. Si tocca il riquadro da caricare,
  oppure si sposta il cursore ▶ trascinando su/giù e si conferma con un tocco.
- **Riquadro dei pulsanti**: cursore ▶ mosso dalla levetta (griglia 2×2), tocco fuori dal riquadro = conferma;
  MENU o il gesto indietro chiudono il pannello.

## 2026-11-18 – menu iniziale ridisegnato con il tocco e il cursore (D-040, D-041)
- Il menu iniziale del gioco viene ridisegnato con i tile del gioco nello stesso fotogramma dell'immagine (nessun
  lampeggio del menu originale): riquadro 18×14 con CONTINUA, CARICA PARTITA, OPZIONI, NUOVO GIOCO come nel mockup.
- Due modi per scegliere: **tocco diretto** sulla voce, oppure **cursore ▶** mosso con la levetta e confermato con un
  tocco fuori dalle voci. CONTINUA/NUOVO GIOCO/OPZIONI vengono scelti nel menu vero del gioco dall'esecutore.
- Il tempo di gioco (DA45/DA47) viene salvato con i salvataggi dell'app.
- 63 test superati.

## 2026-11-18 – pulsanti di gioco come nel mockup del committente (D-040)
- MENU in basso al centro, disegnato con i tile del gioco; aperto mostra in alto la **squadra completa in piccolo**
  (nome, livello, barra della vita con i colori del gioco e PS, barra dell'esperienza) e sotto il riquadro
  **POKéDEX / BORSA / OPZIONI / SALVA**. Nessun cursore: si tocca la voce. Toccare un compagno apre la sua scheda.
- **OPZIONI** nello stile del gioco: testo automatico (NO/LENTO/MEDIO/VELOCE) e accesso al menu originale del gioco.
- La **mappa** è 5 caselle più in basso (il motore sposta l'inquadratura e riempie lo spazio con la mappa); i tocchi
  su persone e cartelli seguono lo spostamento.
- Esperienza come nel gioco: velocità di crescita dalla ROM (statistiche di base a 0x383DE) e formule del gioco.
- 62 test superati.

## 2026-11-18 (promemoria 08:16 UTC) – aggiunte disegnate con i tile del gioco
- Il motore fornisce all'app i **tile veri del gioco** (carattere, riquadri, cursore ▶) con i colori dei riquadri
  di testo, letti dalla memoria video (sfondo e finestra). Nuovo disegnatore `TileCanvas` sulla griglia di 8 pixel.
- Verificato sul gioco vero: il riquadro del menu iniziale ridisegnato dall'app è **identico pixel per pixel** a quello del gioco.
- Linguetta laterale e riquadro dei pulsanti ora sono disegnati come il gioco (come nel mockup 6A/6B): riquadro uguale al
  menu START con SQUADRA, BORSA, POKéDEX, MENU, TESTO, SALVA, ESCI e cursore ▶. Tocco lungo sulla linguetta = cambia lato.
- Strumenti di prova: comandi `vram` e `reset` del runner, `tools/mockup/` (immagini del gioco solo in privato).
- 61 test superati.

## 2026-11-18 – avvio pulito e pulsante laterale (richiesta del committente)
- **Prima di iniziare a giocare non c'è nessun pulsante dell'app**: titolo, menu iniziale, introduzione e nome mostrano
  solo il gioco (più CARICA PARTITA nel menu iniziale e la tastiera del telefono per il nome).
- **Durante il gioco i pulsanti compaiono da un pulsante laterale** (linguetta sul bordo, a destra; si può spostare a
  sinistra dal pannello stesso): Squadra, Borsa, Pokédex, Menu, Testo auto, Salva in colonna. Toccare un pulsante
  o il gioco richiude il pannello. La lotta mantiene i suoi pulsanti.
- Nuovo **Laboratorio d'avvio** (`docs/demo/laboratorio-avvio.html`): sei schermate dall'accensione alla cameretta,
  elementi da spostare e ridimensionare, scelte di stile "in tema" (riquadro del gioco, giallo Pikachu, scuro attuale),
  carattere a pixel, lato e forma del pulsante laterale, disposizione dei pulsanti; invio delle scelte a Claude.
- Lo stile grafico definitivo "in tema" si applicherà dopo le scelte del committente nel Laboratorio.

## 2026-11-18 – Borsa: Usa e Getta
- Nella Borsa nativa, toccando uno strumento compaiono **Usa** e (se non è uno strumento chiave) **Getta** con la quantità.
- L'app esegue l'azione nel menu originale, come farebbe il giocatore: START → STRUM. → strumento → USA/GETTA;
  per Getta sceglie la quantità, conferma "Sì" e chiude i menu. Per Usa si ferma lì e il gioco continua
  (per esempio chiedendo su chi usarlo). Controllo di sicurezza: se sulla riga non c'è lo strumento atteso, si ferma.
- Schermate del menu STRUMENTI della versione italiana documentate (menu START con ordine variabile, lista con
  scorrimento CC36, USA/GETTA, quantità, conferma).
- 60 test superati (3 nuovi sul gioco vero: Getta la Pozione in ~3,5 s, Usa arriva al gioco, nome sbagliato → stop).

## 2026-11-18 – menu Borsa e Pokédex nativi
- **Borsa** a tutto schermo: un'unica lista nello stesso ordine del gioco, con le quantità; gli strumenti chiave
  (come nel gioco) senza quantità. Nomi dalla ROM dell'utente (0x45B8), macchine come MN01/MT01.
- **Pokédex** a tutto schermo: visti e presi, elenco fino al numero più alto visto; le specie non viste restano senza nome.
- Barra in basso: Squadra, Borsa, Pokédex, Menu, Testo, Salva. **Select** compare sopra la barra solo quando è aperto
  un menu del gioco (serve solo lì).
- Memoria italiana verificata: borsa D321/D322 (Pozione ritirata dal PC della cameretta), Pokédex presi D2FB, visti D30E.
- 57 test superati (nuovo sul gioco vero: borsa con la Pozione).

## 2026-11-18 (promemoria 07:17 UTC) – menu Squadra nativo
- **Menu Squadra** a tutto schermo (D-007, D-008, D-012): elenco dei compagni (nome, specie, livello, stato, PS)
  e **scheda** di ognuno (tipi, PS, attacco, difesa, velocità, speciale, mosse con tipo e PP). Solo lettura.
  Si apre dal pulsante "Squadra" in basso o toccando un compagno nella fila in alto.
- Barra in basso con **icona e nome** (grafica del Laboratorio): Squadra, Menu, Testo, Select, Salva;
  in lotta Squadra, Borsa, Fuga con icona.
- Dalla ROM dell'utente: nomi delle specie (0xE8000). Dalla memoria: tipi, mosse, PP (con i PP Su), statistiche.
- 54 test superati.

## 2026-11-18 – avvio come sul Game Boy e "Carica partita" (richiesta del committente)
- A ogni apertura il gioco riparte da capo: introduzione, titolo e menu iniziale del gioco (NUOVO GIOCO, OPZIONI,
  e CONTINUA se c'è un salvataggio del gioco). Tornando all'app dopo averla solo messa da parte, il gioco resta dov'era.
- Nel menu iniziale, sotto il riquadro del gioco, il pulsante **CARICA PARTITA** apre i salvataggi dell'app:
  "Salvataggio" (pulsante Salva) e "Automatico" (all'uscita dall'app), con miniatura, data, nome, medaglie, squadra e soldi.
- "Salva" e il salvataggio automatico funzionano solo durante una partita (non sul titolo); la squadra in alto
  compare solo durante una partita.
- Il vecchio punto di ripresa delle versioni precedenti diventa il salvataggio "Automatico".
- Prima di caricare, il salvataggio del gioco attuale viene copiato a parte (`salvataggio.prima-di-carica.sav`).
- 54 test superati (nuovo sul gioco vero: dall'accensione al menu iniziale riconosciuto).

## 2026-11-18 (promemoria 06:16 UTC) – lotta a tutto schermo
- **Lotta a tutto schermo** (D-018): lo sfondo della lotta riempie lo schermo; la scena (creature e barre, 96 righe)
  sta in alto e il riquadro del testo (48 righe) subito sopra i pulsanti nativi, alla stessa scala, senza deformazioni.
  Si divide solo quando il riquadro del testo è nelle righe 12–17 (scena, menu mosse, scelta squadra);
  le schermate senza riquadro (statistiche) restano intere al centro.
- 50 test superati (uno nuovo sul gioco vero: lotta divisa, mappa no).

## 2026-11-18 – 0.3.0-prototipo (gioco a tutto schermo e tastiera del telefono)
- **Vista estesa**: in esplorazione la mappa riempie tutto lo schermo verticale (circa 10 × 22 caselle invece di 10 × 9).
  Sopra e sotto l'inquadratura originale l'app disegna la mappa con i tile e i colori del gioco presi dalla memoria video;
  al centro resta l'immagine originale esatta (testi, menu, personaggio).
- Allineamento al pixel anche a metà passo: l'app confronta la memoria video con la mappa in RAM e trova lo scorrimento.
- Si spegne da sola (si vede l'immagine 10:9) in lotta, nei menu a tutto schermo, nelle transizioni e quando non è sicura.
- Il tocco su persone e cartelli funziona anche nella parte di mappa aggiunta.
- Nuovo comando `ext` nell'esecutore di prova; 2 nuovi test sul gioco vero (centro identico al fotogramma, spenta in lotta).
- **Tastiera del telefono per i nomi** (D-029): quando il gioco chiede un nome (giocatore, rivale, soprannome)
  la sua tastiera viene coperta da un campo di testo con la tastiera del telefono. Alla conferma l'app scrive il nome
  nel gioco muovendo il cursore sulla griglia originale (circuito chiuso: cancella con B, maiuscole/minuscole con SELECT,
  conferma con START). Accenti tolti, solo i caratteri che il gioco ha; massimo 7 o 10 lettere come nel gioco.
  Se qualcosa non torna, la tastiera del gioco torna visibile con un avviso.
- **Persone e oggetti fuori dall'inquadratura** disegnati nella mappa aggiunta (posizione, direzione e grafica del gioco),
  allineati anche durante il passo; completate le parti tagliate dal bordo. Gli oggetti che il gioco ha nascosto
  (raccolti, persone non presenti) restano nascosti e non si possono toccare (lista del gioco a D5D2/D5AA).
- 47 test superati (14 sul gioco vero): "Ro sa!" scritto come nome del giocatore in ~2 s, un soprannome di 9 caratteri in ~3 s.

## 2026-11-18 – 0.2.2-prototipo
- Pulsante "Testo auto" nella barra in basso: No → Lento → Medio → Veloce (salvato sul telefono). Quando il gioco aspetta un tocco (▼) preme A da solo dopo la pausa scelta.
- Tenendo il dito appoggiato per più di 0,3 s durante un testo, il testo scorre più veloce (impulsi di B).
- Nessuno dei due preme mai nulla se c'è un cursore ▶ (menu, domande Sì/No, tastiera dei nomi).
- 38 test superati; sul gioco vero il testo automatico avanza fino alla domanda Sì/No e si ferma senza rispondere.

## 2026-11-18 – 0.2.1-prototipo
- Correzione B-001: l'app apre il gioco anche dentro uno ZIP, controlla la somma di controllo dell'intestazione,
  mostra il titolo se è un altro gioco e ripara/scarta all'avvio un file salvato non valido dalle versioni precedenti.
- 32 test superati, compreso quello con lo ZIP vero dell'utente (solo in locale).

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
