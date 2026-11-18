# Prove grafiche "come il gioco" (solo in locale)

`gbui.py` disegna testi e riquadri usando i **tile veri del gioco** letti da una copia della memoria video
(`runner` → comando `vram NOME`), così le aggiunte dell'app sono identiche al gioco (D-039).

- Le immagini prodotte (schermate del gioco con le aggiunte) sono **solo per uso privato**: si tengono nella
  cartella temporanea e si mostrano al committente direttamente, mai nel repository né in pagine pubblicate (R-01).
- Sequenze del `runner` usate per le schermate dell'avvio: titolo (avvio + START), menu iniziale con CONTINUA
  (salvare dal menu START, poi `reset`), introduzione (NUOVO GIOCO + A), nome (`naming`), cameretta (`ext NOME 354`).

## Mockup modificabile (privato)
`build_editor.py` crea `mockup-avvio.html` (fuori dal repository) partendo da `editor_template.html`, dalle schermate
vere del runner (`../real/*.ppm`, `*.vram`) e dai tile del gioco: ogni scritta, riquadro e immagine è una parte che il
committente può spostare, ridimensionare o eliminare; il riepilogo delle modifiche si copia in chat.
Il file prodotto contiene immagini del gioco: si invia solo al committente, non si pubblica e non si aggiunge al repository.
