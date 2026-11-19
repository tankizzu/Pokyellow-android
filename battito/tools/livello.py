#!/usr/bin/env python3
"""Generatore del livello "Primo Battito".

Ogni sezione è larga un multiplo di 16 colonne (= battute della musica a 128 BPM).
Si posizionano gli oggetti per coordinate e lo script riscrive i disegni di testo in Levels.kt.
Uso:  python3 tools/livello.py   poi   ./gradlew testDebugUnitTest  (il bot verifica che sia completabile)

put(carattere, colonna, riga=0, larghezza=1, altezza=1)   riga 0 = appena sopra il pavimento
wall(carattere, colonna)  muro alto 8 (invalicabile)
"""
import os
H=8
class S:
    def __init__(s,bars,name):
        s.w=bars*16; s.name=name; s.g=[['.']*s.w for _ in range(H)]  # g[y][x], y=0 bottom
    def put(s,ch,x,y=0,w=1,h=1):
        for xx in range(x,x+w):
            for yy in range(y,y+h): s.g[yy][xx]=ch
        return s
    def wall(s,ch,x): return s.put(ch,x,0,1,H)
    def rows(s):
        top=max([y for y in range(H) if any(c!='.' for c in s.g[y])]+[0])
        return [''.join(s.g[y]) for y in range(top,-1,-1)]

secs=[]
# A intro (P0)
a=S(4,"Intro: spuntoni a tempo")
for x in (20,28,36,44,45,55,59,60): a.put('^',x)
a.put('#',50,w=2)
secs.append(a)
# B build
b=S(4,"Crescendo: gradini e primi muri (ciano -> magenta -> ciano -> magenta)")
b.put('#',6,w=2); b.put('#',8,w=2,h=2); b.put('^',14)
b.wall('C',24); b.put('^',30); b.wall('M',40); b.put('^',46,w=2); b.wall('C',56)
secs.append(b)
# C drop A (P1)
c1=S(4,"Drop: muri sul tempo, letti di spuntoni colorati")
for x,ch in ((4,'M'),(12,'C'),(20,'M'),(28,'C')): c1.wall(ch,x)
c1.put('m',36,w=6); c1.put('c',48,w=6)
secs.append(c1)
c2=S(4,"Drop: piattaforme colorate sopra il fossato")
c2.put('#',3); c2.put('#',4,w=2,h=2)
c2.put('^',6,w=28)
for x,ch in ((6,'M'),(13,'C'),(20,'M'),(27,'C')): c2.put(ch,x,1,4,1)
c2.put('#',34,w=2,h=2); c2.put('^',44); c2.put('^',52,w=2)
secs.append(c2)
c3=S(4,"Drop: corridoio della gravita' (tieni premuto il tasto gravita' per correre sul soffitto)")
c3.put('#',8,3,48,1)                      # soffitto del corridoio
c3.put('^',14,w=7); c3.put('^',30,w=9)    # spuntoni a terra: corri sul soffitto
c3.put('v',24,2,3,1); c3.put('v',44,2,4,1) # spuntoni appesi: torna a terra
c3.put('^',60)
secs.append(c3)
c4=S(4,"Drop: anelli, muro, galleria senza salti")
c4.put('^',3,w=13)
for x in (5,9,13): c4.put('o',x,2)
c4.wall('C',22)
c4.put('#',34,3,22,1); c4.put('v',34,2,22,1)
c4.put('c',40,w=2); c4.put('m',46,w=2); c4.put('c',52,w=2)
c4.wall('M',60)
secs.append(c4)
# D pausa (P0): navicella
d=S(8,"Pausa in volo: NAVICELLA (tocca per salire, lascia per scendere) e portale specchio")
d.put('N',2)
d.put('#',12,0,2,4)                    # colonna dal basso
d.put('#',22,4,2,4)                    # colonna dall'alto
d.put('#',32,0,2,3); d.put('#',32,6,2,2)  # passaggio in mezzo
d.put('#',42,0,2,5)
d.put('#',52,3,2,5)
d.wall('C',62); d.wall('M',70)         # muri colorati anche in volo
for x,y in ((84,0),(92,3),(100,0),(108,3)): d.put('#',x,y,2,5)   # slalom
d.put('Q',118)
d.put('<',124)                         # da qui si corre a specchio
secs.append(d)
# E drop B (P1)
e1=S(4,"Secondo drop A SPECCHIO: muri ogni 6 tile")
for x,ch in ((2,'M'),(8,'C'),(14,'M'),(20,'C')): e1.wall(ch,x)
e1.put('^',26); e1.put('m',30,w=4); e1.put('c',36,w=4); e1.put('^',44,w=2)
e1.wall('M',52); e1.put('^',57); e1.wall('C',62)
secs.append(e1)
e2=S(4,"Secondo drop A SPECCHIO: piattaforme e galleria colorata")
e2.put('#',3); e2.put('#',4,w=2,h=2); e2.put('^',6,w=28)
for x,ch in ((6,'M'),(13,'C'),(20,'M'),(27,'C')): e2.put(ch,x,1,4,1)
e2.put('#',34,w=2,h=2)
e2.put('#',40,3,21,1); e2.put('v',40,2,21,1)
e2.put('c',46,w=2); e2.put('m',52,w=2); e2.put('c',58,w=2)
secs.append(e2)
e3=S(4,"Finale: fine dello specchio, anelli, pedana, tratto SOTTOSOPRA")
e3.put('>',0)
e3.put('^',6,w=13)
for x in (8,12,16): e3.put('o',x,2)
e3.put('p',23); e3.put('C',26,2,5,1); e3.put('^',24,w=8)
e3.put('U',36)                         # gravita' verso l'alto
e3.put('#',36,7,26,1)                  # soffitto su cui correre
for x in (44,50,51,57): e3.put('v',x,6) # spuntoni da saltare "verso il basso"
e3.put('D',62)                         # gravita' normale
secs.append(e3)
f=S(4,"Outro: traguardo")
secs.append(f)

out=[]
for s in secs:
    out.append(f"            // {s.name}")
    rows=s.rows()
    if len(rows)==1: out.append(f'            listOf("{rows[0]}"),')
    else:
        out.append("            listOf(")
        for r in rows: out.append(f'                "{r}",')
        out.append("            ),")
total=sum(s.w for s in secs)
assert total==768, total
here=os.path.dirname(os.path.abspath(__file__))
dst=os.path.join(here,'..','app','src','main','java','it','battito','game','Levels.kt')
kt=open(dst).read()
a=kt.index('        listOf(\n')+len('        listOf(\n'); b=kt.index('        )\n    )')
open(dst,'w').write(kt[:a]+"\n".join(out)+"\n"+kt[b:])
print("Levels.kt aggiornato:", total, "colonne")
