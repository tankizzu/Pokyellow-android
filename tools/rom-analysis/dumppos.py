import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gbtool import open_rom
rom, state = sys.argv[1], sys.argv[2]
pb = open_rom(rom, state)
def row(): return " ".join(f"{pb.memory[a]:02X}" for a in range(0xD35C, 0xD372))
def walk(d):
    pb.button_press(d); pb.tick(20, False); pb.button_release(d); pb.tick(30, False)
pb.tick(60, False)
print("        " + " ".join(f"{a & 0xFF:02X}" for a in range(0xD35C, 0xD372)))
print("start  ", row())
for d in ["down", "down", "right", "right", "up", "left"]:
    walk(d); print(f"{d:7s}", row())
