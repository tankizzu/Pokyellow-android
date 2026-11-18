import sys, io, base64, json
sys.path.insert(0, '.')
from PIL import Image
from gbui import TileSet
R = '../real/'
H = 354
ts = TileSet(R + 'menu3.vram', R + 'menu3.ppm', {(0, 0): 0x79, (2, 2): 0x82, (14, 7): 0x7E, (1, 2): 0xED})
tsn = TileSet(R + 'nm.vram', R + 'nm.ppm', {(0, 4): 0x79})

def b64(img):
    buf = io.BytesIO(); img.save(buf, 'PNG'); return 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()

# Foglio dei tile (16×16 tile da 8×8): carattere e riquadri del gioco; 0x76/0x77 dai tile della schermata dei nomi.
sheet = Image.new('RGB', (128, 128), ts.colors[0])
for t in range(256):
    src = tsn if t in (0x76, 0x77) else ts
    src.put(sheet, t, (t % 16) * 8, (t // 16) * 8)
white = '#%02x%02x%02x' % ts.colors[0]

title = Image.open(R + 'b6.ppm')
oak = Image.open(R + 'o3.ppm')
bed = Image.open(R + 'bedext.ppm')
imgs = {
    'titolo': b64(title),
    'scena': b64(oak.crop((0, 0, 160, 96))),
    'testo': b64(oak.crop((0, 96, 160, 144))),
    'cameretta': b64(bed),
}
Y = 104   # immagine 10:9 al centro, sulla griglia di 8 pixel

def T(text, x, y, label=None): return {'type': 'text', 'text': text, 'x': x, 'y': y, 'label': label or text}
def B(x, y, w, h, label): return {'type': 'box', 'x': x, 'y': y, 'w': w, 'h': h, 'label': label}
def I(key, x, y, w, h, label): return {'type': 'img', 'img': key, 'x': x, 'y': y, 'w': w, 'h': h, 'label': label}

screens = []
screens.append({'id': 'titolo', 'label': 'Titolo', 'bg': white, 'parts': [I('titolo', 0, Y, 160, 144, 'Immagine del titolo')]})
screens.append({'id': 'menu', 'label': 'Menu iniziale', 'bg': white, 'parts': [
    B(0, Y, 17, 10, 'Riquadro del menu'), T('▶', 8, Y + 16, 'Cursore'),
    T('CONTINUA', 16, Y + 16), T('NUOVO GIOCO', 16, Y + 32), T('OPZIONI', 16, Y + 48), T('CARICA PARTITA', 16, Y + 64)]})
car = [B(0, 24, 20, 3, 'Riquadro del titolo'), T('CARICA PARTITA', 24, 32)]
for k, (lab, tempo) in enumerate([('SALVATAGGIO', '0:12'), ('AUTOMATICO', '0:15')]):
    y0 = 24 + (4 + k * 11) * 8
    car += [B(0, y0, 20, 10, 'Riquadro ' + lab.lower())]
    if k == 0: car.append(T('▶', 8, y0 + 16, 'Cursore'))
    car += [T(lab, 16, y0 + 16), T('18/11', 112, y0 + 16, 'Data ' + lab.lower()),
            T('GIOCA', 16, y0 + 32, 'GIOCA ' + lab.lower()), T('GIALLO', 104, y0 + 32, 'Nome ' + lab.lower()),
            T('MEDAGLIE', 16, y0 + 48, 'MEDAGLIE ' + lab.lower()), T('0', 144, y0 + 48, 'Medaglie ' + lab.lower()),
            T('TEMPO', 16, y0 + 64, 'TEMPO ' + lab.lower()), T(tempo, 120, y0 + 64, 'Tempo ' + lab.lower())]
y0 = 24 + 26 * 8
car += [B(0, y0, 20, 4, 'Riquadro INDIETRO'), T('INDIETRO', 16, y0 + 16)]
screens.append({'id': 'carica', 'label': 'Carica partita', 'bg': white, 'parts': car})
screens.append({'id': 'intro', 'label': 'Introduzione', 'bg': white, 'parts': [
    I('scena', 0, Y, 160, 96, 'Professore'), I('testo', 0, Y + 96, 160, 48, 'Riquadro del testo')]})
top = 32
screens.append({'id': 'nome', 'label': 'Il tuo nome', 'bg': white, 'parts': [
    T('NOME TUO?', 0, top + 8, 'Domanda'), T('LUCA', 80, top + 16, 'Nome scritto'),
    {'type': 'text', 'text': 'vvvvwvv', 'raw': [0x76, 0x76, 0x76, 0x76, 0x77, 0x76, 0x76], 'x': 80, 'y': top + 24, 'label': 'Trattini'},
    B(96, top + 40, 8, 4, 'Riquadro FINE'), T('▶FINE', 104, top + 56, 'FINE'),
    {'type': 'kbd', 'x': 0, 'y': int(H * 0.6), 'w': 160, 'h': H - int(H * 0.6), 'label': 'Tastiera del telefono'}]})
tab = [B(136, 216, 3, 5, 'Linguetta'), {'type': 'text', 'text': '═', 'raw': [0x7A, 0x7A, 0x7A], 'vertical': True, 'x': 144, 'y': 224, 'label': 'Righe della linguetta'}]
screens.append({'id': 'cameretta', 'label': 'Cameretta', 'bg': '#000000', 'parts': [I('cameretta', 0, 0, 160, H, 'Mappa')] + json.loads(json.dumps(tab))})
items = ['SQUADRA', 'BORSA', 'POKéDEX', 'MENU', 'TESTO', 'SALVA', 'ESCI']
pan = [B(56, 128, 10, 16, 'Riquadro dei pulsanti'), T('▶', 64, 144, 'Cursore')] + [T(s, 72, 144 + 16 * i) for i, s in enumerate(items)]
screens.append({'id': 'pulsanti', 'label': 'Pulsanti aperti', 'bg': '#000000', 'parts': [I('cameretta', 0, 0, 160, H, 'Mappa')] + json.loads(json.dumps(tab)) + pan})

ENC = {' ': 0x7F, ':': 0x9C, '/': 0xF3, '.': 0xE8, '?': 0xE6, '!': 0xE7, '▶': 0xED, '▼': 0xEE, 'é': 0xBC, '-': 0xE3, '×': 0xF1, ',': 0xF4, '(': 0x9A, ')': 0x9B, '═': 0x7A}
data = {'H': H, 'sheet': b64(sheet), 'imgs': imgs, 'screens': screens, 'enc': ENC, 'white': white}
html = open('editor_template.html').read().replace('/*__DATA__*/null', json.dumps(data))
open('mockup-avvio.html', 'w').write(html)
print('ok', len(html))
