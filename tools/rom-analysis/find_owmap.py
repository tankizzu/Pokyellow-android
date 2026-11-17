import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gbtool import open_rom
rom, state, blk = sys.argv[1], sys.argv[2], sys.argv[3]
pb = open_rom(rom, state); pb.tick(30, False)
mem = bytes(pb.memory[a] for a in range(0xC000, 0xE000))
b = open(blk, "rb").read()
W = pb.memory[0xD36D]; Hh = pb.memory[0xD36C]
print("larghezza", W, "altezza", Hh, "blocchi", b.hex())
row0 = b[:W]
i = mem.find(row0)
while i != -1:
    base = i - 3 - 3 * (W + 6)
    ok = all(mem[base + (3 + y) * (W + 6) + 3: base + (3 + y) * (W + 6) + 3 + W] == b[y * W:(y + 1) * W] for y in range(Hh))
    if ok: print(f"wOverworldMap = {0xC000 + base:04X}; bordo = {mem[base]:02X}")
    i = mem.find(row0, i + 1)
