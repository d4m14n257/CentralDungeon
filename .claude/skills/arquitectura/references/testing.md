# 5. Testing

> Parte de la skill `arquitectura` (#274). Junta lo que estaba repartido en tres lugares: la estrategia del backend (el viejo `arquitectura-backend` §2.7), el cómo de la skill `tests-java`, y el testing del frontend (el viejo `arquitectura-frontend` §3.4).

**Toda regla de negocio nueva llega con su test unitario** (regla dura 7). No hay tests en ninguno de los dos repos viejos, y ese patrón no se repite. Lo que depende del motor real —concurrencia, locks, constraints— también lleva su IT.

**Antes de correr nada**, se lee la skill `entorno-local`. Bajo colima, `./mvnw verify` falla entero si no se exportan `DOCKER_HOST` y `TESTCONTAINERS_RYUK_DISABLED`, y nunca se corre `clean` con el backend levantado.

## 5.1 Backend

Versiones fijadas en `docs/arquitectura.md` §1.1: **JUnit 6** (Jupiter) y **Testcontainers 2.x**. JUnit 4 y el motor Vintage están fuera, así que nada de `@RunWith` ni `SpringRunner`. En Testcontainers 2.x las clases están reubicadas por módulo (`org.testcontainers.mysql.MySQLContainer`).

| Tipo | Herramienta | Cuándo |
|---|---|---|
| Unitario | JUnit 6 + Mockito + AssertJ | Por defecto, para cualquier regla de negocio de un service. Los repositories se mockean y no hay base de datos. Es el grueso de la cobertura y es **obligatorio** antes de dar una regla por terminada |
| Integración | Testcontainers 2.x (MySQL real) + `@SpringBootTest` | Cuando la regla depende del motor: constraints, transacciones, locks y concurrencia, `@Query` no triviales, y toda la lógica que antes vivía en triggers (§4.5) |
| Contrato HTTP | `@SpringBootTest` + MockMvc contra el contenedor (los `*ApiIT`) | Donde importa el contrato mismo: status, forma del `ProblemDetail` y la matriz de roles recorrida. No se duplica lo que ya cubre Playwright |
| E2E | — | No se escribe en Java: va en Playwright (§5.3) |

**Convenciones**
- `<Clase>Test` para los unitarios y `<Clase>IT` para los de integración.
- **Todo en inglés, incluido el nombre del test.** El nombre describe el caso, no el método: `rejectsApplicationFromBlockedUser()`, no `testRegister2()`.
- Las aserciones van con AssertJ (`assertThat`), no con `assertEquals`.
- La clase de test lleva su Javadoc: qué regla prueba y qué decisión `#n` la pide (§6.1).

**Patrón unitario de un service**
1. **Arrange**: `@Mock` para los repositories y `@InjectMocks` para el service.
2. **Act**: se llama al método de negocio.
3. **Assert**: se verifica el resultado. Cuando la regla es «no debe hacer X», se verifica con `verify(repo, never())` que no se persistió nada.
4. Se cubren los caminos de error, no solo el feliz:
   - usuario `Blocked`;
   - mesa en un estado que no admite la acción;
   - transiciones inválidas;
   - **pertenencia**: el rol correcto sobre un recurso ajeno (#121), y `Primary` frente a `Secondary`.

**Patrón de integración**
1. `@Testcontainers` + `@SpringBootTest`, con MySQL 8 (la misma versión que producción) y el perfil `test`.
2. Flyway aplica **todas** las migraciones de `db/migration/`, de `V1__baseline.sql` en adelante, con `ddl-auto: validate`. Nunca `create-drop`: ocultaría justo el desfase entre la entidad y el schema real que este test tiene que detectar.
3. Se prueba el caso de uso completo, no una query aislada. Por ejemplo: se acepta un candidato → su estado pasa a `Player` → el conteo derivado de jugadores de la mesa sube en 1 → se generó la notificación.
4. Si el test crea datos que el e2e también limpia, la tabla nueva entra en el orden de borrado de `TestDataService`. Olvidarlo rompió la limpieza cinco veces (#171, #172).

## 5.2 Frontend

- **Vitest + React Testing Library** para los hooks con lógica y los componentes con comportamiento condicional. Se prueba lo que el usuario ve, no la implementación.
- Los tests viven junto al archivo que prueban: `Foo.tsx` → `Foo.test.tsx`.
- **Corren en `es`**, así que un texto se busca por su traducción en español. Por eso el inglés renderizado es deuda propia de F4 (`fase-4-revision.md` §2.1).
- Lo que no es el sujeto del test se mockea en el límite del módulo (`vi.mock('@/hooks/useAvailableContexts', …)`), no por dentro: la guardia se prueba con la respuesta del hook, y el hook tiene su propio test.
- Una pantalla que lee la URL (#185) se monta dentro de `MemoryRouter` con la URL de entrada. Una que hace queries, dentro de un `QueryClient` nuevo con `retry: false` para cada test.

## 5.3 E2E

**Playwright** (`frontend/e2e/`) corre contra el **backend real**, nunca contra mocks, levantado con `-Dspring-boot.run.profiles=dev,test`. Ese perfil es el que publica `/api/v1/auth/test-login`: sin él, todas las pruebas fallan juntas, y eso es el entorno, no el código (skill `entorno-local`).

- Cubre los flujos críticos: login con Discord, crear una mesa (master), postularse a una mesa (jugador), subir un archivo. Además, cada cruce de roles o de actores que ningún unitario puede ver por su cuenta.
- Cada actor se crea con `test-login`, que le deja **exactamente** los roles que se piden. Un actor sin `asMaster` pierde el `Master` que le haya dado un paso anterior.
- Toda pantalla nueva que alguien alcanza navegando necesita un e2e que la visite; si no, puede romperse sin que nadie se entere (F4.1).
