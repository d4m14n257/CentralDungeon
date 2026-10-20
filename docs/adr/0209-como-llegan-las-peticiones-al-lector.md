---
adr: 209
estado: Aceptada
fecha: 2026-09-04
---

# ADR-0209: Cómo llegan las peticiones al lector

## Decisión

**Endpoint propio, `GET /game-tables/{id}/tasks/applicable`**, y no un campo del detalle de la mesa como las sesiones (F1.3) y los archivos (F1.4)

## Por qué

Esas dos son iguales para todo el que puede ver la mesa; esta lista **depende de quién pregunta**: la audiencia `Players` solo la ven los jugadores y una `Single` solo su destinatario. Meter una respuesta con forma de actor dentro de un DTO que además se sirve sin actor —`toDetail()` se llama sin él en cada transición— es exactamente cómo se confunden las dos y alguien termina viendo lo que no le toca (#121). Aparte deja `features/tasks` autocontenida y le da su propia entrada de caché: dos personas mirando la misma mesa no pueden compartirla
