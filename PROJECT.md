# PROJECT — Pokémon Giallo, Android Native Edition (nome di lavoro)

## Obiettivo
Un'app Android installabile (APK) che permetta di giocare a Pokémon Giallo con
un'esperienza **touch-first, pensata per smartphone in verticale**, senza
pulsantiera virtuale e senza aspetto da emulatore, preservando **tutte** le
meccaniche originali.

## Vincoli non negoziabili (dati dal committente)
- La ROM **non** viene mai caricata nel repository, nell'APK o su servizi esterni.
  L'utente la seleziona dal proprio telefono al primo avvio.
- Gioco completamente offline, nessun server, nessun abbonamento.
- Fedeltà delle meccaniche prima delle modifiche estetiche.
- Nessuna meccanica nuova senza approvazione esplicita.
- Nessuna dipendenza inutile.
- Kotlin, Android SDK moderno, Jetpack Compose per l'interfaccia nativa.
- Documentazione persistente nel repository (questi file .md).

## ROM di riferimento (analizzata localmente, mai committata)
| Campo header | Valore |
|---|---|
| Titolo interno | `POKEMON YELAPSI` (codice versione italiana) |
| Dimensione | 1 MiB (64 banchi da 16 KiB) |
| Tipo cartuccia | `0x1B` = MBC5 + RAM + batteria |
| RAM cartuccia | 32 KiB (salvataggio) |
| Supporto GBC | `0x80` = migliorata per Game Boy Color, compatibile Game Boy |
| Supporto SGB | `0x03` = sì |
| Destinazione | `0x01` = non giapponese |
| Checksum header / globale | validi (dump integro) |
| SHA-1 | `05bb8e99f24d498613930949730afa8024e77d08` |
| CRC32 | `8b56fe33` |

L'impronta SHA-1 servirà all'app per riconoscere la versione supportata.

## Dispositivo di riferimento
CMF Phone 2 Pro: 6,77" AMOLED 1080×2392 (circa 20:9), 120 Hz, MediaTek Dimensity 7300 Pro,
Android 15 con Nothing OS 3.2 di fabbrica. In verticale lo schermo di gioco a tutta larghezza
occupa circa 1080×972 pixel; restano circa 480 dp sotto per pannello e pulsanti.

## Stato generale
**Fase 1 – Analisi**: analisi iniziale completata, **in attesa di approvazione
dell'architettura** (vedi `ARCHITECTURE.md` e `DECISIONS.md`).
Nessun codice applicativo scritto finora (per scelta: regola "prima capire, poi codificare").
