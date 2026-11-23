# Pulso — esquema de datos (Firebase)

## Firebase Authentication (no es una tabla nuestra)
- email + contraseña. Firebase guarda la contraseña con hash scrypt y sal: nadie la puede leer, ni nosotros.
- "Olvidé mi contraseña" y verificación de email vienen incluidos.

## Firestore

usuarios/{uid}
  nombre:        "Ana Pérez"
  usuario:       "ana"            (único, reservado en usuarios_nombres)
  email:         "ana@correo.com"
  creadoEn:      timestamp
  plan:          { tipo: "gratis" | "premium", proveedor: "google_play" | "mercadopago",
                   estado: "activo" | "vencido", renovacion: timestamp, idSuscripcion: "..." }
                 (solo lo escribe el servidor; nunca datos de tarjeta)
  ajustes:       { recordatorio: { activo: true, hora: "08:00" }, tema: "oscuro", sensibilidadVoz: 1 }
  rutina:        { "1": { grupos: ["brazos"], extra: [] }, "2": { grupos: ["piernas","gluteos"], extra: [] }, ... }  (0 = domingo)

usuarios/{uid}/sesiones/{id}
  fecha, ejercicioId, grupo, zona, modo: "objetivo"|"libre", objetivo, reps, completado,
  duracionSeg, pausas, conteo: "voz"|"manual"

usuarios/{uid}/dias/{AAAA-MM-DD}
  completado: true, ejercicios: 5, minutos: 35

usuarios/{uid}/analisis/{id}
  fecha, ejercicioId, puntaje, reps, observaciones: [ "..." ]

usuarios_nombres/{usuario}
  uid
