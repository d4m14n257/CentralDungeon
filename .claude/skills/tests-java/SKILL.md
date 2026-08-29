---
name: tests-java
description: Checklist and patterns for CentralDungeon's Java backend tests (JUnit 6, Mockito, AssertJ, Testcontainers). Use when adding or reviewing tests in backend/.
---

# Tests del backend Java

Estrategia completa en `docs/arquitectura.md` §2.7. Versiones fijadas en §1.1: **JUnit 6** (Jupiter) y **Testcontainers 2.x**. JUnit 4 y el motor Vintage están fuera: nada de `@RunWith` ni `SpringRunner`. En Testcontainers 2.x las clases están reubicadas por módulo (`org.testcontainers.mysql.MySQLContainer`).

## Qué tipo usar

- **Unitario (JUnit 6 + Mockito + AssertJ)** — por defecto, para cualquier regla de negocio de un `service/`. Repositories mockeados, sin base de datos. Es el grueso de la cobertura y es **obligatorio** antes de dar una regla por terminada.
- **Integración (Testcontainers + `@SpringBootTest`)** — cuando la regla depende del motor real: constraints, transacciones, `@Query` no triviales, y toda la lógica migrada desde los triggers heredados (alta de usuario con rol default, paso de candidato a jugador, conteo derivado de jugadores). Flyway aplica las migraciones reales sobre el contenedor.
- **Contrato HTTP (`@WebMvcTest` + MockMvc)** — solo donde el contrato importe por sí mismo (status codes, forma del `ProblemDetail`). No dupliques lo que ya cubre Playwright.
- **E2E** — no se escribe en Java. Va en Playwright, desde el frontend.

## Convenciones

- `<Clase>Test` para unitarios, `<Clase>IT` para integración.
- El nombre del test describe el caso, no el método: `rechazaPostulacionDeUsuarioBloqueado()`, no `testRegister2()`.
- Aserciones con AssertJ (`assertThat`), no `assertEquals`.

## Patrón — unitario de service

1. **Arrange**: `@Mock` los repositories, `@InjectMocks` el service.
2. **Act**: llamar al método de negocio.
3. **Assert**: verificar el resultado y, cuando la regla sea "no debe hacer X", verificar con `verify(repo, never())` que no se persistió nada.
4. Cubrir los caminos de error, no solo el feliz: usuario `Blocked`, mesa en estado que no admite postulaciones, transiciones inválidas de `status`, permisos por mesa (`Owner` vs `Master`).

## Patrón — integración

1. `@Testcontainers` + `@SpringBootTest` con MySQL 8 (misma versión que producción) y perfil `test`.
2. Flyway aplica `V1__baseline.sql` y `V2__seed.sql`; `ddl-auto: validate`. Nunca `create-drop`: ocultaría un desfase entre entidad y schema real, que es justo lo que este test debe detectar.
3. Probar el caso de uso completo, no un query aislado. Ejemplo: candidato aceptado → su `status` pasa a `Player` → el conteo derivado de jugadores de la mesa sube en 1 → se generó la notificación.
