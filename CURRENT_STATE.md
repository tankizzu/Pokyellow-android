# CURRENT_STATE

**Ultimo aggiornamento:** 2026-11-17

## Dove siamo
Fase 1 – Analisi. Proposta architetturale consegnata al committente.
**Nessun codice applicativo scritto.** In attesa delle risposte in `TODO.md` → "Bloccanti".

## Fatti verificati in questa sessione
- ROM: versione italiana `POKEMON YELAPSI`, 1 MiB, MBC5+RAM+batteria, 32 KiB di
  salvataggio, GBC+SGB, checksum validi. SHA-1 in `PROJECT.md`.
  La ROM è stata usata solo nell'area temporanea dell'ambiente cloud, mai nel repository.
- SameBoy: licenza Expat (MIT) per tutto tranne le cartelle iOS e HexFiend;
  include boot ROM open source; Android non citato tra le piattaforme ufficiali.
- pret/pokeyellow documenta la versione inglese, non quella italiana.
- Google: la verifica sviluppatori per le app installate da APK è attiva dal
  30/09/2026 solo in Brasile, Indonesia, Singapore, Thailandia; estensione globale
  prevista nel 2027 (installazione comunque possibile via ADB o "advanced flow",
  oppure account gratuito a distribuzione limitata fino a 20 dispositivi).
- Ambiente cloud: Java 21 e Gradle presenti; Maven Google raggiungibile;
  il sito di download dell'Android SDK (dl.google.com) è **bloccato** dalla rete.
- Il repository GitHub è **pubblico**.

## Prossimo passo
Dopo l'approvazione di D-001: Fase 1b (mappa memoria versione italiana) e
verifica compilazione del core con NDK, poi Fase 2 (proposta UX navigabile).
