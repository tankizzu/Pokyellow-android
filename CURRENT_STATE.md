# CURRENT_STATE

**Ultimo aggiornamento:** 2026-11-18

## Dove siamo
- **0.3.0-prototipo (non ancora consegnato): gioco a tutto schermo.** In esplorazione la mappa riempie lo schermo
  verticale; lotta, menu a tutto schermo e transizioni mostrano l'immagine originale 10:9.
- 0.2.2 consegnata (testo automatico a 3 velocità, testo veloce tenendo premuto). 0.2.1 ha corretto B-001 (ZIP):
  il committente conferma che il gioco parte.
- 40 test superati (12 sul gioco vero, solo in locale con la ROM dell'utente).

## Cosa c'è nell'app
- Scelta del file del gioco (anche ZIP), controllo SHA-1 e intestazione.
- Vista estesa a tutto schermo in esplorazione (ARCHITECTURE.md §7, D-031).
- Levetta ovunque (4 direzioni); tocco veloce = A; tocco su persone/oggetti/cartelli/banconi = vai lì e parla
  (anche nella mappa aggiunta); gesto indietro = B.
- Squadra in alto con dati veri; in lotta pulsanti mosse (tipo, PP), Squadra, Borsa, Fuga; Sì/No nativi.
- Barra in basso: Menu (START), Select, Testo auto (No/Lento/Medio/Veloce), Salva.
- Salvataggi: ripresa esatta all'uscita, batteria ogni 20 s se cambiata, scritture atomiche con .bak.

## Limiti noti
- I personaggi fuori dall'inquadratura originale non sono disegnati (compaiono quando entrano nel riquadro centrale).
- Al cambio di mappa la vista estesa può spegnersi per qualche fotogramma.
- L'app non è stata provata qui su un telefono (nessun emulatore Android): verificati motore e logica su computer.

## Prossimo passo – TASTIERA DEL TELEFONO PER I NOMI (D-029)
1. Riconoscere la schermata dei nomi del gioco (griglia delle lettere, cursore, nome in corso) dalla memoria.
2. Coprirla con un campo di testo nativo e la tastiera del telefono; accettare solo i caratteri del gioco, max 7/10.
3. Scrivere il nome con un esecutore a circuito chiuso sulla griglia (muove il cursore, preme A, controlla il nome in RAM),
   poi "FINE". Il gioco resta quello che decide.
Dopo: personaggi fuori inquadratura nella vista estesa, lotta a tutto schermo, menu nativi.

## Ambiente di lavoro (cloud)
- Ambiente "Pokyellow-android" con accesso a dl.google.com e script che reinstalla l'SDK in `/opt/android-sdk`.
- Build: `ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- Test sul gioco vero: vedi TODO.md ("Test sul gioco vero").
- Push su GitHub funzionante (ramo `claude/pokemon-giallo-android-dquast`).
