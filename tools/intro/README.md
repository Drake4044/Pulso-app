# Intro animada

`render.html` dibuja la animación del logo controlada por tiempo (`setT(ms)`), y `render.js` (Playwright) toma una captura por cuadro a 30 fps. Después ffmpeg los une en `intro.mp4` (H.264, ~100 KB):

```bash
node render.js            # genera frames/
ffmpeg -framerate 30 -i frames/%04d.png -c:v libx264 -pix_fmt yuv420p -crf 26 intro.mp4
```

Se usa video porque animar el logo en vivo con filtros de brillo trababa celulares de gama baja.
