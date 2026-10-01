# 3.3 Reglas

> Parte de la skill `arquitectura` (#274). Se movió desde `docs/` en F4.0 y conserva su numeración original, que es la que citan el código y `decisiones.md`.


**Datos de servidor**: exclusivamente TanStack Query. Prohibido `useEffect` + `fetch` para cargar datos, y prohibido guardar respuestas de la API en Context o Zustand — es lo que hacía el frontend viejo y por eso no tenía caché ni invalidación.

**Paginación** (#173): los listados de lectura traen más con `useInfiniteQuery` y un botón **"Ver más"**; las listas de trabajo de admin usan **página numerada** (`PaginationControls`: tira, «Ir a…» y selector 10 · 25 · 50 · 100, #271) con `keepPreviousData` para que no parpadeen al cambiar de página. Página y tamaño viven en la URL (`?page=`, `?size=`, #185) y el tamaño se lee con `adminPageSizeFrom`. Los tamaños salen de `config/pagination.ts`, nunca de un número suelto en el hook.

**Query keys**: centralizadas en `api/queryKeys.ts` como fábrica (`queryKeys.tables.detail(id)`), nunca strings sueltos en los componentes. Sin esto la invalidación se vuelve adivinanza.

**Cliente HTTP**: un único `client.ts` **genérico y tipado**, con las operaciones como métodos (#104). Inyecta el JWT, traduce el `ProblemDetail` a un `ApiError` tipado y centraliza el `401`. Reemplaza los siete wrappers sueltos del proyecto viejo.

```ts
// api/client.ts
export const api = {
  get:     <TRes>(path: string, params?: QueryParams) => request<TRes>('GET', path, { params }),
  getPage: <TItem>(path: string, params?: QueryParams) => request<PageResponse<TItem>>('GET', path, { params }),
  post:    <TRes, TBody>(path: string, body: TBody) => request<TRes>('POST', path, { body }),
  put:     <TRes, TBody>(path: string, body: TBody) => request<TRes>('PUT', path, { body }),
  patch:   <TRes, TBody>(path: string, body: TBody) => request<TRes>('PATCH', path, { body }),
  delete:  <TRes = void>(path: string) => request<TRes>('DELETE', path),
  upload:  <TRes>(path: string, files: File[], body?: unknown) => request<TRes>('POST', path, { files, body }),
};
```

Cada feature arma su módulo encima, y ahí es donde los tipos derivados de §3.2 llegan hasta la llamada:

```ts
// features/tables/api/gameTablesApi.ts
export const gameTablesApi = {
  list:   (params: GameTableFilters) => api.getPage<GameTableSummary>('/game-tables', params),
  byId:   (id: string)               => api.get<GameTableDetail>(`/game-tables/${id}`),
  create: (input: CreateGameTableInput) =>
             api.post<GameTableResponse, CreateGameTableInput>('/game-tables', input),
};
```

Si el backend cambia el contrato, esto **no compila** — que es el objetivo. Por eso **no hay capa de normalización** que traduzca nombres de campos (#108): esconder la ruptura la convierte en un bug de runtime.

**Estado de UI** — tres mecanismos con criterio explícito (#105):

| Mecanismo | Cuándo | Ejemplos |
|---|---|---|
| **Librería** | Ya existe resuelto | Toasts con `sonner`, tema con `next-themes` |
| **Context** | El estado pertenece a un subárbol, o hay que montar UI | `ConfirmDialogProvider` + `useConfirm()`; selección múltiple con Shift, montada **alrededor de la tabla que la usa**, no global |
| **Zustand** | Global y plano, sin necesidad del árbol | Contexto de rol activo, preferencias |

Todo lo demás —el estado de un modal, los filtros de una pantalla— es estado local del componente que lo posee. Ninguno de los tres guarda **datos de servidor**: eso es siempre TanStack Query.

**Contexto activo**: la navegación se organiza por contexto de rol (`frontend-diseno.md` §2). ⚠️ **El contexto es organización de UI, no autorización.** Estar "en contexto Admin" no habilita nada: el backend autoriza endpoint por endpoint (§2.6) y una ruta forzada sin el rol devuelve `403`. Ningún componente decide qué puede hacer el usuario mirando el contexto activo; lo decide el rol que trae el token.

**Tiempo real**: un único `StompProvider` mantiene la conexión WebSocket. Los mensajes que llegan son **señales de invalidación**, no datos: el handler hace `queryClient.invalidateQueries(...)` y TanStack Query refetchea. Nunca se escribe una respuesta de servidor en la caché a partir de un mensaje — eso reintroduciría por la ventana el estado de servidor fuera de TanStack Query que la regla de arriba prohíbe.

Tres reglas para que eso no se convierta en una tormenta de peticiones (#116):

1. **El mensaje dice qué invalidar, no "algo cambió".** Trae un tipo y un id (`{ type: 'GameTablePublished', tableId }`), y el handler invalida exactamente esa rama de `queryKeys`. Invalidar de más multiplica las peticiones por la cantidad de clientes conectados.
2. **`invalidateQueries` solo refetchea las queries activas** — las montadas en ese momento. Las demás quedan marcadas como stale y piden datos recién cuando alguien vuelve a esa pantalla. Es la defensa que viene de fábrica y por eso la invalidación puede ser generosa dentro de su rama.
3. **Al reconectar se invalida todo lo activo.** Mientras el socket estuvo caído se perdieron mensajes, así que el cliente no puede confiar en su caché. Sin esto, una caída de red de treinta segundos deja la pantalla mintiendo hasta el próximo montaje.

Tres destinos (#101 y #116): `/user/queue/notifications` para lo personal, `/topic/admin-queue` para la bandeja compartida, y `/topic/tables` para los cambios del catálogo público — que es lo que hace que a alguien navegando el explorador le aparezca una mesa recién publicada.

**Caché**: `staleTime` **explícito en toda query**, tomado de la política de `config/query.ts`. El default de TanStack Query es `0` —todo se considera viejo al instante— y dejarlo así es lo que produce el goteo de peticiones que se quiere evitar:

| Dato | `staleTime` | Por qué |
|---|---|---|
| Catálogos (sistemas, tags, plataformas) | 1 h | Solo los cambia un admin desde `/admin/catalogs` |
| Listado de mesas | 30 s | El WS cubre lo urgente; el `staleTime` cubre el resto |
| Detalle de mesa | 1 min | |
| Notificaciones | `Infinity` | El WS es su única fuente de cambio; pedirlas por tiempo es ruido puro |
| Perfil y karma | 5 min | El karma se recalcula al aprobar un comentario o en el job semanal (#97) |
| Bandeja de admins | 30 s | La reserva de #100 se libera sola a los 15 min |

**Sesión**: el access token vive **en memoria**, nunca en `localStorage` ni en `sessionStorage`; el refresh viaja en una cookie `httpOnly` + `SameSite=Strict` que el frontend no lee ni escribe. `client.ts` es el único que conoce el token: lo inyecta, y ante un `401` intenta el refresh una vez y reintenta la llamada; si el refresh también falla, limpia y manda a `/login`. La razón es concreta: hay editor de texto enriquecido renderizado en el navegador (#62), que es la superficie de XSS más directa del sistema, y un token en `localStorage` es legible por cualquier script que se cuele por ahí.

**Textos**: ningún string visible se escribe en el JSX. Todo pasa por `t('espacio.clave')` de i18next, con los JSON en `locales/<idioma>/<espacio>.json` — un espacio de nombres por feature (#107, #117). **Existen `es` y `en`** (#198): la indirección estuvo desde el primer componente y por eso agregar el segundo idioma fue traducir JSON, no recorrer pantallas. Los dos bundles viajan con la aplicación; el idioma sale de la elección guardada, del navegador, o cae en español.

**Todo texto visible habla en el idioma de quien usa la aplicación, nunca en el del código.** Ni rutas (`/admin/files`), ni nombres de campo o comando (`/table_status`, `file_owner`), ni jerga de desarrollo (*wizard*, *despliegue*, *parchear*, *disco*, *backend*, "cuando se construya"). Un lugar se nombra por la etiqueta que la persona ve en la navegación («Archivos», «Mesas»), y un filtro por lo que filtra ("filtrá por estado"). La única excepción es la ayuda del buscador, que enseña sus comandos porque se escriben así.

**Ninguna frase que lee una persona se escribe en el backend** (#197). Un `ApiException` lleva `errorCode` + `errorParams` y su `message` es inglés para un log; una notificación guarda tipo + parámetros. El frontend arma la oración, que es lo único que permite que una fila escrita hace meses se lea hoy en el idioma de quien la mira. La excepción es el texto que escribió una persona —el motivo de un rechazo de un master—: eso viaja verbatim y no se traduce.

**Componentes**: se construyen sobre las primitivas de `components/ui`. Antes de crear una primitiva nueva se consulta el MCP `shadcn-ui` para usar el componente real en vez de aproximar su API. Nada de MUI, Emotion ni styled-components en código nuevo.

**Formularios**: react-hook-form + zod, con el esquema en `schemas.ts` de la feature. El mismo esquema tipa el formulario y valida el submit, y queda atado al tipo del payload (§3.2, regla 7).

El formulario es un **componente puro, desacoplado de su contenedor** (#106): recibe valores iniciales y `onSubmit`, y no sabe si lo van a mostrar en un modal, en un panel lateral o en una página. El modal es un envoltorio aparte. Así el mismo `GameTableForm` sirve para crear en un diálogo y para editar en pantalla completa sin tocarlo.

El reparto es fijo (#110):

| Pieza | Qué hace | Qué **no** hace |
|---|---|---|
| `GameTableForm` | Monta `useForm` con su esquema zod, pinta los campos, valida y llama `onSubmit(values)` | No conoce la mutación, no invalida queries, no cierra nada, no muestra toasts |
| `EditGameTableDialog` | Compone `FormDialog` + el formulario, **posee la mutación** (`useUpdateGameTable`), cierra al terminar y avisa | No declara campos ni validación |
| `FormDialog` (`components/`) | El envoltorio: título, descripción, y confirmación al cerrar con cambios sin guardar | No sabe de dominio: no recibe entidades, solo `children` |

```tsx
// features/tables/components/EditGameTableDialog.tsx
export function EditGameTableDialog({ table, open, onOpenChange }: EditGameTableDialogProps) {
  const updateTable = useUpdateGameTable(table.id);

  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title="Editar mesa" confirmOnDirtyClose>
      <GameTableForm
        defaultValues={table}
        onSubmit={async (values) => {
          await updateTable.mutateAsync(values);
          onOpenChange(false);
        }}
      />
    </FormDialog>
  );
}
```

`confirmOnDirtyClose` es lo único que se rescata del `ModalBase` viejo, que ya preguntaba antes de cerrar un modal a medio llenar. Lo que no se rescata es el resto de aquel reparto: allá el formulario recibía `handleCloseModal` y `reloadAction`, hacía él mismo el `PUT` y decidía cuándo cerrarse — por eso no se podía usar fuera de un modal, que es justo lo que #106 corrige.

**Fechas y horas** (#111): el backend manda ISO-8601 UTC (§2.5) y la conversión a hora local es del frontend, con **`Intl.DateTimeFormat` nativo** desde `lib/date.ts`. Sin librería de fechas: no hay aritmética de calendario en la aplicación —los horarios son día de semana más hora (`modelo-datos.md`)— y el `useDate` del legacy ya resolvía esto con `Intl`. La zona horaria sale del perfil del usuario, y solo si no está, del navegador; nunca se asume la del navegador cuando el perfil dice otra cosa. El locale y la zona son parámetros de las funciones de `date.ts`, nunca constantes incrustadas: el legacy tenía `'es-ES'` fijo en el hook.

**Tipos**: un tipo base por entidad y derivados con utility types (§3.2). Ningún componente declara a mano una variante de un tipo que ya existe.

**Documentación**: JSDoc en todo `export`, con los `props` campo por campo. El detalle, junto con el Javadoc del backend, está en §6.2.

**Ubicación y nombres**: dónde va cada archivo, cuándo sube a la raíz y qué sufijo lleva está en §3.1.1–§3.1.3, y el ruteo en §3.1.6.

**Estilos** (#109): Tailwind en el JSX. Los tokens del tema —colores, tipografía, radios, y los estados de mesa y postulación— se definen en el bloque `@theme` de `styles/globals.css`; no hay `tailwind.config.ts` en Tailwind 4.

Las variantes por props se resuelven con `class-variance-authority`, que es lo que shadcn/ui ya usa internamente, y la composición condicional con el helper `cn()`:

```ts
const badge = cva('inline-flex items-center rounded-md px-2 py-1 text-xs font-medium', {
  variants: {
    state: {
      Opened:     'bg-state-open/15 text-state-open',
      InProgress: 'bg-state-active/15 text-state-active',
      Canceled:   'bg-state-canceled/15 text-state-canceled',
    },
  },
});
```

**Nada de CSS-in-JS, archivos CSS por componente ni objetos de estilo en JavaScript** — el frontend viejo tenía los estilos en `styles/*.js` y cuatro motores conviviendo. Estilos inline solo para valores calculados en runtime.

**Un patrón de estilo tiene nombre** (#273). Una combinación de utilidades que cumple un papel de diseño —título de página, etiqueta de sección, acciones de fila, lista con divisores, error en un diálogo— no se escribe literal en una pantalla: es una clase de `styles/base.css` (`@layer components`, con `@apply`) o un componente de `components/` (`PageHeader`). Cuál existe ya, cuándo se crea uno nuevo y cómo, está en la skill `diseno` §5.c; se lee antes de escribir el JSX de una pantalla.
