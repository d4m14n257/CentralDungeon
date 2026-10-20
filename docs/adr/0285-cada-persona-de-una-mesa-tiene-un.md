---
adr: 285
estado: Aceptada
fecha: 2026-10-01
modifica: [45, 284]
---

# ADR-0285: Cada persona de una mesa tiene un indicador «Ver ficha», y la ficha depende de quién la abre: el admin ve la cuenta entera en /admin/users/:id; el master, una ficha corta

> Precisa #45 y #284; corrige que el perfil omitiera el usuario de Discord (#248)

## Decisión

**La ficha del admin** es una página, `/admin/users/:id`: usuario de Discord, país, miembro desde, mesas terminadas, roles y estado **con las dos acciones que los cambian** (cambiar roles, bloquear o desbloquear, las mismas reglas de la fila de la lista), asistencia, **todas las mesas** que dirige, juega o a las que se postuló —cada una con su relación y abriendo `/admin/tables/:id`— y el historial de lo que hicieron los admins con la cuenta. La lista de usuarios abre la ficha por el usuario de Discord y por un ícono en toda fila, que **reemplaza al de historial**: el historial ahora es una sección de la ficha y perdió su cabecera de estado y roles, que la ficha ya muestra. **La ficha del master** es un modal (`PersonSummaryDialog`): usuario de Discord, nombre y mesas terminadas, nada más. Reemplaza al enlace al perfil de jugador que tenían los nombres. **Las pestañas no saben cuál abren**: reciben `onOpenPerson` en el contexto y la pantalla que las monta lo decide, el modal en la del master y la navegación a la ficha en la del admin; el ícono va en masters, jugadores y candidatos. **Datos nuevos**: el perfil (`ProfileResponse`) suma `discordUsername` y `finishedTables` —mesas `Finished` en las que la persona fue `Player`—, bajo las mismas reglas de visibilidad (#41, #44, #47); y `GET /users/{id}/tables`, solo admin, con las mesas de una persona: masters y postulaciones mezclados y paginados en memoria, como la bandeja (#100), la más nueva primero, sin las dadas de baja ni las postulaciones retiradas (#25)

## Por qué

Lo pidió el usuario al probar #284: desde la mesa, el admin no podía abrir a nadie, y en la lista de usuarios lo único por fila era el historial, «que realmente no sirve»: lo que sirve es toda la información de la persona y desde ahí bloquear, cambiar roles, todo. Y fijó la línea entre los dos roles: **el master solo debe ver comentarios e información muy general**, como el nombre, el usuario de Discord y quizá las mesas terminadas, y eso es «muy importante para distinguir». La forma (página o modal) la dejó a elección: **página para el admin**, porque es un expediente con acciones y una lista que crece; **modal para el master**, porque es una consulta al pasar sin salir de la mesa. **Lo que no está, y por qué**: **ni comentarios ni karma**. Los comentarios no existen todavía en el código —las tablas están en la base, pero no hay servicio ni pantalla: son F5— y #248 sacó el karma del perfil hasta entonces; ninguna de las dos fichas dibuja una sección vacía que los prometa, y cuando lleguen van en las dos. **El usuario de Discord en el perfil** lo ve cualquiera que ya podía ver ese perfil: es una comunidad de un servidor de Discord y es cómo se encuentran, así que no abre nada que el servidor no muestre ya
