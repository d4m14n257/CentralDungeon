---
name: modelo-datos
description: CentralDungeon's data model — schema conventions, the baseline DDL and the migrations after it, the business rules that live in services instead of triggers, the Admin/Owner line and what is out of v1. Use before touching an @Entity, a migration, a repository query or a business rule in backend/, and whenever code cites "modelo-datos skill §n" or roles-y-alcance.md.
---

# Modelo de datos

**Fuente de verdad del schema y de sus reglas.** El código y `docs/decisiones.md` citan estas secciones por su número (`modelo-datos` §5), que es el mismo que tenían cuando vivían en `docs/modelo-datos.md`. Ese documento conserva lo que se lee y no se aplica: qué cambió respecto del schema heredado (§2) y el diagrama entidad-relación (§3).

El **porqué** de cada cosa está en `docs/decisiones.md`; acá está el **qué**. Las referencias `#n` apuntan a esas decisiones.

## 1. Convenciones

| Tema | Convención | Ref. |
|---|---|---|
| Nombre de tabla | `snake_case`, plural, minúsculas | — |
| Nombre de columna | `snake_case` singular. FK = `<entidad_singular>_id` | — |
| Clave primaria | Siempre `id`, `VARCHAR(64)`, UUID v7 generado en la aplicación | #9 |
| Enums | Columna `VARCHAR(32)` + `enum` de Java con `@Enumerated(EnumType.STRING)`. Nunca el tipo `ENUM` de MySQL | #10 |
| Borrado | **Soft delete**: columna `status` con el valor `Deleted` **y** `deleted_at DATETIME NULL` con la fecha. Toda lectura filtra por estado | #25 |
| Cascadas | Se resuelven en el **service layer**, nunca con `ON DELETE CASCADE`. Un borrado arrastra a sus dependientes con la **misma** marca de tiempo, en una transacción | #25 |
| Fechas y horas | **Todo en UTC.** No existe ninguna columna `timezone`: la conversión a hora local la hace el frontend con la zona del navegador | #22 |
| Timestamps | `created_at DATETIME NOT NULL`, `updated_at DATETIME NULL`, `deleted_at DATETIME NULL` donde aplique | #25 |
| Texto enriquecido | `LONGTEXT` en la tabla que lo necesita. **Nunca** una fila de `files`. Se sanitiza al guardar y al servir | #62 |
| Charset | `utf8mb4` / `utf8mb4_unicode_ci` | — |

## Cómo se usa el modelo

- **Ninguna regla de negocio vive en la base** (regla dura 8, #3): ni triggers ni stored procedures. Lo que era trigger vive en el service, y §5 dice en cuál. Cada regla llega con su test unitario; las que dependen del motor real —concurrencia, locks, constraints— también con su IT.
- **Las invariantes que MySQL no puede expresar las sostiene el service con un lock**: un solo `Primary` vivo por mesa (#73), una sola postulación activa por par (#28), el cupo (#34), la plataforma nunca sin `Owner`. Antes de escribir una, se busca cómo lo hace la que ya existe.
- **Una fila `Deleted` es invisible en todos los caminos de lectura**, no solo en el que uno está tocando (#25). Una query nueva filtra por estado desde el primer día.
- **Lo derivado no se guarda** (#11, #232): conteos de jugadores, asistencia agregada, usos de un archivo. Se calculan en la lectura.
- **Las referencias polimórficas** (`approval_requests.entity_id`) no tienen FK: su integridad es del service y de la verificación periódica que exige #78.
- **Todo cambio de schema es una migración Flyway nueva** (regla dura 9). Nunca se edita una aplicada ni se usa `ddl-auto: update`. El procedimiento está en la skill `er-diagram-sync`.
- **Una migración aplicada no se toca ni en sus comentarios.** El checksum de Flyway cubre el archivo entero: cambiar una coma en un comentario SQL hace que la base existente rechace el arranque (`Migration checksum mismatch`). Toda búsqueda y reemplazo masivo sobre `backend/src` **excluye `db/migration/`**. Pasó en F4.0, y una cita vieja en el comentario de una migración se deja como está.
- **Dos «owner» que no son lo mismo**: el rol de plataforma `Owner` y `masters.master_type = 'Primary'`. No se llaman igual en el código (#67, #71, #89). La línea entre `Admin` y `Owner` está en `references/roles-y-alcance.md` §3.
- **Campañas y Temporadas están fuera de v1 a propósito** (regla dura 13, #7). Lo que falta resolver antes de construirlas, en `references/fuera-de-v1.md` §7.1.

## El detalle, por sección

| § | Qué cubre | Archivo |
|---|---|---|
| 4 y 6 | El DDL de `V1__baseline.sql`, literal, más la tabla de lo que cambió cada migración posterior, y el seed mínimo | [`references/ddl.md`](references/ddl.md) |
| 5 | Las reglas de negocio por subsistema, y en qué service vive cada una: identidad y roles, solicitudes, mesa, postulaciones, sesiones y peticiones, comentarios y karma, visibilidad de perfiles, catálogos, archivos, bandeja, notificaciones, configuración, auditoría | [`references/reglas-negocio.md`](references/reglas-negocio.md) |
| 7 | Lo que queda fuera de v1, decidido y no olvidado; §7.1, Campañas y Temporadas | [`references/fuera-de-v1.md`](references/fuera-de-v1.md) |
| — | **Roles y alcance**, lo normativo de F3: la matriz `Admin`/`Owner` y sus tres invariantes (§3), las reglas de cada rebanada de F3 (§4), lo que no construyó (§5), la verificación manual (§6) y los riesgos que el código cita (§7) | [`references/roles-y-alcance.md`](references/roles-y-alcance.md) |

El diagrama entidad-relación está en `docs/modelo-datos.md` §3 y los de cada subsistema en `docs/diagramas/11`–`16`.
