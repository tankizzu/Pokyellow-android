# Fotogramma per fotogramma (pagina privata)

Pagina per scorrere una registrazione d'analisi (`registra(...)` in AppScreensTest) un fotogramma alla volta:
frecce ◀ ▶ (1), ◀◀ ▶▶ (10), "cambio" (salta al prossimo fotogramma diverso), salto a un numero, fasi, riproduzione.

1. `python3 fogli.py CARTELLA_REGISTRAZIONE CARTELLA_USCITA NOME` → fogli PNG (25 fotogrammi ciascuno) e `NOME.json`.
2. Copiare `index.html` nella cartella d'uscita, mettere `NOME` nella costante `NAME`, pubblicare come pagina privata.

Le immagini contengono la grafica del gioco: la cartella d'uscita resta fuori dal repository e la pagina resta privata
(scelta del committente, 2026-11-20).
