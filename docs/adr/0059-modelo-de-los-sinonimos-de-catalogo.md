---
adr: 59
estado: Aceptada
fecha: 2026-08-29
modificada-por: [276]
---

# ADR-0059: Modelo de los sinónimos de catálogo

## Decisión

**`canonical_id` plano: cada alias apunta directo al canónico, nunca a otro alias. Profundidad 1 siempre.** Se abandona el `parent_id` como árbol de profundidad libre

## Por qué

Cierra **M19**. Bajo equivalencia pura y simétrica (#54) el camino del árbol nunca se usa: el CTE heredado sube nodo por nodo y descarta todo salvo la raíz, así que la profundidad se calcula y se tira en cada búsqueda. Con `canonical_id` ese resultado ya está guardado. Las cinco operaciones del admin pasan a ser uno o dos `UPDATE`, la búsqueda deja de necesitar recursión, y **"dar de baja el canónico" y "cambiar el canónico" se vuelven la misma operación** — que es lo que hacía difícil la baja. El nuevo canónico lo elige el admin (#55), no un "primer hijo" arbitrario. Los ciclos (`A → B → A`), hoy posibles y capaces de colgar el CTE, se vuelven imposibles con un `CHECK` de un nivel. Migrar es aplanar: cada descendiente pasa a apuntar a su raíz. Si algún día hace falta taxonomía real (`D&D 5e` dentro de `D&D`), es una relación distinta y va en **otra** columna.
