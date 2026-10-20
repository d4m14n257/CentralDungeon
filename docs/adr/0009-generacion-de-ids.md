---
adr: 9
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0009: Generación de IDs

## Decisión

**UUID v7 generado en la aplicación**, tipo `String`

## Por qué

El schema viejo dependía del stored procedure `generate_base64_id`, que no estaba versionado en ningún repo: la app no arrancaba sin un objeto de BD que no existía en el `database.sql`. Se conserva `VARCHAR(64)` para no romper los IDs existentes. Coherente con la decisión #3: nada de lógica en la base de datos.
