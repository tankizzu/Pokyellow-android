# CURRENT_STATE

**Ultimo aggiornamento:** 2026-11-17 (sera)

## Dove siamo
- Fase 2 UX: Laboratorio v3 (tutto schermo) accettato «per ora» dal committente.
- Architettura A potenziata considerata approvata (D-019).
- **Fase 1b in corso**: mappa della memoria della versione italiana (`docs/MEMORY_MAP_IT.md`).
- Nessun codice dell'app ancora scritto.

## Ambiente di lavoro (cloud)
- Ambiente "Pokyellow-android" con accesso a dl.google.com.
- Installati in `/opt/android-sdk`: cmdline-tools 23.0.0, platform-tools, platforms;android-36,
  build-tools;36.1.0, ndk;30.0.16248370, cmake;3.31.6. (Va reinstallato se il container viene ricreato:
  vedi script in TODO.)
- Strumenti di analisi in una cartella temporanea: venv Python con PyBoy, copie di consultazione di
  SameBoy e pret/pokeyellow (mai nel repository), ROM estratta dall'upload del committente.
- **Compilazione di RGBDS negata dal sistema di permessi**: niente RGBDS per ora.
  Serve per le boot ROM libere di SameBoy; alternative da valutare (vedi TODO).
- **GitHub rifiuta il push** (Claude non ha accesso al repository): i commit esistono solo qui.
  Se il container viene ricreato, il lavoro non salvato su GitHub va perso.

## Fatti verificati
- ROM italiana: `POKEMON YELAPSI`, MBC5+RAM+batteria, 32 KiB di salvataggio, GBC+SGB.
- Indirizzi trovati: wCurMap D362, wYCoord D365, wXCoord D366, tileset D36B, altezza/larghezza mappa D36C/D36D.
- Versioni Android/Kotlin di riferimento: vedi D-021.

## Prossimo passo
1. Continuare la mappa della memoria: wOverworldMap, sprite, direzione, stato dialogo/menu, lotta, squadra, borsa, soldi.
2. Creare lo scheletro del progetto Android (Gradle, moduli `app`, `core-emu`, `bridge`) e verificare che compili.
3. Integrare SameBoy con NDK; risolvere la questione boot ROM.
