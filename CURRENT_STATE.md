# CURRENT_STATE

**Ultimo aggiornamento:** 2026-11-18

## Dove siamo
- **0.3.0-prototipo (consegnato il 2026-11-18): gioco a tutto schermo + tastiera del telefono per i nomi.**
  In esplorazione la mappa riempie lo schermo verticale (con le persone fuori inquadratura); in lotta lo sfondo
  riempie lo schermo con scena in alto e testo sopra i pulsanti; menu a tutto schermo e transizioni mostrano
  l'immagine originale 10:9. Quando il gioco chiede un nome compare la tastiera del telefono.
- 0.2.2 consegnata (testo automatico a 3 velocità, testo veloce tenendo premuto). 0.2.1 ha corretto B-001 (ZIP):
  il committente conferma che il gioco parte.
- 60 test superati (20 sul gioco vero, solo in locale con la ROM dell'utente).

## Cosa c'è nell'app
- Scelta del file del gioco (anche ZIP), controllo SHA-1 e intestazione.
- Vista estesa a tutto schermo in esplorazione (ARCHITECTURE.md §7, D-031).
- Levetta ovunque (4 direzioni); tocco veloce = A; tocco su persone/oggetti/cartelli/banconi = vai lì e parla
  (anche nella mappa aggiunta); gesto indietro = B.
- Squadra in alto con dati veri; in lotta pulsanti mosse (tipo, PP), Squadra, Borsa, Fuga; Sì/No nativi.
- Pulsante laterale: Squadra, Borsa, Pokédex, Menu (START), Testo auto (No/Lento/Medio/Veloce), Salva; Select solo nei menu del gioco.
- Nomi: tastiera del telefono al posto di quella del gioco (D-029, D-032).
- Menu nativi Squadra (con scheda), Borsa (con Usa/Getta tramite il menu del gioco) e Pokédex.
- Prima di giocare nessun pulsante dell'app; durante il gioco pulsante laterale con i pulsanti in colonna (D-037).
- Avvio come sul Game Boy (D-034): introduzione, titolo, menu iniziale del gioco + pulsante nativo CARICA PARTITA.
- Salvataggi: del gioco (CONTINUA, batteria ogni 20 s se cambiata); dell'app: "Salva" (manuale) e automatico
  all'uscita durante una partita, con miniatura e scheda; scritture atomiche con .bak.

## Limiti noti
- Persone fuori inquadratura: disegnate ferme nella loro direzione (il gioco stesso le blocca fuori schermo).
- Al cambio di mappa la vista estesa può spegnersi per qualche fotogramma.
- L'app non è stata provata qui su un telefono (nessun emulatore Android): verificati motore e logica su computer.

## Regola prima di ogni APK (D-042)
Far girare il banco di prova dell'interfaccia e guardare le foto:
`./gradlew testDebugUnitTest --tests '*Screen*Test*' -Pgiallo.rom=ROM -Pgiallo.battery=SALVATAGGIO.sav
 -Pgiallo.state.squadra=STATO_CON_SQUADRA -Pgiallo.state.naming=STATO_NOME -Pgiallo.shots=CARTELLA_FOTO -Pgiallo.hostlib=LIB`
(le foto contengono immagini del gioco: cartella temporanea, mai nel repository).

## Prossimo passo – RISCONTRO SULLA 0.4.1
1. Il committente prova la 0.4.1 (avvio ridisegnato verificato sull'app vera).
2. Poi: Squadra, Borsa, Pokédex nello stile del gioco (oggi ancora scuri), Select e messaggi; prova dei percorsi
   NUOVO GIOCO → nome e del cursore con la levetta nel banco di prova.

## Ambiente di lavoro (cloud)
- Ambiente "Pokyellow-android" con accesso a dl.google.com e script che reinstalla l'SDK in `/opt/android-sdk`.
- Build: `ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- Test sul gioco vero: vedi TODO.md ("Test sul gioco vero").
- Push su GitHub funzionante (ramo `claude/pokemon-giallo-android-dquast`).
