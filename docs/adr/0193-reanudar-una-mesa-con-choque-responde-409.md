---
adr: 193
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0193: Reanudar una mesa con choque responde 409 y no reanuda

## Decisión

Al pasar de `Pause` a `InProgress`, `GameTableService.resume` vuelve a preguntar por el choque del `Primary` (#178) y, si lo hay, corta con `SCHEDULE_CONFLICT` nombrando la otra mesa. No reagenda ni cambia el estado

## Por qué

Una mesa en pausa **no reserva su horario** (#32, #178), así que su master fue libre de tomar otra cosa mientras tanto; volver es justamente el momento en que reclama la franja. Se evaluó la alternativa de reanudar igual y avisar, siguiendo #70, y se descartó: #70 es sobre no expulsar personas, no sobre dejar viva una mesa que su propio master no puede atender. Es además la respuesta que R1 ya da en `TableScheduleService.replace` y en `assignInitialMasters`, y tener dos respuestas distintas para la misma pregunta según por dónde se entre es lo que hace que una regla deje de ser entendible. La salida está a la vista: mover una de las dos agendas y reintentar
