---
adr: 55
estado: Aceptada
fecha: 2026-08-29
modificada-por: [275, 276]
---

# ADR-0055: Alta y clasificación de catálogos

## Decisión

**Masters y admins pueden proponer un valor nuevo; solo un admin lo acepta y lo clasifica** — decide si es canónico nuevo o alias de un grupo existente

## Por qué

Cierra **M20.1** en su parte principal. Es otra instancia del patrón #42. La clasificación tiene que ser del admin: si cada master pudiera decidir a qué grupo pertenece su tag, los grupos se fragmentan y el propósito de la equivalencia se pierde. Explica también por qué la baja se siente difícil — es el admin el que administra las uniones, así que las operaciones de fusionar y separar grupos son parte del producto, no un caso borde.
