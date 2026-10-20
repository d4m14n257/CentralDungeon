---
adr: 201
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0201: Alcance de la deduplicación

## Decisión

**Por dueño, no entre usuarios**

## Por qué

Lo fija el schema, no una preferencia: `uk_files_storage_key` es `UNIQUE`, así que dos filas no pueden compartir un blob, y `user_created_id` es `NOT NULL`. Compartir bytes entre personas necesitaría una tabla de blobs con conteo de referencias, que el baseline no tiene y agregar es una decisión de schema aparte. Lo que sí compra es el caso común de #75: la misma hoja de personaje adjuntada a una segunda mesa no cuesta nada
