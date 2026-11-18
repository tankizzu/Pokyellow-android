# CURRENT_STATE

**Ultimo aggiornamento:** 2026-11-18

## Dove siamo
- **0.3.0-prototipo (non ancora consegnato): gioco a tutto schermo + tastiera del telefono per i nomi.**
  In esplorazione la mappa riempie lo schermo verticale; lotta, menu a tutto schermo e transizioni mostrano
  l'immagine originale 10:9. Quando il gioco chiede un nome compare la tastiera del telefono.
- 0.2.2 consegnata (testo automatico a 3 velocità, testo veloce tenendo premuto). 0.2.1 ha corretto B-001 (ZIP):
  il committente conferma che il gioco parte.
- 47 test superati (14 sul gioco vero, solo in locale con la ROM dell'utente).

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

## Prossimo passo – LOTTA A TUTTO SCHERMO (D-018)
1. Riconoscere le parti della schermata di lotta (creature, barre, riquadro del testo) dalla mappa dello schermo.
2. Estendere lo sfondo (a tinta unita) a tutto lo schermo e posizionare scena e testo senza deformarli.
3. Pulsanti di lotta sopra lo sfondo (già pronti).
Poi: menu nativi (Squadra, Borsa, Pokédex, Scheda, Negozio, PC).
Da provare sul telefono: vista estesa con le persone, tastiera dei nomi (nuova partita → "NUOVO NOME").

## Ambiente di lavoro (cloud)
- Ambiente "Pokyellow-android" con accesso a dl.google.com e script che reinstalla l'SDK in `/opt/android-sdk`.
- Build: `ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- Test sul gioco vero: vedi TODO.md ("Test sul gioco vero").
- Push su GitHub funzionante (ramo `claude/pokemon-giallo-android-dquast`).
