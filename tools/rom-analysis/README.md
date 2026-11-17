# Strumenti di analisi della ROM (solo sviluppo)

Script usati per ricostruire la mappa della memoria della **versione italiana**.
Non fanno parte dell'app e non contengono nulla del gioco: la ROM si passa come argomento
e resta fuori dal repository (vedi `.gitignore`).

Requisiti: Python 3 con `pyboy` (emulatore usato solo per l'analisi, non incluso nell'app).

| Script | Cosa fa |
|---|---|
| `gbtool.py` | Funzioni comuni: apertura ROM, pressione tasti, screenshot. |
| `newgame.py ROM CARTELLA` | Avvia una nuova partita con nomi predefiniti e salva uno stato `after_newgame.state` nella cartella superiore. |
| `findpos2.py ROM STATO CARTELLA` | Cammina in più direzioni e cerca i byte che cambiano come le coordinate X/Y. |
| `dumppos.py ROM STATO` | Stampa la memoria intorno alle coordinate a ogni passo. |
| `verify_main.py ROM STATO` | Verifica nome, squadra, borsa, soldi, opzioni agli indirizzi calcolati. |
| `find_menu.py ROM STATO CARTELLA` | Apre il menu START e trova cursore dei menu e mappa dello schermo. |
| `find_owmap.py ROM STATO FILE.blk` | Trova la mappa dei blocchi in RAM confrontandola con i dati della stanza (file .blk di pret, solo in locale). |
| `verify_sprites.py ROM STATO` | Mostra i dati degli sprite (giocatore, compagno, personaggi). |

Gli script si lanciano con `python -I` (modalità isolata); aggiungono da soli la propria
cartella al percorso dei moduli. Screenshot e stati salvati vanno solo in una cartella
temporanea, mai nel repository.
