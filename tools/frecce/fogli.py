import sys, json, os
from PIL import Image
src, out, name = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(out, exist_ok=True)
rows=[l.split() for l in open(src+'/indice.txt')]
files=[]; idx={}
frames=[]
for r in rows:
    f=r[1]
    if f not in idx: idx[f]=len(files); files.append(f)
    flags=' '.join(t for t in r[6:] if t.endswith('=true'))
    frames.append([int(r[0]), idx[f], r[3], flags])
W,H=320,710; C=5; R=5; per=C*R
nsheets=(len(files)+per-1)//per
for s in range(nsheets):
    sheet=Image.new('RGB',(W*C,H*R),(0,0,0))
    for k,f in enumerate(files[s*per:(s+1)*per]):
        im=Image.open(src+'/'+f).convert('RGB').resize((W,H),Image.NEAREST)
        sheet.paste(im,((k%C)*W,(k//C)*H))
    sheet.save(f'{out}/{name}-{s:03d}.png', optimize=True)
json.dump({'name':name,'w':W,'h':H,'cols':C,'per':per,'sheets':nsheets,'frames':frames}, open(f'{out}/{name}.json','w'), separators=(',',':'))
print(len(files), nsheets)
