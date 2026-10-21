# 4 y 6 Pantallas clave y qué se rescata del frontend viejo

> Parte de la skill `diseno`. Hasta #292 eran `diseno` §4 y §6, y conservan su numeración. **Los wireframes en ASCII de §4 no se trajeron**: habían quedado viejos —rutas anteriores a #222, los filtros que reemplazó el buscador (#242), la pestaña Estado que quitó #287— y lo que se dibuja de verdad son las previews de las pantallas, `design/out/screen-*.html`, en los dos temas, que se regeneran con `python3 design/build.py`. Siguen en git. Quedan las reglas que vivían debajo de cada uno, que son las que una pantalla tiene que cumplir.

## 4. Pantallas clave

Son las cinco que definieron el resto. **`/admin/files`** (#207) y **`/admin/catalogs/:kind/:id`** (#275) no tienen preview de pantalla: la primera se construyó directo sobre `DataTable`, con la forma de `/admin/catalogs`; de la segunda se dibuja el patrón, el lienzo de nodos, en `components-data.html`.

### Explorar mesas — `/player`

- Los comandos de catálogo (`/table_system`, `/table_tag`, `/table_platform`) resuelven **por grupo de sinónimos** (#56, #246): buscar «DANDD» trae también lo etiquetado «D&D».
- Las mesas donde el usuario tiene un veto **no aparecen** (#29), y su detalle por id responde `404`, no `403`: un `403` ya delataría que existe.

### Detalle de mesa — `/player/tables/:id`

- «Postularme» cambia según el caso y **siempre dice por qué** (principio 2): *«Ya tenés una postulación en curso»* · *«La mesa está llena»* · *«No está abierta a postulaciones»* · *«Necesitás el rol Jugador»* (#73). El choque de horario se explica justo debajo (#281).
- Las fechas se guardan en UTC y se muestran en la hora local del navegador (#22).

### Gestión de mesa — `/master/tables/:id`

- La cola de candidatos va **en orden de llegada y no se reordena** (#28).
- Aceptar al que completa el cupo dispara el rechazo automático del resto con la justificación por defecto (#34): la interfaz lo avisa **antes** de confirmar, no después.

### Bandeja de admins — `/admin/queue`

- Al reservar, el ítem desaparece de la bandeja del resto en el momento, vía `admin-queue.changed` (#100, #101).
- Una reserva sin resolver se libera sola —a los 15 minutos por defecto, configurable en `/admin/settings`— y el ítem reaparece para todos.

### Perfil — `/player/profile` y `/player/users/:id`

- **Los comentarios no muestran autor. Nunca, para nadie** (#43): ni al dueño del perfil, ni al master, ni a un admin, ni al owner. Lo único visible es la dirección (jugador → jugador, master → jugador…) y el impacto.
- La asistencia va al lado del karma pero **no está incluida en él** (#98): son dos señales distintas, y mezclarlas haría el número inexplicable.
- Al mirar el perfil de otra persona, la visibilidad caduca a las dos semanas del cierre de la mesa que los vinculó (#44).

## 6. Qué se rescata del frontend viejo

`legacy/frontend-next/` es **JavaScript con MUI**, sin TypeScript (`jsconfig.json`, archivos `.js`/`.jsx`). **No se rescata código.** Lo que aporta es la forma de los flujos, ya validada con usuarios reales.

| Pantalla legacy | Qué pasa con ella |
|---|---|
| `index`, `tables/index`, `public-tables`, `first-class-tables` | Se **fusionan** en `/` con filtros. Eran **cuatro** listados casi iguales que solo se distinguían por un filtro fijo — la duplicación más grande del frontend viejo |
| `tables/[id]`, `tables/available/[id]`, `tables/joined/[id]` | Se **fusionan** en `/tables/:id`. La vista cambia según la relación del usuario con la mesa, no la URL |
| `joined-tables` | → `/my/tables` |
| `master/index`, `master/requests` | → `/master/tables` y la pestaña de candidatos |
| `player-requests` | → `/my/applications` |
| `users/index`, `users/[id]` | → `/admin/users` y `/users/:id`; eran la misma pantalla haciendo dos trabajos |
| `comments` | → `/admin/moderation`. El flujo cambia por completo: ahora hay borradores, anonimato y moderación (#48, #51) |
| `login` | → `/login`, ahora con el paso de invitación al servidor (#38) |
| `_app` | No es pantalla: es el cableado global. → `providers/` y `layouts/` |

Las 15 páginas del legacy quedan cubiertas: 4 se fusionan en el explorador, 3 en el detalle de mesa, 7 tienen destino propio y `_app` pasa a ser cableado.

| Componente legacy | Equivalente |
|---|---|
| `ModalBase` | `FormDialog` en `components/`. Se conserva su mejor idea —preguntar antes de cerrar un modal a medio llenar— como la prop `confirmOnDirtyClose` (#110) |
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
| `@tinymce/tinymce-react` | **TipTap** (`@tiptap/react` + `starter-kit`), elegido en F1.2: TinyMCE necesita API key para uso alojado. La barra ofrece exactamente lo que la lista blanca del backend conserva (#186) |

| Hook o utilidad legacy | Equivalente |
|---|---|
| `useModal` | `useDisclosure<T>` en `hooks/`, que además guarda el ítem que abrió el modal |
| `useMenu` | Desaparece: el `dropdown-menu` de shadcn/ui ya maneja anclaje y apertura |
| `useDate` | `lib/date.ts`, con el locale y la zona como parámetros en vez del `'es-ES'` fijo que tenía (#111) |
| `constants/constants.js` | Se parte en tres: las rutas a `config/paths.ts`; los días de la semana derivados del tipo del dominio —el arreglo escrito a mano tenía el orden mal (`Monday, Thursday, Wednesday, Tuesday…`)—; y las zonas horarias desde `Intl.supportedValuesOf('timeZone')` en vez de 27 strings a mano, que traían el cero dos veces (`UTC-00:00` y `UTC+00:00`) y ninguna zona de media hora |
| `api/getFlagCountry.js` | Sin equivalente. La bandera se resolvía llamando a un servicio externo por cada fila; el país del perfil se muestra como texto, y si más adelante se quiere bandera, es un emoji derivado del código ISO, sin red |
| `helper/getLanguage.js`, `language/*.xml` | `locales/es/<espacio>.json` con `i18next`, desde la primera etapa (#107, #117). El helper propio y los XML vacíos no vuelven |

**No se rescata nada de**: los siete wrappers de API (`getter`, `setter`, `patcher`, `putter`, `deleter`, `setterFiles`) → un solo `client.ts`; los cinco contexts con datos de servidor dentro → TanStack Query (regla dura 11); `next-auth`, instalado y nunca usado; el i18n a medias (`english.xml`, `spanish.xml`); y los cuatro motores de estilo conviviendo (MUI, Emotion, styled-components, `@mui/styled-engine-sc`) → Tailwind.
