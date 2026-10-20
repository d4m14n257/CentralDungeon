---
adr: 155
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0155: Un actor no puede ser jugador y master de la misma mesa

## Decisión

**Dos validaciones nuevas, server-side, una por dirección.** (a) `RegistrationService.apply()` rechaza con `ForbiddenActionException` (403) si `masterService.isMasterOf(gameTableId, actorId)` — un master no puede postularse como candidato a la mesa que él mismo dirige. (b) `MasterService.addOrPromote()` rechaza con `ConflictException` (409) si el `targetUserId` que se va a agregar como Secondary tiene una fila activa (`Candidate`/`Player`) en `table_registrations` para esa mesa — no se puede nombrar master a alguien que ya juega ahí. La segunda validación solo corre en la rama de alta de un master **nuevo** (`orElseGet`): promover o reasignar a alguien que ya es master de esa mesa no vuelve a chequearlo, porque esa persona nunca pudo tener una postulación activa mientras ya era master (la primera validación se lo impide desde que se creó esta regla). Del lado del explorador, `GET /api/v1/game-tables` (la lista pública para postularse) ahora excluye las mesas que el actor mismo dirige — `GameTableRepository.findByStatusInAndNotMasteredByActor`, un `NOT EXISTS` contra `masters` — para que un master nunca vea sus propias mesas en la pantalla pensada para postularse a las de otros

## Por qué

El usuario, que tiene tanto el rol `Player` como `Master`, encontró sus propias mesas listadas en el explorador de Jugador y señaló la regla de fondo: "un jugador no puede ser master de su mesa y viceversa". Repasando el schema no había ninguna restricción — `masters` y `table_registrations` son tablas independientes, tal como ya lo señalaba `M5` (#73) sin cerrar el caso puntual de la mesa propia. #73 dejaba explícitamente permitido que un master se postule **a la mesa de otro co-master**; lo que faltaba decidir era esta mesa, la que él mismo dirige, y el usuario la cerró en las dos direcciones. La exclusión del explorador se resolvió en el backend (no solo ocultando en el cliente) siguiendo el precedente de #151/#152 en esta misma sesión: si hay una regla real, se aplica en la fuente de los datos, no después de que ya viajaron. Verificado en vivo contra el backend corriendo del usuario: un master de prueba recibió 403 al intentar postularse a su propia mesa recién creada, esa mesa no apareció en su propio explorador (`GET /api/v1/game-tables`), y un intento de nombrar master a un jugador que ya tenía una postulación activa en esa mesa devolvió 409 — los tres casos con datos de prueba borrados de MySQL después de confirmar.
