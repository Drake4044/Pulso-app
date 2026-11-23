# Kit de voces del coach (Pulso)

Genera voces naturales (neuronales) para cualquier app o proyecto, **sin internet en el celular**:
cada frase se graba una vez como un clip de audio corto y la app lo reproduce.

## Qué usa
- **Kokoro-82M** (licencia Apache-2.0, uso comercial permitido) en formato ONNX.
  Voces en español: `ef_dora` (mujer), `em_alex` (hombre), `em_santa` (hombre, grave).
- **espeak-ng** (incluido en el paquete de Piper) solo para convertir el texto en fonemas.
- **ffmpeg** para comprimir cada clip en Opus (~6 KB por frase).

## Archivos
- `tools/kvoz.py` — sintetiza un texto: `python3 tools/kvoz.py "¡Vamos, una más!" ef_dora`
- `tools/frases.py` — corta textos en oraciones y arma `{clave: texto}` (la clave es la oración en minúsculas, sin ¡¿ ni puntuación final).
- `tools/pack.py` — genera el paquete: `python3 tools/pack.py frases.json salida/ ef_dora 1.0`
  Crea un `.ogg` por frase y `index.json` (`{clave: [archivo, duración_ms]}`). Si lo cortás, retoma donde quedó.
- `tools/enum.js` — (específico de Pulso) recorre la app y lista todo lo que puede decir el coach.

## Descargas necesarias (en la carpeta `dl/`)
- Piper (trae espeak-ng): https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_linux_x86_64.tar.gz
- Modelo: https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/kokoro-v1.0.onnx
- Voces: https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/voices-v1.0.bin
- Vocabulario: https://raw.githubusercontent.com/thewh1teagle/kokoro-onnx/main/src/kokoro_onnx/config.json
Requiere Python 3 con `numpy` y `onnxruntime`.

## Usarlo en otro proyecto
1. Escribí tus frases en un JSON (lista de textos) y pasalas por `frases.py`.
2. Corré `pack.py` con la voz que quieras.
3. En la app: partí cada texto en oraciones, buscá cada oración en `index.json` y reproducí los clips en orden.
   Si alguna oración no está grabada, usá la voz del sistema como respaldo.
