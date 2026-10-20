---
adr: 189
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0189: PUT /api/v1/game-tables/{id} reemplaza la mesa entera, no parchea

## Decisión

La edición del master es un **reemplazo completo**: un campo ausente vacía, no deja como estaba. Solo en `Preparation` y `ChangesRequested`; después la mesa ya no es suya para reescribirla. `UpdateGameTableRequest` es un `record` propio y no un alias de `CreateGameTableRequest`

## Por qué

El wizard muestra la mesa entera y la manda entera, así que "ausente" solo puede significar vacío — bajo la lectura de parche no habría forma de borrar la última franja de la agenda ni el último tag. Los dos records se mantienen separados porque son mensajes distintos: el día que crear acepte algo que editar no debe aceptar, el tipo ya está ahí para expresarlo, y un alias habría propagado el campo en silencio
