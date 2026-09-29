# 5.c Clases y componentes de patrón

> Parte de la skill `diseno` (#273). Es la sección que faltaba: hasta F4 cada pantalla escribía sus clases a mano, y cambiar cómo se ve una cabecera obligaba a encontrar las veinticinco copias.

## La regla

**Una combinación de utilidades que cumple un papel de diseño tiene nombre y se define una sola vez.** Una pantalla no escribe `font-serif text-2xl font-semibold`: escribe `page-title`. Así, cambiar el papel cambia todas las pantallas que lo tienen, que es lo que uno espera al tocarlo.

¿Qué es un papel? Algo que se puede nombrar por lo que **es**, no por cómo **se ve**: «título de página», «acciones de una fila», «error dentro de un formulario». `text-fg-muted text-sm` **no es un papel**, es un color y un tamaño, y sigue escrito como utilidad.

### Clase o componente

| Si el patrón… | Es… | Dónde |
|---|---|---|
| es **solo estilo** sobre un elemento | una **clase** | `frontend/src/styles/base.css`, dentro de `@layer components`, con `@apply` sobre los tokens del `@theme` |
| tiene **estructura** (varios elementos), **props** o **comportamiento** | un **componente** | `frontend/src/components/` si no tiene dominio; en su feature si lo tiene (skill `arquitectura` §3.1.2) |

- Las clases van en `base.css` y **no** en `globals.css`, porque `globals.css` se sobrescribe entero cada vez que se transcribe el tema (#118, #130). `base.css` abre con `@reference "./globals.css"`, que es lo que le permite a `@apply` ver los tokens sin volver a emitir el tema.
- Van en `@layer components` a propósito: la capa de utilidades viene después, así que **una utilidad escrita al lado gana** (`className="section-title leading-6"`). Un ajuste puntual sigue siendo puntual y no obliga a crear una variante.
- **Un componente de patrón usa las clases de patrón**: `PageHeader` pinta su `h1` con `.page-title`, y `DataTable` sus acciones con `.row-actions`. No hay dos definiciones del mismo papel.

### Cuándo se crea uno

- **A la segunda vez que el mismo papel aparece con las mismas clases**, y no a la décima. Si la segunda copia difiere en un valor, se decide ahí cuál de los dos es el correcto; no se crean dos variantes.
- Se crea en el mismo commit que la segunda pantalla que lo necesita, y **esa pantalla y la primera pasan a usarlo**.
- Se agrega a la tabla de abajo, con el papel que cumple, y se dibuja en `design/build.py` (`components-data.html`, sección «Clases de patrón»).
- Un nombre de clase está en inglés (es código), en kebab-case, y **dice el papel, nunca el aspecto**: `section-label`, no `small-caps-gray`.

## El catálogo

### Clases (`styles/base.css`)

| Clase | Papel | Qué aplica |
|---|---|---|
| `.page-title` | El `h1` de una pantalla | `font-serif text-2xl font-semibold` |
| `.section-title` | El título de un bloque dentro de una pantalla: una sección de formulario, una card, el resumen de un paso | `font-serif text-lg font-semibold` |
| `.section-label` | La etiqueta chica en mayúsculas sobre un grupo: una sección, un campo de un detalle, una columna de una ficha | `text-fg-subtle text-xs font-medium tracking-wide uppercase` |
| `.row-actions` | Las acciones con ícono de una fila o una ficha (#272): **una sola línea, alineada a la derecha, sin wrap** | `flex flex-row flex-nowrap items-center justify-end gap-1` |
| `.list-divided` | Una lista enmarcada con una línea entre filas | `divide-border divide-y rounded-lg border` |
| `.list-divided-bare` | Las mismas filas separadas, sin marco: para una lista que ya está dentro de una card o un diálogo | `divide-border divide-y` |
| `.inline-error` | Un error dicho dentro de un formulario o un diálogo, al lado de lo que lo causó. No es un toast | `bg-state-canceled-bg text-state-canceled-fg rounded-md px-3 py-2 text-sm` |
| `.bg-brand-gradient` | El gradiente de la comunidad (#132), solo en `/login` | `background-image: var(--gradient-brand)` |
| `.graph-canvas` | El marco de un lienzo de nodos (#275). **Además apunta cada variable `--xy-*` de React Flow a un token**, así el lienzo sigue a los dos temas y no trae un color propio. La pone `GraphCanvas` | `bg-canvas border-border h-160 w-full overflow-hidden rounded-lg border` + las `--xy-*` |
| `.graph-tray` | El panel lateral de un lienzo. La pone `GraphTray` | `bg-surface border-border space-y-4 self-start rounded-lg border p-4` |
| `.graph-node` | Un nodo del lienzo. La pone `GraphNode` | `bg-surface text-fg border-border-strong flex w-56 items-center gap-2 rounded-lg border px-3 py-2 shadow-sm` |
| `.graph-node-head` · `.graph-node-floating` · `.graph-node-selected` | El papel del nodo, que cambia el marco y nunca el color: cabeza de la estrella, suelto esperando conexión, seleccionado | `border-brand-500 border-2` · `border-dashed` · `ring-ring ring-2` |

### Componentes (`components/`)

| Componente | Papel | Lo usan |
|---|---|---|
| `PageHeader` | La cabecera de una pantalla: `title`, `description` opcional y `actions` enfrente (la ayuda, el botón principal) | Toda pantalla cuya cabecera tiene esa forma. Las de detalle —una mesa, un perfil— usan `.page-title` sola |
| `DataTable` | La tabla que en móvil se vuelve fichas (§5.b). **Pone `.row-actions` ella misma**: `renderActions` devuelve solo el fragmento de `IconAction` | Las seis tablas de admin |
| `CollapsibleSection`, `FileCard` | Sus ranuras `actions` también se pintan con `.row-actions` | Pestañas del master, bibliotecas de archivos |
| `IconAction` | Una acción de ícono con su tooltip y su `aria-label` | Toda acción de fila o de ficha |
| `PaginationControls` | La paginación de una lista de trabajo (#271) | Las seis tablas de admin y `/my/files` |
| `SectionNav` | La navegación de secciones de un contexto (#277): escrita tres veces igual, una por contexto, hasta que se nombró | `PlayerSectionNav`, `MasterSectionNav`, `AdminSectionNav` |
| `GraphCanvas`, `GraphNode`, `GraphTray` | El lienzo de nodos y sus piezas (#275): ponen `.graph-canvas`, `.graph-node*` y `.graph-tray` ellos mismos | `/admin/catalogs/:kind/:id` |

## Deuda conocida

- El handle de React Flow mide 6 px y lo dibuja su propia hoja **sin capa**, que le gana a todo `@layer`. Por eso su tamaño (`calc(var(--spacing) * 3)`) es una regla sin capa en `base.css`, junto a las correcciones de shadcn: no es deuda, pero es la única excepción del lienzo a «todo en `@layer components`».

- `NotificationBell` y `WizardSteps` usan `text-[11px]`, un valor suelto fuera del `@theme` (regla dura 18). Está anotado en `fase-4-revision.md` §2.3 para triar en F4.1.
