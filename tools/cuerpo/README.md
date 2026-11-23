# Generador del esquema muscular

Convierte el componente [vue-human-muscle-anatomy](https://github.com/LucaWahlen/vue-human-muscle-anatomy) (MIT, © 2025 Luca Wahlen) en archivos que la app usa sin Vue:

- `body_<g>_<v>.svg`: figura base 2D (g = `m` hombre / `f` mujer, v = `f` frente / `b` espalda)
- `body_<g>_<v>_3d.webp`: la misma figura con volumen (luz y relieve con filtros SVG, rasterizada acá una sola vez)
- `breg.json`: regiones de cada músculo; se pega en `app/pulso.html` como `BREGS`

## Uso

```bash
npm i playwright polygon-clipping vue@3.4.38   # dependencias del generador (no de la app)
cp node_modules/vue/dist/vue.global.prod.js .
mkdir -p pc/package/dist && cp node_modules/polygon-clipping/dist/polygon-clipping.umd.min.js pc/package/dist/
node gen2.js out
cp out/body_* ../../android/app/src/main/assets/anat/
```

`vue_hma.js` es el componente compilado con una sola modificación: usa el `Vue` global en vez de `import`.
