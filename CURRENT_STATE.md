# CURRENT_STATE

**Ultimo aggiornamento:** 2026-11-17

## Dove siamo
Fase 1 (analisi) completata, in attesa della conferma esplicita dell'architettura (D-001).
In parallelo è iniziata la parte UX della Fase 2: il committente ha chiesto demo interattive.
**Nessun codice dell'app scritto.**

## Ultime decisioni del committente (vedi DECISIONS.md)
- Verticale come modalità principale (D-005).
- Levetta che compare/sparisce come movimento base (D-006).
- Barra di pulsanti con icone in basso → menu touch a scorrimento (D-007, D-008).
- Salvataggio ovunque approvato; più slot in futuro (D-002).
- Telefono: CMF Phone 2 Pro (D-009).

## Laboratorio UX
- File: `docs/demo/laboratorio-giallo-touch.html` (pagina interattiva, grafica e nomi segnaposto originali).
- Pubblicato come artifact privato: https://claude.ai/artifact/TPosCELEnL4joUJzMzHrtJ
- Le configurazioni inviate dal committente finiscono nel database dell'artifact,
  collezione `invii` (leggibile da Claude con ArtifactData). Al 2026-11-17: 1 invio (vedi D-012).
- Tre demo: Levetta (mappa giocabile), Pulsanti e menu (barra riordinabile, 9 menu), Lotta.
- Versione 2: modalità "Sposta e ridimensiona" (D-014). Le posizioni scelte vengono inviate
  in `cfg.layout` (coordinate in dp sul telefono da 411×911 dp) e nel riepilogo testuale.
- In attesa: il committente sistema posizioni e dimensioni e invia di nuovo.

## Fatti verificati
- ROM: versione italiana `POKEMON YELAPSI`, 1 MiB, MBC5+RAM+batteria, 32 KiB di salvataggio,
  GBC+SGB, checksum validi. SHA-1 in `PROJECT.md`. Mai nel repository.
- SameBoy: licenza Expat (MIT) tranne cartelle iOS e HexFiend; boot ROM open source incluse;
  Android non tra le piattaforme ufficiali (compilazione NDK da verificare).
- pret/pokeyellow documenta la versione inglese, non l'italiana.
- Verifica sviluppatori Google: in Italia nessun effetto fino all'estensione globale prevista nel 2027.
- Ambiente cloud: Java 21, Gradle, Node presenti; Maven Google raggiungibile;
  dl.google.com (Android SDK) **bloccato** dalla rete.
- GitHub: il push verso il repository viene rifiutato (Claude non ha accesso). Commit solo locali.
- Il repository è **pubblico**; il committente vuole renderlo privato.

## Prossimo passo
1. Raccogliere dal Laboratorio la configurazione preferita (o le note in chat).
2. Ottenere la conferma esplicita di D-001 e la scelta su D-010.
3. Poi: Fase 1b (mappa memoria versione italiana) e verifica compilazione del motore con NDK.
