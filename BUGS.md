# BUGS

Formato: `ID · data · componente · descrizione · causa · stato`.

- **B-001** · 2026-11-18 · scelta del gioco · Con l'APK 0.1/0.2 il gioco non partiva: l'app diceva che il file non era corretto.
  · Causa: il file sul telefono è uno ZIP (come quello caricato), l'app accettava solo il `.gb` estratto; con
  "Usa comunque" salvava lo ZIP come gioco. · **Risolto in 0.2.1**: l'app apre gli ZIP, controlla l'intestazione
  del gioco e, all'avvio, ripara o scarta un file salvato non valido. Verificato con lo ZIP vero (test locale).
