---
adr: 236
estado: Aceptada
fecha: 2026-09-10
---

# ADR-0236: Un master puede adjuntar archivos a la petición que escribe

> La mitad del pedido que el modelo nunca tuvo

## Decisión

Tabla `task_files` (`V9`), con la forma de `submission_files`. `CreateTaskRequest`/`UpdateTaskRequest` llevan `fileIds`, y la petición los muestra tanto al master como a quien la recibe. Es la **sexta vía de lectura** de `FileService.requireReadable`: el formulario lo abre quien la petición alcanza —candidato o jugador—, no solo quien dirige

## Por qué

`table_tasks.accepts_files` decía si una **respuesta** podía traer archivos; nada decía que el **pedido** pudiera. Así que un master podía escribir «mandame tu ficha en este formulario» y no tenía forma de adjuntar el formulario: había que describirlo en prosa y esperar. Se construyó junto con #233 y no después porque sin esto el cajón `MasterRequest` solo podría tener adentro lo que publique un admin — un master no podría poner nada suyo, y el circuito que motivó los cajones quedaba a medias. Vincula y no copia (#79), así que el formulario publicado por la comunidad lo adjunta cada master en vez de resubirlo.
