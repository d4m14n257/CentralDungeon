---
adr: 24
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0024: Cupo máximo de jugadores

## Decisión

**Se agrega `max_players` a `game_tables`**

## Por qué

Estaba abierto por falta de confirmación; confirmado que faltaba. El schema heredado nunca lo tuvo: `Players_Table.players` era el conteo actual mantenido por trigger, no el límite. El conteo se deriva con `COUNT` (#11) y se compara contra `max_players` en el service al aceptar una solicitud.
