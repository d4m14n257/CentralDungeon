---
adr: 171
estado: Aceptada
fecha: 2026-09-02
---

# ADR-0171: El explorador ordena por fecha, la mesa nueva primero

## Decisión

`GET /api/v1/game-tables` toma `@PageableDefault(sort = "createdAt", direction = DESC)`. El default vive en el controller y no en el JPQL, así que un `?sort=` explícito lo sigue pisando

## Por qué

Cierra el gap que #163 dejó anotado y que volvió a aparecer solo: sin orden, MySQL devuelve las filas en orden físico —las más viejas primero, porque los ids son UUIDv7 ordenables por tiempo— y una mesa recién publicada podía no entrar en la primera página. Se descubrió otra vez porque las corridas de Playwright acumularon mesas hasta pasarse de página, pero no es un problema de los tests: en producción la mesa nueva es justo la que hay que ver, y hoy quedaba al final
