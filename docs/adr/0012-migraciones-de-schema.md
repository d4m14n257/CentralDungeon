---
adr: 12
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0012: Migraciones de schema

## Decisión

**Flyway**, con `ddl-auto: validate`

## Por qué

El proyecto viejo tenía dos `database.sql` en dos repos, ambos desactualizados y uno con errores de sintaxis que impedían ejecutarlo. Con Flyway el schema tiene una única historia versionada y el arranque falla si la entidad no coincide con la tabla.
