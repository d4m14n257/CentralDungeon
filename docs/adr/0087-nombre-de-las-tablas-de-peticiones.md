---
adr: 87
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0087: Nombre de las tablas de peticiones

## Decisión

**`table_tasks` + `task_submissions`**, en lugar de `table_requirements` + `requirement_submissions`

## Por qué

La razón es una colisión concreta: `game_tables.requirements` ya existe como el texto enriquecido con los requisitos generales de la mesa (#62). Con `table_requirements` como tabla, quien lee el código se encuentra `gameTable.getRequirements()` devolviendo un texto y `TableRequirement` siendo una entidad — dos cosas distintas con el mismo nombre. Además *requirement* describe bien los requisitos de ingreso pero mal los pedidos que surgen durante el juego, que también entran acá. `task` cubre los dos casos y es más cercano a la analogía del salón de clases con la que se diseñó la feature (#63).
