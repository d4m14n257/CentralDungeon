---
adr: 81
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0081: Borrado de valores de catálogo

## Decisión

**Borrado lógico, sin restricciones: se puede dar de baja un valor aunque esté en uso. Las lecturas simplemente saltan los que están dados de baja, según su estado**

## Por qué

Cierra **M20** y reemplaza la propuesta de prohibir el borrado. Es mejor: no se rompe ningún vínculo, `table_tags` conserva su fila, y **restaurar el valor devuelve todo a como estaba** sin ninguna migración. Un valor dado de baja deja de mostrarse y de filtrar, exactamente igual que un tag rechazado (#57). Coherente con el borrado lógico general (#25).
