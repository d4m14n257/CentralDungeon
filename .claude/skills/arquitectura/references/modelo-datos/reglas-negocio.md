# 4.5 Reglas de negocio (viven en el service layer)

> Parte de la skill `arquitectura` (#274), sección 4 — el modelo de datos. Se movió desde `docs/` en F4.0; su número original lleva ahora el prefijo `4.` (el viejo §5 es §4.5).


Ninguna vive en la base: no hay triggers ni stored procedures (#3). Cada una llega con su test unitario.

### Identidad y roles

| Regla | Dónde | Ref. |
|---|---|---|
| Login exige cuenta de Discord y membresía al servidor; si no es miembro se ofrece la invitación antes de cortar | `DiscordOAuth2UserService` | #38 |
| Todo usuario nuevo se crea con rol `Player` | `UserRegistrationService` | #38 |
| Los cuatro roles son acumulables. Única excepción: `Owner` puede todo lo que puede `Admin`. Se escribe `hasAnyRole('ADMIN','OWNER')` en cada endpoint, sin `RoleHierarchy` | `SecurityConfig` | #37, #67, #89 |
| `Owner` y `Admin` **no** implican `Player` ni `Master`: para jugar o dirigir hay que tener ese rol | `SecurityConfig` | #89 |
| **`Admin` y `Owner` son excluyentes**: nadie tiene los dos. Otorgar uno quita el otro — son el mismo rol con distinto alcance, y `Owner` está por encima. La quita deja **su propia fila** en `user_role_changes` | `UserRoleService` | #169 |
| **`Admin` y `Owner` los otorga y los quita solo un `Owner`.** Un `Admin` mueve `Player` y `Master`. El intento de un admin es `403 ROLE_GRANT_FORBIDDEN`, no `400`: es quién sos, no qué mandaste | `UserRoleService` | #169, F3.1 |
| **La plataforma nunca se queda sin `Owner`.** Quitarse el propio `Owner` es `409 CANNOT_REVOKE_OWN_OWNER`; quitárselo al último (o desplazarlo con la exclusión de #169) es `409 LAST_OWNER`. MySQL no lo puede expresar, así que es la única invariante global de roles que vive solo en el service | `UserRoleService` | #169, F3.1 |
| Otorgar o quitar un rol es **idempotente**: repetirlo no escribe fila de auditoría. Restaurar un rol revocado **flipea `users_roles.status`**, nunca inserta una segunda fila — la PK es `(user_id, role_id)` | `UserRoleService` | #25 |
| `users_roles.status` y `users_roles.deleted_at` se mueven **juntos**, vía `UserRole.revoke()` / `.restore()`: no hay setter que permita una fila `Deleted` sin fecha ni una viva con fecha. La columna existía desde el baseline y nada la escribía hasta F3.1 | `UserRole` (entidad) | #25 |
| Salirse del servidor conserva los datos; el baneo lo marca un admin a mano | `UserService` | #84, M30 |
| **Nadie con `Admin` u `Owner` puede ser bloqueado** — ni por un admin, ni por un owner, ni por sí mismo: `403 CANNOT_BLOCK_PRIVILEGED`. Entre pares no hay autoridad, y el bloqueo es irreversible desde el lado del bloqueado | `AdminUserService` | #84, #140a |
| Bloquear **conserva los datos**: no borra mesas, postulaciones ni historial. `Deleted` no transiciona en F3.1 | `AdminUserService` | #84 |
| **Todo cambio de rol o de estado invalida la caché `userAuth`** (`UserService.evictAuthCache`). El TTL de 60 s es la red de seguridad, no el mecanismo: un bloqueo que tarda un minuto en aplicar es un bloqueo que no bloquea | `UserRoleService`, `AdminUserService` | #122, #128 |
| El owner puede migrar los datos de un usuario a una cuenta nueva | `UserService` | #83 |

### Solicitudes y aprobaciones

| Regla | Dónde | Ref. |
|---|---|---|
| Un solo mecanismo para todo pedido con aprobación: pausar mesa, vetar jugador, pedir rol de master, pedir que se abra una mesa, peticiones generales | `ApprovalService` | #42, #90 |
| La entidad afectada se referencia de forma polimórfica; **el service valida que exista antes de insertar**, porque la base no puede. Nunca se mapea como `@ManyToOne` | `ApprovalService` · `ApprovalEntityResolver` | #78, #126 |
| **Un job periódico recorre los pedidos sin resolver y registra en `WARN` los que apuntan a algo que ya no existe.** No borra y no resuelve: un borrado automático sobre datos que perdieron su ancla es cómo se pierde la evidencia. Es el tercio del precio de #78 que no tiene otro test detrás | `ApprovalOrphanCheckService` | #78 |
| **Justificación obligatoria en las dos puntas**: en el pedido y en la resolución. Rechazar sin decir por qué es la mitad del mecanismo | `ApprovalService` | #42 |
| **Un pedido `Pending` por tipo y por persona**: el segundo es `409 REQUEST_ALREADY_PENDING`. Sin esto, un botón apretado dos veces llena la bandeja de duplicados que alguien resuelve a mano | `ApprovalService` | #42, #100 |
| Pedir el rol de `Master` teniéndolo ya es `409 MASTER_ROLE_ALREADY_HELD`, no un pedido que un admin tiene que leer para descubrir que no hacía falta | `ApprovalService` | #42 |
| **Solo se resuelve desde `Pending`**: re-resolver es `409 REQUEST_ALREADY_RESOLVED`, la misma forma que la máquina de estados de la mesa | `ApprovalService` | #42 |
| **Si la entidad referenciada desapareció entre el pedido y la resolución → `409 REQUEST_ENTITY_GONE`.** La fila sobrevive a lo que apunta a propósito —«una solicitud sobre una mesa borrada sigue siendo un hecho»— pero resolverla sería actuar sobre un fantasma | `ApprovalService` | #78, #126 |
| **Aprobar un `MasterGrant` otorga el rol llamando a `UserRoleService.grantRole`**, con la nota de resolución como justificación. No hay una segunda ruta que escriba la misma fila | `ApprovalService` → `UserRoleService` | #42, F3.1 |
| Pedir que se abra una mesa desemboca en #72: aprobar deja constancia de que el pedido procede, **no crea la mesa** —el pedido no trae nombre, sistema, cupo ni agenda—; un admin la crea en `Unassigned` y le asigna master | `ApprovalService` · `GameTableService` | #72, #90 |
| **Un pedido no notifica a nadie**; la resolución notifica **al solicitante**, con `ApprovalRequestApproved` / `ApprovalRequestRejected`. Los ítems de trabajo de admin no se duplican como notificaciones | `ApprovalService` · `NotificationService` | #100, #197 |
| **Aprobar y rechazar rechazan un pedido que tiene otro admin** (`409 ITEM_ALREADY_CLAIMED`); uno libre se resuelve, que es el camino normal de `/admin/requests` —esa pantalla no tiene forma de reservar— | `ApprovalService.approve` · `.reject` · `AdminQueueClaimRule` | #100, #176 |

### Mesa

| Regla | Dónde | Ref. |
|---|---|---|
| **`max_players` tiene un tope de plataforma** desde F3.5: `tables.max_players_cap` en `system_settings`, arranca en 12, y por encima es `400 MAX_PLAYERS_ABOVE_CAP` con el tope en los parámetros. Antes no había ninguno —solo `@Positive`—, así que un ajuste sobre un límite inexistente no habría hecho nada. Gatea **lo que se escribe**, nunca lo guardado: bajarlo no achica una mesa existente, y lo que se rechaza es su próxima edición. Se aplica en un solo punto por el que pasan las tres puertas (`create`, `createUnassigned` y `update`) | `GameTableService` | #24, #141, #265 |
| Máquina de estados: `Draft` → `Preparation` (la envía el master) o `Unassigned` (la crea un admin) → `ChangesRequested` → `Opened` → `InProgress` → `PauseRequested` → `Pause` → `Finished`/`Canceled`. Toda transición no declarada devuelve `409` | `GameTableService` | #27, #32, #72, #245 |
| Una mesa creada por un admin nace `Unassigned` y, al asignarle masters, pasa directo a `Opened` sin revisión | `GameTableService` | #72 |
| Exactamente un `Primary` vivo por mesa | `MasterService` | #71, #73 |
| **La pertenencia mira `masters.status`**: una fila `Deleted` no autoriza nada. Es lo que hace que quitar a un co-master le saque el acceso en el momento, sin borrar el registro de que dirigió la mesa | `MasterService.isMasterOf` · `.isPrimaryOf` · `.findByGameTable` | #135, #175, #216 |
| Solo el `Primary` agrega, promueve y quita masters; **al `Primary` no se lo puede quitar** —primero se le pasa la mesa a otra persona—, y quitarlo marca la fila, nunca la borra | `MasterService.removeMaster` | #73, #175, #216 |
| Volver a agregar a alguien que fue quitado **revive su fila** en vez de insertar una segunda: la clave primaria es `(game_table_id, user_id)` | `MasterService.addOrPromote` | #175, #190, #216 |
| `Pause` y `Canceled` exigen justificación, que se registra en `table_status_changes` | `GameTableService` | #32 |
| La pausa pedida por un master no aplica hasta que un admin la aprueba (`approval_requests`). La pide **cualquier master** —pedir no es decidir—, mueve `InProgress → PauseRequested` y crea el pedido **en una transacción**; aprobar la lleva a `Pause` con la nota de resolución como justificación, y **rechazar la devuelve a `InProgress`** o queda varada | `ApprovalService.submitTablePause` · `GameTableService.markPauseRequested` · `.applyApprovedPause` · `.revertRequestedPause` | #32, #42 |
| **`PauseRequested` sigue reservando horario**: nada se otorgó todavía, así que sus franjas cuentan como choque y la mesa sigue apareciendo en `/my/tables` | `ScheduleConflictService` · `GameTableService.LIVE_MINE_STATUSES` | #32, #178 |
| `Pause` congela la agenda: las sesiones pendientes dejan de aparecer. Al retomar hay que reagendar | `TableSessionService` | #32, #33 |
| **Reanudar vuelve a verificar el choque del `Primary`**: si la franja ya no está libre, responde `409` con el nombre de la otra mesa y no reanuda | `GameTableService.resume` | #178, #193 |
| Al entrar en `Finished` o `Canceled` se sella `closed_at`, que arranca la ventana de visibilidad. **Se sella una sola vez**: si ya tiene fecha, ninguna transición posterior la mueve | `GameTableService` | #44, #180 |
| El texto enriquecido (`description`, `permitted`, `requirements`) se sanitiza **al guardar y al servir**, con lista blanca | `RichTextSanitizer` | #62, #186 |
| Un master edita su propia mesa solo en `Draft` y `ChangesRequested` — **no** en `Preparation`, donde un admin la está leyendo; el `PUT` **reemplaza la mesa entera**, un campo ausente vacía | `GameTableService.update` | #189, #245 |
| La agenda y los vínculos de catálogo se **reemplazan como conjunto** y sus filas se marcan, nunca se borran: la clave primaria incluye el valor, así que sacar y volver a poner tiene que ser un `UPDATE` | `TableScheduleService` · `TableCatalogService` | #190 |
| **Un master no puede tener dos mesas vivas con agendas solapadas**. Se compara intervalo contra intervalo —`[hourtime, hourtime + duration)` con la `duration` **de cada franja** (#228), en UTC, semiabierto y con envoltura semanal—, no `weekday`+`hourtime` exacto | `ScheduleConflictService` | #178 |
| Dos filas de `table_schedules` **de la misma mesa** no pueden solaparse entre sí. Responde `400`, no `409`: es una semana que no se puede jugar, no un choque con el estado de nadie | `TableScheduleService` | #178, #187 |
| Una mesa `Unassigned` no tiene master contra quien medir R1: la verificación se difiere al momento de asignarle masters | `GameTableService.assignInitialMasters` | #72, #178 |
| Una franja sin `duration`, o una mesa sin agenda, no ocupa ningún intervalo y por lo tanto nunca choca | `ScheduleConflictService` | #178, #228 |
| **`Pause` no reserva horario**: congela la agenda, así que sus franjas no cuentan como choque. Al reanudar se reagenda y se vuelve a verificar | `ScheduleConflictService` | #32, #178 |
| Editar la agenda de una mesa ya poblada **avisa** al master a quiénes les genera choque; no expulsa a nadie | `TableScheduleService` | #70, #178 |
| **Una mesa se borra solo si nunca fue pública** (`Draft`/`Unassigned`/`Preparation`/`ChangesRequested`) **y no tiene postulaciones activas**; lo demás se cancela. El borrado es lógico y arrastra `masters` y `table_registrations` con la misma marca de tiempo | `GameTableService.delete` | #25, #175 |
| Una mesa `Deleted` no existe para ninguna lectura: detalle y listados responden `404` o la omiten | `GameTableService` | #25, #175 |
| **Aprobar y pedir cambios rechazan una mesa que tiene otro admin** (`409 ITEM_ALREADY_CLAIMED`); una mesa libre se aprueba sin pasar por la bandeja. Los endpoints siguen siendo del agregado mesa; lo que se mudó a `/admin/queue` es la pantalla | `GameTableService.approve` · `.requestChanges` · `AdminQueueClaimRule` | #100, #176 |
| `/admin/tables` lista **todos los estados menos `Deleted`** y acepta `?q=` con seis comandos. `/table_master` se resuelve con un `exists` sobre `masters` y **nunca con un join** —una mesa con tres masters se duplicaría y rompería el `count`— y filtra solo filas vivas | `GameTableService.listForAdmin` · `GameTableSearchSpecification.forAdmin` | #164, #176, #216 |

### Postulaciones

| Regla | Dónde | Ref. |
|---|---|---|
| Como máximo una postulación activa (`Candidate` o `Player`) por par mesa/usuario | `RegistrationService` | #28 |
| Los candidatos se atienden en orden de llegada (FIFO por `created_at`), sin reordenar por otro criterio | `RegistrationService` | #28 |
| Solo `Player` cuenta contra `max_players`; un candidato no reserva cupo | `RegistrationService` | #28 |
| Al aceptar al jugador que completa `max_players`, el resto de los `Candidate` pasa a `Rejected` con el código `TABLE_FULL`, y se notifica. **El motivo que escribe un master se muestra verbatim; el que escribe el sistema es un código y se traduce** — `rejected_by IS NULL` distingue los dos | `RegistrationService` | #34, #197 |
| Todo `Rejected` genera su fila en `registration_rejections` | `RegistrationService` | #28 |
| Un master solo puede postularse si además tiene el rol `Player` | `RegistrationService` | #73 |
| El veto lo aplica el `Primary` —`isPrimaryOf`, no `isMasterOf`—; un `Secondary` lo pide vía `approval_requests` y **el `Primary` lo resuelve**, no un admin. Un `Secondary` que llama al endpoint directo recibe `403 NOT_PRIMARY_MASTER`: es quién sos, no qué mandaste | `RegistrationService.block` · `ApprovalService.requireMayResolve` | #39, #71 |
| El veto es reversible; veto y levantamiento quedan registrados en `registration_status_changes` con motivo obligatorio. **Levantarlo devuelve a la persona a donde estaba**, leído del `from_status` del último cambio a `Blocked` — no a `Player` por defecto | `RegistrationService.block` · `.unblock` | #39 |
| **`PlayerBan` no entra en `/admin/queue`** —la bandeja es trabajo del admin y un veto no lo es— pero **sí se ve en `/admin/requests`**, que es el registro de todos los pedidos. Ver no es resolver | `AdminQueueService.NOT_FOR_ADMINS` · `ApprovalSearchSpecification.forAdmin` | #39, #90, #100 |
| **Filtro de visibilidad**: toda lectura de una mesa concreta pasa por **un solo punto** y excluye aquellas donde el usuario tenga una postulación `Blocked`. El detalle por id responde `404`, **nunca `403`** — un `403` confirma lo que el `404` niega. El explorador lo filtra con un `NOT EXISTS` **en el `WHERE`**, nunca descartando filas de la página | `TableVisibilityService.requireVisible` · `GameTableSearchSpecification.forExplorer` | #29 |
| **Lo que una mesa comparte deja de leerse por quien está vetado en ella**, con el mismo `404`. Es el agujero que #206 dejó anotado: `!link.isPrivate()` a secas devolvía el archivo a cualquier autenticado | `FileService.requireReadable` | #29, #206 |
| **Una fila `Blocked` se sigue viendo del lado del master**, con quién vetó, cuándo y por qué; no viaja nada de eso a la persona vetada, que ya no ve la mesa. `/registrations/mine` **excluye `Blocked`** igual que `Deleted`: esa fila lleva `gameTableName` | `RegistrationService.listPlayersForTable` · `.listMine` | #29, #39 |
| **Volver a postularse a la misma mesa es imposible sin regla nueva**: `POST /game-tables/{id}/registrations` pasa por el mismo punto único y responde `404` | `RegistrationService.apply` | #29 |
| Un usuario con `status = 'Blocked'` no puede postularse ni loguearse | `RegistrationService` | — |
| **No se puede postular a una mesa que choca de horario con otra donde ya se es `Player`.** Dirigir y jugar cuentan igual: los compromisos de una persona son sus mesas como master y como jugador | `RegistrationService` | #178 |
| **No se puede aceptar a un candidato que ya es `Player` en una mesa que choca.** Se verifica al aceptar y no solo al postularse: el estado puede haber cambiado entre las dos | `RegistrationService` | #178 |
| Al aceptar a alguien, sus otras postulaciones `Candidate` que chocan **se notifican, no se rechazan**: hasta que lo aceptan en una no hay compromiso y elegir es suyo | `RegistrationService` | #70, #178 |
| **Se puede retirar la propia postulación** mientras esté en `Candidate`. Es el borrado lógico de `table_registrations` que la notificación de choque exige poder resolver. Ya aceptada no: eso se habla con el master | `RegistrationService` | #175, #178 |
| Una postulación `Deleted` —retirada o arrastrada por la mesa— no aparece en ninguna lectura | `RegistrationService` | #25, #175 |
| Los archivos de una postulación se **vinculan, nunca se copian**, y pasan por el mismo permiso que adjuntar a una mesa: propios o publicados. El vínculo archiva el cajón `PlayerApplication` | `RegistrationService` · `FileService.classify` | #60, #79, #233 |
| **Retirar una postulación no arrastra sus archivos**: `registration_files` es el registro de qué se mandó y retirarse no lo deshace. Lo que la cascada no hace lo hacen las lecturas — un vínculo cuya postulación no está viva **no cuenta como uso**, así que el archivo vuelve a estar al alcance de la purga | `FileService.usagesByFileId` · `FileRetentionService` | #75, #232, #247 |
| **Una postulación enviada no se edita**: no hay `PUT` ni forma de quitarle un archivo. Lo que se puede es retirarla | `RegistrationService` | #238, #247 |

### Sesiones y peticiones

| Regla | Dónde | Ref. |
|---|---|---|
| Las sesiones se materializan al pasar a `Opened`, a partir de `start_date` + `table_schedules` + `total_sessions` | `TableSessionService.materialize` | #26, #33 |
| **La hora de cada sesión sale siempre de `table_schedules.hourtime`, nunca de `start_date`**, que solo dice desde qué día buscar. Una franja que cae el mismo día de inicio cuenta, a cualquier hora | `TableSessionService.materialize` | #230 |
| Si falta `start_date`, la agenda o `total_sessions`, la mesa **abre igual, con cero sesiones**. Materializar es consecuencia de abrir, no requisito | `TableSessionService.materialize` | #196 |
| Materializar es **idempotente**: una mesa que ya tiene calendario conserva el que tiene | `TableSessionService.materialize` | #33 |
| Se puede corregir la fecha de una sesión suelta. Es una corrección de esa noche y **no** pasa por el choque de #178, que compara semanas y no instantes | `TableSessionService.update` | #33 |
| **Marcar una sesión como jugada es una acción propia**, separada de registrar la asistencia. Ninguna de las dos implica la otra | `TableSessionService.hold` | #195 |
| Una sesión ya `Held` o `Cancelled` no se corrige: es el registro de algo que pasó o que no pasó | `TableSessionService` | #33 |
| **Cancelar una sesión repone otra al final**: la cancelada conserva su `sequence_number` y se agrega una nueva en la primera franja de la agenda posterior a la última en pie. Sin agenda viva no hay reposición | `TableSessionService.cancel` | #194 |
| `Pause` **oculta** las sesiones pendientes en toda lectura, también para el master. No se marcan filas: la pausa es reversible | `TableSessionService` | #32, #33 |
| Al reanudar, las pendientes se vuelven a tender desde la fecha de reanudación con la agenda actual, conservando su numeración. Lo jugado y lo cancelado no se toca | `TableSessionService.rescheduleAfterPause` | #32, #33 |
| Mover una fecha o cancelar una sesión **avisa** a candidatos y jugadores. Nadie es removido | `TableSessionService` | #70, #77 |
| La asistencia se registra por sesión y alimenta la evaluación de karma | `TableSessionService.recordAttendance` | #36 |
| El padrón de una sesión son los `Player` activos de la mesa, resuelto en el servidor: un `userId` que no juega ahí es `400` | `TableSessionService.recordAttendance` | #121 |
| La asistencia histórica se **deriva con `GROUP BY` y no se cachea**, con `Unknown` fuera del denominador y los tres números sin colapsar | `TableSessionService.summarize` | #11, #137 |
| **Crear una petición es publicarla**: no hay borrador, y la creación notifica a sus destinatarios en la misma transacción | `TableTaskService.publish` | #77 |
| Corregir una petición **no vuelve a notificar**. El aviso sale una sola vez | `TableTaskService.update` | #77 |
| La audiencia resuelve a personas en un solo lugar, y de ahí salen las tres respuestas: a quién se notifica, quién puede entregar y quién figura como faltante | `TableTaskService.recipientsOf` | #63 |
| `audience = 'Single'` exige `target_user_id`, y cualquier otra audiencia lo prohíbe. El destinatario tiene que ser `Player` vivo de la mesa | `TableTaskService` | #63, #76 |
| Una petición acepta texto, archivos o ambos: **al menos uno**. El `CHECK` de la base es la red, no la regla | `TableTaskService` | #63 |
| Una petición atada a una sesión solo puede atarse a una sesión **de su propia mesa** | `TableTaskService` | #63 |
| `is_mandatory` es **informativo**: ninguna ruta de código lo lee para bloquear, expulsar ni rechazar | todo `tasks/` | #70 |
| Las peticiones de audiencia `Candidates` las lee **cualquiera que pueda ver la mesa**, aunque todavía no se haya postulado: lo que te van a pedir es parte de decidir si te postulás | `TableTaskService.listApplicable` | #63, #206 |
| Una petición `Single` solo la ve su destinatario, también entre los jugadores de la mesa | `TableTaskService.listApplicable` | #76 |
| **Las entregas se acumulan, nunca se reemplazan**: cada envío es una fila nueva y no hay `update`. El sistema no juzga si cumplen, y no hay aprobar ni rechazar | `TaskSubmissionService.submit` | #76 |
| Entregar exige estar **en la audiencia** de la petición, y que siga `Open` (`409 TASK_CLOSED`) | `TaskSubmissionService.submit` | #63, #76 |
| Una entrega tiene que usar un canal que la petición abrió, y no puede ir vacía de los dos | `TaskSubmissionService.submit` | #63 |
| Los archivos de una entrega se **vinculan**, nunca se copian, y pasan por el mismo permiso que adjuntar a una mesa: propios o publicados | `TaskSubmissionService.submit` | #65, #79 |
| Un master puede abrir un archivo que le entregaron a una petición de su mesa — la quinta vía de lectura | `FileService.requireReadable` | #206, #211 |
| El incumplimiento se avisa y queda visible para el master, pero **no bloquea ni expulsa**: el padrón de faltantes no tiene ninguna acción asociada | `TaskSubmissionService.listForTask` | #70 |
| Cerrar una petición corta las entregas nuevas y **no borra** lo ya entregado | `TableTaskService.close` | #76 |

### Comentarios y karma

| Regla | Dónde | Ref. |
|---|---|---|
| Solo una mesa `Finished` habilita comentar; una `Canceled` no genera comentarios ni karma | `CommentService` | #46 |
| El comentario se escribe como borrador y se confirma al cerrar la mesa; ahí se crea la fila anónima y se borra el borrador | `CommentService` | #48, #49 |
| Un comentario por autor sobre la misma persona por mesa, verificado con el token de `comment_quotas` | `CommentService` | #35, #82 |
| Solo puede comentar quien coincidió con el comentado en la mesa, validado contra la asistencia. **La evidencia se usa y no se persiste** | `CommentService` | #31, #36 |
| El borrador no confirmado expira, con dos avisos previos, y se le purgan autor y contenido | `CommentService` | #50, #52 |
| Todo comentario confirmado pasa por moderación de un admin, que ve contenido y destinatario pero nunca al autor | `CommentModerationService` | #51 |
| El karma se mueve solo con los comentarios aprobados | `CommentModerationService` | #51 |
| **Fórmula**: `karma = 10000 × (W·m + Σ wᵢ·vᵢ) / (W + Σ wᵢ)` con `m=0.8`, `W=20`, `wᵢ=2^(−antigüedad/12 meses)`; `v` vale `1.0` positivo, `0.8` neutro, `0.0` negativo | `KarmaService` | #96 |
| El **neutro vale igual que el prior**: no mueve el promedio pero acumula confianza y amortigua un negativo aislado | `KarmaService` | #96 |
| `users.karma` es una **proyección cacheada**, no la fuente de verdad. La fuente son las filas de `comments` | `KarmaService` | #97 |
| Se recalcula al aprobar un comentario (solo esa persona) y en un **job semanal nocturno** (todos) | `KarmaService` | #97 |
| La asistencia **no** entra en el cálculo: se muestra como métrica aparte | `KarmaService` | #98 |
| Se muestra el puntaje y los comentarios recibidos, sin desglose agregado. El master ve lo mismo que el dueño del perfil | `UserService` | #99 |
| Llegar a 0 o 10000 no dispara ninguna acción automática | `CommentModerationService` | #74 |
| El feedback del sistema vive en `system_feedback`, es anónimo y no guarda autor en ningún momento. Sin destinatario, sin karma y sin mesa | `FeedbackService` | #91, #93 |
| Uno cada 24 h reales: se comprueban los tokens de las últimas 24 franjas horarias en `feedback_quotas` | `FeedbackService` | #94 |
| Las filas de `feedback_quotas` se purgan pasadas 24 h; el token rota por hora y nunca es un identificador estable del usuario | `FeedbackService` | #94 |
| No pasa por moderación: va directo a la bandeja de lectura de admins y owner | `FeedbackService` | #95 |

### Visibilidad de perfiles

| Regla | Dónde | Ref. |
|---|---|---|
| El perfil de un master es visible para cualquiera que mire su mesa | `UserService` | #41 |
| El perfil de un jugador se abre para el master desde que recibe su postulación | `UserService` | #41 |
| Los jugadores de una mesa ven los perfiles de sus compañeros | `UserService` | #47 |
| La visibilidad caduca a los N días de `closed_at`, con N en `system_settings` (`profiles.visibility_window_days`, arranca en 14). En `Pause` el reloj no corre. **El cambio es retroactivo y no hay recálculo**: la regla se evalúa en cada lectura, así que bajarlo le quita la visibilidad a quien la tiene en ese instante y subirlo se la devuelve a quien ya la había perdido — por eso la pantalla de configuración avisa antes de guardar | `ProfileVisibilityService` · `SettingsService` | #44, #141 |
| El admin no tiene restricciones de visibilidad, salvo la autoría de los comentarios | `UserService` | #45 |

### Catálogos

| Regla | Dónde | Ref. |
|---|---|---|
| Los grupos son de sinónimos, profundidad 1: un alias apunta al canónico, nunca a otro alias | `CatalogService` | #59 |
| Buscar por cualquier miembro devuelve las mesas etiquetadas con cualquier otro del grupo. **La equivalencia es simétrica y plana**: dos alias del mismo grupo se encuentran entre sí sin pasar por ser el canónico | `AbstractCatalogService.resolveGroupIdsByName` · `GameTableSearchSpecification` | #54, #56, #59 |
| **Un criterio que no resolvió a ningún valor aceptado no coincide con nada**, nunca con todo: leerlo como «sin filtro» convierte un error de tipeo en el listado completo de la plataforma | `GameTableSearchSpecification` | #246 |
| **El buscador solo acota lo que el lector ya podía ver**: las reglas de visibilidad y los criterios se unen con `and` y nunca se pliegan en la misma expresión, así que un `/or` entre dos criterios no puede cruzar el filtro | `GameTableSearchSpecification.forExplorer` | #121, #154, #246 |
| Masters y admins proponen valores; solo un admin acepta y clasifica | `CatalogService` | #55 |
| **Un admin que crea un valor desde `/admin/catalogs` no propone: el valor nace `Accepted` y canónico**, como grupo propio. Un nombre ya tomado, sin importar mayúsculas, es 409 | `AbstractCatalogService.create` | #280 |
| Un valor en `Created` no filtra ni se muestra a los jugadores; al aceptarse, sí | `CatalogService` | #57 |
| La mesa muestra siempre el alias que le puso su master | `CatalogService` | #58 |
| Dar de baja un valor no rompe vínculos: las lecturas lo saltan por estado y restaurarlo devuelve todo | `CatalogService` | #81 |
| Dar de baja el canónico de un grupo con alias vivos **es** cambiar el canónico: exige sucesor, y lo elige el admin | `CatalogService` | #55, #59, #81 |
| Una mesa puede vincular un valor en `Created` —su master lo acaba de proponer— pero no uno `Rejected` ni `Disabled`. Dar de baja un valor **no** rompe los vínculos que ya tenía | `TableCatalogService` | #57, #81 |
| **Mover un alias de grupo es una sola operación** (`reassign`): solo mueve un alias —un canónico arrastra su grupo y eso es `merge`—, y el destino tiene que ser un canónico `Accepted` distinto del grupo actual | `AbstractCatalogService.reassign` | #59, #276 |
| **Hacer principal a un alias da vuelta el grupo sin sacar a nadie** (`promote`): el alias queda canónico y el canónico anterior y los demás alias pasan a apuntarle, en una transacción. Solo un alias `Accepted`; a diferencia de dar de baja el canónico (#183), nada sale de circulación | `AbstractCatalogService.promote` | #59, #276 |
| El listado de admin por grupos (`?groupsOnly=true`) trae solo cabezas de grupo —canónicos y propuestas sin clasificar— y encuentra un grupo por el nombre de **cualquiera** de sus miembros, en SQL para que la paginación siga en la base | `CatalogSearchSpecification.groupsForAdmin` | #54, #275 |

### Archivos

| Regla | Dónde | Ref. |
|---|---|---|
| Nombre físico = un id generado por el servidor; el nombre original es solo metadato y nunca toca el sistema de archivos | `LocalDiskStorageService` | #80 |
| El usuario ve y reutiliza todo lo que subió; vincular no duplica | `FileService` | #65 |
| Un master puede usar un archivo `Public` como requisito sin copiarlo; quitarlo de la mesa no borra el archivo global | `TableFileService` | #79 |
| Se purgan los archivos sin vínculo vivo y con ~3 meses sin uso. Los `Public` quedan exentos, y el marcado no libera bytes | `FileRetentionService` | #75, #66 |
| Se deduplica por `content_hash` **dentro del mismo dueño** —`uk_files_storage_key` prohíbe que dos filas compartan blob— y se comprime con gzip al guardar. **Reconocer una subida responde 200 y no 201**, y la fila reconocida conserva su categoría | `FileService` · `LocalDiskStorageService` | #75, #234, #235 |
| **Dónde está vinculado ahora se deriva** —tres consultas agrupadas por página, una por tabla puente— y **dónde perteneció alguna vez se guarda**. Despegar un archivo saca el uso y deja el cajón. Un archivo sin ningún uso reporta vacío, que es el aviso de que la purga lo va a alcanzar primero | `FileService.usagesByFileId` | #232, #233 |
| Los cajones que alguien puede usar en **su** biblioteca salen de sus roles y de las mesas que dirige; `Announcement` no es de nadie y solo vive en la de la plataforma | `FileService.personalCategoriesOf` | #237, #38, #135 |
| **El cajón lo pone el vínculo, no quien sube**: adjuntar a una mesa, responder una petición, adjuntar un formulario a un pedido. El único lugar que pregunta es `/my/files`, que no tiene flujo que observar. Idempotente y **add-only**: nada revoca una pertenencia | `FileService.classify` | #233 |
| **En la biblioteca de la plataforma subir es publicar**: cada archivo dice qué es en su fila de `/admin/files/upload` (#279) y la API exige al menos un cajón y acepta varios —la misma hoja sirve al armar la mesa y al pedir algo después—, y **rechaza los dos del lado jugador** antes de guardar un byte: ahí van las respuestas de cada uno, y una plantilla pública no es una respuesta. No hay publicar un archivo ya subido | `FileService.uploadPublished` | #233, #278, #279 |
| `/admin/files` es **solo lo publicado**: el `fileType = Public` va en el `WHERE`, filtrar por un cajón del lado jugador es 400, y dar de baja un archivo privado de alguien es 404 — lo privado se alcanza actuando como esa persona, no desde la biblioteca | `FileService.listForAdmin` · `FileService.deleteAsAdmin` | #278 |
| Un master adjunta formularios a la petición que escribe, y los abre quien la petición alcanza —candidato o jugador—, no solo quien dirige | `TableTaskService.syncBlanks` · `FileService.requireReadable` | #236, #63 |
| La búsqueda de «mis archivos» filtra de verdad: el frontend mandaba `q` y el endpoint no tenía parámetro que lo recibiera | `FileService.listMine` | #65 |
| El contenido se escribe a un área de staging y se confirma al commit de la transacción; un rollback no deja huérfanos | `LocalDiskStorageService` | M26.2 |
| Publicar solo lo hace un admin; el archivo sigue siendo de quien lo subió. ~~La audiencia~~ la reemplazó el cajón (#233) | `FileService` | #55, ~~#64~~ |
| Lo que una mesa comparte lo lee cualquiera que pueda ver la mesa; lo privado, solo quien la dirige. **Al llegar el veto (F3) hay que excluir al vetado acá también** | `FileService` | #17, #29, #121 |
| El borrado físico lo ejecuta el owner desde el menú de administración | `StorageService` | #66 |
| `comments` y `comment_drafts` **nunca** se auditan | `AuditService` | #43 |

### Bandeja compartida de admins

Los ítems de trabajo de admin **no se duplican como notificaciones**: la bandeja es una vista sobre el trabajo pendiente que ya vive en sus tablas.

| Regla | Dónde | Ref. |
|---|---|---|
| La bandeja une `approval_requests` (`Pending`), `comments` (`Under review`), `system_feedback` (`New`) y `game_tables` (`Preparation`), normalizado a un DTO común. **`Draft` nunca entra**: nadie la envió todavía. **`ChangesRequested` tampoco** — la pelota está en el master. **`Unassigned` tampoco**: le falta un master, no una revisión | `AdminQueueService` | #100, #245 |
| El `UNION ALL` de #100 se construye como **una consulta por fuente y el merge en Java**, no como SQL nativo: no hay un solo `nativeQuery` en el proyecto, y el precedente propio del mismo problema es `MasterDashboardService`. Sumar la fuente de F5 es escribir un método privado más | `AdminQueueService` | #100, #11 |
| Cada fuente trae como mucho **200 filas**, y tocar el techo se loguea en `WARN` con el nombre de la fuente: un merge sin tope es una carga de memoria que nadie declaró | `AdminQueueService` | #100 |
| La bandeja se ordena por **el que espera hace más tiempo primero**, con desempate por id, y pagina con el total exacto — el merge ya está en memoria. **No lleva `?q=`**: la pantalla que busca es `/admin/tables` | `AdminQueueService` | #136, #171, #173, #176 |
| Un ítem reservado desaparece de la bandeja del resto, pero sigue visible para quien lo reservó: el filtro es `claimed_by IS NULL OR claimed_by = :actual` | `AdminQueueService` | #100 |
| Reservar es idempotente para el mismo admin —responde `200` y **no mueve `claimed_at`**— y si ya lo tiene otro responde `409 ITEM_ALREADY_CLAIMED`. Se toma bajo el lock de la fila: es un check-then-act y la carrera es real | `AdminQueueService` | #100, #252 |
| Devolver lo propio es `204`; devolver algo sin reserva también (el estado pedido ya se cumple); devolver lo de otro es `409 ITEM_ALREADY_CLAIMED`. Devolver no le pregunta al estado del ítem: es el deshacer | `AdminQueueService` | #100 |
| **Resolver un ítem que tiene otro admin se rechaza** con `409 ITEM_ALREADY_CLAIMED`; uno que no tiene nadie se resuelve, y resolverlo **es una reserva implícita**. Los cuatro caminos: `ApprovalService.approve` / `reject` y `GameTableService.approve` / `requestChanges`, con la regla en un solo lugar porque cuatro copias es cómo una se queda atrás. **No es «exige tenerlo reservado»**: ver la fila de abajo | `AdminQueueClaimRule` | #100 |
| **Por qué la regla no es más estricta.** Esta tabla decía «resolver un ítem exige tenerlo reservado» y se implementó así en F3.3, y dejó `/admin/requests` inutilizable: esa pantalla —que #176 le dio a los pedidos— trae Aprobar y Rechazar y **ninguna forma de reservar**, así que toda resolución respondía `409` por una reserva que no podía ofrecer. Chocaron dos diseños: #176 le da a los pedidos pantalla propia, y esta sección asumía que la bandeja era el único lugar donde algo se resuelve. Lo que #100 compra es «si lo toma uno, baja para todos», y eso solo pide rechazar lo que tiene **otro**. **La consistencia nunca dependió de esto**: dos admins resolviendo la misma fila sin reservar se serializan con el lock pesimista y el segundo recibe «ya estaba resuelto» (#256) | `AdminQueueClaimRule` | #100, #176, #256 |
| Un job libera cada minuto las reservas con más de N minutos. **Desde F3.5 el número sale de `system_settings`** (`admin_queue.claim_timeout_minutes`, arranca en 15 y se mueve entre 1 y 1440); ya no es `app.admin-queue.claim-timeout`, que se borró. Se lee en cada pasada, nunca al arrancar. Solo toca ítems que siguen esperando: uno ya resuelto conserva su `claimed_by` como registro | `AdminQueueClaimReleaseService` · `SettingsService` | #100, #141 |
| Todo cambio en la bandeja emite `admin-queue.changed` por WebSocket a los suscriptores con rol `Admin` u `Owner`. **Hasta F6 lo reemplaza un `refetchInterval` de 15s en el frontend** | `NotificationService` | #101 |

### Notificaciones

| Regla | Dónde | Ref. |
|---|---|---|
| Las notificaciones personales son una fila por destinatario en `notifications`, con estado leído/no leído | `NotificationService` | #14 |
| Una notificación guarda **tipo + parámetros**, nunca la frase. El texto lo arma quien la lee, en su idioma. Las filas anteriores a #197 conservan su texto congelado y se muestran así | `NotificationService` · `notificationText.ts` | #197, #198 |
| Se entregan en tiempo real por WebSocket+STOMP a `/user/queue/notifications`; la bandeja de admins va a `/topic/admin-queue` | `NotificationService` | #101 |
| El mensaje que viaja es una **señal de invalidación**, no el contenido: el cliente refetchea con TanStack Query | `NotificationService` | #101 |
| La suscripción a `/topic/admin-queue` se rechaza si el usuario no tiene `Admin` u `Owner` | `WebSocketConfig` | #101 |
| Una notificación de comentario recibido **nunca** nombra a su autor | `NotificationService` | #43 |

### Configuración

| Regla | Dónde | Ref. |
|---|---|---|
| **El catálogo de ajustes es código, no datos**: `SettingKey` fija la clave, la categoría, el valor por defecto, el rango y si el cambio es retroactivo. `system_settings` guarda **solo overrides**, así que una base recién vaciada y una recién creada se comportan igual y cambiar un default no es una migración | `SettingKey` | #141, #263 |
| **Lo dinámico no cruza la frontera HTTP** (regla dura 3): el almacenamiento es genérico y el service expone **accesores tipados** —`maxFileSizeBytes()`, `maxPlayersCap()`, `claimTimeout()`, `profileVisibilityWindowDays()`—, cada uno en la unidad que el llamador necesita. Nadie afuera sabe que el número está guardado en megabytes, como texto, en una fila que puede no existir | `SettingsService` | #141 |
| Cada ajuste se valida contra **su propio rango** en el service y no con un `@Max` en el `record`: el límite pertenece a la clave, y la clave viaja en la URL. Fuera de rango es `400 SETTING_OUT_OF_RANGE` **con los dos números** en los parámetros | `SettingsService` | #141, #197 |
| **Cada cambio deja su fila** en `system_setting_changes`, con motivo obligatorio. Un cambio de configuración no emite notificación ni se ve en ningún lado: el motivo es todo el registro que queda | `SettingsService` | #141 |
| Los valores se cachean en Caffeine (`systemSettings`) y **la caché se limpia en `afterCommit`**, nunca dentro de la transacción: limpiarla antes abre la ventana en que otro request la repuebla con el valor viejo y lo deja vivo todo el TTL. Es la lección de #128 aplicada de nuevo | `SettingsService` | #128, #141 |
| Una fila con un valor que no es número **cae al default en vez de tirar**: estos accesores están en el camino caliente —cada subida, cada lectura de perfil— y una fila editada a mano no puede voltear esos endpoints | `SettingsService` | #141 |
| El techo de `files.max_file_size_mb` tiene que quedar **estrictamente por debajo** de `spring.servlet.multipart.max-file-size`, o el contenedor rechaza la subida antes de que la aplicación pueda explicarla (#197). La aplicación **no arranca** si dejan de estar en ese orden | `SettingsService` | #141, #197 |
| **Ningún secreto vive acá**: el HMAC de #94 y las credenciales siguen en el entorno | — | #141 |
| **Editar la configuración es una capacidad que `Admin` y `Owner` comparten**, como toda la superficie de administración de F1–F3 | `AdminSettingsController` | #169 |

### Auditoría

| Regla | Dónde | Ref. |
|---|---|---|
| Se registra qué entidad cambió, su id, quién y cuándo. No se registra navegación ni clics | `AuditService` | #92 |
| `before_data` y `after_data` guardan **solo las columnas que cambiaron**, no la fila completa | `AuditService` | #92 |
| La consulta del historial de auditoría es exclusiva del rol `Owner` | `AuditService` | #92 |
| **Excepción deliberada**: `comments` y `comment_drafts` quedan fuera de la auditoría. Auditarlos guardaría autor y contenido juntos y anularía el anonimato | `AuditService` | #43, #92 |
