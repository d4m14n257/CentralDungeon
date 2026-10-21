# 3.1 Patrón: features de dominio, con capas transversales en la raíz

> Parte de la skill `arquitectura` (#274). Se movió desde `docs/` en F4.0 y conserva su numeración original, que es la que citan el código y las ADRs.


El dominio manda, igual que en el backend. Una feature es autocontenida —sus llamadas a la API, sus hooks, sus componentes, sus tipos— y **nunca importa de otra feature**: solo depende de las capas transversales de la raíz de `src/`.

Las **pantallas viven fuera de las features**, en `routes/`. Es lo que hace que esa regla no tenga excepciones (§3.1.5).

```
frontend/
├── index.html
├── vite.config.ts                   incluye el plugin @tailwindcss/vite
├── components.json                  config de shadcn/ui (aliases por defecto)
├── tsconfig.json                    tsconfig de solución ("files": []): el typecheck real es `npx tsc -b`
├── e2e/                             specs de Playwright y sus helpers (acceptReview, test-login)
└── src/
    ├── main.tsx                     bootstrap: providers + RouterProvider
    ├── styles/
    │   ├── globals.css              @import "tailwindcss" + @theme, transcripto de design/build.py (#130)
    │   └── base.css                 las clases de patrón (`diseno` §5.c, #273)
    ├── assets/                      imágenes, íconos propios, fuentes
    │
    ├── config/                      configuración de la app, sin lógica
    │   ├── paths.ts                 builders de URL para los enlaces (§3.1.6)
    │   ├── env.ts                   variables de entorno, leídas y validadas en un solo lugar
    │   ├── query.ts                 defaults del QueryClient, política de staleTime y el único polling (§3.3)
    │   ├── pagination.ts            tamaños de página (#271)   limits.ts   los topes que se dicen antes (#264)
    │   ├── language.ts              idiomas soportados (#198)  zodErrors.ts  mensajes de zod por clave i18n
    │
    ├── routes/                      TODAS las pantallas + el árbol de rutas (§3.1.6)
    │   ├── router.tsx               createBrowserRouter: URLs, layouts anidados y lazy
    │   ├── RootRedirect.tsx         `/`: no es pantalla, despacha a la home del contexto (#222)
    │   ├── LoginPage.tsx  OAuthCallbackPage.tsx  OnboardingPage.tsx  NotificationsPage.tsx  NotFoundPage.tsx
    │   ├── player/                  /player/*
    │   ├── master/                  /master/*  (las pestañas de una mesa son …Tab.tsx)
    │   ├── admin/                   /admin/*
    │   └── my/                      /my/* — transversales, de ningún contexto
    │
    ├── layouts/                     cáscaras de página, montadas por el router
    │   ├── RootLayout.tsx           sesión y guard de autenticación
    │   ├── PublicLayout.tsx         la tarjeta centrada de callback y onboarding
    │   ├── ShellLayout.tsx          el shell sin nav de sección, para las transversales
    │   ├── PlayerLayout.tsx  MasterLayout.tsx  AdminLayout.tsx   cada uno cerrado con RequireContext (#269)
    │   └── components/              AppHeader, ContextSwitcher, UserMenu, BrandMark, RequireContext,
    │                                PlayerSectionNav, MasterSectionNav, AdminSectionNav
    │
    ├── providers/                   QueryProvider, ThemeProvider, AuthProvider, I18nProvider (+ i18n.ts)
    ├── stores/                      Zustand global: contextStore (contexto de rol de respaldo, #222)
    │
    ├── features/                    los dominios. Sin páginas adentro
    │   ├── tables/                  el ejemplo de la forma completa:
    │   │   ├── api/
    │   │   │   ├── gameTablesApi.ts   sessionsApi.ts    las llamadas, sobre api/client
    │   │   │   ├── useGameTables.ts   useGameTable.ts   queries
    │   │   │   └── useCreateTable.ts  useUpdateTable.ts …  mutations, una por archivo
    │   │   ├── components/          plano, con sufijo (§3.1.3): GameTableCard, TableStatusBadge,
    │   │   │                        ScheduleEditor, SessionList, AttendanceEditor,
    │   │   │                        JustifiedTableActionDialog, WeeklyScheduleGrid…
    │   │   ├── lib/                 utilidades puras del dominio (tableTypeLabel.ts)
    │   │   ├── schemas.ts           esquemas zod de los formularios
    │   │   ├── searchFields.ts      los campos del buscador (#164)   tableErrors.ts   códigos → claves i18n (#197)
    │   │   ├── lifecycle.ts         qué acción admite cada estado de la mesa
    │   │   ├── types.ts             los tipos del dominio (§3.2)
    │   │   └── index.ts             la superficie pública de la feature (§3.1.3)
    │   ├── adminQueue/  approvals/  auth/  catalogs/  comments/  feedback/  files/
    │   └── help/  notifications/  registrations/  settings/  tasks/  users/
    │
    ├── components/                  sin dominio, para toda la app
    │   ├── ui/                      primitivas shadcn/ui generadas (button.tsx, dialog.tsx…)
    │   ├── graph/                   el lienzo de nodos sobre React Flow (`diseno` §5, #275)
    │   ├── dev/                     DevPanel, solo en desarrollo (#158)
    │   └── …                        compuestos propios: PageHeader, SectionHeader, DataTable,
    │                                FormDialog, ConfirmDialog, StatusBadge, StatusNotice, IconAction,
    │                                EmptyState, ErrorState, ForbiddenState, SearchQueryInput,
    │                                LoadMore, PaginationControls… (inventario: `diseno` §5)
    ├── hooks/                       useDisclosure, useDebounce, useUnsavedChanges, useLanguage,
    │                                useBackendStatus, useAvailableContexts (#222),
    │                                useSearchQuery (#240), useHasPersonalLibrary (#241),
    │                                useClientLimits (#264), useConfirm (#243) — su Context vive
    │                                acá con el hook y components/ConfirmDialog.tsx solo exporta el provider
    ├── lib/
    │   ├── utils.ts                 cn() — la ruta que shadcn/ui espera por defecto
    │   ├── date.ts                  formateo de fechas y horas con Intl (§3.3)
    │   ├── relativeTime.ts          «hace 2 h»   countries.ts   países del onboarding
    │   └── searchQuery.ts           el lenguaje de los buscadores (#164) — espejo de common/search/
    ├── api/
    │   ├── client.ts                fetch tipado: base URL, JWT, parseo de ProblemDetail
    │   └── queryKeys.ts             fábrica central de query keys
    ├── types/
    │   ├── api.ts                   PageResponse<T>, ProblemDetail, ApiError
    │   ├── catalog.ts  file.ts      los tipos que comparte más de una feature
    │   └── utils.ts                 helpers de tipos propios: StrictOmit, Expect, Equals (§3.2)
    └── locales/
        ├── es/                      un JSON por espacio de nombres (§3.3)
        └── en/                      las mismas claves, en el mismo commit (#198)
```

Dos notas sobre `components/`: las primitivas de shadcn/ui caen en `components/ui/` porque es el alias que su CLI usa por defecto, y `lib/utils.ts` es donde espera encontrar `cn()` — respetar ambos evita reconfigurar el generador en cada componente nuevo.

#### 3.1.1 Dónde va cada archivo

Cuatro preguntas, en orden. La primera que aplique decide:

1. **¿Es una pantalla?** → `routes/`, nunca dentro de una feature.
2. **¿Es una llamada a la API o un hook de TanStack Query?** → `features/<dominio>/api/`.
3. **¿Menciona un concepto del dominio?** Si el nombre del archivo o sus props nombran una mesa, una postulación, un comentario o un archivo → **vive en su feature**, aunque parezca reutilizable. `TableStatusBadge` es de `tables`.
4. **Si no menciona ningún dominio** (un diálogo genérico, una tabla paginada, `cn()`) → la capa transversal que corresponda: `components/`, `hooks/`, `lib/`, `types/`.

Por defecto **todo nace dentro de una feature**. Las capas transversales no se llenan por anticipación.

#### 3.1.2 Cuándo algo sube a la raíz

Una feature **nunca importa de otra feature**. Esa es la regla que fuerza el criterio: si dos features necesitan la misma pieza, la única salida legítima es subirla.

- **Sube cuando una segunda feature ya la necesita** — dos usos reales, no dos usos previstos. Es un umbral más bajo que el del backend (§2.4, tres repeticiones) justamente porque acá la alternativa a subir no es un poco de duplicación: es un import prohibido.
- **Al subir se le quita el dominio.** Si `EditGameTableDialog` y `EditCommentDialog` comparten forma, lo que sube a `components/` es `FormDialog`, sin saber de mesas ni de comentarios. Un componente de `components/` que reciba un `GameTable` está mal ubicado.
- **No sube lo que solo se parece.** Dos formularios no comparten componente por ser dos formularios; comparten `FormDialog`, que es el envoltorio.

#### 3.1.3 Nombres, sufijos y la superficie pública

`features/<dominio>/components/` es **plano**. El sufijo dice qué es cada archivo, y el inventario completo de la feature se lee de un vistazo:

| Sufijo | Qué es |
|---|---|
| `Page` | Pantalla montada por el router. Solo en `routes/` |
| `Form` | Formulario puro: recibe valores iniciales y `onSubmit` (§3.3) |
| `Dialog` | Envoltorio que aloja un formulario o una acción en un modal |
| `Section` | Bloque de una pantalla compuesta: trae sus propios datos a partir de un id (§3.1.5) |
| `Card` | Ficha de una entidad en un listado |
| `Badge` | Etiqueta de estado |
| `List` / `Table` | Listado de entidades |
| `Editor` | Control compuesto de edición (`ScheduleEditor`, `RichTextEditor`) |
| `Provider` | Componente que monta un Context |
| `use…` | Hook |

Se admite **una** subcarpeta dentro de `components/` solo cuando la feature pasa de una docena de archivos y hay un subconjunto que se lee como un bloque. Nunca una carpeta con uno o dos archivos: eso era lo que hacía `components/tables/status/` en el legacy con un único componente adentro.

**Cada feature declara su superficie pública en `index.ts`** (#114). Desde afuera se importa `@/features/tables`, nunca una ruta interna; lo que no está exportado ahí es privado de la feature:

```ts
// features/tables/index.ts
export { GameTableCard } from './components/GameTableCard';
export { TableSchedulesSection } from './components/TableSchedulesSection';
export type { GameTable, GameTableSummary } from './types';
```

Es el **único** barrel del proyecto: dentro de una feature, y en las capas transversales, se importa la ruta completa. Un `index.ts` por carpeta reintroduce ciclos de importación y no aporta ninguna frontera.

Archivos: componentes y páginas en `PascalCase.tsx`, hooks en `useCamelCase.ts`, el resto en `camelCase.ts`.

#### 3.1.4 Carpetas que no existen

El frontend viejo agrupaba **por tipo de archivo** en la raíz de `src/`, mezclando dominios dentro de cada una. La raíz de ahora también tiene carpetas por tipo, pero solo para lo que **no tiene dominio**; todo lo que menciona una mesa o una postulación vive en su feature. Cada carpeta del legacy tiene un destino fijo:

| Legacy | Dónde va ahora | Por qué |
|---|---|---|
| `src/forms/` | `features/<dominio>/components/` | Mezclaba cinco dominios en una sola carpeta (#106) |
| `src/contexts/` | `providers/` si es global; junto al componente que envuelve si es de subárbol | Un Context de una tabla no es global (#105) |
| `src/api/` con un archivo por verbo | `api/client.ts` + `features/<dominio>/api/` | Eran siete wrappers sin tipos (#104) |
| `src/constants/` | `config/paths.ts` las rutas; `features/<dominio>/constants.ts` los labels del dominio | Un archivo global de constantes termina siendo el cajón de todo |
| `src/normalize/` | No existe (#108) | El tipo base es el contrato (§3.2) |
| `src/styles/*.js` | Clases de Tailwind en el JSX (#109) | — |
| `src/helper/` | `lib/` si es puro y sin dominio; si no, su feature | "helper" no dice nada sobre qué hay adentro |
| `src/language/*.xml` | `locales/es/*.json` con i18next (#107, #117) | — |
| `pages/` de Next.js | `routes/` (§3.1.6) | La carpeta ya no define la URL; el router la declara |

Los tests van **junto al archivo que prueban** (`GameTableForm.test.tsx`), no en un `__tests__/` aparte. Los de Playwright son la excepción: viven en `e2e/`.

#### 3.1.5 Una feature no es una pantalla

Una feature es un **dominio**, y la relación con las pantallas no es uno a uno en ninguna dirección: las mesas ocupan dos pantallas (listado y detalle), y la pantalla de detalle necesita seis dominios —mesa, horarios, archivos, catálogos, masters y postulaciones—. En el legacy eso era `PreparationStatus`, un componente de ~300 líneas que importaba de todos lados y manejaba seis modales a la vez.

Por eso las páginas viven en `routes/` y no dentro de una feature: **componer dominios es trabajo de la pantalla**, y sacarlas afuera es lo que permite que "una feature nunca importa de otra" no tenga asteriscos.

**Lo que la página le pasa a cada bloque es un identificador, no una entidad.** Cada bloque es un componente `…Section` que vive en **su** feature, lanza **su** propia query y monta **sus** propios diálogos:

```tsx
// routes/player/MyTableDetailPage.tsx — compone; su query propia es la mesa
const { data: table } = useGameTable(tableId)              // features/tables
<TableStatusBadge status={table.status} />                 // features/tables
<TableTasksSection                                          // features/tasks: su query, sus diálogos
  tableId={tableId}
  renderFiles={(files) => <FileList files={files} />}      // features/files, inyectado por la pantalla
  renderFilePicker={(onPick) => <FilePicker onPick={onPick} … />}
  renderHelp={() => <HelpButton section="players.tasks" />} // features/help
/>
```

`TableTasksSection` recibe un `tableId` y nada más: no conoce el tipo `GameTable`, así que `features/tasks` no depende de `features/tables`. **Y cuando un bloque necesita pintar algo de otro dominio** —los archivos de una entrega, el selector de archivos, la ayuda—, no lo importa: lo recibe como *render prop* que la pantalla llena (`renderFiles`, `renderFilePicker`, `renderHelp`). Es la segunda forma que tiene una dependencia entre features de caber en una prop. Toda la dependencia entre dos features cabe en una prop. Si un `…Section` necesitara la entidad entera, es señal de que el bloque está en la feature equivocada.

La página tampoco concentra el estado de los diálogos: cada `…Section` tiene el suyo. Los seis `useModal` en paralelo del legacy eran el síntoma de que un solo componente estaba haciendo el trabajo de seis.

#### 3.1.6 Ruteo

React Router **no tiene convención de archivos**: la URL no sale de dónde está el archivo, sale de un árbol de objetos declarado a mano. Es la diferencia de fondo con Next.js, donde `pages/tables/[id].js` *era* `/tables/:id`. Por eso el router es una lista central y no algo distribuido por feature: una ruta dentro de `features/` no se registraría sola.

```tsx
// routes/router.tsx (abreviado)
export const router = createBrowserRouter([
  {
    Component: RootLayout,                    // sesión y guard de autenticación
    children: [
      { path: '/login', lazy: () => import('./LoginPage') },   // pantalla completa, sin layout
      {
        Component: PublicLayout,              // sin path: solo agrupa
        children: [
          { path: '/auth/callback', lazy: () => import('./OAuthCallbackPage') },
          { path: '/onboarding',    lazy: () => import('./OnboardingPage') },
        ],
      },
      { index: true, lazy: () => import('./RootRedirect') },  // `/` despacha al contexto (#222)
      {
        path: 'player',                       // cada contexto es dueño de su prefijo (#222)
        Component: PlayerLayout,
        children: [
          { index: true,        lazy: () => import('./player/TableListPage') },
          { path: 'tables/:id', lazy: () => import('./player/TableDetailPage') },
          // …el resto del contexto Jugador
        ],
      },
      { Component: ShellLayout, children: [ /* notifications, my/schedule, my/files */ ] },
      { path: 'master', Component: MasterLayout, children: [ /* … */ ] },
      { path: 'admin',  Component: AdminLayout,  children: [ /* … */ ] },
      { path: '*', lazy: () => import('./NotFoundPage') },
    ],
  },
])
```

Cinco reglas:

1. **El árbol espeja el sitemap** de `diseno` §2. Si dejan de parecerse, uno de los dos está desactualizado.
2. **Los paths salen de `config/paths.ts`**, nunca strings sueltos repartidos entre el router y los `<Link>`.
3. **Anidar es cómo se comparte un layout.** Una ruta sin `path` agrupa; una con `path` además prefija a sus hijas. Las hijas se pintan en el `<Outlet />` del layout.
4. **Cada página se carga con `lazy`**, y para eso su módulo exporta `Component`:

```tsx
export function TableDetailPage() { /* … */ }
export { TableDetailPage as Component };   // lo que consume router.tsx
```

5. **Las pestañas de una pantalla son rutas hijas, no `useState`.** Las siete de `/master/tables/:id` (`candidates`, `players`, `schedule`, `sessions`, `tasks`, `files`, `status`) se declaran como `children`, así cada una tiene URL propia, se comparte por link y el botón de atrás funciona.

**Guards: en el layout, no por ruta.** `RootLayout` redirige a `/login` si no hay sesión, y con eso cubre todo lo que cuelga de él. Cada layout de contexto (`PlayerLayout`, `MasterLayout`, `AdminLayout`) se envuelve en **`RequireContext`** (#269): quien no tiene ese contexto —misma regla que `useAvailableContexts`— es redirigido a `/`, que lo despacha a su home; nunca ve la nav ajena ni un 403. Una pantalla **no** repite ese chequeo. ⚠️ Sigue sin ser la seguridad: el backend autoriza endpoint por endpoint (#103), y `ForbiddenState` queda para el `403` por pertenencia a un recurso concreto (#121).

**No se usan los `loader` de React Router** (#115). La recomendación general del ecosistema es combinarlos con TanStack Query para adelantar el fetch, y es buena con SSR; acá no hay SSR, la app está detrás de login y cada pantalla ya define su skeleton (skill `diseno` §5). Adoptarlos obligaría a inyectar el `queryClient` en el router y a declarar cada query en dos lugares. Si algún día se mide un waterfall real, se agrega el loader en esa pantalla concreta sin tocar el resto.
