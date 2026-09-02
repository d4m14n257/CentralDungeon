# Plan de desarrollo

> En qué orden se construye, qué se rescata del legacy y cuándo una etapa está terminada.
>
> El *qué* del schema está en `modelo-datos.md`, el *cómo* del código en `arquitectura.md`, las pantallas en `frontend-diseno.md` y el *por qué* de todo en `decisiones.md`.

## 1. Criterio

**Rebanada vertical primero.** La etapa 1 entrega un flujo completo y usable de punta a punta con lo mínimo de cada capa, en vez de cerrar subsistemas enteros de a uno. Se ve algo funcionando antes, a costa de volver sobre módulos ya tocados.

Dos cosas no se negocian entre etapas:

- **Nada se da por terminado sin sus tests** (regla dura 7). Cada regla de `modelo-datos.md` §5 que entre en una etapa llega con su test unitario.
- **Las invariantes que MySQL no puede garantizar necesitan test de integración**, no unitario: un solo `Primary` vivo por mesa (#73) y una sola postulación activa por par (#28). Son las dos que se rompen con concurrencia.

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

## 3. Etapas

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

Las dos en negrita cierran un **callejón sin salida** que la etapa tenía: sin `/my/tables`, a un jugador lo aceptan y no tiene dónde ver la mesa a la que entró; y sin `/notifications`, la etapa **emite** filas en `notifications` que nadie puede leer hasta E6. Es lectura por HTTP; el push llega en E6.

**Entrega**: un jugador entra con Discord, ve las mesas, se postula; el master lo acepta o lo rechaza.

**✅ Terminada.** El flujo completo se probó de punta a punta contra el backend y frontend reales, no solo contra los tests: login por Discord (real y con el doble simulado de `TestDiscordController`/`TestLoginController`, #143), onboarding, explorador, postulación, aceptar/rechazar con notificación, `/my/tables`, `/my/applications` y `/notifications`. La ronda de pruebas manuales que cerró la etapa encontró y corrigió una serie de vacíos reales entre lo diseñado y lo construido — el rastro completo, con la evidencia de cada verificación, está en `decisiones.md` #143 a #160. Los más importantes: la pertenencia de mesa se verifica en el backend antes de leer cualquier dato, nunca solo en el cliente (#151, #152); un actor no puede ser jugador y master de la misma mesa, en ninguna de las dos direcciones (#155); el master se entera de una postulación nueva (#153), y las notificaciones son clickeables y cambian de contexto solas si hace falta (#156); y el `ContextSwitcher` por fin cumple lo que #135 ya exigía desde antes de esta etapa. De regalo, un panel de desarrollo (#158) reemplaza los `fetch()` de consola para las pruebas de ahora en más.

**Los siete puntos de la definición de terminado (§5), verificados al cerrar, no asumidos**: `./mvnw test` (61 unitarios) y `./mvnw verify` (+ `MasterServiceIT`/`RegistrationServiceIT`, Testcontainers vía colima) en verde; `npx playwright test` (`registration-flow.spec.ts` + `discord-login.spec.ts`, los dos casos) en verde — y correr esta última suite al cerrar encontró un bug real más, ya corregido: el panel de desarrollo rompía `/login` con un bucle de reload por un `useMe()` sin gatear (#161), invisible en las pruebas manuales porque ahí siempre había sesión de por medio. `npx vitest run` (27) en verde. Los cuatro estados obligatorios están cubiertos pantalla por pantalla desde #150. Sin regla de `modelo-datos.md` §5 pendiente dentro del alcance de E1.

### E2 — Ciclo completo y catálogos

**Backend** — Estados restantes de la mesa (`Unassigned`, `ChangesRequested`, `PauseRequested`, `Pause`, `Finished`, `Canceled`) + `table_status_changes` con justificación obligatoria (#27, #32, #72). `approval_requests` como mecanismo único, con reserva (#42, #78, #100). Veto acotado a la mesa, aplicado por el `Primary`, pedible por un `Secondary` (#39, #71). Catálogos con `canonical_id`, propuesta y aprobación, búsqueda resuelta por grupo (#55, #56, #57, #59).

También entra **`system_settings`** (#141): la tabla clave-valor, el `SettingsService` con accesores tipados y la auditoría de cada cambio. Llega acá porque es la etapa donde nace el resto del ciclo de admin. Los valores que **E1 consume como constantes** —karma inicial y la justificación por defecto del rechazo automático (#34)— pasan a leerse por el service desde acá; los ajustes de etapas posteriores se agregan como **filas**, nunca como columnas.

**Frontend** — Wizard de creación (`/master/tables/new`), transiciones con justificación, `/admin/queue` y `/admin/catalogs` (por HTTP, sin tiempo real), filtros del explorador resolviendo sinónimos. Entra el resto del contexto Admin —**`/admin/tables`**, **`/admin/requests`**, **`/admin/users`** y **`/admin/settings`**—, el dashboard **`/master`** (#136), que recién acá tiene más de un tipo de ítem que agrupar, y **`/my/history`** (#133), que necesita los estados `Finished` y `Canceled` que esta etapa estrena.

**Entrega**: mesas con su ciclo de vida real y catálogos administrables.

**En progreso**, en sub-rebanadas verticales — no se construye de un tirón, siguiendo el mismo criterio del §1:

1. **✅ Máquina de estados de mesa** (sin `approval_requests` todavía). Los 9 estados, `table_status_changes` con su historial, `Unassigned`→`Opened` al asignar masters (#72), aprobar/pedir cambios/reenviar, iniciar/finalizar, cancelar (Primary o admin) y pausa/reanudación directa de un admin. Frontend: wizard `/master/tables/new`, pestaña Estado en `/master/tables/:id`, `/admin/tables` con Aprobar/Pedir cambios/Asignar masters, contexto Admin nuevo en el `ContextSwitcher`. Detalle y límites conocidos en `docs/decisiones.md` #163.
   **Cerrado después, fuera del orden previsto**: el `AssignMastersDialog` pedía ids de usuario a mano —el límite conocido que #163 documentó y que esperaba a la sub-rebanada 5— y se resolvió acá, porque probar la asignación a mano no era viable. Trajo con él el **lenguaje de búsqueda de toda la app** (#164), que nunca había sido decidido: `GET /api/v1/users/search` con `?q=`, `common/search/` en el backend, `lib/searchQuery.ts` + `SearchQueryInput` en el frontend, y el diálogo de asignación mudado a `routes/admin/` con chips donde el orden es el rol (#165). De paso, `Primary`/`Secondary` dejaron de aparecer en pantalla: en la interfaz son **master** y **co-master** (#166). Y con el lenguaje ya crecido —`/and`/`/or` con chip propio, comas para alternativas— entró **`/help`** (#167), partida por audiencia y enlazada por `#ref` (#168): el índice con lo de todos, y una página por rol. Enseña con pasos y no solo describe, se ata al rol de quien lee y resalta la sección que el `#ref` nombra (#170). Cubre lo de **E1 y E2** —cuenta, notificaciones, buscar, contextos, estados de mesa, postulaciones, crear y llevar una mesa, revisar y asignar masters—, y desde acá **toda etapa cierra con su ayuda escrita** (§5, punto 8). Lo que sigue esperando a la sub-rebanada 5 es la pantalla `/admin/users` completa.
2. `approval_requests` + bandeja de admins + veto — siguiente.
3. Catálogos (`systems`/`tags`/`platforms`) — pendiente.
4. `system_settings` — pendiente.
5. Cierre: `/admin/users`, `/master` (dashboard), `/my/history`, `/admin/requests` completo — pendiente.

### E3 — Sesiones y peticiones

**Backend** — `table_sessions` materializadas al pasar a `Opened` a partir de `start_date` + agenda + `total_sessions` (#26, #33). Asistencia por sesión (#36). `table_tasks` + `task_submissions` + `submission_files`, con entregas que se acumulan y no bloquean (#63, #70, #76). Notificación al publicar una petición (#77).

**Frontend** — Agenda en hora local, registro de asistencia, publicar peticiones y entregar respuestas, `/my/tables/:id`.

**Entrega**: la mesa se juega dentro del sistema.

### E4 — Archivos

**Backend** — `files` rediseñada: nombre físico por id (#80), `content_hash` para deduplicar, `file_type`, `public_audience` (#64), `last_used_at`. `StorageService` detrás de interfaz (#15), compresión al guardar y job de retención por desuso (#75). Vinculación a mesa, postulación y entrega sin duplicar el archivo (#65, #79).

**Frontend** — `FilePicker` con subida o reutilización del historial, `/my/files`.

**Entrega**: archivos con costo acotado y reutilizables.

### E5 — Comentarios y karma

**Backend** — `comment_drafts` con autor → `comments` anónima al confirmar (#48, #49), `comment_quotas` con token HMAC (#82), purga de borradores expirados (#50, #52), moderación de todos los comentarios (#51). `KarmaService` con la fórmula de #96 y sus dos disparadores (#97). `system_feedback` + `feedback_quotas` con el token rotativo por hora (#93, #94, #95).

**Frontend** — Escribir borrador durante la mesa, confirmarlo al cerrarse, `/profile` y `/users/:id` con karma y comentarios, `/admin/moderation`, `/admin/feedback`.

**Entrega**: karma funcionando, con anonimato real.

### E6 — Tiempo real, auditoría y owner

**Backend** — WebSocket + STOMP con el JWT en el frame `CONNECT` y autorización por destino (#101). `audit_logs` con diff de columnas cambiadas (#92). Panel exclusivo del owner: consulta de auditoría, borrado físico de archivos (#66) y migración de cuenta (#83).

**"Ver como" (#140) entra acá, y no antes.** No es una preferencia de orden: `audit_logs.impersonation_id` es FK a `impersonation_sessions`, y sobre todo, **sin la auditoría la función es exactamente la versión sin responsable que #140 descartó** — un admin actuando con la identidad de otro y nadie capaz de reconstruir qué pasó. Se construye completa o no se construye: sesión con motivo obligatorio, caducidad a los 30 minutos, bloqueo sobre `Admin` y `Owner`, bloqueo de todo lo irreversible, **exclusión total de lo que toque comentarios** (#43, #45), y notificación inmediata a la persona.

**Frontend** — Notificaciones push, bandeja de admin en vivo, el flujo de **"ver como"** desde `/admin/users` con su banda permanente de sesión activa, y las tres del owner: `/owner/audit`, `/owner/storage` y `/owner/users/:id/migrate`.

**Entrega**: plataforma operable.

## 4. Motor de notificaciones

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

## 5. Definición de terminado

Una etapa se cierra cuando cumple las ocho:

1. Las reglas de `modelo-datos.md` §5 que caen en su alcance están implementadas.
2. Cada una tiene su test unitario, con los caminos de error y no solo el feliz.
3. Las invariantes de concurrencia de su alcance tienen test de integración con Testcontainers.
4. El flujo principal está cubierto en Playwright.
5. Ninguna pantalla del sitemap de esa etapa quedó sin sus cuatro estados (cargando, vacío, error, sin permiso).
6. **Se entrega el inventario de archivos nuevos de la etapa**, con su ruta, para revisión antes de pasar a la siguiente.
7. **Los tests de la etapa corren y pasan**, y se reporta la salida real. Una etapa con tests en rojo no está terminada; si algo queda fuera, se dice cuál y por qué en vez de darla por cerrada.
8. **La ayuda de la etapa está escrita** (#167, #168): lo que la etapa agregó se explica en `/help`, en la audiencia que corresponde y con su `#ref` enlazado desde la pantalla que lo necesita. La documentación que se escribe "después" no se escribe.

Los puntos 6 y 7 son el corte entre etapas: **no se arranca la siguiente sin ellos.**

Al terminar E6 no puede quedar ninguna regla de §5 sin implementar ni ninguna ruta del sitemap sin construir.

## 6. Fuera de estas etapas

Nada de esto entra en E0–E6, y ninguna etapa debe derivar hacia ellos sin decisión explícita:

| Tema | Estado |
|---|---|
| **Campañas** (`table_arcs`) y **Temporadas** (`publish_at` + job) | Fase 2. Diseño cerrado en #129; los tres puntos a resolver antes de construirlas están en `modelo-datos.md` §7.1 |
| **Integración profunda con Discord** | Requiere bot con permisos; no aprobada (#88) |
| **Personajes estructurados** | Siguen siendo archivo adjunto (#4) |
| **Broker externo y caché compartida** | Van juntos: hoy el broker STOMP (#101) y la caché Caffeine (#128) viven en memoria del proceso y sirven para **una sola instancia**. El día que haya dos, hacen falta los dos |
| **Generar los tipos del frontend desde OpenAPI** | Candidato, no adoptado. Se evalúa cuando el contrato esté estable |
