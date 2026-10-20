---
adr: 34
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0034: Qué pasa al llenarse el cupo

## Decisión

**Al aceptar al jugador que completa `max_players`, todas las solicitudes `Candidate` restantes se rechazan automáticamente**, con justificación por defecto (`Mesa llena`) y su notificación al jugador

## Por qué

Resuelve el hueco que dejaba #28: como un `Candidate` no reserva cupo, sin esta regla quedarían solicitudes colgadas para siempre esperando una respuesta que nunca llega. Rechazarlas en la misma transacción en que se llena la mesa es explícito y deja al jugador enterado en el momento, en vez de que lo descubra solo. La justificación por defecto evita que el master tenga que escribir N veces el mismo motivo, y evita malentendidos: el rechazo es por cupo, no por la persona.
