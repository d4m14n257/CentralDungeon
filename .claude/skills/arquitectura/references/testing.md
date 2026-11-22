# 5. Testing

> Parte de la skill `arquitectura` (ADR-01 §5). Junta lo que estaba repartido en tres lugares: la estrategia del backend (el viejo `arquitectura-backend` §2.7), el cómo de la skill `tests-java`, y el testing del frontend (el viejo `arquitectura-frontend` §3.4).

**Toda regla de negocio nueva llega con su test unitario** (regla dura 7). No hay tests en ninguno de los dos repos viejos, y ese patrón no se repite. Lo que depende del motor real —concurrencia, locks, constraints— también lleva su IT.

**Antes de correr nada**, se lee la skill `entorno-local`. Bajo colima, `./mvnw verify` falla entero si no se exportan `DOCKER_HOST` y `TESTCONTAINERS_RYUK_DISABLED`, y nunca se corre `clean` con el backend levantado.

## 5.1 Backend

Versiones fijadas en `arquitectura` §1.1: **JUnit 6** (Jupiter) y **Testcontainers 2.x**. JUnit 4 y el motor Vintage están fuera, así que nada de `@RunWith` ni `SpringRunner`. En Testcontainers 2.x las clases están reubicadas por módulo (`org.testcontainers.mysql.MySQLContainer`).

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
- La clase de test lleva su Javadoc: qué regla prueba y por qué existe, con palabras propias y sin citar ADRs, skills, reglas ni fases (§6.1, ADR-01 «Proyecto, proceso y conocimiento» §12 «La documentación del código se explica sola»).

**Patrón unitario de un service**
1. **Arrange**: `@Mock` para los repositories, y el service **se arma con su constructor en `@BeforeEach`** (`new SettingsService(...)`), igual que lo inyecta Spring (§2.2). `@InjectMocks` existe en tres tests viejos; no se sigue.
2. **Act**: se llama al método de negocio.
3. **Assert**: se verifica el resultado. Cuando la regla es «no debe hacer X», se verifica con `verify(repo, never())` que no se persistió nada.
4. Se cubren los caminos de error, no solo el feliz:
   - usuario `Blocked`;
   - mesa en un estado que no admite la acción;
   - transiciones inválidas;
   - **pertenencia**: el rol correcto sobre un recurso ajeno (ADR-05 §5), y `Primary` frente a `Secondary`.

**Patrón de integración**
1. `@Testcontainers` + `@SpringBootTest`, con MySQL 8 (la misma versión que producción) y el perfil `test`.
2. Flyway aplica **todas** las migraciones de `db/migration/`, de `V1__baseline.sql` en adelante, con `ddl-auto: validate`. Nunca `create-drop`: ocultaría justo el desfase entre la entidad y el schema real que este test tiene que detectar.
3. Se prueba el caso de uso completo, no una query aislada. Por ejemplo: se acepta un candidato → su estado pasa a `Player` → el conteo derivado de jugadores de la mesa sube en 1 → se generó la notificación.
4. Si el test crea datos que el e2e también limpia, la tabla nueva entra en el orden de borrado de `TestDataService` y en `CLEANED` de `TestDataCleanupIT` (o en `EXEMPT`, con su motivo). Olvidarlo rompió la limpieza más de diez veces, y ahora ese IT falla en vez de un spec ajeno en la corrida siguiente (ADR-24 «Testing» §3 «Lo que crea un e2e se borra al terminar la corrida, y un IT falla si una tabla que cuelga de `users` o `game_tables` queda fuera de la limpieza»).

**Patrón de concurrencia** (la regla que prueba, en ADR-06 «Roles y alcance» §2 «La plataforma nunca se queda sin `Owner`, y toda invariante con lock lo toma antes de leer lo que decide»)
1. **Primero el IT, y verlo en rojo**; después el arreglo, y verlo en verde. Un IT de concurrencia que nunca se vio en rojo no prueba nada: la carrera puede no haberse dado nunca.
2. **La carrera se vuelve determinista con `LockGate`** (`backend/src/test/java/com/centraldungeon/common/LockGate.java`), no con un `CountDownLatch`, que solo la deja posible:
   - `LockGate.hold(transactionManager, …)` abre una transacción de test en su propio hilo y toma el mismo lock que el service va a pedir (`findByIdForUpdate` de la mesa, `lockById` del pedido, `lockByName("Owner")`, un `SELECT … FOR UPDATE`);
   - se lanzan los contendientes, y `awaitWaiters(LockGate.monitor(mysql), n, timeout)` —o `awaitUntil`, cuando uno puede terminar antes, como un deadlock que MySQL rompe— espera a que estén en cola detrás del lock, leyendo `performance_schema.data_lock_waits`. Para entonces cada uno ya hizo sus lecturas previas al lock;
   - `open()` suelta la compuerta. Se usa en un `try`-with-resources: cerrarla abre y espera al hilo, así que una aserción que falla no deja un lock tomado para el resto de la clase.
3. **Se cuenta lo que queda en la base, no solo las respuestas**: estados finales, filas de historial (`registration_status_changes`, `user_role_changes`, `system_setting_changes`) y de `registration_rejections`. Dos `200` pueden esconder dos filas donde tenía que haber una.
4. Se sigue la forma de los IT existentes: `@SpringBootTest(webEnvironment = NONE)`, el service llamado directo con su `CurrentUser`, sin `@Transactional` en la clase, y un pool de 8 hilos o menos (Hikari tiene 10). Referencias: `RegistrationConcurrencyIT`, `UserRoleServiceIT` y `AdminQueueServiceIT`.

**La matriz de roles** (`backend/src/test/java/com/centraldungeon/matrix/`, ADR-24 «Testing» §4 «Todo endpoint tiene su fila en la matriz de roles, y el esperado sale de los documentos»)
1. **Todo endpoint tiene su fila** en el `ROWS` de la clase `*RoleMatrixIT` de su dominio, con una celda por cada columna base de `MatrixActor`. `RoleMatrixCompletenessIT` recorre los mappings reales y falla si falta una fila o sobra una.
2. **El esperado se escribe desde los documentos**: la ADR, `references/modelo-datos/roles-y-alcance.md` §3 y la regla de §4.5. **Nunca se lee el `@PreAuthorize` para llenar la celda**: si los documentos no alcanzan, la celda es una decisión que falta, no un hueco que se rellena con lo que el código hace.
3. **Una celda en rojo se tría** en las tres categorías de `fase-4-revision.md` §3. Si es una decisión abierta, va a `KnownDisagreements` con lo observado y una frase de qué choca con qué; la entrada se borra cuando la decisión se aplica, y el test lo pide solo.
4. **Admin y Owner esperan lo mismo** (`AdminOwnerParityTest`, sin base): una fila que los separa entra en `MAY_DIFFER` con su motivo, y solo si mueve el rango de admin.
5. Donde la celda niega un recurso ajeno, es `404` si el actor no puede verlo y `403` si lo ve y no puede hacer la acción (ADR-05 «Seguridad, sesión y login» §10 «Negar no confirma: lo que el actor no puede ver es `404`, y `403` es solo de lo que ve y no puede hacer»; qué es «ver» por recurso, en §2.6).

## 5.2 Frontend

- **Vitest + React Testing Library** para los hooks con lógica y los componentes con comportamiento condicional. Se prueba lo que el usuario ve, no la implementación.
- Los tests viven junto al archivo que prueban: `Foo.tsx` → `Foo.test.tsx`.
- **Corren en `es`**, así que un texto se busca por su traducción en español. Por eso el inglés renderizado es deuda propia de F4 (`fase-4-revision.md` §2.1).
- Lo que no es el sujeto del test se mockea en el límite del módulo (`vi.mock('@/hooks/useAvailableContexts', …)`), no por dentro: la guardia se prueba con la respuesta del hook, y el hook tiene su propio test.
- **Toda escritura pasa por una revisión** (ADR-21 §9), así que un test que envía un formulario la acepta con `acceptReview()` de `src/test/review.ts` —busca el diálogo cuyo título pregunta, «¿…?», y aprieta el botón que no es «Cancelar»— dentro de un `ConfirmDialogProvider`. Un test de validación, donde el formulario no llega a enviarse, **no** la espera: la revisión solo se abre para algo que se puede escribir.
- Una pantalla que lee la URL (ADR-12 §7) se monta dentro de `MemoryRouter` con la URL de entrada. Una que hace queries, dentro de un `QueryClient` nuevo con `retry: false` para cada test.

## 5.3 E2E

**Playwright** (`frontend/e2e/`) corre contra el **backend real**, nunca contra mocks, levantado con `-Dspring-boot.run.profiles=dev,test`. Ese perfil es el que publica `/api/v1/auth/test-login`: sin él, todas las pruebas fallan juntas, y eso es el entorno, no el código (skill `entorno-local`).

- Cubre los flujos críticos: login con Discord, crear una mesa (master), postularse a una mesa (jugador), subir un archivo. Además, cada cruce de roles o de actores que ningún unitario puede ver por su cuenta.
- Cada actor se crea con `test-login`, que le deja **exactamente** los roles que se piden. Un actor sin `asMaster` pierde el `Master` que le haya dado un paso anterior.
- Después de cada envío que escribe, `acceptReview(page)` de `e2e/helpers/review.ts` acepta la revisión (ADR-21 §9), con la misma regla que en Vitest: no se espera donde el formulario se rechaza solo.
- Toda pantalla nueva que alguien alcanza navegando necesita un e2e que la visite; si no, puede romperse sin que nadie se entere (F4.1).
