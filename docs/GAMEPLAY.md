# Pokémon Giallo – Android Native Edition: riepilogo del gameplay

Elenco delle regole di gioco e di interfaccia decise con il committente e già realizzate (riferimenti: DECISIONS.md).
Pensato per essere passato a un nuovo progetto. Aggiornato al 2026-11-18 (versione 0.8.1-prototipo).

## 1. Principi generali
- **Gioco originale intatto**: il gioco italiano gira in un emulatore (SameBoy); l'app non scrive mai nella memoria del
  gioco. Ogni azione dell'app è eseguita premendo i tasti nei menu originali del gioco ("esecutori"), aspettando che
  ogni menu sia fermo prima di premere (il gioco ignora i tasti appena un menu compare).
- **Nessuna ROM né contenuto del gioco** nel repository o nell'APK: ognuno usa la propria copia (R-01, D-025).
- **Offline**, nessun server, nessun pagamento (R-02).
- **Verticale** come modalità principale (D-005); telefono di riferimento CMF Phone 2 Pro (D-009).
- **Tutto in tema** (D-038, D-039): ogni aggiunta dell'app è disegnata con le caselle grafiche del gioco (carattere,
  riquadri, cursore) lette dalla memoria video, alla stessa scala e sulla griglia di 8 pixel. Non si deve notare
  che il gioco è stato modificato.
- **Scritte e riquadri dell'app sempre in bianco, nero e due grigi**, mai con i colori della mappa (D-044, D-053).
- **Nessuna pulsantiera fissa** (niente croce, niente A/B sempre visibili) (R-04).
- **Le schermate vecchie del gioco attraversate dall'app non si vedono mai**, nemmeno per un attimo: al loro posto
  resta l'immagine di prima (D-046, D-049).
- **Verifica prima di ogni APK** con il banco di prova (app vera, motore vero, ROM dell'utente, tocchi simulati),
  foto private guardate prima della consegna (D-042).

## 2. Griglia dello schermo
- Larghezza: **20 caselle** (come il Game Boy), altezza circa **45 caselle** in verticale. Le posizioni nei mockup
  sono (colonna, riga) in caselle da 8 pixel; ".5" = mezza casella.
- **Gioco a tutto schermo** (D-015, D-031): sopra e sotto l'inquadratura originale si vede altra mappa (vista
  estesa, circa 10 × 22 caselle di mappa), con persone e oggetti fuori inquadratura.
- **Luoghi collegati** (D-059): sopra e sotto si vede il luogo confinante vero (letto dalla ROM).
- **Colori per luogo** (D-060): ogni luogo all'aperto ha sempre i suoi colori, anche visto da quello accanto.
- **Niente lampeggi** (D-061): una disposizione cambia solo se il cambio dura almeno 0,2 s.
- Se la vista estesa non è sicura (menu a tutto schermo, titolo, lotta) si mostra l'immagine originale: mai
  un'immagine sbagliata.

## 3. Comandi
- **Levetta virtuale** che compare dove si appoggia il dito, ovunque, e sparisce alzandolo (D-006, D-016).
- **Il personaggio si muove solo con la levetta** (D-049): toccare persone o cartelli non fa camminare.
- **Tocco per parlare** (D-044, D-049): un tocco ovunque, quando accanto (o oltre un bancone) c'è una persona o un
  cartello, fa girare il personaggio e parte il dialogo; altrimenti il tocco è il tasto A.
- **Tenendo premuto il dito il testo scorre più veloce** (D-030), mai su menu o domande.
- **Indietro (gesto di Android) = tasto B**.
- **Menu**: si tocca direttamente la voce. Il **cursore ▶ resta** per i giocatori di vecchia data: la levetta su/giù
  lo sposta, un tocco fuori dalle voci conferma (come A) (D-040, D-041).
- **SELECT** compare (riquadro piccolo in basso a destra) solo quando è aperto un menu del gioco che lo usa (D-036, D-044).
- **Spostare l'ordine** (strumenti, squadra): tenere premuto e trascinare, oppure SELECT sul primo e poi sul
  secondo (D-043).

- **Segnala** (D-064): tre dita sullo schermo → file con foto, stato della partita e note, da condividere.

## 4. Avvio e salvataggi
- **A ogni apertura il gioco riparte come sul Game Boy**: introduzione, titolo, menu iniziale (D-034). Senza il logo
  della console (D-022).
- **Menu iniziale** ridisegnato: riquadro 18×14 con CONTINUA, CARICA PARTITA, OPZIONI, NUOVO GIOCO, una ogni 3
  righe, senza cursore (D-040).
- **Salvataggio ovunque** (D-002): istantanea in qualsiasi momento + salvataggio automatico all'uscita dall'app
  (solo durante una partita). **CARICA PARTITA** mostra i salvataggi con miniatura, data, nome, squadra, soldi;
  caricare riporta anche il salvataggio del gioco a quel momento (copia di sicurezza di quello attuale).
- Messaggio dell'app nello stile del gioco: **"Partita salvata"** nel riquadro alla riga del dialogo (D-049).

## 5. Esplorazione
- Nessun dato del giocatore in vista mentre si gioca (D-045).
- **START** in basso al centro (7×4); sparisce durante dialoghi e lotta (D-043, D-044).
- **Dialoghi in alto** (D-048): mentre si cammina il riquadro del dialogo del gioco compare alla **riga 9**; in basso
  resta la mappa di prima del dialogo. (Eccezione: partita caricata a dialogo già aperto.)
- **Domande SÌ/NO** (D-046, D-048): il riquadro del gioco sparisce; due riquadri dell'app con le parole del gioco,
  **SÌ in (2,37)** e **NO in (12,37)**, senza cursore. Anche in lotta (es. "Usarne un altro?") e per la cura
  dell'infermiera (**CURA / ESCI**, D-060).
- **Testo automatico** a 3 velocità (lento 2 s, medio 1 s, veloce 0,4 s): preme A solo quando il gioco aspetta ▼,
  mai su menu o domande (D-028). Velocità del testo del gioco dalle opzioni (D-013).

## 6. Menu START (D-045, D-046)
- Mappa ferma alla riga 5. Squadra in alto: **icona del Pokémon** (dalla ROM), nome, sotto livello e vita.
- Riquadro 20×11 in basso: **POKéMON** e **nome del giocatore** in alto, poi **POKéDEX / OPZIONI**, **BORSA / SALVA**.
- **Nome del giocatore → scheda allenatore**: è quella vera del gioco, ridisposta: dati alla riga 6, medaglie alla
  riga 16, riquadro Pokédex (visti/presi, squadra) alla riga 28, INDIETRO alla riga 39.
- **OPZIONI** (senza "menu del gioco"): COMPARSA TESTO, **VOLUME** (6 livelli, il primo = muto), **GIOCO A COLORI /
  BIANCO E NERO** (tutto il gioco) (D-054).

## 7. Squadra, Pokémon, Pokédex
- **SQUADRA** nello stile del gioco: nome, icona, livello, PS; tocco = **scheda del Pokémon** con l'**immagine vera**
  (decodificata dalla ROM) nei **colori veri** della specie se il gioco è a colori (D-047).
- **Spostare i Pokémon**: SELECT o trascinamento; l'app esegue START → POKéMON → ORDINA nel gioco (D-057).
- **POKéDEX**: lista; tocco su una specie vista → **scheda della specie**: PRECEDENTE (2,7) e SUCCESSIVO (12,7) 6×6,
  immagine (1,14), numero e nome (9,14), categoria (9,16), altezza (9,18), peso (9,20), descrizione dalla riga 22,
  INDIETRO (5,39) (D-054).

## 8. Borsa
- Lista lunga; **INDIETRO** e **SELECT** alla riga 39; USA / GETTA / ANNULLA in un riquadro sotto la lista (D-046).
- **Strumenti su un Pokémon** (cure, pietre, vitamine, Caramella Rara, MT/MN): dopo USA l'app mostra **"SU CHI?"**
  con la squadra; l'app esegue, lascia leggere il messaggio del gioco e richiude da sola i menu vecchi (D-051).
- **GETTA** con quantità e conferma; **scambio** degli strumenti con SELECT o trascinamento (D-043).
- Da fare: Etere (scelta della mossa).

## 9. Lotta (D-018, D-033, D-045, D-046, D-050)
- Lotta a tutto schermo: **testo del gioco alla riga 4**, **scena alla riga 11**, riquadro **SQUADRA / BORSA** alla
  riga 23, **mosse** (con PP accanto) alla riga 28, **FUGA** in un riquadro alla riga 39.
- **Tocco su una mossa = attacca subito** (un solo tocco).
- **Testi di lotta**: un tocco sui riquadri o sullo schermo manda avanti.
- **SQUADRA e BORSA in lotta (versione C)**: liste dell'app sopra la lotta (riquadro 20×16 alla riga 23, INDIETRO
  alla riga 39), scena visibile, testo "Scegli un POKéMON" / "Usa uno strumento". Un tocco sceglie; l'app guida i
  menu del gioco senza mostrarli. Il Pokémon già in campo non si sceglie.
- **Pokémon KO**: la lista SQUADRA dell'app chiede chi mandare in campo; la scena resta visibile.
- Il menu LOTTA/PKMN/STRUM./FUGA del gioco non si vede mai.

## 10. Nomi
- **La schermata dei nomi del gioco non si vede mai** (D-046): si usa la **tastiera del telefono**, solo con i
  caratteri della griglia originale (accenti tolti), stessa lunghezza massima (D-029, D-032).
- **Nomi proposti dal Professore** (giocatore e rivale): riquadro 11×11 alla riga 15 con i nomi pronti ogni 3 righe,
  **NUOVO NOME** in un riquadro 14×5 in (3,28); un tocco sceglie (D-052, D-054).

## 11. Negozio (D-056)
- Parlando col commesso: **SOLDI** e i pulsanti **COMPRA, VENDI, ESCI** sopra la mappa, dialogo alla riga 9.
- COMPRA: lista con prezzi (dalla ROM), riquadro soldi 20×5, INDIETRO (6,40.5).
- VENDI: borsa con ricavo (metà prezzo), riquadro soldi 19×5.
- Quantità: **cursore a scorrimento** in un riquadro 13×4 in (0,39) (al posto di − e +) e **OK** 7×4 in (13,39).

## 12. PC
- **PC di casa (STRUMENTI PC)** (D-055): lista degli strumenti del PC, tocco = preleva un pezzo; DEPOSITA mostra la
  borsa, tocco = deposita; INDIETRO (1,40.5) spegne il PC.
- **PC del Centro Pokémon** (D-058): la scelta PC DI ??? / PC DI <giocatore> / PC PROF. OAK / DISCONNETTI diventa una
  colonna di riquadri da toccare in basso.
- **BOX POKéMON**: riquadro 20×5 in alto (BOX n, POSTI n/20), lista con icona e livello (tocco = RITIRA), DEPOSITA in
  basso a destra (mostra la squadra, tocco = deposita), INDIETRO in basso a sinistra. L'ultimo Pokémon non si
  deposita; con 6 in squadra non si ritira.
- In basso INDIETRO (11×4) e AZIONI (9×4); AZIONI apre LIBERA / DEPOSITA (box) o DEPOSITA / BUTTA (PC) (D-063).
- **Cambio box scorrendo a lato** (il gioco salva, l'app risponde Sì). **LIBERA**: tenere premuto un Pokémon o
  AZIONI → LIBERA, poi "Liberare NOME?" con SÌ (1,38) / NO (11,38).
- **BUTTA** dal PC di casa: tenere premuto uno strumento o AZIONI → BUTTA; quantità tra due frecce, ANNULLA / OK.

## 13. Ancora da fare (gameplay)
- Mossa da dimenticare, mosse da usare fuori dalla lotta (Taglio, Surf, Volo…) con INDIETRO (6,40.5) (D-054).
- Etere (mockup approvato, D-063); più slot di salvataggio (D-002).
- Opzione mappa extra attenuata/nascosta (D-015, bassa priorità); orizzontale (bassa priorità).

## 14. Note tecniche utili a un nuovo progetto
- Indirizzi della memoria italiana: docs/MEMORY_MAP_IT.md (in genere 4 byte dopo la versione inglese in WRAM alta;
  menu CC24–CC2A, schermo C3A0, squadra D167, borsa, PC D53E, box DA84, collegamenti D374–D38A).
- Dati letti dalla ROM dell'utente (solo in memoria): icone, immagini frontali, tavolozze, voci del Pokédex, prezzi.
- Riconoscimento delle schermate del gioco dalle variabili dei menu + caselle sullo schermo; esecutori a circuito
  chiuso con attesa della stabilità del menu.
- Banco di prova Robolectric e partite di prova costruite giocando in automatico (tappe fino al Centro di
  Smeraldopoli), conservate fuori dal repository.
