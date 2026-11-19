# Battito — prototipo

Runner ritmico a un tocco per Android, ispirato al genere di Geometry Dash ma con una meccanica propria:
**il cambio di fase**. Il cubo è ciano o magenta: *i blocchi del tuo colore sono solidi, quelli
dell'altro colore sono fantasmi*. I muri colorati non si saltano: li attraversi solo cambiando colore
al momento giusto, e le piattaforme colorate ti reggono solo se sei del loro colore.
Se cambi colore mentre sei dentro un blocco che diventa solido, muori.

## Comandi
- **Metà destra dello schermo**: salto (tieni premuto per saltare di continuo)
- **Metà sinistra**: cambia colore
- Tastiera (emulatore): Spazio/↑ salto, X/Shift/← colore

## Struttura
| File | Contenuto |
|---|---|
| `app/src/main/java/it/battito/game/Sim.kt` | Livello a griglia + fisica deterministica a 240 Hz (Kotlin puro, senza Android) |
| `app/src/main/java/it/battito/game/Levels.kt` | Livelli disegnati come testo (legenda in `Sim.kt`) |
| `app/src/main/java/it/battito/game/GameView.kt` | Ciclo di gioco, input multitocco, disegno su Canvas, effetti |
| `app/src/main/java/it/battito/game/MainActivity.kt` | Schermo intero, orizzontale |
| `app/src/test/.../LevelSolvableTest.kt` | Un bot cerca una corsa vincente: ogni livello deve essere completabile |

Nessuna libreria esterna: solo Android SDK + Kotlin.

## Compilare
```
./gradlew testDebugUnitTest   # verifica che i livelli siano completabili
./gradlew assembleDebug       # APK in app/build/outputs/apk/debug/
```

## Prossimi passi possibili
Musica sincronizzata e suoni, modalità pratica con checkpoint, più livelli e selettore,
nuove modalità di movimento, editor di livelli nel gioco.
