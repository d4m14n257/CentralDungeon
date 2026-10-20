---
adr: 136
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0136: Dashboard del master

## Decisión

**`/master` es una bandeja de trabajo, no un resumen con números.** Lista lo que espera acción —candidatos sin responder, entregas sin revisar, sesiones por registrar, transiciones pendientes— agrupado por mesa y ordenado por urgencia. `/master/tables` sigue siendo el listado completo

## Por qué

Un master con tres mesas abiertas no necesita que le digan cuántos candidatos tiene, necesita saber **a quién le debe una respuesta**. Es el principio 4 de §1 aplicado al otro lado del sistema: la lista es la explicación, y un contador de "12 candidatos" obliga a entrar a las tres mesas para descubrir cuál mueve. Se descartó un dashboard con tarjetas de métricas —mesas activas, jugadores totales, asistencia promedio— porque ninguna de esas cifras cambia lo que el master hace a continuación. Reusa la forma de la bandeja de admins (#100) sin su mecanismo: acá **no hay reserva**, porque el trabajo de una mesa tiene un solo dueño y no se lo disputa nadie.
