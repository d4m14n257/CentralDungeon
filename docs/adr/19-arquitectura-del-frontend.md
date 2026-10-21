---
adr: 19
titulo: Arquitectura del frontend
estado: Vigente
actualizada: 2026-10-20
absorbe: [104, 105, 106, 108, 110, 111, 112, 113, 114, 115, 133, 150, 158, 161, 192, 243]
---

# ADR-19: Arquitectura del frontend

> Cómo se organiza y se escribe el código de `frontend/`: carpetas, fronteras entre features, cliente HTTP, datos de servidor, estado de UI, formularios, fechas y el panel de desarrollo. La skill `arquitectura` §3 lo aplica como reglas (estructura en §3.1, tipos en §3.2, datos, estado, formularios y fechas en §3.3). La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 Se agrupa por dominio, y una feature no importa de otra

**Decisión.** El frontend se organiza por dominio, nunca por tipo de archivo: `features/<dominio>/` con un `components/` **plano y con sufijos** (`Form`, `Dialog`, `Card`, `Badge`, `Page`…), y lo que no tiene dominio en las capas de la raíz (`components/`, `hooks/`, `lib/`, `api/`, `types/`, `config/`, `providers/`). Una feature no importa de otra: una pieza sube a la raíz **cuando una segunda feature ya la necesita**, y al subir pierde el dominio. Cada feature declara su superficie en un `index.ts` en su raíz; desde afuera se importa `@/features/tables`, nunca una ruta interna, y ese es el **único** barrel del proyecto. Cada capa guarda lo suyo: un hook vive en `hooks/` aunque su diálogo esté en `components/` (`useConfirm` en `hooks/useConfirm.ts`); un Context global junto a su provider, en `providers/` (`useAuth`); un store de Zustand, en su propia capa. Los tests van junto al archivo que prueban.

**Por qué.** Agrupar por tipo, como el legacy (`forms/`, `contexts/`, `normalize/`, `helper/`…), obligaba a abrir siete carpetas para tocar una mesa y mezclaba dominios sin relación. El umbral de dos usos es más bajo que las tres repeticiones del backend (`arquitectura` §2.4) porque acá la alternativa a subir no es duplicar un poco: es un import entre features, prohibido por la regla dura 16. Plano con sufijos y no subcarpetas por tipo, porque eso dejó en el legacy carpetas de un solo archivo. El barrel vuelve verificable la frontera —sin él nada distingue lo público de lo interno y un refactor rompe consumidores que no se sabía que existían—; uno por carpeta reintroduciría ciclos sin aportar frontera. Y un módulo que exporta un componente y un hook pierde el fast refresh.

*Antes: #112, #114, #243.*

## §2 Las pantallas viven en `routes/` y componen dominios pasando un id

**Decisión.** Una feature es un dominio, no una pantalla. Las pantallas viven en `src/routes/` y son el único lugar que compone varias features. Cada bloque de una pantalla compuesta es un componente `…Section` que vive en su feature, lanza su propia query, monta sus propios diálogos y **recibe un identificador, no una entidad**. Fuera de `routes/`, nada cruza: `components/`, `api/`, `hooks/` y `types.ts` de una feature siguen cerrados a las demás.

**Por qué.** El detalle de mesa necesita seis dominios a la vez; en el legacy era `PreparationStatus`, unas 300 líneas importando de todos lados con seis `useModal` en paralelo, un componente haciendo de página. Sin esta salida, la regla de §1 no tendría forma legítima de construir esa pantalla. Se acota a `routes/` porque las páginas son hojas —nada las importa salvo el router— y no pueden generar ciclos entre features. Que el contrato sea el `id` y no la entidad es lo que evita que `features/files` dependa del tipo `GameTable`: toda la dependencia entre dos features cabe en una prop.

*Antes: #113.*

## §3 Un solo cliente HTTP genérico y tipado, y ninguna capa de normalización

**Decisión.** Un cliente en `api/client.ts`, genérico y tipado, con las operaciones como métodos (`get`, `post`, `put`, `patch`, `delete`, `upload`, `getPage`) parametrizados por el tipo de respuesta y el de cuerpo; cada feature arma su `<dominio>Api.ts` encima. El manejo del `401`, el `ProblemDetail` y el JWT vive solo ahí. **No hay capa de normalización**: el tipo base de cada entidad (ADR-02 §3, `arquitectura` §3.2) es el contrato. Si la forma de la API y la de la UI difieren de verdad (una fecha ISO que se quiere como objeto), eso es un **mapeo** y vive en el `api/` de su feature.

**Por qué.** Tener las operaciones nombradas y reutilizables era lo bueno del legacy; lo malo, siete archivos sueltos (`getter`, `setter`, `patcher`…) sin tipos, cada uno repitiendo el manejo de errores. Al ser genérico, el tipo derivado viaja hasta la llamada: `api.post<GameTableResponse, CreateGameTableInput>(...)` no compila si el payload no coincide. La normalización existía para que un renombre en el backend no rompiera el frontend, y con TypeScript estricto eso es justo lo que no se quiere: si el backend renombra un campo, la compilación tiene que fallar. Una capa que traduce nombres esconde la ruptura y la convierte en un bug de runtime.

*Antes: #104, #108.*

## §4 Los datos se piden con TanStack Query desde el componente, y el error reemplaza la pantalla solo si no hay nada que mostrar

**Decisión.** Cada pantalla pide sus datos con TanStack Query desde el componente; **no se usan los `loader` de React Router**. Es reversible por pantalla: si se mide un waterfall real, se agrega el loader ahí sin tocar el resto. Una pantalla de lectura muestra `ErrorState` solo con **`isLoadingError`** (falló y no hay datos), nunca con `isError`: en una lista, el bloque de error se condiciona a `isLoadingError`; en un detalle, el retorno temprano es `if (isLoadingError || !dato)`. Un refetch de fondo que falla con datos en caché (`isRefetchError`) no toca la pantalla: lo señala el indicador global de conexión.

**Por qué.** Sin SSR, con la aplicación entera detrás de login y cada pantalla con su skeleton, el loader obliga a inyectar el `queryClient` en el router y a declarar la misma query en dos lugares a cambio de milisegundos en navegaciones frías. Y en `@tanstack/query-core` la acción de error pone `status: "error"` sin borrar la `data` cacheada: con `isError`, una lista mostraba los datos **y** el error a la vez, y un detalle reemplazaba contenido bueno por un error en cada refetch fallido.

*Antes: #115, #150.*

## §5 El estado de UI va en una librería, un Context o Zustand, según a quién pertenece

**Decisión.** Tres criterios para el estado que no es de servidor: **librería** cuando ya existe (toasts con `sonner`, tema con `next-themes`); **Context** cuando el estado pertenece a un subárbol o necesita montar UI (la confirmación imperativa, la selección múltiple de una tabla concreta); **Zustand** cuando es global y plano (el contexto recordado). Los datos de servidor no van en ninguno de los tres: son de TanStack Query (regla dura 11).

**Por qué.** Los cinco contexts del legacy acertaban en que mensajes, tema, confirmación y selección son estado de UI; fallaban en usar el mismo mecanismo para todo, datos de servidor incluidos. La selección con Shift fija el criterio: es de **una tabla**, no de la aplicación, así que va como Context alrededor de esa tabla y no como estado global.

*Antes: #105.*

## §6 El formulario es puro, y el diálogo que lo aloja es el dueño de la mutación

**Decisión.** Un formulario recibe valores iniciales y un `onSubmit`, valida, y no sabe si vive en un modal, un panel o una página; va en `features/<dominio>/components/` junto a lo que usa. El envoltorio de modal es `FormDialog`, en `components/`: título, descripción y la pregunta antes de cerrar con cambios sin guardar (ADR-20 §5); no recibe entidades del dominio. El diálogo de cada feature compone `FormDialog` + el formulario y **es el dueño de la mutación**.

**Por qué.** Con la mutación en el diálogo, el mismo `GameTableForm` sirve para crear en un modal y para editar en una página sin tocarlo. En el legacy el reparto estaba invertido —`EditTableForm` recibía `handleCloseModal` y `reloadAction`, hacía él mismo el `PUT` y decidía cuándo cerrarse— y por eso no se podía usar fuera de un modal. De `ModalBase` se rescata solo la pregunta antes de cerrar un modal a medio llenar. `FormDialog` no recibe entidades porque, si las recibiera, no tendría por qué ser transversal.

*Antes: #106, #110.*

## §7 Las fechas se formatean con `Intl`, y la zona y el locale entran por parámetro

**Decisión.** Fechas y horas con `Intl.DateTimeFormat` nativo, en `lib/date.ts`, **sin librería de fechas**. Ninguna función de `lib/date.ts` lee una constante: locale y zona entran por parámetro. La zona sale del navegador (`browserTimeZone()`), porque el modelo no tiene `users.timezone` (ADR-03 §6); el día que exista una preferencia de perfil cambia el llamador y nada más. La suite de Vitest corre con `TZ=America/Argentina/Buenos_Aires`, fijado en `vite.config.ts`.

**Por qué.** No hay aritmética de calendario: los horarios son día de semana más hora, y el backend manda ISO-8601 UTC que solo hay que mostrar; `date-fns` + `date-fns-tz` serían dos dependencias del stack fijado para un caso que no existe. El `useDate` del legacy funcionaba y su único defecto era `'es-ES'` incrustado. La zona es parámetro por testeabilidad, no porque venga de la base. Y la suite corre en una zona real y no en UTC porque buena parte del módulo es correr el día al cruzar la medianoche, y en UTC ese caso no se ejercita nunca.

*Antes: #111, #192.*

## §8 Las mesas vivas y las terminadas del jugador son dos rutas, y el feedback del sistema es una acción del layout

**Decisión.** `/player/my-tables` muestra **solo las mesas vivas**; las terminadas y canceladas van a **`/player/history`**, con ruta propia. El **feedback del sistema** no es una ruta: será una acción global que se abre desde el shell en cualquier contexto —se construye en F5—. La interfaz no predice el límite de uno cada 24 h (ADR-11 §12): ofrece el botón siempre y traduce el `429` a un mensaje.

**Por qué.** Ruta propia y no un filtro, asumiendo el riesgo de dos listados casi iguales —el patrón que el sitemap eliminó del legacy—, porque el historial no muestra las mismas columnas (asistencia final, si dejaste comentario) y separa lo accionable de lo que solo se consulta. Si termina siendo la misma tabla con otro `WHERE`, se fusiona. El feedback no tiene contenido que mostrar, así que no merece pantalla. Y predecir la cuota obligaría a exponer su estado, que es justo lo que el token opaco de ADR-11 §12 evita.

*Antes: #133.*

## §9 El panel de desarrollo existe solo en `npm run dev` y no consulta nada sin sesión

**Decisión.** `components/dev/DevPanel.tsx` es un botón flotante, montado una vez en `RootLayout` y visible en toda pantalla, `/login` incluida, que abre un panel para entrar con `test-login` como Jugador, Master, Admin u Owner —cada fila con su Discord ID editable, precargado con la cuenta compartida de pruebas (`jugador-1`, `master-1`…)— y para crear una mesa de prueba abierta. No tiene endpoints propios: usa `test-login` (ADR-24 §2) y los de mesas. Lo gatea `if (!import.meta.env.DEV) return null`, que Vite vuelve `false` en el build y el minificador elimina. Sus textos pasan por i18n (namespace `dev`) como cualquier otro. Como vive en la rama que incluye `/login`, **ninguna query del panel corre sin sesión**: `useMe(isAuthenticated)`.

**Por qué.** Reemplaza los `fetch()` escritos a mano en la consola para cambiar de cuenta. Las cuentas son las compartidas porque ya tienen datos armados; una inventada no tiene mesas ni notificaciones. El gateo de queries no es cosmético: sin sesión, `useMe()` recibía `401`, `client.ts` fallaba el refresh y hacía `window.location.assign('/login')`, que remontaba el panel y repetía el ciclo — `/login` se recargaba sola indefinidamente en `npm run dev`.

*Antes: #158, #161.*
