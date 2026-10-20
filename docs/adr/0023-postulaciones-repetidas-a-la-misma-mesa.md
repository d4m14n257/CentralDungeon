---
adr: 23
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0023: Postulaciones repetidas a la misma mesa

## Decisión

**Un usuario puede enviar N solicitudes a la misma mesa. Se quita el `UNIQUE (game_table_id, user_id)`** que la propuesta v1 había agregado

## Por qué

Es el flujo real: el jugador se postula (`Candidate`), el master rechaza con justificación (`Rejected` + fila en `registration_rejections`) y el jugador puede volver a intentarlo más adelante. El `UNIQUE` impedía el reintento y además destruía el historial de rechazos, que es justamente lo que `registration_rejections` existe para guardar. Resuelve **M1** en la dirección "historial de intentos", no "fila única mutable".
