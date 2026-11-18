# CURRENT_STATE

**Ultimo aggiornamento:** 2026-11-18

## Dove siamo
- **0.3.0-prototipo (non ancora consegnato): gioco a tutto schermo + tastiera del telefono per i nomi.**
  In esplorazione la mappa riempie lo schermo verticale (con le persone fuori inquadratura); in lotta lo sfondo
  riempie lo schermo con scena in alto e testo sopra i pulsanti; menu a tutto schermo e transizioni mostrano
  l'immagine originale 10:9. Quando il gioco chiede un nome compare la tastiera del telefono.
- 0.2.2 consegnata (testo automatico a 3 velocità, testo veloce tenendo premuto). 0.2.1 ha corretto B-001 (ZIP):
  il committente conferma che il gioco parte.
- 50 test superati (15 sul gioco vero, solo in locale con la ROM dell'utente).

## Cosa c'è nell'app
- Scelta del file del gioco (anche ZIP), controllo SHA-1 e intestazione.
- Vista estesa a tutto schermo in esplorazione (ARCHITECTURE.md §7, D-031).
- Levetta ovunque (4 direzioni); tocco veloce = A; tocco su persone/oggetti/cartelli/banconi = vai lì e parla
  (anche nella mappa aggiunta); gesto indietro = B.
- Squadra in alto con dati veri; in lotta pulsanti mosse (tipo, PP), Squadra, Borsa, Fuga; Sì/No nativi.
- Barra in basso: Menu (START), Select, Testo auto (No/Lento/Medio/Veloce), Salva.
- Nomi: tastiera del telefono al posto di quella del gioco (D-029, D-032).
- Salvataggi: ripresa esatta all'uscita, batteria ogni 20 s se cambiata, scritture atomiche con .bak.

## Limiti noti
- Persone fuori inquadratura: disegnate ferme nella loro direzione (il gioco stesso le blocca fuori schermo).
- Al cambio di mappa la vista estesa può spegnersi per qualche fotogramma.
- L'app non è stata provata qui su un telefono (nessun emulatore Android): verificati motore e logica su computer.

## Prossimo passo – MENU NATIVI (Squadra per prima)
1. Leggere tutto ciò che serve dalla memoria (già pronto per la squadra: nomi, livelli, PS, stato, mosse).
2. Barra di icone in basso come nel Laboratorio (D-012): Squadra, Borsa, Pokédex, Scheda, Negozio, PC.
3. Primo menu: Squadra in sola lettura (scheda di ogni creatura); le azioni che cambiano il gioco
   (scambiare l'ordine, usare strumenti) passano dal menu originale tramite esecutore a circuito chiuso.
Da provare sul telefono: vista estesa con le persone, lotta a tutto schermo, tastiera dei nomi.

## Ambiente di lavoro (cloud)
- Ambiente "Pokyellow-android" con accesso a dl.google.com e script che reinstalla l'SDK in `/opt/android-sdk`.
- Build: `ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- Test sul gioco vero: vedi TODO.md ("Test sul gioco vero").
- Push su GitHub funzionante (ramo `claude/pokemon-giallo-android-dquast`).
