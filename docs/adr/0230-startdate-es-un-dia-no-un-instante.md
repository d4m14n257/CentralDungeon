---
adr: 230
estado: Aceptada
fecha: 2026-09-10
---

# ADR-0230: startdate es un día, no un instante, y el paso a paso del wizard muestra lo que ya resolviste

## Decisión

**Dos cosas, las dos salidas de crear una mesa en la pantalla real.** (a) **`game_tables.start_date` pasa de `DATETIME` a `DATE`** (`LocalDateTime` → `LocalDate`), el input de `datetime-local` a `date`, y el campo deja de llamarse «Primera sesión»: es **«Comienza a partir de»**. No hay conversión de zona en ninguna punta. (b) El indicador de pasos deja de ser una lista de números y pasa a tener **tres estados** —hecho con tilde, actual con anillo, pendiente en gris— sobre una barra que se colorea hasta donde llegaste; cada paso es un botón, y un salto hacia adelante **aterriza en el primer paso que falta completar**, no en el destino. Vive en `components/WizardSteps.tsx`, sin dominio adentro.

## Por qué

(a) **La hora no definía ninguna sesión y nadie podía saberlo.** `TableSessionService.occurrencesFrom` para el calendario sobre `table_schedules.hourtime`; `start_date` entra solo como corte «de acá en adelante». O sea que la hora tenía un único efecto, invisible: decidía si una franja de **ese mismo día** contaba o se salteaba. Y fallaba justo en el caso natural — el master escribe la hora a la que juega, y si la franja cae más temprano ese día en UTC (#22), la primera sesión se va **una semana**, sin que nada lo diga. El segundo motivo es el corrimiento de día: un instante en UTC se lee como **otra fecha** según dónde estés —14/10 21:00 en México se guarda `2026-10-15T03:00Z` y en Madrid dice «15 de octubre»—, y para un marcador grueso de «cuándo arranca» eso es ruido puro. Una fecha es el mismo día en toda LatAm y Europa, que es exactamente el reparto de esta comunidad. La etiqueta además mentía, y las dos pantallas que la mostraban no coincidían: `TableDetailPage` con hora, `MasterTableScheduleTab` sin ella. Migración `V8__start_date_is_a_day.sql`, truncando y no redondeando —el día del instante guardado es el día que se quiso—, y `formatPlainDate` en `lib/date.ts` formatea fijado a UTC **para que nada se mueva**, con su test en la zona de #192. `MasterDashboardService` pasa a marcar `ReadyToStart` cuando **llegó el día**, no cuando pasó un instante. (b) Una lista numerada dice dónde estás pero no qué dejaste resuelto, así que un formulario de cinco pasos se lee igual de incompleto en el último que en el primero — y el pedido fue exactamente ese. El salto hacia adelante valida en cadena en vez de bloquearse: **frenar en el primer paso incompleto deja al master parado donde puede arreglarlo**, que es el mismo criterio por el que el wizard ya avisaba por paso y no recién al enviar (#226, #228).
