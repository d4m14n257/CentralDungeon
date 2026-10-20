---
adr: 26
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0026: Duración de la mesa

## Decisión

**Se agrega a `game_tables` el número de sesiones previstas**

## Por qué

Faltaba. Con `start_date` + agenda semanal (`table_schedules`) + `duration` se sabe cuándo empieza y cuánto dura cada sesión, pero no cuántas hay ni cuándo termina la mesa. Es además lo que hace calculable el "quedan N sesiones pendientes" que exige el estado `Pause`. Cubre parcialmente **M4**.
