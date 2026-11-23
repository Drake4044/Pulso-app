/*
 * Generador del esquema muscular de Pulso (figura de hombre y de mujer, frente y espalda, 2D y 3D).
 *
 * Fuente: componente "vue-human-muscle-anatomy" (MIT, © 2025 Luca Wahlen). No usamos Vue en la app:
 * acá se renderiza el componente UNA vez en un navegador headless (Playwright), se leen sus <path>
 * y se exportan como datos planos. Así la app no carga ningún framework extra.
 *
 * Qué hace:
 *  1. Por cada grupo muscular del componente, detecta qué <path> le corresponde (pinta ese grupo de rojo y mira cuál cambió).
 *  2. "Hornea" las transformaciones (matrix) dentro de las coordenadas, así cada path queda en coordenadas absolutas.
 *  3. Separa cada path en sub-caminos y los agrupa por vista (frente x<512 / espalda x>=512) y por lado (izq/der),
 *     para que el zoom pueda ir a un solo brazo.
 *  4. Divide el pectoral en porción superior e inferior (idea tomada de la opción "body-muscles") recortando la forma
 *     con polygon-clipping (MIT). También recorta un "flexor de cadera" en la parte alta del muslo.
 *  5. Escribe: anat/body_<g>_<v>.svg (figura 2D), anat/body_<g>_<v>_3d.webp (figura con luz/volumen) y breg.json (regiones).
 *
 * Uso:  node gen2.js   (requiere playwright y pillow para el webp)
 */
const {chromium}=require('playwright');const fs=require('fs');const {execSync}=require('child_process');
const NAMES=["abductors","abs","adductors","biceps","calves","chest","forearms","frontDelts","glutes","hamstrings","lats","lowerBack","neck","obliques","quads","rearDelts","rotatorCuffs","shins","sideDelts","traps","triceps"];
// grupo del componente -> [región de Pulso, cabezas que representa]  (null = solo figura, no se ilumina)
const MAP={abductors:['glutes','med'],abs:['abs',''],adductors:['adductors',''],biceps:['biceps','long short braq'],calves:['calves','gas sol'],
 chest:['pecs','clav est'],forearms:['forearm',''],frontDelts:['delts','ant'],glutes:['glutes','max'],hamstrings:['hams','bf st'],lats:['lats',''],
 lowerBack:['lowerback',''],neck:null,obliques:['obliques',''],quads:['quads','rf vl vm'],rearDelts:['delts','post'],rotatorCuffs:['rhomboids',''],
 shins:null,sideDelts:['delts','med'],traps:['traps',''],triceps:['triceps','long lat']};
const OUT=process.argv[2]||'out';fs.mkdirSync(OUT,{recursive:true});
// colores de la figura base (pensados para verse bien en tema claro y oscuro)
const C={sil:'#2b2833',mus:'#5d5a68',line:'#2b2833'};

(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1024,height:1024}});
 const vue=fs.readFileSync('vue.global.prod.js','utf8'),hma=fs.readFileSync('vue_hma.js','utf8'),pc=fs.readFileSync('pc/package/dist/polygon-clipping.umd.min.js','utf8');
 const BREG={};
 for(const [g,gender] of [['m','male'],['f','female']]){
  const load=async sel=>{await p.setContent(`<div id=a style="width:1024px;height:1024px"></div><script>${vue}</script><script>${hma}</script><script>${pc}</script><script>Vue.createApp({render:()=>Vue.h(window.HMA,{gender:'${gender}',primaryHighlightColor:'#ff0000',primaryOpacity:1,selectedPrimaryMuscleGroups:${JSON.stringify(sel)},selectedSecondaryMuscleGroups:[]})}).mount('#a')</script>`);await p.waitForTimeout(120)};
  await load([]);
  // 1) todos los paths con su matriz acumulada
  const paths=await p.evaluate(()=>[...document.querySelectorAll('#a svg path')].map(e=>{const m=e.getCTM();return {d:e.getAttribute('d'),m:[m.a,m.b,m.c,m.d,m.e,m.f],fill:getComputedStyle(e).fill}}));
  for(const n of NAMES){await load([n]);const hit=await p.evaluate(()=>[...document.querySelectorAll('#a svg path')].map((e,i)=>getComputedStyle(e).fill==='rgb(255, 0, 0)'?i:-1).filter(i=>i>=0));hit.forEach(i=>paths[i].name=n)}
  // 2) hornear matrices: todos los comandos son absolutos (M, C, L, Z) con pares x,y
  const bake=(d,[a,b2,c,dd,e,f])=>d.replace(/(-?\d*\.?\d+(?:e-?\d+)?)\s*,\s*(-?\d*\.?\d+(?:e-?\d+)?)/g,(_,x,y)=>{x=+x;y=+y;return `${(a*x+c*y+e).toFixed(1)},${(b2*x+dd*y+f).toFixed(1)}`});
  paths.forEach(q=>q.D=bake(q.d,q.m));
  // 3) partir en sub-caminos, medirlos y clasificarlos
  const subs=await p.evaluate(list=>{const ns='http://www.w3.org/2000/svg';const s=document.createElementNS(ns,'svg');s.setAttribute('viewBox','0 0 1024 1024');s.style.cssText='position:absolute;left:0;top:0;width:1024px;height:1024px';document.body.appendChild(s);
   const out=[];list.forEach((q,pi)=>{q.D.split(/(?=M)/).forEach(sd=>{const e=document.createElementNS(ns,'path');e.setAttribute('d',sd);s.appendChild(e);const bb=e.getBBox();out.push({pi,d:sd.trim(),bb:[bb.x,bb.y,bb.x+bb.width,bb.y+bb.height]});e.remove()})});s.remove();return out},paths);
  BREG[g]={};
  for(const v of ['f','b']){
   const ox=v==='f'?0:512,inV=sb=>v==='f'?(sb.bb[0]+sb.bb[2])/2<512:(sb.bb[0]+sb.bb[2])/2>=512;
   const vs=subs.filter(inV);
   // silueta = el path negro más grande de la vista
   const sil=vs.filter(sb=>paths[sb.pi].fill==='rgb(0, 0, 0)');
   const body=sil.reduce((a,sb)=>[Math.min(a[0],sb.bb[0]),Math.min(a[1],sb.bb[1]),Math.max(a[2],sb.bb[2]),Math.max(a[3],sb.bb[3])],[1e9,1e9,-1e9,-1e9]);
   const cx=(body[0]+body[2])/2;
   // agrupar sub-caminos por (grupo, lado)
   const groups={};vs.forEach(sb=>{const n=paths[sb.pi].name;if(!n||!MAP[n])return;const side=(sb.bb[0]+sb.bb[2])/2<cx?'L':'R';const k=n+'|'+side;(groups[k]=groups[k]||[]).push(sb)});
   let regs=[];
   for(const [k,arr] of Object.entries(groups)){const [n]=k.split('|');const [r,h]=MAP[n];
    const bb=arr.reduce((a,sb)=>[Math.min(a[0],sb.bb[0]),Math.min(a[1],sb.bb[1]),Math.max(a[2],sb.bb[2]),Math.max(a[3],sb.bb[3])],[1e9,1e9,-1e9,-1e9]);
    regs.push({r,h,n,side:k.split('|')[1],d:arr.map(x=>x.d).join(''),bb})}
   // 4) recortes: pectoral superior/inferior y flexor de cadera
   const cut=async(reg,poly)=>p.evaluate(([d,poly])=>{const ns='http://www.w3.org/2000/svg';const s=document.createElementNS(ns,'svg');document.body.appendChild(s);
     const rings=d.split(/(?=M)/).map(sd=>{const e=document.createElementNS(ns,'path');e.setAttribute('d',sd);s.appendChild(e);const L=e.getTotalLength(),pts=[];for(let t=0;t<=L;t+=1.5){const q=e.getPointAtLength(t);pts.push([q.x,q.y])}e.remove();return [pts]});
     s.remove();const res=polygonClipping.intersection(...rings.length>1?[polygonClipping.union(...rings)]:rings,[poly]);
     let out='',x0=1e9,y0=1e9,x1=-1e9,y1=-1e9;res.forEach(pg=>pg.forEach(ring=>{out+='M'+ring.map(([x,y])=>{x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x);y1=Math.max(y1,y);return x.toFixed(1)+','+y.toFixed(1)}).join('L')+'Z'}));return {d:out,bb:[x0,y0,x1,y1]}},[reg.d,poly]);
   const extra=[],lines=[];
   for(const reg of regs.filter(x=>x.n==='chest')){const [x0,y0,x1,y1]=reg.bb,h=y1-y0,med=reg.side==='L'?x1:x0,lat=reg.side==='L'?x0:x1;
    // línea que separa la porción clavicular (arriba) de la esternal: más baja en el esternón, sube hacia la axila
    const ym=y0+h*.40,yl=y0+h*.22;lines.push([med,ym,lat,yl]);
    const up=[[med,y0-20],[lat,y0-20],[lat,yl],[med,ym],[med,y0-20]],lo=[[med,ym],[lat,yl],[lat,y1+20],[med,y1+20],[med,ym]];
    const a=await cut(reg,up),c=await cut(reg,lo);extra.push({r:'pecs',h:'clav',n:'chest',side:reg.side,...a},{r:'pecs',h:'est',n:'chest',side:reg.side,...c})}
   regs=regs.filter(x=>x.n!=='chest').concat(extra);
   if(v==='f')for(const reg of regs.filter(x=>x.n==='quads')){const [x0,y0,x1,y1]=reg.bb,w=x1-x0,h=y1-y0,med=reg.side==='L'?x1:x0,dir=reg.side==='L'?-1:1;
    const tri=[[med,y0-10],[med+dir*w*.62,y0-10],[med,y0+h*.36],[med,y0-10]];const a=await cut(reg,tri);if(a.d)regs.push({r:'hipflex',h:'',n:'quads',side:reg.side,...a})}
   // 5) figura base 2D (silueta + músculos en gris + línea del pectoral)
   const vb=`${ox} 0 512 1024`;
   const baseP=vs.map(sb=>{const q=paths[sb.pi];const fill=q.fill==='rgb(0, 0, 0)'?C.sil:C.mus;return `<path d="${sb.d}" fill="${fill}"/>`}).join('');
   const lineP=lines.map(([a,b1,c,d])=>`<path d="M${a.toFixed(1)},${b1.toFixed(1)}L${c.toFixed(1)},${d.toFixed(1)}" stroke="${C.line}" stroke-width="2.4" stroke-linecap="round"/>`).join('');
   const svg2d=`<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb}">${baseP}${lineP}</svg>`;
   fs.writeFileSync(`${OUT}/body_${g}_${v}.svg`,svg2d);
   // figura "3D": misma forma con volumen (degradado por músculo + luz especular sobre un relieve). Se rasteriza una
   // sola vez acá; en la app es una imagen, así el filtro (caro) no corre en el celular.
   const mus3=vs.filter(sb=>paths[sb.pi].fill!=='rgb(0, 0, 0)').map(sb=>`<path d="${sb.d}"/>`).join('');
   const sil3=vs.filter(sb=>paths[sb.pi].fill==='rgb(0, 0, 0)').map(sb=>`<path d="${sb.d}"/>`).join('');
   const svg3d=`<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb}" width="1024" height="2048">
    <defs><filter id="rel" x="-5%" y="-5%" width="110%" height="110%"><feGaussianBlur in="SourceAlpha" stdDeviation="4.5" result="b"/>
     <feSpecularLighting in="b" surfaceScale="5" specularConstant=".9" specularExponent="22" lighting-color="#ffffff" result="s"><fePointLight x="${ox+150}" y="-200" z="420"/></feSpecularLighting>
     <feComposite in="s" in2="SourceAlpha" operator="in" result="s2"/>
     <feDiffuseLighting in="b" surfaceScale="4" diffuseConstant="1.05" lighting-color="#ffffff" result="dl"><feDistantLight azimuth="235" elevation="52"/></feDiffuseLighting>
     <feComposite in="SourceGraphic" in2="dl" operator="arithmetic" k1="1.05" k2="0" k3="0" k4="0" result="lit"/>
     <feComposite in="lit" in2="SourceAlpha" operator="in" result="lit2"/>
     <feComposite in="s2" in2="lit2" operator="arithmetic" k1="0" k2=".55" k3="1" k4="0"/></filter>
     <filter id="sh"><feDropShadow dx="0" dy="6" stdDeviation="8" flood-opacity=".45"/></filter></defs>
    <g filter="url(#sh)" fill="#24212b">${sil3}</g><g filter="url(#rel)" fill="#7a7686">${mus3}</g>
    <g stroke="#1f1c25" stroke-width="2.4" stroke-linecap="round">${lineP.replace(/stroke="[^"]*" /g,'')}</g></svg>`;
   await p.setContent(`<html><body style="margin:0;background:transparent">${svg3d}</body></html>`);await p.setViewportSize({width:1024,height:2048});
   await p.screenshot({path:`${OUT}/tmp3d.png`,omitBackground:true,clip:{x:0,y:0,width:1024,height:2048}});await p.setViewportSize({width:1024,height:1024});
   execSync(`python3 -c "from PIL import Image;Image.open('${OUT}/tmp3d.png').save('${OUT}/body_${g}_${v}_3d.webp',quality=82,method=6)"`);fs.unlinkSync(`${OUT}/tmp3d.png`);
   BREG[g][v]={img:`anat/body_${g}_${v}.svg`,img3d:`anat/body_${g}_${v}_3d.webp`,W:512,H:1024,ox,
    body:[body[0]-ox,body[1],body[2]-body[0],body[3]-body[1]].map(Math.round),
    r:regs.map(x=>[x.r,x.h,x.d,[x.bb[0]-ox,x.bb[1],x.bb[2]-ox,x.bb[3]].map(Math.round)])};
   console.log(g,v,'regiones',regs.length,'cuerpo',BREG[g][v].body.join(','));
  }}
 fs.writeFileSync(`${OUT}/breg.json`,JSON.stringify(BREG));console.log('breg',fs.statSync(`${OUT}/breg.json`).size);await b.close()})();
