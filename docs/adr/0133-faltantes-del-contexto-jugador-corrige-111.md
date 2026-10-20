---
adr: 133
estado: Aceptada
fecha: 2026-08-29
modifica: [111]
---

# ADR-0133: Faltantes del contexto Jugador — corrige #111

## Decisión

**Tres cosas.** (a) `/my/tables` muestra **solo mesas vivas** y las terminadas y canceladas van a **`/my/history`**, con ruta propia. (b) El **feedback del sistema es una acción global del layout**, no una ruta: no tiene contenido que mostrar. (c) **La zona horaria sale del navegador**, no del perfil — #111 decía lo contrario y contradecía a #22

## Por qué

Salió de revisar el contexto Jugador contra lo que la aplicación necesita de verdad. (a) Se eligió ruta propia sobre un filtro dentro de `/my/tables` asumiendo el riesgo explícitamente: es el patrón de listados casi iguales que el sitemap acaba de eliminar del legacy (§6). Se acepta porque el historial no muestra las mismas columnas —lleva asistencia final y si dejaste comentario— y porque separa lo accionable de lo que solo se consulta. Si termina siendo la misma tabla con otro `WHERE`, se fusiona. (b) El límite de una cada 24 h (#94) lo impone el servidor, así que **la interfaz no lo predice**: ofrece el botón siempre y traduce el `429` a un mensaje. Predecirlo obligaría a exponer el estado de la cuota, que es justo lo que el token opaco de #94 evita. (c) #111 describía un comportamiento que el schema no puede sostener: #22 había eliminado `users.timezone` y no hay dónde guardar una zona de perfil. Gana #22 por ser la que el modelo respalda; el parámetro de zona en `lib/date.ts` se conserva por testeabilidad, no porque venga de la base.
