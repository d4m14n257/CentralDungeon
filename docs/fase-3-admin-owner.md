# F3 — Admin y Owner: implementación

> **Cómo se construye F3.** `plan-desarrollo.md` §4 dice *qué* entrega; acá está el detalle de las cinco rebanadas, la matriz de capacidades y el camino de verificación.
>
> El *por qué* de cada decisión está en `decisiones.md`, las reglas de negocio en `modelo-datos.md` §5, las pantallas en `frontend-diseno.md` y el *cómo se escribe el código* en `arquitectura.md`.
>
> **Documento vivo mientras F3 esté abierta.** Cada rebanada se marca terminada acá al cerrarse, con su inventario. Cuando F3 cierre, este documento queda como registro y no se toca más.

## 1. Por qué existe este documento

F3 es la fase que administra, y administrar es lo que más fácil se construye mal: cada endpoint decide su propia autorización con un `@PreAuthorize`, y basta que uno escriba `hasRole('ADMIN')` en vez de `hasAnyRole('ADMIN','OWNER')` para que el rol máximo de la plataforma quede afuera de una pantalla. No es hipotético: es exactamente la forma del bug que #123 documenta y que la CVE-2025-41248 describe.

Además llega con una pregunta abierta desde #67 y que #89 y #169 acotaron sin cerrar del todo: **qué separa a un `Admin` de un `Owner`, en concreto**. «`Owner` puede todo lo que puede `Admin`» dice qué comparten y no dice qué no. Esta fase lo cierra, y lo cierra **antes** de escribir la primera rebanada, porque las cuatro que siguen preguntan «¿quién puede esto?» en cada endpoint.

## 2. El punto de partida

Se verifica en el repositorio al abrir la fase, no se asume. Lo que sigue es lo que se sabe hoy, y la primera tarea de F3.1 es confirmarlo contra el código.

**Lo que ya está:**

- Los cuatro roles existen en `PlatformRole` y se siembran en `V2__seed.sql` (#67).
- `hasAnyRole('ADMIN','OWNER')` es el patrón vigente en todos los controllers de admin; **no** hay `RoleHierarchy` registrado, que es lo que #89 exige.
- El JWT afirma identidad y no autorización: `JwtAuthenticationFilter` relee usuario y roles de la base en cada request, con caché Caffeine de 60 s (#122, #128).
- `/admin/tables`, `/admin/catalogs` y `/admin/files` existen desde F1, con `AdminSectionNav`.
- La máquina de estados de la mesa está completa, incluidos `pause()` y `resume()` — **con endpoint y sin ninguna pantalla** desde E2 (#163).

**Los huecos concretos que F3 cierra:**

| Hueco | Dónde se ve hoy | Cerrado en |
|---|---|---|
| **No existe ningún service que otorgue o quite roles**, así que la exclusión `Admin`/`Owner` de #169 está escrita en `modelo-datos.md` §5 y sin implementar | no hay `UserRoleService` | F3.1 |
| **No se puede bloquear una cuenta** (#84); `UserStatus.Blocked` existe y nada lo produce | `UserStatus` | F3.1 |
| `/admin/users` no existe | `config/paths.ts` | F3.1 |
| `approval_requests` **sin mapear**: el mecanismo único de #42 no existe, y con él faltan los cuatro pedidos de #90 | — | F3.2 |
| `/admin/queue` no existe, y por eso Aprobar y Pedir cambios viven en `/admin/tables`, que es la pantalla equivocada (#176) | `routes/admin/AdminTablesPage.tsx` | F3.3 |
| `/admin/tables` lista solo lo que espera revisión; el listado completo con filtros y `?q=` es lo que #176 promete | `DEFAULT_ADMIN_REVIEW_STATUSES` | F3.3 |
| **`PauseRequested` es un estado que ningún endpoint produce** — relevado como huérfano en F1.7 | `GameTableStatus` | F3.4 |
| **`pause()` y `resume()` no tienen botón en ninguna pantalla** — huérfano desde #163 | `GameTableController` | F3.4 |
| **El veto no existe**: `TableRegistrationStatus.Blocked` está declarado fuera de alcance desde E1, y con él queda sin cumplir el filtro de visibilidad de #29 | `TableRegistrationStatus` | F3.4 |
| **La exclusión del vetado en la lectura de archivos**, que #206 dejó anotada con destino a esta fase | `FileService.requireReadable` | F3.4 |
| `system_settings` sin mapear; los valores de #141 siguen siendo constantes en el código | `RegistrationService` y otros | F3.5 |

## 3. La línea entre `Admin` y `Owner`

Es el entregable que F3 tiene y ninguna otra fase: **dónde termina uno y empieza el otro, escrito en un solo lugar.**

### La regla, en tres frases

1. **`Owner` es `Admin` con más alcance, y la relación va en una sola dirección** (#89). No hay ninguna otra herencia entre roles: `Owner` no implica `Player` ni `Master`, y para jugar o dirigir hace falta tener ese rol.
2. **Nadie tiene los dos a la vez** (#169). Otorgar uno quita el otro. Son el mismo rango con distinto alcance, y tenerlos juntos no suma un solo permiso.
3. **La jerarquía no se configura, se escribe** (#37, #89, #123). Cada endpoint enumera sus roles: `hasAnyRole('ADMIN','OWNER')`. **No se registra un `RoleHierarchy`**, porque haría que el permiso de un endpoint dependa de una configuración lejana — que es justo lo que #37 evita, y la forma exacta de la CVE de #123.

### La matriz

| Capacidad | `Admin` | `Owner` | Dónde |
|---|---|---|---|
| Toda la superficie de administración de F1–F3: mesas, catálogos, archivos, bandeja, solicitudes, configuración | sí | sí | F1 y F3 |
| Otorgar y quitar `Player` y `Master` | sí | sí | F3.1 |
| **Otorgar y quitar `Admin` y `Owner`** | **no** | sí | F3.1 |
| Bloquear y desbloquear una cuenta (#84) | sí | sí | F3.1 |
| **Bloquear a un `Admin` o a un `Owner`** | **no** | **no** | F3.1 |
| Aprobar un pedido de `approval_requests` (#42, #90) | sí | sí | F3.2 |
| Reservar un ítem de la bandeja (#100) | sí | sí | F3.3 |
| Editar `system_settings` (#141) | sí | sí | F3.5 |
| **Consultar `audit_logs`** (#92) | no | sí | F6 |
| **Borrado físico de archivos** (#66) | no | sí | F6 |
| **Migrar una cuenta** (#83) | no | sí | F6 |
| Iniciar «ver como» (#140) | sí | sí | F6 |
| Ser objeto de «ver como» (#140a) | **no** | **no** | F6 |

**Leído de una vez: en F3 la diferencia es exactamente una — quién puede otorgar el rango.** Todo lo demás que separa a un owner necesita `audit_logs`, y por eso es F6: #140 lo dice sin rodeos para «ver como», y el borrado físico y la migración de cuenta son de la misma clase. Un owner, hasta F6, usa la superficie de admin completa y nada más (#169).

### Las tres decisiones que la matriz abre

Se contestan **antes** de escribir F3.1, que es el paso 0 de `plan-desarrollo.md` §7.

| Pregunta | Recomendación | Por qué |
|---|---|---|
| **¿Un `Admin` puede crear otro `Admin`?** | **No**: `Admin` y `Owner` los otorga y los quita solo un `Owner` | #169 los hace un rango con dos alcances. Un rango que se replica a sí mismo sin nadie por encima no es un rango: el primer admin comprometido puede fabricar los que quiera, y no queda ninguna autoridad que lo deshaga |
| **¿Un `Admin` puede bloquear a otro `Admin`?** | **No**, y un `Owner` tampoco puede bloquear a un `Owner` | Es la misma forma que el límite (a) de #140, que ya prohíbe «ver como» sobre `Admin` y `Owner`: entre pares no hay autoridad, y el bloqueo es irreversible desde el lado del bloqueado — no puede entrar a pedir que lo desbloqueen |
| **¿Puede quedar la plataforma sin `Owner`?** | **No**: quitarse el propio `Owner`, o quitárselo al último que queda, responde `409` | Un sistema sin nadie que pueda otorgar `Owner` no tiene forma de recuperarse desde adentro. Es la única invariante global de roles, y como MySQL no la puede expresar, va con test de integración igual que el `Primary` único de #73 |

## 4. Las cinco rebanadas

Cada una se cierra con las ocho de `plan-desarrollo.md` §6 en su alcance — **incluido el punto 5 nuevo**: lo que construyó y no verificó se escribe, porque quien lo verifica es F4 (#250).

Las secciones **Backend** y **Frontend** de cada rebanada son, literalmente, el encargo de A1 y de A2 (`plan-desarrollo.md` §7).

---

### F3.1 — Roles, bloqueo y `/admin/users`

**Por qué primero:** las cuatro rebanadas que siguen preguntan «¿quién puede esto?» en cada endpoint que escriben, y hoy la respuesta está repartida en anotaciones sueltas sin un lugar que la afirme. Y porque la matriz de §3 no es documentación si nada la hace cumplir.

**Backend** — en `users/`:

- `UserRoleService`: otorgar y quitar roles, con **la exclusión `Admin`/`Owner`** (#169) aplicada acá y no en el controller — el otorgamiento es la regla, no la puerta.
- **Quién otorga qué**, según §3: un `Owner` mueve los cuatro roles; un `Admin` mueve `Player` y `Master` y nada más. El intento de un admin sobre `Admin` u `Owner` es `403`, no `400`: es una cuestión de quién sos, no de qué mandaste.
- **La plataforma nunca se queda sin `Owner`**: `409` con código propio al quitar el último. Es la invariante global de la fase.
- Bloqueo y desbloqueo de cuentas (#84), con `@CacheEvict` sobre la caché de #128 — un bloqueo que tarda 60 s en aplicar es un bloqueo que no bloquea.
- `UserSearchSpecification` ya existe desde #164; `/admin/users` la reusa y le suma el filtro por rol y por estado.
- **Auditoría**: no hay `audit_logs` hasta F6, así que cada cambio de rol y cada bloqueo se registran en su propia fila con motivo obligatorio. Dónde vive esa fila es una decisión de la rebanada — la recomendación es **no** inventar una tabla que F6 va a reemplazar, y usar `approval_requests` si F3.2 ya existiera; como no existe todavía, el orden importa y se resuelve al escribir el contrato.

**Frontend** — `routes/admin/AdminUsersPage.tsx`:

- La tabla ancha de `frontend-diseno.md` §5.b: en móvil deja de ser tabla.
- Los roles como chips, con el orden que #165 fijó. **Los botones que un admin no puede usar no se muestran** — principio 2 de `frontend-diseno.md` §1 — y el owner ve los cuatro.
- Bloquear pide motivo, y el diálogo dice qué implica: la persona no entra más, y sus datos se conservan (#84).

**Se prueba:** un owner asciende a alguien a admin; ese admin abre `/admin/users`, puede dar el rol de master y **no encuentra ninguna forma** de dar el de admin; el owner intenta quitarse su propio rol y recibe el `409`.

#### ✅ Terminada

Las tres decisiones de §3 quedaron implementadas **en el service y no en la puerta**, y la matriz tiene por fin lo que §7 exigía: una prueba que la recorre. `AdminUserApiIT.everyRouteAnswersTheSameToAnAdminAndToAnOwner` llama **las siete rutas con los dos rangos** y exige la misma respuesta, así que el `hasRole('ADMIN')` que deja al owner afuera ya no puede entrar sin romper algo.

**Lo que la rebanada descubrió de sí misma**, y no sabía al empezar:

- **La invariante del último owner se rompía de verdad.** Dos owners revocándose a la vez —o dos promociones a `Admin` simultáneas, por la exclusión de #169— dejaban la plataforma en **cero owners**. Ningún unitario podía verlo y en el código se lee bien; hizo falta concurrencia real contra MySQL. El arreglo son **dos** bloqueos y el segundo no es opcional: bajo `REPEATABLE READ` un `count(*)` plano responde desde el snapshot previo a la espera. Todo en **#252**.
- **`PlatformRole` cruzaba HTTP sin `@JsonValue`**, así que todo grant y revoke desde la pantalla respondía `400` mientras la respuesta sí decía `"Player"`. Ningún test de ninguno de los dos lados podía encontrarlo. Arrastró un `500` que debía ser `400` para cualquier cuerpo malformado, en cualquier endpoint (**#253**).
- **`TestDataService` no limpiaba las tablas nuevas**, y como la limpieza es una sola transacción, el choque de FK hacía rollback entero y la corrida siguiente arrancaba con la base llena (**#254**).

**Suites al cerrar, salida real:** `./mvnw test` 409/409 · `./mvnw verify` 120 ITs en 16 clases, 0 fallos · `npx tsc -b` limpio · `npm run test` 315/315 · `npm run test:e2e` 54/54 · `npm run format` sin reescrituras. Cero regresiones en las 12 clases de IT preexistentes.

> **Nota de entorno:** `./mvnw verify` **no corre de fábrica** en esta máquina. Testcontainers no descubre el socket de colima aunque `docker info` funcione, y las 16 clases fallan con «Could not find a valid Docker environment», que parece código roto. Hay que pasarle `DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"` y `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.

**Deuda de revisión — para F4** (punto 5 de `plan-desarrollo.md` §6, #250):

| Sin verificar | Por qué queda |
|---|---|
| Los cuatro estados de cada pantalla nueva, y el layout de ficha en un viewport de 375 px | Es F4 por diseño (#250) |
| El `<Dialog>` del historial en móvil, que según #138 debería ser sheet desde abajo | No se miró |
| Contraste de los chips de rol (`text-brand-fg` sobre `bg-raised`) en los dos temas | No se pasó por el medidor de `design/build.py` |
| La evicción de caché por la rama `afterCommit`, medida como caché | Se verificó su **efecto** por HTTP —un owner degradado es rechazado en el request inmediato— pero ningún test observa la caché misma |
| Idempotencia de dos revocaciones simultáneas sobre la **misma** persona | Borde conocido y aceptado a propósito: responde `200` y `409`, no `200` y `200`. Documentado en `@implNote` y en **#252** |

**Inventario de archivos** — 20 nuevos en `backend/` (`users/`: `AdminUserController`, `AdminUserService`, `UserRoleService`, `UserRoleChange`, `UserRoleChangeAction`, `UserRoleChangeRepository`, `UserRoleGrant`, `UserStatusChange`, `UserStatusChangeRepository`; `users/dto/`: los siete records; `db/migration/V11__user_admin_changes.sql`; tests: `UserRoleServiceTest`, `AdminUserServiceTest`, `PlatformRoleJsonTest`, `UserSearchFieldTest`, `UserRoleServiceIT`, `AdminUserApiIT`), 20 nuevos en `frontend/` (`features/users/`: `roles.ts`, `adminErrors.ts`, `api/adminUsersApi.ts` + siete hooks, `hooks/useUserAdminCapabilities.ts`, cinco componentes y sus tests; `routes/admin/AdminUsersPage.tsx` + test; `e2e/admin-users.spec.ts`), y los modificados que el commit de la rebanada lista.

**Tres archivos de producción se tocaron por infraestructura de pruebas**, y se anota a propósito: `TestLoginController` (gana `asOwner` — sin eso el flujo de la rebanada no tenía actor), `TestDataService` y el `DevPanel` con su cuarta fila. Todo en **#254**.

---

### F3.2 — `approval_requests`: un mecanismo, cuatro pedidos

**Por qué acá:** es la pieza de la que cuelgan la bandeja (F3.3) y los dos flujos de F3.4. Construirla después sería construir sus consumidores contra algo que no existe.

**Backend** — paquete `approvals/`:

- `ApprovalRequest` (`@Entity`), `ApprovalRequestType`, `ApprovalStatus`, `ApprovalService`, `ApprovalController`.
- **Referencia polimórfica** `entity_type` + `entity_id`, **sin FK real** (#78). El precio está escrito en la decisión y hay que pagarlo entero: **el service valida que la entidad exista antes de insertar**, no se mapea como `@ManyToOne`, y hace falta **una verificación periódica de referencias huérfanas** — es la clase de problema que este proyecto ya sufrió con `table_files` apuntando a archivos borrados.
- Los cuatro tipos de #42 y #90: pausa de mesa pedida por un master, veto pedido por un `Secondary`, pedido del rol de master, pedido de que se abra una mesa, y la petición general al equipo.
- **Justificación obligatoria** en el pedido y en la resolución (#42).
- Pedir que se abra una mesa **desemboca en #72**: el admin la crea en `Unassigned` y le asigna master. Los dos flujos son el mismo circuito por sus dos puntas (#90).

**Frontend** — `routes/admin/AdminRequestsPage.tsx` (`/admin/requests`), y el lado de quien pide: el formulario del pedido vive en la pantalla que lo provoca, no en una pantalla de «hacer un pedido».

**Se prueba:** un jugador pide el rol de master; el pedido aparece en `/admin/requests` con su motivo; un admin lo aprueba y el rol queda otorgado por el `UserRoleService` de F3.1, no por una segunda ruta que haga lo mismo.

---

### F3.3 — La bandeja compartida, y `/admin/tables` completo

**Backend** — en `approvals/` más lo que cada fuente aporta:

- **La bandeja es una vista, no una copia** (#100). `claimed_by` / `claimed_at` sobre `approval_requests`, `comments`, `system_feedback` y `game_tables`; el ítem reservado desaparece para el resto y una reserva sin resolver se libera sola a los 15 minutos.
- El precio aceptado en #100: **un `UNION ALL` de cuatro consultas pequeñas**, normalizadas a un DTO común en el service. No se construye una tabla `admin_queue` — sería la denormalización mantenida a mano que #11 eliminó.
- `POST`/`DELETE` de `claim`, idempotentes para el mismo admin y `409` si lo tiene otro. Job de liberación.
- **`comments` y `system_feedback` no existen hasta F5**: la bandeja nace con dos de sus cuatro fuentes y el `UNION` se escribe para que sumar la tercera sea agregar una consulta. Eso se dice acá y no se descubre en F5.
- `/admin/tables` pasa a listar **todas** las mesas en cualquier estado, con filtros y `?q=` (#176): `DEFAULT_ADMIN_REVIEW_STATUSES` deja de ser el default. El buscador se monta sobre `GameTableSearchField`, que F2.1 ya construyó — le sobra agregar `/table_status` y `/table_master`.

**Frontend** — `routes/admin/AdminQueuePage.tsx` (`/admin/queue`) y la reescritura de `/admin/tables`:

- Aprobar y Pedir cambios **se mudan** a la bandeja (#176). Es una mudanza, no una copia: quedarse en las dos pantallas sería tener dos lugares que hacen lo mismo con reglas distintas.
- El vacío de la bandeja es una **buena noticia**, igual que el de `/master` (#136): «nada espera una acción», no una pantalla rota.
- La reserva se ve: quién tomó cada ítem y hace cuánto.

**Se prueba:** dos admins abren `/admin/queue`; uno reserva una mesa y al otro le desaparece de la lista sin recargar la página a mano; a los 15 minutos sin resolver, vuelve.

---

### F3.4 — Pausa y veto

**Por qué acá:** son los dos pedidos de #42 que cambian el estado de algo, y los dos necesitan `approval_requests` y la bandeja. Además cierran tres huérfanos que F1.7 relevó y no pudo tocar.

**Backend**:

- **Pausa pedida por un master** (#32): `PauseRequested` deja de ser un estado que nada produce. El master pide, el admin aprueba, y recién ahí la mesa entra en `Pause`. `Pause` y `Canceled` exigen justificación, que se registra en `table_status_changes`.
- **La pausa congela la agenda** y reanudar reagenda desde la fecha de reanudación, **volviendo a verificar el choque** (#32, #33, #178, #193). Todo eso ya está construido desde F1.3: lo que F3.4 agrega es la puerta por la que un master lo pide.
- **Veto** (#39, #71): lo aplica el `Primary` de la mesa; un `Secondary` lo pide vía `approval_requests`. Es reversible, y el veto y su levantamiento quedan registrados. `TableRegistrationStatus.Blocked` sale de «fuera de alcance».
- **Y lo que el veto arrastra, que es más que una fila**:
  - El filtro de visibilidad de #29: toda lectura de mesas excluye aquellas donde el actor tenga una postulación `Blocked`, y el detalle por id responde **`404`, no `403`** — un `403` confirma lo que el `404` niega.
  - **La exclusión del vetado en la lectura de archivos**, que #206 dejó anotada con destino a esta fase: lo que una mesa comparte lo lee quien puede ver la mesa, y un vetado dejó de poder verla.
  - El explorador, `/player/tables/:id`, las sesiones, las peticiones y la descarga de archivos: **cada vía de lectura de una mesa tiene que respetarlo**, y son seis. El inventario de cuáles son se escribe antes de tocar la primera.

**Frontend**:

- Botón de pedir pausa en la pestaña Estado del master, y los botones de **pausa y reanudación del admin**, que tienen endpoint desde E2 y ninguna pantalla (#163).
- Veto desde la pestaña Jugadores, con motivo; el `Secondary` ve el mismo botón y lo que manda es un pedido — y la pantalla lo dice antes de apretar, no después.

**Se prueba:** un master pide pausa y un admin la aprueba; el calendario del jugador se congela. Un `Primary` veta a alguien: esa persona deja de ver la mesa en el explorador, recibe `404` en el detalle y `404` en el archivo que antes descargaba.

---

### F3.5 — `system_settings`

**Backend** — en `settings/`:

- La tabla clave-valor con las tres categorías de #141 —parámetros de negocio, límites y cuotas, textos—, el `SettingsService` con **accesores tipados** y el endpoint que devuelve su `record` con nombre propio: **lo dinámico no cruza la frontera HTTP** (regla dura 3).
- **Ningún secreto vive acá** (#141): el HMAC de #94 y las credenciales siguen en el entorno.
- Los valores que dejan de ser constantes: karma inicial, ventana de decaimiento, caducidad de visibilidad (#44), timeout de reserva de la bandeja (#100), tope por archivo, cupo máximo, ventana del feedback (#94) y la justificación por defecto del rechazo automático (#34).
- **Cada cambio se audita** (#141). Sin `audit_logs` hasta F6, aplica lo mismo que F3.1: se registra dónde la rebanada decida, y la decisión se escribe.

**Frontend** — `routes/admin/AdminSettingsPage.tsx` (`/admin/settings`), agrupada por categoría, con el valor actual y el por defecto a la vista.

**Se prueba:** un admin cambia el tope por archivo; la subida siguiente lo respeta sin reiniciar nada, y el cambio queda registrado con quién y cuándo.

## 5. Lo que F3 explícitamente NO construye

Anotado a propósito: un hueco implícito es una sorpresa (`plan-desarrollo.md` §1).

| Queda fuera | Dónde vive |
|---|---|
| `audit_logs` y `/owner/audit` (#92) | F6 |
| Borrado físico de archivos (#66) y `/owner/storage` | F6 |
| Migración de cuenta (#83) y `/owner/users/:id/migrate` | F6 |
| «Ver como» (#140) — **se construye completa o no se construye**, y sin auditoría no hay responsable | F6 |
| La bandeja **en vivo**: en F3 funciona por HTTP, el WebSocket es F6 (#101) | F6 |
| `comments` y `system_feedback` como fuentes de la bandeja | F5 |
| `/admin/moderation` y `/admin/feedback` | F5 |
| **La verificación de todo lo anterior**: los cuatro estados de cada pantalla nueva, el mapa de navegación y la matriz de roles probada | **F4** (#250) |

## 6. Verificación de punta a punta

Se prueba **contra el backend y el frontend que ya están corriendo** — no se levantan instancias paralelas.

```bash
cd backend && ./mvnw test          # unitarios, sin Docker
cd backend && ./mvnw verify        # + Testcontainers (colima arriba)
cd frontend && npx tsc -b          # typecheck strict
cd frontend && npm run test        # Vitest
cd frontend && npm run test:e2e    # Playwright contra el backend real
cd frontend && npm run format      # prettier del repo (#174)
```

**El camino manual completo al cerrar F3**, con el `DevPanel` (#158) para armar los actores:

1. Un owner asciende a alguien a admin, y ese admin no encuentra forma de ascender a nadie más.
2. El owner intenta quitarse su propio rol → `409`, la plataforma no se queda sin owner.
3. Un jugador pide el rol de master; el pedido llega a `/admin/requests` y el admin lo aprueba.
4. Un master pide pausa de su mesa; el pedido aparece en `/admin/queue`, otro admin lo reserva y al primero le desaparece.
5. El admin aprueba la pausa; el calendario de los jugadores se congela y reanudar vuelve a verificar el choque.
6. Un `Primary` veta a un jugador: deja de ver la mesa en el explorador, recibe `404` en el detalle y `404` al abrir un archivo que la mesa comparte.
7. Un `Secondary` pide un veto y el `Primary` lo resuelve.
8. Un admin cambia el tope por archivo en `/admin/settings` y la subida siguiente lo respeta.
9. Un admin bloquea una cuenta; esa persona no puede entrar, y sus mesas y su historial siguen existiendo (#84).

## 7. Riesgos conocidos

- **La referencia polimórfica de #78 es la pieza que la base no puede cuidar.** Dos cosas van con ella o no va: la validación en el service antes de insertar, y el chequeo periódico de huérfanas. Sin la segunda, el problema aparece meses después y sin forma de reconstruir qué apuntaba a qué.
- **El veto toca seis vías de lectura y es fácil cerrar cinco.** Un vetado que no ve la mesa pero sí descarga su archivo es el bug que #206 anticipó por escrito. El inventario de vías se escribe antes de tocar la primera, y cada una lleva su test.
- **`hasRole('ADMIN')` en vez de `hasAnyRole('ADMIN','OWNER')` deja al owner afuera y nadie lo nota**, porque en desarrollo el actor de prueba suele ser admin. La matriz de §3 tiene que terminar en una prueba que la recorra, no solo en esta tabla.
- **Tres cosas se llaman «owner» en este proyecto** y F3 las toca a todas: el rol de plataforma `PlatformRole.OWNER` (#67), el dueño de una mesa que #71 renombró a `Primary`, y «el owner» de #66 que es el dueño de la plataforma. #39 sigue escrita con el nombre viejo —dice que el veto lo aplica «el `Owner`» y quiere decir el `Primary`—; `modelo-datos.md` §5 ya está corregida y es la que manda.
