# CURRENT_STATE

**Ultimo aggiornamento:** 2026-11-17 (notte)

## Dove siamo
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

## Prossimo passo
1. Verificare la lettura della squadra su una partita vera: nell'emulatore di prova ottenere il primo compagno
   e confrontare i valori letti con quelli mostrati dal gioco.
2. Trovare dove inizia lo spostamento di +5 byte (zona CC2B–D15B) e le variabili wJoyIgnore, wIsInBattle, wTextBoxID.
3. Tocco su persone/oggetti con percorso automatico: servono collisioni (tabelle nella ROM) e posizioni degli sprite.
4. Raccogliere il riscontro del committente sull'APK 0.1 e correggere.
