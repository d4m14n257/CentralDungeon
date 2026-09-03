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

- **Nada se da por terminado sin sus tests** (regla dura 7). Cada regla de `modelo-datos.md` §5 que entre en una fase llega con su test unitario.
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

## 3. Lo ya construido

> **La numeración `E` es historia y no continúa** (#177). Estas cuatro etapas se construyeron con el plan anterior, que ordenaba por subsistema; se conservan porque son el registro de qué se entregó y cómo se cerró. Lo que sigue se planifica por fases (§4), y la tabla de equivalencia dice dónde fue a parar lo que las etapas `E3`–`E6` prometían.

### E0 — Diseño del frontend

Sin código. Produce `frontend-diseno.md`: principios, navegación por contexto, sitemap de ~25 rutas, tokens con los colores de los nueve estados, wireframes de las cinco pantallas que definen al resto, inventario de componentes y mapeo legacy→nuevo.

**Terminada cuando**: toda ruta del sitemap tiene su pantalla descrita y toda pantalla del legacy aparece en el mapeo, como equivalente o como descarte explícito.

### E0.5 — Sistema de diseño

El diseño visual, antes de escribir componentes. **La fuente de verdad de los tokens es el design system en Claude Design** (#118, #130): colores, tipografía, espaciados y radios se deciden ahí y se transcriben al bloque `@theme` de `globals.css`. Lo que no está en el tema no se usa en el JSX — un `bg-[#7c3aed]` suelto es la señal de que falta un token.

La dirección está fijada en #131: fantasía sobria, oscuro por defecto, densidad media. Lo que E0.5 decide son los valores.

Alcance: el sistema de tokens completo, las primitivas que se apartan del default de shadcn/ui, y las cinco pantallas de los wireframes de `frontend-diseno.md` §4 llevadas a diseño real. El resto de las pantallas se derivan de esas cinco al construirlas.

**✅ Terminada.** El `@theme` está transcrito en `frontend/src/styles/globals.css` —27 tokens de estado y 6 de marca verificados en el CSS compilado, con el tema claro sobrescribiendo la capa semántica—, las **28 rutas** del sitemap están diseñadas en ambos temas, y el contraste está medido en cada build: **30 pares, 0 por debajo de AA**, con corte por código de salida. La transcripción no es manual recurrente: el comando está en `CLAUDE.md`.

**Estaba terminada cuando**: el `@theme` transcrito cubre los nueve estados de mesa, los cinco de postulación y el karma; las cinco pantallas están diseñadas en los dos temas (claro y oscuro); y **el contraste de los catorce badges de estado está medido, no estimado a ojo** — el acento comparte familia con `state-active` y `state-paused` (#132), así que el choque se controla con números y con separación de roles, no a ojo.

### E1 — Rebanada usable

**Backend** — Scaffold Spring Boot 4.1.1 con Maven, Flyway `V1__baseline.sql` + `V2__seed.sql`, perfiles y `docker-compose` para MySQL. Entidades: `users`, `roles`, `users_roles`, `table_types`, `game_tables`, `masters`, `table_registrations`. Estados de mesa acotados a `Preparation → Opened → InProgress`. `RegistrationService` con cola FIFO, cupo, rechazo automático al llenarse y la invariante de una postulación activa (#28, #34). Emisión de filas en `notifications`, sin tiempo real todavía. springdoc publicando el esquema, que es el contrato para el frontend (#119).

Toda la seguridad entra acá, porque es la etapa que la estrena:

- OAuth2 con Discord, verificación de membresía al guild, los cuatro roles (#38, #67). El token de Discord **se descarta** al terminar el callback (#125).
- **Access token corto + refresh rotativo** en cookie `httpOnly`; el refresh relee `status` y roles (#125). **CSRF activo solo en `/auth/refresh`** (#127).
- **El JWT afirma identidad, no autorización**: `JwtAuthenticationFilter` carga el usuario y sus roles de la base en cada request (#122), cacheado con **Caffeine** a 60 s más `@CacheEvict` al bloquear o cambiar roles (#128).
- **Verificación de pertenencia** en toda lectura y mutación de un recurso concreto (#121) — es la etapa donde nacen las primeras mutaciones de mesa, así que el patrón se establece acá o no se establece nunca.

**Frontend** — Scaffold Vite 8 + React 19 + TypeScript strict + Tailwind 4 + shadcn/ui, con la estructura de `arquitectura.md` §3.1: `routes/` con `router.tsx` y las páginas, `layouts/`, `features/` con su `index.ts`, y las capas transversales. `client.ts` tipado con parseo de `ProblemDetail`, access token **en memoria** y reintento único ante `401` (#125); `queryKeys.ts` y `config/query.ts` con la política de `staleTime` (#116). **i18next desde el primer componente** (#117): ningún string en el JSX, ni siquiera en esta etapa.

Pantallas: `/login`, `/auth/callback`, **`/onboarding`** (nombre y país, paso bloqueante — #134), `/`, `/tables/:id`, `/my/applications`, **`/my/tables`**, **`/notifications`**, `/master/tables`, `/master/tables/:id` con la pestaña de candidatos — las pestañas como rutas hijas, no como `useState` (§3.1.6).

Las dos en negrita cierran un **callejón sin salida** que la etapa tenía: sin `/my/tables`, a un jugador lo aceptan y no tiene dónde ver la mesa a la que entró; y sin `/notifications`, la etapa **emite** filas en `notifications` que nadie puede leer. Es lectura por HTTP; el push llega en F5.

**Entrega**: un jugador entra con Discord, ve las mesas, se postula; el master lo acepta o lo rechaza.

**✅ Terminada.** El flujo completo se probó de punta a punta contra el backend y frontend reales, no solo contra los tests: login por Discord (real y con el doble simulado de `TestDiscordController`/`TestLoginController`, #143), onboarding, explorador, postulación, aceptar/rechazar con notificación, `/my/tables`, `/my/applications` y `/notifications`. La ronda de pruebas manuales que cerró la etapa encontró y corrigió una serie de vacíos reales entre lo diseñado y lo construido — el rastro completo, con la evidencia de cada verificación, está en `decisiones.md` #143 a #160. Los más importantes: la pertenencia de mesa se verifica en el backend antes de leer cualquier dato, nunca solo en el cliente (#151, #152); un actor no puede ser jugador y master de la misma mesa, en ninguna de las dos direcciones (#155); el master se entera de una postulación nueva (#153), y las notificaciones son clickeables y cambian de contexto solas si hace falta (#156); y el `ContextSwitcher` por fin cumple lo que #135 ya exigía desde antes de esta etapa. De regalo, un panel de desarrollo (#158) reemplaza los `fetch()` de consola para las pruebas de ahora en más.

**Los siete puntos de la definición de terminado, verificados al cerrar, no asumidos**: `./mvnw test` (61 unitarios) y `./mvnw verify` (+ `MasterServiceIT`/`RegistrationServiceIT`, Testcontainers vía colima) en verde; `npx playwright test` (`registration-flow.spec.ts` + `discord-login.spec.ts`, los dos casos) en verde — y correr esta última suite al cerrar encontró un bug real más, ya corregido: el panel de desarrollo rompía `/login` con un bucle de reload por un `useMe()` sin gatear (#161), invisible en las pruebas manuales porque ahí siempre había sesión de por medio. `npx vitest run` (27) en verde. Los cuatro estados obligatorios están cubiertos pantalla por pantalla desde #150. Sin regla de `modelo-datos.md` §5 pendiente dentro del alcance de E1.

### E2 — Ciclo de vida de la mesa (parcial)

La etapa completa prometía además `approval_requests`, catálogos y `system_settings`. **Se construyó su primera sub-rebanada y lo que se le fue sumando encima**; el resto se redistribuyó en las fases de §4 y ya no se planifica como E2.

**✅ Máquina de estados de mesa.** Los 9 estados, `table_status_changes` con su historial, `Unassigned`→`Opened` al asignar masters (#72), aprobar/pedir cambios/reenviar, iniciar/finalizar, cancelar (Primary o admin) y pausa/reanudación directa de un admin. Frontend: wizard `/master/tables/new`, pestaña Estado en `/master/tables/:id`, `/admin/tables` con Aprobar/Pedir cambios/Asignar masters, contexto Admin nuevo en el `ContextSwitcher`. Detalle y límites conocidos en `docs/decisiones.md` #163.

**Lo que se sumó después, fuera del orden previsto**: el `AssignMastersDialog` pedía ids de usuario a mano —el límite que #163 documentó— y resolverlo trajo el **lenguaje de búsqueda de toda la app** (#164), con `GET /api/v1/users/search`, `common/search/` en el backend y `lib/searchQuery.ts` + `SearchQueryInput` en el frontend; el diálogo se mudó a `routes/admin/` con chips donde el orden es el rol (#165). `Primary`/`Secondary` dejaron de aparecer en pantalla: en la interfaz son **master** y **co-master** (#166). Entró **`/help`** (#167), partida por audiencia y enlazada por `#ref` (#168), que enseña con pasos, se ata al rol de quien lee y resalta la sección nombrada (#170) — y desde ahí **toda fase cierra con su ayuda escrita** (§6, punto 8). Entró el **borrado de una mesa que nunca fue pública** (#175) con su línea contra la cancelación, la **paginación** decidida y aplicada (#173), **prettier** con la configuración del repo (#174) y la **limpieza automática de los datos de e2e** (#172).

## 4. Fases

### F1 — Master

**La mesa completa, de la creación al cierre.** Es la fase que produce lo que todo lo demás consume.

**Backend** — Catálogos que la mesa usa: `systems`/`tags`/`platforms` con `canonical_id` y grupos de sinónimos de profundidad 1 (#59), lectura y **propuesta** al crear (#55). Un valor en `Created` no filtra ni se muestra a los jugadores (#57), y la mesa muestra siempre el alias que le puso su master (#58). `TableTypeController`, que falta: `V2__seed.sql` siembra los tipos y hoy no hay forma de listarlos. `table_schedules` con la agenda semanal, y `table_sessions` materializadas al pasar a `Opened` a partir de `start_date` + agenda + `total_sessions` (#26, #33), con asistencia por sesión (#36). `table_tasks` publicadas por el master, que notifican a sus destinatarios (#77), con entregas que se acumulan y no bloquean (#63, #70, #76). **Archivos**: `files` con nombre físico por id (#80), `content_hash` para deduplicar, `file_type`, `public_audience` (#64) y `last_used_at`; `StorageService` detrás de interfaz (#15), compresión al guardar y job de retención por desuso (#75); `table_files` sin duplicar el archivo (#79).

**Frontend** — Wizard de creación completo: tipo, sistema, tags, plataformas, fecha de inicio, duración, sesiones, cupo y agenda. `ScheduleEditor` en hora local. Sesiones y asistencia en `/master/tables/:id`. Publicar peticiones y ver entregas. `FilePicker` con subida o reutilización del historial (#65). Co-masters desde la pantalla del master — `POST /{id}/masters` existe desde E2 y nunca tuvo interfaz. El dashboard **`/master`** (#136).

**El mínimo del jugador para poder probar**: en `/tables/:id` y `/my/tables/:id`, lectura de la agenda, las sesiones, las peticiones publicadas y los archivos públicos. Solo lectura.

**No entra**: pedir pausa ni veto (F3, necesitan `approval_requests`), entregar respuestas a las peticiones (F2), karma (F4).

**Entrega**: un master arma su mesa entera y la lleva hasta el final, y un jugador ve todo lo que publicó.

### F2 — Jugador

**Todo lo que el jugador hace con lo que el master publicó.**

**Backend** — `task_submissions` + `submission_files` (#63, #76). `registration_files` para el archivo de personaje en la postulación. Búsqueda del explorador resolviendo grupos de sinónimos (#54, #56). Retirar una postulación, que es el borrado que #175 dejó anotado para esta fase.

**Frontend** — `/my/tables/:id` completo: agenda en hora local, sesiones, su asistencia y sus peticiones. Entregar respuestas con adjuntos. Archivo de personaje al postularse, sobre el `FilePicker` de F1. **`/my/files`** (#65) y **`/my/history`** (#133). Filtros del explorador por sistema, tag y plataforma — es donde el buscador estrena `/tag`, el caso que motivó el diseño de #164. **`/profile`** y **`/users/:id`** con lo que exista; el karma llega en F4.

**Entrega**: el jugador vive la mesa dentro del sistema, no solo se postula.

### F3 — Admin y Owner

**Revisión, moderación de flujo y administración.**

**Backend** — `approval_requests` como mecanismo único para todo pedido con aprobación, con reserva (#42, #78, #90, #100). Pausa pedida por un master (#32) y veto acotado a la mesa, aplicado por el `Primary` y pedible por un `Secondary` (#39, #71). Administración de catálogos: aceptar, clasificar, fusionar y dar de baja sin romper vínculos (#55, #57, #59, #81). `system_settings` (#141): la tabla clave-valor, el `SettingsService` con accesores tipados y la auditoría de cada cambio; los valores que hoy son constantes —karma inicial, justificación del rechazo automático (#34)— pasan a leerse por el service. El service que otorga roles, con la exclusión `Admin`/`Owner` que #169 dejó pendiente, y el bloqueo de cuentas (#84).

**Frontend** — **`/admin/queue`**, la bandeja compartida con reserva; al nacer, Aprobar y Pedir cambios **se mudan ahí** desde `/admin/tables` (#176). **`/admin/tables`** completo: todas las mesas, cualquier estado, filtros y `?q=` (#176), con los botones de pausa y reanudación que hoy tienen endpoint y ninguna pantalla (#163). **`/admin/catalogs`**, **`/admin/users`**, **`/admin/settings`** y **`/admin/requests`**.

La bandeja funciona **por HTTP** en esta fase; el vivo es F5.

**Entrega**: la comunidad se administra desde la aplicación.

### F4 — Comunidad

**Comentarios y karma**, que es lo que convierte al sistema en una comunidad y no en un calendario.

**Backend** — `comment_drafts` con autor → `comments` anónima al confirmar (#48, #49), `comment_quotas` con token HMAC (#82), purga de borradores expirados (#50, #52), moderación de todos los comentarios (#51). `KarmaService` con la fórmula de #96 y sus dos disparadores (#97). `system_feedback` + `feedback_quotas` con el token rotativo por hora (#93, #94, #95).

**Frontend** — Escribir el borrador durante la mesa y confirmarlo al cerrarse. Karma y comentarios en `/profile` y `/users/:id`, con la ventana de visibilidad de #44 y las restricciones de #41, #45 y #47. **`/admin/moderation`** y **`/admin/feedback`**.

**Entrega**: karma funcionando, con anonimato real.

### F5 — Operación

**Lo que hace la plataforma operable, y el panel exclusivo del owner.**

**Backend** — WebSocket + STOMP con el JWT en el frame `CONNECT` y autorización por destino (#101). `audit_logs` con diff de columnas cambiadas (#92). Borrado físico de archivos (#66) y migración de cuenta (#83).

**Frontend** — Notificaciones push, bandeja de admin en vivo, y las tres del owner: `/owner/audit`, `/owner/storage` y `/owner/users/:id/migrate`.

**"Ver como" (#140) va acá, y no en F3.** No es preferencia de orden: `audit_logs.impersonation_id` es FK a `impersonation_sessions`, y sobre todo, **sin la auditoría la función es exactamente la versión sin responsable que #140 descartó** — un admin actuando con la identidad de otro y nadie capaz de reconstruir qué pasó. Se construye completa o no se construye: sesión con motivo obligatorio, caducidad a los 30 minutos, bloqueo sobre `Admin` y `Owner`, bloqueo de todo lo irreversible, **exclusión total de lo que toque comentarios** (#43, #45), y notificación inmediata a la persona. Por lo mismo esperan acá el borrado físico y la migración de cuenta: son de la misma clase.

**Un owner usa toda la superficie de admin desde que existe** (#169); lo que espera a F5 es lo exclusivo suyo.

**Repaso final**: los tipos de notificación que falten y las rutas del sitemap que hayan quedado sin construir.

**Entrega**: plataforma operable.

### Dónde fue a parar lo que prometían las etapas viejas

Para leer las decisiones ya escritas, que citan la numeración anterior:

| Etapa vieja | Dónde vive ahora |
|---|---|
| E2 sub-rebanada 2 — `approval_requests`, bandeja, veto | **F3** |
| E2 sub-rebanada 3 — catálogos | Consumo y propuesta en **F1**; administración en **F3** |
| E2 sub-rebanada 4 — `system_settings` | **F3** |
| E2 sub-rebanada 5 — `/admin/users`, `/master`, `/my/history`, `/admin/requests`, `/admin/tables` completo | `/master` en **F1**, `/my/history` en **F2**, el resto en **F3** |
| E3 — sesiones y peticiones | Lo que publica el master en **F1**; lo que entrega el jugador en **F2** |
| E4 — archivos | **F1** (subsistema y preparación) y **F2** (personaje, `/my/files`) |
| E5 — comentarios y karma | **F4** |
| E6 — tiempo real, auditoría y owner | **F5** |

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

1. Las reglas de `modelo-datos.md` §5 que caen en su alcance están implementadas.
2. Cada una tiene su test unitario, con los caminos de error y no solo el feliz.
3. Las invariantes de concurrencia de su alcance tienen test de integración con Testcontainers.
4. El flujo principal está cubierto en Playwright.
5. Ninguna pantalla del sitemap de esa fase quedó sin sus cuatro estados (cargando, vacío, error, sin permiso).
6. **Se entrega el inventario de archivos nuevos de la fase**, con su ruta, para revisión antes de pasar a la siguiente.
7. **Los tests de la fase corren y pasan**, y se reporta la salida real. Una fase con tests en rojo no está terminada; si algo queda fuera, se dice cuál y por qué en vez de darla por cerrada.
8. **La ayuda de la fase está escrita** (#167, #168): lo que la fase agregó se explica en `/help`, en la audiencia que corresponde y con su `#ref` enlazado desde la pantalla que lo necesita. La documentación que se escribe "después" no se escribe.

Los puntos 6 y 7 son el corte entre fases: **no se arranca la siguiente sin ellos.**

Al terminar F5 no puede quedar ninguna regla de §5 sin implementar ni ninguna ruta del sitemap sin construir.

## 7. Fuera de estas fases

Nada de esto entra en F1–F5, y ninguna fase debe derivar hacia ellos sin decisión explícita:

| Tema | Estado |
|---|---|
| **Campañas** (`table_arcs`) y **Temporadas** (`publish_at` + job) | Fase 2. Diseño cerrado en #129; los tres puntos a resolver antes de construirlas están en `modelo-datos.md` §7.1 |
| **Integración profunda con Discord** | Requiere bot con permisos; no aprobada (#88) |
| **Personajes estructurados** | Siguen siendo archivo adjunto (#4) |
| **Broker externo y caché compartida** | Van juntos: hoy el broker STOMP (#101) y la caché Caffeine (#128) viven en memoria del proceso y sirven para **una sola instancia**. El día que haya dos, hacen falta los dos |
| **Generar los tipos del frontend desde OpenAPI** | Candidato, no adoptado. Se evalúa cuando el contrato esté estable |
