# TODO

## Azioni del committente
- [ ] Rendere privato il repository (impostazioni GitHub).
- [ ] Eventuale permesso per compilare RGBDS (serve per le boot ROM libere di SameBoy), se l'alternativa non funziona.

## Fase 1b – Memoria versione italiana
- [x] wCurMap, wYCoord, wXCoord, tileset, dimensioni mappa
- [ ] wOverworldMap, wSpriteStateData1/2, direzione giocatore
- [ ] wJoyIgnore, wTextBoxID, wCurrentMenuItem, wIsInBattle
- [ ] Squadra, borsa, soldi, stato a piedi/bici/surf

## Fase 3 – Prototipo
- [x] Scheletro Gradle (AGP 9.5.1, Gradle 9.9.1, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37)
- [x] SameBoy via NDK 30 + JNI (per ora dentro il modulo `app`; separare in `core-emu` quando cresce)
- [x] Programma di avvio proprio (D-022)
- [x] App 0.1: scelta ROM, gioco in verticale, levetta, tocco = A, indietro = B, Menu/Select/Salva, salvataggi
- [x] APK di debug consegnato (0.1.0-prototipo)
- [ ] Riscontro del committente sull'APK 0.1
- [x] Ponte (`bridge`): lettura di squadra, soldi, mappa, posizione con test su JVM
- [x] Squadra in alto con dati reali (da verificare su partita vera con un compagno in squadra)
- [x] Esecutore comandi a circuito chiuso per la lotta (provato sul gioco vero)
- [x] Ponte: pathfinding e tocco su persone, oggetti, cartelli, banconi
- [ ] Tocco sui PC; sporgenze, acqua (Surf) e dislivelli nel percorso
- [x] Testo automatico a 3 velocità (D-028) e testo veloce tenendo premuto (D-030)
- [x] **Gioco a tutto schermo (D-026/D-027)**: vista estesa in esplorazione (0.3.0)
- [x] Vista estesa: persone e oggetti fuori inquadratura
- [x] Lotta a tutto schermo (D-018, D-033)
- [ ] Opzione mappa attenuata/nascosta (D-015, bassa priorità)
- [x] Tastiera del telefono per i nomi (D-029, D-032) – da provare sul telefono
- [x] Sì/No nativi nelle domande del gioco
- [x] Tipo e PP massimi delle mosse dalla ROM
- [x] Menu nativo Squadra con scheda (sola lettura) e barra con icone (D-035)
- [x] Menu nativi Borsa e Pokédex (sola lettura), barra D-036
- [x] Borsa: Usa (fino alla scelta nel gioco) e Getta (completo) tramite il menu originale
- [x] Avvio senza pulsanti dell'app; pulsante laterale con i pulsanti in colonna (D-037)
- [x] Avvio con le grafiche del gioco come nel mockup del committente (D-039, D-040, D-041)
- [ ] **Prossimo passo**: prova sul telefono; poi Squadra, Borsa, Pokédex, Select e messaggi nello stile del gioco
- [ ] Usa con scelta del compagno nell'app (pozioni, cure); Squadra: "Sposta"
- [ ] Copia di sicurezza dei salvataggi (esporta/importa) prima di eventuali reinstallazioni
- [ ] Pokédex: scheda della specie (descrizione, altezza, peso) dalla ROM, solo per le specie prese
- [ ] Negozio e PC sul posto (D-010)
- [ ] Squadra: "Sposta" e strumenti tramite il menu originale (esecutore)

## Prova del motore su computer
```
SB=third_party/SameBoy/Core; D=(-D_GNU_SOURCE -D_USE_MATH_DEFINES -DGB_DISABLE_TIMEKEEPING -DGB_DISABLE_REWIND -DGB_DISABLE_DEBUGGER -DGB_DISABLE_CHEATS -DGB_DISABLE_CHEAT_SEARCH '-DGB_VERSION="1.0.5"' '-DGB_COPYRIGHT_YEAR="2026"')
# compilare ogni file del motore con -DGB_INTERNAL, poi boot_rom.c e tools/native-test/harness.c senza, e collegare con -lm
./harness ROM CARTELLA_USCITA   # immagini PPM solo in una cartella temporanea
```

## Test sul gioco vero (facoltativi)
1. Compilare il motore per il computer come libreria condivisa: stessi file e opzioni, più `-fPIC`,
   `app/src/main/cpp/gbcore_jni.c`, `boot_rom.c` ed `extended_view.c`, inclusi `$JAVA_HOME/include` e `include/linux`,
   collegati con `clang -shared ... -lm -o CARTELLA/libgbcore.so`.
2. Preparare uno stato salvato al menu di lotta con `tools/native-test/runner.c` (sequenza in docs/MEMORY_MAP_IT.md).
3. `./gradlew testDebugUnitTest -Pgiallo.rom=ROM -Pgiallo.state=STATO_MENU_LOTTA -Pgiallo.state.yesno=STATO_DOMANDA -Pgiallo.hostlib=CARTELLA`
   (altri stati facoltativi: giallo.state.text = domanda del soprannome, giallo.state.pallet = paese iniziale,
   giallo.state.naming = schermata "NOME TUO?" di una nuova partita)

## Ambiente: ripristino SDK se il container viene ricreato
```
export ANDROID_HOME=/opt/android-sdk
mkdir -p $ANDROID_HOME/cmdline-tools && curl -sS -o /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
unzip -q /tmp/clt.zip -d $ANDROID_HOME/cmdline-tools && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --install "platform-tools" "platforms;android-36" "build-tools;36.1.0" "ndk;30.0.16248370" "cmake;3.31.6"
```

## Più avanti
- [x] Schermata "Carica partita" dal menu iniziale, salvataggio manuale + automatico (D-034)
- [ ] Più slot di salvataggio manuali (D-002)
- [ ] Layout orizzontale (bassa priorità)
