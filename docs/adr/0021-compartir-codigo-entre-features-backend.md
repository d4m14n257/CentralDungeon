---
adr: 21
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0021: Compartir código entre features (backend)

## Decisión

**Interfaz solo con más de una implementación real; clase abstracta genérica solo cuando la forma se repite idéntica en 3+ features y ya se vio repetida**

## Por qué

Los dos extremos duelen: duplicar CRUD idéntico entre `systems`/`tags`/`platforms` multiplica el mismo bug, y abstraer services que solo se parecían acopla dominios que divergen en el primer requerimiento. El criterio operativo es abstraer lo que es igual *por definición*, no lo que hoy es parecido por casualidad. La excepción absoluta es la seguridad: `@PreAuthorize` nunca se hereda, para que el permiso sea visible en el archivo que declara el endpoint. Detalle en skill `arquitectura` §2.4.
