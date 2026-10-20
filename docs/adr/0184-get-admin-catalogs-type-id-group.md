---
adr: 184
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0184: GET /admin/catalogs/{type}/{id}/group

## Decisión

**Una lectura del grupo completo**, canónico primero y con todos sus miembros cualquiera sea su estado. Sin paginar

## Por qué

La pantalla no puede ofrecer dos de las seis operaciones sin ella: el diálogo de fusión necesita los grupos y el de baja necesita los candidatos a sucesor (#183), y ninguna de las dos cosas sale de una fila suelta del listado paginado. No se pagina porque la profundidad es 1 (#59): un grupo está acotado por cuántos sinónimos escribe una comunidad para una cosa, y se lee entero o no se lee
