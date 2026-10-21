---
adr: 15
titulo: Peticiones de la mesa
estado: Vigente
actualizada: 2026-10-20
absorbe: [63, 70, 76, 77, 87, 209, 210, 212, 236]
---

# ADR-15: Peticiones de la mesa

> Cómo una mesa le pide cosas a sus candidatos y jugadores, cómo se responden y quién las ve. Las reglas de código y el DDL están en `arquitectura` §4. La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 Una mesa pide cosas en cualquier momento, y todo pedido se modela una sola vez

**Decisión.** Una mesa puede pedirle cosas a sus candidatos y jugadores antes de entrar, al entrar y durante las sesiones, atadas a una sesión o sueltas. Cada petición se responde con texto enriquecido, con archivos o con ambos. Las tablas son **`table_tasks`** (el pedido) y **`task_submissions`** (cada respuesta), con `submission_files` para lo adjunto a una respuesta.

**Por qué.** Unifica tres cosas que tienen la misma forma —alguien pide, alguien responde con texto y/o archivos—: los requisitos que el master publica al abrir la mesa, lo que el jugador manda al postularse (ADR-13 §1) y lo que el master pide después de aceptarlo. La analogía es un salón de clases. Es funcionalidad nueva, no algo que el schema heredado insinuara. El nombre es *task* y no *requirement* por una colisión concreta: `game_tables.requirements` ya es el texto con los requisitos generales de la mesa (ADR-03 §8), y `TableRequirement` como entidad al lado de `getRequirements()` devolviendo texto serían dos cosas distintas con el mismo nombre; además *task* cubre también lo que se pide durante el juego.

*Antes: #63, #87.*

## §2 El sistema facilita las entregas y no las juzga

**Decisión.** Las entregas **se acumulan**, nunca se reemplazan, y no hay flujo de aprobación: revisarlas es del master. El jugador entrega con texto y archivos. El master puede dirigir una petición a un jugador concreto (`audience = 'Single'`). Una petición sin cumplir **no bloquea ni expulsa**: se le avisa al jugador y queda visible para el master; cualquier acción posterior —intervención, expulsión, veto— es siempre manual, del master o de un admin.

**Por qué.** Saber si un personaje entregado es el definitivo o si cumple lo pedido exige un criterio humano que el sistema no tiene y no debe fingir. Acumular preserva el historial sin pedirle al sistema que decida cuál vale. Un incumplimiento puede tener mil motivos, y automatizar una sanción genera más problemas de los que resuelve; además evita sumar un estado a la inscripción (ADR-09 §1). El sistema informa; las personas deciden. La entrega existe desde la misma rebanada que el panel de entregas: sin nadie que pueda entregar, el panel muestra a todos como faltantes y la regla de acumular queda sin ejercitar.

*Antes: #70, #76, #210.*

## §3 El master puede adjuntar archivos al pedido que escribe

**Decisión.** Tabla `task_files`, con la forma de `submission_files`. `CreateTaskRequest` y `UpdateTaskRequest` llevan `fileIds`, y la petición los muestra al master y a quien la recibe. Lo abre todo el que la petición alcanza —candidato o jugador—, no solo quien dirige (ADR-14 §2). Vincula y no copia (ADR-14 §1): el formulario publicado por la comunidad lo adjunta cada master sin resubirlo. `table_tasks.accepts_files` sigue diciendo si la **respuesta** puede traer archivos.

**Por qué.** Sin esto un master podía escribir «mandame tu ficha en este formulario» y no tenía cómo adjuntar el formulario. Y el cajón `MasterRequest` (ADR-14 §4) solo podría contener lo que publica un admin, nunca algo del master.

*Antes: #236.*

## §4 Las peticiones que le tocan a alguien se piden a un endpoint propio

**Decisión.** `GET /game-tables/{id}/tasks/applicable`, y no un campo del detalle de la mesa como las sesiones y los archivos.

**Por qué.** Sesiones y archivos son iguales para todo el que ve la mesa; esta lista **depende de quién pregunta**: la audiencia `Players` solo la ven los jugadores y una `Single` solo su destinatario. Meter una respuesta con forma de actor dentro de un DTO que se arma sin actor —`toDetail()` se llama sin él en cada transición— es como alguien termina viendo lo que no le toca (ADR-05 §5). Además deja `features/tasks` autocontenida y con su propia entrada de caché, que dos personas mirando la misma mesa no pueden compartir.

*Antes: #209.*

## §5 Publicar una petición notifica a sus destinatarios, y el aviso lleva al detalle público de la mesa

**Decisión.** Publicar una petición crea una notificación `TaskPublished` (ADR-18 §1) para cada destinatario, con destino **`/player/tables/:id`**, no `/player/my-tables/:id`.

**Por qué.** Una petición que nadie ve no se cumple. El mismo aviso le llega a un candidato y a un jugador: una petición de audiencia `Candidates` es para gente que todavía no está en la mesa y no tiene entrada en `/my/tables`. El detalle público muestra a los dos lo que la mesa les pide (ADR-14 §2), así que es el único destino que sirve sin partir el tipo de notificación en dos.

*Antes: #77, #212.*
