# DECISIONS

## Requisiti approvati (dal brief iniziale del committente, 2026-11-17)
- R-01 Nessuna ROM o contenuto protetto nel repository/APK.
- R-02 Offline, nessun server, nessun pagamento.
- R-03 Verticale come modalità principale, orizzontale quando utile, nessun cambio forzato e continuo.
- R-04 Nessuna pulsantiera virtuale permanente (niente croce, niente A/B fissi).
- R-05 Fedeltà meccaniche prioritaria; nuove meccaniche solo con approvazione.
- R-06 Sviluppo per fasi (Analisi → Progettazione → Prototipo → Implementazione → Test → Distribuzione).
- R-07 Documentazione persistente nel repository.

## Decisioni in attesa di approvazione
| ID | Decisione | Proposta | Stato |
|---|---|---|---|
| D-001 | Architettura | A potenziata: core SameBoy + ponte di lettura stato + UI nativa Compose | ⛔ in attesa |
| D-002 | Ripresa rapida | Istantanea automatica a slot unico alla chiusura/sospensione, non caricabile a piacere (niente "salva ovunque") | ⛔ in attesa |
| D-003 | Visibilità repository | Il repository è pubblico: consigliato renderlo privato | ⛔ in attesa |
| D-004 | Versioni supportate | Solo la versione italiana fornita (riconosciuta tramite SHA-1) | proposta predefinita |
