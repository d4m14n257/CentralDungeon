---
adr: 16
titulo: Pedidos con aprobación y bandeja de admins
estado: Vigente
actualizada: 2026-10-20
absorbe: [42, 78, 90, 100, 126, 176, 255, 256, 257, 258, 259, 260]
---

# ADR-16: Pedidos con aprobación y bandeja de admins

> El mecanismo único de solicitud y aprobación (`approval_requests`) y la bandeja compartida donde los admins toman el trabajo pendiente. Las reglas de código, el DDL y la regla de reserva están en `arquitectura` §4.5. La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 Pedir y aprobar es un solo mecanismo, con justificación obligatoria

**Decisión.** Todo pedido dirigido a quien tiene más autoridad pasa por `approval_requests`: alguien pide con **justificación obligatoria**, alguien aprueba o rechaza. Cinco tipos: `MasterGrant` (el rol de master permanente), `TableOpen` (que se abra una mesa sin ser master), `General` (peticiones al equipo), `TablePause` (el master pide pausar su mesa) y `PlayerBan` (un co-master `Secondary` pide vetar a un jugador). Los cuatro primeros los resuelve un admin; `PlayerBan` lo resuelve el `Primary` de la mesa y no un admin (ADR-09 §5), así que no entra en la bandeja y su autorización se decide por tipo dentro de `ApprovalService`. Un tipo entra al enum **junto con su productor**, nunca antes. `MasterGrant`, `TableOpen` y `General` apuntan al **solicitante** (`entity_type = 'user'`); `TablePause`, a la mesa; `PlayerBan`, a la inscripción. Las dos columnas de la referencia son `NOT NULL`.

**Por qué.** No es burocracia accidental: es el control que evita que los masters abran mesas sin freno o que decisiones fuertes se tomen solas. La forma se repite idéntica, así que se modela una vez (`arquitectura` §2.4), con una sola bandeja. `TableOpen` es el mismo circuito que la mesa `Unassigned` vista desde la otra punta (ADR-06 §7): un jugador la pide y un admin la crea y le asigna master. Declarar un tipo antes que su productor no compra nada —la columna es `VARCHAR(32)`— y deja un valor que nada produce. Apuntar al solicitante no es relleno: `TableOpen` pide una mesa que todavía no existe y `General` no es sobre ninguna entidad, pero el pedido *es* sobre esa persona.

*Antes: #42, #90, #255.*

## §2 La referencia a la entidad afectada es polimórfica y la integridad es del service

**Decisión.** `entity_type` + `entity_id`, sin FK real. En JPA no es un `@ManyToOne`: es un id suelto que el service resuelve según el tipo, y **el service valida que la entidad exista** antes de crear la fila, con test (regla dura 7). Un borrado de la entidad apuntada no arrastra sus solicitudes. Hace falta una verificación periódica de referencias huérfanas.

**Por qué.** Agregar un flujo es un valor más del enum, sin `ALTER TABLE`. Con una FK nullable por flujo, cada uno nuevo suma una columna a una tabla que ya nadie entiende, y la bandeja tendría que saber cuál mirar. El precio —la base no garantiza la integridad referencial, la clase de problema que el proyecto ya sufrió con `table_files` apuntando a archivos borrados— se asume porque `approval_requests` es un registro histórico: una solicitud sobre una mesa borrada sigue siendo un hecho que conviene conservar.

*Antes: #78, #126.*

## §3 `approval_requests` está en el baseline y no tiene migración propia

**Decisión.** La tabla ya está en `V1__baseline.sql`, con sus columnas, sus FK y sus índices; mapear la entidad no agrega ninguna migración. Antes de escribir una migración que crea una tabla se comprueba contra `V1`.

**Por qué.** El baseline contiene tablas de fases futuras, así que «está en el modelo de datos» no significa «hay que crearla». Una `CREATE TABLE` duplicada falla con `Table already exists` (42S01/1050), deja una fila `success = 0` en `flyway_schema_history` y tira abajo el arranque de contexto de toda la suite de integración a la vez: el síntoma parece código roto y es una tabla duplicada.

*Antes: #257.*

## §4 Pedir y resolver se serializan con un bloqueo pesimista que es la lectura del estado

**Decisión.** `ApprovalService.resolve` lee el pedido con `ApprovalRequestRepository.lockById` (`PESSIMISTIC_WRITE`), y **esa lectura es la lectura del estado**. `ApprovalService.submit` bloquea la fila del solicitante con `UserRepository.lockById` **como primera operación** de la transacción, antes de chequear pendientes. Una `DataIntegrityViolationException` se responde `409 CONFLICT`. Los tests unitarios stubean los métodos con bloqueo, no `findById`.

**Por qué.** `REQUEST_ALREADY_PENDING` y `REQUEST_ALREADY_RESOLVED` eran check-then-act y se rompían con concurrencia real contra MySQL: dos submits abrían dos pedidos, y aprobar y rechazar a la vez pasaban los dos, dejando `Rejected` a alguien que ya es `Master` y notificándole ambas cosas. Lo que parecía proteger a `MasterGrant` era una colisión accidental con la PK de `users_roles`, que respondía `500`. El orden es la parte que no se ve: bajo `REPEATABLE READ`, bloquear y después leer con una query plana devuelve el snapshot anterior a la espera, y un `findById` previo deja en el contexto de persistencia una instancia que el lock no refresca. Es lo mismo aprendido en ADR-06 §2. Stubear `findById` dejaría los tests en verde con el lock sacado.

*Antes: #256.*

## §5 La bandeja es una vista sobre las tablas donde el trabajo ya vive, con reserva

**Decisión.** El trabajo pendiente no se copia a notificaciones ni a una tabla `admin_queue`. `approval_requests`, `comments`, `system_feedback` y `game_tables` llevan `claimed_by`/`claimed_at`; el ítem reservado desaparece para el resto (`claimed_by IS NULL OR = :actor`) y una reserva sin resolver se libera sola pasado el timeout, 15 minutos por defecto y configurable (ADR-17 §3). Hoy las fuentes son los pedidos `Pending` dirigidos a admins (todos menos `PlayerBan`) y las mesas en `Preparation` —ni `Draft` (nadie la envió), ni `ChangesRequested` (la pelota está en el master), ni `Unassigned` (le falta master, no revisión)—; comentarios y feedback se suman cuando existan sus subsistemas. Una consulta por fuente con techo de 200 filas y `WARN` al tocarlo, merge y orden en el service —el que espera hace más tiempo primero, desempate por id— y paginación en memoria con total exacto (ADR-04 §6). Sin SQL nativo ni `UNION ALL`.

**Por qué.** Si la bandeja lee el estado real, «si la toma uno baja para todos» sale gratis; una copia habría que mantenerla sincronizada, la denormalización que ADR-03 §1 eliminó. Un union nativo sobre tablas de columnas distintas sería el primero del repositorio y habría que reescribirlo al sumar fuentes; sumar una es un método privado, como en `MasterDashboardService`. Lo que se une es trabajo pendiente, acotado por definición, pero un merge sin techo es memoria que nadie declaró. Sin timeout, un ítem reservado y abandonado quedaría invisible para siempre.

*Antes: #100, #259.*

## §6 Resolver un ítem no exige tenerlo reservado: exige que no lo tenga otro

**Decisión.** `AdminQueueClaimRule.requireNotHeldByAnother` pasa si nadie lo tiene o si lo tiene el actor, y responde **`409 ITEM_ALREADY_CLAIMED`** solo si lo tiene otro. Resolver algo libre es una reserva implícita. No existe `ITEM_NOT_CLAIMED`. Toda fila visible en la bandeja está libre o es del lector, así que sus acciones de resolver se muestran.

**Por qué.** Exigir la reserva volvió la bandeja obligatoria para flujos que no pasan por ella: `/admin/requests` resuelve sin ofrecer reservar y cada resolución respondía `409`. Lo que la reserva compra es no pisarle el trabajo a un colega —un link viejo, otra pestaña, una bandeja sin refrescar—; la consistencia no depende de ella, porque dos admins sobre la misma fila libre se serializan con el lock de §4 y el segundo recibe «ya estaba resuelto». Esconder una acción que funciona es el mismo pecado que ofrecer una que no.

*Antes: #258.*

## §7 «Por revisar» y «todas las mesas» son dos pantallas distintas

**Decisión.** **`/admin/queue`** es lo que pide una acción: compartida, con reserva, una lista que se vacía. **`/admin/tables`** es el listado de administración: **todas** las mesas en cualquier estado salvo `Deleted` por defecto, con filtros y buscador, sin reserva ni idea de «pendiente». Las filas de la lista no ofrecen aprobar ni pedir cambios; esas decisiones están en la bandeja y en el detalle de la mesa, con los mismos endpoints y la misma regla de reserva (ADR-21 §10).

**Por qué.** «¿Qué tengo que resolver?» se contesta con una lista corta y compartida para que dos admins no trabajen el mismo ítem. «¿Cómo va la mesa de Fulano?» se contesta con un listado completo donde lo que importa es encontrar. Un admin administra, y eso incluye mirar mesas que no requieren nada de él. No es un problema de permisos —el backend ya devuelve cualquier estado—, sino de qué muestra cada pantalla.

*Antes: #176.*

## §8 La bandeja se refresca sola cada 15 segundos hasta que llegue el tiempo real

**Decisión.** `refetchInterval` de 15 segundos más `refetchOnWindowFocus`, con el valor en `config/query.ts` junto a los `staleTime`, no suelto en el hook. Lo reemplaza el WebSocket (ADR-18 §2) cuando exista.

**Por qué.** A un admin le tiene que desaparecer **sin recargar a mano** el ítem que otro reservó, y el frontend solo refrescaba por invalidación tras una mutación propia. Es una decisión de comportamiento de la plataforma, así que vive donde se encuentra el día que haya que cambiarla. Un refetch periódico multiplica cualquier N+1: la consulta de pedidos lleva `join fetch`, o cada admin conectado genera tráfico constante.

*Antes: #260.*
