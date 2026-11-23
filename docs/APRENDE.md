# Aprendé cómo está hecho Pulso

Una guía en orden para entender el código completo. Cada tema trae: la idea, dónde está en Pulso y un ejercicio para practicar.

Consejo: abrí `app/pulso.html` en el editor (VS Code) y usá Ctrl+F para buscar los nombres que aparecen acá.

---

## Nivel 1 · HTML: la estructura

**La idea.** HTML describe qué hay en la página: títulos, botones, imágenes. Cada elemento es una etiqueta: `<button>Entrenar</button>`.

**En Pulso.** Casi todo el HTML no está escrito "a mano" en el archivo: lo generan las funciones de las vistas (`V.home`, `V.exercise`…). El HTML fijo es poco: el contenedor `<div id="app">`, la barra de navegación `<nav>` y la capa de hojas `<div id="layer">`.

**Atributos que vas a ver mucho:**
- `data-act="hoy"`: indica qué acción hace un botón (ver Nivel 3).
- `hidden`: oculta un elemento.
- `aria-label`: describe el botón para lectores de pantalla (accesibilidad).

**Practicá.** Buscá `data-act="gotoday"` y seguí qué función se ejecuta.

---

## Nivel 2 · CSS: el diseño

**Variables.** Los colores están definidos una vez en `:root` (`--bg`, `--ember`…). El tema claro solo cambia esas variables:

```css
:root { --bg:#12101B; --ember:#FF5B2E; }
:root[data-theme="light"] { --bg:#FFF7F2; }
.btn.primary { background: var(--ember); }
```

**Flexbox y Grid.** Para ubicar cosas en fila o en grilla. La tira de días del inicio es un `grid` horizontal con `scroll-snap` (al soltar, se acomoda en una semana).

**Animaciones.** `@keyframes` + `animation`. Regla de oro para que sean fluidas: animar solo `transform` (mover, escalar, girar) y `opacity`.

**Practicá.** Buscá `@keyframes mblink` (el titileo del músculo) y cambiá `1.4s` por `0.8s`.

---

## Nivel 3 · JavaScript: la lógica

### 3.1 Lo básico que usa Pulso
- `const` / `let` para variables.
- **Funciones flecha:** `const sumar = (a, b) => a + b;`
- **Objetos:** `const S = { view: 'home', sel: '2026-11-23' };`
- **Template literals** (texto con `${}`), la base de todas las vistas:

```js
const tarjeta = e => `<button data-act="ex" data-id="${e.id}">${e.name}</button>`;
```

- **Arrays:** `map`, `filter`, `find`, `join`. Ejemplo real: `EX.filter(e => e.g === 'brazos').map(tarjeta).join('')`.

### 3.2 Estado → render
Toda la app sigue este patrón:

```js
S.view = 'exercise'; S.ex = 'curl';   // 1. cambio el estado
render();                             // 2. redibujo
```

Leé `function render()` y `function go(v)`.

### 3.3 Delegación de eventos
En vez de un `onclick` en cada botón, hay un solo listener:

```js
document.addEventListener('click', e => {
  const a = e.target.closest('[data-act]'); if (!a) return;
  switch (a.dataset.act) {
    case 'gotoday': goToday(); break;
    case 'b3d': toggle3D(); break;
  }
});
```

### 3.4 Asincronía
`async`/`await` para esperar cosas que tardan (Firebase, cámara, audio):

```js
async function saveProf() { …; await b.commit(); toast('Datos guardados'); }
```

### 3.5 Guardar datos
`localStorage` guarda texto en el celular. `store` (busca `const store=`) lo envuelve con JSON y manejo de errores.

**Practicá.** Agregá un `case 'hola': toast('¡Hola!'); break;` y un botón `<button data-act="hola">` en alguna vista.

---

## Nivel 4 · SVG: dibujos con código

SVG describe dibujos con formas: `<circle>`, `<line>`, `<path d="M10 10 L50 50">`.

**En Pulso:**
- **Íconos** (`const I=`): cada uno es un pequeño SVG.
- **Figuras animadas:** líneas cuyos extremos se calculan con trigonometría (`function geo`): `x = sin(ángulo) * largo`, `y = cos(ángulo) * largo`.
- **Esquema de músculos** (`function bodySVG`): cada músculo es un `<path>`. El zoom cambia el `viewBox` (la "ventana" del dibujo que se ve).

**Practicá.** En la consola del navegador: `document.querySelector('.bm.ov').setAttribute('viewBox','100 150 200 250')` y mirá qué pasa.

---

## Nivel 5 · Android: la app nativa

- **Activity** (`MainActivity.java`): una pantalla de Android. Pulso tiene una sola, con un **WebView** adentro.
- **WebView:** un navegador sin barra que muestra `index.html`.
- **Puente** (`Bridge.java`): `addJavascriptInterface(new Bridge(this), "PulsoNative")` hace que JavaScript pueda llamar a métodos de Java marcados con `@JavascriptInterface`.
- **Permisos:** cámara, micrófono y notificaciones se piden en tiempo de ejecución.
- **AlarmManager + BroadcastReceiver** (`Reminder.java`): recordatorios que suenan con la app cerrada.
- **Gradle** (`android/app/build.gradle`): compila y empaqueta el APK. `minifyEnabled` achica el código.

**Practicá.** Agregá en `Bridge.java` un método `@JavascriptInterface public String version() { return "1.27"; }` y llamalo desde JS con `PulsoNative.version()`.

---

## Nivel 6 · Firebase: cuentas y nube

- **Authentication:** crea y verifica usuarios (las contraseñas nunca pasan por nuestro código).
- **Firestore:** base de datos de documentos. Estructura: `usuarios/{uid}/sesiones/{id}`.
- **Reglas de seguridad** (`firebase/firestore.rules`): `request.auth.uid == uid` significa "solo el dueño".

Leé `const Cloud=` en el HTML: inicio de sesión, lectura del perfil, guardado de sesiones.

---

## Nivel 7 · Visión por computadora (cámara)

- MediaPipe da puntos del cuerpo (hombro, codo, muñeca…).
- **Ángulo de una articulación** con tres puntos A-B-C: vectores BA y BC, y `ángulo = acos(BA·BC / (|BA|·|BC|))`. Busca `const ang3=`.
- **Filtro One Euro** (`function oneEuro`): suaviza el temblor.
- **Máquina de estados** (`function repEngine`): cuenta una repetición al completar el ciclo arriba → abajo → arriba.

---

## Nivel 8 · Herramientas (Node.js, Python, Playwright)

No van dentro de la app; ayudan a crearla:
- `tools/cuerpo/gen2.js` (Node + Playwright): convierte el componente de anatomía en las figuras y regiones.
- `tools/intro/` (Playwright + ffmpeg): renderiza la intro cuadro por cuadro a 30 fps y la convierte en video.
- `tools/voz/` (Python + Kokoro + ffmpeg): graba las frases del coach.
- `content/data.py` (Python): el contenido investigado (ejercicios, estiramientos, anatomía).

---

## Glosario rápido

| Término | Significado |
|---|---|
| DOM | El árbol de elementos de la página que JavaScript puede leer y cambiar |
| Render | Dibujar la pantalla a partir del estado |
| Delegación de eventos | Un solo listener que atiende los clicks de muchos botones |
| viewBox | La parte del dibujo SVG que se muestra |
| WebView | Navegador embebido dentro de una app nativa |
| API | Conjunto de funciones que un sistema ofrece para usarlo |
| Máquina de estados | Lógica que pasa de un estado a otro según reglas |
| SDK | Librería oficial para usar un servicio (por ejemplo, Firebase) |

## Orden sugerido para leer el código

1. Comentario inicial de `app/pulso.html`.
2. `const S=`, `const store=`, `function render`, `function go`.
3. `V.home` y `function initHome` (una pantalla completa).
4. Sección de eventos (buscá `eventos ----------`).
5. `function anatomy`, `function bodySVG`, `function muscleZoom` (el esquema de músculos).
6. `MainActivity.java` y `Bridge.java`.
7. `const Cloud=` y `firebase/firestore.rules`.
8. `function repEngine` (cámara), cuando lo anterior esté claro.
