# Diseño del frontend

> Define **qué pantallas existen, cómo se navega entre ellas y con qué piezas se construyen**. Se lee antes de crear cualquier componente.
>
> El *cómo se escribe el código* está en `arquitectura.md` §3. El *por qué* de cada decisión, en `decisiones.md`. Acá está el diseño.
>
> El diseño visual —tokens y componentes— vive en el design system de **Claude Design** (`decisiones.md` #130). Acá están las pantallas, la navegación y el inventario de piezas; los valores concretos de color, tipografía y espaciado se deciden allá y se transcriben al `@theme` (#118).

## 1. Principios

Cuatro, para poder resolver discusiones sin volver a discutirlas:

1. **El estado de la mesa siempre visible.** Una mesa tiene nueve estados y casi todo lo que se puede hacer depende de en cuál está. El estado va en la card, en el detalle y en cualquier listado — nunca hay que adivinarlo ni entrar a buscarlo.
2. **No se muestra lo que no se puede hacer.** Si un jugador no puede postularse porque la mesa está llena o porque ya tiene una postulación activa, el botón no aparece deshabilitado sin explicación: se reemplaza por el motivo. Un botón gris que no dice por qué está gris es peor que no tener botón.
3. **Lo irreversible se confirma.** Vetar (#39), cancelar una mesa (#27) y confirmar un comentario (#40, es el único que se podrá dejar sobre esa persona en esa mesa) no tienen vuelta atrás. Los tres pasan por `ConfirmDialog` explicando la consecuencia, no por un "¿Estás seguro?" genérico.
4. **La lista es la explicación.** En vez de resumir con números agregados, se muestran los elementos: el karma se explica con los comentarios recibidos (#99), no con un gráfico de barras.

## 2. Navegación

### Cambio de contexto explícito

Los roles son acumulables y sin jerarquía (#37, #89): alguien puede ser `Player` y `Master` a la vez, o `Master` sin ser `Player`. En vez de un menú que crece mezclando actividades sin relación, hay un **selector de contexto** arriba:

```
┌────────────────────────────────────────────────────────┐
│  CentralDungeon    [ Jugador ▾ ]        🔔 3    ( AV ) │
└────────────────────────────────────────────────────────┘
                       │
                       ├── Jugador
                       ├── Master
                       ├── Admin
                       └── Owner      ← solo los que tenga
```

- **Quien tiene un solo rol ve el chip igual, pero sin caret ni menú** (#145): no hay nada que elegir, pero sí algo que mostrar — sin esto el header no da ninguna señal de en qué contexto está.
- **El contexto Master aparece con el rol `Master` o con al menos una fila viva en `masters`** (#135). Un jugador al que un admin asignó como master de una mesa entra por ahí, ve solo esa mesa, y **no ve `/master/tables/new`**: dirigir no es crear.
- El contexto activo se recuerda en Zustand + `localStorage`. Por defecto `Jugador` si lo tiene; si no, el primero disponible.
- **El contexto es organización de UI, no seguridad.** Estar "en contexto Admin" no habilita nada: el backend autoriza endpoint por endpoint (#103). Si alguien fuerza la ruta `/admin/queue` sin el rol, el backend responde `403` y la pantalla muestra el error — no se confía en el selector para nada.
- Las notificaciones y el avatar son globales: no dependen del contexto.
- **El feedback del sistema también es global y vive en el layout, no en una ruta** (#133). No tiene pantalla propia porque no tiene contenido que mostrar: es una acción. Se abre desde el shell, en cualquier contexto, y manda a `system_feedback` — anónima (#93), una cada 24 h (#94), directo a la bandeja de admins sin moderación (#95). Como el límite es del servidor, la interfaz **no lo predice**: ofrece el botón siempre y explica el `429` si toca.

### Sitemap

| Contexto | Ruta | Pantalla |
|---|---|---|
| Público | `/login` | Entrar con Discord |
| | `/auth/callback` | Retorno del OAuth, incluye el paso de invitación al servidor (#38) |
| | `/onboarding` | **Solo la primera vez**: nombre a mostrar y país. Bloquea hasta completarse (#134) |
| **Jugador** | `/` | Explorar mesas, con filtros por sistema, tag y plataforma |
| | `/tables/:id` | Detalle de una mesa y postulación |
| | `/my/applications` | Mis postulaciones y en qué estado están |
| | `/my/tables` | Mesas donde soy jugador — **solo las vivas** |
| | `/my/tables/:id` | Mi mesa: agenda, sesiones, peticiones pendientes |
| | `/my/history` | Mesas terminadas y canceladas, con la asistencia final (#133) |
| | `/my/files` | Mis archivos, reutilizables al adjuntar (#65) |
| | `/profile` | Mi karma y los comentarios que recibí |
| | `/users/:id` | Perfil de otra persona, sujeto a #41, #44 y #47 |
| | `/notifications` | Historial de notificaciones |
| | `/help` | **Global, no del contexto Jugador**: lo que sirve a todos —buscar, contextos, estados de mesa, cuenta, notificaciones—. Se entra desde el menú de la cuenta y pide sesión (#167) |
| | `/help/players` · `/help/masters` · `/help/admins` | La ayuda de cada rol, como rutas hijas. Cada bloque tiene su `#ref` estable y se enlaza desde la pantalla que lo necesita: `/help#search`, `/help/admins#assign-masters` (#168) |
| **Master** | `/master` | Dashboard: qué necesita tu atención hoy, en todas tus mesas (#136) |
| | `/master/tables` | Mis mesas como master |
| | `/master/tables/new` | Wizard de creación — **solo con el rol `Master`** (#135) |
| | `/master/tables/:id` | Gestión, con pestañas: candidatos · jugadores · agenda · sesiones · peticiones · archivos · estado |
| **Admin** | `/admin/queue` | Bandeja compartida con reserva (#100) |
| | `/admin/tables` | Mesas esperando revisión |
| | `/admin/catalogs` | Sistemas, tags y plataformas; fusionar y separar grupos |
| | `/admin/moderation` | Comentarios por moderar |
| | `/admin/requests` | Solicitudes de rol, de mesa y generales |
| | `/admin/feedback` | Feedback del sistema |
| | `/admin/users` | Usuarios y bloqueos; desde acá se inicia **"ver como"** (#140) |
| | `/admin/settings` | Configuración: parámetros de negocio, límites y textos (#141) |
| **Owner** | `/owner/audit` | Trazabilidad de cambios (#92) |
| | `/owner/storage` | Borrado físico de archivos (#66) |
| | `/owner/users/:id/migrate` | Migración de cuenta (#83) |

## 3. Sistema de diseño

Tokens en el bloque `@theme` de `src/styles/globals.css`. Tailwind 4 no tiene `tailwind.config.ts`.

### Dirección visual

**Fantasía sobria** (#131): serif solo en títulos, sans en el cuerpo, densidad media. Se lee como herramienta seria, con un guiño al género.

La paleta **no se eligió, se midió** sobre los assets que la comunidad ya usaba (#132):

| Fuente | Qué dio |
|---|---|
| Gradiente de `links.centraldungeon.org` | `#214b90` · `#070c12` · `#211949` · `#3e308b` |
| Píxeles del logo y el favicon | Azul ~218° (27% de lo cromático) y violeta ~250° (17%). **Ningún píxel cálido** |

De ahí salen las dos decisiones que gobiernan todo lo demás:

- **Acento: el violeta de marca** (`#3e308b` y su escala). Se eligió sobre el azul aunque el azul sea el hue dominante, porque el azul choca con `state-active` (**InProgress**, el estado más frecuente) y el violeta solo con `state-paused` (**Pause**, excepcional).
- **Canvas oscuro: `#070c12`**, el negro-azulado de la propia comunidad, no slate neutro.

**El choque es inevitable y se contiene con separación de roles**, no con distancia de tono: los dos hues de la marca ya están ocupados por estados, así que el **acento aparece únicamente como relleno sólido** (botones, foco, karma) y los **estados únicamente como relleno suave con punto y etiqueta**. Nunca compiten en el mismo rol.

Los valores exactos no viven acá: los genera `design/build.py` y se publican en el design system (#130).

### Cómo se trabaja la paleta

`design/build.py` es la fuente de verdad. **Solo se versiona el script**; `design/out/` se regenera, igual que los PNG de `docs/diagramas/`.

```bash
python3 design/build.py                  # regenera out/ y mide los 30 pares de contraste
open design/out/accent-decision.html     # cualquier preview
python3 design/extract-brand-colors.py <logo.png> "LOGO"   # rehace la medición de #132
```

Qué produce en `design/out/`:

| Salida | Qué es |
|---|---|
| `theme.css` | El bloque `@theme` completo. **Es lo que se transcribe** a `frontend/src/styles/globals.css` |
| `accent-decision.html` · `colors.html` · `states.html` · `typography.html` | Los tokens, con la evidencia de marca y el contraste de cada par |
| `components.html` | Botones, badges, karma y `GameTableCard`, en ambos temas |
| `screen-*.html` | Las cinco pantallas de §4, en ambos temas |

**Cambiar un color es editar `build.py` y volver a correrlo**, nunca tocar el CSS generado. Cada corrida mide el contraste y **sale con código 1 si algo cae por debajo de AA**: una paleta que rompe accesibilidad no llega a publicarse.

### Tema claro y oscuro

Ambos, con `next-themes` o equivalente. El frontend viejo ya tenía `ColorModeContext`; es una función que se conserva.

**Oscuro por defecto** (#131) — la comunidad juega de noche. El claro se deriva del oscuro, no al revés, y los dos tienen que estar igual de terminados: E0.5 no se cierra con uno solo.

**Se cambia desde el `UserMenu`**, no desde una pantalla de configuración, y **sin opción "seguir al sistema"** (#144): que el default sea oscuro es una decisión de diseño, no la preferencia del sistema operativo de cada uno. El ítem nombra la acción, no el estado — estando en oscuro dice "Tema claro". La elección queda guardada. Como el menú solo existe con sesión, `/login` se ve siempre en oscuro; el gradiente de marca que lo cubre no depende del tema.

**El acento como texto es un token propio**, `--color-brand-fg`, distinto del acento como relleno: ningún tono único pasa AA en los dos temas (`brand-400` da 3.29:1 sobre el canvas claro). Cualquier texto en color de marca —el wordmark, el karma— usa ese token y nunca una escala elegida a mano.

### Colores de estado — lo que más se repite

Los nueve estados de mesa y los cinco de postulación aparecen en toda la aplicación. Se definen **una vez** como tokens semánticos; ninguna pantalla elige su propio verde.

| Estado de mesa | Token | Lectura |
|---|---|---|
| `Unassigned` | `--color-state-draft` | gris — existe pero le falta master |
| `Preparation` | `--color-state-pending` | ámbar — esperando a un admin |
| `ChangesRequested` | `--color-state-warning` | naranja — el master tiene que corregir |
| `Opened` | `--color-state-open` | verde — se puede postular |
| `InProgress` | `--color-state-active` | azul — está jugándose |
| `PauseRequested` | `--color-state-pending` | ámbar — esperando al admin |
| `Pause` | `--color-state-paused` | violeta — congelada |
| `Finished` | `--color-state-done` | gris azulado — terminó bien |
| `Canceled` | `--color-state-canceled` | rojo apagado — se cortó |

| Estado de postulación | Token |
|---|---|
| `Candidate` | `--color-state-pending` |
| `Player` | `--color-state-open` |
| `Rejected` | `--color-state-canceled` |
| `Blocked` | `--color-state-blocked` |
| `Deleted` | `--color-state-draft` |

**Accesibilidad**: el color nunca es el único portador de información. Cada badge lleva su etiqueta de texto, porque `Pause` y `PauseRequested` comparten familia de color y solo se distinguen leyendo.

### Karma

Escala 0–10000 con 8000 por defecto (#30). Se muestra como número con un indicador cualitativo, **sin** desglose agregado (#99):

```
Karma  8 240   ●●●●○     (basado en 12 comentarios)
```

El detalle son los comentarios listados debajo. Nada de gráficos: el rango real es angosto y una barra sugiere precisión que el número no tiene.

## 4. Wireframes

> Los de abajo son los cinco que definieron el resto y se conservan como referencia rápida en texto. **Las 28 rutas del sitemap están dibujadas** en `design/out/screen-*.html`, en tema claro y oscuro — se regeneran con `python3 design/build.py`.

### Explorar mesas — `/`

```
┌──────────────────────────────────────────────────────────────┐
│  [ Buscar... ]   Sistema ▾   Tags ▾   Plataforma ▾   Tipo ▾  │
├──────────────────────────────────────────────────────────────┤
│  ┌────────────────────────┐  ┌────────────────────────┐      │
│  │ La Cripta de Ondrak    │  │ Hijos del Vacio        │      │
│  │ ● Opened               │  │ ● InProgress           │      │
│  │ D&D · Roll20 · Corta   │  │ D&D · Foundry          │      │
│  │ Martes 20:00 UTC       │  │ Jueves 19:00 UTC       │      │
│  │ 3 / 5 jugadores        │  │ 5 / 5 jugadores        │      │
│  │ Master: Ana (8 240)    │  │ Master: Beto (7 900)   │      │
│  └────────────────────────┘  └────────────────────────┘      │
└──────────────────────────────────────────────────────────────┘
```

Los filtros de catálogo resuelven por grupo de sinónimos (#56): buscar "DANDD" trae también lo etiquetado "D&D". Las mesas donde el usuario tiene un veto **no aparecen** (#29) — y el detalle por id responde `404`, no `403`, porque un `403` ya delataría que existe.

### Detalle de mesa — `/tables/:id`

```
┌──────────────────────────────────────────────────────────────┐
│  La Cripta de Ondrak                            ● Opened     │
│  Master: Ana (karma 8 240)  ·  Co-master: Beto               │
├──────────────────────────────────────────────────────────────┤
│  Descripcion (texto enriquecido)                             │
│                                                              │
│  Requisitos                                                  │
│  · Ficha de personaje nivel 3                                │
│  · Contar por que queres entrar                              │
│                                                              │
│  Agenda    Martes 20:00 UTC · 3h · 12 sesiones               │
│            (se muestra en TU hora local)                     │
│  Cupo      3 / 5                                             │
├──────────────────────────────────────────────────────────────┤
│                                    [ Postularme ]            │
└──────────────────────────────────────────────────────────────┘
```

El botón cambia según el caso, y **siempre dice por qué** (principio 2): *"Ya tenés una postulación en curso"* · *"La mesa está llena"* · *"No está abierta a postulaciones"* · *"Necesitás el rol Jugador"* (#73).

Las fechas se guardan en UTC y se convierten en el navegador (#22).

### Gestión de mesa (master) — `/master/tables/:id`

```
┌──────────────────────────────────────────────────────────────┐
│  La Cripta de Ondrak            ● Opened      [ Acciones ▾ ] │
├──────────────────────────────────────────────────────────────┤
│ Candidatos│Jugadores│Agenda│Sesiones│Peticiones│Archivos│Estado│
├──────────────────────────────────────────────────────────────┤
│  Cola de candidatos — en orden de llegada                    │
│                                                              │
│  1.  Carla    karma 8 400   hace 2 dias    [Ver] [✓] [✗]     │
│  2.  Diego    karma 6 100   hace 1 dia     [Ver] [✓] [✗]     │
│  3.  Eva      karma 8 000   hace 4 horas   [Ver] [✓] [✗]     │
│                                                              │
│  El orden es por fecha de postulacion y no se reordena (#28) │
└──────────────────────────────────────────────────────────────┘
```

Aceptar al que completa el cupo dispara el rechazo automático del resto con la justificación por defecto (#34); la interfaz lo avisa **antes** de confirmar, no después.

### Bandeja de admins — `/admin/queue`

```
┌──────────────────────────────────────────────────────────────┐
│  Bandeja           ○ en vivo        [ Solo lo mio ]          │
├──────────────────────────────────────────────────────────────┤
│  ⬤ Mesa por revisar    "Hijos del Vacio"      hace 10 min    │
│                                              [ Reservar ]    │
│  ⬤ Comentario          moderacion pendiente   hace 1 h       │
│                                              [ Reservar ]    │
│  ⬤ Pausa solicitada    "La Cripta"            hace 2 h       │
│                            reservado por vos [ Resolver ]    │
│  ⬤ Feedback            del sistema            hace 3 h       │
│                                              [ Reservar ]    │
└──────────────────────────────────────────────────────────────┘
```

Al reservar, el ítem desaparece de la bandeja del resto en el momento, vía `admin-queue.changed` (#100, #101). Una reserva sin resolver se libera sola a los 15 minutos y el ítem reaparece para todos.

### Perfil — `/profile` y `/users/:id`

```
┌──────────────────────────────────────────────────────────────┐
│  ( AV )  Ana Valdez                                          │
│          Karma 8 240  ●●●●○      Jugador · Master            │
│          Asistencia: 18 de 20 sesiones                       │
├──────────────────────────────────────────────────────────────┤
│  Comentarios recibidos                                       │
│                                                              │
│  ● Positivo · master a jugador · hace 2 meses                │
│    "Siempre puntual y con la ficha lista."                   │
│                                                              │
│  ● Negativo · jugador a jugador · hace 5 meses               │
│    "Interrumpia bastante durante las sesiones."              │
└──────────────────────────────────────────────────────────────┘
```

**Los comentarios no muestran autor. Nunca, para nadie** (#43): ni al dueño del perfil, ni al master, ni a un admin, ni al owner. Lo único visible es la dirección (jugador→jugador, master→jugador…) y el impacto.

La asistencia va al lado del karma pero **no está incluida en él** (#98): son dos señales distintas y mezclarlas haría el número inexplicable.

Al mirar el perfil de otra persona, la visibilidad caduca a las dos semanas del cierre de la mesa que los vinculó (#44).

## 5. Componentes

### Primitivas shadcn/ui

Se generan en `components/ui/`. Antes de crear cualquiera se consulta el MCP `shadcn-ui` para usar la API real y no aproximarla.

`button` · `card` · `dialog` · `dropdown-menu` · `form` · `input` · `textarea` · `select` · `combobox` · `badge` · `table` · `tabs` · `sheet` · `popover` · `tooltip` · `avatar` · `skeleton` · `sonner` · `alert` · `separator` · `pagination` · `calendar`

#### Las tres que se apartan del default

Todo lo demás se usa tal como viene: los tokens del `@theme` ya lo tiñen solo. Estas tres **cambian de estructura**, no de color, y por eso hay que saberlo antes de generarlas:

| Primitiva | Qué cambia | Por qué |
|---|---|---|
| `badge` | Lleva **punto de color + etiqueta de texto**, no solo texto | El color nunca es el único portador de información (§3). `Pause` y `PauseRequested` comparten familia y solo se distinguen leyendo |
| `dialog` | En móvil es un **sheet desde abajo** con asa, no un modal centrado | Un diálogo centrado en 375 px queda pegado a los bordes (#138) |
| `table` | En móvil **deja de ser tabla**: cada fila pasa a ficha. Nunca scroll horizontal | Los listados de Admin y Owner tienen cinco o más columnas (#138) |

`button` no cambia de estructura, pero su color de texto **se calcula**: se elige entre casi-negro y blanco el que da AA sobre el relleno del acento, en cada tema. No es un valor fijo.

### Compuestos sin dominio

En `components/`. Ninguno recibe una entidad del dominio: si la recibiera, estaría mal ubicado (`arquitectura.md` §3.1.2).

> Los 20 compuestos de esta sección están dibujados en `design/out/`: `components-dialogs.html` (ConfirmDialog, FormDialog), `components-data.html` (DataTable, CollapsibleSection, IconAction), `components-inputs.html` (FilePicker, RichText, ScheduleEditor), `components-shell.html` (NotificationBell, ContextSwitcher, UserMenu), `ui-states.html` (EmptyState, ErrorState, ForbiddenState) y `components.html` (badges, karma, GameTableCard). `SearchQueryInput` y `UserPicker` (#164, #165) todavía no tienen preview: se construyeron directo en la pantalla que los pedía.

| Componente | Para qué |
|---|---|
| `FormDialog` | Envoltorio de todo formulario en modal: título, descripción y confirmación al cerrar con cambios sin guardar (#110) |
| `ConfirmDialog` | Toda acción irreversible (principio 3), detrás de `useConfirm` |
| `DataTable` | Listados paginados con orden, sobre `PageResponse<T>` |
| `CollapsibleSection` | Bloque plegable con título y acciones en la cabecera — el patrón que el legacy repetía en `CardComponent` y `ListComponent` |
| `IconAction` | Botón de icono con tooltip para las acciones de una fila o una ficha |
| `EmptyState` | Listas vacías, con la acción que corresponde |
| `ErrorState` | Error de carga: mensaje del `ProblemDetail` y botón de reintento |
| `ForbiddenState` | El `403` explicado (el `404` por veto se ve como "no existe", que es intencional) |
| `RichTextEditor` | Texto enriquecido (#62), sanitizado al enviar y al mostrar |
| `RichTextView` | Render sanitizado de lo guardado |
| `SearchQueryInput` | **Todo buscador de la app** (#164). Texto suelto busca por el criterio básico; `/` abre la lista —campos, y `/and`/`/or` cuando hay algo que unir— y lo elegido queda como chip fijo, con todo lo que se escriba después como su valor hasta el próximo `/`; las comas separan alternativas y el chip del conector se toca para pasarlo de "y" a "o". Recibe los campos que acepta, no los conoce |

### Compuestos con dominio

Viven en su feature, no en las capas transversales de la raíz, aunque se usen en varias pantallas de esa misma feature:

| Componente | Dónde |
|---|---|
| `TableStatusBadge` — los nueve estados, con su token y su etiqueta | `features/tables/` |
| `ScheduleEditor` — día de semana + hora, mostrado en hora local | `features/tables/` |
| `GameTableCard` — la ficha del explorador | `features/tables/` |
| `RegistrationStatusBadge` — los cinco de postulación | `features/registrations/` |
| `FilePicker` — subir **o** reutilizar del historial (#65), con el tope por archivo | `features/files/` |
| `KarmaBadge` — número + indicador cualitativo | `features/users/` |
| `UserPicker` — buscar una persona y elegirla, sobre `SearchQueryInput`; el criterio básico es el nombre de Discord **o** el del sistema (#164) | `features/users/` |
| `NotificationBell` — contador y panel, alimentado por WebSocket | `features/notifications/` |
| `ContextSwitcher` — el selector de rol de §2 | `app/components/` (es shell, no dominio) |
| `UserMenu` — avatar, tema y cerrar sesión | `app/components/` |
| `SystemFeedbackDialog` — el botón global de §2, sobre `FormDialog`; maneja el `429` de la cuota como mensaje, no como error roto | `features/feedback/` |

### Hooks compartidos

En `hooks/`:

| Hook | Para qué |
|---|---|
| `useTableSelection` | Selección múltiple en tablas, con rango al mantener **Shift**. Se monta como Context **alrededor de la tabla que lo usa**, no global (#105) |
| `useConfirm` | Confirmación imperativa: devuelve una promesa, para no encadenar estados de diálogo a mano |
| `useDebounce` | Filtros del explorador |
| `useDisclosure<T>` | Abrir/cerrar modales y paneles, y guardar el ítem que los abrió (`open(row)`) — es lo que hacía `useModal` con su `dataModal` |

### Estados obligatorios

Toda pantalla que lea datos define los cuatro: **cargando** (`skeleton`, no spinner suelto), **vacío** (`EmptyState`), **error** (mensaje del `ProblemDetail`, con reintento) y **sin permiso** (`403` con explicación; el `404` por veto se ve como "no existe", que es intencional).

**No se especifican pantalla por pantalla** (#139). Hay **cuatro arquetipos** —listado, detalle, formulario y dashboard— y cada pantalla hereda los cuatro estados del suyo, porque el skeleton, el error y el 403 son idénticos entre pantallas del mismo tipo.

Lo único que cada pantalla define por su cuenta es **el texto del vacío y qué acción ofrece**. Está en `design/out/ui-states-copy.html`, y ahí hay una trampa que conviene no repetir: `/master` y `/admin/queue` vacíos son **buenas noticias** —"nada espera tu respuesta"— y no pueden leerse como una pantalla rota.

## 5.b Responsive

**Las 27 rutas funcionan en teléfono, tablet y escritorio** (#138). Puntos de corte de Tailwind, sin inventar ninguno, y se diseña **de menor a mayor**: las clases sin prefijo son las del teléfono.

| Prefijo | Ancho | Qué cambia |
|---|---|---|
| *(sin prefijo)* | ≥ 375 px | Una columna. Los filtros se van a un `sheet`. Los modales son *sheet* desde abajo |
| `md` | ≥ 768 px | Dos columnas. Los filtros vuelven a la barra |
| `lg` | ≥ 1024 px | Tres columnas. Barra lateral donde la haya |

**La regla de la tabla ancha**, que es el caso caro: `/admin/catalogs`, `/admin/users` y `/owner/audit` tienen cinco o más columnas. En móvil **dejan de ser tablas** — cada fila se vuelve una ficha con identidad y estado arriba, el resto como texto y la acción al pie. **Nunca scroll horizontal.**

Referencia visual en `design/out/responsive.html`.

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
| `CardSettings` | `UserMenu` en `app/components/`, sobre `dropdown-menu`. El click-fuera escrito a mano con `window.addEventListener` desaparece: lo resuelve Radix |
| `MenuItemComponent` | `ContextSwitcher`. Su cadena de ternarios para elegir el icono según el nombre del rol se vuelve un `Record<Role, LucideIcon>` |
| `Span`, `forms/TextArea` | Desaparecen: eran estilo. `textarea` de shadcn/ui y clases de Tailwind |
| `@tinymce/tinymce-react` | Otro editor (TipTap o similar): TinyMCE necesita API key para uso alojado |

| Hook o utilidad legacy | Equivalente |
|---|---|
| `useModal` | `useDisclosure<T>` en `hooks/`, que además guarda el ítem que abrió el modal |
| `useMenu` | Desaparece: el `dropdown-menu` de shadcn/ui ya maneja anclaje y apertura |
| `useDate` | `lib/date.ts`, con el locale y la zona como parámetros en vez del `'es-ES'` fijo que tenía (#111) |
| `constants/constants.js` | Se parte en tres: las rutas a `config/paths.ts`; los días de la semana derivados del tipo del dominio —el arreglo escrito a mano tenía el orden mal (`Monday, Thursday, Wednesday, Tuesday…`)—; y las zonas horarias desde `Intl.supportedValuesOf('timeZone')` en vez de 27 strings a mano, que traían el cero dos veces (`UTC-00:00` y `UTC+00:00`) y ninguna zona de media hora |
| `api/getFlagCountry.js` | Sin equivalente. La bandera se resolvía llamando a un servicio externo por cada fila; el país del perfil se muestra como texto, y si más adelante se quiere bandera, es un emoji derivado del código ISO, sin red |
| `helper/getLanguage.js`, `language/*.xml` | `locales/es/<espacio>.json` con `i18next`, desde la primera etapa (#107, #117). El helper propio y los XML vacíos no vuelven |

**No se rescata nada de**: los siete wrappers de API (`getter`, `setter`, `patcher`, `putter`, `deleter`, `setterFiles`) → un solo `client.ts`; los cinco contexts con datos de servidor dentro → TanStack Query (regla dura 11); `next-auth`, instalado y nunca usado; el i18n a medias (`english.xml`, `spanish.xml`); y los cuatro motores de estilo conviviendo (MUI, Emotion, styled-components, `@mui/styled-engine-sc`) → Tailwind.
