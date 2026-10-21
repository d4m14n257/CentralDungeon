---
adr: 7
titulo: "Mesas: ciclo de vida"
estado: Vigente
actualizada: 2026-10-20
absorbe: [7, 24, 27, 32, 129, 163, 165, 171, 175, 180, 196, 216, 226, 229, 230, 245, 265]
---

# ADR-07: Mesas: ciclo de vida

> Cómo nace, se revisa, se abre, se pausa, se cierra y se borra una mesa, qué exige para existir y quién la dirige. Las reglas que esto produce las aplica la skill `arquitectura` (`reglas-negocio.md` §Mesa y el modelo de §4). La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 La mesa tiene una máquina de estados formal, y toda transición deja rastro

**Decisión.** `GameTableStatus` tiene once valores: `Draft`, `Unassigned`, `Preparation`, `ChangesRequested`, `Opened`, `InProgress`, `PauseRequested`, `Pause`, `Finished`, `Canceled` y `Deleted`. La mesa de un master nace en **`Draft`** —solo la ve él, no aparece en ningún listado ni avisa a nadie— y `POST /{id}/submit` la manda a `Preparation`, donde un admin la **aprueba** (`Opened`) o **pide cambios** (`ChangesRequested`, y el master la reenvía). La de un admin nace `Unassigned` (ADR-06 §7). El master edita **solo en `Draft` y `ChangesRequested`**: `Preparation` no es editable. `start`/`finish` los hace el `Primary`; `cancel`, el `Primary` o un admin, resuelto en el service porque es "pertenencia o rol". Toda transición no declarada es `409`, y toda transición real se escribe en `table_status_changes` desde un único método privado que es el que de verdad mueve el estado; las sensibles llevan justificación.

**Por qué.** El schema heredado tenía los estados pero ninguna regla sobre quién pasa de cuál a cuál ni dónde queda el motivo. `Opened` lo mueve el admin al aprobar, nunca el propio master. `Preparation` significaba a la vez «la estoy escribiendo» y «la estoy esperando», y el master podía reescribir una mesa mientras un admin la leía; separarlas da la regla: se edita cuando la mesa es tuya, antes de mandarla o cuando te la devuelven. Enviar es deliberado y no automático al crear, porque una mesa se escribe en varias sentadas. El admin no tiene borrador porque revisar es su trabajo. Un único método de transición impide que una nueva se agregue sin dejar su rastro.

*Antes: #27, #163, #245.*

## §2 La pausa del master necesita aprobación, y las justificaciones de moderación no son comentarios

**Decisión.** Las justificaciones de moderación —mesa rechazada y por qué, pedido de correcciones, motivo de pausa, motivo de cancelación— van en su propia tabla, **separadas de `comments`**. La pausa que pide un master **no es efectiva hasta que un admin la aprueba**: la mesa pasa a `PauseRequested` y sigue corriendo hasta que el admin resuelve (ADR-16 §1). La pausa que inicia un admin es inmediata, pero también se justifica. `Pause` y `Canceled` exigen justificación.

**Por qué.** `comments` es opinión entre personas y alimenta el karma (ADR-11 §1, ADR-11 §2); mezclar actos administrativos lo contaminaría. La pausa del admin se justifica igual para que el resto de los admins vea el motivo.

*Antes: #32.*

## §3 Una mesa no se crea sin decir qué, dónde y cuándo; abrirla no exige calendario

**Decisión.** `create` y `update` exigen **al menos un sistema, una plataforma, un tag y una franja de agenda**, con un código cada uno (`TABLE_NEEDS_SYSTEM`, `TABLE_NEEDS_PLATFORM`, `TABLE_NEEDS_TAG`, `TABLE_NEEDS_SCHEDULE`). La validación va **después** de autorizar. No aplica a `createUnassigned`: la mesa de un admin sin master es un borrador con nombre (ADR-06 §7). El wizard corta en el paso que puede arreglarlo, y la duración de cada franja se escribe en un `input[type=time]` en vez de elegirse de horas redondas (ADR-08 §1). En cambio, una mesa sin `start_date`, sin agenda o sin `total_sessions` **se abre igual, con cero sesiones**: materializar es consecuencia de abrir, no requisito. En el frontend, los mensajes de zod se traducen con un `customError` global (`config/zodErrors.ts`), y los días de la semana van con mayúscula inicial en los dos idiomas, capitalizando con el locale.

**Por qué.** Es regla de negocio y no comodidad de formulario, por eso vive en el backend. Autorizar primero evita que quien no dirige la mesa reciba un `400` sobre su agenda en vez de un `403`. El tag es obligatorio porque una mesa que nadie puede encontrar por tema es peor que el tag de relleno que después un admin fusiona (ADR-12 §2). 45 minutos y 2 h 45 son sesiones reales. Exigir calendario para **aprobar** sería una compuerta nueva sobre el flujo del admin decidida de costado. El mapa global de zod evita acordarse de un `t()` en cada `.min()` (regla dura 18); un día es una etiqueta de selector, nunca una palabra dentro de una oración.

*Antes: #196, #226, #229.*

## §4 `start_date` es un día, no un instante, y el wizard muestra lo ya resuelto

**Decisión.** `game_tables.start_date` es `DATE` (`LocalDate`), se carga con un input de fecha y se llama **«Comienza a partir de»**. Solo dice desde qué día buscar: la hora de cada sesión sale de la agenda (ADR-08 §3). Se formatea fijado a UTC para que no se mueva. El dashboard marca `ReadyToStart` cuando **llegó el día**. El indicador de pasos del wizard (`components/WizardSteps.tsx`) tiene tres estados —hecho, actual, pendiente— sobre una barra de progreso; cada paso es un botón, y un salto hacia adelante aterriza en **el primer paso que falta completar**.

**Por qué.** La hora no definía ninguna sesión y su único efecto era invisible: decidir si una franja de ese mismo día contaba, y fallaba en el caso natural —si la franja caía más temprano ese día en UTC (ADR-03 §6), la primera sesión se iba una semana—. Además un instante en UTC se lee como otra fecha según dónde estés; una fecha es el mismo día en toda LatAm y Europa. Una lista numerada dice dónde estás pero no qué dejaste resuelto, y frenar en el primer paso incompleto deja al master donde puede arreglarlo.

*Antes: #230.*

## §5 El cupo es `max_players`, con un tope de plataforma que gatea la escritura

**Decisión.** `game_tables.max_players` es el límite de jugadores; el conteo actual se deriva con `COUNT` (ADR-03 §1) y se compara en el service al aceptar. Hay un tope de plataforma, `tables.max_players_cap` (arranca en **12**, configurable sin desplegar, ADR-17 §3), aplicado en `GameTableService` en el punto por el que pasan `create`, `createUnassigned` y `update`: por encima es `400 MAX_PLAYERS_ABOVE_CAP` con el tope en los parámetros. **No achica nada existente**: una mesa por encima del tope sigue viva y se rechaza su próxima edición.

**Por qué.** El schema heredado nunca tuvo límite; su `players` era un conteo mantenido por trigger. Sin tope, `@Positive` dejaba crear una mesa para dos mil millones de personas, y un ajuste sobre un límite inexistente es una perilla desconectada. Va en el service y no en un `@Max` porque una anotación es una constante. Gatea la escritura y no la lectura para que bajar el tope no deje mesas en un estado que crearlas nunca habría alcanzado.

*Antes: #24, #265.*

## §6 Se borra lo que nadie vio; lo demás se cancela

**Decisión.** Una mesa se **borra** solo mientras nunca fue pública —`Draft`, `Unassigned`, `Preparation`, `ChangesRequested`— **y nadie se postuló**. Todo lo demás se **cancela**, con motivo, y queda visible como cancelada. El borrado es lógico (ADR-03 §7): `status = 'Deleted'` + `deleted_at`, con cascada explícita en el service a `masters` y `table_registrations` con la misma marca de tiempo y en una transacción; la transición queda en `table_status_changes`. Lo hace el `Primary` o un admin, `DELETE /api/v1/game-tables/{id}` devuelve `204` y **toda lectura la trata como inexistente** (`404`). Regla general: cada etapa que agrega una entidad decide y construye su borrado en esa misma etapa.

**Por qué.** Cancelar una mesa que otros vieron, a la que se postularon o en la que jugaron es el registro de algo que pasó, y de su cierre cuelgan la ventana de visibilidad (ADR-10 §2) y el karma (ADR-11 §9). Un borrador que nadie vio no tiene nada de eso, y obligar a cancelarlo llena la lista de mesas "canceladas" que no existieron para nadie. La condición de "sin postulaciones" cierra el hueco: si alguna vez hay candidatos, manda el hecho y no el estado.

*Antes: #175.*

## §7 `closed_at` se sella al cerrar la mesa, una sola vez

**Decisión.** Al entrar en `Finished` o `Canceled`, `GameTableService` sella `game_tables.closed_at`. Si ya tiene fecha, ninguna transición posterior la mueve.

**Por qué.** `closed_at` arranca la ventana de visibilidad de perfiles (ADR-10 §2) y es lo que distingue una mesa cerrada ayer de una cerrada hace un año; una mesa que se cierra sin sellar la fecha del cierre no se cerró del todo.

*Antes: #180.*

## §8 Los masters de una mesa: el orden de los chips es el cargo, y quitar marca la fila

**Decisión.** Al asignar masters, el admin busca personas y cada elegido entra como chip: **el primero es el `Primary` y el resto `Secondary`**; tocar el chip de un `Secondary` lo asciende y el anterior baja, y quitar al `Primary` asciende al siguiente. El buscador es `GET /api/v1/users/search`, **solo `Admin`/`Owner`**, y nunca ofrece a alguien que no esté `Allowed`. El diálogo vive en `routes/admin/` porque compone `users` y `tables`. `DELETE /api/v1/game-tables/{id}/masters/{userId}` marca la fila (`status = Deleted` + `deleted_at`); **solo el `Primary` quita**, al `Primary` no se lo puede quitar (`409`, primero se le pasa la mesa) y volver a agregar a la misma persona **revive su fila**. La pertenencia (`isMasterOf`, `isPrimaryOf`, la lista de masters y `hasManagedTables`) **filtra por estado**.

**Por qué.** Hay exactamente un `Primary` (ADR-06 §3): una lista ordenada lo expresa sin un segundo control y sin estado inválido posible. El buscador queda admin-only porque un directorio de personas no se abre más de lo que pide quien lo usa; abrirlo al `Primary` será una decisión explícita. Sin filtrar por estado, un co-master quitado seguía autorizado. La fila se marca porque quién dirigió una mesa es un registro, y la PK `(game_table_id, user_id)` hace que revivir sea la única forma de volver a agregarlo.

*Antes: #165, #216.*

## §9 El explorador ordena por fecha, la mesa nueva primero

**Decisión.** `GET /api/v1/game-tables` toma `@PageableDefault(sort = "createdAt", direction = DESC)`. El default vive en el controller y no en el JPQL, así que un `?sort=` explícito lo pisa.

**Por qué.** Sin orden, MySQL devuelve el orden físico —las más viejas primero, por los UUIDv7— y una mesa recién publicada podía no entrar en la primera página, que es justo la que hay que ver.

*Antes: #171.*

## §10 Campañas y Temporadas están fuera de la v1, con el diseño cerrado

**Decisión.** No se construyen en v1 (regla dura 13); el diseño queda cerrado para que la v1 no tome decisiones que lo bloqueen. **Campaña**: una sola mesa de N sesiones dividida en bloques, con una entidad nueva `table_arcs` entre `game_tables` y `table_sessions`, porque **el reclutamiento es único**. **Temporada**: no es una entidad; es una columna `publish_at` en `game_tables` más un job que pasa la mesa a abierta cuando llega la fecha. Los puntos a resolver antes de construirlas están en la skill `arquitectura` §4.7.1.

**Por qué.** Construirlas bloquearía el arranque, y el schema original nunca las tuvo. Una campaña no agrupa varias mesas: si el grupo es el mismo los cuatro juegos, el jugador se postula **una** vez, así que es una mesa larga con estructura interna y no una FK. Abrir veinte mesas a la vez ya es posible con la bandeja de admins (ADR-16 §5); lo único que falta es que la salida no dependa de que alguien esté despierto a medianoche, y una entidad `seasons` modelaría el calendario de la comunidad sin que nada lo pida. Las dos son aditivas.

*Antes: #7, #129.*
