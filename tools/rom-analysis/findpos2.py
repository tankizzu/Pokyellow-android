import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gbtool import open_rom, shot
rom, state, out = sys.argv[1], sys.argv[2], sys.argv[3]
pb = open_rom(rom, state)
def snap(): return bytes(pb.memory[a] for a in range(0xC000, 0xE000))
def walk(d):
    pb.button_press(d); pb.tick(20, False); pb.button_release(d); pb.tick(30, False)
pb.tick(60, False)
seq = ["down", "down", "right", "right", "up", "left"]
snaps = [snap()]
for i, d in enumerate(seq):
    walk(d); snaps.append(snap()); shot(pb, f"{out}/fp2_{i}_{d}.png")
exp = {"down": (0, 1), "up": (0, -1), "right": (1, 0), "left": (-1, 0)}
xs, ys = [], []
for i in range(0x2000):
    okx = oky = True
    for k, d in enumerate(seq):
        dx, dy = exp[d]
        delta = (snaps[k+1][i] - snaps[k][i]) & 0xFF
        delta = delta - 256 if delta > 127 else delta
        if delta != dx: okx = False
        if delta != dy: oky = False
    if okx: xs.append(0xC000 + i)
    if oky: ys.append(0xC000 + i)
print("X:", [f"{a:04X}={snaps[0][a-0xC000]}" for a in xs])
print("Y:", [f"{a:04X}={snaps[0][a-0xC000]}" for a in ys])
