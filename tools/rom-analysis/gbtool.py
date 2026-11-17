from pyboy import PyBoy
def open_rom(rom, state=None):
    pb = PyBoy(rom, window="null", cgb=True, sound_emulated=False)
    pb.set_emulation_speed(0)
    if state:
        with open(state, "rb") as f: pb.load_state(f)
    return pb
def press(pb, btn, hold=4, wait=12):
    pb.button_press(btn); pb.tick(hold, False); pb.button_release(btn); pb.tick(wait, False)
def shot(pb, path):
    pb.tick(1, True); pb.screen.image.save(path)
