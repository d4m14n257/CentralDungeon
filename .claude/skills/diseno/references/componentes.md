# 5. Componentes

> Parte de la skill `diseno` (#273). Se movió desde `docs/frontend-diseno.md` §5 y §5.b y **conserva su numeración**. Se lee antes de crear cualquier componente: dice qué existe ya (#261). Las clases y componentes de patrón —lo que una pantalla nunca vuelve a escribir a mano— están en §5.c, `patrones.md`.

### Primitivas shadcn/ui

Se generan en `components/ui/`. Antes de crear cualquiera se consulta el MCP `shadcn-ui` para usar la API real y no aproximarla.

**Construidas hoy, 21**: `alert` · `avatar` · `badge` · `button` · `card` · `checkbox` · `command` · `dialog` · `dropdown-menu` · `form` · `input` · `label` · `popover` · `select` · `separator` · `skeleton` · `sonner` · `table` · `tabs` · `textarea` · `tooltip`. Las que este documento previó y todavía no se necesitaron: `sheet`, `pagination`, `calendar` y `combobox` — el combobox se resolvió con `command` + `popover`, que es lo que shadcn genera para eso.

**Son las únicas piezas del frontend sin JSDoc, y es la excepción declarada** en `CLAUDE.md` (*Documentación del código*): es código que escribe el CLI de shadcn y no se edita a mano, así que documentarlo sería documentar algo que la próxima regeneración pisa. Verificado y no asumido: los 21 archivos siguen tal como entraron en su commit, con comillas dobles y clases `bg-primary`/`text-primary-foreground` en vez de los tokens del `@theme` — ninguno pasó por una mano. Si alguno alguna vez se edita, deja de ser generado y entra en la regla dura 19 como cualquier otro archivo.

#### Las tres que se apartan del default

Todo lo demás se usa tal como viene: los tokens del `@theme` ya lo tiñen solo. Estas tres **cambian de estructura**, no de color, y por eso hay que saberlo antes de generarlas:

| Primitiva | Qué cambia | Por qué |
|---|---|---|
| `badge` | Lleva **punto de color + etiqueta de texto**, no solo texto | El color nunca es el único portador de información (§3). `Pause` y `PauseRequested` comparten familia y solo se distinguen leyendo |
| `dialog` | En móvil es un **sheet desde abajo** con asa, no un modal centrado | Un diálogo centrado en 375 px queda pegado a los bordes (#138) |
| `table` | En móvil **deja de ser tabla**: cada fila pasa a ficha. Nunca scroll horizontal | Los listados de Admin y Owner tienen cinco o más columnas (#138) |

`button` no cambia de estructura, pero su color de texto **se calcula**: se elige entre casi-negro y blanco el que da AA sobre el relleno del acento, en cada tema. No es un valor fijo.

### Compuestos sin dominio

En `components/`. Ninguno recibe una entidad del dominio: si la recibiera, estaría mal ubicado (skill `arquitectura` §3.1.2).

> Los compuestos de esta sección están dibujados en `design/out/`: `components-dialogs.html` (ConfirmDialog, FormDialog), `components-data.html` (DataTable, CollapsibleSection, IconAction), `components-inputs.html` (FilePicker, RichText, ScheduleEditor), `components-shell.html` (NotificationBell, ContextSwitcher, UserMenu), `ui-states.html` (EmptyState, ErrorState, ForbiddenState) y `components.html` (badges, karma, GameTableCard). `SearchQueryInput` y `UserPicker` (#164, #165) todavía no tienen preview: se construyeron directo en la pantalla que los pedía.

| Componente | Para qué |
|---|---|
| `FormDialog` | Envoltorio de todo formulario en modal: título, descripción y confirmación al cerrar con cambios sin guardar (#110) |
| `ConfirmDialog` | Toda acción irreversible (principio 3), detrás de `useConfirm` |
| `PageHeader` | **La cabecera de toda pantalla** (#273): título (`h1` con `.page-title`), una línea de propósito y, enfrente, la ayuda o el botón principal. Las pantallas de detalle cuya cabecera tiene otra forma usan la clase `.page-title` sola (§5.c) |
| `DataTable` | Listados paginados con orden, sobre `PageResponse<T>`. **Es dueño del contenedor de acciones**: envuelve `renderActions` en `.row-actions`, así ninguna pantalla lo escribe (#273) |
| `CollapsibleSection` | Bloque plegable con título y acciones en la cabecera — el patrón que el legacy repetía en `CardComponent` y `ListComponent` |
| `IconAction` | Botón de icono con tooltip para las acciones de una fila o una ficha. **Es la forma de toda acción de fila en una tabla** (#272): ver «Listas de trabajo», abajo |
| `EmptyState` | Listas vacías, con la acción que corresponde |
| `ErrorState` | Error de carga: mensaje del `ProblemDetail` y botón de reintento |
| `ForbiddenState` | El `403` explicado (el `404` por veto se ve como "no existe", que es intencional) |
| `RichTextEditor` | Texto enriquecido (#62), sanitizado al enviar y al mostrar |
| `RichTextView` | Render sanitizado de lo guardado |
| `LoadMore` | Paginación de un listado de lectura: trae la página siguiente y siempre dice cuántos de cuántos se están viendo. Botón explícito, nunca scroll infinito (#173) |
| `PaginationControls` | Paginación de una lista de trabajo (#173, #271): página X de Y y el total, anterior/siguiente, tira numerada compacta (`1 … 899 900 901 … 1000`), salto directo con «Ir a…» y, si la pantalla lo pide, el selector «Por página» (10 · 25 · 50 · 100) |
| `SearchQueryInput` | **Todo buscador de la app** (#164, #240). Texto suelto busca por el criterio básico; `/` abre la lista —comandos, y `/and`/`/or` cuando hay algo que unir— y **elegir de ahí escribe el comando en el texto, igual que tipearlo a mano**: hasta **Enter** todo es texto, y Enter es lo que lo cierra en chips. **Buscar son dos Enter** (#268): el primero cierra lo escrito en chips, el segundo —con el texto vacío— busca, y mientras los chips difieran de lo buscado la caja se marca con borde de marca y un botón «↵ Buscar» que confirma igual que Enter. Recibe `searchedQuery` y `onSearch`. «Limpiar filtros» aparece cuando hay algo que limpiar y vuelve a la búsqueda por defecto de la pantalla (`defaultQuery`), incluidos los filtros que viven al lado de la caja (`extraFiltersActive`, `onClearExtraFilters`). Un comando de opciones fijas ofrece sus valores en cuanto hay un espacio después de él, venga escrito o elegido; las comas separan alternativas y el chip del conector se toca para pasarlo de "y" a "o". Recibe los comandos que acepta, no los conoce, y con ellos arma además los ejemplos de su ayuda |
| `StatusBadge` | **Todo badge de estado de la aplicación** (#261): punto de color + etiqueta. Recibe el `tone` —una de las nueve familias de §3— y la etiqueta **ya traducida**; el mapa de estado a tono y el `t()` quedan en cada feature, que es la parte que sí le pertenece. Las clases viven acá como literales completos porque Tailwind 4 no ve una clase armada con template string |
| `WizardSteps` | El riel de pasos de un formulario largo, con el paso actual y los que ya se completaron. Hoy lo usa solo el wizard de crear mesa, que es el único formulario de varios pasos que existe |
| `AttendanceSummaryView` | Los tres números de asistencia de #137 —presentes, ausentes, justificados— sin saber de qué mesa son. Lo usan la pestaña del master y la ficha del jugador |
| `LanguageSwitch` | Elegir idioma, recordado sin ida al servidor (#198). Vive acá y no en `UserMenu` porque `/login` no tiene header y también lo necesita |
| `BackendStatusIndicator` | Si el backend responde. En `RootLayout` para que se vea en toda pantalla, `/login` incluida |
| `useSearchQuery` | El cableado alrededor de esa caja, escrito una vez (#240): estado de los chips, la última consulta confirmada (`query`, `onSearch`) y escritura del `?q=` cuando una búsqueda la cambia — sin debounce desde #268. Cada feature declara sus comandos en un `searchFields.ts` propio — `userSearchFields`, `myFileSearchFields`, `adminFileSearchFields` |

### Compuestos con dominio

Viven en su feature, no en las capas transversales de la raíz, aunque se usen en varias pantallas de esa misma feature:

| Componente | Dónde |
|---|---|
| `TableStatusBadge` — los nueve estados, con su token y su etiqueta | `features/tables/` |
| `ScheduleEditor` — día de semana + hora, mostrado en hora local | `features/tables/` |
| `GameTableCard` — la ficha del explorador | `features/tables/` |
| `RegistrationStatusBadge` — los cinco de postulación | `features/registrations/` |
| `FilePicker` — subir **o** reutilizar del historial (#65) o de lo publicado (#79). Recibe el **cajón en el que está parado** y la pestaña Publicados pide justo lo que la comunidad publicó para ese momento (#233) | `features/files/` |
| `FileDropzone` — arrastrar y soltar, con los límites dichos antes de romperlos y el error **inline** bajo la zona. **No sube: acumula** (#238) | `features/files/` |
| `StagedFileList` — lo que está por subirse, con su botón de quitar. Sin subida inmediata, es la única señal de que el archivo se tomó (#238) | `features/files/` |
| `FileCategoryChoice` — el cajón como chips, no como `<Select>`: es una decisión previa al envío y verla entera es lo que deja tomarla (#233) | `features/files/` |
| `FileCard` — la fila de un archivo: icono por MIME, tamaño, categoría, último uso y dónde se usa | `features/files/` |
| `FileCategoryFilter` — los cinco cajones como fila de toggles, no como `<Select>` (#233). **Solo en `/admin/files`**: en `/my/files` el cajón se narrowea desde el buscador con `/file_categories` (#242) | `features/files/` |
| `FileUsageChips` — dónde se usa un archivo, o «sin usar», que es el aviso de la purga (#232, #75) | `features/files/` |
| `KarmaBadge` — número + indicador cualitativo. **Nunca se construyó**: el karma se pinta dentro de `ProfileCard` y como texto en las listas | `features/users/` |
| `UserPicker` — buscar una persona y elegirla, sobre `SearchQueryInput`; el criterio básico es el nombre de Discord **o** el del sistema (#164) | `features/users/` |
| `NotificationBell` — contador y panel, alimentado por WebSocket | `features/notifications/` |
| `ContextSwitcher` — el selector de rol de §2 | `layouts/components/` (es shell, no dominio) |
| `UserMenu` — avatar, idioma, tema y cerrar sesión | `layouts/components/` |
| `SystemFeedbackDialog` — el botón global de §2, sobre `FormDialog`; maneja el `429` de la cuota como mensaje, no como error roto. **Todavía no construido**: `features/feedback/` existe vacío y `system_feedback` es de F5 (#250) | `features/feedback/` |

### Inventario completo, y lo que el inventario curado escondía

La tabla de arriba es **curada**: nombra los compuestos con dominio que tienen algo que explicar. No es el inventario, y durante varias fases se leyó como si lo fuera — con la consecuencia concreta que está más abajo. Lo que hay construido hoy, entero, es **65 compuestos con dominio en 11 features**, más 7 de shell:

| Feature | Compuestos |
|---|---|
| `adminQueue` (2) | `ClaimBadge` · `QueueItemKindBadge` |
| `approvals` (7) | `BanRequestsSection` · `RequestDetailPanel` · `RequestStatusBadge` · `RequestTypeBadge` · `ResolveRequestDialog` · `SubmitRequestDialog` · `SubmitRequestSection` |
| `catalogs` (8) | `AcceptCatalogValueDialog` · `CanonicalPicker` · `CatalogChip` · `CatalogCombobox` · `CatalogPicker` · `CatalogStatusBadge` · `DisableCatalogValueDialog` · `MergeCatalogGroupsDialog` |
| `files` (12) | `EditFileDialog` · `FileCard` · `FileCategoryBadge` · `FileCategoryChoice` · `FileCategoryFilter` · `FileDropzone` · `FileList` · `FilePicker` · `FileTypeBadge` · `FileUsageChips` · `PublishFileDialog` · `StagedFileList` |
| `help` (3) | `HelpBlocks` · `HelpDialog` · `HelpLink` |
| `notifications` (1) | `NotificationBell` |
| `registrations` (4) | `ApplyToTableDialog` · `BlockPlayerDialog` · `RegistrationStatusBadge` · `RejectRegistrationDialog` |
| `settings` (2) | `SettingHistory` · `SettingValueDialog` — F3.5 (#141). La pantalla no usa `DataTable`: son cuatro filas que se leen enteras, no un listado que se recorre y se busca |
| `tables` (10) | `AttendanceEditor` · `CreateUnassignedTableDialog` · `GameTableCard` · `JustifiedTableActionDialog` · `MasterWorkItemList` · `ScheduleEditor` · `SessionList` · `SessionStatusBadge` · `TableStatusBadge` · `WeeklyScheduleGrid` |
| `tasks` (9) | `ApplicableTaskList` · `MySubmissions` · `TableTasksSection` · `TaskAudienceBadge` · `TaskBoardList` · `TaskFormDialog` · `TaskStatusBadge` · `TaskSubmissionsPanel` · `TaskSubmitDialog` |
| `users` (7) | `AdminUserRolesCell` · `BlockUserDialog` · `ProfileCard` · `RoleChangeDialog` · `UserAdminHistory` · `UserPicker` · `UserStatusBadge` |
| shell (7) | `AdminSectionNav` · `AppHeader` · `BrandMark` · `ContextSwitcher` · `MasterSectionNav` · `PlayerSectionNav` · `UserMenu` — en `layouts/components/` |

**Tres que este documento prometió y no existen**: `KarmaBadge` (el karma se pinta dentro de `ProfileCard` y como texto en las listas, nunca como badge propio), `SystemFeedbackDialog` (F5, ya anotado) y el hook `useTableSelection` (ninguna tabla pide selección múltiple todavía, ya anotado). `KarmaBadge` no estaba anotado y ahora lo está: un componente prometido que nadie construyó es una pieza que la próxima fase cree que puede reusar.

#### El badge de estado estaba escrito diez veces — resuelto en #261

**Fue el hallazgo que abrir el inventario completo produjo**, y era un incumplimiento de skill `arquitectura` §3.1.2: esa regla fija el umbral en **dos** usos reales, más bajo que el del backend, y la razón que da es exacta — *«acá la alternativa a subir no es un poco de duplicación: es un import prohibido»*. Nueve no era un caso de borde.

**Ya está subido**: `components/StatusBadge.tsx`, y los diez pasaron a usarlo. Queda escrito lo que había porque es lo que explica la forma del componente y lo que evita que el próximo se escriba de cero.

**Eran diez y no nueve, y el décimo es el dato interesante**: `routes/NotificationsPage.tsx` tenía la copia completa —el mismo bloque y el mismo mapa `{ badge, dot }`— y el primer recuento no la vio porque solo miró `components/` y las carpetas `components/` de las features. Una pantalla también es un lugar donde alguien escribe un componente, y buscar duplicados solo donde los componentes *deberían* estar es cómo se cuenta uno de menos.

Los diez renderizaban **el mismo bloque, byte a byte**: `<span class="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium">` con un punto `size-1.5 rounded-full` adentro y la etiqueta traducida al lado. Ocho de ellos además comparten la misma forma entera — un `Record<Estado, { badge, dot }>` con las clases escritas literales y un `t()` sobre el estado:

| Componente | Feature | Namespace | Clave | Estados |
|---|---|---|---|---|
| `TableStatusBadge` | `tables` | `tables` | `status.${status}` | 10 |
| `SessionStatusBadge` | `tables` | `tables` | `sessions.status.${status}` | 3 |
| `RegistrationStatusBadge` | `registrations` | `registrations` | `status.${status}` | 4 |
| `UserStatusBadge` | `users` | `admin` | `users.status.${status}` | 3 |
| `RequestStatusBadge` | `approvals` | `admin` | `requests.status.${status}` | 3 |
| `CatalogStatusBadge` | `catalogs` | `catalogs` | `status.${status}` | 4 |
| `TaskStatusBadge` | `tasks` | `tasks` | `status.${status}` | 2 |
| `FileTypeBadge` | `files` | `files` | `fileType.${fileType}` | 3 |
| `ClaimBadge` | `adminQueue` | `admin` | — (dos ramas, no un `Record`) | — |
| el badge de resultado | `routes/NotificationsPage.tsx` | `notifications` | `badge.accepted` · `badge.rejected` | 2 |

**Difieren en tres cosas y en ninguna más**: el namespace de i18n, el prefijo de la clave, y el mapa de estado a token. Todo lo demás —el `cn()`, las clases del contenedor, el tamaño del punto, el orden de los dos hijos— es el mismo texto repetido ocho veces.

**Cómo quedó.** `StatusBadge` recibe un `tone` —una de las nueve familias de estado de §3— y la etiqueta ya traducida. El `tone` y no un par de clases, por dos razones: al subir se le quita el dominio, así que un componente de `components/` que sepa qué es `PauseRequested` estaría mal ubicado; y el mapa de clases literales, que existe porque Tailwind 4 no ve una clase armada con template string, pasa a estar en **un** archivo en vez de replicado en nueve. Cada feature conserva su `Record<Estado, StatusTone>` y su `t()`.

`ClaimBadge` es el único que mantuvo forma propia encima: no pinta el valor de un enum sino la respuesta a un sí-o-no, y el «sí» lleva además el «hace cuánto». Usa `StatusBadge` con ese dato como hijo.

**Dos badges quedaron afuera a propósito**, y no son deuda: `FileCategoryBadge` es neutro con borde y un icono —está apagado a propósito para no competir con el `FileTypeBadge` que tiene al lado— y `TaskAudienceBadge` no tiene fondo, es texto atenuado con un punto `aria-hidden`. No comparten el bloque ni el contrato de accesibilidad, así que subirlos sería «abstraer lo que solo se parece», que es lo que §3.1.2 prohíbe en su tercer punto.

**Salida real del refactor**: los diez badges perdieron 159 líneas y ganaron 108. `npx tsc -b` limpio, `npm run test` **488/488 en 55 archivos**, `npm run test:e2e` **59/59**, `npm run format` sin reescrituras.

**Ningún test existente se tocó**, que era la condición: el de `TableStatusBadge` —el único que los nueve tenían— sigue verde sin una línea cambiada, porque afirma la *presencia* de la clase del punto y que la etiqueta esté junto a él, y eso es exactamente el contrato que `StatusBadge` preserva. Se sumó uno nuevo, `components/StatusBadge.test.tsx`: la invariante «el color nunca viaja solo» estaba fijada para mesas y confiada para las otras ocho, y ahora que hay un solo lugar se fija ahí para los nueve tonos a la vez.

**Por qué se sostuvo diez veces sin que nadie lo viera**, que es la parte que importa más que el duplicado: la tabla curada de más arriba nombra **dos** de los diez —`TableStatusBadge` y `RegistrationStatusBadge`— y no dice que los otros ocho existen. Una fase que quiere un badge nuevo lee ese inventario, encuentra dos badges de dominio, concluye que un badge es cosa de cada feature, y escribe el siguiente. El inventario incompleto no es un problema de prolijidad: es el mecanismo por el que el duplicado se reproduce — y es por eso que este documento entrega ahora el inventario entero y no una selección.

#### El diálogo con motivo obligatorio, siete veces — y por qué acá la regla dice lo contrario

Siete diálogos en cinco features comparten el mismo esqueleto: `FormDialog` + `useForm` con `zodResolver` + un `Textarea` en un `FormField` + `FormMessage` + `form.reset` al cerrar. Son `ResolveRequestDialog`, `SubmitRequestDialog`, `BlockPlayerDialog`, `RejectRegistrationDialog`, `JustifiedTableActionDialog`, `BlockUserDialog` y `RoleChangeDialog`.

**Y acá la conclusión es la opuesta, por el tercer punto de §3.1.2**: *«no sube lo que solo se parece. Dos formularios no comparten componente por ser dos formularios; comparten `FormDialog`, que es el envoltorio»*. Es exactamente este caso: **ya comparten lo que tenían que compartir**. Lo que queda distinto en cada uno es la mutación que dispara, qué hace al salir bien, y los campos que rodean al motivo —`RoleChangeDialog` elige un rol, `ApplyToTableDialog` tiene dos pasos y adjuntos, `BlockPlayerDialog` cambia de endpoint según si el lector es `Primary`—, y eso es lógica de la feature y no forma compartida.

Queda escrito igual, con los nombres, por dos razones. Una: que la próxima fase que agregue un diálogo con motivo sepa que hay siete precedentes y de cuál copiar la forma. Dos: que si el número sigue creciendo, la decisión se revise **con esta lista a la vista** en vez de volver a contarla desde cero. `BlockPlayerDialog` ya dejó la pregunta abierta por escrito en su propio JSDoc —*«no es `JustifiedTableActionDialog`, que es la forma idéntica una feature más allá»*— y lo que faltaba era el recuento que la contesta.

### Listas de trabajo — cómo se ve una tabla de admin

Lo que el usuario fijó en la revisión de F4 para toda tabla que se **trabaja** —las seis de `/admin`— y que vale para las que vengan (`/admin/moderation`, `/admin/feedback`, `/owner/audit`):

- **Las acciones de una fila son íconos, con tooltip** (#272). Cada una es un `IconAction`: el ícono en la última columna, el nombre de la acción en un tooltip al pasar el cursor y como `aria-label` —en un teléfono no hay hover—. **Van en una sola línea, sin wrap** (`.row-actions`, que pone `DataTable` y no la pantalla, #273): una segunda línea de íconos se lee como otra fila. Las destructivas llevan `text-destructive`. Botones con texto solo para lo que no es de una fila («Subir», «Crear mesa sin master»). Lo que la fila no permite **no aparece** (principio 2): ni gris ni deshabilitado.
- **El vocabulario de íconos se repite, no se inventa por pantalla**: aprobar/aceptar `Check` · rechazar `X` · borrar `Trash2` · detalle `Eye` · historial `History` · roles `UserCog` · bloquear `Ban` · desbloquear `LockOpen` · publicar `Globe` · despublicar `EyeOff` · asignar masters `UserPlus` · pausar `Pause` · reanudar `Play` · fusionar `Merge` · separar `Split` · restaurar `RotateCcw` · reservar/liberar `Bookmark`/`BookmarkX` · pedir cambios `MessageSquareWarning`. Una acción nueva que ya tiene su gemela en esta lista usa el mismo ícono.
- **La paginación llega a cualquier página en un paso** (#271): tira numerada con la primera, la última y las vecinas de la actual; «Ir a…» para el resto; y el selector «Por página» con 10 · 25 · 50 · 100, que vive en `?size=` como la página y el buscador (#185). Por debajo de `sm` la tira se esconde y quedan las flechas, «X de Y» y el salto.
- **A 375 px la tabla deja de ser tabla** (§5.b, `DataTable`): cada fila es una ficha y los íconos van al pie.

Dibujado en `design/out/components-data.html`.

### Hooks compartidos

En `hooks/`:

| Hook | Para qué |
|---|---|
| `useTableSelection` | Selección múltiple en tablas, con rango al mantener **Shift**. Se monta como Context **alrededor de la tabla que lo usa**, no global (#105). **Todavía no construido**: ninguna tabla pide selección múltiple por ahora |
| `useConfirm` | Confirmación imperativa: devuelve una promesa, para no encadenar estados de diálogo a mano. El hook y su Context están en `hooks/useConfirm.ts`; `components/ConfirmDialog.tsx` monta el provider y el diálogo (#243) |
| `useDebounce` | Filtros del explorador y buscadores |
| `useSearchQuery` | El cableado de un buscador (#240): estado de los chips, última consulta confirmada y escritura del `?q=`; busca con el segundo Enter (#268). Los comandos se le pasan; no los conoce |
| `useHasPersonalLibrary` | Si esta cuenta es de las que llenan biblioteca — `Player` o `Master` o `hasManagedTables` (#241) |
| `useAvailableContexts` | Qué contextos tiene la cuenta y en cuál está parada, resuelto desde la URL (#222) |
| `useUnsavedChanges` | Avisar antes de perder un formulario a medio llenar |
| `useLanguage` | El idioma elegido, recordado sin ida al servidor (#198) |
| `useBackendStatus` | Si el backend responde, para el indicador «En línea» |
| `useClientLimits` | Los límites de plataforma que una pantalla enuncia antes de que alguien los rompa: el tope por archivo y el timeout de la reserva de la bandeja (#141, #264). Están en `hooks/` y no en una feature porque `features/files`, `features/help` y `/admin/queue` los leen y `features/settings` los edita — y una feature nunca importa otra. Nunca suspende ni rompe una pantalla: contesta con los defaults de `config/limits.ts` mientras viaja el pedido |
| `useDisclosure<T>` | Abrir/cerrar modales y paneles, y guardar el ítem que los abrió (`open(row)`) — es lo que hacía `useModal` con su `dataModal` |

### Estados obligatorios

Toda pantalla que lea datos define los cuatro: **cargando** (`skeleton`, no spinner suelto), **vacío** (`EmptyState`), **error** (mensaje del `ProblemDetail`, con reintento) y **sin permiso** (`403` con explicación; el `404` por veto se ve como "no existe", que es intencional).

**No se especifican pantalla por pantalla** (#139). Hay **cuatro arquetipos** —listado, detalle, formulario y dashboard— y cada pantalla hereda los cuatro estados del suyo, porque el skeleton, el error y el 403 son idénticos entre pantallas del mismo tipo.

Lo único que cada pantalla define por su cuenta es **el texto del vacío y qué acción ofrece**. Está en `design/out/ui-states-copy.html`, y ahí hay una trampa que conviene no repetir: `/master` y `/admin/queue` vacíos son **buenas noticias** —"nada espera tu respuesta"— y no pueden leerse como una pantalla rota.

# 5.b Responsive

**Las 28 rutas funcionan en teléfono, tablet y escritorio** (#138). Puntos de corte de Tailwind, sin inventar ninguno, y se diseña **de menor a mayor**: las clases sin prefijo son las del teléfono.

| Prefijo | Ancho | Qué cambia |
|---|---|---|
| *(sin prefijo)* | ≥ 375 px | Una columna. Los filtros se van a un `sheet`. Los modales son *sheet* desde abajo |
| `md` | ≥ 768 px | Dos columnas. Los filtros vuelven a la barra |
| `lg` | ≥ 1024 px | Tres columnas. Barra lateral donde la haya |

**La regla de la tabla ancha**, que es el caso caro: `/admin/catalogs`, `/admin/files`, `/admin/users` y `/owner/audit` tienen cinco o más columnas. En móvil **dejan de ser tablas** — cada fila se vuelve una ficha con identidad y estado arriba, el resto como texto y la acción al pie. **Nunca scroll horizontal.**

Referencia visual en `design/out/responsive.html`.

