---
adr: 9
titulo: Postulaciones y veto
estado: Vigente
actualizada: 2026-10-20
absorbe: [23, 28, 29, 34, 39, 69, 149, 160, 213, 247]
---

# ADR-09: Postulaciones y veto

> Cómo se postula una persona a una mesa, cómo se atiende la cola de candidatos, qué pasa al llenarse el cupo y cómo funciona el veto. Las reglas que esto produce las aplica la skill `arquitectura` (`reglas-negocio.md` §Postulaciones). La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 Las postulaciones son un historial de intentos, con una sola activa y cola FIFO

**Decisión.** `table_registrations` **no** tiene `UNIQUE (game_table_id, user_id)`: un usuario puede postularse N veces a la misma mesa. Como máximo hay **una postulación activa** (`Candidate` o `Player`) por par mesa/usuario, así que la vigente es siempre la última; el reintento se habilita cuando la anterior quedó `Rejected`. Todo `Rejected` deja su fila en `registration_rejections`, con motivo obligatorio. La lista de candidatos se atiende **en orden de llegada** (FIFO por fecha de solicitud) y no se reordena por karma, antigüedad ni criterio del master. Solo `Player` cuenta contra el cupo: un candidato no lo reserva.

**Por qué.** Es el flujo real: se postula, el master rechaza con motivo, y la persona puede volver a intentarlo. El `UNIQUE` impedía el reintento y destruía el historial de rechazos, que es lo que `registration_rejections` existe para guardar. Acotar a una sola activa hace que "la última" y "la activa" sean la misma, sin desempatar por fecha. El FIFO es una regla de equidad explícita del negocio: todos tienen la misma oportunidad de entrar.

*Antes: #23, #28.*

## §2 Una postulación lleva texto, archivos o ambos, según pida la mesa, y sus archivos sobreviven a retirarla

**Decisión.** La postulación admite **texto enriquecido y archivos, opcionales por separado**: el master define qué exige su mesa. Conserva su tabla y **no** se modela como una petición más. Los archivos se vinculan en `registration_files`, y esas filas **no se arrastran** al retirar la postulación ni al borrar la mesa (ADR-07 §6). Lo que las trata como muertas son las lecturas: la consulta de usos (ADR-14 §3) exige una postulación **viva**, así que un archivo cuyo único vínculo era una postulación retirada figura «sin usar» y vuelve al alcance de la purga (ADR-13 §3). El cajón `PlayerApplication` no se revoca (ADR-14 §4).

**Por qué.** Un master puede pedir solo archivos, solo una explicación o las dos, así que ninguna vía puede ser obligatoria en el modelo; y mezclar la admisión con las peticiones (ADR-15 §1) juntaría flujos con reglas distintas. Una fila de `table_registrations` es una relación que se deshizo al retirarse; una de `registration_files` es un hecho —*esta hoja se adjuntó a esta postulación*— que retirarse no deshace, y marcarla borraría la única traza de qué se mandó. Lo que no puede pasar es que ese registro cuente como vínculo vivo: inmunizaría el archivo contra la purga para siempre. Una postulación `Deleted` sigue sin aparecer en ninguna lectura (ADR-03 §7); lo que sobrevive es el papel que llevaba adentro.

*Antes: #69, #247.*

## §3 Al llenarse el cupo, los candidatos restantes se rechazan en la misma transacción

**Decisión.** Al aceptar al jugador que completa `max_players`, todas las postulaciones `Candidate` restantes pasan a `Rejected` en la misma transacción, con un motivo por defecto —un código, `TABLE_FULL`, que el frontend traduce (ADR-23 §3)— y su notificación a cada persona.

**Por qué.** Como un candidato no reserva cupo (§1), sin esta regla quedarían postulaciones colgadas para siempre esperando una respuesta que nunca llega. Rechazar en el momento deja a la persona enterada, le ahorra al master escribir N veces el mismo motivo y aclara que el rechazo es por cupo, no por la persona.

*Antes: #34.*

## §4 El veto se acota a una sola mesa y la hace invisible para el vetado

**Decisión.** El veto (`Blocked`) vale **para una sola mesa**: no hay veto a nivel master ni a nivel plataforma por decisión de un master. Es un estado de `table_registrations` y no tiene tabla propia. El vetado no ve esa mesa ni sabe que existe —toda lectura por id responde `404`, nunca `403`, y volver a postularse también es `404`—, pero **puede postularse a una mesa nueva del mismo master**. No confundir con `users.status = 'Blocked'`, el bloqueo global que aplica un admin.

**Por qué.** CentralDungeon no discrimina: lo que regula la actitud de una persona es el karma (ADR-11 §1), no una lista negra que la siga por toda la plataforma. Como el veto siempre reacciona a una postulación existente, no necesita otra entidad. El `404` es el mismo que da un perfil caducado (ADR-10 §3): un `403` confirmaría lo que el `404` niega.

*Antes: #29.*

## §5 Veta el `Primary`; un co-master lo pide con justificación, y el veto se deshace con registro

**Decisión.** Solo el **`Primary`** veta. Un co-master puede **pedir** el veto, con justificación, y lo resuelve el `Primary`, no un admin. El veto es **reversible**, y tanto el veto como su levantamiento quedan registrados con motivo obligatorio.

**Por qué.** Le da contenido real a la distinción entre master y co-master (ADR-06 §3), que si no quedaba decorativa: quien está a cargo de la mesa responde por quién está y quién no. La reversibilidad es realismo: en una partida la gente se altera y un veto en caliente es esperable, así que tiene que poder deshacerse — pero queda en el historial para que un patrón de vetos impulsivos sea visible.

*Antes: #39.*

## §6 El padrón de la mesa es un endpoint propio, separado de la cola de candidatos

**Decisión.** `GET /game-tables/{id}/players` devuelve los jugadores de la mesa **y los vetados**, con quién vetó, cuándo y por qué —es el único lugar donde se levanta un veto (§5)—, separado de `GET .../registrations`, que devuelve la cola de candidatos.

**Por qué.** El primero que lo necesitó fue el selector de una petición `Single` (ADR-15 §2): dirigirle algo a una persona exige poder elegirla entre los que juegan, no del directorio de la plataforma. No es un `?status=` sobre el otro endpoint porque el orden de la cola es una regla de equidad (§1) y el de un padrón no: un endpoint que responde dos preguntas con dos ordenamientos es cómo alguien termina confiando en el equivocado.

*Antes: #213.*

## §7 El jugador ve a qué mesas se postuló y por qué lo rechazaron

**Decisión.** En el explorador, la card de una mesa muestra un chip neutro de «ya te postulaste» cuando hay una postulación no rechazada; `GameTableCard` recibe un booleano `alreadyApplied` que resuelve la ruta (`TableListPage`) cruzando mesas con `useMyApplications()`. En «Mis postulaciones», cada fila dice **a qué mesa** (`gameTableName`, denormalizado en `RegistrationResponse`), enlaza a `/player/tables/:id` y muestra el **motivo del rechazo** (`rejectionJustification`, que solo llena `listMine()` con una consulta extra por página, sin N+1).

**Por qué.** La lista tiene que explicar sin entrar a cada mesa. El booleano y no el estado real porque una feature no importa de otra (regla dura 16) y porque «ya te postulaste» es cierto tanto en `Candidate` como en `Player`. El chip es neutro porque el acento se reserva para acciones y karma, y esto no es ni una acción ni un estado de la mesa. Una fila de postulación sin el nombre de la mesa no distinguía una de otra, y un rechazo sin motivo no existe en el modelo.

*Antes: #149, #160.*
