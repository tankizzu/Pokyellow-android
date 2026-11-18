"""Disegno di elementi "come il gioco" con i tile veri (carattere e riquadri) presi dalla memoria video.
Solo per prove locali: le immagini prodotte restano nella cartella temporanea."""
from PIL import Image

class TileSet:
    def __init__(self, vram_path, ref_frame_path, ref_map):
        d = open(vram_path, 'rb').read()
        self.vram = d[:0x4000]
        self.lcdc = d[0x4000 + 128]
        frame = Image.open(ref_frame_path).convert('RGB')
        # colori: dal riquadro del menu nel fotogramma vero (indice di colore → colore sullo schermo)
        self.colors = {}
        for (tx, ty), t in ref_map.items():
            px = self.tile_pixels(t)
            for y in range(8):
                for x in range(8):
                    self.colors.setdefault(px[y][x], frame.getpixel((tx * 8 + x, ty * 8 + y)))
        self.colors.setdefault(0, (248, 248, 248)); self.colors.setdefault(3, (24, 24, 24))
        for i in (1, 2): self.colors.setdefault(i, self.colors[3])

    def tile_pixels(self, t):
        if t >= 0x80: base = 0x8800 + (t - 0x80) * 16
        elif self.lcdc & 0x10: base = 0x8000 + t * 16
        else: base = 0x9000 + t * 16
        base -= 0x8000
        rows = []
        for y in range(8):
            lo, hi = self.vram[base + 2 * y], self.vram[base + 2 * y + 1]
            rows.append([((hi >> (7 - x)) & 1) << 1 | ((lo >> (7 - x)) & 1) for x in range(8)])
        return rows

    def put(self, img, t, x, y):
        px = self.tile_pixels(t)
        for j in range(8):
            for i in range(8):
                img.putpixel((x + i, y + j), self.colors[px[j][i]])

ENC = {' ': 0x7F, ':': 0x9C, '/': 0xF3, '.': 0xE8, '?': 0xE6, '!': 0xE7, '▶': 0xED, '▼': 0xEE, 'é': 0xBC, '-': 0xE3, '×': 0xF1, ',': 0xF4, '(': 0x9A, ')': 0x9B}
def enc(s):
    out = []
    for c in s:
        if 'A' <= c <= 'Z': out.append(0x80 + ord(c) - 65)
        elif 'a' <= c <= 'z': out.append(0xA0 + ord(c) - 97)
        elif '0' <= c <= '9': out.append(0xF6 + ord(c) - 48)
        else: out.append(ENC[c])
    return out

def text(ts, img, s, tx, ty, ox=0, oy=0):
    for i, t in enumerate(enc(s)): ts.put(img, t, ox + (tx + i) * 8, oy + ty * 8)

def box(ts, img, x0, y0, x1, y1, ox=0, oy=0):
    """Riquadro del gioco da (x0,y0) a (x1,y1) in tile, angoli compresi."""
    for ty in range(y0, y1 + 1):
        for tx in range(x0, x1 + 1):
            if ty == y0: t = 0x79 if tx == x0 else 0x7B if tx == x1 else 0x7A
            elif ty == y1: t = 0x7D if tx == x0 else 0x7E if tx == x1 else 0x7A
            else: t = 0x7C if tx in (x0, x1) else 0x7F
            ts.put(img, t, ox + tx * 8, oy + ty * 8)
