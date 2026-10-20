---
adr: 159
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0159: hasManagedTables en /users/me: #135 quedaba a medias

## Decisión

La decisión #135 ya decía, textual, que "el `ContextSwitcher` muestra el contexto Master si hay rol **o** al menos una fila viva en `masters`" — pero nunca se implementó esa segunda mitad. `AppHeader.tsx` solo miraba `me.roles.includes('Master')`. Se agregó `hasManagedTables: boolean` a `UserDetailResponse` (`UserService.getDetailResponse`/`completeOnboarding` piden `masterRepository.existsByUser_Id(userId)`, nuevo método en `MasterRepository`) y `AppHeader` ahora arma `availableContexts` con `roles.includes('Master') || hasManagedTables`

## Por qué

Saltó al armar el caso de prueba del pedido: asignar a `jugador-1` (sin el rol `Master`) como Secondary de una mesa nueva para probar el doble rol. Sin este fix, `jugador-1` quedaría con una fila real en `masters` pero **sin ninguna forma de llegar a `/master/tables` desde la interfaz** — el `ContextSwitcher` jamás iba a ofrecerle el contexto, contradiciendo lo que la propia decisión #135 ya exigía. `UserService` (paquete `users`) pasa a depender de `MasterRepository` (paquete `tables`) - no hay ciclo de beans porque es una dependencia a un repository, no a `MasterService` (que ya depende de `UserService`). Verificado en vivo: `GET /users/me` de `jugador-1` devuelve `roles: ["Player"]` y `hasManagedTables: true` tras asignarlo como Secondary de "El Pozo de las Sombras".
