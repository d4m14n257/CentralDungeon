---
adr: 22
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0022: Zona horaria

## Decisión

**Se elimina la columna `timezone` de `users` y de `game_tables`. Todo se persiste en UTC (offset 0); la conversión a hora local la hace el frontend con la zona del navegador**

## Por qué

La columna existía porque en su momento no se manejaban bien los timestamps y se resolvió pidiéndole al usuario que declarara su zona — la comunidad está repartida por toda Latinoamérica y algo de Europa. Guardar en UTC y convertir en el cliente elimina el problema de raíz, no obliga al usuario a configurar nada y sigue siendo correcto si viaja o se muda. Ya era la convención del contrato de la API (skill `arquitectura` §2.5: ISO-8601 UTC, conversión en el frontend); ahora también lo es del modelo. Aplica a **todo** timestamp: creación, borrado, agenda y horarios de sesión.
