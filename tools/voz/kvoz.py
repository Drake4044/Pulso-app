"""Generador de voces del coach (reutilizable para otros proyectos).
Motor: Kokoro-82M (Apache-2.0) en ONNX + fonemas IPA de espeak-ng.
Uso: python3 kvoz.py frases.json salida/ --voz ef_dora --vel 1.0"""
import json, os, re, subprocess, sys, numpy as np, onnxruntime as ort, wave
D=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','dl')
ESPEAK=os.path.join(D,'piper','espeak-ng'); EDATA=os.path.join(D,'piper','espeak-ng-data')
CFG=json.load(open(os.path.join(D,'config.json'))); VOCAB=CFG['vocab']
VOICES=np.load(os.path.join(D,'voices-v1.0.bin'))
_sess=None
def sess():
    global _sess
    if _sess is None:
        o=ort.SessionOptions(); o.intra_op_num_threads=os.cpu_count() or 4
        _sess=ort.InferenceSession(os.path.join(D,'kokoro-v1.0.onnx'),o,providers=['CPUExecutionProvider'])
    return _sess
def ipa(txt,lang='es-419'):
    env=dict(os.environ,ESPEAK_DATA_PATH=EDATA,LD_LIBRARY_PATH=os.path.join(D,'piper'))
    out=subprocess.run([ESPEAK,'--path='+D+'/piper','-q','--ipa','-v',lang,txt],capture_output=True,text=True,env=env).stdout
    return ' '.join(out.split())
def phonemize(text,lang='es-419'):
    # se separa por signos de puntuación para conservarlos (dan las pausas y la entonación)
    parts=re.split(r'([.,;:!?¡¿…—])',text); res=''
    for p in parts:
        if not p.strip(): continue
        if re.fullmatch(r'[.,;:!?¡¿…—]',p): res=res.rstrip()+p+' '
        else: res+=ipa(p,lang)+' '
    return res.strip()
def tokens(ph): return [VOCAB[c] for c in ph if c in VOCAB]
def synth(text,voice='ef_dora',speed=1.0,lang='es-419'):
    ph=phonemize(text,lang); tk=tokens(ph)[:509]
    st=VOICES[voice][len(tk)]
    s=sess(); names=[i.name for i in s.get_inputs()]
    feed={names[0]:np.array([[0,*tk,0]],dtype=np.int64),names[1]:st.astype(np.float32),names[2]:np.array([speed],dtype=np.float32)}
    return s.run(None,feed)[0].squeeze(), ph
def save_wav(path,a,sr=24000):
    a=np.clip(a,-1,1); x=(a*32767).astype(np.int16)
    with wave.open(path,'wb') as w: w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes(x.tobytes())
if __name__=='__main__':
    t=sys.argv[1] if len(sys.argv)>1 else '¡Muy bien! Bajá lento, pecho arriba, y subí con fuerza. ¡Eso es!'
    for v in sys.argv[2:] or ['ef_dora','em_alex']:
        a,ph=synth(t,v); save_wav(f'/home/claude/vozlab/test_{v}.wav',a); print(v,ph,len(a)/24000)
