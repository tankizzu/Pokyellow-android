import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gbtool import open_rom
rom, state = sys.argv[1], sys.argv[2]
pb = open_rom(rom, state); pb.tick(30, False)
m = lambda a, n=1: [pb.memory[a + i] for i in range(n)]
hx = lambda l: " ".join(f"{b:02X}" for b in l)
print("wPlayerMoney  D34B:", hx(m(0xD34B, 3)), "(atteso 00 30 00 = 3000)")
print("wNumBagItems  D321:", hx(m(0xD321, 3)), "(atteso 00 FF ..)")
print("wPartyCount   D167:", hx(m(0xD167, 3)), "(atteso 00 FF ..)")
print("wPlayerName   D15C:", hx(m(0xD15C, 11)))
print("wRivalName    D34E:", hx(m(0xD34E, 11)))
print("wOptions      D359:", hx(m(0xD359)), " badges D35A:", hx(m(0xD35A)), " ID D35D:", hx(m(0xD35D, 2)))
print("wPokedexOwned D2FB:", hx(m(0xD2FB, 19)))
