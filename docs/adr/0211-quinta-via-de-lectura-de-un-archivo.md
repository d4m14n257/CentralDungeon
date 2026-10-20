---
adr: 211
estado: Aceptada
fecha: 2026-09-04
---

# ADR-0211: Quinta vía de lectura de un archivo

## Decisión

**Un master puede abrir lo que le entregaron a una petición de su mesa** — se suma a las cuatro de `FileService.requireReadable`

## Por qué

Es el mismo desajuste que #206 tuvo que arreglar para lo que una mesa comparte: sin esto el master ve la ficha de la entrega en pantalla y recibe `404` al abrirla, que es lo peor de los dos mundos. Se resuelve con una consulta de `SubmissionFileRepository` que devuelve los `game_table_id` alcanzables desde el archivo, más `masterService.isMasterOf`: el repositorio resuelve alcance, el service decide autorización (#17, #121, #135). El que entregó ya llega por la primera vía —es suyo—, así que la vía nueva es solo para quien dirige
