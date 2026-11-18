# BUGS

Formato: `ID · data · componente · descrizione · causa · stato`.

- **B-001** · 2026-11-18 · scelta del gioco · Con l'APK 0.1/0.2 il gioco non partiva: l'app diceva che il file non era corretto.
  · Causa: il file sul telefono è uno ZIP (come quello caricato), l'app accettava solo il `.gb` estratto; con
  "Usa comunque" salvava lo ZIP come gioco. · **Risolto in 0.2.1**: l'app apre gli ZIP, controlla l'intestazione
  del gioco e, all'avvio, ripara o scarta un file salvato non valido. Verificato con lo ZIP vero (test locale).

## B-002 – Scritte dell'app "glitchate" sulla mappa (0.4.0) – CORRETTO in 0.4.1
Sulla mappa il gioco usa la zona della memoria video del carattere (0x8800) per i fotogrammi dei personaggi che
camminano: le lettere A–X prese da lì erano pezzi di personaggi. Ora il carattere 0x80–0xFF viene dalla ROM
(0x10600) per la versione italiana riconosciuta. Test: `RealGameTilesOverworldTest`.

## B-003 – Pulsanti dell'app visibili sul titolo (0.4.0) – CORRETTO in 0.4.1
Durante l'animazione iniziale lo schermo sembrava "libero" e l'app si credeva in partita (MENU e Select sul titolo).
Ora si è in partita solo con la mappa vera riconosciuta (vista estesa attiva) o dopo CARICA PARTITA.

## B-004 – MENU invisibile in gioco (0.4.0) – CORRETTO in 0.4.1
L'immagine del gioco era una View a parte ridisegnata a ogni fotogramma e copriva i pulsanti dell'app.
Ora è disegnata dentro Compose sotto i pulsanti. Trovato con il nuovo banco di prova dell'interfaccia.

## B-005 – Lotta: serviva toccare due volte la mossa (risolto, 0.6.1)
Il tocco sulla mossa arrivava al gioco anche come tasto A: il menu delle mosse si apriva e il comando trovava A già
premuto; inoltre premeva A appena il menu compariva, quando il gioco non ascolta ancora. Corretto: un tocco usato da un
riquadro dell'app non diventa più tasto A; il comando parte con i tasti rilasciati e aspetta che il menu sia fermo.
Prova: `AppBattleFullTest` (lotta col rivale fino alla fine, una mossa diversa a ogni turno, un solo tocco).

## B-006 – Lotta: testi bloccati toccando i riquadri (risolto, 0.6.1)
Durante i testi della lotta i riquadri SQUADRA/BORSA/mosse prendevano il tocco senza fare niente: sembrava bloccato
(anche nei dialoghi dopo la vittoria sul rivale). Ora un tocco sui riquadri, quando il gioco non mostra il menu di lotta,
manda avanti il testo. Prova: `AppBattlePanelTapTest` (lotta fino alla fine toccando solo i riquadri).

## B-007 – Dialoghi a volte bloccati, soprattutto ricevendo oggetti (correzione possibile, 0.6.3)
Segnalato dal committente sulla 0.6.2. Trovato un caso in cui un tocco poteva andare perso: con lo stato dell'interfaccia
vecchio di qualche fotogramma (per esempio appena parte un dialogo o il messaggio di un oggetto ricevuto), il tocco
diventava "parla con chi è vicino" e l'azione veniva annullata perché il gioco era già in un dialogo. Ora l'app controlla
nel gioco, in quel momento, se si cammina davvero; altrimenti il tocco è sempre il tasto A.
Prova: `AppReceiveItemTest` (dopo la lotta col rivale si va sulla Strada 1, si parla col commesso, si riceve la Pozione
solo toccando lo schermo; anche con un Pokémon selvatico lungo la strada). Il blocco del committente non è stato
riprodotto con certezza: da ricontrollare sul telefono.
