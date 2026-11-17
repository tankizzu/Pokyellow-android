# DECISIONS

## Requisiti approvati (dal brief iniziale del committente, 2026-11-17)
- R-01 Nessuna ROM o contenuto protetto nel repository/APK.
- R-02 Offline, nessun server, nessun pagamento.
- R-03 Verticale come modalità principale, orizzontale quando utile, nessun cambio forzato e continuo.
- R-04 Nessuna pulsantiera virtuale permanente (niente croce, niente A/B fissi).
- R-05 Fedeltà meccaniche prioritaria; nuove meccaniche solo con approvazione.
- R-06 Sviluppo per fasi (Analisi → Progettazione → Prototipo → Implementazione → Test → Distribuzione).
- R-07 Documentazione persistente nel repository.

## Decisioni approvate
| ID | Data | Decisione |
|---|---|---|
| D-002 | 2026-11-17 | **Salvataggio ovunque approvato** (istantanea dello stato in qualsiasi momento + ripresa automatica). Più avanti: sistema con più slot e schermata di caricamento. |
| D-005 | 2026-11-17 | **Il gioco funziona principalmente in verticale.** L'orizzontale passa in secondo piano (non prioritario). |
| D-006 | 2026-11-17 | **Movimento base: levetta virtuale che compare sotto il dito quando serve e sparisce quando lo si alza.** Nessuna croce fissa. Dettagli (zona, comportamento, grandezza) da definire con le demo. |
| D-007 | 2026-11-17 | **Barra di pulsanti in basso, tutti con la stessa grafica e un'icona**, per Squadra, Borsa, Pokédex, Scheda Pokémon, Negozio, PC. Un tocco apre il menu corrispondente. |
| D-008 | 2026-11-17 | **Dentro i menu si scorre e si seleziona col dito** (liste a scorrimento, righe grandi). |
| D-009 | 2026-11-17 | **Dispositivo di riferimento: CMF Phone 2 Pro** (6,77", 1080×2392, 120 Hz, Dimensity 7300 Pro, Android 15 / Nothing OS 3.2 di fabbrica). |
| D-012 | 2026-11-17 | Prima configurazione inviata dal Laboratorio: **menu a tutto schermo**, **Sì/No sullo schermo di gioco**, levetta ovunque/ferma/media, tocco su persone e oggetti sì, tocco sul terreno no, barra a una riga con icona e nome, lotta a griglia 2×2 con Squadra/Borsa/Fuga sotto. |
| D-013 | 2026-11-17 | **La velocità del testo si regola nelle impostazioni del gioco** (menu Opzioni), non è un comando a parte. |
| D-014 | 2026-11-17 | Il committente vuole **spostare e ridimensionare liberamente** gli elementi dell'interfaccia: aggiunta la modalità "Sposta e ridimensiona" al Laboratorio. Lo schermo di gioco mantiene sempre le proporzioni 10:9; testo dei dialoghi e informazioni di lotta fanno parte dell'immagine del gioco e si spostano con essa. |

| D-015 | 2026-11-17 | **Gioco a tutto schermo in verticale.** La larghezza resta di 10 caselle come l'originale; sopra e sotto si vede più mappa (circa 10 × 22 caselle invece di 10 × 9). Richiede la "vista estesa" (vedi ARCHITECTURE.md §7). Il committente può scegliere se la mappa extra è visibile, attenuata o nascosta. |
| D-016 | 2026-11-17 | **Levetta ovunque:** compare in qualsiasi punto si appoggi il dito. Eliminata la zona dedicata ("Appoggia il dito…"). |
| D-017 | 2026-11-17 | **Squadra in alto** (con luogo e soldi), sovrapposta alla mappa; barra dei pulsanti in basso, sovrapposta. |
| D-018 | 2026-11-17 | **Lotta a tutto schermo:** lo sfondo della lotta riempie lo schermo, mosse e pulsanti Squadra/Borsa/Fuga stanno sopra lo sfondo. Scena e riquadro del testo sono parti dell'immagine originale, posizionate separatamente senza deformarle. |

| D-019 | 2026-11-17 | Il committente ha risposto «va bene per ora, continua» alle domande aperte. **Interpretazione registrata** (correggibile dal committente): accettate le raccomandazioni → architettura A potenziata con SameBoy (D-001), Negozio/PC solo sul posto (D-010), mappa extra visibile (D-015). Si procede con analisi e prototipo. |
| D-020 | 2026-11-17 | Promemoria orario per continuare il lavoro in autonomia; report e APK quando il committente li chiede. Creato ambiente cloud dedicato "Pokyellow-android" con accesso a dl.google.com (Android SDK/NDK). |
| D-021 | 2026-11-17 | Versioni di riferimento verificate su repository ufficiali: AGP 9.5.1, Gradle 9.9.1, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk/targetSdk 36, build-tools 36.1.0, NDK 30.0.16248370, CMake 3.31.6. |

## Decisioni in attesa
| ID | Decisione | Proposta | Stato |
|---|---|---|---|
| D-001 | Architettura | A potenziata: motore di emulazione (SameBoy) + ponte di lettura stato + UI nativa Compose | ✅ vedi D-019 (da confermare se l'interpretazione è errata) |
| D-003 | Visibilità repository | Il committente vuole renderlo privato; va fatto da GitHub (Claude non ha accesso alle impostazioni) | ⏳ a carico del committente |
| D-004 | Versioni supportate | Solo la versione italiana fornita (riconosciuta tramite SHA-1) | proposta predefinita |
| D-010 | Pulsanti Negozio e PC | Proposta fedele: attivi solo davanti al bancone o al PC (si illuminano). "Sempre utilizzabili" = meccanica nuova | ⛔ attesa scelta |
| D-011 | Configurazione definitiva dei comandi | Da scegliere con il Laboratorio (`docs/demo/laboratorio-giallo-touch.html`) | ⏳ in prova |
