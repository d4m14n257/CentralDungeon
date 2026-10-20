---
adr: 8
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0008: Tables.tabletype (Public / First class)

## Decisión

**Deja de ser enum, pasa a ser la tabla catálogo `table_types`**

## Por qué

Era la forma en que los admins separaban mesas por tema, y dos valores fijos se quedan cortos. Como catálogo se agregan tipos desde la aplicación sin `ALTER TABLE`. Es FK simple, no tabla puente: una mesa tiene un tipo (a diferencia de tags/sistemas/plataformas, que sí son multivaluados).
