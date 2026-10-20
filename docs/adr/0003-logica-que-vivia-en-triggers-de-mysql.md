---
adr: 3
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0003: Lógica que vivía en triggers de MySQL

## Decisión

**Se mueve al service layer**

## Por qué

Los tres triggers heredados (`create_new_user`, `create_player_in_table`, `players_in_table`) eran invisibles desde Java, no los cubría ningún test y no se versionaban junto al dominio. Detalle del reemplazo en skill `arquitectura` §4.5.
