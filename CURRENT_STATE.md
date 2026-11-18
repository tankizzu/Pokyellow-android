# CURRENT_STATE

**Ultimo aggiornamento:** 2026-11-18

## Dove siamo
- **0.7.1-prototipo (consegnata il 2026-11-18)**: tutta l'interfaccia nello stile del gioco, dai mockup del
  committente (D-039…D-049), correzioni della lotta (B-005, B-006) e dei tocchi nei dialoghi (B-007); SQUADRA/BORSA in lotta (D-050) e
  strumenti su un Pokémon con "SU CHI?" dell'app (D-051).
- 80 prove superate (le prove sul gioco vero e il banco di prova dell'interfaccia girano solo in locale con la ROM).

## Cosa c'è nell'app
- Scelta del file del gioco (anche ZIP), controllo SHA-1 e intestazione.
- Avvio come sul Game Boy; menu iniziale ridisegnato (CONTINUA, CARICA PARTITA, OPZIONI, NUOVO GIOCO); Carica
  partita con i riquadri dei salvataggi.
- Gioco: vista estesa a tutto schermo; levetta; tocco = vai lì e parla, tocco nel vuoto vicino a qualcuno = parla;
  START in basso (nascosto in dialoghi e lotta); SELECT solo con un menu del gioco aperto.
- Dialoghi alla riga 9 (D-048); SÌ/NO in due riquadri in basso; schermata dei nomi del gioco mai visibile, nome con la
  tastiera del telefono.
- START aperto: squadra con icone dalla ROM; POKéMON, nome (scheda allenatore vera ridisposta), POKéDEX, OPZIONI,
  BORSA, SALVA. Schermate SQUADRA, scheda del Pokémon (immagine dalla ROM), POKéDEX, BORSA (USA/GETTA, SELECT e
  trascinamento), OPZIONI (comparsa testo, volume).
- Lotta: testo in alto, scena, SQUADRA/BORSA, mosse con PP, FUGA; un tocco = la mossa; i testi vanno avanti col tocco.
- Salvataggi: del gioco (batteria ogni 20 s se cambiata); dell'app manuale e automatico; scritture atomiche con .bak.

## Limiti noti
- Spostare i Pokémon della squadra: non ancora (serve una partita di prova con almeno 2 Pokémon); lo scambio degli
  strumenti è provato solo con la logica (serve una partita con almeno 2 strumenti). Chiesto al committente un .sav.
- Partita caricata a dialogo aperto: quel dialogo resta in basso (manca l'immagine della mappa senza dialogo).
- Persone fuori inquadratura disegnate ferme; al cambio di mappa la vista estesa può spegnersi per qualche fotogramma.
- Nessun telefono qui: l'app è provata sul computer con il banco di prova (telefono simulato), non su un telefono vero.

## Regola prima di ogni APK (D-042)
Far girare il banco di prova dell'interfaccia e guardare le foto:
`./gradlew testDebugUnitTest --tests '*Screen*Test*' -Pgiallo.rom=ROM -Pgiallo.state=STATO_LOTTA -Pgiallo.battery=SALVATAGGIO.sav
 -Pgiallo.state.squadra=STATO_CON_SQUADRA -Pgiallo.state.naming=STATO_NOME -Pgiallo.state.yesno=STATO_DOMANDA -Pgiallo.state.text=STATO_DIALOGO -Pgiallo.shots=CARTELLA_FOTO -Pgiallo.hostlib=LIB`
(le foto contengono immagini del gioco: cartella temporanea, mai nel repository).

## Prossimo passo
1. In attesa del committente: riepilogo del mockup del negozio (inviato), file .sav con almeno 2 Pokémon e 2
   strumenti (cambio in lotta, spostamenti), colore dell'immagine del Pokémon.
2. Poi: negozio secondo il mockup (il primo negozio si apre dopo aver consegnato il Pacco a Oak: serve una partita di
   prova più avanti), PC.

## Ambiente di lavoro (cloud)
- Ambiente "Pokyellow-android" con accesso a dl.google.com e script che reinstalla l'SDK in `/opt/android-sdk`.
- Build: `ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- Test sul gioco vero: vedi TODO.md ("Test sul gioco vero").
- Push su GitHub funzionante (ramo `claude/pokemon-giallo-android-dquast`).
