---
name: er-diagram-sync
description: Keeps the modelo-datos skill (DDL and migration table), the ER diagrams in docs/ and the Flyway migrations in sync whenever a JPA entity changes. Use after adding, removing, or modifying an @Entity in backend/.
---

# Mantener sincronizado el modelo de datos

El proyecto viejo terminó con dos `database.sql` desactualizados en dos repos distintos, uno de ellos con errores de sintaxis que impedían ejecutarlo. La skill `modelo-datos` es ahora la fuente de verdad del schema y no puede quedar atrás del código (regla dura 10).

## Cuándo aplica

Cualquier cambio a una clase `@Entity`: tabla nueva, columna nueva, relación nueva o eliminada, cambio de tipo, valor nuevo en un enum.

## Qué hacer, en orden

1. **Migración Flyway nueva** en `backend/src/main/resources/db/migration/`, con el número siguiente al último que exista (`ls` antes de elegirlo). Nunca editar una migración ya aplicada (regla dura 9). El comentario SQL de cabecera dice **por qué**, con su `#n`, en inglés.
2. **Actualizar `.claude/skills/modelo-datos/references/ddl.md`**: **una fila nueva** en la tabla de migraciones con qué cambió y por qué. El bloque DDL de ese archivo es el `V1__baseline.sql` literal y no se toca, igual que la migración.
3. **Actualizar el diagrama entidad-relación**: el bloque Mermaid de `docs/modelo-datos.md` §3 (atributos de la entidad y la línea de relación si cambió una FK) **y** el `.mmd` del subsistema en `docs/diagramas/11`–`16`.
4. **Respetar las convenciones** de `modelo-datos` §1: `snake_case` plural, PK `id` `VARCHAR(64)` generada en la app, enums como `VARCHAR(32)` (nunca el tipo `ENUM` de MySQL), soft delete con `status` y `deleted_at`, `created_at`/`updated_at`, cascadas en el service y no `ON DELETE CASCADE`.
5. Si el cambio agrega o modifica una **regla de negocio**, anotarla en `.claude/skills/modelo-datos/references/reglas-negocio.md` (§5) con el service donde vive, y escribir su test.
6. Si la tabla nueva la llena el e2e, agregarla al orden de borrado de `TestDataService` (#171, #172).
7. Si el cambio **contradice una decisión** ya registrada, actualizar `docs/decisiones.md` en el mismo commit: no dejar el registro mintiendo.
8. Verificar que los bloques Mermaid siguen siendo válidos (nombres de entidad consistentes, sin comas sueltas) antes de dar el cambio por terminado.
