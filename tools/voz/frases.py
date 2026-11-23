import json,re,sys
def sents(t): return [x.strip() for x in re.split(r'(?<=[.!?:…])\s+',t.strip()) if x.strip()]
def key(x):
    x=x.lower().replace('…','.')
    x=re.sub(r'[¡¿"«»“”]','',x); x=re.sub(r'\s+',' ',x).strip(); return re.sub(r'[.!?:,;\s]+$','',x)
if __name__=='__main__':
    T=json.load(open(sys.argv[1])); S={}
    for t in T:
        for s in sents(t):
            k=key(s)
            if k and k not in S and not re.search(r'W_\(|=>|[{}$\\]|\n|^[)\]]',s): S[k]=s
    json.dump(S,open(sys.argv[2],'w'),ensure_ascii=False,indent=0); print(len(S))
