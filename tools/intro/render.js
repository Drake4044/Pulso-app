const {chromium}=require('playwright');const fs=require('fs');const OUT=process.argv[2];
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:520,height:560},deviceScaleFactor:1.5});
await p.goto('file:///home/claude/pulso/introlab/render.html');fs.mkdirSync(OUT,{recursive:true});
const FPS=30,END=4600;let n=0;for(let ms=0;ms<=END;ms+=1000/FPS){await p.evaluate(t=>setT(t),ms);await p.screenshot({path:`${OUT}/f${String(n++).padStart(4,'0')}.png`})}
console.log(n);await b.close()})();
