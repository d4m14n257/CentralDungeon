---
adr: 17
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0017: Karma y comentarios

## Decisión

**Se retoma el diseño que ya estaba en el schema**

## Por qué

`comments.comment_type`, `comments.karma_impact` y `users.karma` son el diseño de datos más elaborado que existe para una feature que nunca se construyó. No hay una alternativa evidentemente mejor, y rediseñarlo desde cero sería trabajo sin beneficio claro.
