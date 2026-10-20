---
adr: 104
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0104: Cliente HTTP

## Decisión

**Un solo módulo genérico y tipado con las operaciones como métodos** (`get`, `post`, `put`, `patch`, `delete`, `upload`, `getPage`), parametrizados por el tipo de respuesta y el de cuerpo. Cada feature arma su `<dominio>Api.ts` encima

## Por qué

Rescata lo que funcionaba del frontend viejo —tener las operaciones nombradas y reutilizables— y corrige lo que no: eran **siete archivos sueltos** (`getter`, `setter`, `patcher`, `putter`, `deleter`, `setterFiles`, `getFlagCountry`) sin tipos, cada uno repitiendo el manejo de errores. Al ser genérico sobre los modelos, el tipo derivado de #20 viaja hasta la llamada: `api.post<GameTableResponse, CreateGameTableInput>(...)` no compila si el payload no coincide. El manejo del `401`, el `ProblemDetail` y el JWT viven en un solo lugar.
