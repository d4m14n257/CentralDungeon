---
adr: 252
estado: Aceptada
fecha: 2026-09-14
---

# ADR-0252: La invariante del último Owner necesita dos bloqueos, no uno

> Cierra el punto (c) de `roles-y-alcance.md` §3 (skill `arquitectura`, antes `modelo-datos`)

## Decisión

`UserRoleService.isLastActiveOwner` toma **primero** `RoleRepository.lockByName("Owner")` —`@Lock(PESSIMISTIC_WRITE)` sobre la fila de `roles`, que es el mutex— y **después** cuenta con `UserRoleRepository.lockActiveHolders("Owner")`, que es una lectura **con bloqueo** y devuelve filas. `countActiveHolders` sobrevive degradado a observación, con Javadoc que prohíbe enforcar la invariante con él. Todo camino capaz de quitar un `Owner` pasa por ahí: la revocación directa y el otorgamiento de `Admin`, que se lo quita por la exclusión de #169.

## Por qué

El conteo optimista original **se rompía de verdad**, y no en teoría: dos ITs con concurrencia real contra MySQL dejaron la plataforma en **cero owners**, por las dos puertas. Ningún test unitario podía encontrarlo y en el código se lee perfectamente bien — hizo falta el nivel que `plan-desarrollo.md` §7 reserva para A3, y es el caso que justifica separarlo de los constructores. **Y el segundo bloqueo es la mitad load-bearing del arreglo**: bajo `REPEATABLE READ`, la transacción que esperó el mutex despierta y un `count(*)` plano le responde desde **su propio snapshot**, abierto antes de empezar a esperar, contando como vivo al owner que la transacción por la que acabó de esperar ya degradó. Tomar el lock y después contar con una query normal *parece* correcto y no lo es; solo una lectura con bloqueo saltea el snapshot. Está escrito en el Javadoc para que nadie lo «simplifique» de vuelta. **El mutex es una fila única y siempre presente**, así que un ciclo de locks no es ni expresable, y cubre el caso que un lock sobre los grants no puede: cuando no queda ninguna fila que lockear. Otorgar `Player` o `Master` nunca llega ahí, así que el caso común no paga nada. **Borde conocido y no arreglado**: dos revocaciones simultáneas de `Owner` sobre la **misma** persona responden `200` y `409 LAST_OWNER` en vez de `200` y `200`, porque el chequeo de idempotencia lee los grants antes de tomar el lock — el `409` es una afirmación verdadera sobre la plataforma aunque no sobre el clic, y la alternativa es una rama más en el camino más delicado.
