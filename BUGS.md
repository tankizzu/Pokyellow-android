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
