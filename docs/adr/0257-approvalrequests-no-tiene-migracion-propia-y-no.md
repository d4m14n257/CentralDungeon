---
adr: 257
estado: Aceptada
fecha: 2026-09-17
---

# ADR-0257: approvalrequests no tiene migración propia y no puede tenerla

## Decisión

La tabla **ya está en `V1__baseline.sql:472`**, con sus columnas, sus tres FK y sus dos índices. F3.2 **no** agrega ninguna migración: solo mapea la entidad contra lo que ya existe. `docs/modelo-datos.md` lo dice ahora en sus tres menciones.

## Por qué

Se escribió una `V12__approval_requests.sql` que la creaba de nuevo, porque el contrato de la rebanada decía «el DDL ya está escrito en `modelo-datos.md`, transcribilo desde ahí» — y ese bloque **es el baseline ya aplicado**, no una migración pendiente. El resultado fue `Table 'approval_requests' already exists` (SQL 42S01/1050), que **tiró abajo el arranque de contexto de las diecisiete clases de IT** y dejó el backend de desarrollo caído con una fila `success = 0` en `flyway_schema_history`. **Lo que enseña no es que alguien se equivocó, sino dónde mirar**: el baseline de este proyecto ya contiene tablas de fases futuras, así que «está en `modelo-datos.md`» **no** significa «hay que crearla». Antes de escribir una migración se comprueba contra `V1`, y el síntoma —toda la suite de integración roja a la vez— parece código roto y es una tabla duplicada.
