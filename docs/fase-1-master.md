# F1 — Master: implementación

> **Cómo se construye F1**, la fase más grande del proyecto. `plan-desarrollo.md` §4 dice *qué* entrega y en qué orden van las cinco fases; acá está el detalle de las siete rebanadas, con sus archivos y su verificación.
>
> El *por qué* de cada decisión está en `decisiones.md`, las reglas de negocio en `modelo-datos.md` §5, las pantallas en `frontend-diseno.md` y el *cómo se escribe el código* en `arquitectura.md`.
>
> **Documento vivo mientras F1 esté abierta.** Cada rebanada se marca terminada acá al cerrarse, con su inventario. Cuando F1 cierre, este documento queda como registro y no se toca más.

## 1. Por qué existe este documento

`plan-desarrollo.md` §4 define F1 en un párrafo: *"la mesa completa, de la creación al cierre"*. Ese párrafo no alcanza para empezar a construir — F1 arrastra cinco subsistemas (catálogos, agenda, sesiones, archivos, peticiones), el dashboard del master y el mínimo del jugador para poder probar todo eso. No dice en qué orden, dónde están los huecos, ni qué faltaba decidir.

Acá está lo que faltaba: **siete rebanadas verticales**, cada una con backend + frontend + tests + ayuda, cada una terminando en un flujo que se puede probar a mano. Es el criterio que funcionó en E1 y E2, y el que el criterio 2 de `plan-desarrollo.md` §1 exige.

## 2. El punto de partida

Verificado en el repositorio al abrir la fase, no asumido. Es la foto contra la que se mide lo que F1 agrega.

**Lo que ya está y no hay que construir:**

- El **schema baseline ya tiene las 34 tablas** de `modelo-datos.md` §4. `table_schedules`, `table_sessions`, `session_attendance`, `table_tasks`, `task_submissions`, `files`, `table_files`, `systems`/`tags`/`platforms` y sus tres puentes **ya existen** en `V1__baseline.sql`. **F1 casi no necesita migraciones de schema**: necesita `@Entity`, services, endpoints y pantallas. Las únicas migraciones previstas son de **datos** (el seed de catálogos) y una aditiva si aparece una columna que el baseline no tenga.
- Mapeado hoy en Java: `users`, `roles`, `users_roles`, `table_types`, `game_tables` (parcial), `masters`, `table_registrations`, `registration_rejections`, `table_status_changes`, `notifications`.
- La máquina de estados de la mesa está completa desde E2, con su historial y sus nueve estados.
- El lenguaje de búsqueda (`common/search/` + `lib/searchQuery.ts`), la paginación, el `UserPicker` y el `ContextSwitcher` con pertenencia ya existen.

**Los huecos concretos que F1 cierra:**

| Hueco | Dónde se ve hoy | Cerrado en |
|---|---|---|
| `game_tables.permitted` y `closed_at` **sin mapear** | comentario en `backend/.../tables/GameTable.java` | F1.2 |
| **`closed_at` nunca se sella** — es una regla de `modelo-datos.md` §5 que no se cumple | `GameTableService.finish()` / `.cancel()` | F1.2 |
| El wizard de `/master/tables/new` es **un formulario de cinco campos** | `frontend/src/routes/master/MasterTableCreatePage.tsx` lo dice explícito | F1.2 |
| `TableTypeController` **no existe** — `V2__seed.sql` siembra los tipos y no hay forma de listarlos | — | F1.1 (backend) · F1.2 (el `useTableTypes` que lo consume) |
| ~~`NotificationType` tiene **tres valores**~~ | — | Cerrado: F1.2 sumó `ScheduleConflict`, F1.3 `SessionScheduled` y `SessionCanceled`, F1.5 `TaskPublished` |
| ~~`features/catalogs/` y `features/files/` son carpetas con un `.gitkeep`~~ | — | Cerrado: F1.1 y F1.4 |
| ~~`POST /{id}/masters` existe desde E2 y **nunca tuvo interfaz**~~ | — | Cerrado: F1.6 |

**Piezas del inventario de `frontend-diseno.md` §5 que F1 estrena:** `RichTextEditor`, `RichTextView`, `ScheduleEditor`, `FilePicker`, `DataTable`, `CollapsibleSection`, `IconAction`, `useConfirm`, `lib/date.ts`.

**Primitivas de shadcn que faltan y F1 necesita:** `command` (combobox), `calendar`, `table`, `sheet`, `tooltip`, `separator`, `checkbox`. Al cerrar F1.4 quedan todas salvo `calendar` y `sheet`, que ninguna rebanada terminó necesitando: la agenda se resolvió con campos de fecha y hora nativos, y los diálogos con `dialog`.

## 3. Las decisiones que F1 abrió

Tres decisiones nuevas se tomaron al planificar esta fase. El razonamiento completo está en `decisiones.md`; acá el resumen de qué implica cada una para el trabajo:

| Decisión | Qué cambia en F1 |
|---|---|
| **#178** — choque de horarios | Cuatro reglas nuevas (R1–R4), un `ScheduleConflictService`, un campo derivado en el explorador y **el retiro de postulación se adelanta de F2 a F1** |
| **#179** — `/admin/catalogs` se adelanta de F3 | F1.1 construye la administración completa de catálogos, no solo el consumo y la propuesta |
| **#180** — `closed_at` se sella al cerrar | F1.2 mapea la columna y sella en `finish()` y `cancel()` |

Las reglas de negocio que salen de #178 y #180 viven en `modelo-datos.md` §5, como todas las demás.

## 4. Las siete rebanadas

Cada una se cierra con las ocho de `plan-desarrollo.md` §6 en su alcance. **Ninguna arranca sin que la anterior tenga sus tests en verde y su inventario de archivos** — es el corte entre rebanadas, igual que entre fases.

Las secciones **Backend** y **Frontend** de cada rebanada son, literalmente, el encargo de A1 y de A2 (`plan-desarrollo.md` §7).

---

### F1.1 — Catálogos

**Por qué primero:** el wizard de F1.2 no se puede escribir sin sistemas, tags y plataformas.

**Backend** — paquete `com.centraldungeon.catalogs/`:

- `System`, `Tag`, `Platform` (`@Entity`); `TableSystem`, `TableTag`, `TablePlatform` (puente con clave compuesta, mismo patrón que `Master`/`MasterId`).
- **`SystemService` completo primero.** Recién cuando `TagService` y `PlatformService` salgan idénticos se extrae `AbstractCatalogService`: es la condición literal de `arquitectura.md` §2.4 —se abstrae después de ver la repetición, no antes— y el ejemplo que ese documento usa es justamente este.
- Reglas: profundidad 1, el destino de un `canonical_id` tiene que tener el suyo en `NULL` (#59); buscar por cualquier miembro devuelve todo el grupo (#54, #56); proponer lo hacen masters y admins, aceptar y clasificar solo un admin (#55); un valor en `Created` no filtra ni se muestra a los jugadores (#57); la mesa muestra el alias que le puso su master (#58); dar de baja no rompe vínculos (#81).
- **Fusionar y separar grupos son operaciones explícitas**, no un `PATCH` de `canonical_id` suelto: son parte del producto (#55), no un caso borde.
- `TableTypeController`, que falta.
- `SystemController`, `TagController`, `PlatformController`, `AdminCatalogController`: **clases concretas con su `@PreAuthorize` en cada método**, aunque el service venga de una base abstracta (§2.4, última línea — la seguridad no se hereda).
- Migración `V3__catalog_seed.sql`: el catálogo real de la comunidad con sus grupos de sinónimos. Es de **datos**, no de schema.

**Frontend** — `features/catalogs/`:

- `CatalogCombobox` sobre `command`: elegir del catálogo aceptado o proponer uno nuevo, con el aviso de "pendiente de aprobación" que #57 exige — el master ve su mesa con un tag que los demás todavía no ven, y la interfaz tiene que decirlo.
- `CatalogChip` — muestra el alias (#58), atenuado si está `Created`.
- **`/admin/catalogs`** (`routes/admin/AdminCatalogsPage.tsx`): aceptar, clasificar, fusionar, separar, dar de baja. Es una de las tablas anchas de `frontend-diseno.md` §5.b: **en móvil deja de ser tabla**, cada fila pasa a ficha, nunca scroll horizontal.
- `components/DataTable.tsx`, `components/ui/table.tsx`, `components/ui/command.tsx`.

**Ayuda:** `/help/admins#catalogs` y `/help/masters#propose-catalog`.

**Se prueba:** un admin abre `/admin/catalogs`, fusiona "DANDD" con "D&D", y el explorador encuentra las mesas de las dos formas sin que se haya migrado una sola fila de `table_tags` (#56).

#### ✅ Terminada

Tres decisiones nuevas salieron de construirla: **#182** (`GameSystem`, no `System`), **#183** (dar de baja el canónico exige sucesor) y **#184** (`GET .../{id}/group`), más **#185** para el estado en la URL de la pantalla.

Queda fuera a propósito, con su motivo: **el alta de vínculos mesa↔catálogo**. F1.1 entrega las tres entidades puente, sus repositorios y la resolución de grupos —lo que §5 llama "el backend de F1.1 ya los resuelve"—, pero vincular no tiene quien lo llame hasta que exista el wizard, así que `TableCatalogService` entra con F1.2. Los repositorios puente sí tienen uso hoy: cuentan el `uses` de cada valor en `/admin/catalogs`.

**Backend** (`backend/src/main/java/com/centraldungeon/`):

| Ruta | Qué es |
|---|---|
| `catalogs/CatalogValue.java` · `GameSystem` · `Tag` · `Platform` | `@MappedSuperclass` + las tres `@Entity` |
| `catalogs/TableCatalogLink.java` · `TableSystem` · `TableTag` · `TablePlatform` (+ sus tres `…Id`) | Las tablas puente, con clave compuesta |
| `catalogs/CatalogStatus.java` · `TableCatalogLinkStatus.java` · `CatalogType.java` (+ `CatalogTypeConverter`) | Los vocabularios |
| `catalogs/CatalogValueRepository.java` + `SystemRepository` · `TagRepository` · `PlatformRepository` | Base `@NoRepositoryBean` y las tres concretas |
| `catalogs/TableSystemRepository.java` · `TableTagRepository` · `TablePlatformRepository` (+ `CatalogUsageCount`) | El conteo de uso, en una consulta agrupada por página |
| `catalogs/CatalogSearchField.java` · `CatalogSearchSpecification.java` | El buscador, sobre `common/search/` |
| `catalogs/AbstractCatalogService.java` + `SystemService` · `TagService` · `PlatformService` · `CatalogServices` | Las reglas, escritas una vez; la extracción se hizo después de ver la repetición (§2.4) |
| `catalogs/CatalogMapper.java` | Entidad → los dos DTOs |
| `catalogs/SystemController.java` · `TagController` · `PlatformController` · `AdminCatalogController` | Clases concretas, con su `@PreAuthorize` en cada método |
| `catalogs/dto/` | 7 records: `CatalogValueResponse`, `AdminCatalogValueResponse`, `CreateCatalogValueRequest`, `AcceptCatalogValueRequest`, `MergeCatalogGroupsRequest`, `SplitCatalogGroupRequest`, `DisableCatalogValueRequest` |
| `tables/TableTypeController.java` · `TableTypeService.java` · `dto/TableTypeResponse.java` | El hueco que F1 abrió: los tipos se sembraban desde E1 y nada podía listarlos |
| `resources/db/migration/V3__catalog_seed.sql` | 9 sistemas (3 grupos), 5 plataformas (1 grupo), 5 tags. Datos, no schema |
| `test/…/catalogs/SystemServiceTest.java` · `CatalogGroupIT.java` | 27 unitarios + 12 de integración |

Modificados: `MapperConfig` (+`catalogMapper`), `TableType` (+`description`), `GlobalExceptionHandler` (+`MethodArgumentTypeMismatchException` → `400`; sin eso `/admin/catalogs/colores` respondía `500`).

**Frontend** (`frontend/src/`):

| Ruta | Qué es |
|---|---|
| `features/catalogs/types.ts` | El tipo base (`AdminCatalogValue`) y todo lo demás derivado |
| `features/catalogs/api/` | `catalogsApi.ts` + 11 hooks (3 queries, 8 mutations) |
| `features/catalogs/components/` | `CatalogCombobox`, `CatalogChip`, `CatalogStatusBadge`, `CanonicalPicker`, y los tres diálogos: aceptar, fusionar, dar de baja |
| `features/catalogs/index.ts` | La superficie pública de la feature |
| `routes/admin/AdminCatalogsPage.tsx` | La pantalla, con sus cuatro estados |
| `components/DataTable.tsx` | La tabla ancha que en móvil deja de ser tabla (§5.b) |
| `components/ui/command.tsx` · `table.tsx` | Primitivas de shadcn |
| `layouts/components/AdminSectionNav.tsx` | La navegación del contexto Admin, que con una sola pantalla no hacía falta |
| `locales/es/catalogs.json` | Los textos, todos por `t()` |
| Tests | `CatalogChip.test.tsx`, `DataTable.test.tsx`, `e2e/admin-catalogs.spec.ts` |

Tocados: `api/queryKeys.ts`, `config/paths.ts`, `routes/router.tsx`, `providers/i18n.ts`, `layouts/AdminLayout.tsx`, `locales/es/admin.json`, `locales/es/help.json`, `routes/help/HelpAdminsTab.tsx`, `routes/help/HelpMastersTab.tsx`.

**Salida real de las suites:**

```
./mvnw test    → 112 tests, 0 fallos
./mvnw verify  → 112 unitarios + 23 integración, 0 fallos
npx tsc -b     → limpio
npm run test   → 11 archivos, 89 tests
npx playwright test → 12 tests, todos verdes, contra el backend y el frontend reales
```

---

### F1.2 — Agenda, horarios y wizard completo

**Backend** — en `tables/`:

- `TableSchedule` + `TableScheduleId` (`@Entity`, clave compuesta), `TableScheduleService`.
- **`ScheduleConflictService`** — el corazón de #178. Vive en `tables/` porque la agenda es del agregado mesa; `RegistrationService` lo llama para R2, R3 y R4. Su unidad de cálculo es un **intervalo semanal en minutos UTC con envoltura**, y **se testea solo, sin base**: es la pieza de F1 con más casos borde.
- Mapear `permitted` y `closed_at` en `GameTable`; sellar `closed_at` en `finish()` y `cancel()` (#180).
- `CreateGameTableRequest` / `UpdateGameTableRequest` crecen: tipo, sistemas, tags, plataformas, `startDate`, `duration`, `totalSessions`, `maxPlayers` y agenda. Validación Jakarta en el `Request`; las reglas de negocio —choque, profundidad de catálogo, estado de la mesa— en el service.
- **Sanitización del texto enriquecido al guardar y al servir** (#62) sobre `description`, `permitted` y `requirements`. Es la superficie de XSS más directa del sistema, y entra acá porque acá se estrena el editor.
- `GameTableSummaryResponse` suma el campo derivado de choque para el explorador. Se calcula para el actor del token, **nunca para un id que venga de la URL** (#121).
- `DELETE /api/v1/registrations/{id}` — retirar la propia postulación. Es el adelanto de F2 que R4 obliga.
- `NotificationType.ScheduleConflict`.

**Frontend**:

- `lib/date.ts` — conversión UTC↔local con `Intl.DateTimeFormat`, **locale y zona como parámetros**, nunca constantes incrustadas (`arquitectura.md` §3.3, #111). Sin librería de fechas.
- `ScheduleEditor` en `features/tables/` — día de semana + hora **en hora local**, mostrando el equivalente UTC. Lo que viaja al backend es UTC (#22).
- `RichTextEditor` + `RichTextView` en `components/` — TipTap: TinyMCE necesita API key para uso alojado (`frontend-diseno.md` §6).
- Wizard de `/master/tables/new` **reescrito a pasos**: identidad → catálogos → agenda y duración → cupo y revisión. Un paso por decisión, con el resumen antes de enviar.
- `GameTableCard` muestra la agenda en hora local y la **advertencia de choque**.
- `/tables/:id` muestra la agenda en hora local y **explica** el bloqueo de R2 en el botón, con el motivo — principio 2 de `frontend-diseno.md` §1: un botón gris que no dice por qué está gris es peor que no tener botón.
- Primitivas: `calendar`, `sheet`, `tooltip`, `separator`.

**Tests:** `ScheduleConflictServiceTest` con la matriz completa —solapa, adyacente, envuelve la semana, sin duración, mesa en `Pause`, dos filas de la misma mesa—; `RegistrationServiceTest` para R2, R3 y R4; e2e Playwright del wizard.

**Se prueba:** un master arma una mesa con agenda real; un jugador la ve en su hora local y, si ya juega a esa hora, la ve advertida y no puede postularse.


#### ✅ Terminada

Siete decisiones nuevas salieron de construirla: **#186** (sanitizador de OWASP), **#187** (`InvalidRequestException`, el `400` de una agenda incoherente), **#188** (un `409` con código propio y su mensaje redactado), **#189** (`PUT` que reemplaza la mesa entera), **#190** (agenda y catálogos se reemplazan como conjunto y sus filas se marcan), **#191** (`Weekday` propio) y **#192** (locale y zona como parámetros de `lib/date.ts`).

**Un bug de producto encontrado por el e2e, no por un test unitario**: el botón «Siguiente» y el de enviar eran el mismo nodo del DOM con distinto `type`, así que React le cambiaba el atributo *durante* el despacho del click y el navegador ejecutaba la acción por defecto sobre el nodo ya convertido — el tercer «Siguiente» creaba la mesa. Se arregla con una `key` distinta por rama, más una guarda en `onSubmit` que ignora el envío fuera del último paso (Enter en un campo de texto hacía lo mismo desde el teclado).

Queda fuera a propósito, con su motivo: **el `PUT` no tiene todavía su pantalla**. El endpoint, el hook y los tipos están; la pantalla de edición del master vive en `/master/tables/:id` y su rediseño es de F1.6, cuando esa pantalla gane además co-masters y el resto de las pestañas. Y **`table_sessions` no se materializa**: la agenda queda guardada y verificada, pero convertirla en sesiones es F1.3 (#26, #33).

**Backend** (`backend/src/main/java/com/centraldungeon/`):

| Ruta | Qué es |
|---|---|
| `tables/Weekday.java` · `WeeklyInterval.java` | El vocabulario del tiempo semanal: el día en UTC y el intervalo semiabierto con envoltura |
| `tables/TableSchedule.java` · `TableScheduleId` · `TableScheduleStatus` · `TableScheduleRepository` | La agenda, con su clave compuesta de tres columnas |
| `tables/ScheduleConflictService.java` + `CommittedTable.java` | El corazón de #178, sin base en la parte que se puede calcular |
| `tables/TableScheduleService.java` | Reemplazo de la agenda, el `400` de la incoherencia, R1 y el aviso a quien ya estaba |
| `catalogs/TableCatalogService.java` | El vínculo mesa↔catálogo que F1.1 dejó anotado |
| `common/text/RichTextSanitizer.java` | La lista blanca de #62, al guardar y al servir |
| `common/exception/InvalidRequestException.java` | El quinto miembro de la jerarquía sellada (#187) |
| `tables/dto/TableScheduleEntry.java` · `UpdateGameTableRequest.java` | Los dos records nuevos |
| `test/…/tables/ScheduleConflictServiceTest.java` · `TableScheduleServiceTest` · `TableScheduleIT` | 20 + 10 unitarios y 8 de integración sobre MySQL real |
| `test/…/common/text/RichTextSanitizerTest.java` | 9 unitarios sobre la superficie de XSS |

Modificados: `GameTable` (+`permitted`, +`closedAt`, +`setName`), `GameTableService` (sanitización, catálogos, agenda, `update()`, sellado de `closed_at`), `GameTableMapper`, `GameTableController` (+`PUT`), `GameTableRepository` y `TableRegistrationRepository` (las dos consultas de compromisos), `CreateGameTableRequest`, `GameTableSummaryResponse` y `GameTableDetailResponse` (agenda, catálogos, `closedAt`, campo derivado de choque), `RegistrationService` y `RegistrationController` (R2, R3, R4 y el retiro), `NotificationService` y `NotificationType` (+`ScheduleConflict`), `ConflictException` (+`errorCode`), `ApiException`, `TableRegistrationStatus`, los tres repositorios puente y `TestDataService` — sin este último la limpieza del e2e rompía la clave foránea de `table_schedules` y respondía `500`, que es lo que llenaba la base y hacía fallar la corrida siguiente por paginación (#171, #172).

**Sin migración Flyway**: `permitted`, `closed_at` y las cinco columnas de `table_schedules` ya estaban en `V1__baseline.sql`. F1.2 mapea, no agrega.

**Frontend** (`frontend/src/`):

| Ruta | Qué es |
|---|---|
| `lib/date.ts` + `date.test.ts` | UTC↔local con `Intl`, sin librería de fechas; locale y zona por parámetro (#111) |
| `types/catalog.ts` | El tipo de valor de catálogo, subido a la raíz porque lo necesitan dos features (regla dura 16) |
| `components/RichTextEditor.tsx` · `RichTextView.tsx` | TipTap, con la barra alineada a la lista blanca del backend |
| `features/tables/components/ScheduleEditor.tsx` + su test | Día y hora en zona local, con el equivalente UTC a la vista |
| `features/tables/api/useUpdateTable.ts` · `useTableTypes.ts` | El `PUT` y los tipos de mesa, que se sembraban desde E1 y nada leía |
| `features/registrations/api/useWithdrawApplication.ts` | El retiro que R4 obliga |
| `routes/master/MasterTableCreatePage.tsx` | El wizard reescrito a cuatro pasos, con su resumen |
| `components/ui/tooltip.tsx` · `separator.tsx` | Primitivas de shadcn |
| `e2e/table-schedule.spec.ts` | Wizard con agenda, R1 con el motivo en pantalla y R2 desde el lado del jugador |

Tocados: `features/tables/types.ts`, `schemas.ts`, `index.ts`, `gameTablesApi.ts`, `components/GameTableCard.tsx` (+ su test), `features/catalogs/types.ts` (deriva del tipo subido), `features/registrations/` (api e index), `routes/TableDetailPage.tsx`, `routes/my/MyApplicationsPage.tsx`, `routes/help/HelpMastersTab.tsx` y `HelpPlayersTab.tsx`, `api/queryKeys.ts`, `config/query.ts` (los códigos de error que se muestran verbatim), `vite.config.ts`, `e2e/table-lifecycle.spec.ts` y los locales `master`, `tables`, `registrations`, `common` y `help`.

**Ayuda:** `/help/masters#schedule` (agenda, duración, intervalos y choque) y `/help/players#schedule-conflicts` (por qué no se puede postular y cómo retirar), enlazadas desde el paso de agenda del wizard y desde el aviso de `/tables/:id`.

**Salida real de las suites:**

```
./mvnw test    → 159 tests, 0 fallos
./mvnw verify  → 159 unitarios + 31 integración, 0 fallos
npx tsc -b     → limpio
npm run test   → 13 archivos, 112 tests
npx playwright test → 15 tests, todos verdes, contra el backend y el frontend reales
```

---

### F1.3 — Sesiones y asistencia

**Backend** — `tables/`:

- `TableSession`, `SessionAttendance` + `SessionAttendanceId`, `TableSessionService`.
- **Materialización al pasar a `Opened`** (#26, #33): `start_date` + `table_schedules` + `total_sessions` → N filas con su `sequence_number` y su `scheduled_at`.
- Corregir la fecha de una sesión, cancelar una suelta, notas por sesión. Es la razón de materializar en vez de derivar (#33).
- **`Pause` congela la agenda** (#32, #33): las sesiones pendientes dejan de aparecer; reanudar reagenda desde la fecha de reanudación y **vuelve a verificar el choque** (#178).
- Asistencia `Present` / `Absent` / `Excused` / `Unknown` (#36). La asistencia histórica se **deriva con `COUNT` y no se cachea**, con `Unknown` fuera del denominador y los tres números sin colapsar (#137). El índice cubridor ya está en el baseline.
- `NotificationType.SessionScheduled` y `SessionCanceled`.

**Frontend**:

- Pestaña **Sesiones** en `/master/tables/:id` — lista, editar fecha, cancelar, registrar asistencia.
- **`/my/tables/:id`, ruta nueva** — agenda, sesiones y *mi* asistencia, solo lectura. Es el mínimo del jugador para poder probar la fase; el resto de la pantalla es F2.
- Sesiones en `/tables/:id`, solo lectura.
- `CollapsibleSection`, `IconAction`, `useConfirm`.

**Se prueba:** el master abre la mesa y aparecen las 12 sesiones; registra la asistencia de la primera; el jugador ve su calendario en `/my/tables/:id`, en su hora local.

#### ✅ Terminada

Cuatro decisiones nuevas salieron de construirla: **#193** (reanudar con choque responde `409`), **#194** (cancelar una sesión repone otra al final), **#195** (`Held` es una acción explícita, separada de la asistencia) y **#196** (una mesa sin fecha, agenda o cantidad abre con cero sesiones y no se bloquea la aprobación).

**Un bug encontrado por el test, no por la pantalla**: `formatDateTime` y `formatDate` leían mal todo instante que venía del backend. Jackson serializa `LocalDateTime` sin offset —`2026-09-09T01:00:00`— y JavaScript interpreta una fecha así como hora **local**, así que una sesión de la 01:00 UTC se mostraba como 01:00 a alguien tres horas atrás: exactamente el error que la conversión existe para evitar (#22). `utcIsoToLocalInput` ya lo resolvía agregando la `Z`; las otras dos no. Corregido en `lib/date.ts` con su test de regresión, y arregla de paso cómo se venían mostrando `startDate` y `createdAt`.

**Una decisión de forma que el contrato no tenía**: el calendario de solo lectura de `/tables/:id` **viaja dentro del detalle de la mesa** (`GameTableDetailResponse.sessions`) y no en un endpoint propio. Esa lectura ya decide quién puede ver la mesa —un vetado recibe `404` (#29)—, así que las sesiones heredan esa única respuesta en vez de repetir la verificación en un segundo lugar donde podría desincronizarse.

Queda fuera a propósito, con su motivo: **agregar una sesión suelta**. F1.3 entrega las cuatro operaciones que §4 nombra —corregir fecha, notas, marcar jugada, cancelar— más la reposición de #194; un «agregar sesión» a mano no lo pide ningún documento y sin él la cantidad de la mesa sigue siendo la que prometió. Y **la asistencia no aparece todavía en un perfil**: el agregado de #137 está construido y expuesto por mesa para el propio jugador, pero `/profile` y `/users/:id` son F2.

**Backend** (`backend/src/main/java/com/centraldungeon/`):

| Ruta | Qué es |
|---|---|
| `tables/TableSession.java` · `TableSessionStatus` · `TableSessionRepository` | El calendario materializado, con su lock pesimista y el `max(sequence_number)` que #194 necesita |
| `tables/SessionAttendance.java` · `SessionAttendanceId` · `AttendanceStatus` · `SessionAttendanceRepository` | La asistencia, con clave compuesta y el `GROUP BY` de #137 sobre el índice cubridor del baseline |
| `tables/AttendanceCount.java` | La proyección interna del conteo agrupado — no cruza HTTP |
| `tables/TableSessionService.java` | Materializar, congelar, reagendar, corregir, cerrar, cancelar con reposición y registrar asistencia |
| `tables/TableSessionController.java` | Seis endpoints, clase concreta, `@PreAuthorize` en cada método |
| `tables/dto/` | 8 records: `TableSessionResponse`, `PlayerSessionResponse`, `PublicSessionResponse`, `SessionAttendanceEntry`, `AttendanceSummaryResponse`, `MySessionsResponse`, `UpdateSessionRequest`, `RecordAttendanceRequest` (+ `AttendanceEntryRequest`) |
| `test/…/tables/TableSessionServiceTest.java` · `TableSessionIT.java` | 29 unitarios + 6 de integración sobre MySQL real |

Modificados: `GameTableService` (materializa en `approve()` y `assignInitialMasters()`, verifica el choque y reagenda en `resume()`, y el calendario en `toDetail()`), `GameTableMapper` (+4 métodos), `GameTableDetailResponse` (+`sessions`), `NotificationType` (+`SessionScheduled`, +`SessionCanceled`), `NotificationService` (+2 avisos) y `TestDataService` — sin borrar `SessionAttendance` y `TableSession` antes de la mesa, la limpieza del e2e rompía la foreign key, que es el mismo bug que F1.2 tuvo que arreglar (#171, #172).

**Sin migración Flyway**: `table_sessions` y `session_attendance` ya estaban en `V1__baseline.sql`, con su índice cubridor `(user_id, attendance)`. F1.3 mapea, no agrega.

**Frontend** (`frontend/src/`):

| Ruta | Qué es |
|---|---|
| `components/CollapsibleSection.tsx` · `IconAction.tsx` | Los dos compuestos del inventario de §5 que F1.3 estrena. `useConfirm` ya existía desde E2 |
| `features/tables/api/sessionsApi.ts` + 6 hooks | Dos queries y cuatro mutations |
| `features/tables/components/SessionList.tsx` (+ test) · `SessionStatusBadge` · `AttendanceEditor` · `AttendanceSummaryView` (+ test) | El calendario de solo lectura, el badge, el padrón y los tres números de #137 |
| `routes/master/MasterTableSessionsTab.tsx` | La pestaña Sesiones, con una `CollapsibleSection` por sesión |
| `routes/my/MyTableDetailPage.tsx` | La ruta nueva `/my/tables/:id` — agenda, sesiones y mi asistencia, solo lectura |
| `e2e/table-sessions.spec.ts` | Materialización al aprobar, la reposición de #194 avisada antes de confirmar, y la asistencia vista desde el lado del jugador |

Tocados: `lib/date.ts` (+ `date.test.ts`, la corrección del instante sin offset), `features/tables/types.ts` e `index.ts`, `api/queryKeys.ts` (+rama `sessions`), `config/paths.ts`, `routes/router.tsx`, `routes/TableDetailPage.tsx`, `routes/master/MasterTableDetailPage.tsx` (+pestaña), `routes/my/MyTablesPage.tsx` (las fichas ahora llevan a `/my/tables/:id`), `routes/help/HelpMastersTab.tsx` y `HelpPlayersTab.tsx`, y los locales `tables`, `master` y `help`.

**Ayuda:** `/help/masters#sessions` (cuándo aparece el calendario, corregir una fecha, marcar jugada, la reposición de #194, qué congela la pausa y por qué puede fallar reanudar) y `/help/players#my-sessions` (dónde está mi calendario y cómo se leen los tres números de #137), enlazadas desde la pestaña Sesiones y desde `/my/tables/:id`.

**Salida real de las suites:**

```
./mvnw test    → 188 tests, 0 fallos
./mvnw verify  → 188 unitarios + 37 integración, 0 fallos
npx tsc -b     → limpio
npm run test   → 15 archivos, 124 tests
npx playwright test → 18 tests, todos verdes, contra el backend y el frontend reales
```

---

### F1.4 — Archivos

**Backend** — paquete `files/` más `common/storage/`:

- `StorageService` (interfaz) + `LocalDiskStorageService` (#15). **Es el caso legítimo de interfaz** de `arquitectura.md` §2.4: hay una segunda implementación prevista (S3), no es un `Impl` por costumbre.
- `StorageProperties` (`@ConfigurationProperties`): raíz de disco y tope de tamaño por archivo.
- `File` (`@Entity`), `TableFile` + su id compuesta, `FileService`, `FileController`.
- **Nombre físico = id del archivo** (#80). El nombre original es metadato de descarga y **nunca toca el sistema de archivos**: el legacy concatenaba el nombre sin sanitizar, que es path traversal directo.
- `content_hash` SHA-256 para deduplicar, compresión al guardar, `last_used_at` (#75).
- `file_type` `Public`/`Private`/`Single-use` (#68) y `public_audience` `Masters`/`Players`/`Announcements` (#64).
- `table_files` **vincula, no duplica** (#79): quitar un archivo de la mesa no borra el archivo global, y actualizar el global actualiza lo que ven todas las mesas que lo usan.
- Descarga autorizada **por pertenencia, no por rol** (#17, #121).
- `FileRetentionService`: job de purga por desuso (#75). **El borrado físico no entra** — es F5 (#66); acá solo se marca.

**Frontend** — `features/files/`:

- `FilePicker` (#65): subir **o** reutilizar del historial, con el tope por archivo. Es la palanca principal de costo de la fase.
- Pestaña **Archivos** en `/master/tables/:id`.
- Archivos públicos, solo lectura, en `/tables/:id` y `/my/tables/:id`.
- `/my/files` **no entra** — es F2.

**Se prueba:** el master sube una hoja de personaje, la vincula a dos mesas sin duplicarla, y el jugador la descarga desde el detalle público.

#### ✅ Terminada

Diez decisiones nuevas salieron de construirla: **#199** (`StoredFile`, no `File`), **#200** (escritura a staging confirmada al commit), **#201** (deduplicación por dueño), **#202** (gzip adentro del almacenamiento), **#203** (subir y vincular son dos llamadas), **#204** (`is_private` y `Public` son ejes distintos), **#205** (el selector ofrece todos los publicados), **#206** (lo que la mesa comparte lo lee quien puede ver la mesa), **#207** (`/admin/files` entra en la fase) y **#208** (`@Transactional` en el método que llama el scheduler). Con #200 y #199 queda **cerrada M26**, que arrastraba dos puntos abiertos desde el relevamiento del legacy.

**Un bug que solo podía ver un test de integración.** `FileRetentionService.purgeUnusedFiles()` llamaba a `markUnusedFiles()`, que era el que tenía el `@Transactional`. Eso es autoinvocación: no pasa por el proxy de Spring, así que la anotación no aplicaba cuando el que llamaba era el scheduler. Sin transacción, la consulta corre en la suya propia de lectura, las entidades vuelven **desprendidas**, y marcarlas escribe sobre objetos que nadie observa — el job registraba un conteo y no cambiaba una sola fila, **en silencio**. Ningún unitario lo puede ver: mockean el repositorio y las aserciones son sobre los objetos devueltos. Va con su `FileIT`, que llama al método que llama el scheduler y después le pregunta a la base.

**Dos bugs de producto que encontró el e2e, no un test unitario.** El primero: `useDownloadFile` revocaba el object URL en la misma tarea que el click, y el navegador solo empieza a buscar el blob cuando esa tarea termina — la descarga se cancelaba sola, sin que nada lanzara un error. Se arregla revocando en un `setTimeout(…, 0)`. El segundo es de diseño y más caro: el `FilePicker` del master filtraba los publicados por audiencia `Masters`, así que la hoja de personaje por defecto —publicada **para jugadores**— no aparecía, y el ejemplo que motiva #79 no se podía ejecutar. La audiencia dice quién **lee** el archivo, no quién lo adjunta (#205).

**Una regla que hubo que corregir sobre la marcha**: la descarga exigía pertenencia y el detalle de la mesa ya listaba el archivo, así que un candidato veía la ficha y recibía `404` al abrirla. Lo que una mesa comparte es tan alcanzable como la mesa (#206) — y queda anotado, acá y en `modelo-datos.md` §5, que al construir el veto (F3) hay que excluir al vetado también en esa lectura.

Queda fuera a propósito, con su motivo: **`/my/files`** es F2, y en F1.4 el historial se ve dentro del `FilePicker`, que es donde #65 lo pide. **El borrado físico** no entra: es F5 (#66), y todo lo de acá solo marca. **`registration_files` y `submission_files`** siguen sin mapear — el archivo de personaje en la postulación es F2. Y **la deduplicación entre usuarios** no se puede hacer sin una tabla de blobs con conteo de referencias, que el baseline no tiene: lo prohíbe `uk_files_storage_key` (#201).

**Backend** (`backend/src/main/java/com/centraldungeon/`):

| Ruta | Qué es |
|---|---|
| `common/storage/StorageService.java` · `LocalDiskStorageService.java` | La interfaz que sí gana serlo (§2.4, #15) y su implementación en disco: gzip, clave validada como segmento simple, y el staging de M26.2 |
| `common/config/StorageProperties.java` · `SchedulingConfig.java` | La raíz, el tope, la whitelist MIME y la ventana de retención; y `@EnableScheduling`, que nada necesitaba hasta ahora |
| `files/StoredFile.java` · `FileType` (+ `FileTypeConverter`) · `PublicAudience` · `FileStatus` | La entidad y sus tres vocabularios. El converter existe porque la columna guarda `Single-use` con guion |
| `files/TableFile.java` · `TableFileId` · `TableFileStatus` · `TableFileType` | El puente, con clave compuesta — y su estado propio, que es lo que hace que re-vincular reviva la fila |
| `files/StoredFileRepository.java` · `TableFileRepository` (+ `FileUsageCount`) | Las lecturas, el conteo de usos agrupado y la consulta de la purga |
| `files/FileSearchField.java` · `FileSearchSpecification.java` | El buscador de `/admin/files`, sobre `common/search/` (#164) |
| `files/FileService.java` | Subida, whitelist y tope, deduplicación, promoción, baja lógica, publicación y la regla de lectura de #206 |
| `files/TableFileService.java` | Vincular, compartir, desvincular y lo que la mesa muestra — todo sin tocar el archivo (#79) |
| `files/FileRetentionService.java` | La purga de #75, por lotes y solo marcando |
| `files/FileMapper.java` | Entidad → los cinco DTOs |
| `files/FileController.java` · `AdminFileController` · `TableFileController` | Clases concretas, con su `@PreAuthorize` en cada método |
| `files/dto/` | 9 records: `FileResponse`, `AdminFileResponse`, `TableFileResponse`, `SharedFileResponse`, `PublicFileResponse`, `UploadFileRequest`, `UpdateFileRequest`, `LinkTableFileRequest`, `UpdateTableFileRequest`, `PublishFileRequest` |
| `files/FileDownload.java` | El portador interno de la descarga: bytes, nombre y tipo. No cruza HTTP como JSON |
| `resources/db/migration/V5__files_updated_at.sql` | La primera migración de **schema** desde el baseline. Aditiva y obligatoria: sin ella `ddl-auto: validate` no deja arrancar |
| `test/…/common/storage/LocalDiskStorageServiceTest.java` | 10 unitarios sobre lo que llega al disco, incluido el rollback |
| `test/…/files/FileServiceTest.java` · `TableFileServiceTest` · `FileRetentionServiceTest` · `FileIT` | 22 + 10 + 4 unitarios y 10 de integración sobre MySQL real |

Modificados: `GameTableDetailResponse` (+`files`), `GameTableMapper` y `GameTableService` (los archivos compartidos viajan con el detalle, igual que las sesiones en F1.3), `GlobalExceptionHandler` (+`MaxUploadSizeExceededException` → `400` con `FILE_TOO_LARGE`; sin eso pasarse del tope era un `500`), `InvalidRequestException` (+código y parámetros, misma forma que `ConflictException` ganó en F1.2), `MapperConfig`, `CentralDungeonApplication` (+`StorageProperties`), `application.yml` y `application-test.yml`, y `TestDataService` — **tercera vez** que esta clase de foreign key rompe la limpieza del e2e, después de la agenda en F1.2 y el calendario en F1.3 (#171, #172).

**Frontend** (`frontend/src/`):

| Ruta | Qué es |
|---|---|
| `types/file.ts` | `SharedFile` y `TableFileType`, subidos a la raíz porque los necesitan dos features (regla dura 16), igual que `types/catalog.ts` |
| `features/files/types.ts` | El tipo base (`StoredFile`) y todo lo demás derivado |
| `features/files/api/` | `filesApi.ts` + 14 hooks (4 queries, 10 mutations) |
| `features/files/format.ts` | El tamaño en unidades legibles, con el locale por parámetro (#111, #192) |
| `features/files/components/` | `FilePicker` (#65), `FileList`, `FileTypeBadge`, `FileAudienceBadge` y `PublishFileDialog` |
| `features/files/index.ts` | La superficie pública de la feature |
| `routes/master/MasterTableFilesTab.tsx` | La pestaña Archivos, como ruta hija |
| `routes/admin/AdminFilesPage.tsx` | La pantalla ancha, con sus cuatro estados y el estado en la URL |
| `components/ui/checkbox.tsx` | La última primitiva de shadcn que §2 anotaba como faltante |
| `locales/es/files.json` · `locales/en/files.json` | Los textos, en los dos idiomas y en el mismo commit (#198) |
| Tests | `FilePicker.test.tsx`, `FileList.test.tsx`, `e2e/table-files.spec.ts` |

Tocados: `api/client.ts` (la parte JSON del multipart pasa a `Blob`, y `api.download` nuevo), `api/queryKeys.ts` (+rama `files`), `config/paths.ts`, `config/query.ts` (los tres códigos de error que se explican), `providers/i18n.ts`, `routes/router.tsx`, `layouts/components/AdminSectionNav.tsx`, `features/tables/types.ts` (+`files` en el detalle), `routes/TableDetailPage.tsx`, `routes/my/MyTableDetailPage.tsx`, `routes/master/MasterTableDetailPage.tsx` (+pestaña), las tres pestañas de `/help` y los locales `master`, `admin`, `help` y `common`.

**Ayuda:** `/help/masters#files` (subir o reutilizar, qué significa privado en una mesa, por qué quitar no borra, los límites), `/help/players#files` (dónde están los archivos de mi mesa y qué no voy a ver) y `/help/admins#files` (publicar, la audiencia como listado y no como permiso, y qué purga el job), enlazadas desde la pestaña, desde `/my/tables/:id` y desde `/admin/files`.

**Salida real de las suites:**

```
./mvnw test    → 238 tests, 0 fallos
./mvnw verify  → 238 unitarios + 47 integración, 0 fallos
npx tsc -b     → limpio
npm run test   → 20 archivos, 157 tests
npx playwright test → 27 tests, todos verdes, contra el backend y el frontend reales
```


---

### F1.5 — Peticiones (lo que publica el master)

**Backend** — paquete `tasks/`:

- `TableTask` (`@Entity`), `TaskService`, `TableTaskController`.
- `audience` `Candidates` / `Players` / `Single`, con `target_user_id` solo en `Single` (#63, #76); `accepts_text` / `accepts_files` con al menos uno en `true`; `is_mandatory` **informativo, no bloqueante** (#70); `due_at` opcional.
- **Publicar una petición notifica a sus destinatarios** (#77) — `NotificationType.TaskPublished`. Una petición que nadie ve no se cumple.
- Lectura de entregas: el master ve quién entregó y quién no. **Las entregas se acumulan y el sistema no juzga si cumplen** (#76). ~~`task_submissions` se **lee** en F1; el jugador entrega en F2~~ — **corregido al construirla**: entregar se adelantó entera a F1.5 (#210), con texto y archivos.
- El incumplimiento se avisa y queda visible, pero **no bloquea ni expulsa** (#70): el sistema informa, las personas deciden.

**Frontend**:

- Pestaña **Peticiones** en `/master/tables/:id`: publicar, editar, cerrar, ver entregas y faltantes.
- Peticiones aplicables, solo lectura, en `/tables/:id` (audiencia `Candidates`) y `/my/tables/:id` (audiencia `Players` y las `Single` propias).

**Se prueba:** el master publica una petición para sus jugadores, les llega la notificación y la ven en su mesa.

#### ✅ Terminada

Siete decisiones nuevas salieron de construirla: **#209** (endpoint propio para las peticiones aplicables, en vez de un campo del detalle), **#210** (la entrega del jugador se adelanta de F2), **#211** (la quinta vía de lectura de un archivo), **#212** (a dónde manda `TaskPublished`), **#213** (`GET .../players`), **#214** (el alto de un diálogo de formulario) y **#215** (el editor se anuncia como campo de texto).

**Lo que se agrandó a propósito**: F1.5 iba a *leer* `task_submissions` y el jugador entregaba en F2. Con nadie que pudiera entregar, el padrón de faltantes muestra a todos como faltantes siempre, no hay flujo e2e que probar, y **la regla que más importa del subsistema —las entregas se acumulan (#76)— queda escrita y sin ejercitar**. Se adelantó entera, con archivos (#210), y `plan-desarrollo.md` y §5 de este documento se corrigieron con ella. Lo que **no** se adelantó: `/my/files` y el archivo de personaje en la postulación siguen en F2.

**Dos bugs que encontró el e2e y ningún test unitario podía ver**, los dos en piezas compartidas y no en la rebanada:

1. **Un diálogo alto no se puede usar** (#214). `DialogContent` de shadcn se centra con `top-50%` + `translate-y-[-50%]` y no acota alto ni overflow, así que el diálogo de entrega —editor más selector de archivos— se derramaba fuera de la ventana y **la mitad de arriba quedaba inalcanzable**: lo que scrollea es la página de atrás. Playwright se colgó esperando que el editor fuera clickeable, que es exactamente lo que le habría pasado a una persona. Arreglado en `FormDialog`, que es nuestro; `components/ui/` lo genera el CLI y no se toca.
2. **El editor de texto enriquecido no se anunciaba como campo de texto** (#215). Chrome expone el `contenteditable` de TipTap como un grupo cualquiera, así que `getByRole('textbox')` no lo encontraba — y **la búsqueda que hace el test es la misma que hace un lector de pantalla**. Se le pusieron `role="textbox"` y `aria-multiline` explícitos: el test dejó de colgarse y el editor pasó a ser navegable con tecnología asistiva, que era el problema de fondo.

Queda fuera a propósito, con su motivo: **borrar una petición**. Se puede cerrar, que es lo que §4 pide; borrar una que ya se publicó y notificó haría desaparecer algo que la gente vio, y nada lo pide. `TaskStatus.Deleted` existe para la baja lógica del día que haga falta. Y **el incumplimiento no genera un aviso automático**: #70 pide que quede visible para el master, y lo está en el padrón de faltantes; mandar una notificación cuando pasa un `due_at` sería el sistema empujando, que es justo lo que #70 evita.

**Sin migración Flyway**: `table_tasks`, `task_submissions` y `submission_files` ya estaban completas en `V1__baseline.sql`, con su `updated_at` y su `ck_task_accepts`. F1.5 mapea, no agrega. El cuarto componente de `NotificationParams` tampoco la necesitó: es JSON en una columna, y una fila vieja sin esa clave se lee con el campo en `null`.

**Backend** (`backend/src/main/java/com/centraldungeon/`):

| Ruta | Qué es |
|---|---|
| `tasks/TableTask.java` · `TaskAudience` · `TaskStatus` | Lo que una mesa pide, y sus dos vocabularios. No hay `Draft`: crear es publicar (#77) |
| `tasks/TaskSubmission.java` · `SubmissionStatus` | Las entregas, que se acumulan y no se editan (#76). Dos estados, no cuatro |
| `tasks/SubmissionFile.java` · `SubmissionFileId` · `SubmissionFileStatus` | El puente con clave compuesta. Vive en `tasks/` y no en `files/` porque la entrega es su agregado dueño |
| `tasks/TableTaskRepository.java` · `TaskSubmissionRepository` (+ `TaskSubmissionCount`) · `SubmissionFileRepository` | Las lecturas, el conteo agrupado —entregas y personas son números distintos (#76)— y la consulta de alcance que la quinta vía necesita |
| `tasks/TableTaskService.java` | Publicar y notificar, corregir sin volver a notificar, cerrar, y la audiencia resuelta en un solo lugar |
| `tasks/TaskSubmissionService.java` | Entregar, y lo que el master lee: las entregas más el padrón de faltantes |
| `tasks/TaskMapper.java` | Entidad → los cuatro DTOs de salida |
| `tasks/TableTaskController.java` · `TaskSubmissionController` | Clases concretas, con su `@PreAuthorize` en cada método |
| `tasks/dto/` | 9 records: `TaskResponse`, `ApplicableTaskResponse`, `TaskSubmissionResponse`, `TaskSubmissionsResponse`, `TaskRecipientResponse`, `SubmittedFileResponse`, `CreateTaskRequest`, `UpdateTaskRequest`, `CreateSubmissionRequest` |
| `registrations/dto/TablePlayerResponse.java` | El padrón de la mesa, que hasta ahora nada podía listar (#213) |
| `test/…/tasks/TableTaskServiceTest.java` · `TaskSubmissionServiceTest` · `TaskIT` | 19 + 13 unitarios y 9 de integración sobre MySQL real |

Modificados: `FileService` (la quinta vía de lectura, #211), `NotificationType` (+`TaskPublished`), `NotificationParams` (+`taskTitle`, el cuarto componente que su propio Javadoc anticipaba) y `NotificationService`, `RegistrationService` y `RegistrationController` (+`GET /game-tables/{id}/players`), `MapperConfig`, y `TestDataService` — **cuarta vez** que esta clase de foreign key rompe la limpieza del e2e, ahora con una cadena de tres niveles (#171, #172).

**Frontend** (`frontend/src/`):

| Ruta | Qué es |
|---|---|
| `features/tasks/types.ts` · `schemas.ts` | El tipo base (`TableTask`) y todo lo demás derivado; el zod con las dos reglas de forma |
| `features/tasks/api/` | `tasksApi.ts` + 8 hooks (4 queries, 4 mutations) |
| `features/tasks/components/` | `TableTasksSection` (el bloque que montan las dos pantallas de lectura), `TaskBoardList`, `ApplicableTaskList`, `TaskFormDialog`, `TaskSubmitDialog`, `TaskSubmissionsPanel`, `MySubmissions`, `TaskAudienceBadge`, `TaskStatusBadge` |
| `features/tasks/index.ts` | La superficie pública de la feature |
| `routes/master/MasterTableTasksTab.tsx` | La pestaña Peticiones, como ruta hija |
| `features/registrations/api/useTablePlayers.ts` | El padrón, para elegir a quién va una petición `Single` |
| `locales/es/tasks.json` · `locales/en/tasks.json` | Los textos, en los dos idiomas y en el mismo commit (#198) |
| Tests | `ApplicableTaskList.test.tsx`, `TaskFormDialog.test.tsx`, `e2e/table-tasks.spec.ts` |

**Cómo se resolvió el cruce de dominios** (regla dura 16): `features/tasks` no importa de `files` ni de `registrations`. `TableTasksSection` y `TaskSubmitDialog` reciben el listado y el selector de archivos como **render props**, y `TaskFormDialog` recibe el padrón y las sesiones como datos planos. Las pantallas de `routes/` son las que componen.

Tocados: `components/FormDialog.tsx` (#214), `components/RichTextEditor.tsx` (#215), `features/files/components/FilePicker.tsx` (el `onPick` pasa el nombre además del id) y su test, `api/queryKeys.ts` (+rama `tasks`, +`registrations.players`), `config/paths.ts`, `config/query.ts` (+`TASK_CLOSED`), `providers/i18n.ts`, `routes/router.tsx`, `routes/master/MasterTableDetailPage.tsx` (+pestaña), `routes/TableDetailPage.tsx` y `routes/my/MyTableDetailPage.tsx` (+la sección de peticiones), `features/notifications/` (tipo, texto y destino de `TaskPublished`), `test/setup.ts` (los huecos de jsdom que Radix usa) y los locales `master`, `notifications` y `help`.

**Ayuda:** `/help/masters#tasks` (las tres audiencias, que publicar avisa y corregir no, que las entregas se acumulan y el sistema no las juzga, que "importante" no expulsa a nadie, y qué pasa al cerrar) y `/help/players#tasks` (dónde las veo, que puedo leer lo que se le pide a los candidatos antes de postularme, cómo entregar reusando un archivo, y que no entregar no me saca de la mesa), enlazadas desde la pestaña y desde las dos pantallas de lectura.

**Salida real de las suites:**

```
./mvnw test    → 270 tests, 0 fallos
./mvnw verify  → 270 unitarios + 56 integración, 0 fallos
npx tsc -b     → limpio
npm run test   → 22 archivos, 168 tests
npx playwright test → 31 tests, todos verdes, contra el backend y el frontend reales
```

---

### F1.6 — Co-masters y dashboard `/master`

**Backend**:

- Gestión de co-masters sobre el `POST /{id}/masters` que existe desde E2 y nunca tuvo interfaz: agregar, quitar, promover. La invariante de **un solo `Primary` vivo** ya tiene su `MasterServiceIT`; se extiende con quitar y degradar.
- `MasterDashboardService` y su endpoint: **bandeja de trabajo, no métricas** (#136). Candidatos sin responder, entregas sin revisar, sesiones por registrar, transiciones pendientes — agrupado por mesa y ordenado por urgencia. **Sin reserva**: el trabajo de una mesa tiene un solo dueño y no se lo disputa nadie.

**Frontend**:

- Sección de co-masters en `/master/tables/:id`, sobre el `UserPicker` que ya existe (#164, #165). En pantalla son **master** y **co-master**, nunca `Primary`/`Secondary` (#166).
- **`/master`** — el dashboard. Su estado vacío es una **buena noticia** —"nada espera tu respuesta"— y no puede leerse como una pantalla rota (`frontend-diseno.md` §5).
- El `ContextSwitcher` ya distingue pertenencia de rol (#135); solo se le agrega el destino.

**Se prueba:** un master con tres mesas entra a `/master` y ve a quién le debe una respuesta sin abrir ninguna.

#### ✅ Terminada

Seis decisiones nuevas salieron de construirla: **#216** (quitar un co-master marca la fila, y la pertenencia pasa a filtrar por estado), **#217** (los cinco tipos de la bandeja, y por qué «entregas sin revisar» se resuelve como petición vencida con faltantes), **#218** (la edición es una página con secciones, no el wizard), **#219** (paquete `dashboard/` propio), **#220** (`/master` pasa a ser el home del contexto) y **#221** (el buscador de personas se acota a la mesa en vez de abrirse a más roles).

**Lo que se agrandó a propósito.** La spec pedía co-masters y dashboard. Entraron además dos cosas que estaban anotadas como pendientes y sin las cuales F1 no cerraba: la **pantalla de edición** que F1.2 difirió textualmente a esta rebanada —sin ella una mesa en `ChangesRequested` no se podía corregir: el botón de reenviar a revisión existía y la forma de aplicar la corrección no— y las dos **pestañas que el sitemap pide y no existían**, Jugadores y Agenda. Con eso las siete pestañas del diseño están completas.

**Un bug latente que solo se activaba al construir esta rebanada.** `MasterService.isMasterOf()` e `isPrimaryOf()` leían la fila de `masters` **sin mirar `MasterRowStatus`**, y `findByGameTable()` devolvía filas vivas y borradas por igual. Mientras `Deleted` solo lo produjera la cascada de una mesa borrada nadie podía notarlo —la mesa entera dejaba de existir—, pero en el momento en que una fila puede morir sola, **un co-master quitado seguía autorizado**: las pantallas dejaban de listarlo y los endpoints seguían aceptándolo. Se arregló en la misma rebanada que introduce la causa (#216), `UserService` incluido, que es lo que decide si el `ContextSwitcher` ofrece el contexto Master.

**Un `403` que ningún unitario podía ver** (#221): el `UserPicker` de la sección de co-masters llamaba a `GET /api/v1/users/search`, que es de admin. En Vitest el picker está mockeado y nunca llama a la API, así que el fallo solo aparece con el navegador y el backend reales — en pantalla, como «Algo salió mal» debajo del buscador. La salida no fue ampliar el directorio sino acotar la búsqueda a la mesa.

**Un incumplimiento de #197 que encontró el barrido de idioma, no un test.** `RegistrationService.accept()` era el único de los cinco puntos de choque que **no** mandaba el nombre de la otra mesa como parámetro: armaba una frase en español y la metía en el `detail`. Y el código que usaba, `SCHEDULE_CONFLICT`, tiene el texto escrito desde el punto de vista de quien se postula —«donde ya estás comprometido»—, que es **falso** cuando quien lee es el master y el choque es de la semana de un tercero. Ahora manda `CANDIDATE_SCHEDULE_CONFLICT` con su parámetro, y las dos frases están en `es` y en `en`. El test que lo cubría buscaba el nombre de la mesa **dentro del mensaje**, que es exactamente cómo la frase en español pasó desapercibida; ahora afirma el código y el parámetro.

**Barrido de idioma** (regla dura 19, y lo que motivó encontrar lo anterior): pasaron a inglés los 39 nombres de test de `SearchQueryInput.test.tsx` y `lib/searchQuery.test.ts`, que estaban en español desde E2, más el comentario en español que quedaba en `MasterTableCreatePage`. Lo que **no** se tocó: las ~115 referencias a `regla dura`, `principio N` y `pertenencia` dentro de Javadoc y JSDoc en inglés — son citas a los nombres que estos documentos usan, no prosa en español, y renombrarlas rompería el puente entre el código y la documentación.

Queda fuera a propósito, con su motivo: **la bandeja no lista `PauseRequested`** aunque el preview de diseño la dibuje — es F3 y ningún endpoint la produce todavía. Y **«entregas sin revisar» no existe como tal**: no hay marcador de revisada en el modelo y #76 prohíbe que el sistema juzgue las entregas, así que el ítem es el que #70 sí pide, la petición vencida con gente que no entregó (#217).

**Backend** (`backend/src/main/java/com/centraldungeon/`):

| Ruta | Qué es |
|---|---|
| `dashboard/MasterDashboardService.java` · `MasterDashboardController` · `MasterWorkItemKind` | La bandeja de #136, en paquete propio para no acoplar `tables` con `tasks` (#219) |
| `dashboard/dto/MasterDashboardResponse.java` · `MasterWorkItem` | Código y parámetros, nunca la frase (#197) |
| `registrations/PendingCandidateCount.java` · `tables/UnrecordedSessionCount.java` | Las dos proyecciones agrupadas de las sondas — mismo patrón que `CatalogUsageCount` |
| `test/…/dashboard/MasterDashboardServiceTest.java` | 11 unitarios: un caso por tipo, el orden, y la bandeja vacía como éxito |

Modificados: `MasterRepository` (+4 lecturas que filtran `MasterRowStatus`, +`findLiveByUser`), `MasterService` (`removeMaster`, revivir una fila borrada, `requirePrimary` extraído, y las tres lecturas de pertenencia filtradas), `MasterRowStatus` y `Master` (el Javadoc que decía que `Deleted` solo venía de la cascada dejó de ser cierto), `UserService` (`existsByUser_IdAndStatus`), `GameTableService` (+`removeMaster`, +`searchMasterCandidates`), `GameTableController` (+`DELETE /{id}/masters/{userId}`, +`GET /{id}/master-candidates`), `TableRegistrationRepository`, `TableSessionRepository` y `TableTaskRepository` (una consulta agregada cada uno), `ConflictException` y `RegistrationService` (el `CANDIDATE_SCHEDULE_CONFLICT` de más abajo), `MasterServiceTest` (+7) y `MasterServiceIT` (+2: promociones y bajas concurrentes, y revivir sin duplicar fila).

**Sin migración Flyway**: `masters.status` y `masters.deleted_at` ya estaban en `V1__baseline.sql`. F1.6 mapea comportamiento, no schema.

**Frontend** (`frontend/src/`):

| Ruta | Qué es |
|---|---|
| `features/tables/api/useMasterDashboard.ts` · `useAddMaster.ts` · `useRemoveMaster.ts` | La bandeja y las dos mutaciones de co-masters |
| `features/tables/components/MasterWorkItemList.tsx` (+ test) | La bandeja renderizada: recibe los ítems, no los pide |
| `features/catalogs/components/CatalogPicker.tsx` | Extraído del wizard, porque ahora lo usan dos pantallas |
| `layouts/components/MasterSectionNav.tsx` | La navegación del contexto Master, que con una sola pantalla no hacía falta (#220) |
| `routes/master/MasterDashboardPage.tsx` | `/master`, con sus cuatro estados y el vacío como buena noticia |
| `routes/master/MasterTablePlayersTab.tsx` (+ test) | Quién dirige la mesa y quién juega en ella, en un solo lugar |
| `routes/master/MasterTableScheduleTab.tsx` | La agenda en hora local, solo lectura, con el enlace a editar |
| `routes/master/MasterTableEditPage.tsx` | El `PUT` que F1.2 dejó sin pantalla (#218) |
| `e2e/master-dashboard.spec.ts` | El co-master que gana y pierde la mesa, y la bandeja de llena a vacía |

Tocados: `api/queryKeys.ts` (+rama `master`, +alcance en `users.search`), `config/paths.ts`, `routes/router.tsx`, `layouts/MasterLayout.tsx`, `layouts/components/ContextSwitcher.tsx` y `AppHeader.tsx` (el home del contexto pasa a `/master`), `components/ForbiddenState.tsx` (acepta la explicación que su propio JSDoc ya documentaba), `features/users/` (`usersApi`, `useUserSearch`, `UserPicker` con `tableId`), `features/tables/` (tipos, índice, `gameTablesApi`, `useUpdateTable`), `features/catalogs/index.ts`, `routes/master/MasterTableDetailPage.tsx` (siete pestañas y el botón de editar), `MasterTableCreatePage.tsx` (usa el `CatalogPicker` compartido; su comentario en español pasó a inglés), siete hooks que invalidan la bandeja al resolver trabajo, `routes/help/HelpMastersTab.tsx` y los locales `master` y `help`.

**Ayuda:** `/help/masters#dashboard` (qué lista, por qué el orden es por tiempo, que el vacío es buena noticia y que no hay métricas), `#co-masters` (ampliada: quién agrega, que promover te degrada, que quitar no borra el registro y que al master no se lo quita) y `#edit-table` (cuándo se puede, que reemplaza en vez de parchear, y que corregir no reenvía a revisión). Las tres en `es` y en `en`, enlazadas desde la pantalla que las necesita.

**Salida real de las suites:**

```
./mvnw test    → 288 tests, 0 fallos
./mvnw verify  → 288 unitarios + 58 integración, 0 fallos
npx tsc -b     → limpio
npm run test   → 24 archivos, 178 tests
npx playwright test → 33 tests, todos verdes, contra el backend y el frontend reales
```

---

### F1.7 — Cierre de fase

No es trabajo nuevo ni es de un agente: es el corte de `plan-desarrollo.md` §6, puntos 6 y 7, en el hilo principal. **Y no es solo correr las suites.** Los tests de cada rebanada ya corrieron en verde en su momento; lo que nadie miró todavía es el **producto entero**: si cada pantalla es alcanzable navegando, si cada endpoint tiene puerta, si el recorrido del master cierra de punta a punta.

El instrumento de esa revisión es un **artifact** — *Revisión de cierre F1* — con el checklist, el diagrama de navegación del rol Master y el inventario de lo que quedó sin puerta en la UI. El detalle de los puntos 2 y 3 vive ahí y no se copia acá.

1. **Las cuatro suites en verde**, con la salida real reportada.
2. **Revisión de producto contra el artifact**: cada pantalla de F1 alcanzable **por navegación**, no por URL escrita a mano; cada una con sus cuatro estados; y el recorrido del rol Master cerrado de punta a punta, con los saltos donde depende de otro actor marcados.
3. **Inventario de huérfanos triado**, uno por uno: *bug de F1* o *alcance de F2/F3 anotado a propósito*. Un hueco implícito es una sorpresa (`plan-desarrollo.md` §1). Lo relevado al abrir F1.7:
   - `POST /{id}/pause` y `POST /{id}/resume` — construidos y **sin botón en ninguna pantalla**, anotado desde #163;
   - `GET /api/v1/files/{fileId}` — solo se consume `/content`;
   - `useUpdateFile`, `useDeleteFile` y `useCatalogValue` — hooks montados en **cero** pantallas;
   - `PauseRequested` — estado del enum que ningún endpoint produce.
4. **Inventario de archivos nuevos de la fase**, con su ruta.
5. **Documentación sincronizada**; `er-diagram-sync` corrida por cada `@Entity` nueva; i18n a la par (`es`/`en`); Javadoc y JSDoc **en inglés**.
6. **`/help` completo** para lo que F1 agregó, con sus `#ref` enlazados desde la pantalla que los necesita (#167, #168).
7. **Los hallazgos corregidos**, con su commit.

## 5. Lo que F1 explícitamente NO construye

Anotado a propósito: un hueco implícito es una sorpresa (`plan-desarrollo.md` §1).

| Queda fuera | Dónde vive |
|---|---|
| ~~Entregar respuestas a las peticiones~~ | **Se adelantó a F1.5** (#210) |
| Archivo de personaje en la postulación, `/my/files`, `/my/history` | F2 |
| Filtros del explorador por catálogo | F2 — el backend de F1.1 ya los resuelve |
| `/profile`, `/users/:id` | F2 |
| Pedir pausa (`PauseRequested`) y veto — necesitan `approval_requests` | F3 |
| `/admin/queue`, `/admin/users`, `/admin/settings`, `/admin/requests` | F3 |
| Comentarios y karma | F4 |
| Tiempo real, auditoría, borrado físico de archivos | F5 |

## 6. Verificación de punta a punta

Es el encargo de **A3** en cada rebanada (`plan-desarrollo.md` §7). El camino de abajo es el acumulado al cerrar la fase. Se prueba **contra el backend y el frontend que ya están corriendo** — no se levantan instancias paralelas.

```bash
cd backend && ./mvnw test          # unitarios, sin Docker
cd backend && ./mvnw verify        # + Testcontainers (colima arriba)
cd frontend && npx tsc -b          # typecheck strict
cd frontend && npm run test        # Vitest
cd frontend && npm run test:e2e    # Playwright contra el backend real
cd frontend && npm run format      # prettier del repo (#174)
```

**El camino manual completo al cerrar F1**, con el `DevPanel` (#158) para armar los actores:

1. Un admin siembra y administra catálogos en `/admin/catalogs`; fusiona dos sinónimos.
2. Un master crea una mesa con el wizard completo. Propone un tag nuevo y lo ve marcado como pendiente.
3. Ese master intenta crear una segunda mesa con horario solapado → `409` con el motivo en pantalla (R1).
4. El admin la aprueba; la mesa pasa a `Opened` y se materializan las sesiones.
5. Un jugador ve la mesa en el explorador **con la advertencia de choque** si corresponde, se postula, y en una mesa que choca con la suya no puede (R2).
6. El master lo acepta; las otras postulaciones del jugador que chocan le llegan como notificación (R4) y él retira una.
7. El master publica una petición y sube un archivo de preparación; al jugador le llega la notificación y ve las dos cosas en `/my/tables/:id`.
8. El master registra la asistencia de la primera sesión.
9. El master agrega un co-master; el co-master entra por su contexto y ve la mesa.
10. El master finaliza la mesa → `closed_at` sellado (#180).
11. `/master` queda vacío y dice que no hay nada esperando respuesta, no que algo se rompió.

## 7. Riesgos conocidos

- **`ScheduleConflictService` es la pieza con más casos borde de la fase** y toca tres flujos distintos: crear mesa, postularse y aceptar. Se escribe con sus tests **antes** de conectarla a nada.
- **La envoltura semanal en UTC es el bug más probable de F1.** La comunidad juega de noche en América, o sea de madrugada del día siguiente en UTC (#22): una sesión de martes 23:00 + 3 h termina miércoles 02:00. Va con test explícito.
- ~~**F1.4 es la rebanada más pesada**~~ — **cerrada.** Lo fue, y el job de retención no hizo falta recortarlo. El riesgo real resultó ser otro y no estaba anotado: **los dos ejes de visibilidad** (#204). `table_files.is_private` es del vínculo y `files.file_type = 'Public'` es del archivo, y confundirlos produjo los dos únicos bugs de la rebanada — uno de ellos escondía justamente el archivo que #79 existe para compartir.
