---
adr: 8
titulo: Agenda, sesiones y asistencia
estado: Vigente
actualizada: 2026-10-20
absorbe: [26, 33, 36, 137, 178, 191, 193, 194, 195, 227, 228]
---

# ADR-08: Agenda, sesiones y asistencia

> La semana que una mesa reserva, los choques entre mesas, las sesiones con fecha que salen de esa semana y la asistencia que se registra en ellas. Las reglas que esto produce las aplica la skill `arquitectura` (`reglas-negocio.md` §Mesa y §Sesiones y peticiones). La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 La agenda es una semana de franjas en UTC, cada una con su duración, y se arma desde la grilla

**Decisión.** La agenda de una mesa son filas de `table_schedules`: día, hora y **duración propia de cada franja** (`table_schedules.duration`; `game_tables` ya no tiene duración), así que una mesa puede correr tres horas entre semana y seis el sábado. El día es el enum propio `Weekday`, con los mismos valores `'Monday'`…`'Sunday'` que guarda la columna y la traducción a `DayOfWeek` en un método; el frontend tiene la unión de literales equivalente en `lib/date.ts`. **El día y la hora son UTC** (ADR-03 §6). En el formulario hay **una sola forma** de armar la agenda: la grilla dice **cuándo** y la lista de franjas dice **cuánto** (escrito, ADR-07 §3). Una franja sin duración no reclama intervalo. El wizard tiene además un paso propio de archivos (el cuarto de cinco), que se enganchan apenas la mesa se crea, compartidos con los jugadores por defecto.

**Por qué.** La duración por franja la pidió el usuario sabiendo que era un cambio de modelo, y resultó chico: las sesiones guardan un instante, no un largo (§3), y lo único que la columna alimentaba era el choque (§2). Dos formas de cargar lo mismo dejaban al master adivinando a cuál le iba a creer la mesa. Un enum propio evita migrar los valores guardados o escribir un converter, y documenta en el tipo lo único que importa: el día es UTC, no el que dicen los jugadores. Los archivos tienen paso propio porque son una decisión propia —qué le doy a los jugadores— y plegados en la revisión serían lo que nadie mira.

*Antes: #191, #228.*

## §2 Dos compromisos chocan si sus intervalos se solapan en la semana UTC

**Decisión.** Se compara **intervalo contra intervalo**, `[hourtime, hourtime + duration)` en UTC, **semiabierto** (encadenar no choca) y **con envoltura** semanal. Cuatro reglas, en `ScheduleConflictService`: **R1** un master no tiene dos mesas vivas solapadas (`409` al guardar la agenda, crear la mesa, asignar masters o **reanudar**: `resume` vuelve a preguntar por el choque del `Primary` y, si lo hay, responde `SCHEDULE_CONFLICT` nombrando la otra mesa y no reanuda); **R2** nadie se postula a una mesa que choca con otra donde ya es `Player` (`409`); **R3** un master no acepta a un candidato que ya es `Player` en una mesa que choca (`409`); **R4** al aceptar a alguien, sus otras postulaciones `Candidate` que chocan **se notifican, no se rechazan**. Dirigir y jugar cuentan igual. **`Pause` no reserva horario**; una mesa sin agenda o franja sin duración nunca choca; dos franjas de la misma mesa solapadas son `400`; editar la agenda de una mesa poblada **avisa** al master a quiénes les genera choque, sin expulsar a nadie. Cada mesa del explorador trae un campo derivado que dice si choca con lo del actor.

**Por qué.** Nada impedía comprometerse dos veces en la misma franja. Comparar hora exacta dejaba pasar el caso frecuente (3 h a las 20:00 contra otra a las 22:00). R4 avisa porque hasta que aceptan a alguien no hay compromiso y elegir es suyo (ADR-15 §2); por eso exige poder retirar una postulación (ADR-07 §6). Reanudar no avisa y sigue: ADR-15 §2 es sobre no expulsar personas, no sobre dejar viva una mesa que su master no puede atender, y la pausa liberó la franja. El riesgo real es la envoltura: la comunidad juega de noche en América, de madrugada del día siguiente en UTC.

*Antes: #178, #193.*

## §3 Las sesiones se materializan como filas, y una cancelada se repone al final

**Decisión.** `game_tables.total_sessions` dice cuántas sesiones promete la mesa. Las sesiones son filas de `table_sessions`, materializadas al abrir desde `start_date` + agenda + `total_sessions`, no derivadas por cálculo. Cancelar una sesión **repone otra al final**: la cancelada conserva su `sequence_number` como registro de lo planificado y se agrega una nueva en la primera franja posterior a la última sesión en pie, con el siguiente número; sin agenda viva no se repone. La interfaz lo avisa **antes** de confirmar.

**Por qué.** Con `start_date`, agenda y duración se sabía cuándo empieza cada sesión pero no cuántas hay ni cuándo termina la mesa, ni cuántas quedan pendientes en una pausa. Derivar el calendario solo funcionaría si nada pudiera alterarlo, y `Pause` sí puede: al retomar hay que saber qué se jugó y qué no. Materializar habilita además corregir una fecha puntual, cancelar una sesión suelta y anclar comentarios a la sesión (ADR-11 §2). Reponer al final porque una cancelación es una noche que no se jugó, no una campaña más corta: mueve la fecha de cierre y nada más; no reponer dejaba a la mesa jugando once de doce sin que nadie lo decidiera.

*Antes: #26, #33, #194.*

## §4 Marcar una sesión como jugada y registrar quién vino son dos actos

**Decisión.** `table_sessions` registra la asistencia de cada jugador (`Present`, `Excused`, `Absent`, `Unknown`). `POST /sessions/{id}/hold` mueve `Scheduled` → `Held`; `PUT /sessions/{id}/attendance` no toca el estado de la sesión.

**Por qué.** La asistencia permite validar que quien comenta sobre alguien jugó con esa persona (ADR-11 §2) y es una métrica más del karma: faltar sin aviso dice algo y antes no quedaba en ningún lado. «Jugamos» y «vino esta gente» son hechos distintos: deducir uno del otro marcaría como jugada una sesión suspendida en la que el master cargó quién había avisado, o exigiría inventar un padrón para cerrar una sesión. Cuesta un clic y ningún registro dice algo que nadie afirmó.

*Antes: #36, #195.*

## §5 La asistencia histórica son tres números, derivados y sin cachear

**Decisión.** Se deriva con `COUNT`/`GROUP BY` y no se cachea. El denominador son las sesiones **con asistencia registrada**: `Unknown` queda fuera. **No se muestra como razón**: se muestran `Present`, `Excused` y `Absent`. Índice cubridor `(user_id, attendance)` para que el agregado sea index-only, y una lista de N personas se resuelve en **una** consulta agrupada, nunca una por fila.

**Por qué.** Contar `Unknown` haría ver a todos como ausentes crónicos al arrancar una mesa, justo cuando un master evalúa. Colapsar `Excused` con `Absent` vuelve inexplicable el número, igual que mezclar asistencia con karma (ADR-11 §10). No se cachea por el precedente de ADR-03 §1, que eliminó un contador mantenido por trigger para matar esa clase de inconsistencia; si algún día hiciera falta, el precedente es la proyección del karma (ADR-11 §9). El riesgo real no es el volumen sino el N+1.

*Antes: #137.*

## §6 Mi horario: la semana entera de una persona, y la misma grilla elige la franja

**Decisión.** `GET /api/v1/users/me/schedule` devuelve todo lo que la persona tiene comprometido en la semana —lo que **dirige** y lo que **juega**, con su rol— como bloques en **minutos desde el lunes 00:00 UTC**, servido por `ScheduleConflictService.weekOf()` con las mismas consultas del choque (§2). `/my/schedule` es transversal, bajo `/my` y no bajo un contexto: siete columnas por veinticuatro filas, un bloque que cruza medianoche se dibuja partido, cada hora libre es un `<button>`, la grilla se desplaza sola hasta la primera hora ocupada, cada bloque abre su mesa, y la leyenda tiene dos ítems fijos (el rol) más **una** lista con todas las mesas y su horario. La misma grilla va en el wizard, donde **hacer clic en una hora libre la agrega** y la hora que otra mesa ocupa no se ofrece; ahí los bloques **no navegan**. Queda abierto si `/my/schedule` sigue la regla de ADR-14 §5 para cuentas sin `Player` ni `Master`.

**Por qué.** Para saber si tenía lugar, quien creaba una mesa solo contaba con texto. La grilla dibuja lo que la regla compara: un hueco en pantalla es un hueco que el servidor acepta, y una segunda consulta habría dejado que las dos se desincronizaran. La semana de alguien no es la de un contexto: partirla escondería justo el cruce. Minutos desde el lunes UTC porque la semana del lector no empieza donde la de UTC (ADR-03 §6). Es la semana que se repite y no las sesiones, porque una mesa en preparación todavía no las tiene (§3). Botones reales para teclado y lectores de pantalla; navegar desde el wizard tiraría el formulario. Prevenir la franja ocupada es mejor que rechazarla.

*Antes: #227.*
