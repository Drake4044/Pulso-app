// Enumera todas las frases que puede decir el coach de Pulso (corre dentro de la app con Playwright)
const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const p=await b.newPage();
await p.addInitScript(()=>{localStorage.setItem('pulso.logged','true');localStorage.setItem('pulso.dv','2')});
await p.goto('file:///home/claude/pulso/build/assets/index.html');await p.waitForTimeout(1200);
const out=await p.evaluate(()=>{const T=new Set(),add=x=>{if(x&&typeof x==='string')T.add(x.trim())};const N=[...Array(60)].map((_,i)=>i+1);
 const exs=EX.map(e=>e.name.toLowerCase());
 for(const st of Object.values(LINES))for(const arr of Object.values(st))for(const t of arr){let vs=[t];
  if(t.includes('{ex}'))vs=vs.flatMap(x=>exs.map(e=>x.split('{ex}').join(e)));if(t.includes('{n}'))vs=vs.flatMap(x=>N.map(n=>x.split('{n}').join(n)));
  vs=vs.map(x=>x.split('{w}').join(''));vs.forEach(add)}
 const lits=src=>[...src.matchAll(/'([^'\\]{4,})'/g)].map(m=>m[1]);
 Object.values(RS).forEach(sp=>(sp.rules||[]).concat(sp.live||[]).forEach(f=>{const l=lits(f.toString());l.forEach(x=>{add(x);if(x.length<30){add(x+'.');add(`Otra vez: ${x.toLowerCase()}.`);add(`Para la próxima, enfocate en esto: ${x.toLowerCase()}.`)}})}));
 Object.values(AZ).forEach(sp=>(sp.checks||[]).forEach(f=>lits(f.toString()).filter(x=>!/^(warn|ok|info)$/.test(x)).forEach(add)));
 lits(framing.toString()).filter(x=>/ /.test(x)).forEach(add);lits(analyzeFrames.toString()).filter(x=>/ /.test(x)&&!/^(warn|ok|info)$/.test(x)).forEach(add);
 Object.keys(EXI).concat(['x']).forEach(id=>add(depthWhy(id,{})));
 ['Recorrido completo','Más lento'].forEach(x=>{add(x+'.');add(`Otra vez: ${x.toLowerCase()}.`);add(`Para la próxima, enfocate en esto: ${x.toLowerCase()}.`)});
 add('Fue muy rápida: bajá en dos segundos y subí controlando.');
 ['No cuenta.','Esa no la cuento.','Casi, pero esa no vale.'].forEach(add);
 N.forEach(n=>[`${n}.`,`${n}, bien.`,`${n}, muy bien.`,`¡${n}!`,`${n}, eso.`,`${n}. ¡La última, con todo!`,`${n}. Queda una sola, ¡dale!`,`${n}. Quedan tres.`,`${n}. Así, técnica limpia.`,`${n}. No aflojes.`,`¡${n}! Vas muy bien.`,`¡${n}! Así se hace.`,`¡${n}! Qué buena técnica.`,
  `¡${n}! Objetivo cumplido. Excelente trabajo.`,`¡Y ${n}! Lo lograste. Muy bien hecho.`,`Terminaste. Hiciste ${n} repeticiones bien hechas.`,`En total intentaste ${n}.`,`¡${n}! Seguí así.`].forEach(add));
 [...Array(150)].forEach((_,i)=>add(`Hoy hiciste ${i+1} repeticiones en total.`));
 ['Terminaste. Hiciste una repetición bien hecha.','Terminaste. Esta vez no conté repeticiones completas, pero ya diste el primer paso.','Técnica impecable, te felicito.',
  'Perfecto, te veo entero. Ponete en posición y arrancá cuando quieras.','Listo, te veo bien. Empezá cuando estés listo.','Bien, esa es la posición. Vamos.','Ahí está. Arrancamos.','Perfecto. Empezá.',
  '¡Hiciste más repeticiones que la última vez, muy bien! Siempre que se quiere, se puede.','Igualaste tu marca de la última vez. La próxima la superás.','Hoy hiciste menos que la última vez. Cada día suma: la próxima vas por más.',
  'Hola, soy tu coach. Vamos a entrenar juntos.','Acá estoy. Hoy lo damos todo.','Hola, soy tu coach.','Vamos con sentadillas. Pecho arriba, peso en los talones. Bajá lento… y subí con fuerza. ¡Eso!',
  '¡Lo lograste!','¡La última!','¡Últimas dos!','¡Vas por la mitad!'].forEach(add);
 STR.forEach(x=>{add(`Arrancamos. Ahora: ${x.name.toLowerCase()}. Mantené ${x.hold} segundos.`);add(`Ahora: ${x.name.toLowerCase()}. Mantené ${x.hold} segundos.`)});['Cambiá de lado.','Respirá lento, aflojá.','¡Listo! Elongación completa. Tus músculos te lo agradecen.'].forEach(add);
 EX.forEach(e=>{add(`Vamos a aprender ${e.name}.`);(e.steps||[]).forEach(add);if(e.tip)add(`Clave: ${e.tip}`)});MOTTOS.forEach(add);
 return [...T]});
require('fs').writeFileSync(process.argv[2],JSON.stringify(out,null,1));console.log(out.length);await b.close()})();
