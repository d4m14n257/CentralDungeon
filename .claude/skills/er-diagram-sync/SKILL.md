---
name: er-diagram-sync
description: Keeps docs/modelo-datos.md (Mermaid ER + baseline DDL) and the Flyway migrations in sync whenever a JPA entity changes. Use after adding, removing, or modifying an @Entity in backend/.
---

# Mantener sincronizado el modelo de datos

El proyecto viejo terminó con dos `database.sql` desactualizados en dos repos distintos, uno de ellos con errores de sintaxis que impedían ejecutarlo. `docs/modelo-datos.md` es ahora la fuente de verdad del schema y no puede quedar atrás del código.

## Cuándo aplica

Cualquier cambio a una clase `@Entity`: tabla nueva, columna nueva, relación nueva o eliminada, cambio de tipo, valor nuevo en un enum.

## Qué hacer, en orden

1. **Migración Flyway nueva** en `backend/src/main/resources/db/migration/`, con el siguiente número de versión (`V3__...`, `V4__...`). Nunca editar una migración ya aplicada.
2. **Actualizar `docs/modelo-datos.md`**:
   - el bloque de atributos de la entidad en el diagrama Mermaid (§3),
   - la línea de relación (`entidad ||--o{ otra : "..."`) si cambió una FK,
   - el DDL baseline (§4), para que refleje el estado final acumulado, no solo la migración nueva.
3. **Respetar las convenciones de §1**: `snake_case` plural, PK `id` `VARCHAR(64)` generada en la app, enums como `VARCHAR(32)` (nunca el tipo `ENUM` de MySQL), soft delete vía `status`, `created_at`/`updated_at`, cascadas en el service y no `ON DELETE CASCADE`.
4. Si el cambio agrega o modifica una **regla de negocio**, anotarla en la tabla de §5 (las reglas que reemplazaron a los triggers) y escribir su test.
5. Si el cambio **contradice una decisión** ya registrada, actualizar `docs/decisiones.md` en el mismo commit — no dejar el registro mintiendo.
6. Verificar que el bloque ` ```mermaid ` sigue siendo válido (nombres de entidad consistentes, sin comas sueltas) antes de dar el cambio por terminado.
