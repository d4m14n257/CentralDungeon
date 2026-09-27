# 3.4 Testing frontend

> Parte de la skill `arquitectura-frontend`. Se movió desde `docs/` en F4.0 y conserva su numeración original, que es la que citan el código y `decisiones.md`.


- **Vitest + React Testing Library**: hooks con lógica y componentes con comportamiento condicional. Se testea lo que el usuario ve, no la implementación.
- **Playwright** (`e2e/`): los cuatro flujos críticos — login con Discord, crear mesa (master), postularse a una mesa (jugador), subir archivo de preparación. Corre contra backend real, no mocks.
