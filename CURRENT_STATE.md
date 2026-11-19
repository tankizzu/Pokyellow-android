# CURRENT_STATE

**Ultimo aggiornamento:** 2026-11-19

## Dove siamo
- **0.14.0-prototipo (consegnata il 2026-11-19)**: mossa da dimenticare (D-085), tendina e dissolvenze coi colori
  del luogo (D-083), TIPO/PP senza PP (D-084), analisi dalla cameretta al laboratorio. Prima, 0.13.4: OPZIONI in un solo riquadro (D-075, D-080, D-082), menu e nomi
  immediati (D-081), stelline una per una, vibrazione nei lampi. Prima, 0.12.4: analisi fotogramma per fotogramma dell'inizio (D-074, video con
  il numero del fotogramma), introduzione con stelle e Pikachu interi (D-076), vibrazione nel Tuonoshock (D-073),
  scritta (C) del titolo ferma in basso; bozza OPZIONI uniche (D-075). Prima, 0.12.0: introduzione senza bande nere (D-072), strumenti sui Pokémon a
  schermo intero (D-070), squadra del menu START dal mockup con icone animate e scorrimento (D-071), oltre a D-050…D-069
  (box e PC, negozio, cura, prima apertura, titolo, vibrazione, colori dei luoghi).
- 100 prove; quelle sul gioco vero chiedono una partita precisa (-Pgiallo.state.X) e senza si saltano.

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
1. Domani col committente (pagina "domani.html" inviata): mossa da dimenticare sul telefono; fine cattura di Pikachu
   (rettangolo bianco, dialogo che scende); colori all'ingresso del laboratorio; lampi chiari prima della lotta;
   menu del PC; Etere/Elisir, MT/MN e mosse fuori lotta (stesso riquadro delle 4 mosse).
2. Da verificare: D-086 (dialogo spostato solo a riquadro disegnato) col video del laboratorio.
3. Poi: analisi fotogramma per fotogramma della lotta col rivale, Strada 1, Smeraldopoli, Pacco.

## Diario del lavoro autonomo (dal 2026-11-18 19:30 UTC, per il resoconto)
- PC del giocatore (D-055) fatto e verificato; colori veri delle immagini dei Pokémon dalla ROM (tavolozze a 0x72B39,
  scelta per specie a 0x72961), verificati sulla scheda di Pikachu.
- Partita automatica nel banco di prova (ZzScenarioTest): dalla lotta col rivale verso Smeraldopoli per costruire
  partite di prova più avanti (negozio, più Pokémon). Prima tappa riuscita: Strada 1, Smeraldopoli, Pacco di Oak
  ricevuto al Pokémon Market (partite route1, viridian, parcel nella cartella temporanea). Seconda tappa riuscita:
  Pacco consegnato a Oak, negozio aperto (partite pokedex, shop).
- Negozio dell'app (D-056) fatto e verificato: comprare e vendere con cursore a scorrimento.
- Terza tappa: 7 Poké Ball, Rattata catturato (partita two: Pikachu + Rattata). Spostare i Pokémon (D-057) verificato.
- Cambio Pokémon in lotta dalla lista SQUADRA (D-050) verificato con due Pokémon.
- Pokémon KO: la lista dell'app chiede chi mandare in campo, verificato (Rattata KO → Pikachu). Corretti e
  verificati: scena della lotta visibile dietro la lista (prima bianca) e niente più "PIKACHU è già in campo!"
  (l'app ripeteva il cambio).
- Quarta tappa della partita automatica: Centro Pokémon di Smeraldopoli (partita center).
- PC del Centro (D-058): scelta del PC a riquadri e BOX POKéMON dell'app (deposita/ritira) fatti e verificati.
- SÌ/NO in lotta ("Usarne un altro?") ora nello stile del gioco come fuori dalla lotta (riquadri SÌ e NO in basso,
  riquadro del gioco nascosto), verificato col banco di prova.
- Richiesta del committente: cercare difetti, comparse sbagliate e luoghi che non si collegano. Corretto: vista
  estesa ai bordi tra luoghi collegati (D-059). Prove di tocchi lontano dalle persone: nessun dialogo indesiderato.
- 19/11: Segnala (D-064) fatto e verificato; prova completa riuscita; APK 0.10.0-prototipo consegnato.
- 19/11 mattina: risposte del committente (D-062) e mockup mosse e box (D-063): Box (scorrere a lato, LIBERA,
  AZIONI) e PC (AZIONI, BUTTA) fatti e verificati sul gioco vero; START e INDIETRO centrati, SÌ/NO simmetrici.
- 19/11 notte, richieste del committente: colori coerenti tra Biancavilla e le strade (D-060), cura CURA/ESCI nello
  stile dell'app (D-060), lampeggi in lotta trovati con un controllo fotogramma per fotogramma e corretti (D-061).
  Prova completa riuscita, APK 0.9.0-prototipo consegnato (01:08 UTC). Confine nord di Smeraldopoli (Strada 2):
  vista estesa e colori corretti (il passaggio è ancora chiuso dalla storia: "Non puoi passare da qui!").
- Controllo delle schermate del banco di prova in cerca di elementi inutili: tolto INDIETRO dalla lista del KO (il
  gioco obbliga a scegliere). Riepilogo del gameplay per un nuovo progetto: docs/GAMEPLAY.md. APK 0.8.1 consegnato.
- APK 0.8.0-prototipo consegnato (23:18 UTC). Domande al committente: cambio box, LIBERA PKMN, mossa da
  dimenticare.
- Prova completa del banco di prova: tutto riuscito (23:10 UTC). Commit bloccato dal controllo automatico dei
  permessi: le modifiche sono nella cartella del progetto, in attesa del permesso del committente.

## Ambiente di lavoro (cloud)
- Ambiente "Pokyellow-android" con accesso a dl.google.com e script che reinstalla l'SDK in `/opt/android-sdk`.
- Build: `ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- Test sul gioco vero: vedi TODO.md ("Test sul gioco vero").
- Push su GitHub funzionante (ramo `claude/pokemon-giallo-android-dquast`).
