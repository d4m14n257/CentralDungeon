---
paths:
  - "backend/pom.xml"
  - "frontend/package.json"
  - ".claude/skills/arquitectura/references/stack.md"
---

# Versiones del stack — regla dura 15

Las versiones están **fijadas** en `arquitectura` §1 (#19). **Subir una major es una decisión, no un mantenimiento**: se registra como ADR nueva en `docs/adr/` y se actualiza `arquitectura` §1 en el mismo commit. Un parche o una minor que no rompe nada no necesita ADR, pero sí que §1 siga diciendo la verdad.

Ojo con Boot 4 al leer material escrito para Boot 3: **Jackson 3** (`tools.jackson.*`), **JSpecify** en vez de `org.springframework.lang.@Nullable`, **JUnit 6** (nada de JUnit 4 ni Vintage), **Testcontainers 2.x** (clases reubicadas por módulo), `RestTemplate` ya no se autoconfigura, `@MockBean` no existe (es `@MockitoBean`). Ante la duda, context7.
