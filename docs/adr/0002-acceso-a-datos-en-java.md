---
adr: 2
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0002: Acceso a datos en Java

## Decisión

**Spring Data JPA**, no `JdbcTemplate`

## Por qué

El `JdbcTemplate` a mano fue la causa directa de la mayoría de los bugs del intento previo. Con entidades reales y `JpaRepository` esos errores no existen, y las entidades nuevas no necesitan un `RowMapper` escrito a mano cada una.
