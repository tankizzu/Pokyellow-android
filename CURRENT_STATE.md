# CURRENT_STATE

**Ultimo aggiornamento:** 2026-11-17 (notte)

## Dove siamo
- **APK 0.2.2-prototipo consegnato il 2026-11-18** (testo automatico, testo veloce tenendo premuto). 0.2.1 ha corretto B-001 (ZIP): il committente conferma che il gioco parte. con il resoconto (squadra in alto, comandi di lotta, Sì/No, tocco su persone/oggetti).
- **Prototipo 0.1 compilato e consegnato al committente** per le prime prove sul CMF Phone 2 Pro.
  Il gioco gira con SameBoy; comandi provvisori (vedi D-024).
- Fase 1b (memoria versione italiana) iniziata: `docs/MEMORY_MAP_IT.md`.
- In attesa del riscontro del committente sull'APK (domani).

## Cosa contiene il prototipo 0.1
- Scelta del file del gioco dal telefono (selettore di Android), controllo SHA-1.
- Gioco in verticale, immagine originale 10:9 a tutta larghezza, centrata, pixel nitidi, barre di sistema nascoste.
- Levetta che compare ovunque (4 direzioni); tocco veloce = A; gesto indietro = B.
- Pulsanti in basso: Menu (START), Select, Salva.
- Audio a 48 kHz che fa da orologio del gioco.
- Salvataggio: stato esatto all'uscita (ripresa automatica), batteria ogni 20 s se cambiata, scritture atomiche con .bak.

## Non ancora fatto (dichiarato)
- Menu nativi (Squadra, Borsa, Pokédex…), squadra in alto, vista estesa a tutto schermo,
  tocco su persone/oggetti con percorso automatico, lotta con pulsanti sovrapposti.
- Prova su un telefono reale: compilazione verificata, motore verificato su computer,
  ma l'app Android non è stata eseguita su un dispositivo (nessun emulatore qui).

## Ambiente di lavoro (cloud)
- Ambiente "Pokyellow-android" con accesso a dl.google.com e script che reinstalla l'SDK.
- SDK in `/opt/android-sdk`: platforms android-36 e android-37.0, build-tools 36.0.0 e 36.1.0,
  NDK 30.0.16248370, CMake 3.31.6 (AGP scarica da solo ciò che manca).
- Build: `ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- Prova del motore su computer: vedi `tools/native-test/harness.c` (comandi in TODO).
- **GitHub rifiuta il push**: i commit esistono solo qui.

## Ultimo lavoro (promemoria 23:16 UTC)
- Ponte di sola lettura + squadra in alto con dati veri; 4 test superati; APK ricompilato (non ancora consegnato).

## Ultimo lavoro (promemoria 05:16 UTC)
- Tocco su persone, oggetti, cartelli e banconi con percorso automatico e interazione; 28 test superati (4 sul gioco vero).
- APK ricompilato con squadra in alto, comandi di lotta, Sì/No e tocco per parlare (non ancora consegnato).

## Prossimo passo – GIOCO A TUTTO SCHERMO (priorità del committente, D-026)
Piano per la "vista estesa" (ARCHITECTURE.md §7):
1. Leggere dal motore la memoria video (tile del set corrente, palette a colori) e i registri di scorrimento.
2. Calcolare dove cade l'inquadratura originale sulla mappa (posizione del giocatore + scorrimento fine durante il passo).
3. Disegnare la mappa sopra e sotto l'inquadratura con i tile e le palette del gioco, allineata al pixel,
   e sovrapporre al centro l'immagine originale (testi e menu restano quelli del gioco).
4. Usarla solo in esplorazione; lotta e schermate speciali restano come ora.
5. Poi: personaggi fuori inquadratura (oggi spariscono al bordo).

Dopo il tutto schermo: **tastiera del telefono per i nomi** (D-029): riconoscere la schermata dei nomi,
coprirla, scrivere il nome con un esecutore a circuito chiuso sulla griglia delle lettere del gioco.

2. Raccogliere il riscontro del committente sull'APK 0.1 e correggere.
