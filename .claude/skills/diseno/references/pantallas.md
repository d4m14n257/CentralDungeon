# 4 y 6 Pantallas clave y qué se rescata del frontend viejo

> Parte de la skill `diseno`. Hasta ADR-01 §5 eran `docs/frontend-diseno.md` §4 y §6, y conservan su numeración. **Los wireframes en ASCII de §4 no se trajeron**: habían quedado viejos —rutas anteriores a ADR-20 §2, los filtros que reemplazó el buscador (ADR-14 §6), la pestaña Estado que quitó ADR-21 §10— y lo que se dibuja de verdad son las previews de las pantallas, `design/out/screen-*.html`, en los dos temas, que se regeneran con `python3 design/build.py`. Siguen en git. Quedan las reglas que vivían debajo de cada uno, que son las que una pantalla tiene que cumplir.

## 4. Pantallas clave

Son las cinco que definieron el resto. **`/admin/files`** (ADR-14 §7) y **`/admin/catalogs/:kind/:id`** (ADR-12 §7) no tienen preview de pantalla: la primera se construyó directo sobre `DataTable`, con la forma de `/admin/catalogs`; de la segunda se dibuja el patrón, el lienzo de nodos, en `components-data.html`.

### Explorar mesas — `/player`

- Los comandos de catálogo (`/table_system`, `/table_tag`, `/table_platform`) resuelven **por grupo de sinónimos** (ADR-12 §4, §8): buscar «DANDD» trae también lo etiquetado «D&D».
- Las mesas donde el usuario tiene un veto **no aparecen** (ADR-09 §4), y su detalle por id responde `404`, no `403`: un `403` ya delataría que existe.
- Lista **solo las `Opened` y las `InProgress` que buscan jugadores**; estas llevan su chip «Busca jugadores» en la card (ADR-07 «Mesas: ciclo de vida» §11 «Una mesa en curso puede buscar jugadores, y no es un estado»).

### Detalle de mesa — `/player/tables/:id`

- «Postularme» cambia según el caso y **siempre dice por qué** (principio 2): *«Ya tienes una postulación en curso»* · *«La mesa está llena»* · *«No está abierta a postulaciones»* · *«Necesitas el rol Jugador»* (ADR-06 §3). El choque de horario se explica justo debajo (ADR-21 §5).
- **Quien fue rechazado lo ve** (ADR-09 «Postulaciones y veto» §7 «El jugador ve a qué mesas se postuló y por qué lo rechazaron»): si su última postulación es `Rejected`, un aviso «Te rechazaron en esta mesa» con el motivo y la fecha, y debajo **«Postularme de nuevo»**, con las mismas condiciones que la primera vez. Un rechazo automático por cupo se dice desde su código (`TABLE_FULL`), nunca con el motivo vacío.
- Las fechas se guardan en UTC y se muestran en la hora local del navegador (ADR-03 §6).

### Gestión de mesa — `/master/tables/:id`

- La cola de candidatos va **en orden de llegada y no se reordena** (ADR-09 §1).
- Aceptar al que completa el cupo dispara el rechazo automático del resto con la justificación por defecto (ADR-09 §3): la interfaz lo avisa **antes** de confirmar, no después.
- En una mesa `InProgress`, el aviso de estado lleva «Buscar jugadores» / «Dejar de buscar» para cualquier master, deshabilitado con la mesa llena (ADR-07 §11). El aviso dice qué pasa con la búsqueda en su segunda frase (`hint`): «Buscás jugadores», o por qué el botón está apagado, «La mesa está completa: no hay lugar para buscar jugadores» (ADR-21 «Diseño visual e interacción» §10 «Las decisiones sobre una entidad van en su aviso de estado, con las destructivas aparte»).
- En la pestaña Jugadores, «Pedir veto» es de cualquier master y «Levantar el veto», del `Primary`. Se veta solo a un jugador: la pestaña Candidatos no lo ofrece, porque al candidato que no se quiere se lo rechaza con motivo; en `/admin/tables/:id`, el admin lo levanta y aprueba o rechaza los pedidos pendientes de esa mesa (ADR-09 «Postulaciones y veto» §5 «El veto lo pide un master y lo decide un admin; lo levanta el `Primary` o un admin, con registro», §6 «El padrón de la mesa es un endpoint propio, separado de la cola de candidatos»).
- **Un admin que co-dirige la mesa, acá, es un master**: tiene lo de su fila en `masters` —levanta un veto solo si es el `Primary`— y ve los pedidos de veto pendientes sin botones para resolverlos, como cualquier master. Lo que hace como admin lo hace desde `/admin` (ADR-20 «Navegación, contextos y ayuda» §9 «Cada contexto muestra lo de su rol: un admin que co-dirige una mesa, en `/master`, actúa como master»).
- **La fila de un vetado dice las dos razones**: quién pidió el veto (un master) y su justificación, y debajo, quién lo aprobó (un admin) y su nota de resolución. Un veto sin pedido aprobado detrás muestra solo quién escribió el `Blocked` y su justificación (ADR-09 «Postulaciones y veto» §5). Vale igual en `/admin/tables/:id`, que monta la misma pestaña.

### Bandeja de admins — `/admin/queue`

- Al reservar, el ítem desaparece de la bandeja del resto en el momento, vía `queue.changed` (ADR-16 §5, ADR-18 §2).
- Una reserva sin resolver se libera sola —a los 15 minutos por defecto, configurable en `/admin/settings`— y el ítem reaparece para todos.

### Perfil — `/profile` y `/users/:id`

- **Es de la cuenta, no de un contexto** (ADR-20 «Navegación, contextos y ayuda» §7 «El perfil es de la cuenta y vive fuera de los contextos»): lo abre cualquier cuenta con sesión, tenga o no `Player`.
- **«Mesas terminadas» se abre** (ADR-10 «Perfiles» §7 «Las mesas terminadas de un perfil se pueden abrir»): en el propio lleva a `/player/history`; en el ajeno, a un listado de las suyas con lo público de cada mesa, y cada fila al detalle público.
- **Los comentarios no muestran autor. Nunca, para nadie** (ADR-11 §5): ni al dueño del perfil, ni al master, ni a un admin, ni al owner. Lo único visible es la dirección (jugador → jugador, master → jugador…) y el impacto.
- La asistencia va al lado del karma —que llega con F5 (ADR-10 §5)— pero **no está incluida en él** (ADR-11 §10): son dos señales distintas, y mezclarlas haría el número inexplicable.
- Al mirar el perfil de otra persona, la visibilidad caduca a las dos semanas del cierre de la mesa que los vinculó (ADR-10 §2).

## 6. Qué se rescata del frontend viejo

`legacy/frontend-next/` es **JavaScript con MUI**, sin TypeScript (`jsconfig.json`, archivos `.js`/`.jsx`). **No se rescata código.** Lo que aporta es la forma de los flujos, ya validada con usuarios reales.

| Pantalla legacy | Qué pasa con ella |
|---|---|
| `index`, `tables/index`, `public-tables`, `first-class-tables` | Se **fusionan** en `/` con filtros. Eran **cuatro** listados casi iguales que solo se distinguían por un filtro fijo — la duplicación más grande del frontend viejo |
| `tables/[id]`, `tables/available/[id]`, `tables/joined/[id]` | Se **fusionan** en `/player/tables/:id`. La vista cambia según la relación del usuario con la mesa, no la URL |
| `joined-tables` | → `/my/tables` |
| `master/index`, `master/requests` | → `/master/tables` y la pestaña de candidatos |
| `player-requests` | → `/my/applications` |
| `users/index`, `users/[id]` | → `/admin/users` y `/users/:id`; eran la misma pantalla haciendo dos trabajos |
| `comments` | → `/admin/moderation`. El flujo cambia por completo: ahora hay borradores, anonimato y moderación (ADR-11 §3, §7) |
| `login` | → `/login`, ahora con el paso de invitación al servidor (ADR-05 §1) |
| `_app` | No es pantalla: es el cableado global. → `providers/` y `layouts/` |

Las 15 páginas del legacy quedan cubiertas: 4 se fusionan en el explorador, 3 en el detalle de mesa, 7 tienen destino propio y `_app` pasa a ser cableado.

| Componente legacy | Equivalente |
|---|---|
| `ModalBase` | `FormDialog` en `components/`. Se conserva su mejor idea —preguntar antes de cerrar un modal a medio llenar— como la prop `confirmOnDirtyClose` (ADR-19 §6) |
| `DialogConfirmed` | `ConfirmDialog` |
| `SnackMessage` | `sonner` |
| `TableComponent`, `ListComponent` | `DataTable`. El borrado que `TableComponent` hacía adentro llamando a `deleter` sale: la tabla emite la acción, la mutación es de quien la monta |
| `CardComponent`, `ViewMoreComponent` | `CollapsibleSection` |
| `ActionButtonDefault` | `IconAction`. Los `ActionButtonTable`, `ActionButtonMaster` y `ActionButtonUser` que lo envolvían quedan en su feature: son la lista de acciones de ese dominio, no un componente compartido |
| `HandlerError`, `HandlerMessage` | `ErrorState` y `EmptyState`. El patrón `Error.When` / `Error.Else` no vuelve: con TanStack Query el `isPending` / `isError` se lee directo en la página |
| `CardBodyTable`, `CardContentTable` | `GameTableCard` en `features/tables/` |
| `ListScheduleTable`, `EditModalScheduleTable` | `ScheduleEditor` |
| `ListFilesTable`, `UploadButton` | `FilePicker` |
| `ListCataloguesTable` | Combobox de catálogo con resolución por grupo |
| `PreparationStatus` | `TableStatusBadge`, generalizado a los nueve estados |
| `CardSettings` | `UserMenu` en `layouts/components/`, sobre `dropdown-menu`. El click-fuera escrito a mano con `window.addEventListener` desaparece: lo resuelve Radix |
| `MenuItemComponent` | `ContextSwitcher`. Su cadena de ternarios para elegir el icono según el nombre del rol se vuelve un `Record<Role, LucideIcon>` |
| `Span`, `forms/TextArea` | Desaparecen: eran estilo. `textarea` de shadcn/ui y clases de Tailwind |
| `@tinymce/tinymce-react` | **TipTap** (`@tiptap/react` + `starter-kit`), elegido en F1.2: TinyMCE necesita API key para uso alojado. La barra ofrece exactamente lo que la lista blanca del backend conserva (ADR-05 §6) |

| Hook o utilidad legacy | Equivalente |
|---|---|
| `useModal` | `useDisclosure<T>` en `hooks/`, que además guarda el ítem que abrió el modal |
| `useMenu` | Desaparece: el `dropdown-menu` de shadcn/ui ya maneja anclaje y apertura |
| `useDate` | `lib/date.ts`, con el locale y la zona como parámetros en vez del `'es-ES'` fijo que tenía (ADR-19 §7) |
| `constants/constants.js` | Se parte en tres: las rutas a `config/paths.ts`; los días de la semana derivados del tipo del dominio —el arreglo escrito a mano tenía el orden mal (`Monday, Thursday, Wednesday, Tuesday…`)—; y las zonas horarias desde `Intl.supportedValuesOf('timeZone')` en vez de 27 strings a mano, que traían el cero dos veces (`UTC-00:00` y `UTC+00:00`) y ninguna zona de media hora |
| `api/getFlagCountry.js` | Sin equivalente. La bandera se resolvía llamando a un servicio externo por cada fila; el país del perfil se muestra como texto, y si más adelante se quiere bandera, es un emoji derivado del código ISO, sin red |
| `helper/getLanguage.js`, `language/*.xml` | `locales/es/<espacio>.json` con `i18next`, desde la primera etapa (ADR-23 §1). El helper propio y los XML vacíos no vuelven |

**No se rescata nada de**: los siete wrappers de API (`getter`, `setter`, `patcher`, `putter`, `deleter`, `setterFiles`) → un solo `client.ts`; los cinco contexts con datos de servidor dentro → TanStack Query (regla dura 11); `next-auth`, instalado y nunca usado; el i18n a medias (`english.xml`, `spanish.xml`); y los cuatro motores de estilo conviviendo (MUI, Emotion, styled-components, `@mui/styled-engine-sc`) → Tailwind.
