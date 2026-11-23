# Arquitectura de Pulso

Este documento explica cómo funciona la app por dentro: qué pasa al abrirla, cómo se dibuja cada pantalla y cómo trabajan juntas la parte web y la parte Android.

## 1. Dos capas: web y Android

```
┌───────────────────────────────────────────────┐
│ Android (Java)                                │
│  MainActivity ── WebView ── assets/index.html │  ← toda la interfaz
│        │                                      │
│     Bridge.java  ← window.PulsoNative          │  ← lo que el navegador no puede hacer
│        │                                      │
│     Reminder.java (notificaciones)            │
└───────────────────────────────────────────────┘
```

- **La interfaz es web.** `app/pulso.html` se copia como `assets/index.html` dentro del APK. El WebView lo abre desde el teléfono (`file:///android_asset/index.html`), así que abre sin internet.
- **Java solo para lo nativo.** `Bridge.java` expone métodos con `@JavascriptInterface`. Desde JavaScript se llaman así:

```js
if (window.PulsoNative) PulsoNative.schedule(id, horaEnMs, 'Hoy te toca', 'Brazos y hombros');
```

- **Respuestas asincrónicas.** Cuando Java termina algo que tarda (elegir una foto, iniciar sesión con Google), llama a una función global de JavaScript: `PulsoPhotoReady()`, `PulsoGoogle(token, error)`, `PulsoSpoke()`.

## 2. Qué pasa al abrir la app

1. Android muestra un fondo oscuro (tema `Pulso`) para que no haya un destello blanco.
2. El WebView carga `index.html`. Se ve la **intro**: un video corto (`intro.mp4`) prerenderizado con `tools/intro/`. Se usa video porque animar el logo en vivo trababa celulares lentos.
3. Aparece el logo con la **barra de carga**. La barra sigue el progreso **real**: `loadStep()` marca hitos (scripts listos 12 %, Firebase 30 %, sesión 42 %, perfil 58 %, sesiones 74 %, días 88 %, listo 100 %). `barRun()` la anima para que nunca se detenga y frene en 97 % hasta que todo cargó.
4. `render()` dibuja la primera pantalla (inicio o ingreso).

## 3. Cómo se dibuja una pantalla

```js
const V = {};                       // una función por pantalla
V.home = () => `<main>…${daySlide(...)}…</main>`;

function render() {
  app.innerHTML = V[S.view]();      // 1. HTML de la vista actual
  mountFigs(app);                   // 2. figuras animadas
  if (S.view === 'home') initHome();// 3. inicialización propia de la pantalla
}

function go(v) { S.view = v; render(); }
```

- **`S`** es el estado: vista actual, día elegido, ejercicio abierto, etc.
- **`store`** guarda en `localStorage` con el prefijo `pulso.` y, con cuenta, sincroniza con Firestore.
- **Eventos por delegación:** un único `document.addEventListener('click', …)` busca el elemento con `data-act` más cercano y ejecuta el `case` correspondiente. No hay que volver a conectar listeners cada vez que se redibuja.

Cuando algo tiene que ser fluido (deslizar días, zoom al músculo, barra de carga) no se llama a `render()`: se actualiza solo el elemento que cambia.

## 4. Datos

| Constante | Qué contiene |
|---|---|
| `G` | Grupos musculares (brazos, piernas…), color e ícono |
| `EX` | Ejercicios: textos, músculos, poses A/B para la figura animada |
| `MZ` / `MZW` | Músculos que trabaja cada ejercicio y con qué intensidad (0–1) |
| `MUS` | Ficha de cada músculo: forma, porciones, función, mejores ejercicios |
| `STR` | Estiramientos guiados |
| `SRC` | Fuentes científicas citadas |
| `BREGS` | Figura del cuerpo y regiones de cada músculo |

El contenido investigado vive en `content/data.py` y se inserta en el HTML como JSON.

## 5. Esquema de músculos

Generado con `tools/cuerpo/gen2.js` a partir del componente *vue-human-muscle-anatomy* (MIT):

1. Se renderiza el componente en un navegador sin ventana (Playwright) y se leen sus `<path>`.
2. Se detecta qué path es cada músculo pintando un grupo por vez y viendo cuál cambia de color.
3. Se "hornean" las transformaciones en las coordenadas y se separa cada músculo por vista (frente/espalda) y lado (izquierdo/derecho).
4. El pectoral se divide en porción superior e inferior recortándolo con `polygon-clipping`.
5. Se exportan: figura base 2D (`body_<g>_<v>.svg`), figura 3D (`*_3d.webp`, con luz y relieve hechos con filtros SVG y rasterizados una sola vez) y las regiones (`BREGS`).

En la app, `bodySVG()` arma **dos capas**: la figura base (una imagen que el navegador dibuja una vez) y encima un SVG solo con los músculos que trabaja el ejercicio. El color depende de la intensidad (`--w` en CSS). El titileo anima solo la opacidad de la capa de arriba, que es lo más barato de animar.

**Zoom:** se cambia el `viewBox` de las dos capas a la vez (`animVB`). La caja tiene siempre el mismo tamaño, así que ir y volver no reacomoda nada. Si se toca otro músculo durante el zoom, la animación parte desde donde está.

**Figura según el usuario:** `bodyG()` usa el campo "Figura del esquema" del perfil; si no está elegido, lo estima por el nombre.

## 6. Figuras animadas de los ejercicios

Cada ejercicio guarda dos poses (A y B) como ángulos de torso, brazos y piernas. `geo()` calcula la posición de cada articulación (cinemática directa: sumar segmentos con seno y coseno) y `drawFig()` interpola entre A y B con una curva de aceleración. Un `IntersectionObserver` pausa las figuras que no están en pantalla.

## 7. Cámara y conteo de repeticiones

- **MediaPipe Pose** devuelve 33 puntos del cuerpo, también en 3D (`worldLandmarks`).
- `oneEuro` / `lmSmoother` suavizan el temblor sin agregar retraso.
- `measure2` calcula ángulos 3D (por ejemplo, rodilla = ángulo entre cadera-rodilla-tobillo con producto punto).
- `repEngine` es una **máquina de estados** por ejercicio: arriba → bajando → abajo → subiendo → arriba = 1 repetición. Las reglas (`RS`) indican qué ángulo mirar, los umbrales y qué errores avisar.
- Si el equipo es lento, `poseTiming` cambia al modelo "lite".
- El video nunca sale del celular.

## 8. Voz del coach

- Las 1.556 frases posibles se enumeraron recorriendo la app (`tools/voz/enum.js`) y se grabaron con Kokoro (`tools/voz/pack.py`), en voz de mujer y de hombre (~6 KB por frase en Opus).
- `VP` parte cada texto en oraciones, busca cada una en el índice y reproduce los clips en orden. Si una oración no está grabada, usa la voz del sistema (`Bridge.say`).

## 9. Nube

- **Firebase Authentication:** email/contraseña y Google (en Android, con Credential Manager vía `Bridge.googleSignIn`).
- **Firestore:** `usuarios/{uid}` con perfil, rutina y ajustes; subcolecciones `sesiones`, `dias` y `analisis`. Esquema en `firebase/esquema.md`.
- **Reglas:** cada usuario lee y escribe solo lo suyo; el plan Premium solo lo cambia el servidor.
- La configuración web de Firebase que está en el HTML no es secreta: lo que protege los datos son las reglas.

## 10. Rendimiento en celulares modestos

- Animaciones solo con `transform` y `opacity`.
- Modo liviano automático (`html.lite`) si el equipo tiene poca memoria o se detectan cuadros lentos.
- Figuras fuera de pantalla en pausa.
- Firebase y MediaPipe se cargan solo cuando hacen falta.
- La intro es un video, y la figura 3D es una imagen prerenderizada: los efectos caros se calculan una vez en la computadora, no en el celular.
