import json,os,shutil,sys
src,dst,v=sys.argv[1],sys.argv[2],sys.argv[3]
os.makedirs(dst,exist_ok=True)
idx=json.load(open(os.path.join(src,'index.json')))
for k,(f,ms) in idx.items(): shutil.copy(os.path.join(src,f),os.path.join(dst,f))
open(os.path.join(dst,'index.js'),'w').write(f'window.VOZ_{v}='+json.dumps(idx,ensure_ascii=False,separators=(",",":"))+';')
print(v,len(idx))
