# F4 — Revisión: implementación

> **Cómo se revisa lo construido.** F4 no entrega producto: entrega **saber qué hay, quién lo alcanza y qué está roto**, con nombre y apellido.
>
> Nace de ADR-01 §3. `plan-desarrollo.md` §4 dice qué cubre; acá está el detalle de las seis rebanadas y el instrumento de cada una.
>
> **Documento vivo mientras F4 esté abierta.** Cada rebanada se marca terminada acá con sus hallazgos y su triaje.
>
> **No confundir con F3.5**, que es la quinta rebanada de F3 —la configuración editable de ADR-17 §3 y ADR-07 §5— y está cerrada. La fase de revisión entre F3 y F5 es esta, F4.

## 1. Por qué existe esta fase

F1.7 intentó la revisión por fase y funcionó: encontró dos huecos de navegación reales y los corrigió. Pero encontró **lo que podía encontrar**, porque revisó F1 contra F1.

Lo que de verdad se rompe está en las costuras entre actores, y **una costura no se puede mirar hasta que existen sus dos lados**:

- **La matriz de roles** no tiene respuesta hasta que los cuatro roles están construidos. Revisarla en F1 es revisar un tercio y darla por buena.
- **Una prueba de IDOR necesita dos actores con derechos distintos.** Con un solo rol construido no hay contra quién probar.
- **El veto de F3.4 excluye al vetado de seis vías de lectura**, y una de ellas —los archivos— la anotó ADR-14 §2 durante F1.4, meses antes de que el veto existiera.
- **El mapa de la interfaz completo** no existe hasta que existen todas las pantallas. Un huérfano en F1 podía ser «alcance de F2 anotado a propósito»; en F4 ya no hay fase siguiente que lo justifique.

Y hay un recurso escaso que es **la atención del cliente**. Gastarla tres veces sobre tres productos parciales, y otra vez sobre el entero, desperdicia justamente las pasadas que importan. F4 la concentra en una.

**Lo que F4 no es.** No es «escribir los tests que las fases no escribieron». Toda regla de negocio llegó con su test unitario escrito por quien la escribió — eso no se movió y no se negocia (`plan-desarrollo.md` §6). F4 escribe el nivel que ningún constructor podía cubrir solo: el que cruza fases, roles y actores.

## 2. Con qué empieza

**No empieza en cero.** Cada fase cerró entregando su **deuda de revisión** —el punto 5 nuevo de `plan-desarrollo.md` §6—, así que F4 arranca con una lista escrita en vez de redescubriendo. Los documentos de implementación de F1, F2 y F3 se borraron en F4.0 (git conserva la historia); **sus tres listas viven ahora acá**, juntas y ordenadas por la rebanada que las verifica.

### 2.1 La deuda heredada de F1, F2 y F3

| Deuda | De dónde viene | La verifica |
|---|---|---|
| Los cuatro estados de cada pantalla de F1, F2 y F3, el viewport de 375 px y el contraste de lo nuevo, verificados y no asumidos | punto 5 viejo de §6, que ADR-01 §3 movió acá; repetido en cada rebanada de F3 | F4.1 |
| Que cada pantalla sea alcanzable **navegando**. F2 solo lo comprobó para `/player/history` | F1.7, F2 | F4.1 — resuelto como mapa en 21–24 (ADR-01 §10); falta el triaje |
| ~~El `<Dialog>` del historial de roles en móvil, que según ADR-21 §7 debería ser sheet desde abajo~~ — **resuelto en §2.4**: ADR-21 §7 retiró el *sheet* | F3.1 | F4.1 |
| El badge `Vetado` y el aviso de retroactividad **renderizados** en los dos temas: los tokens están medidos, la pantalla no | F3.4, F3.5 | F4.1 |
| El inglés renderizado: la paridad de claves está verificada, pero los tests corren en `es` | F3.3, F3.5 | F4.1 |
| `/admin/requests` pone su default `Pending` en la caja y no en la URL: se aparta de ADR-12 §7, y un criterio tipeado se **suma** al chip | F3.2 | F4.1 — es decisión de producto |
| `PlayerBan` visible en `/admin/requests` pero no resoluble ahí: que la pantalla **no ofrezca** los botones a un admin no se miró | F3.4 | F4.1 |
| **Crear y editar una mesa son dos pantallas que repiten lo mismo a mano**: el asistente `MasterTableCreatePage` y el formulario `MasterTableEditPage` comparten el esquema de validación, pero cada uno escribe los mismos ocho campos y la lógica de la agenda (`MasterTableCreatePage.tsx:142-152` es `MasterTableEditPage.tsx:123-130`). No se fusionan —uno va por pasos y el otro en una página—: se proponen bloques compartidos de campos y agenda que usen los dos. El usuario pidió no tocarlo todavía | F1.2 «Agenda, horarios y wizard completo» | F4.5 «La revisión mano a mano» |
| **Las features del backend se agrupan bajo `feature/`**: hoy los catorce paquetes de dominio (`adminqueue`, `approvals`, `auth`, `catalogs`, `dashboard`, `files`, `health`, `notifications`, `profiles`, `registrations`, `settings`, `tables`, `tasks`, `users`) cuelgan sueltos de `com.centraldungeon`, al lado de `common/`. El usuario pide `com.centraldungeon.features.<nombre>/`, para que la raíz tenga solo `CentralDungeonApplication`, `common/` y `features/`. **Es una decisión nueva**: corrige ADR-04 «Backend y contrato de la API» §1 «El código se organiza por feature, con las capas adentro» y su regla en la skill `arquitectura` §2.1 (`references/backend/paquetes.md`, y el resumen del `SKILL.md`), más la línea de «Stack» de `CLAUDE.md`. Se hace en un commit propio que solo mueve paquetes, sin cambiar comportamiento, con `./mvnw verify` en verde antes y después, y moviendo también los tests (`backend/src/test/...`) y los `{@link}` del Javadoc. **Decidido por el usuario el 2026-10-24**: (1) `features`, en plural, igual que el `src/features/` del frontend; (2) `health` no es dominio y va a `common/`; (3) `adminqueue` se renombra **en los dos lados**: paquete, clases `AdminQueue*`, endpoint `/api/v1/admin-queue`, la feature `frontend/src/features/adminQueue`, la ruta `/admin/queue` y las claves de `paths.ts`, `limits.ts` y `query.ts`, unos 54 archivos. La pantalla y ADR-16 «Pedidos con aprobación y bandeja» ya la llaman «Bandeja». **El nombre nuevo es `queue`**: la bandeja ya vive dentro de admin, así que el prefijo sobra. Queda `features/queue`, con `QueueService`, `QueueController`, `QueueClaimRule`, `QueueSource` y el resto de las clases; el endpoint pasa a `/api/v1/admin/queue`, al lado de `/admin/users` y `/admin/files` (hoy `/api/v1/admin-queue` es el único que no cuelga de `/api/v1/admin/`), y en el frontend queda `src/features/queue`. La ruta `/admin/queue` no cambia. Planea Opus 5.5 y ejecuta Sonnet 5.5 (§2.6) | Pedido del usuario, 2026-10-24 | F4.5 «La revisión mano a mano» |
| **El código deja de citar la documentación agéntica**: unas 5.400 citas —3.118 en Java y 2.248 en TypeScript, en unos 830 archivos, a 2026-10-24— a ADRs, secciones de skill, reglas duras, fases y hallazgos, en Javadoc, JSDoc, comentarios de línea, `@DisplayName` y logs. La regla ya cambió (ADR-01 «Proyecto, proceso y conocimiento» §12 «La documentación del código se explica sola») y el código nuevo no agrega ninguna; esta fila es el barrido de lo que existe. Si el comentario ya explica el porqué, la cita se borra; si la cita era el único porqué, se escribe en inglés con palabras propias. Las migraciones aplicadas no se tocan (regla dura 9 «Todo cambio de schema es una migración nueva»). **Va al final de F4**, después de mover los paquetes a `features/`: los dos cambios tocan casi todos los archivos, y en ese orden el barrido trabaja sobre las rutas definitivas y no choca con los cambios de F4.5. Planea Opus 5.5 (el criterio de borrar o reescribir, y el reparto); ejecuta Sonnet 5.5, un agente por feature en paralelo, y Opus 5.5 revisa por muestreo. Se verifica con `grep -rE "ADR-[0-9]|arquitectura §|diseno §|regla dura|hard rule" backend/src frontend/src frontend/e2e` vacío, salvo `db/migration/`, y con `./mvnw verify`, `npm run test` y `npx tsc -b` en verde | Pedido del usuario, 2026-10-24 | F4.5 «La revisión mano a mano» |
| `tables.max_players_cap` no se enuncia al escribir el cupo: el formulario no lo pide a `useClientLimits` (principio 2, ADR-17 §4) | F3.5 | F4.1 |
| ~~`pageSize.adminQueue` sirve a dos pantallas y el nombre quedó significando la otra~~ — **resuelto en §2.3**: ahora es `pageSize.admin` y sirve a las seis (ADR-21 §11) | F3.3 | F4.1 — cosmético |
| `GET /api/v1/files/{fileId}` —solo se consume `/content`— y los hooks montados en cero lugares (`useCatalogValue`, `useUploadFile`) | F1.7, confirmado por el barrido de F4 | F4.1 |
| La matriz de visibilidad de perfiles probada con `Player`, `Master` y `Admin`: falta `Owner` y falta el vetado | F2 | F4.2 |
| Las tres cosas que se llaman «owner» | F3 §7 | F4.2 |
| La evicción de las cachés `userAuth` y `systemSettings` observada **como caché**: se probó su efecto por HTTP, no la caché | F3.1, F3.5 | F4.2 / F4.4 |
| Idempotencia de dos revocaciones simultáneas sobre la misma persona: responde `200` y `409`, aceptado a propósito (ADR-06 §2) | F3.1 | F4.2 — confirmar que sigue siendo lo querido |
| `registration_files` frente al borrado lógico: ADR-09 §2 decidió que la fila sobreviva, y no se barrió qué otras lecturas podrían devolverla | F2 | F4.3 |
| `TestDataService` se rompió por quinta vez. Corregido, pero la clase de error sigue viva | F1.2–F2.2 | F4.3 |
| Concurrencia del veto contra MySQL real: hay unitario del `409`, no hay IT de dos masters vetando a la vez | F3.4 | F4.3 |
| Dos admins editando el mismo ajuste: `last write wins` a propósito, sin IT que lo recorra | F3.5 | F4.3 |
| El cron real del barrido de huérfanas, el `@Scheduled` de la bandeja en una JVM de producción y el guard de multipart en un arranque real: se prueban los métodos, no el despliegue | F3.2, F3.3, F3.5 | F4.3 |
| La resolución de grupos de catálogo no está acotada | F2.1 | F4.3 |
| `REQUEST_ENTITY_GONE` en su caso natural, que empezaba a dispararse en F3.4 | F3.2 | F4.4 |
| Las siete vías de lectura de un archivo, juntas y contra el actor que no debería pasar | F2.2 | F4.4 |
| La pausa reagendando al reanudar y el veto de un `Secondary` resuelto por el `Primary`, recorridos en el navegador | F3.4 | F4.5 |
| El buscador de `/admin/tables`, si F3.3 no lo completó; el e2e de «aprobar `TableOpen` no crea mesa» acotado a `Unassigned` | F2.1, F3.2 | F4.5 |
| Las dos fuentes de la bandeja que nacen vacías (`comments`, `system_feedback`) | F3.3 | **F5** — anotado a propósito |

### 2.2 Lo que ya encontró el barrido de navegación

Al dibujar los diagramas 21–24 (ADR-01 §10) se barrieron `router.tsx`, cada `Link`, `NavLink`, `navigate` y `Navigate`, `config/paths.ts`, `notificationTarget.ts`, los 131 handlers del backend contra sus llamadores y los enums compartidos. **Son hallazgos sin triar**: F4.1 los clasifica en los tres de §3.

| Hallazgo | Evidencia |
|---|---|
| **No hay guardia de onboarding.** Solo el callback manda a `/onboarding`; cualquier otra entrada lo salta. Y el JSDoc de `OnboardingPage` afirma que el redirect vive en la guardia de sesión | `RootLayout.tsx`, `OAuthCallbackPage.tsx` |
| `/login` no redirige a quien ya tiene sesión | `LoginPage.tsx` |
| ~~`/admin` a secas pinta el layout con un `Outlet` vacío: ni índice, ni redirect, ni 404~~ — **resuelto en §2.3** (ADR-20 §2, §3) | `router.tsx` |
| ~~`TableChangesRequested` y `TableApproved` caen en la pestaña Candidatos, aunque el comentario promete la de estado (ADR-18 §3)~~ — **resuelto en §2.4**: la pestaña Estado ya no existe y las decisiones están en el aviso, visible desde cualquier pestaña (ADR-21 §10) | `notificationTarget.ts` |
| Un `PlayerBan` resuelto manda al co-master a `/player/profile`; un `TablePause` resuelto manda al master a la vista de jugador de su mesa | `notificationTarget.ts` |
| Las pestañas del master enlazan a `/player/users/:id`, y el `UserMenu` manda a `/player/profile` a una cuenta que solo es admin: cruces de contexto | `MasterTableCandidatesTab`, `MasterTablePlayersTab`, `UserMenu` |
| Ninguna de las siete pantallas de admin tiene un `Link` hacia afuera: un admin no puede abrir el detalle de una mesa, el perfil de un usuario ni la entidad de un pedido. La única salida es de `/admin/catalogs` a su propio lienzo de grupo (ADR-12 §7), que no cierra este hallazgo | `routes/admin/*` |
| Las filas de `/player/history` no enlazan a la mesa terminada | `PlayerHistoryPage` |
| `/my/files` se esconde sin biblioteca: una cuenta solo admin u owner no tiene puerta | `UserMenu` |
| Tres builders de `paths.ts` sin llamador; ~~las constantes de patrón que el router ignora aunque el JSDoc diga que son para él~~ (resuelto en §2.4, ADR-19 §10); `helpPlayers`/`helpMasters`/`helpAdmins` sin ruta; tres URL escritas a mano | `config/paths.ts`, `NotificationBell`, `api/client.ts`, `devApi.ts` |
| Dos tipos del frontend que no coinciden con lo que devuelve el backend: `requestBlock` tipado `void` (devuelve `ApprovalRequestDetailResponse`) y `banRequestsApi.approve` tipado `UnreadBody` (devuelve `RegistrationResponse`) | `registrationsApi.ts`, `approvalsApi.ts` |
| **El texto de la postulación no lo muestra ninguna pantalla**: se pide, se guarda como texto plano (ADR-03 §8) y el master no lo lee en ningún lado | `ApplyToTableDialog`, `MasterTableCandidatesTab` |
| Valores de enum que ningún código produce: `TableApprovedWithChanges`, `SubmissionStatus.Pending`, `UserStatus.Deleted`, `RegistrationFileStatus.Removed`, `SubmissionFileStatus.Deleted` | los enums del backend y sus espejos |
| **117 alertas de Dependabot** en la rama por defecto (6 críticas, 59 altas) | aviso de GitHub al hacer push |
| **Los diagramas ER quedaron atrás de las migraciones.** El de `arquitectura` §4.3 no tiene `file_categories` ni `task_files` (V9), y los `.mmd` por subsistema no tienen `user_role_changes`, `user_status_changes` (V11) ni `registration_status_changes` (V12). La tabla de migraciones del DDL llegaba hasta V9; F4.0 la completó hasta V13 | `docs/modelo-datos.md`, `docs/diagramas/11`–`16` |
| `er-diagram-sync` pedía que el DDL «refleje el estado final acumulado», pero el DDL es el `V1__baseline.sql` literal más una tabla de migraciones. Corregido en F4.0 | la skill |

### 2.3 Cambios de la revisión mano a mano

**Registro vivo** de lo que el usuario cambia mientras recorre las pantallas. Cada fila ya viene triada —casi siempre como **decisión nueva**, porque lo que se corrige es una regla que nadie había cuestionado— y lo estético queda además en la skill `diseno`, donde se lee antes de construir la próxima pantalla. Una fila nueva por cambio; lo que cambia de nuevo se tacha y se agrega, no se reescribe.

| Cambio | Triaje | Dónde quedó |
|---|---|---|
| **Cada contexto se cierra a quien no lo tiene y lo devuelve a su home**, sin mostrar un 403: un Player que escribe `/admin/users` o `/master` vuelve a `/player`; una cuenta solo-master que escribe `/player` vuelve a `/master`. `/master/tables/new` sin el rol `Master` vuelve a `/master`. Sin ningún contexto, `/` explica en vez de hacer bucle | Decisión nueva: **ADR-20 §2**, corrige ADR-20 §1 y ADR-20 §2 | `RequireContext` en los tres layouts; `diseno` §2; skill `arquitectura` §3.1.6 |
| **`/admin` es la home de Admin**: bienvenida sin métricas, donde cae un admin al entrar, primer ítem «Inicio» de la nav | Decisión nueva: **ADR-20 §3**, corrige la home de F3.3 | `AdminHomePage`; sitemap de `diseno` §2; diagrama 24 |
| **Paginación de las tablas de trabajo**: tira numerada compacta, «Ir a…» para saltar de la 1 a la 900, y «Por página» con 10 · 25 · 50 · 100 en `?size=` | Decisión nueva: **ADR-21 §11**, precisa ADR-04 §6 | `PaginationControls`; skill `diseno` §5 «Listas de trabajo»; `design/build.py` |
| **Acciones de fila como íconos con tooltip** en las seis tablas de admin, con un vocabulario de íconos fijo | Decisión nueva: **ADR-21 §11** | `IconAction` en cada `renderActions`; skill `diseno` §5 «Listas de trabajo»; `design/build.py` |
| **Las acciones de fila van en una línea, sin wrap, y un patrón de estilo tiene nombre**: cambiar el contenedor de `/admin/users` no cambiaba las otras tablas porque cada pantalla escribía el suyo. Se nombraron `.row-actions` (que ponen `DataTable`, `CollapsibleSection` y `FileCard`), `.page-title`, `.section-title`, `.section-label`, `.list-divided`, `.list-divided-bare`, `.inline-error` y `PageHeader`, y se barrió todo el frontend | Decisión nueva: **ADR-21 §5** | `styles/base.css`, `components/PageHeader.tsx`; skill nueva `diseno` §5.c; `design/build.py` |
| **La arquitectura es una sola skill**, que incluye el modelo de datos, el testing, la documentación con Javadoc/JSDoc y los procedimientos | Decisión nueva: **ADR-01 §5** | skill `arquitectura`; `CLAUDE.md`, `entorno-local` §6 |

**Consecuencias de ADR-20 §2, para triar en F4.1** — dos cruces de contexto de §2.2 que antes llevaban a una pantalla del prefijo ajeno y ahora **redirigen**:

- El `UserMenu` manda a `/player/profile` también a una cuenta sin `Player` (solo admin, solo master): el perfil propio queda inalcanzable para ella.
- Las pestañas Candidatos y Jugadores del master enlazan a `/player/users/:id`: un master sin `Player` que abre un perfil vuelve a `/master`. **Esto rompe ADR-10 §1 para ese master** —ver el perfil de quien se postuló a su mesa— y lo fija en rojo el e2e `profile-visibility.spec.ts` (el paso del master que abre al candidato), que se deja sin tocar a propósito hasta decidir dónde vive el perfil.

Las dos piden lo mismo —que el perfil deje de colgar del contexto Jugador o que cada contexto tenga el suyo— y es una decisión, no un bug.

**Deuda de estilo que dejó a la vista ADR-21 §5, para triar en F4.1**: `text-[11px]` en `WizardSteps` y `text-[10px]` en `WeeklyScheduleGrid` (los de `NotificationBell` se corrigieron en `34884c2`) son valores sueltos fuera del `@theme` (regla dura 18) — o se agrega el tamaño a `design/build.py`, o se usa `text-xs`.

### 2.4 Lo que encontró la auditoría de las ADRs

El 2026-10-20, tres agentes `auditor-conocimiento` contrastaron las 24 ADRs con el código (89 hallazgos). Lo crítico —producción arrancaba con el secreto JWT de desarrollo— se corrigió en el acto (`1ebd72f`), y el texto que mentía, en `afb16f1`. **Lo que quedó se resolvió el mismo día**: los cinco bugs B1–B5, uno nuevo (B6: una mesa que nunca fue pública se leía por id) y las diecisiete decisiones D1–D17, aprobadas por el usuario, en `bbca625`, `def5f07`, `27e6d0a` y `34884c2`. Cada decisión vive ahora en su ADR, con su regla en la skill:

| Dónde | Decisiones |
|---|---|
| ADR-02 §3 | D1 — un espejo a mano por cada `…Response` (regla dura 6) |
| ADR-19 §1, §6, §10 | D5 — la raíz importa el barrel de una feature, la ayuda solo `import type`; D6 — el diálogo es dueño de su formulario; D2 — el router escribe segmentos, `paths.ts` los enlaces |
| ADR-21 §7, §10 | D14 — sin *sheet* en móvil; D3 — `MasterTableStatusNotice` y la pestaña Historial del master |
| ADR-10 §5 | D7 — sin karma en ninguna respuesta hasta F5 |
| ADR-12 §3, ADR-06 §4 | D8 — propone quien escribe una mesa (cierra B2) |
| ADR-08 §2, ADR-06 §8 | D9 — la agenda se mide contra todos los masters, `MASTER_SCHEDULE_CONFLICT` |
| ADR-07 §1 | D10 y B6 — el admin ve los `Draft`; lo nunca público es `404` para el resto |
| ADR-05 §6 | D12 — enlaces solo `http(s)` |
| ADR-03 §8 | D13 — el texto de la postulación es plano |
| ADR-18 §2 | D15 — la campana hace polling de 60 s; D16 — el `staleTime` del cliente es la política |
| ADR-09 §7 | B1 — la marca «ya te postulaste» la calcula el servidor |
| «Abierto» de `docs/adr/README.md` | D17 — `Confirmed` de `comment_drafts` (F5) y el estado del job de Temporadas (fase 2) |

D4 (`TestLoginController` sin repositories) y D11 («Ver más» en notificaciones y mis mesas, ADR-04 §6) eran código que no cumplía una regla vigente, y la regla no cambió.

**Queda abierto**: cinco e2e que fallan igual en `2ba6c5e`, antes de este trabajo. Una ya tenía explicación: `profile-visibility.spec.ts:102` se dejó en rojo a propósito en §2.3, porque ADR-20 «Navegación, contextos y ayuda» §2 «Cada contexto se cierra a quien no lo tiene» devuelve a `/master` al master sin `Player` que abre un perfil, y espera la decisión de dónde vive el perfil. Las otras cuatro están **para triar**: `admin-requests.spec.ts:149`, `admin-users.spec.ts:95`, `master-dashboard.spec.ts:67` (el co-master quitado debería ver «No tenés permiso») y `profile-visibility.spec.ts:159` (no encuentra el encabezado «Asistencia» en el perfil que abre el admin).

### 2.5 La reunión con el cliente del 2026-10-21

El usuario le mostró al cliente lo construido en F1–F3. La grabación tiene dos pistas, cada una con una sola voz: en una está el cliente, que revisa y corrige, y en la otra el usuario, que hace la demo. A la mitad, revisaron juntos [StartPlaying](https://startplaying.games/search) como referencia **para rescatar ideas, no para copiar su vista**. Las tareas van separadas por quién las dijo: **C**, el cliente; **U**, el usuario; **S**, lo que se rescata de StartPlaying; y **P**, las preguntas que quedan para el cliente.

Cada fila ya viene triada con las tres categorías de §3. Casi todo es **decisión nueva**, porque el cliente corrige reglas que nadie había cuestionado. Las decisiones **no están escritas en sus ADRs todavía**: cada una pasa por `escriba-adr` cuando el usuario la confirme y, si depende de una P, cuando el cliente la responda.

**Orden sugerido**, porque las de la agenda se encadenan:

1. C1, S1, C2 con S2, y C3: agenda, frecuencia, duración y cupo.
2. C8 a C10: primera clase.
3. C11 y U8: cierre con revisión.
4. C13 y U1: zona horaria.
5. C7: idioma.
6. El resto.

**Los bugs, siguientes en F4.5 «La revisión mano a mano»**, cada uno con su test de regresión (regla dura 7 «Toda regla de negocio nueva llega con su test»): C14, U2, U3 y U10. U4, U5 y U8 se anotaron como bugs, pero al contrastarlos con el código resultó que no lo son, o no del todo (ver «Dudas y puntos por afinar»).

#### Lo que pidió u observó el cliente

| # | Tarea | Qué hay hoy | Triaje |
|---|---|---|---|
| C1 «La agenda manda sobre la fecha de inicio» | Primero se define la agenda, y el selector de fecha de inicio ofrece solo los días que caen en ella: si se juega los lunes, la mesa no puede empezar un martes | El form no cruza `start_date` con los días de la agenda (`MasterTableCreatePage.tsx`, `schemas.ts`); el service toma el primer slot posterior | Decisión nueva: ADR-08 «Agenda, sesiones y asistencia», con la validación en el backend y en el schema zod |
| C2 «Duración abstracta en vez de sesiones planeadas» | En vez de "sesiones planeadas", una **clase de duración**: One-shot (1–2 sesiones), Corta (4–6), Mediana (hasta unos 6 meses, ~24 sesiones) y Larga (más de 6 meses), más un texto opcional del tipo "de 1 a 4 sesiones aprox." | Solo `total_sessions` (`GameTable`), y es el que decide cuándo termina la mesa | Decisión nueva: ADR-07 «Mesas: ciclo de vida» y ADR-08 «Agenda, sesiones y asistencia» §3 «Las sesiones se materializan como filas» — sin `total_sessions`, falta decidir hasta dónde se generan las sesiones (ver S2) |
| C3 «Cupo mínimo y máximo» | La mesa tiene mínimo y máximo. Al llegar al mínimo, el master puede **cerrar las postulaciones** y aceptar cualquier número entre el mínimo y el máximo | Solo `max_players` | Decisión nueva: ADR-07 «Mesas: ciclo de vida» §5 «El cupo es `max_players`» y ADR-09 «Postulaciones y veto» §3 «Al llenarse el cupo, los candidatos restantes se rechazan» (el cierre manual) |
| C4 «Preview de archivos» | Al darle «usar» a un archivo de la biblioteca, el master tiene que poder verlo antes, porque no recuerda qué tiene | No hay preview, el archivo solo se descarga (`FilePicker.tsx`, `FileController`) | Decisión nueva: ADR-14 «Archivos: lectura y bibliotecas» §2 «Leer un archivo pasa por una sola puerta». Falta decidir qué tipos se previsualizan (¿imagen y PDF?) |
| C5 «Peticiones divididas por destinatario» | La lista de peticiones se separa en las de candidatos y las de jugadores. El form lo aprobó tal como está | `TaskAudience` ya distingue `Candidates`, `Players` y `Single`; la lista no agrupa | Ajuste de UI en F4.5 «La revisión mano a mano»: agrupar por audiencia |
| C6 «"Lo que espera tu respuesta" no se entiende» | Confunde la etiqueta de arriba a la izquierda, junto a «Mis mesas» | `locales/es/master.json`, `help.json` | Entra en C7 |
| C7 «El español está mal» | Hay que pasar todos los textos a un español correcto | No es español de España, como creyeron en la reunión: es **voseo rioplatense** («tenés», «agregá», «podés») | Decisión nueva: ADR-23 «Textos e idiomas» §2 «Hay dos idiomas» fija la variante, y se barre `frontend/src/locales/es/*.json`. Depende de P5 |
| C8 «Ocultar el master es una configuración de la mesa» | Toda mesa tiene sus configuraciones, y una de ellas es **no mostrar el nombre del master a los candidatos**: cualquier master puede activarla y en primera clase va siempre activa. El nombre se ve recién al ser jugador | El tipo `FIRST_CLASS` no cambia ningún comportamiento; las respuestas de lista llevan siempre `primaryMaster` (`GameTableSummaryResponse`) | Decisión nueva: ADR-07 «Mesas: ciclo de vida» y ADR-06 «Roles y alcance». **Es de seguridad**: el nombre se omite en el backend, no se esconde en la UI, y pasa por `revisor-seguridad`. Depende de P1 |
| C9 «Primera clase con o sin master, y pedirla» | Una mesa de primera clase la crea un admin, sin master o asignándolo, o la crea un master que pide "quiero que sea de primera clase" al mandarla a revisión, y el admin decide si va | Crearla sin master y asignar existe (ADR-06 «Roles y alcance» §7 «Un admin crea una mesa sin ser su master»); no hay forma de pedir primera clase | Decisión nueva: ADR-07 «Mesas: ciclo de vida» y ADR-16 «Pedidos con aprobación y bandeja» |
| C10 «Vista del jugador más sencilla» | El jugador ve dos grupos: **mesas públicas**, de cualquier master, y **mesas de primera clase** | El explorador no filtra por tipo (`GameTableSearchField`) | Decisión nueva: ADR-22 «Buscadores», con la pantalla en la skill `diseno` §4 |
| C11 «Finalizar también pasa por revisión» | Finalizar pide motivo y revisión del admin, "por si alguien la finaliza solo para no pedírselo al admin" | Se finaliza directo y sin motivo (`GameTableService`) | Decisión nueva: ADR-07 «Mesas: ciclo de vida» §1 «La mesa tiene una máquina de estados formal», con el patrón de §2 «La pausa del master necesita aprobación» |
| C12 «Se califica al terminar, también si se cancela» | Al finalizar **y al cancelar**, los jugadores pueden calificar la mesa | Comentarios y karma son de F5 «Comunidad», y ADR-11 «Comentarios, karma y feedback» §4 «Una mesa cancelada no genera comentarios ni mueve karma» dice lo contrario | Alcance de F5 «Comunidad» **y** decisión nueva que corrige ADR-11 §4. Depende de P3 |
| C13 «País y zona horaria los elige el usuario» | La del navegador no sirve: falla con una VPN. El usuario elige su país, que se muestra con su bandera, y su zona; el selector pone primero las zonas de ese país, y la zona se puede cambiar si vive en otro lado. El país sirve para que los demás vean de dónde es sin mirar la zona | Existe `users.country`; no hay zona en el perfil, solo la del navegador (`lib/date.ts`) | Decisión nueva: ADR-10 «Perfiles» §4 «Nombre y país se piden una vez» y ADR-08 «Agenda, sesiones y asistencia» §1 (la zona del perfil manda sobre la del navegador), con su migración |
| C14 «El doble modal» | Error clásico: al cerrar la confirmación se cierra también el modal de abajo. Pide interceptar el cierre y preguntar "¿seguro que quieres salir?", **sin** guardar borradores | `FormDialog` abre un `ConfirmDialog` encima | Bug: reproducirlo, test de regresión y corregirlo en `FormDialog` |
| C15 «Despliegue» | En Linux, Ubuntu en concreto; la base le da igual. Quiere CI/CD | ADR-02 «Stack, herramientas y despliegue» §5 deja la topología de producción sin decidir; el usuario pensaba en Terraform sobre Oracle Cloud | Decisión nueva: ADR-02 §5, con Ubuntu como destino y el pipeline |
| C16 «Tags y temas separados», a futuro | "Tal vez después" separar los tags en tags y temas, con la misma lógica y otra clasificación | Una sola lista, sin categoría ni descripción | Decisión nueva: ADR-12 «Catálogos» §1 «Hay tres catálogos». Se trata junto con S6 |

**Lo que el cliente aprobó sin cambios**:

- La mesa nace en borrador y nadie la ve hasta que se manda.
- El form de peticiones, con fecha límite y el aviso de que no pasa nada si vence.
- Las mesas sin master.
- El rol dinámico: todos son jugadores de base, master es quien dirige una mesa, y aparte está el permiso de crear mesas (la regla dura 17 «El rol no es la pertenencia»).
- El polling de notificaciones y el indicador de conexión.
- El botón «Cómo funciona».
- El login con Discord.

#### Lo que el usuario reconoció como pendiente en la demo

| # | Tarea | Triaje |
|---|---|---|
| U1 «Las horas sin AM/PM y sin zona» | "Falta especificar las zonas horarias, que no marque AM/PM": cada hora se muestra con su zona y en el formato del idioma | Bug de UI en F4.5 «La revisión mano a mano»; qué zona se muestra depende de C13 |
| U2 «El seguimiento de la revisión sale en UTC» | En el seguimiento de la revisión, la fecha no se pasaba a la zona local | Bug: encontrar dónde se formatea sin `lib/date.ts`, con su test |
| U3 «Un botón que no hace nada» | En los archivos de la mesa | Bug: identificarlo en el recorrido |
| U4 «Archivos publicados» | Falta definir la sección «publicados», que según el cliente son los archivos que publican los admins | **Ya está diseñado**: es ADR-14 «Archivos: lectura y bibliotecas» §7 «La biblioteca de la plataforma solo tiene lo de la plataforma», y salió vacío porque no había nada publicado. A lo sumo, sembrar un archivo publicado en los datos de prueba |
| U5 «Lo que parecía legacy en pantalla» | En la reunión se dijo que «De mesa / Petición del master / Solicitud de jugador» y una «lista» de las peticiones venían del legacy | **No es legacy**: son los cajones de ADR-14 «Archivos: lectura y bibliotecas» §4 y §5 (`files.json` → `category`), usados como filtro. Y el form de peticiones ya acepta varios archivos. Queda como duda, no como bug |
| U6 «La petición no muestra su detalle sin editarla» | Mostrar más del contenido de la petición sin entrar a editarla, y ocultar el botón según el estado | Ajuste de UI en F4.5 «La revisión mano a mano» |
| U7 «La pantalla de revisión está mezclada» | Lo que ve el admin al revisar se mezcla con una solicitud y hay que reordenarlo | Ajuste de UI en F4.5 «La revisión mano a mano» |
| U8 «Cancelar una mesa en curso sin motivo» | Una mesa en progreso se canceló sin justificación, y no debía. La regla que propuso el usuario: en `Draft` y `Preparation` se cancela libre; desde `Opened` o `InProgress`, con motivo y revisión del admin, porque ahí es fuerza mayor. Además, una mesa cancelada seguía apareciendo en la lista | Decisión nueva en ADR-07 «Mesas: ciclo de vida» §1: la revisión del admin al cancelar. **Probablemente no hay bug**, porque el motivo ya es obligatorio (`ChangeTableStatusRequest`, `@NotBlank`) y en la grabación el cliente dice «pero ahí pusiste la justificación»; lo que faltaba era la revisión. Se trata junto con C11 |
| U9 «Pedir ser master y pedir una mesa» | Son dos pedidos distintos, y falta el flujo desde el jugador: uno sin el rol `Master` no tiene cómo crear una mesa | `MasterGrant` ya otorga el rol al aprobarse; `TableOpen` se registra, pero aprobarlo no produce nada (`ApprovalService`). Verificar la UI del jugador y decidir qué produce aprobar `TableOpen` (ADR-16 «Pedidos con aprobación y bandeja») |
| U10 «Algo se ve mal en el tema claro» | Durante el flujo de solicitudes | Bug visual: corregirlo con tokens (regla dura 18 «Ningún texto ni valor de estilo suelto») |
| U11 «Las métricas y los logs del Owner» | El Owner es el único que crea admins, y un admin no puede actuar sobre otro. Falta preguntarle al cliente qué quiere medir: uso del sistema, logs del servidor | Alcance de F6 «Operación». Depende de P2 |
| U12 «Selección múltiple con Shift» | Para borrar varios a la vez. El hook existe en `hooks/`, sin usar | Decisión nueva chica: ADR-21 «Diseño visual e interacción» §11 «Una lista de trabajo actúa con íconos por fila» (dónde aplica) |
| U13 «Un catálogo de sistemas para probar» | Usar la lista de sistemas y plataformas de StartPlaying como datos de prueba | Tarea: sembrarla en `TestDataService`, no en una migración |
| U14 «Notificaciones push» | No hay service worker: hoy todo es polling | Alcance de F6 «Operación» (ADR-18 «Notificaciones y tiempo real» §2 «El tiempo real es WebSocket + STOMP») |

#### Lo que se rescata de StartPlaying

Revisado en el sitio el 2026-10-21.

| # | Qué hace StartPlaying | Para nosotros |
|---|---|---|
| S1 «Frecuencia» | La tarjeta dice `Weekly / Thursday - 12:00 AM CST`; también hay `Biweekly` (cada dos semanas) y `Once` (una sola sesión) | Hoy todo es semanal (`TableSessionService.occurrencesFrom`, `plusWeeks(1)`). Decisión nueva en ADR-08 «Agenda, sesiones y asistencia»: semanal, quincenal o única |
| S2 «Cómo termina» | `Campaign Length / 9–15 Sessions` (un rango, no un número), `Session Duration / 3 hours`, y la próxima fecha con su número: `Oct 22 / Session 12` | Encaja con C2: la clase de duración más un rango opcional. El número de sesión se calcula, y la mesa termina cuando el master la finaliza (C11), no al llegar a un número |
| S3 «Cupo y arranque» | `3 NEEDED TO START`, `2 SEATS LEFT` y `0 / 6 Seats Filled`; "la partida empieza cuando se sumen 3 jugadores" | Es como se ve C3. Los lugares que quedan se calculan, sin columna nueva (`GameTableCard`) |
| S4 «Estado en el buscador» | El filtro *Status* separa las listas para jugar de las que todavía buscan el mínimo | Sale de C3 |
| S5 «Tipo de juego» | One-Shot, Campaign y Play by Post (por texto, asíncrono) | *Play by post* entra como plataforma («Por texto») o como tipo de mesa; se decide con C2 |
| S6 «Catálogos con categoría y definición» | Temas (Anime, Cosmic Horror…), estilos de juego con su definición, content warnings, herramientas de seguridad, idioma, experiencia y edad (18+) | Con C16: los tags llevan **categoría** y **descripción** (ADR-12 «Catálogos»). Los content warnings y el nivel de experiencia son lo que más se rescata |
| S7 «Plataformas» | D&D Beyond, Roll20, Foundry y Discord, cada una con su descripción | Discord, primera; al seed se suman «Por texto» y Obsidian (U13) |
| S8 «Filtros prehechos» | Chips de un clic: *Play Today*, *Few Seats Left*, *New Campaigns*, *For Beginners*, *Exclude D&D* | Ideas para C10, sin cambiar el lenguaje de ADR-22 «Buscadores» §1 «Todo buscador habla el mismo lenguaje de una línea» |
| S9 «La ficha del master» | Años jugando, partidas dirigidas, tiempo de respuesta y reseñas | Alcance de F5 «Comunidad» |
| S10 «Las secciones de la mesa» | Creación de personaje, qué esperar, equipo necesario, plataformas, seguridad y content warnings | Campos de texto de la mesa: una decisión en ADR-07 «Mesas: ciclo de vida», con cuidado de no inflar el form |
| S11 «Lo que no se copia» | El diálogo de filtros pierde lo elegido al cerrarse (el cliente se quejó de eso), y el modelo de pago | Nuestros filtros viven en la URL y no se pierden. El precio no se pidió: queda como idea, fuera de alcance |

#### Preguntas para el cliente

- **P1 «Ocultar el master»**: cuando el nombre se oculta, ¿el candidato ve un alias, algo como "Master de primera clase", o nada? (C8)
- **P2 «Las métricas del Owner»**: ¿qué quiere medir? (U11)
- **P3 «Calificar al cancelar»**: ¿califican todos los jugadores, o solo quienes llegaron a jugar alguna sesión? (C12)
- **P4 «Los datos actuales»**: dijo que hoy el sistema "es un archivo de texto plano". ¿Hay datos que migrar a la base nueva?
- **P5 «La variante del español»**: ¿español de México, tuteando? (C7)

#### Dudas y puntos por afinar

Lo que no quedó claro de la grabación o lo que hay que precisar antes de escribir cada decisión. Al lado de cada duda va quién la resuelve: el **cliente** (se suma a las P cuando se le pregunte), el **usuario**, o el **recorrido**, que es verificarlo en la app. Una duda resuelta se tacha y se anota la respuesta, no se borra.

**Agenda, duración y cupo (C1, C2, C3, S1–S5)**

- **C1** — Si la fecha elegida no cae en un día de la agenda, ¿el selector **oculta** esos días o el sistema **corre** el inicio al próximo día válido? Si la agenda se edita después de elegir la fecha, ¿se vuelve a validar? *Usuario.*
- **C1** — En tu parte de la grabación: «cuando seleccionas esto, la mesa ya cierra solicitudes, o sea, ya inicia». ¿Llegar a la fecha de inicio cierra las postulaciones solo, o el cierre es siempre manual (C3)? *Cliente.*
- **C2** — Los rangos se pisan o dejan huecos: One-shot 1–2, Corta 4–6, Mediana hasta ~24 y Larga más de 6 meses. ¿Dónde caen 3 sesiones, o 7 a 23? ¿Las clases se definen en sesiones o en meses? *Cliente.*
- **C2** — ¿La lista de clases es fija (un enum en el código) o la configura un admin (`system_settings`, ADR-17 «Configuración del sistema y auditoría»)? *Usuario.*
- **C2 + S2** — Sin `total_sessions`, ¿hasta dónde se generan las filas de sesión? Opción: una ventana móvil (las próximas N semanas) que se renueva sola. ¿Y `total_sessions` desaparece o queda opcional para quien sí sabe cuántas son? *Usuario.*
- **C2** — El texto libre ("de 1 a 4 sesiones aprox."), ¿es opcional? ¿Reemplaza a la clase o la acompaña? *Cliente.*
- **C3** — ¿El mínimo es obligatorio? Hoy el máximo puede ser `null` (sin tope); ¿se admite un mínimo sin máximo? *Usuario.*
- **C3** — Cerrar las postulaciones, ¿se puede deshacer? ¿Cerrarlas rechaza a los candidatos que quedan, como pasa hoy al llenarse el cupo (ADR-09 «Postulaciones y veto» §3)? *Cliente.*
- **C3** — Si un jugador se va y la mesa queda por debajo del mínimo, ¿pasa algo, o es solo informativo? ¿Una mesa puede arrancar (`InProgress`) sin llegar al mínimo? *Cliente.*
- **S1** — ¿La frecuencia es de la mesa entera o de cada franja (los lunes semanal, los jueves quincenal)? Lo simple es de la mesa. ¿Hace falta mensual? *Cliente.*
- **S1** — En una quincenal, ¿qué semana es la "1"? Lo natural: la de la fecha de inicio. *Usuario.*
- **S1** — El choque de agendas (ADR-08 «Agenda, sesiones y asistencia» §2 «Dos compromisos chocan si sus intervalos se solapan en la semana UTC») hoy mide sobre una semana. Dos quincenales en semanas alternas no chocan de verdad. ¿Se acepta el falso positivo o se mide sobre dos semanas? *Usuario.*
- **S2** — El contador `Session 12`, ¿cuenta las sesiones jugadas o las programadas? *Usuario.*
- **S5** — Una mesa por texto (*play by post*) no tiene horario, y ADR-07 «Mesas: ciclo de vida» §3 «Una mesa no se crea sin decir qué, dónde y cuándo» exige el «cuándo». ¿Se admite una mesa sin agenda? *Cliente.*

**Primera clase y visibilidad del master (C8, C9, C10)**

- **C8** — El cliente habló de «Table Settings», las configuraciones de la mesa, y de que «son roles que activan ciertas características». ¿Qué otras configuraciones hay, además de ocultar al master? ¿Qué otras características activa la primera clase? *Cliente.*
- **C8** — ¿"Candidatos" incluye a cualquiera que mira el explorador sin postularse? Se asume que sí: el nombre lo ve solo un jugador de la mesa. *Cliente.*
- **C8** — ¿También se ocultan los co-masters (`Secondary`)? ¿Y el enlace al perfil del master, su nombre en las notificaciones al candidato y en la postulación rechazada? *Cliente.*
- **C8** — Un jugador que deja la mesa, ¿deja de ver el nombre? El admin lo ve siempre (ADR-06 «Roles y alcance» §8). *Usuario.*
- **C9** — Si el admin rechaza la primera clase pero la mesa está bien, ¿se publica como mesa normal o vuelve a cambios solicitados? ¿Una mesa normal se puede pasar a primera clase después? *Cliente.*
- **C10** — ¿Dos listas separadas, dos pestañas, o una lista con un filtro por tipo? ¿La primera clase se destaca visualmente? *Usuario*, con la skill `diseno`.

**Cierre de la mesa y calificación (C11, C12, U8)**

- **C11 + U8** — ¿Hacen falta estados nuevos (`FinishRequested`, `CancelRequested`), como existe `PauseRequested`? Si el admin rechaza, ¿la mesa sigue como estaba? *Usuario.*
- **U8** — ¿Desde qué estado exacto cancelar pasa a necesitar revisión? Tu propuesta: libre en `Draft` y `Preparation`, con revisión desde `Opened`. ¿Y `Unassigned` y `ChangesRequested`, que hoy también se cancelan? *Usuario.*
- **U8** — «La de Star sigue apareciendo»: ¿en qué lista? Las mesas canceladas están a propósito en el Historial (`HISTORY_STATUSES`), y puede que fuera eso. *Recorrido.*
- **C12** — **La duda más grande**: "calificar la mesa", ¿es una calificación de la mesa o del master (estrellas, reseña), o son los comentarios y el karma de ADR-11 «Comentarios, karma y feedback», que evalúan **a personas** con las que se jugó? Son modelos distintos. *Cliente.*
- **C12** — Si se califica al cancelar, ¿mueve karma? ADR-11 §4 dice que no justamente para que una cancelación no castigue. *Cliente* (es P3).

**Zona horaria y horas (C13, U1, U2)**

- **U1** — «Que no marque AM/PM», ¿significa que **falta** el AM/PM o que **no debe** aparecer? En México se usa el reloj de 12 horas; la otra opción es seguir el formato del idioma. *Usuario.*
- **C13** — ¿La zona se pide en el onboarding junto con el país (ADR-10 «Perfiles» §4), con la del navegador como sugerencia, o se elige después en el perfil? *Usuario.*
- **C13** — En una mesa con gente de varios países, ¿cada uno ve las horas en su zona, o también la zona del master como referencia (como el `CST` de StartPlaying)? *Cliente.*
- **U2** — ¿Qué pantalla exacta es el «seguimiento» de la revisión: el historial de la mesa en el lado del master o la ficha del pedido en el lado del admin? *Recorrido.*

**Archivos y peticiones (C4, C5, U3–U6)**

- **C4** — ¿Qué se previsualiza? Lo razonable: imágenes y PDF dentro de la app, y lo demás (`.docx`) solo se descarga. ¿La preview va solo en «Usar» o en todos los lugares donde aparece un archivo? *Usuario.*
- **C5** — Las peticiones a una sola persona (`Single`), ¿van con las de los jugadores o en un grupo propio? *Usuario.*
- **U3** — No se identifica en la grabación qué botón no hacía nada; fue justo antes de hablar de la preview, en la pantalla de archivos. *Recorrido.*
- **U5** — Ver el cajón de origen en el selector, ¿le sirve al master o lo confunde? Lo que confundió al cliente fue ver cajones de jugador («Solicitud de jugador») donde el master elige un archivo. *Usuario.*
- **U5** — El cliente dijo «ahí solo puedes utilizar uno». El form de la petición acepta varios archivos; ¿era la entrega del jugador, la postulación o el selector? *Recorrido.*
- **U6** — ¿Qué se muestra de una petición sin abrirla (descripción, fecha límite, a quién va, archivos, cuántos respondieron)? ¿Qué botón se oculta y en qué estado? *Usuario.*

**Pantallas que hay que señalar (U7, U10)**

- **U7** — ¿Qué se mezcla con qué en la pantalla de revisión? En la grabación solo queda «se mezcló con una solicitud». *Usuario.*
- **U10** — ¿Qué pantalla se ve mal en el tema claro? *Recorrido.*

**Roles, pedidos y operación (U9, U11–U14, C14, C15)**

- **U9** — Aprobar `TableOpen`, ¿qué tiene que producir? Opciones: un `Draft` de una sola mesa a nombre de quien la pidió, sin darle el rol `Master` (encaja con la regla dura 17 «El rol no es la pertenencia»), u otorgar el rol. *Cliente.*
- **U9** — ¿El jugador ya tiene en su UI dónde pedir ser master? El backend lo soporta (`MasterGrant`). *Recorrido.*
- **U12** — ¿En qué listas va la selección múltiple, y para qué acciones además de borrar? *Usuario.*
- **U13** — El catálogo de sistemas y plataformas, ¿es solo de prueba (`TestDataService`, perfiles `dev,test`) o el catálogo real de producción? El real iría en una migración de datos. *Usuario.*
- **U14** — El cliente nombró el service worker, que es notificación **del navegador**, fuera de la app. ADR-18 «Notificaciones y tiempo real» §1 dice «solo in-app», así que es otra decisión, distinta del tiempo real de §2. *Cliente.*
- **C14** — En la grabación, el cliente leía algo en pantalla («esto dice que el código va a cometer un error y cuando lo cierres se va a cerrar el anterior modal»), probablemente una respuesta de Claude, no la app. Hay que reproducir el bug antes de corregirlo. ¿Y hacer clic fuera de un form con datos debería cerrarlo, o solo el botón Cancelar? *Recorrido* y *usuario.*
- **C15** — ¿El servidor Ubuntu es del cliente o es una nube (Oracle)? ¿Quién paga y administra la infraestructura? ¿El CI/CD sobre GitHub Actions? ¿Hay dominio? *Cliente.*
- **C16 + S6** — ¿Qué categorías arrancan (temas, estilo de juego, content warnings, experiencia, idioma, edad)? ¿Un tag puede estar en varias? ¿Quién escribe las definiciones? *Cliente.*

### 2.6 Con qué modelo se planea y se ejecuta cada tarea

Lo pide ADR-01 «Proyecto, proceso y conocimiento» §13 «Cada tarea de un plan dice con qué modelo se planea y con cuál se ejecuta»: primero se planea cada punto con el modelo de la columna «Planea», y con el plan aprobado se cambia al de «Ejecuta». Fable 5.1 planea lo más complejo, Opus 5.5 lo demás, y Sonnet 5.5 ejecuta lo de volumen ya decidido. Escribir la ADR de cada decisión, con `escriba-adr`, es siempre Opus 5.5.

**La reunión con el cliente (§2.5)**

| Tarea | Planea | Ejecuta | Por qué |
|---|---|---|---|
| C1, C2 con S2, C3, S1, S3 y S4: agenda, frecuencia, duración y cupo | Fable 5.1 | Opus 5.5 el backend; Sonnet 5.5 el form y las tarjetas | Cruza ADR-07 «Mesas: ciclo de vida», ADR-08 «Agenda, sesiones y asistencia» y ADR-09 «Postulaciones y veto», cambia el modelo de datos y cómo se generan las sesiones: un error ahí corrompe el calendario de todas las mesas |
| S5: mesas por texto, sin agenda | Fable 5.1, junto con la anterior | Opus 5.5 | Toca la regla de que toda mesa dice cuándo se juega |
| C8, C9 y C10: primera clase y master oculto | Fable 5.1 | Opus 5.5 el backend, con `revisor-seguridad`; Sonnet 5.5 la vista del jugador | Es seguridad: el nombre tiene que faltar en todas las respuestas que llegan a un candidato, no solo en una |
| C11 y U8: finalizar y cancelar con revisión | Opus 5.5 | Sonnet 5.5 | El patrón ya existe —la pausa con aprobación— y se replica |
| C12: calificar al terminar y al cancelar | Fable 5.1, cuando el cliente responda P3 | En F5 «Comunidad» | Redefine el modelo de comentarios y karma de ADR-11 «Comentarios, karma y feedback» |
| C13, U1 y U2: zona horaria y formato de las horas | Opus 5.5 | Sonnet 5.5 | Una columna, un selector y pasar todo el formateo por `lib/date.ts` |
| C6 y C7: el español | Opus 5.5 (el tono y un glosario corto) | Sonnet 5.5, en paralelo por archivo de `locales/es/` | Puro volumen una vez fijada la variante |
| C4: preview de archivos | Opus 5.5 | Sonnet 5.5 | Una decisión chica sobre qué tipos se previsualizan; el resto es UI |
| C5 y U6: peticiones agrupadas y con detalle | Sonnet 5.5 | Sonnet 5.5 | Ajuste de UI ya decidido |
| C16, S6 y S10: tags con categoría y secciones de la mesa | Opus 5.5 | Sonnet 5.5 | Toca el modelo de catálogos, pero sin cruzarse con otras ADRs |
| C15: despliegue y CI/CD | Opus 5.5, cuando el cliente responda | Sonnet 5.5 | El pipeline y los scripts son volumen; dónde corre es lo que se decide |
| C14: el doble modal | Opus 5.5 (reproducir y encontrar la causa) | Sonnet 5.5 | Diagnosticar es lo difícil; el arreglo es local a `FormDialog` |
| U3 y U10: el botón que no hace nada y el tema claro | Sonnet 5.5 | Sonnet 5.5 | Encontrarlos y corregirlos, sin decisión |
| U9: qué produce aprobar `TableOpen` | Opus 5.5 | Sonnet 5.5 | Una decisión de producto con el circuito de `Unassigned` ya hecho |
| U12 y U13: selección con Shift y catálogo de prueba | Sonnet 5.5 | Sonnet 5.5 | Chicas y decididas |
| U11, U14 y S9: métricas del Owner, push y ficha del master | Se asigna al planear F5 «Comunidad» y F6 «Operación» | — | Son alcance de otra fase |

**La deuda de §2.1, por rebanada**

| Rebanada | Planea | Ejecuta | Por qué |
|---|---|---|---|
| F4.1 «El mapa de la interfaz»: estados, 375 px, temas, inglés renderizado | Opus 5.5 (el triaje) | Sonnet 5.5, recorriendo con Playwright | Mirar muchas pantallas es volumen; decidir qué es bug, no |
| F4.2 «La matriz de roles» | Opus 5.5 | Sonnet 5.5 escribe los tests de la matriz | La matriz se define una vez y los casos se repiten |
| F4.3 «Integridad»: concurrencia y `TestDataService` | Opus 5.5 | Opus 5.5 | Los IT de concurrencia contra MySQL real son sutiles: un test mal hecho pasa siempre |
| F4.4 «Seguridad» | Fable 5.1 | Opus 5.5, con `revisor-seguridad` | Seguridad, y cruza todas las vías de lectura |
| F4.5 «La revisión mano a mano» | Opus 5.5, con el usuario | Sonnet 5.5 para los cambios que salgan | Es una conversación; lo que se decide en ella después es UI |
| Crear y editar una mesa con bloques compartidos | Opus 5.5 | Sonnet 5.5 | Elegir los bloques es diseño; moverlos es volumen |
| Mover los paquetes a `features/` y renombrar `queue` | Opus 5.5 | Sonnet 5.5 | Mecánico, con `./mvnw verify` como red |
| El barrido de las citas | Opus 5.5 | Sonnet 5.5, un agente por feature | El mayor volumen de F4, sin decisiones una vez fijado el criterio |

## 3. Las seis rebanadas

F4 no usa el procedimiento de `plan-desarrollo.md` §7 —no hay A1 ni A2, porque no se construye producto—. Lo que sí conserva, y es lo que importa, es el corte: **una rebanada no arranca sin que la anterior tenga sus hallazgos escritos y triados.**

**Cada hallazgo se triaje en uno de tres, siempre, sin una cuarta categoría:**

1. **Bug** — se corrige en F4, con su test de regresión.
2. **Alcance de F5 o F6, anotado a propósito** — con la fase donde vive.
3. **Decisión nueva** — va a su ADR de `docs/adr/`, como sección nueva o reescribiendo la que cambia (agente `escriba-adr`). Un hallazgo que cambia una regla no es un bug: es una decisión que nadie había tomado.

---

### F4.0 — Documentación y conocimiento

**Por qué primero:** las cinco que siguen revisan el código **contra los documentos**. Si el documento está vencido, repite lo que ya dice otro o se contradice con él, cada hallazgo se discute dos veces: ¿está mal el código o está mal el papel?

**Qué se produce:**

- **Se borra lo que ya cumplió su función.** Los documentos de implementación de F1, F2 y F3 (su deuda pasó a §2.1), los pendientes M1–M32 colapsados a un índice, lo histórico de `plan-desarrollo.md` y las secciones de `arquitectura.md` que repiten `CLAUDE.md`. Lo normativo que vivía ahí —la matriz `Admin`/`Owner` de F3— se rescata **antes** de borrar.
- **Arquitectura y modelo de datos pasan a ser skills del repo**, con reglas operativas en `SKILL.md` y el detalle en `references/`. Lo que Claude sabía del proyecto solo por su memoria local —el entorno, los tropiezos de colima y de JDT— pasa a una skill versionada, donde no se pierde ni depende de una máquina.
- **Ningún Javadoc o JSDoc miente.** El caso que abre la lista es el de `OnboardingPage`, que describe una guardia que no existe.
- **Las citas a documentos van por `§` o por `ADR-NN §k`, nunca por número de línea.** Cualquier edición del documento las rompe en silencio, y al abrir F4 había ~15 archivos citando `fase-3-admin-owner.md:110` y similares.

**Terminada cuando:** ninguna cita de código apunta a un documento borrado o a un número de línea, y las skills nuevas cargan sus `references/`.

**Avance.** Hecho: los documentos de F1–F3 borrados con lo normativo rescatado en `roles-y-alcance.md`; M1–M32 colapsados a un índice; §3 de `plan-desarrollo.md` resumida; las skills `arquitectura-backend`, `arquitectura-frontend`, `modelo-datos` y `entorno-local` creadas y las cuatro de procedimiento desduplicadas; ~320 citas del código reescritas a su sección nueva, sin ninguna por número de línea; la memoria local reducida a lo que no es del proyecto; las 8 citas al «contrato de F3.3» —un documento de trabajo que **nunca se versionó**— apuntan ahora a `arquitectura` §2.2 o a su `#n`, y la regla del N+1 que citaban, que no había llegado a ninguna skill, quedó escrita en §2.2 a partir del código y de los ITs que la miden. **Falta**: el barrido de Javadoc/JSDoc que miente (el primero, `OnboardingPage`, está anotado en §2.2; el segundo, `AdminUserApiIT`, se declara «currently red» por un hueco de `GlobalExceptionHandler` que ADR-04 §5 ya cerró), las **7 citas a los contratos de F3.1, F3.2 y F3.4**, que tampoco existen en el repo (`UserRoleServiceTest`, `AdminUserServiceTest`, `AdminUserApiIT`, `ApprovalServiceTest`, `ApprovalSearchFieldTest`, `ApprovalService`, `RegistrationService`), y poner al día los diagramas ER.

---

### F4.1 — El mapa de la interfaz

**Por qué primero:** las cuatro que siguen preguntan «¿esto se alcanza?» y «¿desde dónde?». Sin el mapa, cada una lo redescubre por su cuenta.

**Es la rebanada que el cliente pidió por nombre: qué pantallas hay, cómo están conectadas, y qué quedó flotando en el aire.**

**Qué se produce:**

- **El ledger de rutas**: cada ruta del sitemap de `diseno` §2 con cinco columnas — su guard, **desde qué pantalla se llega navegando** (no escribiendo la URL), qué enlaces salen de ella, si está construida, y **qué e2e la visita**. Una ruta que ningún e2e recorre puede romperse sin que nadie se entere.
- **El mapa de navegación**, por contexto: Jugador, Master, Admin y las transversales. Dibujado, no listado: lo que se busca son los nodos sin arista de entrada. **Ya existe**: los diagramas 21–24 de `docs/diagramas/` (ADR-01 §10), un nodo por ruta.
- **Las guardias como objeto de revisión**, no como supuesto: la de sesión, la de onboarding, qué hace `/login` con sesión, y qué pinta un prefijo de contexto sin ruta índice.
- **El inventario de lo que quedó flotando**, en las dos direcciones:
  - **Pantallas sin puerta**: alcanzables solo escribiendo la URL.
  - **Callejones sin salida**: pantallas de las que no sale ningún enlace a otra, sobre todo cuando muestran una entidad que tiene su propia pantalla.
  - **Cruces de contexto**: un enlace de un contexto que aterriza en el prefijo de otro que el lector puede no tener.
  - **Endpoints sin pantalla**: construidos, autorizados, y que ninguna interfaz llama.
  - **Hooks montados en cero lugares.**
  - **Valores de enum que ningún código produce.**
  - **Tipos de notificación que no llevan a ningún lado** — el caso que F1.7 encontró y F2.4 cerró; se vuelve a barrer entero. **Y ahora también los que llevan al lugar equivocado**: tener destino no alcanza, tiene que ser la pantalla o la pestaña que contiene lo que la notificación anuncia.
  - **La coherencia de `config/paths.ts`**: builders sin llamador, constantes que nadie usa, URL escritas a mano que se saltan los builders.
  - **Textos de i18n sin usar**, y su inverso: claves usadas que no existen en `en`. Incluye **cada código de error del backend** (ADR-23 §3): todo código que un endpoint puede devolver tiene su clave en `es` y en `en`, o el lector ve la clave cruda justo cuando algo salió mal.
  - **Valores de estilo sueltos** fuera del `@theme` (regla dura 18).
- **Los cuatro estados obligatorios** de cada pantalla —cargando, vacío, error, sin permiso—, verificados uno por uno, **en tema claro y oscuro y a 375 px de ancho**. Es el punto 5 viejo de §6, hecho de una vez sobre el producto entero.

**El instrumento es un artifact**, como el de F1.7: el ledger es largo y se lee mejor como página que como tabla en markdown. El documento guarda el resumen y los hallazgos; el detalle vive ahí.

**Terminada cuando:** toda ruta del sitemap tiene su fila, todo huérfano tiene su triaje, y ninguna pantalla construida queda sin sus cuatro estados verificados.

---

### F4.2 — La matriz de roles

**Lo que se verifica:** que «quién puede qué» sea lo que los documentos dicen, **probado y no leído de un `@PreAuthorize`**.

- **La matriz completa**: cada endpoint × cada rol. Cuatro roles, más el actor sin sesión, más el actor bloqueado.
- **La matriz `Admin`/`Owner` —la de F3, hoy en la skill `arquitectura`, `references/modelo-datos/roles-y-alcance.md`— termina en una prueba que la recorre.** Una tabla en un documento no impide que alguien escriba `hasRole('ADMIN')` y deje al owner afuera — y eso no se nota en desarrollo, donde el actor de prueba suele ser admin.
- **Pertenencia, que es la otra mitad y la más fácil de olvidar** (ADR-05 §5): el rol correcto sobre el **recurso ajeno**. Un master legítimo pidiendo la mesa de otro master; un jugador pidiendo la postulación de otro; un admin leyendo lo que ADR-06 §8 le permite y lo que ADR-11 §5 no.
- **Los tres contextos se cierran pero no son la autorización** (ADR-20 §1, §2): cada layout redirige a quien no tiene el contexto, y el backend responde `403` igual a quien llama el endpoint directo. La prueba fija las dos mitades: que la interfaz no muestra lo ajeno y que el backend no depende de eso.
- **La exclusión `Admin`/`Owner`** de ADR-06 §1 y sus tres invariantes —ahora en la skill `arquitectura`, `references/modelo-datos/roles-y-alcance.md`—, incluida la que dice que la plataforma nunca se queda sin owner.
- **El contrato de las respuestas**: cada tipo de retorno de `features/*/api` contra el `record` que el backend devuelve de verdad. Un tipo del frontend que dice `void` donde llega un cuerpo no rompe nada hoy, y por eso nadie lo nota hasta que una pantalla necesita ese cuerpo.

**Terminada cuando:** existe una suite que recorre la matriz, cada celda que no coincide con la documentación quedó triada, y cada tipo de respuesta del frontend coincide con su `record`.

---

### F4.3 — Integridad

**Lo que se verifica:** que los datos no puedan quedar en un estado que ninguna pantalla sepa mostrar.

- **Las invariantes que MySQL no sostiene**, todas juntas y con concurrencia real: un solo `Primary` vivo por mesa (ADR-06 §3), una sola postulación activa por par (ADR-09 §1), el cupo y el rechazo automático (ADR-09 §3), la reserva de la bandeja (ADR-16 §5) y la que F3 estrena, que la plataforma no se quede sin `Owner`.
- **Las referencias huérfanas que ADR-16 §2 obliga a vigilar.** La referencia polimórfica de `approval_requests` no tiene FK, así que la integridad es del service — y la verificación periódica que la decisión exige se prueba acá.
- **La coherencia del borrado lógico** (ADR-03 §7), que es transversal y por eso no es de nadie: una fila `Deleted` tiene que ser invisible en **todos** los caminos de lectura, no en los que alguien se acordó. Mesas (ADR-07 §6), postulaciones, vínculos de catálogo (ADR-04 §7), archivos, filas de `masters` (ADR-07 §8).
- **La cadena de claves foráneas que rompió la limpieza del e2e cinco veces** —F1.2 la agenda, F1.3 el calendario, F1.4 los archivos de mesa, F1.5 las entregas y F2.2 los archivos de la postulación, todas en `TestDataService` (ADR-07 §9, ADR-24 §3)—. **Cinco veces es una clase de error que merece una barrera, no una corrección más**, y el motivo es cómo se manifiesta: la limpieza responde `500`, nadie lo mira porque las pruebas ya terminaron, la base se llena, y **la corrida siguiente falla por paginación en un lugar que no tiene nada que ver** — en F2.2 fueron 16 pruebas en rojo y un admin buscando una fila que había quedado fuera de la primera página. Acá se verifica que el orden de borrado cubra el grafo entero, y **se decide la barrera**: el candidato obvio es que la limpieza falle ruidosamente en vez de en silencio, o un test que recorra el grafo de `@Entity` y exija que toda tabla puente esté en la lista.
- **Lo derivado contra lo guardado** (ADR-03 §1, ADR-14 §3): los conteos de jugadores, la asistencia agregada (ADR-08 §5) y los usos de un archivo se derivan y no se cachean. Se comprueba que ninguna ruta haya introducido una copia.

**Terminada cuando:** cada invariante tiene su test de integración con Testcontainers, y el barrido de borrado lógico cubrió cada entidad con `deleted_at`.

---

### F4.4 — Seguridad

**Lo que se verifica:** que lo que no se puede ver, no se vea — y que negar no confirme.

- **IDOR por recurso**, sistemáticamente: para cada entidad con id en la URL, el actor equivocado recibe la respuesta correcta. **Y la respuesta correcta a veces es `404` y no `403`**, que es una regla de este proyecto y no una preferencia: el veto (ADR-09 §4), la mesa borrada (ADR-07 §6) y el perfil caducado (ADR-10 §3) lo niegan sin confirmar que existan.
- **Las vías de lectura de un archivo**, que para F4 son siete y llegaron de a una: propio, publicado, adjunto privado de una mesa que dirigís, compartido por una mesa (ADR-14 §2), entregado a una petición (ADR-14 §2), adjunto de un pedido (ADR-15 §3) y adjunto de una postulación (F2.2). **Cada una se prueba con el actor que no debería pasar**, y con el vetado que F3.4 agregó a todas. El número no se da por bueno: se cuenta contra el Javadoc de `FileService.requireReadable`, que es donde viven de verdad.
- **La lista blanca del sanitizador** (ADR-03 §8, ADR-05 §6) sobre los tres campos de texto enriquecido, al guardar y al servir. Es la superficie de XSS más directa del sistema.
- **El circuito de sesión** (ADR-05 §2): el access token en memoria y nunca en `localStorage`, el refresh rotativo en cookie `httpOnly`, el CSRF activo solo en `/auth/refresh`, el reintento único ante `401`, y que el token de Discord se descarte al terminar el callback.
- **Que el JWT no autorice** (ADR-05 §3): los roles se releen de la base en cada request, y un rol quitado deja de valer dentro de la ventana de la caché de ADR-05 §3 — que el bloqueo de F3.1 tiene que invalidar en el momento.
- **Que ningún `Map<String, Object>` cruce HTTP** (regla dura 3) y que ningún endpoint devuelva más de lo que la pantalla necesita.
- **El doble de login de pruebas** (`TestLoginController`, ADR-24 §1, §2) **no existe fuera del perfil `test`**. Es la verificación más barata de esta rebanada y la más cara de olvidar. Lo mismo para sus hermanos `TestDataController` y el Discord falso de `TestDiscordController`.
- **Las dependencias.** Al abrir F4, GitHub reporta 117 alertas de Dependabot (6 críticas). Se separan las de `legacy/` —que se borra al alcanzar paridad y no se despliega— de las de `backend/` y `frontend/`, y cada una de estas se triaje como las demás: se sube la versión (una major es decisión, regla dura 15), se justifica que no aplica, o se anota.
- **El rate limiting, que no existe ni tiene decisión.** Ningún documento ni decisión lo menciona, y hay superficies que lo piden: el login con Discord, las escrituras que cualquier `Player` puede repetir (postulaciones, y en F5 los comentarios anónimos) y la subida de archivos. Su triaje es **decisión nueva** (categoría 3): qué endpoints, con qué límite y dónde vive el contador. No se agrega código antes de esa decisión.

**Fuera de alcance, con su motivo:** el anonimato de los comentarios (ADR-11 §5, ADR-06 §8) no se puede verificar porque los comentarios son **F5**. Queda anotado como la primera línea de la revisión de esa fase.

**Terminada cuando:** cada vía de lectura tiene su prueba negativa, y ninguna negativa devuelve un código que confirme lo que niega.

---

### F4.5 — La revisión mano a mano

**Es la rebanada del cliente, no de un agente.** Las cuatro anteriores producen el instrumento; esta es la pasada humana sobre el producto real.

**Cómo se ejecuta:**

- Se recorre el producto **navegando**, con el mapa de F4.1 al lado, con un actor por rol armado desde el `DevPanel` (ADR-19 §9).
- **Cada hallazgo se anota en el momento**, con la pantalla, lo que se esperaba y lo que pasó. Un hallazgo que se recuerda al final se recuerda mal.
- **Cada hallazgo se triaje en los tres de §3**, uno por uno, sin dejar ninguno «para ver después».
- Lo que se corrige, se corrige **con su test de regresión**: si un humano lo encontró una vez, un test tiene que encontrarlo la próxima.

**Terminada cuando:** el registro de hallazgos está cerrado — cada uno corregido con su commit, o anotado con su fase y su motivo.

## 4. Lo que F4 explícitamente NO hace

| Queda fuera | Por qué |
|---|---|
| Construir pantallas o endpoints nuevos | F4 verifica. Una pantalla que falta es un hallazgo, y su fase es F5 o F6 |
| Escribir los tests unitarios que las fases debían escribir | Si falta uno, es un hallazgo y su triaje es «bug»: la fase no cumplió el punto 2 de §6 |
| Rediseñar | Un cambio de diseño es una decisión (ADR-01 §3, categoría 3), y se toma con su número antes de tocar nada |
| Verificar comentarios, karma y feedback | Son **F5** y todavía no existen |
| Verificar auditoría, tiempo real y «ver como» | Son **F6** |
| Optimizar rendimiento | No está en el alcance de ninguna fase todavía, y meterlo acá lo convertiría en trabajo sin destino |

## 5. Cómo se mide que sirvió

Una fase de revisión que termina sin hallazgos no probó que el producto esté bien: probó que la revisión fue floja. Las tres señales de que F4 hizo su trabajo:

1. **El inventario de huérfanos está vacío o justificado**, ítem por ítem. Cero ítems sin triaje.
2. **La matriz de roles existe como suite y corre en verde**, y cada celda que no coincidía con los documentos terminó en una corrección o en una decisión.
3. **Todo hallazgo corregido tiene su test de regresión.** Es la diferencia entre haber revisado y haber arreglado: sin el test, el mismo bug vuelve en F5.
4. **La documentación que queda describe el código de hoy.** Ninguna cita rota, ningún bloque de Javadoc o JSDoc que afirme algo que el cuerpo no hace, y ninguna regla escrita en dos lugares.

## 6. Verificación

Se prueba **contra el backend y el frontend que ya están corriendo** — no se levantan instancias paralelas.

```bash
cd backend && ./mvnw test          # unitarios, sin Docker
cd backend && ./mvnw verify        # + Testcontainers (colima arriba)
cd frontend && npx tsc -b          # typecheck strict
cd frontend && npm run test        # Vitest
cd frontend && npm run test:e2e    # Playwright contra el backend real
cd frontend && npm run format      # prettier del repo (ADR-02 §4)
```

**Nunca `./mvnw clean` con el backend levantado**: borra `target/classes` bajo el proceso vivo, el backend se cae, y la suite e2e falla entera como si el código se hubiera roto.
