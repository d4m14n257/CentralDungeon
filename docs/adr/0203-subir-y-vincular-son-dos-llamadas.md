---
adr: 203
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0203: Subir y vincular son dos llamadas

## Decisión

**`POST /files` y después `POST /game-tables/{id}/files`**

## Por qué

Es lo que hace que «subir uno nuevo» y «reutilizar uno que ya tengo» terminen en la misma segunda llamada. La reutilización es la palanca de costo de toda la fase (#65, #75), así que no puede ser el camino con el paso de más. Un endpoint que subiera y vinculara de una haría lo contrario: subir sería lo directo y reutilizar lo raro
