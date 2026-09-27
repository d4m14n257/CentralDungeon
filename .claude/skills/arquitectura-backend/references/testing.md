# 2.7 Testing backend

> Parte de la skill `arquitectura-backend`. Se movió desde `docs/` en F4.0 y conserva su numeración original, que es la que citan el código y `decisiones.md`.


| Tipo | Herramienta | Alcance |
|---|---|---|
| Unitario | JUnit 6 + Mockito + AssertJ | Cada regla de negocio de un service, con los repositories mockeados. Es el grueso de la cobertura. **Obligatorio** antes de dar una regla por terminada. |
| Integración | Testcontainers 2.x (MySQL real) + `@SpringBootTest` | Queries JPA no triviales, constraints, transacciones, y toda la lógica migrada desde triggers. Flyway aplica las migraciones reales sobre el contenedor. |
| Contrato HTTP | `@WebMvcTest` + MockMvc | Solo donde el contrato importe por sí mismo (status codes, forma del `ProblemDetail`). No se duplica lo que ya cubre Playwright. |

Convención: `<ClaseATestear>Test` para unitarios, `<ClaseATestear>IT` para integración. Cada test nombra el caso, no el método: `rechazaPostulacionDeUsuarioBloqueado()`, no `testRegister2()`.
