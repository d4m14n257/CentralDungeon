---
adr: 20
titulo: Navegación, contextos y ayuda
estado: Vigente
actualizada: 2026-10-20
absorbe: [103, 136, 167, 168, 170, 217, 219, 220, 222, 231, 269, 270]
---

# ADR-20: Navegación, contextos y ayuda

> Cómo se recorre la aplicación: los contextos por rol con su prefijo y su puerta, la home de cada uno, la bandeja del master y la ayuda que se lee sin salir de la pantalla. La skill `diseno` §2 lo aplica (selector de contexto y sitemap) y `arquitectura` §3.1 el ruteo y las guardias. La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 Se cambia de contexto explícitamente, y el contexto es organización de UI, no seguridad

**Decisión.** Un chip en el header muestra los contextos que el usuario tiene habilitados —Jugador, Master, Admin, Owner— y cada uno tiene su propia navegación. Quien tiene uno solo ve el chip igual, sin caret ni menú (ADR-21 §3). **El contexto no autoriza nada**: el backend sigue autorizando endpoint por endpoint, y estar «en contexto Admin» no habilita absolutamente nada.

**Por qué.** Con roles acumulables y sin jerarquía (ADR-06 §1, ADR-06 §1, ADR-06 §1), un menú único que crece según los roles se vuelve largo y mezcla actividades que no se parecen en nada: explorar mesas para jugar y moderar comentarios. Separar por contexto mantiene cada espacio enfocado. Que la puerta del frontend no sea seguridad es lo que permite que la UI decida qué se muestra (§2) sin que nadie confunda eso con qué se permite.

*Antes: #103.*

## §2 Cada contexto es dueño de un prefijo y se cierra a quien no lo tiene

**Decisión.** Cada contexto tiene su prefijo: **`/player/*`** (explorar en `/player`, `/player/tables/:id`, `/player/applications`, `/player/my-tables`, `/player/history`, `/player/profile`…), **`/master/*`** y **`/admin/*`**. Cada layout monta `RequireContext`: `/player/*` exige el rol `Player`; `/master/*`, el rol `Master` o una fila viva en `masters` (ADR-06 §4); `/admin/*`, `Admin` u `Owner`. Quien no tiene el contexto no ve su header ni su nav ni un «sin permiso»: se lo manda a `/`. **`/` no es una pantalla**: es un despachador que mira los contextos de la cuenta y reenvía a la home del que tenga; el retorno del OAuth, el onboarding y el 404 apuntan ahí. Sin ningún contexto, `RootRedirect` lo explica en pantalla en vez de navegar. `/master/tables/new`, que pide el rol `Master` y no solo pertenencia, redirige a `/master`. **El chip reporta el contexto de la URL**, no uno elegido antes; las transversales (`/notifications`, `/my/*`, `/help`) no son de ningún contexto, no se cierran y conservan el que traía quien las abrió. La regla de la puerta es la misma que calcula `useAvailableContexts` para el chip. `ForbiddenState` queda para un recurso concreto, dentro de un contexto propio, que no es del lector (ADR-05 §5).

**Por qué.** El chip leía un valor guardado mientras la navegación era de otro contexto: un master en `/` exploraba mesas con el chip diciendo «Master». Con prefijos, el contexto sale de la URL y al entrar se cae siempre en lo propio. La puerta existe porque cualquier rol veía las pantallas de los demás aunque el backend respondiera `403`, y el usuario decidió que forzar una puerta ajena se contesta devolviéndote a tu zona. Compartir la regla con el chip impide que discrepen y deja entrar a su mesa a un co-master asignado sin el rol.

*Antes: #222, #269.*

## §3 La home de cada contexto es la dirección de su prefijo

**Decisión.** El `ContextSwitcher` y el logo llevan a la home del contexto activo (ADR-06 §6), y esa home es su prefijo: `/player` (explorar), **`/master`** (la bandeja, §4) y **`/admin`**. `/master` encabeza `MasterSectionNav` con *Lo que espera* y *Mis mesas*. `/admin` es una ruta índice con título y una línea de bienvenida, sin métricas; es donde cae un admin al entrar (`homePathFor('admin')`) y el primer ítem de `AdminSectionNav` («Inicio», con `end`); la bandeja va segunda.

**Por qué.** La home es la pantalla que dice qué hacer a continuación; en Master eso es la bandeja, no el listado. La barra de secciones existe porque, con dos pantallas en un contexto, sin una forma de cruzar entre ellas una queda alcanzable solo con una URL escrita a mano. Un prefijo sin índice era una página huérfana. Y todavía no se sabe qué métricas le sirven a un admin: una página de números que nadie lee es peor que un saludo. **Cuando esa decisión exista, reemplaza el cuerpo** de `/admin`; la ruta y su lugar en la nav ya son de la home.

*Antes: #220, #270.*

## §4 La home del master es una bandeja de trabajo ordenada por espera, no un resumen con números

**Decisión.** `/master` lista lo que espera acción del master, agrupado por mesa: **cinco tipos de ítem** —`CandidatesWaiting`, `OverdueTaskMissing`, `SessionToRecord`, `ChangesRequested`, `ReadyToStart`—, **ordenados por tiempo de espera**, no por cantidad. El backend manda código y parámetros y el frontend arma la frase (ADR-23 §3). «Entregas sin revisar» **no existe**: el ítem es la petición vencida con gente que no entregó, contando **personas** y no filas (ADR-15 §2); una petición que todos contestaron no genera ítem. Vive en su propio paquete, `com.centraldungeon.dashboard` (`/api/v1/master/dashboard`), y resuelve las sondas con consultas agrupadas por lista de ids; solo los destinatarios de una petición vencida se resuelven por petición, con `TableTaskService.recipientsOf`. `/master/tables` sigue siendo el listado completo.

**Por qué.** Un master con tres mesas no necesita saber cuántos candidatos tiene sino **a quién le debe una respuesta**; ninguna métrica (mesas activas, asistencia promedio) cambia lo que hace a continuación. Reusa la forma de la bandeja de admins (ADR-16 §5) sin su reserva, porque el trabajo de una mesa tiene un solo dueño. No hay marca de «revisada» en el modelo, y agregarla rozaría ADR-15 §2 (el sistema no juzga entregas); lo que sí se pide es que el incumplimiento quede visible (ADR-15 §2). Una persona esperando nueve días pesa más que tres esperando dos horas. Paquete propio porque lee de cuatro features y nada lee de él: en `tables/` habría creado un ciclo con `tasks/`. Los destinatarios no se duplican en una consulta agregada porque así las dos respuestas se desincronizan.

*Antes: #136, #217, #219.*

## §5 La ayuda se lee en un diálogo sobre la pantalla, y salir con cambios sin guardar pregunta

**Decisión.** La ayuda no es una ruta: `<HelpLink section="masters.schedule">` (o el «Cómo funciona» de una cabecera, ADR-21 §5) abre un `HelpDialog` sobre la pantalla. Las secciones viven en `features/help/sections/`, una por audiencia, y `HELP_SECTIONS` de `registry.tsx` las direcciona con un id tipado (`basics.search`, `admins.assign-masters`) derivado del propio registro: nombrar una que no existe no compila. **`/help` es de soporte** —pedir asistencia, reportar un error—, no de ayuda. `components/SearchQueryInput` importa `features/help`: es la única inversión de capa aceptada, porque la regla dura 16 prohíbe feature→feature y no esto; entre features se usa render prop. Y **salir con trabajo sin guardar pregunta**: `useUnsavedChangesGuard` (`useBlocker` para la navegación interna, `beforeunload` para la pestaña) y `FormDialog`, que cubre todos los formularios en diálogo. «Sucio» se mide contra lo que se cargó, no con el `isDirty` de react-hook-form, y la liberación tras guardar es un `ref`, no estado.

**Por qué.** Leer la ayuda del horario tiraba el wizard de creación entero: el costo no era buscar la sección sino irse. Se pierde la lectura corrida del sistema de punta a punta y se acepta —el usuario lo eligió—: la ayuda contesta preguntas donde aparecen, sin saturar. `isDirty` compara contra `defaultValues` y da por cambiado todo campo ausente, así que un formulario intacto se negaba a ser abandonado. Y el router pregunta si bloquear en el mismo tick que el `navigate`, antes de re-renderizar: un flag de estado todavía valdría `false` y la pantalla preguntaría si descartar lo que acababa de guardar.

*Antes: #167, #231.*

## §6 La ayuda enseña con pasos, se direcciona por un id estable, y cada fase escribe la suya

**Decisión.** Cada bloque de una tarea lleva sus **pasos numerados** —cómo se crea una mesa, cómo se acepta un candidato, cómo se asignan masters, cómo se usa el buscador— además de lo que hay que saber. Cada sección tiene un **id estable que es contrato**: cambiarlo rompe los enlaces que lo nombran. Las secciones se reparten por audiencia —lo de todos (`basics`: buscar, contextos, estados de mesa, cuenta, notificaciones) y lo de cada rol (`players`, `masters`, `admins`, con Owner adentro)—. Todo su texto sale de los JSON de idioma y no hace una sola query. **Toda fase que agrega funcionalidad agrega o actualiza su sección de ayuda**, y eso es parte de la definición de terminado (`plan-desarrollo.md` §6, punto 8).

**Por qué.** El buscador (ADR-22 §1) y los conceptos de la comunidad —cuatro roles acumulables, una mesa con nueve estados, master frente a co-master— no se adivinan, y sin un lugar donde leerlos cada pantalla repetiría media explicación. Una ayuda que solo define contesta «qué es» cuando la pregunta real es «cómo se hace»: el paso numerado es lo que se sigue con la pantalla detrás. El id enlazado contesta la pregunta que se acaba de hacer en vez de obligar a buscar. Y la ayuda que se escribe «después» no se escribe: es parte de la funcionalidad.

*Antes: #168, #170.*
