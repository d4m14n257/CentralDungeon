---
name: nuevo-endpoint-java
description: Scaffolds a new Spring Boot REST endpoint (controller, service, repository, DTO, mapper) inside its feature package, following CentralDungeon's layered architecture. Use when adding an endpoint to backend/ (Java/Spring Boot).
---

# Nuevo endpoint Java (Spring Boot)

**Las reglas no están acá**: son las «reglas fijas» de la skill `arquitectura-backend`, y se aplican todas. Esta skill es el procedimiento, en orden. Si la regla que hace falta no está en el resumen, se lee la sección de su referencia (`arquitectura-backend` §2.x).

## Pasos

1. **Confirmar qué tablas toca** contra la skill `modelo-datos`: el DDL (§4, más la tabla de migraciones posteriores) y la regla de negocio que corresponde (§5). Si el endpoint toca roles, bloqueo, pedidos, veto o ajustes, también `.claude/skills/modelo-datos/references/roles-y-alcance.md`.
2. **Si el schema cambia**: skill `er-diagram-sync`. Migración Flyway nueva, nunca editar una aplicada.
3. Crear o ajustar la `@Entity` en el paquete de la feature (`LAZY` por defecto, enums con `@Enumerated(EnumType.STRING)`).
4. Crear el `JpaRepository`, con `@Query` de parámetros nombrados y **el actor en el `WHERE`** cuando el recurso tiene dueño.
5. Crear los DTO en `dto/`: request y response separados, `record`, validación Jakarta en el de entrada, y Javadoc con un `@param` por componente.
6. Crear el mapper MapStruct si hace falta traducción no trivial.
7. Implementar el método de negocio en el service: transacción, verificación de pertenencia **antes** de tocar nada, y la excepción de `common/exception` con su código (#197) para cada negativa.
8. Crear el método del controller: ruta bajo `/api/v1`, recurso plural en kebab-case, status explícito (`201` + `Location` al crear, `204` sin cuerpo), y `@PreAuthorize` en el método, enumerando sus roles.
9. **Test del service** (skill `tests-java`) antes de dar el endpoint por terminado; IT si la regla depende del motor real.
10. Si el código de error es nuevo, **su clave va en `es` y en `en`** del frontend en el mismo commit (#197, #198).
11. Si un frontend lo va a llamar, su tipo en `features/<dominio>/types.ts` tiene que ser espejo exacto del `record` de respuesta (`arquitectura-frontend` §3.2).
