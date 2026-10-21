---
adr: 188
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0188: Un error 409 puede traer su propio código y su mensaje redactado

## Decisión

`ConflictException` acepta un `errorCode`. El primero es **`SCHEDULE_CONFLICT`**, y su mensaje se escribe **en español** porque se muestra tal cual. En el frontend, `config/query.ts` tiene una lista corta —`EXPLAINED_ERROR_CODES`— de códigos cuyo `detail` se muestra verbatim en vez del genérico "no se pudo guardar"

## Por qué

#178 pide el motivo **en pantalla**: el choque se rechaza nombrando la mesa con la que se pisa, y un toast genérico esconde justamente lo único que la persona puede resolver (principio 2 de `diseno` §1). El nombre de la mesa no cabe en un código, así que o viaja en el `detail` o el frontend lo pide de nuevo. Que el mensaje esté en español no rompe #102: es contenido que lee una persona, el mismo precedente que la justificación "Mesa llena" de #34 y los títulos de las notificaciones. La lista es corta a propósito: el mensaje del servidor solo llega al usuario cuando el servidor se comprometió a redactarlo
