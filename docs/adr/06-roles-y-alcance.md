---
adr: 6
titulo: Roles y alcance
estado: Vigente
actualizada: 2026-10-24
absorbe: [16, 37, 45, 67, 71, 72, 73, 89, 135, 140, 151, 152, 155, 159, 166, 169, 252, 284]
---

# ADR-06: Roles y alcance

> Qué roles hay en la plataforma y en cada mesa, cómo se acumulan, qué alcanza cada uno y dónde se verifica la pertenencia. Las reglas que esto produce las aplica la skill `arquitectura` (§2.6 para la autorización, `roles-y-alcance.md` y `reglas-negocio.md` §Identidad y roles para el modelo). La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 Cuatro roles de plataforma, acumulables y sin jerarquía, salvo `Admin`/`Owner`

**Decisión.** La autorización tiene dos capas: un **rol global** (`users_roles`), que se resuelve con `@PreAuthorize`, y un **rol por mesa** (`masters.master_type`), que se resuelve en el service porque depende del recurso concreto. Los roles globales son cuatro: `Player`, `Master`, `Admin` y `Owner`. Son **funciones acumulables, no niveles**: se puede ser `Master` y `Player` a la vez, o `Master` sin ser `Player`, y ningún rol hereda los permisos de otro. La única relación entre roles es que **`Owner` puede todo lo que puede `Admin`**, y por eso **no se acumulan**: nadie tiene los dos, y otorgar uno quita el otro (lo aplica el service que asigna roles). `Owner` y `Admin` **no** implican `Player` ni `Master`: para jugar o dirigir hace falta tener ese rol. **No se registra un `RoleHierarchy`**: cada endpoint enumera sus roles (`hasAnyRole('ADMIN','OWNER')`), nunca el "mínimo" de una escala. En el código el rol de plataforma es `Owner` y el de mesa es `Primary`, para que los dos no se llamen igual (§3). **`Owner` no se otorga desde la aplicación**: la interfaz no lo ofrece entre los roles que se pueden dar, y la API lo rechaza —un otorgamiento de `Owner` por `UserRoleService.grantRole` responde error, lo pida quien lo pida—. **Por ahora se da solo desde la base de datos**; el `POST /auth/test-login` de desarrollo y pruebas, que arma el actor con `asOwner` (ADR-24 «Testing» §2 «El actor de prueba tiene exactamente un rol, también cuando es Owner»), es un fixture y no un camino de la aplicación. El rechazo vive en el backend y no solo en la interfaz: esconder la opción decide qué se ve, no qué se permite (ADR-20 «Navegación, contextos y ayuda» §1 «Se cambia de contexto explícitamente, y el contexto es organización de UI, no seguridad»). Lo demás no cambia: un `Owner` sigue otorgando y quitando `Admin`, puede quitar `Owner`, y todo camino que quite un `Owner` sigue pasando por §2.

**Por qué.** El rol global replica lo que el schema heredado ya distinguía, y no se diseña un sistema de permisos más granular. `Owner` faltaba en el schema y en el seed, y sin él había operaciones —como el borrado físico de archivos (ADR-13 §8)— que ningún rol podía ejecutar. Un bean de jerarquía haría que el permiso de un endpoint dependa de una configuración lejana, y es el vector de CVE-2025-41248 (ADR-05 §4). Tener `Admin` y `Owner` juntos no suma un permiso y ensucia todo lo que pregunte por el rol: listados por rol duplicados, un `ContextSwitcher` que deduplica, la ayuda de ADR-20 §6 con la misma audiencia dos veces. `Player` y `Master` describen funciones distintas y se suman; `Admin` y `Owner` describen la misma con distinto alcance, y se elige una. Que `Owner` no se otorgue desde la aplicación lo decidió el usuario el 2026-10-24 como excepción (M33 «El diálogo de roles del Owner», F4.1 «El mapa de la interfaz»): el diálogo le ofrecía al owner `Admin` y `Owner` como roles para dar, y hoy no hay ninguna forma prevista de otorgar `Owner`.

*Antes: #16, #37, #67, #89, #169.*

## §2 La plataforma nunca se queda sin `Owner`, y la invariante toma dos bloqueos

**Decisión.** Todo camino capaz de quitar un `Owner` —la revocación directa y el otorgamiento de `Admin`, que lo quita por la exclusión de §1— pasa por `UserRoleService.isLastActiveOwner`, que toma **primero** un `PESSIMISTIC_WRITE` sobre la fila `Owner` de `roles` (el mutex) y **después** cuenta los titulares con una **lectura con bloqueo** (`lockActiveHolders`). `countActiveHolders` queda solo para observar; su Javadoc prohíbe enforcar la invariante con él. Borde aceptado: dos revocaciones simultáneas de `Owner` sobre la **misma** persona responden `200` y `409 LAST_OWNER` en vez de `200` y `200`.

**Por qué.** El conteo optimista se rompía de verdad: dos ITs con concurrencia real contra MySQL dejaron la plataforma en cero owners, por las dos puertas, y ningún test unitario podía verlo. El segundo bloqueo es la mitad que sostiene el arreglo: bajo `REPEATABLE READ`, la transacción que esperó el mutex despierta y un `count(*)` plano le responde desde **su propio snapshot**, contando como vivo al owner que la otra transacción ya degradó; solo una lectura con bloqueo saltea el snapshot. El mutex es una fila única y siempre presente, así que no hay ciclo de locks posible y cubre el caso en que no queda ninguna fila de grants que lockear. Otorgar `Player` o `Master` nunca llega ahí. El borde se acepta porque el `409` es verdadero sobre la plataforma, y evitarlo agrega una rama al camino más delicado.

*Antes: #252.*

## §3 En la mesa hay un master y sus co-masters: `Primary` y `Secondary`, exactamente un `Primary`

**Decisión.** `masters.master_type` vale `Primary` o `Secondary`, y cada mesa tiene **exactamente un `Primary` vivo**. Son vocabulario interno: **ningún texto visible los usa**. En la interfaz una mesa tiene un **master** y sus **co-masters**; el chip del primero dice "Master" y ascender a alguien es "Hacer master a X", en toda pantalla. Ser master **no** habilita a jugar: un master puede postularse como jugador a otra mesa solo si además tiene el rol `Player` (§1), y nunca a la que él mismo dirige (§5).

**Por qué.** El nombre anterior, `Owner`, colisionaba con el rol de plataforma. Se descartó `Creator` porque sería falso cuando un admin crea la mesa y le asigna el master (§7); `Primary` describe la jerarquía real —uno manda, los otros acompañan— la haya creado o se la hayan asignado. El `Primary` único es lo que sostiene los circuitos que necesitan a alguien a cargo, como la aprobación de vetos (ADR-09 §5): con dos o ninguno quedan sin quién cerrarlos. Los términos no se muestran porque son del modelo de datos, no del juego, y filtrarlos obliga a traducir en cada uso y reabre la confusión entre los dos "owner".

*Antes: #71, #73, #166.*

## §4 Dirigir una mesa se autoriza por la fila viva en `masters`, no por el rol `Master`

**Decisión.** El rol de plataforma `Master` significa **"puedo crear mesas propias"** y nada más. Dirigir una mesa concreta se autoriza **solo por la fila en `masters`** (viva, ver ADR-07 §8), sin exigir el rol: un `Player` al que un admin asigna como master tiene ahí **todas** las atribuciones —candidatos, agenda, sesiones, peticiones, vetos, pausa— y ninguna fuera de ella. La fila no se borra al cerrar la mesa: es el registro de quién la dirigió, y la caducidad sale de la máquina de estados (ADR-07 §1). En la interfaz, el `ContextSwitcher` ofrece el contexto Master si hay rol **o** al menos una fila viva en `masters` —`UserDetailResponse.hasManagedTables`, que `/users/me` calcula con un `exists` sobre las filas vivas—, y `/master/tables/new` se ofrece **solo con el rol**. **Proponer un valor de catálogo es parte de escribir una mesa**, así que también lo habilita la fila: `MasterService.writesAnyTable` (rol `Master`, `Admin` u `Owner`, o una fila viva en `masters`), en ADR-12 §3.

**Por qué.** Es un caso real: los admins abren mesas y reclutan a un jugador para dirigir **esa** mesa. El schema ya lo soportaba; lo que no lo soportaba era `hasRole('MASTER')` en los endpoints de mesa. Se descartó conceder y revocar el rol al asignar y al cerrar: obliga a preguntar "¿le queda otra mesa viva?", mete escritura de roles en un flujo que no es de roles y convierte un permiso de mesa en uno global (podría crear otras). Es la regla dura 17 leída al revés: **la pertenencia sola alcanza**. Sin `hasManagedTables`, quien tiene la fila pero no el rol no tendría forma de llegar a su mesa desde la interfaz.

*Antes: #135, #159.*

## §5 Nadie es jugador y master de la misma mesa

**Decisión.** Dos validaciones server-side, una por dirección: postularse a una mesa que el actor dirige es `403` (`RegistrationService.apply`), y agregar como `Secondary` a alguien con una postulación activa (`Candidate`/`Player`) en esa mesa es `409` (`MasterService.addOrPromote`, solo en la rama de alta de un master nuevo). El explorador público (`GET /api/v1/game-tables`) **excluye las mesas que el actor dirige** con un `NOT EXISTS` contra `masters`.

**Por qué.** `masters` y `table_registrations` son tablas independientes y nada lo impedía: un usuario con `Player` y `Master` veía sus propias mesas en el explorador. Postularse a la mesa de **otro** master sigue permitido con el rol `Player` (§3). La promoción no repite el chequeo porque quien ya es master nunca pudo tener una postulación activa ahí. La exclusión del explorador va en el backend y no ocultando en el cliente por el mismo criterio de §6: una regla real se aplica en la fuente de los datos.

*Antes: #155.*

## §6 La vista de gestión de una mesa tiene su propio endpoint y verifica pertenencia antes de leer

**Decisión.** `/master/tables/:id` lee de `GET /api/v1/game-tables/{id}/managed`, que verifica la pertenencia (`canOversee`, §8) **antes** de mapear nada y responde `403` sin cuerpo si no corresponde. `GET /api/v1/game-tables/{id}` sigue público para `/tables/:id`, porque cualquier jugador necesita ver una mesa para decidir si postularse. Nunca el mismo path con dos comportamientos según quién pregunta. El logo del header vuelve al home del **contexto activo** (`/master/tables` en Master, `/` en Jugador), con el mismo builder de rutas que usa el `ContextSwitcher`.

**Por qué.** Un chequeo en el cliente cambia qué se pinta, no qué se pide: el cuerpo de la mesa ajena viajaba igual y se veía en la pestaña Network. La regla dura 17 pide la verificación en el service antes de tocar nada, y eso es la lectura, no la pintura. La ruta de **gestión** no debe mostrar ni el encabezado sin pertenencia; mezclar el dato público con la pantalla del master en la misma URL confunde aunque no se filtre nada sensible.

*Antes: #151, #152.*

## §7 Un admin crea una mesa sin ser su master, y al asignarle masters se abre sin revisión

**Decisión.** Un admin puede crear una mesa sin dirigirla. Nace en `Unassigned` y, al asignarle sus masters (el admin decide quién es `Primary`), pasa directo a `Opened` sin pasar por revisión.

**Por qué.** Quien aprueba una mesa es un admin (ADR-07 §1), así que pedirle que apruebe la que él mismo creó no agrega control. `Unassigned` hace visible que a la mesa le falta quien la dirija, en vez de dejarla en un estado de revisión que no describe su situación.

*Antes: #72.*

## §8 El admin ve todo salvo la autoría de los comentarios; lee la gestión de cualquier mesa, edita la ajena, y el dueño se entera

**Decisión.** **Ver.** El admin no tiene restricciones de visibilidad, con una única excepción: **la autoría de los comentarios**, que tampoco puede ver; modera el contenido sin saber quién lo escribió. La caducidad de perfiles no le aplica (ADR-10 §2). Las lecturas de gestión de una mesa —detalle, candidatos, jugadores, sesiones, peticiones y entregas, archivos, pedidos de veto— y la descarga de un archivo ligado a una mesa aceptan a admin u owner con un solo criterio, `MasterService.canOversee` («dirige la mesa o tiene el rango de admin»). **Solo lecturas**: toda escritura sigue exigiendo `isMasterOf`, porque el rango no es la pertenencia (§4). Un archivo personal que ninguna mesa liga sigue siendo de su dueño. `/admin/tables/:id` monta las mismas pestañas del master en solo lectura, más **Detalles** primero e **Historial** al final; las decisiones de admin van en el aviso de estado (ADR-21 §10). **Editar.** `PUT /game-tables/{id}/admin-edit`, ruta propia (§6) que comparte la reescritura con el `PUT` del master; se permite en cualquier estado salvo `Finished`, `Canceled` y `Deleted`, y en `Preparation` no si otro admin la reservó (ADR-16 §5). El choque de agenda se mide contra todos los masters de la mesa, y el admin que no la dirige lo lee como `MASTER_SCHEDULE_CONFLICT` (ADR-08 §2) y el cupo no baja de los jugadores aceptados (`CAPACITY_BELOW_PLAYERS`). **Avisar.** Todo lo que un admin cambia de algo ajeno se le notifica a quien le pertenece: `TableEditedByAdmin`, `TablePausedByAdmin`, `TableResumedByAdmin`, `TableCanceledByAdmin` y `TableDeletedByAdmin`, a todos los masters de la mesa, sin nombrar al admin ni copiar el motivo (ADR-23 §3); no se avisa a un admin que también dirige esa mesa. Los jugadores reciben lo que ADR-18 «Notificaciones y tiempo real» §3 «Notifica lo que alguien tiene que saber y no tiene otra forma de enterarse» les avisa de su mesa: pausa, reanudación, cancelación, finalización y cambios de agenda, también cuando los hace un admin. Quién es cada persona de la mesa lo resuelve la ficha de ADR-10 §6.

**Por qué.** Moderar exige ver todo; el anonimato de los comentarios (ADR-11 §5) es absoluto y está por encima de este permiso. El admin abría `/admin/tables` y no podía entrar a ninguna mesa. Puede editar en cualquier estado no cerrado porque su puerta existe justamente para corregir la mesa ya abierta o en curso, y el aviso compensa que el master no la haya tocado. El admin no actúa por el master dentro de las pestañas (aceptar, vetar, asistencia, peticiones, archivos): para eso existe «ver como» (§9). A los jugadores no se les avisa de una edición que no toque la agenda: lo que mueve su semana les llega por ADR-18 «Notificaciones y tiempo real» §3, y el choque de agenda, por ADR-08 «Agenda, sesiones y asistencia» §2 «Dos compromisos chocan si sus intervalos se solapan en la semana UTC».

*Antes: #45, #284.*

## §9 «Ver como» es suplantación asistida, con cuatro límites duros

**Decisión.** Un admin puede entrar viendo lo que ve otra persona y actuar por ella, con cuatro límites: (a) **nunca sobre `Admin` ni `Owner`**; (b) **todo lo irreversible bloqueado**: vetar (ADR-09 §5), cancelar mesa (ADR-07 §1) y confirmar comentario (ADR-11 §3); (c) **todo lo que toque comentarios fuera por completo** —borradores, diálogo de comentar, pendientes—; (d) **motivo obligatorio** al iniciar. Se registra en `impersonation_sessions`, caduca sola a los **30 minutos**, y `audit_logs` gana `impersonation_id`. La persona es notificada al inicio, con el motivo y sin el nombre del admin. `impersonation_sessions.admin_id` se guarda siempre y lo ve el `Owner`; a cualquier otro se le muestra "modificado por un administrador". `audit_logs.updated_by` sigue siendo la identidad bajo la que ocurrió el cambio. Es de F6.

**Por qué.** El límite (c) es lo que hace admisible la función: `comment_drafts` guarda el autor a propósito hasta confirmarse, así que un "ver como" sin ese recorte le mostraría a un admin quién escribió qué, anulando el anonimato (ADR-11 §5, requisito duro del cliente), la excepción de §8 y la exclusión de comentarios de la auditoría (ADR-17 §1). Se separó qué se guarda de qué se muestra porque la función más poderosa del sistema no puede quedar sin responsable. Se descartó solo lectura (no cubre asistir) y actuar sin restricciones (permitiría vetar o cancelar con la cara de otro).

*Antes: #140.*
