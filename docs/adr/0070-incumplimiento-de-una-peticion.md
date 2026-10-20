---
adr: 70
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0070: Incumplimiento de una petición

## Decisión

**No bloquea ni expulsa. Se le avisa al jugador de su incumplimiento y queda visible para el master; cualquier acción posterior —intervención del master o de un admin, expulsión, veto— es siempre manual**

## Por qué

Responde la pregunta 2 del diseño de #63. Evita agregar un estado nuevo a #28 y deja la decisión donde corresponde: una entrega faltante puede tener mil motivos, y automatizar una sanción sobre eso genera más problemas de los que resuelve. El sistema informa; las personas deciden.
