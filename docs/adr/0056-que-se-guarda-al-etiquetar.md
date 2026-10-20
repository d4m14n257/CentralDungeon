---
adr: 56
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0056: Qué se guarda al etiquetar

## Decisión

**Se guarda el alias tal como lo eligió el master.** La búsqueda resuelve el grupo: buscar por un término encuentra las mesas etiquetadas con ese término, con su canónico y con sus equivalentes

## Por qué

Cierra **M20.2** y **M20.3**. Conserva la forma en que el master escribió el tag, que es información real. Y como la búsqueda resuelve por grupo y no por el valor literal, **fusionar sinónimos después no obliga a reescribir `table_tags`**: la mesa etiquetada con "DANDD" se vuelve encontrable por "D&D" en el momento en que un admin une los dos, sin migrar una sola fila.
