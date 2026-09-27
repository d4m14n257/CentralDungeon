# Plan de desarrollo

> En qué orden se construye, qué se rescata del legacy y cuándo una fase está terminada.
>
> El *qué* del schema está en `modelo-datos.md`, el *cómo* del código en `arquitectura.md`, las pantallas en `frontend-diseno.md` y el *por qué* de todo en `decisiones.md`.

## 1. Criterio

**Una fase, un actor** (#177). Cada fase entrega la experiencia **completa** de quien usa el sistema —el master, el jugador, el admin— en vez de cerrar un subsistema de punta a punta. El orden es **quien produce antes que quien consume, y quien administra al final**: sin mesas no hay nada que jugar, y sin mesas ni jugadores no hay nada que moderar.

Tres reglas que salen de ahí:

1. **La fase arrastra los subsistemas que su actor necesita**, no al revés. Los catálogos y los archivos entran con el master porque su mesa los pide, no como fases propias: un subsistema construido sin nadie que lo use se diseña a ciegas y se corrige después.
2. **Cada capacidad llega con el mínimo del otro lado para poder probarla** de punta a punta. Si el master publica una agenda, el jugador tiene que poder verla en la misma fase. Es la trampa que E1 documentó cuando `/my/tables` faltaba y a un jugador aceptado no le quedaba dónde ver su mesa.
3. **Lo que una fase no construye se dice explícitamente**, con la fase donde vive. Un hueco anotado es una decisión; un hueco implícito es una sorpresa.

Y tres cosas que no se negocian entre fases:

- **Nada se da por terminado sin sus tests** (regla dura 7). Cada regla de skill `modelo-datos` §5 que entre en una fase llega con su test unitario.
- **Las invariantes que MySQL no puede garantizar necesitan test de integración**, no unitario: un solo `Primary` vivo por mesa (#73) y una sola postulación activa por par (#28). Son las dos que se rompen con concurrencia.
- **La fase que estrena una entidad decide y construye su borrado** (#175). No se deja "para más adelante": una entidad que se puede crear y no se puede sacar de encima obliga a inventarle un final falso —cancelarla, vaciarla, renombrarla— y ese parche después es más caro que la decisión. Decidir el borrado incluye decidir **si lo hay**: para las mesas, borrar solo aplica a lo que nunca fue público, y lo demás se cancela a propósito.

## 2. Qué se rescata del legacy

### Backend — `legacy/backend-node/`

Express + TypeScript, 6 routers, ~35 endpoints, 1735 líneas de handlers (`tables.ts` sola tiene 1065).

**No existe**: autenticación, comentarios, notificaciones, sesiones, peticiones, aprobaciones ni auditoría. Lo que hay cubre mesas, catálogos, archivos de preparación y parte de usuarios.

**Se rescata**: el inventario de endpoints como **checklist funcional** —dice qué necesitaban las pantallas reales— y algunas queries como referencia de intención, como el CTE recursivo de catálogos que quedó comentado en `app.ts` y reveló que `parent_id` eran sinónimos (#53).

**No se rescata código.** El stack es otro y el que había arrastraba los problemas del inventario de `arquitectura.md` §5.

### Frontend — `legacy/frontend-next/`

**Es JavaScript con MUI, no TypeScript**: `jsconfig.json`, archivos `.js` y `.jsx`. 13 páginas, ~45 componentes, 5 contexts con datos de servidor adentro, 7 wrappers de API.

**Se rescata**: el mapa de pantallas, que es el punto de partida del sitemap nuevo, y la descomposición en modales por acción. Mapeo pantalla por pantalla y componente por componente en `frontend-diseno.md` §6.

**No se rescata código.** Dos cosas ya presentes sí son continuidad y no novedad: `react-hook-form` + `zod` (siguen) y `@tinymce/tinymce-react`, que confirma que el texto enriquecido de #62 ya estaba en camino aunque se cambie de editor.

## 3. Lo ya construido antes de las fases

> **La numeración `E` es historia y no continúa** (#177). Estas cuatro etapas se construyeron con el plan anterior, que ordenaba por subsistema. El detalle de cada una —alcance, criterio de terminado y cómo se cerró— se colapsó en F4.0 a este resumen y queda completo en git.

| Etapa | Qué entregó |
|---|---|
| **E0** — Diseño del frontend | `frontend-diseno.md`: principios, navegación por contexto, sitemap, wireframes, inventario de componentes y mapeo legacy→nuevo. Sin código |
| **E0.5** — Sistema de diseño | Los tokens en `design/build.py`, transcriptos al `@theme` (#118, #130, #131); las 28 rutas del sitemap diseñadas en los dos temas; el contraste medido en cada build (30 pares, WCAG AA) |
| **E1** — Rebanada usable | Los dos scaffolds; **toda la seguridad** (OAuth2 con Discord y membresía al guild, access token corto + refresh rotativo en cookie `httpOnly`, CSRF solo en `/auth/refresh`, JWT que afirma identidad y no autorización, pertenencia en cada recurso — #121, #122, #125, #127, #128); y el primer flujo de punta a punta: entrar, ver mesas, postularse, ser aceptado o rechazado. **Estrenó el doble de login de pruebas** (`TestLoginController`, `TestDiscordController`, #143): no hay una aplicación de Discord registrada para desarrollo, y sin el doble no se puede probar nada autenticado |
| **E2** — Ciclo de vida de la mesa (parcial) | La máquina de estados de la mesa con su historial, el wizard de creación y la pestaña de estado, y el lenguaje de búsqueda de toda la app (#164). El resto de lo que prometía se redistribuyó en las fases |

### Dónde fue a parar lo que prometían las etapas viejas


Para leer las decisiones ya escritas, que citan la numeración anterior:

| Etapa vieja | Dónde vive ahora |
|---|---|
| E2 sub-rebanada 2 — `approval_requests`, bandeja, veto | **F3** |
| E2 sub-rebanada 3 — catálogos | **F1 completo**: consumo, propuesta y administración (#179) |
| E2 sub-rebanada 4 — `system_settings` | **F3** |
| E2 sub-rebanada 5 — `/admin/users`, `/master`, `/my/history`, `/admin/requests`, `/admin/tables` completo | `/master` en **F1**, `/my/history` en **F2**, el resto en **F3** |
| E3 — sesiones y peticiones | Lo que publica el master en **F1**; lo que entrega el jugador en **F2** |
| E4 — archivos | **F1** (subsistema, preparación, `/my/files`, cajones #232/#233, formularios del pedido #236) y **F2** (archivo de personaje en la postulación: `registration_files` es el cajón `PlayerApplication` y la cuarta fuente de usos) |
| E5 — comentarios y karma | **F5** |
| E6 — tiempo real, auditoría y owner | **F6** |

## 4. Fases

**Seis, desde #250**: entró **F4 Revisión** entre Admin y Comunidad, y las dos que venían detrás corrieron un número.

| Fase | Qué entrega | Estado |
|---|---|---|
| **F1 — Master** | La mesa completa, de la creación al cierre | ✅ **Hecha, sin revisar** |
| **F2 — Jugador** | Todo lo que el jugador hace con lo que el master publicó | ✅ **Hecha, sin revisar** |
| **F3 — Admin y Owner** | La comunidad se administra, y la línea entre los dos roles queda escrita | ✅ **Hecha, sin revisar** |
| **F4 — Revisión** | Documentación, mapa de la interfaz, matriz de roles, integridad y seguridad — y la revisión del cliente | ⏳ |
| **F5 — Comunidad** | Comentarios y karma, con anonimato real | ⏳ |
| **F6 — Operación** | Tiempo real, auditoría y el panel exclusivo del owner | ⏳ |

**«Hecha, sin revisar» es un estado real y no un eufemismo.** Quiere decir: sus reglas están implementadas, cada una con su test, las suites en verde, y **nadie miró todavía el producto terminado** — si cada pantalla es alcanzable navegando, si tiene sus cuatro estados, si la matriz de roles dice lo que promete. Eso es F4, y cada fase llega ahí con su deuda de revisión escrita (§6, punto 5).

### F1 — Master

**La mesa completa, de la creación al cierre.** Es la fase que produce lo que todo lo demás consume.

> **El documento de implementación de F1 se borró en F4.0** (queda en git); su deuda de revisión pasó a `fase-4-revision.md` §2.1.

**Backend** — Catálogos que la mesa usa: `systems`/`tags`/`platforms` con `canonical_id` y grupos de sinónimos de profundidad 1 (#59), lectura, **propuesta** al crear y **su administración completa** —aceptar, clasificar, fusionar, separar, dar de baja— que se adelantó desde F3 (#179). Un valor en `Created` no filtra ni se muestra a los jugadores (#57), y la mesa muestra siempre el alias que le puso su master (#58). `TableTypeController`, que falta: `V2__seed.sql` siembra los tipos y hoy no hay forma de listarlos. `table_schedules` con la agenda semanal y el **choque de horarios** (#178): un master no se compromete dos veces en la misma franja, nadie se postula ni es aceptado en una mesa que se pisa con otra donde ya juega, y las postulaciones sin resolver que chocan se avisan — lo que trae consigo el **retiro de una postulación**, adelantado desde F2. `table_sessions` materializadas al pasar a `Opened` a partir de `start_date` + agenda + `total_sessions` (#26, #33), con asistencia por sesión (#36). `closed_at` sellado al cerrar la mesa (#180), que E2 dejó sin implementar. `table_tasks` publicadas por el master, que notifican a sus destinatarios (#77), con entregas que se acumulan y no bloquean (#63, #70, #76). **Archivos**: `files` con nombre físico por id (#80), `content_hash` para deduplicar, `file_type`, `public_audience` (#64) y `last_used_at`; `StorageService` detrás de interfaz (#15), compresión al guardar y job de retención por desuso (#75); `table_files` sin duplicar el archivo (#79).

**Frontend** — Wizard de creación completo: tipo, sistema, tags, plataformas, fecha de inicio, duración, sesiones, cupo y agenda. `ScheduleEditor` en hora local, con la advertencia de choque en el explorador y el motivo del bloqueo en el botón de postularse. `/admin/catalogs` (#179). Sesiones y asistencia en `/master/tables/:id`. Publicar peticiones y ver entregas. `FilePicker` con subida o reutilización del historial (#65). Co-masters desde la pantalla del master — `POST /{id}/masters` existe desde E2 y nunca tuvo interfaz. El dashboard **`/master`** (#136).

**El mínimo del jugador para poder probar**: en `/tables/:id` y `/my/tables/:id`, lectura de la agenda, las sesiones y los archivos públicos, más las peticiones que le aplican **con su entrega** — texto y archivos. Entregar se adelantó de F2 a F1.5 (#210): sin nadie que pueda entregar, el padrón de faltantes muestra a todos como faltantes siempre y la regla que más importa del subsistema —las entregas se acumulan (#76)— queda sin ejercitar. El resto del lado del jugador sigue siendo solo lectura.

**No entra**: pedir pausa ni veto (F3, necesitan `approval_requests`), el archivo de personaje en la postulación (F2), karma (F5). ~~`/my/files`~~ — **adelantada a F1**: es la pantalla que le da sentido al historial de #65, y sin ella la reutilización solo existe dentro del diálogo de adjuntar. Llegó junto con la clasificación por flujo (#232, #233) y con los archivos del pedido (#236), que no existían.

**Lo que se ajustó después, sobre lo ya entregado**: el buscador quedó **uno solo de verdad** (#240) — elegir un comando de la lista escribe el mismo texto que tipearlo, **Enter** es lo único que cierra un criterio en chips, y `SearchQueryValue` perdió `activeField`; el cableado (estado, string canónico, debounce, `?q=`) se unificó en `useSearchQuery` y cada feature declara sus comandos en su `searchFields.ts`; la ayuda `basics.search` recibe los comandos de la caja que la abrió y arma con ellos su lista y sus ejemplos. `/my/files` pasó a ser **de players y masters** (#241): quien no tiene ninguno de los dos roles no la ve en el menú y la pantalla se lo dice — la regla vive en `useHasPersonalLibrary` y sigue la forma de `useAvailableContexts`, con `hasManagedTables` para el co-master de #135. Y su parte visual se acotó (#242): el buscador es el único filtro, sin la fila de toggles de cajón, con el placeholder diciendo el criterio básico y nada de comandos. **Cuidado al leer #164 y #239**: las dos filas están corregidas por #240 y lo dicen al final; #233 y #237 apuntan a #242 y #241.

**Entrega**: un master arma su mesa entera y la lleva hasta el final, y un jugador ve todo lo que publicó.

### F2 — Jugador

**Todo lo que el jugador hace con lo que el master publicó.**

> **El documento de implementación de F2 se borró en F4.0** (queda en git); su deuda de revisión pasó a `fase-4-revision.md` §2.1.

**F1 se llevó por delante buena parte de lo que este párrafo prometía**, y lo que queda es lo que sobrevivió. Se adelantaron: entregar respuestas a las peticiones, entera y con archivos (#210); `/my/files`, que además creció con los cajones (#232, #233, #237, #241, #242); retirar una postulación, que el choque de horarios exigía (#178); y `/my/tables/:id` completo —agenda, sesiones, asistencia y peticiones—, que era el mínimo del jugador para poder probar F1.

**Backend** — `registration_files` para el archivo de personaje en la postulación (#60 uso 2), con su cajón `PlayerApplication` y la cuarta consulta de usos (#232, #233). Búsqueda del explorador resolviendo grupos de sinónimos (#54, #56) — el backend de F1.1 ya los resuelve y nada los consume. Las cinco reglas de **visibilidad de perfiles** de skill `modelo-datos` §5, ninguna implementada todavía (#41, #44, #45, #47), con la asistencia agregada sobre todas las mesas (#137).

**Frontend** — Filtros del explorador por sistema, tag y plataforma: es donde el buscador estrena `/tag`, el caso que motivó el diseño de #164. Archivo de personaje al postularse, sobre el `FilePicker` y la subida diferida de F1 (#238), con el paso de revisión que una postulación no editable obliga. **`/player/profile`** y **`/player/users/:id`** con lo que exista; el karma llega en F4. **`/player/history`** (#133), y con él `/player/my-tables` acotada a lo vivo.

**Y cierra tres deudas de F1**: los tipos de notificación `ScheduleConflict`, `SessionScheduled` y `SessionCanceled` no llevan a ningún lado, y sus destinos son pantallas de este contexto.

**Entrega**: el jugador vive la mesa dentro del sistema, no solo se postula.

### F3 — Admin y Owner

**Revisión, moderación de flujo y administración — y la línea entre los dos roles que administran.**

> **El documento de implementación de F3 se borró en F4.0** (queda en git); lo normativo —la matriz `Admin`/`Owner`, las reglas de cada rebanada y los riesgos— vive en la skill `modelo-datos`, `references/roles-y-alcance.md`, y su deuda de revisión en `fase-4-revision.md` §2.1.

**Backend** — El service que otorga roles, con la exclusión `Admin`/`Owner` que #169 dejó pendiente, y el bloqueo de cuentas (#84). `approval_requests` como mecanismo único para todo pedido con aprobación, con reserva (#42, #78, #90, #100). Pausa pedida por un master (#32) y veto acotado a la mesa, aplicado por el `Primary` y pedible por un `Secondary` (#39, #71) — con la exclusión del vetado en la lectura de archivos que #206 dejó anotada para esta fase. **La administración de catálogos ya no está acá**: se adelantó a F1 (#179) — dejarla en esta fase le abría a F1 el hueco de proponer valores que nadie podía aceptar. `system_settings` (#141): la tabla clave-valor, el `SettingsService` con accesores tipados y la auditoría de cada cambio; los valores que hoy son constantes —karma inicial, justificación del rechazo automático (#34), ventana de visibilidad (#44)— pasan a leerse por el service.

**Frontend** — **`/admin/users`**, con los roles y el bloqueo. **`/admin/queue`**, la bandeja compartida con reserva; al nacer, Aprobar y Pedir cambios **se mudan ahí** desde `/admin/tables` (#176). **`/admin/tables`** completo: todas las mesas, cualquier estado, filtros y `?q=` (#176), con los botones de pausa y reanudación que hoy tienen endpoint y ninguna pantalla (#163). **`/admin/settings`** y **`/admin/requests`** — `/admin/catalogs` llegó en F1 (#179).

**La línea entre `Admin` y `Owner` se traza acá y queda escrita** (#67, #89, #169). En F3 la diferencia es **exactamente una**: quién puede otorgar el rol del otro. Todo lo demás que separa a un owner —auditoría, borrado físico, migración de cuenta, «ver como»— es **F6**, y hasta entonces un owner usa la superficie de admin completa y nada más (#169). La matriz vive en `roles-y-alcance.md` §3, en la skill `modelo-datos`.

La bandeja funciona **por HTTP** en esta fase; el vivo es F6.

**Entrega**: la comunidad se administra desde la aplicación, y quién puede qué está escrito en un solo lugar.

### F4 — Revisión

**No construye producto. Verifica las tres fases anteriores juntas, y termina con la revisión del cliente.**

> **El detalle está en [`fase-4-revision.md`](fase-4-revision.md)**: las seis rebanadas y el instrumento de cada una.

Nace de #250, que sacó la revisión del final de cada fase. El motivo, en una frase: **una costura no se puede mirar hasta que existen sus dos lados.** F1.7 revisó F1 contra F1 y encontró lo que podía; la matriz de roles, la seguridad entre actores y el mapa completo de la interfaz no tienen respuesta hasta que los cuatro roles están construidos.

- **La documentación primero** (F4.0): se borra lo que ya cumplió su función, arquitectura y modelo de datos pasan a ser skills del repo, y ninguna cita del código apunta a un documento vencido.
- **El mapa de la interfaz**, entero: cada ruta del sitemap con su guard, desde qué pantalla se llega y qué sale de ella — **y el inventario de lo que quedó flotando**: endpoints sin pantalla, hooks montados en cero lugares, valores de enum que nada produce, pantallas alcanzables solo escribiendo la URL.
- **La matriz de roles**, verificada con tests y no leída de un `@PreAuthorize`, incluida la pertenencia: el rol correcto sobre el recurso ajeno.
- **Integridad**: las invariantes que MySQL no sostiene, las referencias huérfanas que #78 obliga a vigilar, y la coherencia del borrado lógico en todos los caminos de lectura.
- **Seguridad**: IDOR por recurso, las vías de lectura de un archivo, la lista blanca del sanitizador, el CSRF de `/auth/refresh`, y el `404` del vetado que nunca debe ser `403`.
- **La revisión mano a mano del cliente**, con su registro de hallazgos y cada uno triado: bug o alcance diferido.

**Entrega**: se sabe qué hay construido, quién lo alcanza y qué está roto — con nombre y apellido.

### F5 — Comunidad

**Comentarios y karma**, que es lo que convierte al sistema en una comunidad y no en un calendario.

**Backend** — `comment_drafts` con autor → `comments` anónima al confirmar (#48, #49), `comment_quotas` con token HMAC (#82), purga de borradores expirados (#50, #52), moderación de todos los comentarios (#51). `KarmaService` con la fórmula de #96 y sus dos disparadores (#97). `system_feedback` + `feedback_quotas` con el token rotativo por hora (#93, #94, #95).

**Frontend** — Escribir el borrador durante la mesa y confirmarlo al cerrarse. Karma y comentarios en `/profile` y `/users/:id`, con la ventana de visibilidad de #44 y las restricciones de #41, #45 y #47. **`/admin/moderation`** y **`/admin/feedback`**.

**Entrega**: karma funcionando, con anonimato real.

### F6 — Operación

**Lo que hace la plataforma operable, y el panel exclusivo del owner.**

**Backend** — WebSocket + STOMP con el JWT en el frame `CONNECT` y autorización por destino (#101). `audit_logs` con diff de columnas cambiadas (#92). Borrado físico de archivos (#66) y migración de cuenta (#83).

**Frontend** — Notificaciones push, bandeja de admin en vivo, y las tres del owner: `/owner/audit`, `/owner/storage` y `/owner/users/:id/migrate`.

**"Ver como" (#140) va acá, y no en F3.** No es preferencia de orden: `audit_logs.impersonation_id` es FK a `impersonation_sessions`, y sobre todo, **sin la auditoría la función es exactamente la versión sin responsable que #140 descartó** — un admin actuando con la identidad de otro y nadie capaz de reconstruir qué pasó. Se construye completa o no se construye: sesión con motivo obligatorio, caducidad a los 30 minutos, bloqueo sobre `Admin` y `Owner`, bloqueo de todo lo irreversible, **exclusión total de lo que toque comentarios** (#43, #45), y notificación inmediata a la persona. Por lo mismo esperan acá el borrado físico y la migración de cuenta: son de la misma clase.

**Un owner usa toda la superficie de admin desde que existe** (#169); lo que espera a F6 es lo exclusivo suyo.

**Repaso final**: los tipos de notificación que falten y las rutas del sitemap que hayan quedado sin construir.

**Entrega**: plataforma operable.

## 5. Motor de notificaciones

Dos cosas distintas que conviene no confundir:

**Notificación personal** (jugador, master). Informativa, con destinatario, leída/no leída. Es la tabla `notifications`: una fila por persona.

**Bandeja compartida de admins.** No son notificaciones: son **ítems de trabajo** que ya viven en sus tablas — `approval_requests` pendientes, `comments` en `Under review`, `system_feedback` en `New`, `game_tables` esperando revisión. La bandeja es una **vista** sobre eso, no una copia. Por eso "si la toma uno baja para todos" sale gratis: cambia el estado de la fila real (#100).

### Transporte

WebSocket + STOMP en `/ws` (#101).

- **Autenticación**: el navegador no puede mandar headers en el handshake, así que el token va en el frame STOMP `CONNECT` y lo valida un `ChannelInterceptor`. No en la query string, donde quedaría en los logs de acceso.
- **Tres destinos** (#101, #116): `/user/queue/notifications` (personal, Spring resuelve el `Principal`), `/topic/admin-queue` (compartido entre admins) y `/topic/tables` (catálogo público — es lo que hace que a alguien navegando el explorador le aparezca una mesa recién publicada).
- **Autorización por destino**: el interceptor rechaza la suscripción a `/topic/admin-queue` de quien no tenga `Admin` u `Owner`. Sin esto cualquiera observa el movimiento de la moderación.
- **El mensaje es una señal, no el contenido, y dice qué invalidar**: `{"type":"GameTablePublished","tableId":"…"}` y el cliente invalida **esa** rama de `queryKeys`, no media caché (#116). Mantiene TanStack Query como única fuente (regla dura 11) y evita que un mensaje perdido deje la interfaz mostrando datos inventados.
- **Cliente**: reconexión con backoff exponencial; al reconectar, invalidar todo lo suscrito para recuperar lo perdido durante la caída.
- **Límite conocido**: el broker en memoria de Spring sirve para **una sola instancia**. Con más de una hace falta un broker externo (RabbitMQ o Redis). Se anota; no se construye ahora.

### Reserva

```
POST   /api/v1/admin-queue/{type}/{id}/claim     reserva
DELETE /api/v1/admin-queue/{type}/{id}/claim     libera
```

Idempotente para el mismo admin, `409` si ya lo tiene otro. Un job libera las reservas de más de 15 minutos. Cada cambio emite `admin-queue.changed`.

## 6. Definición de terminado

Una fase se cierra cuando cumple las ocho:

1. Las reglas de skill `modelo-datos` §5 que caen en su alcance están implementadas.
2. Cada una tiene su test unitario, con los caminos de error y no solo el feliz.
3. Las invariantes de concurrencia de su alcance tienen test de integración con Testcontainers.
4. El flujo principal está cubierto en Playwright.
5. **Se entrega la deuda de revisión de la fase**: lo que se construyó y **no** se verificó, escrito en el momento. Reemplaza al punto que pedía los cuatro estados de cada pantalla (#250) — esa verificación, y toda la demás, se hacen en **F4**.
6. **Se entrega el inventario de archivos nuevos de la fase**, con su ruta.
7. **Los tests de la fase corren y pasan**, y se reporta la salida real. Una fase con tests en rojo no está terminada; si algo queda fuera, se dice cuál y por qué en vez de darla por cerrada.
8. **La ayuda de la fase está escrita** (#231): lo que la fase agregó se explica en `features/help/sections/` y se levanta con `<HelpLink>` desde la pantalla que provoca la pregunta. La documentación que se escribe "después" no se escribe.

Los puntos 5, 6 y 7 son el corte entre fases: **no se arranca la siguiente sin ellos.**

## 7. Cómo se ejecuta cada rebanada

Vale para toda fase que construya producto — F1, F2, F3, F5 y F6 (#181). No es una sugerencia por rebanada: es el procedimiento. **F4 no lo usa**: no construye, y su forma de trabajo está en `fase-4-revision.md`.

### El paso 0 no es un agente

**El contrato se escribe en el hilo principal, antes de repartir**: la lista de endpoints —verbo, ruta, `record` de entrada, `record` de salida, códigos de estado—, las rutas nuevas del sitemap y las ramas nuevas de `queryKeys`.

Sin esto los dos constructores divergen y el trabajo de uno se tira: el frontend no puede esperar a que el backend exista para empezar, y si adivina la forma del DTO, adivina mal. Es lo que hace que el paralelismo ahorre tiempo en vez de gastarlo.

### Los tres agentes

| Agente | Alcance de archivos | Qué entrega | Skills |
|---|---|---|---|
| **A1 · Backend** | solo `backend/` | `@Entity`, migración Flyway, repository, service, DTO, mapper, controller — **y el test unitario de cada regla de negocio que escribe** | `nuevo-endpoint-java`, `er-diagram-sync`, `tests-java` |
| **A2 · Frontend** | solo `frontend/` | tipos derivados del contrato, `features/<dominio>/`, componentes, pantallas, i18n — **y sus tests de Vitest** | `nuevo-componente-react` |
| **A3 · Verificación** | transversal, lectura + tests | corre las cuatro suites, escribe lo que ningún constructor cubre, recorre el camino manual | `tests-java` |

**A1 y A2 corren en paralelo** una vez que existe el contrato: tocan directorios disjuntos, así que comparten el árbol de trabajo y no hace falta un worktree aparte. **A3 arranca cuando los dos terminaron.**

### A3 no es "el que escribe los tests"

Es la parte que más fácil se malinterpreta. La regla dura 7 pide que toda regla de negocio nueva llegue con su test unitario **con ella, escrito por quien la escribió**. Un constructor que entrega lógica pelada y deja que otro le ponga los tests después produce tests que describen lo que el código hace, no lo que la regla exige — que es exactamente el bug que nadie encuentra.

Lo que A3 aporta es el nivel que ningún constructor puede cubrir solo:

1. Las **invariantes de concurrencia** con Testcontainers — las que MySQL no garantiza (§1).
2. El **flujo principal en Playwright**, que cruza backend y frontend y por definición no es de ninguno de los dos.
3. Los **cuatro estados obligatorios** de cada pantalla nueva, verificados y no asumidos.
4. El **camino manual** de la rebanada, de punta a punta.
5. **La salida real de las cuatro suites, reportada.** Una rebanada con tests en rojo no está terminada; si algo queda fuera, se dice cuál y por qué.

**A3 usa el backend y el frontend que ya están corriendo.** No levanta instancias paralelas en otros puertos.

### El cuarto agente, en las rebanadas pesadas

**A4 · Revisor**, entre los constructores y A3: lee el diff contra las reglas duras de `CLAUDE.md` y las de capa de skill `arquitectura-backend` §2.2. Un controller que llama a un repository, un `Map<String, Object>` cruzando HTTP, un string en el JSX sin `t()` o un valor de color suelto se ven en el diff en un minuto y cuestan una tarde si los encuentra un test.

Se usa donde la rebanada toca varios flujos a la vez o algo fuera del proceso —el sistema de archivos, un job—; en las livianas, A3 alcanza.

### El cierre vuelve al hilo principal

Documentación sincronizada en el mismo commit, inventario de archivos nuevos, ayuda escrita (#167, #168), y **un solo commit por rebanada** con su push. **Los agentes no commitean**: un commit por agente rompe el punto 5 de las reglas de git, que pide un mensaje que describa todo lo que se hizo.

### Qué se le pasa a cada agente

Todo agente arranca en frío y no hereda la conversación. Cada invocación lleva, sí o sí: la rebanada y su alcance, el contrato del paso 0, las decisiones `#n` que su parte implementa, los archivos existentes que tiene que seguir como patrón, y **su límite de directorio**. A un agente al que no se le dice "solo `backend/`" toca `frontend/` y pisa al otro.

## 8. Fuera de estas fases

Nada de esto entra en F1–F6, y ninguna fase debe derivar hacia ellos sin decisión explícita:

| Tema | Estado |
|---|---|
| **Campañas** (`table_arcs`) y **Temporadas** (`publish_at` + job) | Fase 2. Diseño cerrado en #129; los tres puntos a resolver antes de construirlas están en skill `modelo-datos` §7.1 |
| **Integración profunda con Discord** | Requiere bot con permisos; no aprobada (#88) |
| **Personajes estructurados** | Siguen siendo archivo adjunto (#4) |
| **Broker externo y caché compartida** | Van juntos: hoy el broker STOMP (#101) y la caché Caffeine (#128) viven en memoria del proceso y sirven para **una sola instancia**. El día que haya dos, hacen falta los dos |
| **Generar los tipos del frontend desde OpenAPI** | Candidato, no adoptado. Se evalúa cuando el contrato esté estable |
