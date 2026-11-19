# Battito — prototipo

Runner ritmico a un tocco per Android, ispirato al genere di Geometry Dash ma con una meccanica propria:
**il cambio di fase**. Il cubo è ciano o magenta: *i blocchi del tuo colore sono solidi, quelli
dell'altro colore sono fantasmi*. I muri colorati non si saltano: li attraversi solo cambiando colore
al momento giusto, e le piattaforme colorate ti reggono solo se sei del loro colore.
Se cambi colore mentre sei dentro un blocco che diventa solido, muori.

## Livello e musica
"Primo Battito" dura 90 secondi (768 colonne) ed è agganciato al brano: 16 colonne = 1 battuta a 128 BPM.
Le sezioni del livello cadono sulle sezioni della musica (intro, crescendo, drop, pausa, secondo drop, outro).

La musica è **originale e generata dal gioco stesso** (`Synth.kt` + `Song.kt`): un piccolo sintetizzatore
suona la partitura in tempo reale. Nessun file audio, nessuna licenza: è tutta del progetto.
Per ascoltarla sul computer: `./gradlew testDebugUnitTest -Pbattito.wav=/percorso/brano.wav`.

## Impostazioni
Ingranaggio in alto a destra nel menu (in partita: pausa → Impostazioni):
volume musica, volume effetti (passi del 5%, cursore trascinabile o −/+), vibrazione sì/no.
Anche i tasti volume del telefono regolano l'audio del gioco.

## Comandi
- **Metà destra dello schermo**: salto (tieni premuto per saltare di continuo)
- **Metà sinistra**: cambia colore
- **Cerchio in alto a destra**: pausa
- Tastiera (emulatore): Spazio/↑ salto, X/Shift/← colore, P/Esc pausa

## Struttura
| File | Contenuto |
|---|---|
| `app/src/main/java/it/battito/game/Sim.kt` | Livello a griglia + fisica deterministica a 240 Hz (Kotlin puro, senza Android) |
| `app/src/main/java/it/battito/game/Levels.kt` | Livelli disegnati come testo (legenda in `Sim.kt`) |
| `app/src/main/java/it/battito/game/GameView.kt` | Ciclo di gioco, input multitocco, disegno su Canvas, effetti |
| `app/src/main/java/it/battito/game/Synth.kt` | Sintetizzatore (cassa, clap, charleston, basso, pad, melodia, arpeggio, effetti) |
| `app/src/main/java/it/battito/game/Song.kt` | Partitura del brano + sequencer |
| `app/src/main/java/it/battito/game/AudioEngine.kt` | Thread audio, mixer musica/effetti con volumi separati |
| `app/src/main/java/it/battito/game/Settings.kt` | Impostazioni salvate |
| `app/src/main/java/it/battito/game/MainActivity.kt` | Schermo intero, orizzontale |
| `app/src/test/.../LevelSolvableTest.kt` | Un bot cerca una corsa vincente, anche con soli 15 input al secondo (niente passaggi al fotogramma) |
| `app/src/test/.../SongTest.kt` | Il brano non satura e dura quanto il livello |
| `app/src/test/.../ScreenshotTest.kt` | Foto delle schermate sul computer: `-Pbattito.shots=/cartella` |

Nessuna libreria esterna: solo Android SDK + Kotlin.

## Compilare
```
./gradlew testDebugUnitTest   # verifica che i livelli siano completabili
./gradlew assembleDebug       # APK in app/build/outputs/apk/debug/
```

## Prossimi passi possibili
Modalità pratica con checkpoint, più livelli e selettore,
nuove modalità di movimento, editor di livelli nel gioco.
