import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gbtool import open_rom, press, shot
rom, state, out = sys.argv[1], sys.argv[2], sys.argv[3]
pb = open_rom(rom, state); pb.tick(30, False)
snap = lambda: bytes(pb.memory[a] for a in range(0xC000, 0xE000))
s_closed = snap()
press(pb, "start", wait=40); s0 = snap(); shot(pb, f"{out}/menu0.png")
press(pb, "down", wait=20); s1 = snap()
press(pb, "down", wait=20); s2 = snap()
cand = [0xC000 + i for i in range(0x2000) if s0[i] == 0 and s1[i] == 1 and s2[i] == 2]
print("wCurrentMenuItem candidati:", [f"{a:04X}" for a in cand])
pat = bytes([0x79] + [0x7A] * 8 + [0x7B])
i = s0.find(pat)
while i != -1:
    print(f"bordo menu (┌──┐) a {0xC000 + i:04X} -> wTileMap = {0xC000 + i - 10:04X} se il menu parte a colonna 10")
    i = s0.find(pat, i + 1)
changed = [0xC000 + i for i in range(0x2000) if s_closed[i] != s0[i] and 0xCC00 <= 0xC000 + i < 0xD100]
print("byte cambiati aprendo il menu in CC00-D0FF:", " ".join(f"{a:04X}:{s_closed[a-0xC000]:02X}>{s0[a-0xC000]:02X}" for a in changed[:40]))
