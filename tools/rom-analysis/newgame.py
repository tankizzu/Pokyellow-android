import sys
import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gbtool import open_rom, press, shot
rom, out = sys.argv[1], sys.argv[2]
pb = open_rom(rom)
pb.tick(600, False)
press(pb, "start", wait=120)   # salta intro -> titolo
press(pb, "start", wait=90)    # titolo -> menu
shot(pb, f"{out}/ng_menu.png")
press(pb, "a", wait=90)        # nuova partita
for i in range(1, 121):
    press(pb, "down", hold=3, wait=8)
    press(pb, "a", hold=3, wait=40)
    if i % 20 == 0: shot(pb, f"{out}/ng_{i:03d}.png")
with open(f"{out}/../after_newgame.state", "wb") as f: pb.save_state(f)
pb.stop(save=False)
