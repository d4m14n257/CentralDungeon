# F4 — Revisión: implementación

> **Cómo se revisa lo construido.** F4 no entrega producto: entrega **saber qué hay, quién lo alcanza y qué está roto**, con nombre y apellido.
>
> Nace de #250. `plan-desarrollo.md` §4 dice qué cubre; acá está el detalle de las seis rebanadas y el instrumento de cada una.
>
> **Documento vivo mientras F4 esté abierta.** Cada rebanada se marca terminada acá con sus hallazgos y su triaje.
>
> **No confundir con F3.5**, que es la quinta rebanada de F3 —la configuración editable de #141 y #265— y está cerrada. La fase de revisión entre F3 y F5 es esta, F4.

## 1. Por qué existe esta fase

F1.7 intentó la revisión por fase y funcionó: encontró dos huecos de navegación reales y los corrigió. Pero encontró **lo que podía encontrar**, porque revisó F1 contra F1.

Lo que de verdad se rompe está en las costuras entre actores, y **una costura no se puede mirar hasta que existen sus dos lados**:

- **La matriz de roles** no tiene respuesta hasta que los cuatro roles están construidos. Revisarla en F1 es revisar un tercio y darla por buena.
- **Una prueba de IDOR necesita dos actores con derechos distintos.** Con un solo rol construido no hay contra quién probar.
- **El veto de F3.4 excluye al vetado de seis vías de lectura**, y una de ellas —los archivos— la anotó #206 durante F1.4, meses antes de que el veto existiera.
- **El mapa de la interfaz completo** no existe hasta que existen todas las pantallas. Un huérfano en F1 podía ser «alcance de F2 anotado a propósito»; en F4 ya no hay fase siguiente que lo justifique.

Y hay un recurso escaso que es **la atención del cliente**. Gastarla tres veces sobre tres productos parciales, y otra vez sobre el entero, desperdicia justamente las pasadas que importan. F4 la concentra en una.

**Lo que F4 no es.** No es «escribir los tests que las fases no escribieron». Toda regla de negocio llegó con su test unitario escrito por quien la escribió — eso no se movió y no se negocia (`plan-desarrollo.md` §6). F4 escribe el nivel que ningún constructor podía cubrir solo: el que cruza fases, roles y actores.

## 2. Con qué empieza

**No empieza en cero.** Cada fase cerró entregando su **deuda de revisión** —el punto 5 nuevo de `plan-desarrollo.md` §6—, así que F4 arranca con una lista escrita en vez de redescubriendo. Los documentos de implementación de F1, F2 y F3 se borraron en F4.0 (git conserva la historia); **sus tres listas viven ahora acá**, juntas y ordenadas por la rebanada que las verifica.

### 2.1 La deuda heredada de F1, F2 y F3

| Deuda | De dónde viene | La verifica |
|---|---|---|
| Los cuatro estados de cada pantalla de F1, F2 y F3, el viewport de 375 px y el contraste de lo nuevo, verificados y no asumidos | punto 5 viejo de §6, que #250 movió acá; repetido en cada rebanada de F3 | F4.1 |
| Que cada pantalla sea alcanzable **navegando**. F2 solo lo comprobó para `/player/history` | F1.7, F2 | F4.1 — resuelto como mapa en 21–24 (#267); falta el triaje |
| El `<Dialog>` del historial de roles en móvil, que según #138 debería ser sheet desde abajo | F3.1 | F4.1 |
| El badge `Vetado` y el aviso de retroactividad **renderizados** en los dos temas: los tokens están medidos, la pantalla no | F3.4, F3.5 | F4.1 |
| El inglés renderizado: la paridad de claves está verificada, pero los tests corren en `es` | F3.3, F3.5 | F4.1 |
| `/admin/requests` pone su default `Pending` en la caja y no en la URL: se aparta de #185, y un criterio tipeado se **suma** al chip | F3.2 | F4.1 — es decisión de producto |
| `PlayerBan` visible en `/admin/requests` pero no resoluble ahí: que la pantalla **no ofrezca** los botones a un admin no se miró | F3.4 | F4.1 |
| `tables.max_players_cap` no se enuncia al escribir el cupo: el formulario no lo pide a `useClientLimits` (principio 2, #264) | F3.5 | F4.1 |
| ~~`pageSize.adminQueue` sirve a dos pantallas y el nombre quedó significando la otra~~ — **resuelto en §2.3**: ahora es `pageSize.admin` y sirve a las seis (#271) | F3.3 | F4.1 — cosmético |
| `GET /api/v1/files/{fileId}` —solo se consume `/content`— y los hooks montados en cero lugares (`useCatalogValue`, `useUploadFile`) | F1.7, confirmado por el barrido de F4 | F4.1 |
| La matriz de visibilidad de perfiles probada con `Player`, `Master` y `Admin`: falta `Owner` y falta el vetado | F2 | F4.2 |
| Las tres cosas que se llaman «owner» | F3 §7 | F4.2 |
| La evicción de las cachés `userAuth` y `systemSettings` observada **como caché**: se probó su efecto por HTTP, no la caché | F3.1, F3.5 | F4.2 / F4.4 |
| Idempotencia de dos revocaciones simultáneas sobre la misma persona: responde `200` y `409`, aceptado a propósito (#252) | F3.1 | F4.2 — confirmar que sigue siendo lo querido |
| `registration_files` frente al borrado lógico: #247 decidió que la fila sobreviva, y no se barrió qué otras lecturas podrían devolverla | F2 | F4.3 |
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

Al dibujar los diagramas 21–24 (#267) se barrieron `router.tsx`, cada `Link`, `NavLink`, `navigate` y `Navigate`, `config/paths.ts`, `notificationTarget.ts`, los 131 handlers del backend contra sus llamadores y los enums compartidos. **Son hallazgos sin triar**: F4.1 los clasifica en los tres de §3.

| Hallazgo | Evidencia |
|---|---|
| **No hay guardia de onboarding.** Solo el callback manda a `/onboarding`; cualquier otra entrada lo salta. Y el JSDoc de `OnboardingPage` afirma que el redirect vive en la guardia de sesión | `RootLayout.tsx`, `OAuthCallbackPage.tsx` |
| `/login` no redirige a quien ya tiene sesión | `LoginPage.tsx` |
| ~~`/admin` a secas pinta el layout con un `Outlet` vacío: ni índice, ni redirect, ni 404~~ — **resuelto en §2.3** (#269, #270) | `router.tsx` |
| `TableChangesRequested` y `TableApproved` caen en la pestaña Candidatos, aunque el comentario promete la de estado (#244) | `notificationTarget.ts` |
| Un `PlayerBan` resuelto manda al co-master a `/player/profile`; un `TablePause` resuelto manda al master a la vista de jugador de su mesa | `notificationTarget.ts` |
| Las pestañas del master enlazan a `/player/users/:id`, y el `UserMenu` manda a `/player/profile` a una cuenta que solo es admin: cruces de contexto | `MasterTableCandidatesTab`, `MasterTablePlayersTab`, `UserMenu` |
| Ninguna de las siete pantallas de admin tiene un `Link`: un admin no puede abrir el detalle de una mesa, el perfil de un usuario ni la entidad de un pedido | `routes/admin/*` |
| Las filas de `/player/history` no enlazan a la mesa terminada | `PlayerHistoryPage` |
| `/my/files` se esconde sin biblioteca: una cuenta solo admin u owner no tiene puerta | `UserMenu` |
| Tres builders de `paths.ts` sin llamador; las constantes de patrón que el router ignora aunque el JSDoc diga que son para él; `helpPlayers`/`helpMasters`/`helpAdmins` sin ruta; tres URL escritas a mano | `config/paths.ts`, `NotificationBell`, `api/client.ts`, `devApi.ts` |
| Dos tipos del frontend que no coinciden con lo que devuelve el backend: `requestBlock` tipado `void` (devuelve `ApprovalRequestDetailResponse`) y `banRequestsApi.approve` tipado `UnreadBody` (devuelve `RegistrationResponse`) | `registrationsApi.ts`, `approvalsApi.ts` |
| Valores de enum que ningún código produce: `TableApprovedWithChanges`, `SubmissionStatus.Pending`, `UserStatus.Deleted`, `RegistrationFileStatus.Removed`, `SubmissionFileStatus.Deleted` | los enums del backend y sus espejos |
| **117 alertas de Dependabot** en la rama por defecto (6 críticas, 59 altas) | aviso de GitHub al hacer push |
| **Los diagramas ER quedaron atrás de las migraciones.** El de `modelo-datos.md` §3 no tiene `file_categories` ni `task_files` (V9), y los `.mmd` por subsistema no tienen `user_role_changes`, `user_status_changes` (V11) ni `registration_status_changes` (V12). La tabla de migraciones del DDL llegaba hasta V9; F4.0 la completó hasta V13 | `docs/modelo-datos.md`, `docs/diagramas/11`–`16` |
| `er-diagram-sync` pedía que el DDL «refleje el estado final acumulado», pero el DDL es el `V1__baseline.sql` literal más una tabla de migraciones. Corregido en F4.0 | la skill |

### 2.3 Cambios de la revisión mano a mano

**Registro vivo** de lo que el usuario cambia mientras recorre las pantallas. Cada fila ya viene triada —casi siempre como **decisión nueva**, porque lo que se corrige es una regla que nadie había cuestionado— y lo estético queda además en `frontend-diseno.md`, donde se lee antes de construir la próxima pantalla. Una fila nueva por cambio; lo que cambia de nuevo se tacha y se agrega, no se reescribe.

| Cambio | Triaje | Dónde quedó |
|---|---|---|
| **Cada contexto se cierra a quien no lo tiene y lo devuelve a su home**, sin mostrar un 403: un Player que escribe `/admin/users` o `/master` vuelve a `/player`; una cuenta solo-master que escribe `/player` vuelve a `/master`. `/master/tables/new` sin el rol `Master` vuelve a `/master`. Sin ningún contexto, `/` explica en vez de hacer bucle | Decisión nueva: **#269**, corrige #103 y #222 | `RequireContext` en los tres layouts; `frontend-diseno.md` §2; skill `arquitectura` §3.1.6 |
| **`/admin` es la home de Admin**: bienvenida sin métricas, donde cae un admin al entrar, primer ítem «Inicio» de la nav | Decisión nueva: **#270**, corrige la home de F3.3 | `AdminHomePage`; sitemap de `frontend-diseno.md` §2; diagrama 24 |
| **Paginación de las tablas de trabajo**: tira numerada compacta, «Ir a…» para saltar de la 1 a la 900, y «Por página» con 10 · 25 · 50 · 100 en `?size=` | Decisión nueva: **#271**, precisa #173 | `PaginationControls`; skill `diseno` §5 «Listas de trabajo»; `design/build.py` |
| **Acciones de fila como íconos con tooltip** en las seis tablas de admin, con un vocabulario de íconos fijo | Decisión nueva: **#272** | `IconAction` en cada `renderActions`; skill `diseno` §5 «Listas de trabajo»; `design/build.py` |
| **Las acciones de fila van en una línea, sin wrap, y un patrón de estilo tiene nombre**: cambiar el contenedor de `/admin/users` no cambiaba las otras tablas porque cada pantalla escribía el suyo. Se nombraron `.row-actions` (que ponen `DataTable`, `CollapsibleSection` y `FileCard`), `.page-title`, `.section-title`, `.section-label`, `.list-divided`, `.list-divided-bare`, `.inline-error` y `PageHeader`, y se barrió todo el frontend | Decisión nueva: **#273** | `styles/base.css`, `components/PageHeader.tsx`; skill nueva `diseno` §5.c; `design/build.py` |
| **La arquitectura es una sola skill**, que incluye el modelo de datos, el testing, la documentación con Javadoc/JSDoc y los procedimientos | Decisión nueva: **#274** | skill `arquitectura`; `CLAUDE.md`, `mcp-y-skills.md` |

**Consecuencias de #269, para triar en F4.1** — dos cruces de contexto de §2.2 que antes llevaban a una pantalla del prefijo ajeno y ahora **redirigen**:

- El `UserMenu` manda a `/player/profile` también a una cuenta sin `Player` (solo admin, solo master): el perfil propio queda inalcanzable para ella.
- Las pestañas Candidatos y Jugadores del master enlazan a `/player/users/:id`: un master sin `Player` que abre un perfil vuelve a `/master`. **Esto rompe #41b para ese master** —ver el perfil de quien se postuló a su mesa— y lo fija en rojo el e2e `profile-visibility.spec.ts` (el paso del master que abre al candidato), que se deja sin tocar a propósito hasta decidir dónde vive el perfil.

Las dos piden lo mismo —que el perfil deje de colgar del contexto Jugador o que cada contexto tenga el suyo— y es una decisión, no un bug.

**Deuda de estilo que dejó a la vista #273, para triar en F4.1**: `text-[11px]` en `NotificationBell` y `WizardSteps` es un valor suelto fuera del `@theme` (regla dura 18) — o se agrega el tamaño a `design/build.py`, o se usa `text-xs`.

## 3. Las seis rebanadas

F4 no usa el procedimiento de `plan-desarrollo.md` §7 —no hay A1 ni A2, porque no se construye producto—. Lo que sí conserva, y es lo que importa, es el corte: **una rebanada no arranca sin que la anterior tenga sus hallazgos escritos y triados.**

**Cada hallazgo se triaje en uno de tres, siempre, sin una cuarta categoría:**

1. **Bug** — se corrige en F4, con su test de regresión.
2. **Alcance de F5 o F6, anotado a propósito** — con la fase donde vive.
3. **Decisión nueva** — va a `decisiones.md` con su número. Un hallazgo que cambia una regla no es un bug: es una decisión que nadie había tomado.

---

### F4.0 — Documentación y conocimiento

**Por qué primero:** las cinco que siguen revisan el código **contra los documentos**. Si el documento está vencido, repite lo que ya dice otro o se contradice con él, cada hallazgo se discute dos veces: ¿está mal el código o está mal el papel?

**Qué se produce:**

- **Se borra lo que ya cumplió su función.** Los documentos de implementación de F1, F2 y F3 (su deuda pasó a §2.1), los pendientes M1–M32 colapsados a un índice, lo histórico de `plan-desarrollo.md` y las secciones de `arquitectura.md` que repiten `CLAUDE.md`. Lo normativo que vivía ahí —la matriz `Admin`/`Owner` de F3— se rescata **antes** de borrar.
- **Arquitectura y modelo de datos pasan a ser skills del repo**, con reglas operativas en `SKILL.md` y el detalle en `references/`. Lo que Claude sabía del proyecto solo por su memoria local —el entorno, los tropiezos de colima y de JDT— pasa a una skill versionada, donde no se pierde ni depende de una máquina.
- **Ningún Javadoc o JSDoc miente.** El caso que abre la lista es el de `OnboardingPage`, que describe una guardia que no existe.
- **Las citas a documentos van por `§` o por `#n`, nunca por número de línea.** Cualquier edición del documento las rompe en silencio, y al abrir F4 había ~15 archivos citando `fase-3-admin-owner.md:110` y similares.

**Terminada cuando:** ninguna cita de código apunta a un documento borrado o a un número de línea, y las skills nuevas cargan sus `references/`.

**Avance.** Hecho: los documentos de F1–F3 borrados con lo normativo rescatado en `roles-y-alcance.md`; M1–M32 colapsados a un índice; §3 de `plan-desarrollo.md` resumida; las skills `arquitectura-backend`, `arquitectura-frontend`, `modelo-datos` y `entorno-local` creadas y las cuatro de procedimiento desduplicadas; ~320 citas del código reescritas a su sección nueva, sin ninguna por número de línea; la memoria local reducida a lo que no es del proyecto. **Falta**: el barrido de Javadoc/JSDoc que miente (el primero, `OnboardingPage`, está anotado en §2.2) y poner al día los diagramas ER.

---

### F4.1 — El mapa de la interfaz

**Por qué primero:** las cuatro que siguen preguntan «¿esto se alcanza?» y «¿desde dónde?». Sin el mapa, cada una lo redescubre por su cuenta.

**Es la rebanada que el cliente pidió por nombre: qué pantallas hay, cómo están conectadas, y qué quedó flotando en el aire.**

**Qué se produce:**

- **El ledger de rutas**: cada ruta del sitemap de `frontend-diseno.md` §2 con cinco columnas — su guard, **desde qué pantalla se llega navegando** (no escribiendo la URL), qué enlaces salen de ella, si está construida, y **qué e2e la visita**. Una ruta que ningún e2e recorre puede romperse sin que nadie se entere.
- **El mapa de navegación**, por contexto: Jugador, Master, Admin y las transversales. Dibujado, no listado: lo que se busca son los nodos sin arista de entrada. **Ya existe**: los diagramas 21–24 de `docs/diagramas/` (#267), un nodo por ruta.
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
  - **Textos de i18n sin usar**, y su inverso: claves usadas que no existen en `en`. Incluye **cada código de error del backend** (#197): todo código que un endpoint puede devolver tiene su clave en `es` y en `en`, o el lector ve la clave cruda justo cuando algo salió mal.
  - **Valores de estilo sueltos** fuera del `@theme` (regla dura 18).
- **Los cuatro estados obligatorios** de cada pantalla —cargando, vacío, error, sin permiso—, verificados uno por uno, **en tema claro y oscuro y a 375 px de ancho**. Es el punto 5 viejo de §6, hecho de una vez sobre el producto entero.

**El instrumento es un artifact**, como el de F1.7: el ledger es largo y se lee mejor como página que como tabla en markdown. El documento guarda el resumen y los hallazgos; el detalle vive ahí.

**Terminada cuando:** toda ruta del sitemap tiene su fila, todo huérfano tiene su triaje, y ninguna pantalla construida queda sin sus cuatro estados verificados.

---

### F4.2 — La matriz de roles

**Lo que se verifica:** que «quién puede qué» sea lo que los documentos dicen, **probado y no leído de un `@PreAuthorize`**.

- **La matriz completa**: cada endpoint × cada rol. Cuatro roles, más el actor sin sesión, más el actor bloqueado.
- **La matriz `Admin`/`Owner` —la de F3, hoy en la skill `arquitectura`, `references/modelo-datos/roles-y-alcance.md`— termina en una prueba que la recorre.** Una tabla en un documento no impide que alguien escriba `hasRole('ADMIN')` y deje al owner afuera — y eso no se nota en desarrollo, donde el actor de prueba suele ser admin.
- **Pertenencia, que es la otra mitad y la más fácil de olvidar** (#121): el rol correcto sobre el **recurso ajeno**. Un master legítimo pidiendo la mesa de otro master; un jugador pidiendo la postulación de otro; un admin leyendo lo que #45 le permite y lo que #43 no.
- **Los tres contextos se cierran pero no son la autorización** (#103, #269): cada layout redirige a quien no tiene el contexto, y el backend responde `403` igual a quien llama el endpoint directo. La prueba fija las dos mitades: que la interfaz no muestra lo ajeno y que el backend no depende de eso.
- **La exclusión `Admin`/`Owner`** de #169 y sus tres invariantes —ahora en la skill `arquitectura`, `references/modelo-datos/roles-y-alcance.md`—, incluida la que dice que la plataforma nunca se queda sin owner.
- **El contrato de las respuestas**: cada tipo de retorno de `features/*/api` contra el `record` que el backend devuelve de verdad. Un tipo del frontend que dice `void` donde llega un cuerpo no rompe nada hoy, y por eso nadie lo nota hasta que una pantalla necesita ese cuerpo.

**Terminada cuando:** existe una suite que recorre la matriz, cada celda que no coincide con la documentación quedó triada, y cada tipo de respuesta del frontend coincide con su `record`.

---

### F4.3 — Integridad

**Lo que se verifica:** que los datos no puedan quedar en un estado que ninguna pantalla sepa mostrar.

- **Las invariantes que MySQL no sostiene**, todas juntas y con concurrencia real: un solo `Primary` vivo por mesa (#73), una sola postulación activa por par (#28), el cupo y el rechazo automático (#34), la reserva de la bandeja (#100) y la que F3 estrena, que la plataforma no se quede sin `Owner`.
- **Las referencias huérfanas que #78 obliga a vigilar.** La referencia polimórfica de `approval_requests` no tiene FK, así que la integridad es del service — y la verificación periódica que la decisión exige se prueba acá.
- **La coherencia del borrado lógico** (#25), que es transversal y por eso no es de nadie: una fila `Deleted` tiene que ser invisible en **todos** los caminos de lectura, no en los que alguien se acordó. Mesas (#175), postulaciones, vínculos de catálogo (#190), archivos, filas de `masters` (#216).
- **La cadena de claves foráneas que rompió la limpieza del e2e cinco veces** —F1.2 la agenda, F1.3 el calendario, F1.4 los archivos de mesa, F1.5 las entregas y F2.2 los archivos de la postulación, todas en `TestDataService` (#171, #172)—. **Cinco veces es una clase de error que merece una barrera, no una corrección más**, y el motivo es cómo se manifiesta: la limpieza responde `500`, nadie lo mira porque las pruebas ya terminaron, la base se llena, y **la corrida siguiente falla por paginación en un lugar que no tiene nada que ver** — en F2.2 fueron 16 pruebas en rojo y un admin buscando una fila que había quedado fuera de la primera página. Acá se verifica que el orden de borrado cubra el grafo entero, y **se decide la barrera**: el candidato obvio es que la limpieza falle ruidosamente en vez de en silencio, o un test que recorra el grafo de `@Entity` y exija que toda tabla puente esté en la lista.
- **Lo derivado contra lo guardado** (#11, #232): los conteos de jugadores, la asistencia agregada (#137) y los usos de un archivo se derivan y no se cachean. Se comprueba que ninguna ruta haya introducido una copia.

**Terminada cuando:** cada invariante tiene su test de integración con Testcontainers, y el barrido de borrado lógico cubrió cada entidad con `deleted_at`.

---

### F4.4 — Seguridad

**Lo que se verifica:** que lo que no se puede ver, no se vea — y que negar no confirme.

- **IDOR por recurso**, sistemáticamente: para cada entidad con id en la URL, el actor equivocado recibe la respuesta correcta. **Y la respuesta correcta a veces es `404` y no `403`**, que es una regla de este proyecto y no una preferencia: el veto (#29), la mesa borrada (#175) y el perfil caducado (#249) lo niegan sin confirmar que existan.
- **Las vías de lectura de un archivo**, que para F4 son siete y llegaron de a una: propio, publicado, adjunto privado de una mesa que dirigís, compartido por una mesa (#206), entregado a una petición (#211), adjunto de un pedido (#236) y adjunto de una postulación (F2.2). **Cada una se prueba con el actor que no debería pasar**, y con el vetado que F3.4 agregó a todas. El número no se da por bueno: se cuenta contra el Javadoc de `FileService.requireReadable`, que es donde viven de verdad.
- **La lista blanca del sanitizador** (#62, #186) sobre los tres campos de texto enriquecido, al guardar y al servir. Es la superficie de XSS más directa del sistema.
- **El circuito de sesión** (#125, #127): el access token en memoria y nunca en `localStorage`, el refresh rotativo en cookie `httpOnly`, el CSRF activo solo en `/auth/refresh`, el reintento único ante `401`, y que el token de Discord se descarte al terminar el callback.
- **Que el JWT no autorice** (#122): los roles se releen de la base en cada request, y un rol quitado deja de valer dentro de la ventana de la caché de #128 — que el bloqueo de F3.1 tiene que invalidar en el momento.
- **Que ningún `Map<String, Object>` cruce HTTP** (regla dura 3) y que ningún endpoint devuelva más de lo que la pantalla necesita.
- **El doble de login de pruebas** (`TestLoginController`, #143, #223) **no existe fuera del perfil `test`**. Es la verificación más barata de esta rebanada y la más cara de olvidar. Lo mismo para sus hermanos `TestDataController` y el Discord falso de `TestDiscordController`.
- **Las dependencias.** Al abrir F4, GitHub reporta 117 alertas de Dependabot (6 críticas). Se separan las de `legacy/` —que se borra al alcanzar paridad y no se despliega— de las de `backend/` y `frontend/`, y cada una de estas se triaje como las demás: se sube la versión (una major es decisión, regla dura 15), se justifica que no aplica, o se anota.

**Fuera de alcance, con su motivo:** el anonimato de los comentarios (#43, #45) no se puede verificar porque los comentarios son **F5**. Queda anotado como la primera línea de la revisión de esa fase.

**Terminada cuando:** cada vía de lectura tiene su prueba negativa, y ninguna negativa devuelve un código que confirme lo que niega.

---

### F4.5 — La revisión mano a mano

**Es la rebanada del cliente, no de un agente.** Las cuatro anteriores producen el instrumento; esta es la pasada humana sobre el producto real.

**Cómo se ejecuta:**

- Se recorre el producto **navegando**, con el mapa de F4.1 al lado, con un actor por rol armado desde el `DevPanel` (#158).
- **Cada hallazgo se anota en el momento**, con la pantalla, lo que se esperaba y lo que pasó. Un hallazgo que se recuerda al final se recuerda mal.
- **Cada hallazgo se triaje en los tres de §3**, uno por uno, sin dejar ninguno «para ver después».
- Lo que se corrige, se corrige **con su test de regresión**: si un humano lo encontró una vez, un test tiene que encontrarlo la próxima.

**Terminada cuando:** el registro de hallazgos está cerrado — cada uno corregido con su commit, o anotado con su fase y su motivo.

## 4. Lo que F4 explícitamente NO hace

| Queda fuera | Por qué |
|---|---|
| Construir pantallas o endpoints nuevos | F4 verifica. Una pantalla que falta es un hallazgo, y su fase es F5 o F6 |
| Escribir los tests unitarios que las fases debían escribir | Si falta uno, es un hallazgo y su triaje es «bug»: la fase no cumplió el punto 2 de §6 |
| Rediseñar | Un cambio de diseño es una decisión (#250, categoría 3), y se toma con su número antes de tocar nada |
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
cd frontend && npm run format      # prettier del repo (#174)
```

**Nunca `./mvnw clean` con el backend levantado**: borra `target/classes` bajo el proceso vivo, el backend se cae, y la suite e2e falla entera como si el código se hubiera roto.
