---
adr: 129
estado: Aceptada
fecha: 2026-08-29
modifica: [7]
---

# ADR-0129: Diseño de Campañas y Temporadas — precisa y corrige #7

## Decisión

**Son dos problemas distintos y se resuelven distinto.** *Campaña*: **una** mesa de N sesiones dividida en bloques — entidad nueva `table_arcs` entre `game_tables` y `table_sessions`, porque **el reclutamiento es único** (el jugador se postula una vez y juega los cuatro juegos). *Temporada*: **no es una entidad** — es una columna `publish_at` en `game_tables` más un job que pasa `Preparation → Opened` cuando llega la fecha. **Construcción en fase 2**; el diseño se cierra ahora para que la v1 no tome decisiones que lo bloqueen

## Por qué

Corrige el supuesto de #7 y de skill `arquitectura` §4.7, que daban por hecho que campaña = agrupador de varias mesas y por lo tanto una FK. No lo es: si el grupo de jugadores es el mismo durante los cuatro juegos, no hay cuatro mesas que agrupar, hay **una** mesa larga con estructura interna. La diferencia se ve en una pregunta: cuántas veces se postula un jugador. Una. Para temporadas el razonamiento va al revés: lo que la comunidad hace —abrir veinte mesas a la vez cada cierto tiempo— **ya es posible** con la bandeja de admins (#100) y la selección múltiple; lo único que falta es que la salida no dependa de que alguien esté despierto a medianoche. Eso es una columna y un job, no un concepto nuevo. Inventar una entidad `seasons` para eso sería modelar el calendario de la comunidad dentro del schema sin que nada lo pida.
