# ROADMAP

Legenda: ✅ fatto · 🔄 in corso · ⏳ da fare · ⛔ in attesa di approvazione

## Fase 1 – Analisi 🔄
- ✅ Analisi header della ROM (versione italiana, MBC5, 32 KiB salvataggio, GBC+SGB)
- ✅ Confronto architetture A/B → proposta "A potenziata"
- ✅ Verifica licenza core candidato (SameBoy: Expat/MIT)
- ✅ Verifica contesto distribuzione APK (verifica sviluppatori Google)
- ⛔ Approvazione architettura (D-001)
- ⏳ 1b. Ricostruzione e verifica mappa memoria della versione italiana

## Fase 2 – Progettazione ⏳
- Architettura dettagliata e struttura moduli definitiva
- Proposta UX navigabile (verticale + orizzontale) → approvazione
- Specifica sistema touch (movimento, interazioni, casi speciali)
- Specifica salvataggi e ripresa
- Versioni SDK/strumenti verificate su documentazione ufficiale

## Fase 3 – Prototipo ⏳
Obiettivo: APK di prova che permetta di
- scegliere la ROM dal telefono e avviare il gioco a schermo intero in verticale;
- muovere il personaggio toccando la mappa (percorso automatico);
- interagire toccando persone/oggetti, avanzare i dialoghi con un tocco;
- aprire il menu.

## Fase 4 – Implementazione per sistemi ⏳
Ordine previsto, ciascuno completato e verificato prima del successivo:
1. Salvataggi robusti, ripresa rapida, ciclo di vita Android
2. Dialoghi, scelte Sì/No, inserimento nomi
3. Combattimenti nativi
4. Squadra e scheda Pokémon
5. Borsa / strumenti
6. Pokédex
7. Negozi, PC, Centro Pokémon
8. Layout orizzontale completo
9. Impostazioni, esportazione/importazione salvataggi
10. Casi speciali (Zona Safari, Casinò, minigiochi, pavimenti scorrevoli…)

## Fase 5 – Test ⏳
Test automatici del ponte con la ROM nell'ambiente di sviluppo (mai committata)
+ test manuali sul telefono dell'utente.

## Fase 6 – Distribuzione ⏳
APK firmato per installazione diretta.
