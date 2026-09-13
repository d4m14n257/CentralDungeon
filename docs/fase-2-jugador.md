# F2 — Jugador: implementación

> **Cómo se construye F2**, la fase que le da al jugador todo lo que el master publicó en F1. `plan-desarrollo.md` §4 dice *qué* entrega; acá está el detalle de las cinco rebanadas, con sus archivos y su verificación.
>
> El *por qué* de cada decisión está en `decisiones.md`, las reglas de negocio en `modelo-datos.md` §5, las pantallas en `frontend-diseno.md` y el *cómo se escribe el código* en `arquitectura.md`.
>
> **Documento vivo mientras F2 esté abierta.** Cada rebanada se marca terminada acá al cerrarse, con su inventario. Cuando F2 cierre, este documento queda como registro y no se toca más.

## 1. Por qué existe este documento

`plan-desarrollo.md` §4 define F2 en un párrafo. Ese párrafo se escribió **antes** de construir F1, y F1 se llevó por delante buena parte de él: entregar respuestas a las peticiones se adelantó entera (#210), `/my/files` se adelantó y además creció con los cajones (#232, #233, #237, #241, #242), y el retiro de una postulación se adelantó porque el choque de horarios lo exigía (#178).

Lo que queda no es "el párrafo menos lo adelantado": es **lo que sobrevive, verificado contra el repositorio**. Este documento lo fija en cinco rebanadas verticales —backend + frontend + tests + ayuda, cada una terminando en un flujo que se puede probar a mano— con el mismo criterio que funcionó en F1.

F2 es **notablemente más chica que F1**. No arrastra subsistemas nuevos: los cinco que F1 levantó —catálogos, agenda, sesiones, archivos, peticiones— ya están, y F2 los **consume** desde el otro lado. Las dos entidades nuevas son una tabla puente (`registration_files`) y ninguna migración de schema.

## 2. El punto de partida

Verificado en el repositorio al abrir la fase, no asumido. Es la foto contra la que se mide lo que F2 agrega.

**Las suites, corridas al abrir F2:**

```
./mvnw test    → 318 tests, 0 fallos
npx vitest run → 27 archivos, 242 tests, 0 fallos
```

**Lo que ya está y F2 no construye, aunque `plan-desarrollo.md` §4 se lo prometa:**

| Lo que el plan le asigna a F2 | Dónde está hoy |
|---|---|
| `task_submissions` + `submission_files` | **F1.5** — se adelantó entero, con texto y archivos (#210) |
| `/my/tables/:id` completo: agenda, sesiones, asistencia, peticiones | **F1.2 / F1.3 / F1.5** — `routes/player/MyTableDetailPage.tsx` |
| `/my/files` (#65) | **F1** — `routes/my/MyFilesPage.tsx`, con cajones (#233), usos (#232), roles (#241) y buscador único (#242) |
| Retirar una postulación | **F1.2** — `DELETE /api/v1/registrations/{id}`, que R4 obligaba (#178) |
| Diferir la subida hasta confirmar la operación | **F1** — `StagedFile` + `useCommitStagedFiles` (#238) |
| El buscador con `/comando` y chips | **F1** — `common/search/` + `lib/searchQuery.ts` + `useSearchQuery` (#164, #239, #240) |
| Resolución de grupos de sinónimos | **F1.1** — el backend los resuelve; lo que falta es **quién los consume** |

**Los huecos concretos que F2 cierra**, cada uno verificado en el código:

| Hueco | Dónde se ve hoy | Cerrado en |
|---|---|---|
| `GET /api/v1/game-tables` **no tiene `?q=`**: el explorador no filtra por nada | `GameTableController.list` — solo `Pageable` | F2.1 |
| No existe `GameTableSearchField` ni `GameTableSearchSpecification`; los únicos buscadores son el de usuarios y el de archivos | `backend/.../common/search/` no tiene consumidor del lado de mesas | F2.1 |
| `registration_files` **sin mapear**: la hoja de personaje del uso 2 de #60 no se puede adjuntar | `CreateRegistrationRequest` lo dice explícito: *"the character file that goes with an application is F2"* | F2.2 |
| La **cuarta consulta de usos** de un archivo no existe: `/my/files` no sabe decir "usado en la postulación a X" | `FileService.usagesByFileId` resuelve tres, no cuatro (#232) | F2.2 |
| **Una postulación no se puede revisar antes de enviarse**, y no se edita después | pendiente anotado al final de #238 | F2.2 |
| **No hay perfil**: `/player/profile` ni `/player/users/:id` existen, y `UserController` solo expone `/me`, `/search` y el onboarding | `routes/router.tsx` — las rutas no están registradas | F2.3 |
| Las cinco reglas de **visibilidad de perfiles** de `modelo-datos.md` §5 están escritas y **ninguna implementada** | no hay `ProfileService` ni equivalente | F2.3 |
| **La asistencia solo existe por mesa**: `/profile` la necesita agregada sobre todas | `TableSessionService.summarize` recibe un `tableId` | F2.3 |
| `/player/my-tables` **lista también las terminadas y canceladas** | `GameTableService.listMine` filtra por `status = Player` de la postulación, no por el estado de la mesa | F2.4 |
| `/player/history` no existe (#133) | `routes/router.tsx` | F2.4 |
| **Tres tipos de notificación no llevan a ningún lado** — `ScheduleConflict`, `SessionScheduled`, `SessionCanceled` caen en el `default` y devuelven `null` | `features/notifications/lib/notificationTarget.ts:45` | F2.4 |

**Los tres últimos son deuda de F1**, relevada en F1.7 y todavía abierta. Se cierran acá y no antes **porque sus destinos son pantallas del contexto Jugador**: un choque de horarios se resuelve en `/player/applications` —retirando una— y una sesión movida o cancelada se lee en `/player/my-tables/:id`. Arreglarlos en F1 habría sido apuntar a pantallas que F2 todavía estaba por tocar.

**Piezas del inventario de `frontend-diseno.md` §5 que F2 estrena:** ninguna nueva. F2 compone con lo que F1 dejó: `FilePicker`, `DataTable`, `CollapsibleSection`, `SearchQueryInput`, `AttendanceSummaryView`, `UserPicker`.

**Primitivas de shadcn que faltan:** ninguna. `avatar` es la única candidata y se evalúa en F2.3 — el perfil puede resolverse con iniciales sobre un `div`, que es lo que hace hoy el header.

## 3. Las decisiones que F2 abrió

Cuatro preguntas se contestaron **antes** de escribir una línea, que es el paso 0 de `plan-desarrollo.md` §7. El razonamiento completo está en `decisiones.md`; acá, qué implica cada una para el trabajo.

| Decisión | Qué cambia en F2 |
|---|---|
| **#246** — los comandos de catálogo son texto libre y el grupo lo resuelve el backend | F2.1 declara los tres comandos con `examples` y **`SearchField` no cambia**: no hay tercer modo, no hay combobox adentro del buscador |
| **#247** — una postulación retirada conserva sus archivos; lo que muere es el uso | F2.2 **no** escribe cascada sobre `registration_files`; la escribe en las **lecturas**: la cuarta consulta de usos exige postulación viva, y el job de retención hereda ese filtro |
| **#248** — el perfil entra en F2 con la mitad de arriba | F2.3 se construye, sin `karma` ni `comments` en el DTO y sin dibujar la sección vacía. Lo que lo justifica son las cinco reglas de visibilidad, no la pantalla |
| **#249** — un perfil caducado responde `404` | F2.3 alinea la caducidad con el veto (#29): el `403` y el `200` recortado filtran la existencia de la persona |

Las reglas de negocio que salen de #247 viven en `modelo-datos.md` §5, como todas las demás.

## 4. Las cinco rebanadas

Cada una se cierra con las ocho de `plan-desarrollo.md` §6 en su alcance. **Ninguna arranca sin que la anterior tenga sus tests en verde y su inventario de archivos** — es el corte entre rebanadas, igual que entre fases.

Las secciones **Backend** y **Frontend** de cada rebanada son, literalmente, el encargo de A1 y de A2 (`plan-desarrollo.md` §7).

---

### F2.1 — El explorador encuentra

**Por qué primero:** es la puerta de entrada del jugador y la única pantalla suya que hoy no puede hacer su trabajo. Un explorador sin filtros es una lista paginada de todo lo que existe, y con veinte mesas abiertas deja de servir. Además es **el caso que motivó el diseño del buscador**: #164 se escribió pensando en `/tag`, y hasta ahora el comando no existe en ninguna caja.

**Backend** — en `tables/`:

- `GameTableSearchField` (enum) con cuatro campos, cada uno con su entidad adelante (#164, #239): `table_name` —el criterio básico, lo que busca un término sin comando—, `table_system`, `table_tag`, `table_platform`.
- `GameTableSearchSpecification` sobre `common/search/`, con la misma forma que `FileSearchSpecification`. **Los tres campos de catálogo no son columnas**: resuelven a un `exists` sobre la tabla puente, igual que `file_categories` en `FileSearchField.CATEGORIES`.
- **La resolución del grupo de sinónimos es la regla de la rebanada** (#54, #56): buscar por cualquier miembro trae las mesas etiquetadas con cualquier otro. La mesa sigue guardando el alias que eligió su master; el que viaja al `WHERE` es el grupo entero. Es lo que hace que fusionar "DANDD" con "D&D" en `/admin/catalogs` vuelva encontrables las dos **sin migrar una sola fila de `table_tags`**.
- **Un valor en `Created` no filtra** (#57): el tag que un master acaba de proponer no es un criterio de búsqueda para nadie más, y un `Rejected` o `Disabled` tampoco (#81).
- `?q=` en `GET /api/v1/game-tables`, parseado con `SearchQueryParser`. Se compone con el filtro de visibilidad que la lista ya tiene —estados públicos, sin las mesas que el actor dirige (#154), sin aquellas donde está `Blocked` (#29)— y **no lo reemplaza**: un criterio de búsqueda nunca amplía lo que alguien puede ver.

**Frontend**:

- `features/tables/searchFields.ts` — los cuatro comandos, con la forma de `features/files/searchFields.ts`.
- Los tres comandos de catálogo se declaran con **`examples` y no con `values`** (#246): texto libre, con ejemplos sacados de `V3__catalog_seed.sql` para que el primero que alguien pruebe devuelva mesas. El grupo lo resuelve el backend, que es donde vive la regla (#54).
- `SearchQueryInput` en `routes/player/TableListPage.tsx`, cableado con `useSearchQuery` — estado, string canónico, debounce y `?q=` en la URL vienen del hook, no se reescriben (#240).
- El `placeholder` nombra el criterio básico: *"Buscar por nombre de mesa"* (#242).
- La ayuda `basics.search` recibe los comandos de esta caja y arma con ellos su lista y sus ejemplos (#240) — no se escribe una ayuda nueva, se le pasan los campos.

**Tests:** unitarios de `GameTableSearchSpecification` con la matriz de grupos —alias→canónico, canónico→alias, alias→alias hermano, valor sin grupo, valor `Created` que no filtra, dos criterios que se combinan—; integración sobre MySQL real para el `exists` con el grupo, que es donde una consulta mal armada devuelve duplicados; Vitest de `searchFields.ts`; e2e: un admin fusiona dos sinónimos y el explorador encuentra la mesa por los dos términos.

**Se prueba:** una mesa etiquetada "DANDD" aparece al buscar `/tag D&D`, sin que se haya tocado su etiqueta.

#### ✅ Terminada

Una decisión nueva salió de planificarla —**#246**, los comandos de catálogo son texto libre— y se tomó antes de escribir una línea, que es el paso 0 de `plan-desarrollo.md` §7.

**Lo que la rebanada encontró y no era suyo: la suite estaba en rojo.** F2.1 abrió corriendo las cuatro suites para tener una foto del punto de partida, y la foto no era la que los documentos decían. **15 de 43 pruebas e2e y 7 de integración fallaban en `master`**, todas por #245: la mesa nace en `Draft` y el `create → approve` que nueve specs y dos ITs hacían desde hace un año dejó de tener sentido —un borrador no aparece en `/admin/tables` y la API contesta `409`—. Se arregló con un `submitForReview` compartido en `e2e/helpers/tableWizard.ts`, por el mismo motivo por el que ese archivo existe: nueve copias de las mismas dos líneas se rompen todas juntas.

**Y un bug real de F1, que el e2e encontró y ningún unitario podía**: `ScheduleConflictService.COMMITTING_STATUSES` **no incluía `Draft`**, mientras el Javadoc escrito justo encima decía, literal, que los borradores cuentan. O sea que un master podía armar dos borradores en la misma franja del viernes y enterarse recién cuando un admin aprobara el segundo — exactamente el escenario que ese comentario existe para descartar. La regla estaba documentada y sin implementar. Va corregido, con su unitario de comportamiento y no solo de argumento.

**Un tercer desfasaje, en la prueba y no en el código**: `my-files.spec.ts` verificaba la fila de toggles de cajón que **#242 quitó**. Lo que #237 afirma sigue siendo cierto y sigue siendo verificable, pero en otra superficie: el panel de subida, que es el único lugar que pregunta el cajón porque es el único sin flujo del que deducirlo.

**Backend** (`backend/src/main/java/com/centraldungeon/`):

| Ruta | Qué es |
|---|---|
| `tables/GameTableSearchField.java` | Los cuatro comandos, con su entidad adelante (#239) y su catálogo asociado |
| `tables/GameTableSearchSpecification.java` | El predicado: las reglas de visibilidad y la búsqueda unidas con `and` y nunca plegadas, y el `exists` que no duplica filas |
| `tables/GameTableSearchResolver.java` | El paso previo — una especificación no puede preguntarle nada a la base mientras se construye |
| `test/…/tables/GameTableSearchFieldTest.java` · `GameTableSearchResolverTest` · `GameTableSearchIT` | 5 + 8 unitarios y 11 de integración sobre MySQL real |

Modificados: `AbstractCatalogService` (+`resolveGroupIdsByName`, la pregunta de #54 hecha con palabras en vez de con un id), `CatalogValueRepository` (+2 lecturas derivadas), `GameTableRepository` (+`JpaSpecificationExecutor`, −la JPQL del explorador), `GameTableService.list` y `GameTableController.list` (+`?q=`), `GameTableServiceTest` (la exclusión de #154 se mudó al predicado, así que el unitario dejó de afirmarla y el IT la afirma de verdad), `ScheduleConflictService` (+`Draft`) y `ScheduleConflictServiceTest`, `GameTableTransitionIT` y `TableSessionIT` (#245), y el Javadoc del constructor de `GameTable`, que seguía diciendo `Preparation`.

**Sin migración Flyway**: F2.1 no toca el schema. Lee lo que F1.1 ya había sembrado.

**Frontend** (`frontend/src/`):

| Ruta | Qué es |
|---|---|
| `features/tables/searchFields.ts` (+ test) | Los cuatro comandos, todos con `examples` y ninguno con `values` (#246) |
| `e2e/table-search.spec.ts` | El sinónimo de punta a punta, el typo que no lista todo, y el `?q=` enlazable |
| `e2e/helpers/tableWizard.ts` | +`submitForReview`, el paso que #245 volvió obligatorio para nueve specs |

Tocados: `features/tables/api/gameTablesApi.ts` y `useGameTables.ts` (la búsqueda entra en la clave, no se filtra después), `features/tables/index.ts`, `routes/player/TableListPage.tsx` (la caja, el `?q=` en la URL y **dos estados vacíos distintos**: «no hay mesas» y «ninguna coincide» son dos noticias diferentes), `api/queryKeys.ts` (+`tables.lists()`) y `features/registrations/api/useWithdrawApplication.ts`, los locales `tables` en `es` y en `en` (#198), y ocho specs de e2e.

**Documentación sincronizada:** la fila de `/player` en el sitemap de `frontend-diseno.md` §2 decía «con filtros por sistema, tag y plataforma» y describe ahora los comandos que los reemplazan; `modelo-datos.md` §5 gana las dos reglas nuevas del buscador y corrige el «dónde» de la regla de #54, que apuntaba a un `CatalogService` que no es el que la implementa.

**Ayuda:** ninguna escrita a mano, y a propósito. `basics.search` recibe los comandos de la caja que la abrió (#240), así que el diálogo de ayuda del explorador se arma solo con `/table_name`, `/table_system`, `/table_tag` y `/table_platform` y sus ejemplos.

**Un bug que introdujo la propia rebanada y encontró la revisión de cierre**, no un test: el explorador pasó a llavear por lo buscado, así que su clave dejó de ser `['tables', 'list', undefined]` y `useWithdrawApplication` —lo único que invalidaba esa rama— dejó de alcanzarla. Retirar una postulación seguía funcionando y la tarjeta del explorador seguía diciendo «ya te postulaste». Se arregla con `queryKeys.tables.lists()`, la rama entera: una mutación no sabe qué tenía escrito el lector.

**Queda fuera a propósito, con su motivo:** el buscador de `/admin/tables` y sus filtros por estado (#176) son **F3** — esta rebanada construye el explorador del jugador, no el listado de administración. Y la **resolución de grupos no está acotada**: un criterio de una sola letra carga todos los valores aceptados que la contengan. Está anotado en el Javadoc de `resolveGroupIdsByName` con su razón — un tope tendría que elegir qué sinónimos descartar, y descartar uno en silencio rompe justamente la simetría que #54 define.

**Salida real de las suites:**

```
./mvnw test    → 333 tests, 0 fallos
./mvnw verify  → 333 unitarios + 82 integración, 0 fallos
npx tsc -b     → limpio
npm run test   → 28 archivos, 247 tests
npx playwright test → 43 tests, todos verdes, contra el backend y el frontend reales
```

---

### F2.2 — Postularse con la hoja de personaje

**Por qué acá:** es el uso 2 de #60, el único de los cuatro que sigue sin construirse, y es lo que convierte una postulación en algo que el master puede evaluar. Depende del `FilePicker` y de `StagedFile`, que F1 ya dejó terminados.

**Backend** — en `registrations/`:

- `RegistrationFile` + `RegistrationFileId` (`@Entity`, clave compuesta), con la forma exacta de `SubmissionFile` en `tasks/`. **Vive en `registrations/` y no en `files/`**, por el mismo criterio que puso `SubmissionFile` en `tasks/`: la postulación es su agregado dueño.
- `CreateRegistrationRequest` gana `fileIds`. Los archivos se **vinculan, nunca se copian** (#79), y pasan por el mismo permiso que adjuntar a una mesa: propios o publicados.
- **La séptima vía de lectura de un archivo** en `FileService.requireReadable`: el master de la mesa abre lo que le adjuntó un candidato. Es la hermana de la quinta (#211), que ya existe para las entregas, y tiene el mismo límite — el master de **esa** mesa, no cualquiera. *(Al planificar F2 se la llamó «la sexta»: el Javadoc de `requireReadable` documenta seis desde que #236 agregó los archivos del pedido, así que la que entra acá es la séptima.)*
- `FileService.classify` archiva el vínculo en el cajón `PlayerApplication` (#233). El cajón lo pone el vínculo, no quien sube: es add-only e idempotente, y ya tiene su método.
- **La cuarta consulta de usos** (#232): `usagesByFileId` pasa de tres agrupadas a cuatro. Sigue siendo constante por página, que es lo que importa.
- **El borrado, resuelto en #247.** No hay cascada: retirar una postulación **deja las filas de `registration_files` como estaban**, porque son el registro de que esa hoja se mandó y retirarse no lo deshace. Lo que sí cambia es lo que las lecturas hacen con ellas: la cuarta consulta de usos exige postulación **viva**, así que el archivo vuelve a reportar «sin usar» y la purga de #75 vuelve a alcanzarlo. **El filtro es la parte frágil de la rebanada**: sin él, retirar una postulación inmuniza el archivo contra la purga para siempre, en silencio. Va con test.
- No hay "quitar un archivo de mi postulación" suelto: una postulación enviada no se edita, y esa es la otra mitad de la decisión.
- **Una postulación no se edita una vez enviada.** Es lo que el final de #238 dejó anotado y lo que obliga a la pantalla de revisión del frontend.

**Frontend**:

- `ApplyToTableDialog` gana el `FilePicker` con subida diferida (#238): elegir deja el archivo en el navegador, el botón que envía la postulación es el que sube.
- **Paso de revisión antes de enviar** — el pendiente de #238. Es un diálogo de dos pasos, no un wizard: *escribir y adjuntar* → *revisar y enviar*, con lo que se va a mandar a la vista. El motivo está en el modelo y no en el gusto: esto no se puede corregir después.
- Las peticiones de audiencia `Candidates` ya se leen antes de postularse (#63, F1.5), así que el diálogo puede decir **qué se va a pedir** sin construir nada nuevo.
- `MasterTableCandidatesTab` muestra los archivos de cada candidato, con descarga. Reusa `FileList`.
- `/my/files` empieza a mostrar el cuarto uso sin tocar la pantalla: la fila dice *"postulación a «Mesa X»"* porque el backend ahora lo responde.

**Tests:** unitarios de vincular —archivo ajeno sin publicar (`403`), archivo publicado (ok), propio (ok), postulación de otro (`403`), retirar que arrastra el vínculo—; el conteo de usos con las cuatro fuentes; integración sobre MySQL real para la cuarta consulta agrupada y para el arrastre; e2e: un jugador se postula con su hoja, la revisa antes de enviar, el master la descarga desde candidatos, y el jugador la ve listada como uso en `/my/files`.

**Se prueba:** un jugador adjunta su hoja de personaje al postularse; el master la abre desde la pestaña de candidatos; el archivo sigue siendo uno solo en la biblioteca del jugador.

#### ✅ Terminada

**Primera rebanada repartida en subagentes** según `plan-desarrollo.md` §7: el contrato en el hilo principal, A1 sobre `backend/` y A2 sobre `frontend/` en paralelo, y la verificación al final. Lo que el reparto enseñó está más abajo, porque no fue gratis.

**El bug que encontró el e2e y ningún test de ninguno de los dos lados podía ver: A1 y A2 no coincidieron en el nombre del campo.** El backend manda `attachedFiles`, el frontend leía `files`, y la pestaña de candidatos del master reventaba con `Cannot read properties of undefined`. Los 338 unitarios del backend y los 252 de Vitest pasaban **los dos**, porque cada lado probaba contra su propia idea del contrato. Es exactamente lo que §7 anticipa —«si adivina la forma del DTO, adivina mal»— y **la culpa es del contrato, no de los agentes**: decía «un campo con los archivos adjuntos» sin fijar el nombre. Se alineó el frontend, que era el lado roto; el backend y sus tests estaban en verde y cambiarlos era mover riesgo sin ganar nada.

**El segundo hallazgo es una recurrencia, y ya es un patrón: `TestDataService` volvió a romperse por una clave foránea.** `registration_files` cuelga de la postulación y hay que borrarla antes; sin eso `DELETE /api/v1/test-data/e2e` responde `500`. **Es la quinta vez** — la agenda en F1.2, el calendario en F1.3, los archivos de mesa en F1.4 y las entregas en F1.5 (#171, #172). Y el síntoma nunca es el 500: la limpieza falla en silencio, la base se llena, y **la corrida siguiente falla por paginación en un lugar que no tiene nada que ver** — acá fueron 16 pruebas e2e en rojo y 47 mesas esperando revisión, con el admin buscando una fila que había quedado fuera de la primera página. Cinco veces es una clase de error que merece una barrera, no una corrección más: queda anotado para **F4.3**, que ya tiene el barrido de borrado en su alcance.

**Y un tercero, del mismo tipo que el de F2.1**: el diálogo de postulación pasó a dos pasos y con eso **rompió cuatro specs que no eran de esta rebanada** —`registration-flow`, `table-schedule`, `table-sessions`, `table-tasks`—, que apretaban «Postularme» dos veces para llegar a ser jugadores y probar otra cosa. Se resolvió con `applyToTable` en `e2e/helpers/application.ts`, por el mismo motivo por el que existe `submitForReview`: cuatro copias de las mismas tres líneas se rompen todas juntas.

Queda fuera a propósito, con su motivo: **quitarle un archivo a una postulación enviada** no existe y no va a existir — es la otra mitad de #247. **El vetado** todavía no se excluye de la séptima vía de lectura porque el veto es F3.4; queda anotado ahí junto con las otras seis.

**Backend** (`backend/src/main/java/com/centraldungeon/`):

| Ruta | Qué es |
|---|---|
| `registrations/RegistrationFile.java` · `RegistrationFileId` · `RegistrationFileStatus` | La fila puente con clave compuesta, con la forma de `SubmissionFile`. Vive en `registrations/` porque la postulación es su agregado dueño |
| `registrations/RegistrationFileRow.java` | La proyección interna que arma la lista por página. No cruza HTTP |
| `registrations/RegistrationFileRepository.java` | Las tres lecturas: los adjuntos por postulación, las mesas que alcanzan un archivo, y **la cuarta fuente de usos** (#232) filtrada por postulación viva (#247) |
| `registrations/dto/RegistrationFileResponse.java` | El record nuevo. Ninguno de `files/dto/` calzaba —los de mesa cargan `tableFileType`, los de archivo cargan campos de dueño— y el único con la forma exacta vive en `tasks/dto`, del que `registrations` no puede depender |
| `test/…/registrations/RegistrationFileIT.java` | 5 de integración sobre MySQL real |

Modificados: `RegistrationService` (vincular, leer, y el Javadoc de `withdraw` explicando por qué **no** hay cascada), `RegistrationMapper`, `CreateRegistrationRequest` (+`fileIds`), `RegistrationResponse` (+`attachedFiles`), `FileService` (la séptima vía de lectura y la cuarta fuente de usos, con su Javadoc — que ya anticipaba «a fourth source joins them in F2»), `RegistrationServiceTest` (+6), `FileServiceTest`, `RegistrationServiceIT`, y **`TestDataService`** por quinta vez.

**Sin migración Flyway**: `registration_files` ya estaba completa en `V1__baseline.sql`. F2.2 mapea, no agrega.

**Frontend** (`frontend/src/`):

| Ruta | Qué es |
|---|---|
| `features/registrations/components/ApplyToTableDialog.tsx` (+ test) | El diálogo en dos pasos: escribir y adjuntar → revisar y enviar. El segundo paso no es adorno: una postulación enviada no se edita |
| `e2e/table-application-files.spec.ts` | El recorrido entero, y el caso que protege #247: retirar deja el archivo y le quita el uso |
| `e2e/helpers/application.ts` | `applyToTable`, el paso que los dos pasos volvieron obligatorio para cuatro specs |

Tocados: `features/registrations/types.ts` e `index.ts`, `routes/player/TableDetailPage.tsx` y `routes/master/MasterTableCandidatesTab.tsx` (las dos pantallas que **componen** — `features/registrations` no importa de `features/files`, regla dura 16: el selector llega como render prop igual que en `TaskSubmitDialog`), los locales `tables`, `registrations` y `master` en `es` y en `en` (#198), y cuatro specs de e2e.

**Una decisión de forma que el contrato no tenía**: la lista de adjuntos que se **muestra** no filtra por estado de la postulación, mientras la de **usos** sí. Son dos preguntas distintas, que es la misma distinción de #232: qué se adjuntó es un hecho histórico y una postulación rechazada sigue mostrando con qué se mandó; qué cuenta como uso es lo que decide si la purga alcanza el archivo.

**Salida real de las suites:**

```
./mvnw test    → 338 tests, 0 fallos
./mvnw verify  → 338 unitarios + 87 integración, 0 fallos
npx tsc -b     → limpio
npm run test   → 29 archivos, 252 tests
npx playwright test → 45 tests, todos verdes, contra el backend y el frontend reales
```

---

### F2.3 — El perfil

**Por qué acá:** es lo que cierra el par de #41 —el jugador mira al master antes de postularse, el master mira al candidato cuando recibe la postulación— y por eso va **después** de que la postulación lleve su archivo: las dos mitades de "a quién estoy dejando entrar" quedan juntas.

**Las cinco reglas de visibilidad de `modelo-datos.md` §5 se implementan enteras acá.** Es la sección de reglas más grande que F1 no tocó.

**Backend** — en `users/`:

- `ProfileService` con la regla de visibilidad en **un solo lugar**, y las dos lecturas colgando de ella: `GET /api/v1/users/me/profile` y `GET /api/v1/users/{id}/profile`.
- Las cinco reglas: el perfil de un master lo ve cualquiera que mire su mesa (#41); el de un jugador se abre para el master **desde que recibe su postulación** (#41); los jugadores de una mesa se ven entre sí (#47); la visibilidad **caduca a las dos semanas de `closed_at`** y en `Pause` el reloj no corre (#44); el admin no tiene restricción (#45).
- **`closed_at` ya se sella** desde F1.2 (#180), así que la ventana tiene de dónde contarse. Es la primera vez que algo la lee.
- **Un perfil que no se puede ver responde `404`, no `403`** (#249), por el mismo criterio que el veto (#29): un `403` confirma que esa persona existe y que hubo una relación, que es exactamente lo que la caducidad cierra.
- **La asistencia agregada sobre todas las mesas**, con la forma de #137: los tres números sin colapsar, `Unknown` fuera del denominador, derivada con `GROUP BY` y **no cacheada** (#11). Es una consulta nueva sobre el índice cubridor `(user_id, attendance)` que el baseline ya tiene; lo que existe hoy agrupa por mesa.
- **Sin karma y sin comentarios**: son F5 (#248, #250). La respuesta **no lleva los campos**, ni siquiera en `null` — un DTO que promete `karma` le enseña al frontend a preguntarlo, y el primer componente que lo lea muestra un hueco donde va un número.

**Frontend**:

- `routes/player/ProfilePage.tsx` (`/player/profile`) y `routes/player/UserProfilePage.tsx` (`/player/users/:id`), sobre un `ProfileCard` compartido en `features/users/components/`.
- El wireframe de `frontend-diseno.md` §4 dibuja karma y comentarios. **Lo que F2 entrega es la mitad de arriba**: nombre, país, roles y asistencia. El bloque de comentarios **no se dibuja vacío**: una sección "Comentarios recibidos" sin nada adentro se lee como pantalla rota, que es justo lo que `frontend-diseno.md` §5 prohíbe para el estado vacío.
- Los **enlaces al perfil** son la mitad que hace que la pantalla exista: la línea "Master: nombre" del detalle de mesa, la lista de candidatos, la pestaña Jugadores y el padrón de asistencia.
- Los cuatro estados, con el de **sin permiso** diciendo la verdad de #44: *"Ya no podés ver este perfil"*, no *"no existe"* — el backend responde `404` para no confirmar nada, pero a quien tuvo la relación la pantalla le puede explicar la caducidad sin revelar nada nuevo.

**Tests:** la matriz de visibilidad es el test de la rebanada, un caso por regla y su negativo —jugador mirando al master de una mesa que no mira, master mirando a alguien que nunca se le postuló, compañeros de mesa, dos semanas y un día, mesa en `Pause` con seis meses encima, admin—; unitarios del agregado de asistencia con `Unknown` fuera; integración sobre MySQL real para la ventana temporal, que es donde un `BETWEEN` mal escrito se ve; e2e: un jugador abre el perfil del master desde el detalle de la mesa.

**Se prueba:** un jugador ve el perfil del master antes de postularse; el master ve el del candidato recién cuando le llega la postulación; dos semanas después de cerrada la mesa, ninguno de los dos ve al otro.

#### ✅ Terminada

**Las cinco reglas de visibilidad de perfiles llevaban un año escritas en `modelo-datos.md` §5 sin una sola línea que las implementara.** Ahora existen, en un solo método, con su matriz de diez casos unitarios y la ventana temporal ejercitada contra MySQL real. Eso es lo que justificaba construir la pantalla en F2 y no esperar a F5 con el karma (#248): F5 las necesita funcionando para decidir quién puede comentar a quién, y llegar ahí con la regla de privacidad más delicada del sistema sin estrenar habría sido construir el karma sobre una puerta que nadie abrió nunca.

**La ventana de #44 quedó en una sola línea, y es la parte elegante de la rebanada.** `Pause` no tiene ningún caso especial: `closed_at` es `NULL` mientras la mesa viva, así que «en `Pause` el reloj no corre» sale de la misma comparación que todo lo demás. Una mesa pausada con seis meses encima sigue abriendo el perfil, y hay un test que lo fija.

**Lo que el paquete propio evitó:** `ProfileService` no está en `users/`. `GameTableService` ya depende de `UserService`, y el perfil necesita leer mesas, sesiones y postulaciones — meterlo ahí habría creado un ciclo entre paquetes. Vive en `profiles/`, que es el precedente literal de #219 con `dashboard/`.

**Una decisión que A1 tomó y el contrato no cubría, y que estaba bien tomar así**: qué estados de mesa cuentan como «vínculo posible». El contrato nombraba `Opened`/`InProgress` para #41a, pero el test obligatorio de la mesa en `Pause` solo tiene sentido si `Pause` también cuenta. La lectura que quedó: **una mesa vincula desde que dejó de ser privada de su master** —es decir, todo salvo `Draft`, `Unassigned`, `Preparation`, `ChangesRequested` y `Deleted`— y la ventana de #44 se aplica encima, uniforme. Reconcilia el texto de #41a con el caso de #44 en vez de tratarlos como reglas sueltas.

**Y una que corrigió un error mío**: mi contrato decía que el agregado de asistencia llevaba un campo `total`. El campo real de `AttendanceSummaryResponse` es **`registered`**, y A2 lo verificó contra el backend antes de escribir el tipo en vez de inventar un alias. Es exactamente la comprobación que le faltó a F2.2 y que costó el choque `attachedFiles`/`files`.

Queda fuera a propósito, con su motivo: **karma y comentarios** son F5 y **el DTO no los lleva ni en `null`** (#248) — un DTO que promete `karma` le enseña al frontend a preguntarlo. **La sección «Comentarios recibidos» del wireframe no se dibuja vacía**: se leería como pantalla rota, que es lo que `frontend-diseno.md` §5 prohíbe.

**Backend** (`backend/src/main/java/com/centraldungeon/`):

| Ruta | Qué es |
|---|---|
| `profiles/ProfileVisibilityService.java` | Las cinco reglas en un solo método, y `isLinkStillOpen` — la ventana de #44 en tres líneas, sin rama para `Pause` |
| `profiles/ProfileService.java` · `ProfileController` · `dto/ProfileResponse` | El perfil, sus dos endpoints y su DTO. Existencia y visibilidad salen por la misma `NotFoundException`: el `404` no distingue (#249) |
| `test/…/profiles/ProfileVisibilityServiceTest.java` · `ProfileServiceTest` · `ProfileServiceIT` | 10 + 3 unitarios y 2 de integración: la matriz completa, la caducidad a los quince días, y la mesa pausada con seis meses |

Modificados: `SessionAttendanceRepository` (+`countByUser`, la hermana sin alcance de mesa), `TableSessionService` (+`summarizeAll`, con la lógica de `Unknown` extraída para que viva una sola vez), `MasterRepository` y `TableRegistrationRepository` (las lecturas que la visibilidad necesita, la de #41b agrupada para no consultar una vez por mesa), y `TableSessionServiceTest`.

**Sin migración Flyway**: F2.3 no toca el schema. Es la primera vez que algo **lee** el `closed_at` que F1.2 selló (#180).

**Frontend** (`frontend/src/`):

| Ruta | Qué es |
|---|---|
| `features/users/components/ProfileCard.tsx` (+ test) | La mitad de arriba del wireframe. La de abajo no se dibuja (#248) |
| `features/users/api/useMyProfile.ts` · `useUserProfile.ts` | Las dos lecturas |
| `routes/player/ProfilePage.tsx` · `UserProfilePage.tsx` (+ tests) | `/player/profile` y `/player/users/:id`, con sus cuatro estados |
| `components/AttendanceSummaryView.tsx` (+ test) | **Subido desde `features/tables/`**: lo necesitan dos features, y lo que necesitan dos features sube a la raíz (regla dura 16, igual que `types/catalog.ts` y `types/file.ts`) |
| `e2e/profile-visibility.spec.ts` | La asimetría de #41, el caso negativo, el admin sin restricción (#45) y el menú de cuenta |

Tocados: `api/queryKeys.ts` (+rama `profiles`, separada de `users.me()` porque esa alimenta el shell y nunca lleva asistencia), `config/paths.ts`, `routes/router.tsx`, `types/api.ts`, `features/tables/` y `features/users/`, `layouts/components/UserMenu.tsx` (+«Mi perfil»), y los **enlaces**, que son la mitad que hace que la pantalla exista: la línea del master en el detalle de mesa, la cola de candidatos y la pestaña Jugadores.

**Salida real de las suites:**

```
./mvnw test    → 353 tests, 0 fallos
./mvnw verify  → 353 unitarios + 89 integración, 0 fallos
npx tsc -b     → limpio
npm run test   → 32 archivos, 264 tests
npx playwright test → 49 tests, todos verdes, contra el backend y el frontend reales
```

---

### F2.4 — Mi historial, y las notificaciones que no llevaban a ningún lado

**Por qué última:** cierra #133(a), que es una corrección sobre una pantalla que F1 ya entregó, y arrastra los tres destinos de notificación que F1.7 dejó abiertos porque apuntan a pantallas de este contexto.

**Backend** — en `tables/`:

- `listMine` pasa a filtrar **por el estado de la mesa**, no solo por el de la postulación: `/player/my-tables` muestra lo vivo (#133).
- La lectura del historial: las mesas donde el actor fue `Player` y que están `Finished` o `Canceled`, con su `closed_at` y **su asistencia final** — el agregado de #137 por mesa, que ya existe desde F1.3.
- El campo *"si dejaste comentario"* que #133 menciona **no entra**: los comentarios son F5 y no hay de dónde sacarlo. Se anota acá para que no aparezca como sorpresa.

**Frontend**:

- `routes/player/PlayerHistoryPage.tsx` (`/player/history`), con su entrada en `PlayerSectionNav`.
- **#133(a) asumió un riesgo explícito** —dos listados casi iguales es el patrón que el sitemap eliminó del legacy— y la condición de aceptarlo fue que las columnas sean distintas. Si al construirla resulta ser la misma tabla con otro `WHERE`, la decisión dice qué hacer: se fusionan. Esa evaluación es parte de la rebanada, no un extra.
- `notificationTarget` gana los tres casos que faltan: `ScheduleConflict` → `/player/applications` —la notificación pide retirar una, y ahí están—, `SessionScheduled` y `SessionCanceled` → `/player/my-tables/:id`.

**Tests:** unitarios del filtro por estado en las dos lecturas; Vitest de `notificationTarget` para los tres tipos, con la regresión de que ninguno vuelva al `default`; e2e: una mesa que se termina desaparece de `/player/my-tables` y aparece en `/player/history` con su asistencia.

**Se prueba:** el master finaliza la mesa; el jugador deja de verla entre las suyas y la encuentra en su historial, con cuántas sesiones asistió.

#### ✅ Terminada

**La evaluación que #133(a) dejó pedida, hecha y con respuesta.** Esa decisión eligió ruta propia sobre un filtro dentro de `/player/my-tables` asumiendo un riesgo explícito —es el patrón de «listados casi iguales» que el sitemap eliminó del legacy— y dejó escrito que si terminaba siendo la misma tabla con otro `WHERE`, se fusionaba. **No lo es, y las columnas lo demuestran**: el listado vivo lleva cupo, jugadores, agenda y advertencia de choque —preguntas sobre una mesa que todavía recluta—, y el historial lleva `closed_at` y asistencia —preguntas sobre una que ya cerró—. Ninguno de los dos conjuntos significa nada en el otro: cupo en una mesa cerrada no dice nada, y la asistencia de una mesa viva todavía se está escribiendo sesión a sesión. **La ruta propia se queda.**

**El bug que la rebanada corrigió es el que la motivaba**: `listMine` filtraba por el estado de la **postulación** y nunca por el de la **mesa**, así que una mesa terminada seguía apareciendo entre «mis mesas» para siempre. `Pause` queda del lado vivo, que es lo correcto: una mesa pausada está congelada, no terminada (#32).

**Y cerró tres huérfanos que venían de la revisión de F1**: `ScheduleConflict`, `SessionScheduled` y `SessionCanceled` caían en el `default` de `notificationTarget` y devolvían `null` — el aviso llegaba, se leía, se marcaba como leído y **el clic no abría nada**. Ahora el choque lleva a `/player/applications`, que es donde se retira una postulación —la acción que la notificación de R4 pide—, y los dos de sesión al detalle de la mesa del jugador, que es donde está el calendario. Va con **un test de regresión que recorre todos los tipos que el backend define y exige que ninguno vuelva a caer en el `default`**: es la clase de hueco que se reabre sola cada vez que alguien agrega un tipo.

**Una técnica nueva en el repositorio**, que A1 introdujo y vale anotar: la prueba de integración que verifica que la asistencia del historial **no se resuelve con una consulta por fila** cuenta las queries con `Statistics` de Hibernate. No había precedente de eso acá. Es la única forma de fijar un N+1 con un test, y el historial es exactamente donde aparecería.

Queda fuera a propósito, con su motivo: el campo *«si dejaste comentario»* que #133 menciona **no entra** — los comentarios son F5 y no hay de dónde sacarlo.

**Backend** (`backend/src/main/java/com/centraldungeon/`):

| Ruta | Qué es |
|---|---|
| `tables/TableAttendanceCount.java` | La proyección agrupada por mesa. No cruza HTTP, mismo patrón que `CatalogUsageCount` |
| `tables/dto/GameTableHistoryResponse.java` | El DTO propio del historial: `closed_at` y asistencia, sin cupo ni choque |
| `test/…/tables/GameTableHistoryIT.java` | 2 de integración, una de ellas contando queries |

Modificados: `GameTableService` (`listMine` filtra por estado de mesa, +`listMineHistory`), `GameTableController` (+`GET /mine/history`), `GameTableMapper`, `SessionAttendanceRepository` (+`countByGameTablesAndUser`, agrupada por mesa **y** por valor), `TableSessionService` (+`summarizeByTables`, una consulta por página), `TableRegistrationRepository`, `GameTableServiceTest` (+5) y `TableSessionServiceTest` (+3).

**Sin migración Flyway**: F2.4 no toca el schema.

**Frontend** (`frontend/src/`):

| Ruta | Qué es |
|---|---|
| `routes/player/PlayerHistoryPage.tsx` (+ test) | `/player/history`, con sus cuatro estados y el vacío como noticia neutra |
| `features/tables/api/useTableHistory.ts` | La lectura, acumulativa con «Ver más» — es la única lista de esta familia que crece sin techo |
| `features/notifications/lib/notificationTarget.test.ts` | El test de regresión que recorre todos los tipos |
| `e2e/player-history.spec.ts` | La mudanza de una lista a la otra, y que al historial se llegue **navegando** |

Tocados: `features/tables/types.ts`, `gameTablesApi.ts` e `index.ts`, `api/queryKeys.ts` (+rama `history`), `config/paths.ts`, `routes/router.tsx`, **`layouts/components/PlayerSectionNav.tsx`** —sin esa entrada la pantalla no se alcanza navegando, que es justo el bug que la revisión de F1 encontró en este contexto—, `features/notifications/lib/notificationTarget.ts`, y los locales `tables` en `es` y `en`.

**Salida real de las suites:**

```
./mvnw test    → 361 tests, 0 fallos
./mvnw verify  → 361 unitarios + 91 integración, 0 fallos
npx tsc -b     → limpio
npm run test   → 34 archivos, 273 tests
npx playwright test → 51 tests, todos verdes, contra el backend y el frontend reales
```

---

### F2.5 — Cierre de fase

**Reescrita con #250**: lo que esta rebanada tenía de *revisión* se mudó a **F4**, y lo que queda es el corte entre fases. F2 cierra **hecha y sin revisar**, igual que F1 y que F3.

1. **Las cuatro suites en verde**, con la salida real reportada. **Esto no se movió**: una fase en rojo no cierra.
2. **La deuda de revisión de la fase, escrita** — el punto 5 nuevo de `plan-desarrollo.md` §6. Qué se construyó y no se verificó, con nombre: las pantallas nuevas cuyos cuatro estados nadie miró, los endpoints que ninguna interfaz llama, los hooks montados en cero lugares. **Es lo que hace que F4 empiece verificando en vez de redescubriendo.**
3. **Inventario de archivos nuevos de la fase**, con su ruta.
4. **Documentación sincronizada**; `er-diagram-sync` corrida por `RegistrationFile`, que es la única `@Entity` nueva; i18n a la par (`es`/`en`); Javadoc y JSDoc **en inglés**.
5. **Ayuda completa** para lo que F2 agregó, en `features/help/sections/` y levantada con `<HelpLink>` desde la pantalla que provoca la pregunta (#231).

**Lo que ya no está acá, y dónde está:** la revisión de producto del contexto Jugador entero —cada pantalla alcanzable navegando, sus cuatro estados, el recorrido del rol cerrado— y el triaje de huérfanos son **F4.1** y **F4.5**. El motivo está en #250: el contexto Jugador tiene costuras con el master y con el admin, y ninguna de las dos se puede mirar desde adentro de F2.

#### ✅ F2 terminada, sin revisar

**Salida real de las cuatro suites al cerrar la fase:**

```
./mvnw test    → 361 tests, 0 fallos
./mvnw verify  → 361 unitarios + 91 integración, 0 fallos
npx tsc -b     → limpio
npm run test   → 34 archivos, 273 tests
npx playwright test → 51 tests, todos verdes, contra el backend y el frontend reales
```

Desde el punto de partida de la fase —318 unitarios y 242 de Vitest— F2 sumó **43 unitarios, 10 de integración, 31 de Vitest y 6 e2e**.

**La ayuda de la fase está escrita** (#231), y la escribió el hilo principal al cerrar porque ninguna rebanada la había tocado: `players.profile` —quién ve tu perfil, y sobre todo **por qué deja de verse**, que es lo único que nadie adivina mirando un perfil que hoy se ve— y `players.history` —por qué una mesa se mudó sola—. `players.applying` se corrigió: era anterior a la hoja de personaje y al paso de revisión, y decía que postularse eran cuatro pasos. Las dos nuevas están enlazadas con `<HelpLink>` desde la pantalla que provoca la pregunta.

### La deuda de revisión de F2

Es el punto 5 nuevo de `plan-desarrollo.md` §6 (#250): **lo que F2 construyó y no verificó**, escrito ahora para que F4 empiece verificando en vez de redescubriendo.

| Sin verificar | Qué haría falta |
|---|---|
| **Los cuatro estados** de las cuatro pantallas nuevas —`/player/profile`, `/player/users/:id`, `/player/history`— y del diálogo de postulación reescrito | F4.1 |
| **Que cada pantalla de F2 sea alcanzable navegando.** Solo se comprobó para `/player/history`, y con un e2e escrito a propósito porque es el bug que F1 tuvo acá | F4.1 |
| **La matriz de visibilidad de perfiles contra roles que todavía no existen**: F2 la probó con `Player`, `Master` y `Admin`. Falta `Owner`, y falta el vetado — que es F3 | F4.2 |
| **Las siete vías de lectura de un archivo, juntas.** F2.2 agregó la séptima y probó esa; nadie las probó todas contra el actor que no debería pasar | F4.4 |
| **`registration_files` frente al borrado lógico**: #247 decidió que la fila sobreviva, y se probó por el lado del uso. No se barrió qué otras lecturas podrían devolverla | F4.3 |
| **`TestDataService` volvió a romperse por quinta vez.** Corregido, pero la clase de error sigue viva | F4.3, que ya tiene el barrido en su alcance |

## 5. Lo que F2 explícitamente NO construye

Anotado a propósito: un hueco implícito es una sorpresa (`plan-desarrollo.md` §1).

| Queda fuera | Dónde vive |
|---|---|
| Karma y comentarios en el perfil — `/player/profile` entrega la mitad de arriba del wireframe | F5 |
| *"Si dejaste comentario"* en el historial (#133) | F5 |
| Pedir pausa (`PauseRequested`) y veto — necesitan `approval_requests` | F3 |
| `/admin/queue`, `/admin/users`, `/admin/settings`, `/admin/requests` | F3 |
| Los botones de `POST /{id}/pause` y `/resume`, construidos desde E2 y sin pantalla (#163) | F3 |
| Tiempo real, auditoría, borrado físico de archivos, "ver como" | F6 |
| **La verificación de lo que F2 construye**: los cuatro estados de sus pantallas, el mapa de navegación del contexto Jugador y el triaje de huérfanos | **F4** (#250) |
| Editar una postulación ya enviada | **No se construye nunca**: es la decisión de F2.2, no un pendiente |

## 6. Verificación de punta a punta

Es el encargo de **A3** en cada rebanada (`plan-desarrollo.md` §7). Se prueba **contra el backend y el frontend que ya están corriendo** — no se levantan instancias paralelas.

```bash
cd backend && ./mvnw test          # unitarios, sin Docker
cd backend && ./mvnw verify        # + Testcontainers (colima arriba)
cd frontend && npx tsc -b          # typecheck strict
cd frontend && npm run test        # Vitest
cd frontend && npm run test:e2e    # Playwright contra el backend real
cd frontend && npm run format      # prettier del repo (#174)
```

**El camino manual completo al cerrar F2**, con el `DevPanel` (#158) para armar los actores:

1. Un admin fusiona dos sinónimos en `/admin/catalogs`.
2. Un jugador busca en `/player` por el término que **no** está en la mesa y la encuentra igual (#56).
3. Abre el detalle, lee lo que la mesa le va a pedir, y **abre el perfil del master** desde ahí.
4. Se postula adjuntando su hoja de personaje, **revisando antes de enviar**.
5. El master ve la postulación, **abre el perfil del candidato** —que hasta ese momento no podía ver— y descarga la hoja.
6. El jugador ve su hoja listada en `/my/files` con el uso *"postulación a «Mesa X»"*.
7. El master lo acepta; la mesa aparece en `/player/my-tables` y no en el historial.
8. El master finaliza la mesa; la mesa se muda a `/player/history` con la asistencia final.
9. Dos semanas después de `closed_at`, ninguno de los dos ve el perfil del otro (#44).

## 7. Riesgos conocidos

- **La resolución del grupo de sinónimos en una `Specification` es la pieza con más forma de devolver duplicados.** Un `join` sobre la tabla puente multiplica filas por cada valor que matchea, y con paginación eso se ve como una mesa que aparece dos veces o como una página de 19. Se escribe como `exists`, no como `join`, y va con test de integración sobre MySQL real — un unitario con la Criteria API mockeada no puede verlo.
- **La ventana de dos semanas de #44 es un cálculo temporal sobre un campo nullable.** `closed_at` es `NULL` mientras la mesa viva, y una comparación que no lo contemple deja de mostrar perfiles de mesas en curso o los muestra para siempre. El reloj detenido en `Pause` es el caso que más fácil se escribe al revés.
- **La postulación no se edita, así que el paso de revisión no es un adorno.** Es la única barrera entre alguien y una hoja de personaje equivocada adjuntada para siempre. Si por tiempo hubiera que recortar algo de F2.2, no es esto.
- **F2.4 puede descubrir que `/player/history` no se justifica.** #133 lo dejó dicho: si termina siendo la misma tabla con otro `WHERE`, se fusiona con `/player/my-tables`. Que la rebanada exista no obliga a que la pantalla sobreviva.
