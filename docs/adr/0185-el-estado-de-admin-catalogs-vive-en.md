---
adr: 185
estado: Aceptada
fecha: 2026-09-03
modificada-por: [275]
---

# ADR-0185: El estado de /admin/catalogs vive en la URL

## Decisión

**Catálogo activo, búsqueda y página son query params**, no `useState`. Las tres pestañas no son rutas hijas: el sitemap tiene una sola ruta `/admin/catalogs`

## Por qué

Una fila de un catálogo es algo que un admin le pasa a otro, y un estado que solo vive en memoria no se puede enlazar. Rutas hijas darían URLs (`/admin/catalogs/tags`) que el sitemap de `frontend-diseno.md` §2 no tiene; los query params dan lo mismo que la regla de las pestañas busca —URL propia, compartible, botón de atrás— sin inventar rutas
