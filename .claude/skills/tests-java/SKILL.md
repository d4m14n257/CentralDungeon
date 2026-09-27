---
name: tests-java
description: Checklist and patterns for CentralDungeon's Java backend tests (JUnit 6, Mockito, AssertJ, Testcontainers). Use when adding or reviewing tests in backend/.
---

# Tests del backend Java

La estrategia es la de `arquitectura-backend` §2.7; esta skill es el cómo. Versiones fijadas en `docs/arquitectura.md` §1.1: **JUnit 6** (Jupiter) y **Testcontainers 2.x**. JUnit 4 y el motor Vintage están fuera: nada de `@RunWith` ni `SpringRunner`. En Testcontainers 2.x las clases están reubicadas por módulo (`org.testcontainers.mysql.MySQLContainer`).

**Antes de correr las IT**, la skill `entorno-local`: bajo colima, `./mvnw verify` falla entero si no se exportan `DOCKER_HOST` y `TESTCONTAINERS_RYUK_DISABLED`, y nunca se corre `clean` con el backend levantado.

## Qué tipo usar

- **Unitario (JUnit 6 + Mockito + AssertJ)**: por defecto, para cualquier regla de negocio de un service. Repositories mockeados, sin base de datos. Es el grueso de la cobertura y es **obligatorio** antes de dar una regla por terminada (regla dura 7).
- **Integración (Testcontainers + `@SpringBootTest`)**: cuando la regla depende del motor real, es decir constraints, transacciones, locks y concurrencia, `@Query` no triviales, y toda la lógica migrada desde los triggers heredados (skill `modelo-datos` §5). Flyway aplica las migraciones reales sobre el contenedor.
- **Contrato HTTP** (`@SpringBootTest` + MockMvc contra el contenedor, como los `*ApiIT`): donde importa el contrato mismo, es decir status, forma del `ProblemDetail` y la matriz de roles recorrida. No se duplica lo que ya cubre Playwright.
- **E2E**: no se escribe en Java. Va en Playwright, desde el frontend.

## Convenciones

- `<Clase>Test` para unitarios, `<Clase>IT` para integración.
- **Todo en inglés, incluido el nombre del test.** Describe el caso, no el método: `rejectsApplicationFromBlockedUser()`, no `testRegister2()`.
- Aserciones con AssertJ (`assertThat`), no `assertEquals`.
- La clase de test lleva su Javadoc: qué regla prueba y qué decisión `#n` la pide.

## Patrón: unitario de service

1. **Arrange**: `@Mock` los repositories, `@InjectMocks` el service.
2. **Act**: llamar al método de negocio.
3. **Assert**: verificar el resultado y, cuando la regla sea «no debe hacer X», verificar con `verify(repo, never())` que no se persistió nada.
4. Cubrir los caminos de error, no solo el feliz: usuario `Blocked`, mesa en un estado que no admite la acción, transiciones inválidas, y **pertenencia**: el rol correcto sobre el recurso ajeno (#121), `Primary` frente a `Secondary`.

## Patrón: integración

1. `@Testcontainers` + `@SpringBootTest` con MySQL 8 (misma versión que producción) y perfil `test`.
2. Flyway aplica **todas** las migraciones de `db/migration/` (de `V1__baseline.sql` en adelante), con `ddl-auto: validate`. Nunca `create-drop`: ocultaría un desfase entre entidad y schema real, que es justo lo que este test debe detectar.
3. Probar el caso de uso completo, no una query aislada. Ejemplo: candidato aceptado → su estado pasa a `Player` → el conteo derivado de jugadores de la mesa sube en 1 → se generó la notificación.
4. Si el test crea datos que el e2e también limpia, la tabla nueva entra en el orden de borrado de `TestDataService`. Olvidarlo rompió la limpieza cinco veces (#171, #172).
