---
adr: 152
estado: Aceptada
fecha: 2026-08-31
modifica: [151]
---

# ADR-0152: GET /api/v1/game-tables/{id}/managed — la pertenencia se verifica en el backend, no después de que el dato ya viajó

## Decisión

Corrige #151, que había resuelto el mismo caso solo en el frontend (comparar `table.masters` contra el actor antes de pintar) — dejaba pasar el request completo a `GET /api/v1/game-tables/{id}` igual, con el cuerpo entero de la mesa ajena viajando por la red antes de que React decidiera no mostrarlo. Ahora `GameTableService.getManagedDetail(id, actorId)` llama a `masterService.isMasterOf()` **antes** de mapear nada y tira `ForbiddenActionException` (403) sin cuerpo si no corresponde; `GameTableController` lo expone en `/{id}/managed`, endpoint nuevo y separado de `getDetail` — nunca el mismo path con dos comportamientos según quién pregunta. El frontend pasó de `useGameTable` (el público) a `useManagedTable` en `MasterTableDetailPage`, y el chequeo client-side de #151 se borró: ya no hace falta, el 403 real reemplaza al `ForbiddenState` calculado a mano

## Por qué

El usuario no aflojó, y tenía razón en no aflojar: "por qué se sigue disparando este endpoint... no debería lanzarse esto". #151 cambiaba qué se pinta, no qué se pide — exactamente la clase de arreglo cosmético que un client-side check siempre es: cualquiera con las devtools abiertas ve la respuesta completa en la pestaña Network antes de que el componente decida ocultarla. La regla 17 ("el actor... verificación explícita en el service antes de tocar nada") ya lo decía y #151 no la había seguido del todo — la aplicó a la *pintura*, no a la *lectura*. `getDetail` sigue existiendo tal cual, público, porque sigue siendo la ruta correcta para `/tables/:id` (cualquier jugador necesita ver una mesa para decidir si postularse); lo que cambió es que la vista de **gestión** ya no comparte esa puerta. Verificado con `curl` primero (confirmando el 403 sin cuerpo) y después con el Network real del navegador: la única llamada que sale desde `/master/tables/:id` para una mesa ajena es a `/managed`, y responde 403 — cero requests al endpoint público desde esa pantalla.
