---
adr: 216
estado: Aceptada
fecha: 2026-09-04
---

# ADR-0216: Quitar a un co-master, y qué pasa con su fila

## Decisión

**`DELETE /api/v1/game-tables/{id}/masters/{userId}` marca la fila (`status = Deleted` + `deleted_at`) y la pertenencia pasa a filtrar por estado.** Solo el `Primary` quita; **al `Primary` no se lo puede quitar** (primero se le pasa la mesa, `409`); volver a agregar a la misma persona **revive la fila** en vez de insertar otra

## Por qué

F1.6 tenía que darle interfaz al `POST /{id}/masters` que existía desde E2, y agregar «quitar» destapó un bug latente que ningún test podía ver antes: `MasterService.isMasterOf()` e `isPrimaryOf()` leían `findByGameTable_IdAndUser_Id` **sin mirar `MasterRowStatus`**, y `findByGameTable()` devolvía filas vivas y borradas por igual. Mientras `Deleted` solo lo produjera la cascada de una mesa borrada nadie lo notaba —la mesa entera ya no existía—, pero en el momento en que una fila puede morir sola, **un co-master quitado seguía autorizado**: las pantallas dejaban de listarlo y los endpoints seguían aceptándolo. Se arregló en la misma rebanada que introduce la causa, con `existsByUser_IdAndStatus` en `UserService` incluido, que es lo que decide si el `ContextSwitcher` ofrece el contexto Master. La fila se marca y no se borra porque quién dirigió una mesa es un **registro**, no un permiso (#175) — y como la clave primaria es `(game_table_id, user_id)`, revivir es además la única forma de volver a agregar a alguien sin violarla, el mismo patrón que `TableFileStatus` estrenó en F1.4 (#190). Al `Primary` no se lo quita porque una mesa sin nadie a cargo no tiene quién la dirija: la salida es pasarle la mesa a otro, que es lo que la invariante de #73 ya sabía expresar. Verificado con `MasterServiceTest` (6 casos nuevos), `MasterServiceIT` (promociones y bajas concurrentes: la mesa nunca queda sin `Primary`; revivir no crea una segunda fila) y el e2e `master-dashboard.spec.ts`, que es donde se ve el bug real: el co-master entra por su contexto, ve la mesa, y tras la baja recibe «No tenés permiso» en la misma URL.
