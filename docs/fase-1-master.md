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

| Hueco | Dónde se ve hoy |
|---|---|
| `game_tables.permitted` y `closed_at` **sin mapear** | comentario en `backend/.../tables/GameTable.java` |
| **`closed_at` nunca se sella** — es una regla de `modelo-datos.md` §5 que no se cumple | `GameTableService.finish()` / `.cancel()` |
| El wizard de `/master/tables/new` es **un formulario de cinco campos** | `frontend/src/routes/master/MasterTableCreatePage.tsx` lo dice explícito |
| `TableTypeController` **no existe** — `V2__seed.sql` siembra los tipos y no hay forma de listarlos | — |
| `NotificationType` tiene **tres valores** | `backend/.../notifications/NotificationType.java` |
| `features/catalogs/` y `features/files/` son carpetas con un `.gitkeep` | — |
| `POST /{id}/masters` existe desde E2 y **nunca tuvo interfaz** | — |

**Piezas del inventario de `frontend-diseno.md` §5 que F1 estrena:** `RichTextEditor`, `RichTextView`, `ScheduleEditor`, `FilePicker`, `DataTable`, `CollapsibleSection`, `IconAction`, `useConfirm`, `lib/date.ts`.

**Primitivas de shadcn que faltan y F1 necesita:** `command` (combobox), `calendar`, `table`, `sheet`, `tooltip`, `separator`, `checkbox`.

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

---

### F1.5 — Peticiones (lo que publica el master)

**Backend** — paquete `tasks/`:

- `TableTask` (`@Entity`), `TaskService`, `TableTaskController`.
- `audience` `Candidates` / `Players` / `Single`, con `target_user_id` solo en `Single` (#63, #76); `accepts_text` / `accepts_files` con al menos uno en `true`; `is_mandatory` **informativo, no bloqueante** (#70); `due_at` opcional.
- **Publicar una petición notifica a sus destinatarios** (#77) — `NotificationType.TaskPublished`. Una petición que nadie ve no se cumple.
- Lectura de entregas: el master ve quién entregó y quién no. **Las entregas se acumulan y el sistema no juzga si cumplen** (#76). `task_submissions` se **lee** en F1; el jugador entrega en F2.
- El incumplimiento se avisa y queda visible, pero **no bloquea ni expulsa** (#70): el sistema informa, las personas deciden.

**Frontend**:

- Pestaña **Peticiones** en `/master/tables/:id`: publicar, editar, cerrar, ver entregas y faltantes.
- Peticiones aplicables, solo lectura, en `/tables/:id` (audiencia `Candidates`) y `/my/tables/:id` (audiencia `Players` y las `Single` propias).

**Se prueba:** el master publica una petición para sus jugadores, les llega la notificación y la ven en su mesa.

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

---

### F1.7 — Cierre de fase

No es trabajo nuevo ni es de un agente: es el corte de `plan-desarrollo.md` §6, puntos 6 y 7, en el hilo principal.

1. `./mvnw test` y `./mvnw verify` en verde, con la salida real reportada.
2. `npx vitest run` y `npx playwright test` en verde.
3. Inventario de archivos nuevos de la fase, con su ruta.
4. Los cuatro estados obligatorios verificados en cada pantalla nueva.
5. Documentación sincronizada; `er-diagram-sync` corrida por cada `@Entity` nueva.
6. `/help` completo para lo que F1 agregó, con sus `#ref` enlazados desde la pantalla que los necesita (#167, #168).

## 5. Lo que F1 explícitamente NO construye

Anotado a propósito: un hueco implícito es una sorpresa (`plan-desarrollo.md` §1).

| Queda fuera | Dónde vive |
|---|---|
| Entregar respuestas a las peticiones | F2 |
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
- **F1.4 es la rebanada más pesada** y la única que toca el sistema de archivos. Si hay que recortar por tiempo, el candidato es el job de retención (#75): se puede diferir sin dejar callejón sin salida — pero se dice explícitamente, no se omite.
