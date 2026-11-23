"""Genera un paquete de voz: un clip .ogg (Opus) por frase + index.json {clave: [archivo, ms]}.
Uso: python3 pack.py frases.json salida_dir voz [velocidad]
Reutilizable: frases.json es {clave: texto}; cambiá el texto y la voz para cualquier proyecto."""
import json,os,sys,hashlib,subprocess,numpy as np
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from kvoz import synth,save_wav
F=json.load(open(sys.argv[1])); out=sys.argv[2]; voz=sys.argv[3]; vel=float(sys.argv[4]) if len(sys.argv)>4 else 1.0
os.makedirs(out,exist_ok=True); idx_p=os.path.join(out,'index.json')
idx=json.load(open(idx_p)) if os.path.exists(idx_p) else {}
def trim(a,sr=24000,th=.012,pad=.06):
    nz=np.where(np.abs(a)>th)[0]
    if not len(nz): return a
    s=max(0,nz[0]-int(pad*sr)); e=min(len(a),nz[-1]+int(pad*sr*2)); return a[s:e]
def fade(a,sr=24000,ms=8):
    n=int(sr*ms/1000); w=np.linspace(0,1,n); a=a.copy(); a[:n]*=w; a[-n:]*=w[::-1]; return a
done=0
for k,t in F.items():
    if k in idx and os.path.exists(os.path.join(out,idx[k][0])): continue
    fn=hashlib.md5(k.encode()).hexdigest()[:10]+'.ogg'
    a,_=synth(t,voz,vel); a=fade(trim(a)); pk=np.max(np.abs(a)) or 1; a=a/pk*.92
    tmp=f'/tmp/{voz}.wav'; save_wav(tmp,a)
    subprocess.run(['ffmpeg','-y','-loglevel','error','-i',tmp,'-c:a','libopus','-b:a','28k','-ac','1','-application','voip',os.path.join(out,fn)],check=True)
    idx[k]=[fn,int(len(a)/24)]; done+=1
    if done%50==0: json.dump(idx,open(idx_p,'w'),ensure_ascii=False); print(voz,done,flush=True)
json.dump(idx,open(idx_p,'w'),ensure_ascii=False); print('listo',voz,len(idx))
