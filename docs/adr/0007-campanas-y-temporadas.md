---
adr: 7
estado: Aceptada
fecha: 2026-08-29
modificada-por: [129]
---

# ADR-0007: Campañas y Temporadas

## Decisión

**Fuera de la v1, a propósito.** El **diseño** quedó cerrado en #129; lo que espera a fase 2 es la migración

## Por qué

El schema original nunca las tuvo y construirlas bloquearía el arranque del backend. v1 consolida el schema heredado y se pone en pie. ~~El impacto previsible es una FK nueva en `game_tables`~~ — **corregido en #129**: para campañas no es una FK sino una entidad intermedia, porque el reclutamiento es único. Sigue siendo aditivo y no destructivo.
