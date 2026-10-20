---
adr: 290
estado: Aceptada
fecha: 2026-10-20
---

# ADR-0290: Las reglas duras se cargan por ruta desde `.claude/rules/`, y las rules de ECC se versionan

## Decisión

Las reglas duras dejan de vivir completas en `CLAUDE.md`. Cada una pasa a una rule de `.claude/rules/` con `paths:` en el frontmatter, que Claude Code carga solo al tocar un archivo que coincide:

| Rule | Se carga en | Reglas duras |
|---|---|---|
| `backend.md` | `backend/**` | 1, 2, 3, 4, 7, 10, 14 y la mitad del backend de 18 |
| `frontend.md` | `frontend/**` | 6, 7, 11, 16, 18 |
| `migraciones.md` | `db/migration/**` | 8, 9, 10 |
| `documentacion-codigo.md` | código de `backend/src`, `frontend/src` y `frontend/e2e` | 19 (la antigua sección «Documentación del código» de `CLAUDE.md`) |
| `tokens.md` | `design/**`, `frontend/src/styles/**` | lo de `design/build.py` |
| `stack.md` | `pom.xml`, `package.json`, `docs/arquitectura.md` | 15 |

**Los números de las reglas duras se conservan**: el código y los docs citan «regla dura n» unas ciento cincuenta veces. `CLAUDE.md` queda con un índice de las 19, una línea cada una y dónde vive, y desarrolla completas solo las del dominio que valen al planificar cualquier cosa, toque el archivo que toque: 5 (roles), 8 (nada de lógica en la base), 12 (`legacy/` de solo lectura), 13 (sin Campañas ni Temporadas) y 17 (el rol no es la pertenencia).

Las rules de `.claude/rules/ecc/` —qué parte de ECC choca con el proyecto— **se versionan**: salen de `.git/info/exclude`.

Las skills no cambian de papel: siguen siendo el detalle y el porqué. Una rule es el resumen que se impone al tocar un archivo y remite a su sección.

## Por qué

`CLAUDE.md` se carga entero en cada sesión, y las diecinueve reglas con su desarrollo —más la sección de documentación del código— pesaban lo mismo en una sesión de diseño que en una de migraciones. Por ruta, cada regla está en contexto justo cuando se va a romper, y `CLAUDE.md` vuelve a ser lo que tiene que ser: el mapa del proyecto.

Las rules de ECC eran personales porque se pensaron como una capa del autor. Pero el cliente recibe el repo para seguir mejorándolo (regla dura 19), y si usa ECC necesita saber lo mismo: que su envoltura `{success, data, error}`, sus skills de Boot 3 y su TDD obligatorio no aplican aquí.
