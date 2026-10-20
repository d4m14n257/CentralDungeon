---
adr: 281
estado: Aceptada
fecha: 2026-09-30
---

# ADR-0281: El estándar de cabecera llega a todas las pantallas: los dos detalles de mesa del jugador pasan a PageHeader, y Mis archivos y Mi horario tienen su «Cómo funciona»

> Cierra lo que #280 dejó fuera

## Decisión

**`/player/tables/:id`**: la tarjeta con el título adentro se va; la cabecera es `PageHeader` con el estado como `badge`, la línea de masters y karma como `description`, `back` a explorar (`/player`), `help` `players.applying` y **`action` «Postularme»**, que estaba al pie de la tarjeta, después de todo lo demás. El rótulo del botón sigue siendo también su razón cuando no se puede (principio 2: «Ya te postulaste», «La mesa está llena»…), y el aviso de choque de horario —la única negativa que el lector puede resolver— queda justo debajo de la cabecera, al lado del botón que bloquea. El contenido sigue en su tarjeta, debajo. **`/player/my-tables/:id`**: el estado deja la esquina derecha —el lugar de la acción— y va junto al título, `back` a mis mesas, `help` `players.my-tables`, y el enlace a la ficha pública como `description`; no tiene acción principal porque solo se lee. **Dos secciones de ayuda nuevas, en `basics`** porque las dos pantallas son de jugador y de master a la vez: `basics.my-files` (qué junta, de dónde sale el cajón, «sin usar», la deduplicación, la purga y la baja, con pasos) y `basics.my-schedule` (qué muestra, qué dice el color, a dónde lleva cada bloque, la zona horaria, que un hueco es un hueco que el servidor acepta, la lista de abajo), en `es` y `en`. **La única cabecera que no es `PageHeader` es la de un perfil**, cuyo título vive en `ProfileCard` junto al avatar

## Por qué

Lo pidió el usuario para cerrar el estándar de #280 en todas las páginas y no solo en las de admin y master. **Al escribir la ayuda de Mis archivos apareció una discrepancia**, anotada en *Abierto* y no resuelta acá: el diálogo de editar dice que un archivo sin «Conservarlo para reusar» «se libera cuando pase mucho tiempo sin uso», dando a entender que uno conservado no; pero `StoredFileRepository.findPurgeCandidates` solo exceptúa a los publicados, así que un archivo conservado que ninguna mesa tiene adjunto también se da de baja a los 90 días. La ayuda nueva dice lo que el backend hace. **Un detalle que salió en las capturas**: en Mi mesa, sin agenda, el separador de Archivos quedaba como una línea suelta arriba de la tarjeta; ahora solo existe cuando hay agenda que separar
