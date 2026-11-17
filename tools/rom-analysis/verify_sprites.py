import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gbtool import open_rom
rom, state = sys.argv[1], sys.argv[2]
pb = open_rom(rom, state); pb.tick(30, False)
m = pb.memory
print("wYCoord/wXCoord:", m[0xD365], m[0xD366])
for n in range(16):
    d1 = [m[0xC100 + n * 16 + i] for i in range(16)]
    d2 = [m[0xC200 + n * 16 + i] for i in range(16)]
    if d1[0]:
        print(f"sprite {n:2d}: pic={d1[0]:02X} stato={d1[1]:02X} yPix={d1[4]:3d} xPix={d1[6]:3d} dir={d1[9]:02X} | mapY={d2[4]} mapX={d2[5]}")
