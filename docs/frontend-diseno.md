# Diseño del frontend

> Define **qué pantallas existen y cómo se navega entre ellas**. Con qué piezas se construyen está en la skill `diseno`.
>
> El *cómo se escribe el código* está en la skill `arquitectura` §3, y **el sistema visual —tokens, componentes y patrones— en la skill `diseno`** (#273). El *por qué* de cada decisión, en `decisiones.md`. Acá quedan los principios, la navegación, el sitemap y los wireframes.
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
- **El chip dice dónde estás, no qué elegiste** (#222). Cada contexto es dueño de un prefijo —`/player`, `/master`, `/admin`—, así que el contexto sale de la URL. Un master que abre `/player` lee «Jugador», porque ahí es donde está. Las pantallas transversales (`/notifications`, `/my/schedule`) no son de ningún contexto y conservan el que traía quien las abrió.
- El contexto se recuerda en Zustand + `localStorage`, pero **solo como respaldo** para esas transversales y para elegir a dónde despacha `/`. Solo se recuerda un contexto que la cuenta tenga: por defecto `Jugador` si lo tiene, si no el primero disponible.
- **Al entrar siempre se cae en la home del contexto propio** (#222). `/` no tiene pantalla: mira los contextos de la cuenta y reenvía. El retorno del OAuth, el onboarding y el 404 apuntan ahí y no recalculan el destino cada uno.
- **Cada contexto se cierra a quien no lo tiene, y se lo devuelve a su home** (#269). `/player/*` pide el rol `Player`; `/master/*`, el rol `Master` o una fila viva en `masters` (#135); `/admin/*`, `Admin` u `Owner`. Quien fuerza la ruta de un contexto ajeno **no ve ni su nav ni un «sin permiso»**: el layout lo manda a `/` y `/` lo despacha a lo suyo. La regla es la misma que decide qué contextos lista el chip, así que el chip y la puerta nunca discrepan. Las transversales no son de ningún contexto y no se cierran.
- **Aun así, el contexto no es la seguridad.** El backend sigue autorizando endpoint por endpoint y responde `403` igual (#103, #121): la puerta del frontend decide qué se muestra, no qué se permite. `ForbiddenState` queda para el otro rechazo — un recurso concreto, dentro de un contexto que sí tenés, que no es tuyo.
- Las notificaciones y el avatar son globales: no dependen del contexto.
- **El feedback del sistema también es global y vive en el layout, no en una ruta** (#133). No tiene pantalla propia porque no tiene contenido que mostrar: es una acción. Se abre desde el shell, en cualquier contexto, y manda a `system_feedback` — anónima (#93), una cada 24 h (#94), directo a la bandeja de admins sin moderación (#95). Como el límite es del servidor, la interfaz **no lo predice**: ofrece el botón siempre y explica el `429` si toca.

### Sitemap

| Contexto | Ruta | Pantalla |
|---|---|---|
| Público | `/login` | Entrar con Discord |
| | `/auth/callback` | Retorno del OAuth, incluye el paso de invitación al servidor (#38) |
| | `/onboarding` | **Solo la primera vez**: nombre a mostrar y país. Bloquea hasta completarse (#134) |
| | `/` | **No es una pantalla**: despacha a la home del contexto de quien entra (#222) |
| **Jugador** | `/player` | Explorar mesas. **Home del contexto**. **El buscador es su único filtro** (#242, misma lectura que `/my/files`): los filtros por sistema, tag y plataforma que esta fila describía son los comandos `/table_system`, `/table_tag` y `/table_platform`, que acotan desde la misma línea y se combinan entre sí — y un término sin comando busca el nombre de la mesa. **Los tres son texto libre** (#246): el backend resuelve el grupo de sinónimos, así que buscar por uno encuentra las mesas etiquetadas con cualquier otro (#54, #56). Lo buscado vive en `?q=` (#185); la página no, porque el listado se acumula con «Ver más» (#173) |
| | `/player/tables/:id` | Detalle de una mesa y postulación |
| | `/player/applications` | Mis postulaciones y en qué estado están |
| | `/player/my-tables` | Mesas donde soy jugador — **solo las vivas** |
| | `/player/my-tables/:id` | Mi mesa: agenda, sesiones, peticiones pendientes |
| | `/player/history` | Mesas terminadas y canceladas, con la asistencia final (#133) |
| | `/my/files` | Mis archivos: a qué flujos pertenece cada uno (#233) y dónde se usa hoy (#232), reutilizables al adjuntar (#65). **Es el único lugar que pregunta el cajón**, porque es el único sin flujo del que deducirlo — y solo ofrece los que son tuyos (#237): los de jugador a cualquiera, los de master a quien dirija mesas, y nunca los anuncios. **Transversal, no del contexto Jugador** — lo que alguien subió como jugador y como master es una sola biblioteca (#222). **Es de players y masters** (#241): quien no tenga ninguno de los dos roles no la ve en el menú y la pantalla le dice por qué. **El buscador es su único filtro** (#242), y la lista va paginada de a 20 con divider entre filas |
| | `/player/profile` | Mi karma y los comentarios que recibí |
| | `/player/users/:id` | Perfil de otra persona, sujeto a #41, #44 y #47 |
| Transversal | `/my/schedule` | **Mi horario**: la semana entera en una grilla — lo que dirigís y lo que jugás, junto. De ningún contexto a propósito: son las mismas noches (#227) |
| | `/notifications` | Historial de notificaciones. **De ningún contexto**: no cambia el chip (#222) |
| | ~~`/help`~~ · ~~`/help/players`~~ · ~~`/help/masters`~~ · ~~`/help/admins`~~ | **Ya no son pantallas** (#231). La ayuda se lee en un diálogo levantado desde la pantalla que provoca la pregunta —`<HelpLink section="masters.schedule">`—, porque navegar a leerla tiraba el wizard a medio llenar. Las secciones y sus textos siguen enteros, en `features/help/sections/`. **`/help` queda reservada para soporte**: pedir asistencia, reportar un error. No se registra hasta que exista |
| **Master** | `/master` | Dashboard: qué necesita tu atención hoy, en todas tus mesas (#136) |
| | `/master/tables` | Mis mesas como master |
| | `/master/tables/new` | Wizard de creación — **solo con el rol `Master`** (#135) |
| | `/master/tables/:id` | Gestión, con pestañas: candidatos · jugadores · agenda · sesiones · peticiones · archivos · estado |
| **Admin** | `/admin` | **Home del contexto** (#270): una bienvenida, sin métricas hasta que se decida cuáles le sirven a un admin. Es donde cae un admin al entrar y el primer ítem de la nav («Inicio») |
| | `/admin/queue` | Bandeja compartida con reserva (#100): **solo lo que pide una acción**, no un listado de consulta (#176) |
| | `/admin/tables` | **Todas** las mesas, en cualquier estado, con filtros y buscador: el listado de administración, no una cola (#176). **Hoy** muestra solo las que esperan revisión porque `/admin/queue` todavía no existe; al llegar la bandeja (F3), las acciones de revisión se mudan ahí |
| | `/admin/catalogs` | Sistemas, tags y plataformas, **un grupo por fila** (#275). La cabecera crea un valor del catálogo de la pestaña, que nace aceptado (#280): el principal o una propuesta sin clasificar, que se aprueba o rechaza desde la fila. Buscar un equivalente encuentra su grupo |
| | `/admin/catalogs/:kind/:id` | **El lienzo de un grupo** (#275): el principal al centro, sus equivalentes alrededor y las propuestas flotando hasta que se conectan. Conectar acepta, mueve (#276) o fusiona; quitar una conexión separa. Otros grupos se traen al lienzo y quedan en `?with=`. Debajo de `md`, una lista con el mismo menú |
| | `/admin/files` | **La biblioteca de la plataforma**, no la personal (#237), y **solo lo publicado** (#278): el admin **sube desde acá, y subir es publicar**: la cabecera lleva a `/admin/files/upload` (#279). Cada archivo con quien lo subió y en cuántas mesas se usa; despublicar lo saca de la biblioteca, y dar de baja lo marca. Los archivos privados de las personas no aparecen: se alcanzan personificando, no desde acá. **No es `/owner/storage`**: acá solo se marca, los bytes los libera el owner y eso es F6 (#66, #207, #250) |
| | `/admin/files/upload` | **Subir a la biblioteca**, su propia página y no un panel sobre la lista (#279): la zona de subida acepta varios archivos, y cada uno elige en su fila, con un select, qué es —de los tres cajones publicables—. Sin elegir, «Subir» no manda nada y dice cuántos faltan. Al subir todo vuelve a `/admin/files`; lo que falló queda en la lista |
| | `/admin/moderation` | Comentarios por moderar |
| | `/admin/requests` | Solicitudes de rol, de mesa y generales |
| | `/admin/feedback` | Feedback del sistema |
| | `/admin/users` | Usuarios y bloqueos; desde acá se inicia **"ver como"** (#140) |
| | `/admin/settings` | Configuración: parámetros de negocio y límites (#141). **Sin «textos»**: desde #197 el backend no escribe ninguna frase que lea una persona, así que esa categoría no se construyó (#263) |
| **Owner** | `/owner/audit` | Trazabilidad de cambios (#92) |
| | `/owner/storage` | Borrado físico de archivos (#66) |
| | `/owner/users/:id/migrate` | Migración de cuenta (#83) |

## 3. Sistema de diseño

**Vive en la skill `diseno`** (§3, `references/sistema.md`), que es la fuente de todo lo visual desde #273: dirección visual, cómo se trabaja la paleta y su contraste, los dos temas, los colores de estado y el karma. Los valores salen de `design/build.py` (#130) y se transcriben al `@theme` (#118).

## 4. Wireframes

> Los de abajo son los cinco que definieron el resto y se conservan como referencia rápida en texto. **Las 28 rutas del sitemap están dibujadas** en `design/out/screen-*.html`, en tema claro y oscuro — se regeneran con `python3 design/build.py`. La excepción es **`/admin/files`** (#207), que llegó con F1.4 y todavía no tiene preview: se construyó directo sobre `DataTable`, con la misma forma que `/admin/catalogs`, igual que `SearchQueryInput` y `UserPicker`. **`/admin/catalogs/:kind/:id`** (#275) tampoco tiene preview de pantalla: lo que se dibuja es el patrón, el lienzo de nodos, en `components-data.html`, porque la pantalla es ese lienzo más su panel.

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

**Viven en la skill `diseno`**: el inventario completo y las listas de trabajo (§5), el responsive (§5.b) y **el catálogo de clases y componentes de patrón** (§5.c). Se lee antes de crear cualquier pantalla o componente: es lo que evita escribir el décimo badge de estado (#261) o la vigésima cabecera de página a mano (#273).

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
