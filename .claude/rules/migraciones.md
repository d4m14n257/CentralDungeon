---
paths:
  - "backend/src/main/resources/db/migration/**"
---

# Migraciones Flyway

> Se cargan al tocar una migración. El procedimiento completo de un cambio de schema es `arquitectura` §7.3; las convenciones del schema, §4.1.

**9.** Todo cambio de schema es una migración Flyway nueva (`V<n+1>__snake_case.sql`). **Nunca se edita una migración aplicada, ni siquiera sus comentarios**: el checksum cubre el archivo entero y el arranque falla. Nunca `ddl-auto: update`. Toda búsqueda y reemplazo masivo sobre `backend/src` **excluye `db/migration/`**.

**8.** Nada de lógica de negocio en la base: ni triggers ni stored procedures (#3). Lo que era trigger vive en el service layer (`arquitectura` §4.5).

**10.** La migración llega en el mismo commit que la `@Entity`, el modelo de `arquitectura` §4 y el diagrama ER de `docs/modelo-datos.md`.

Los comentarios SQL van en inglés, como todo el código. Motor: **MySQL 8** — un consejo escrito para PostgreSQL se verifica antes de aplicarlo (`ecc:mysql-patterns`).
