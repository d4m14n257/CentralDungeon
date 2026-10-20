---
adr: 160
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0160: Detalle en "Mis postulaciones": qué mesa, y por qué te rechazaron

## Decisión

`RegistrationResponse` suma dos campos: `gameTableName` (denormalizado desde `TableRegistration.gameTable.name`, mismo criterio que ya usa `userName`/`userKarma` para no forzar un join en el cliente) y `rejectionJustification` (solo se llena en `RegistrationService.listMine()`, con una consulta extra por página a `RegistrationRejectionRepository.findByRegistration_IdIn()` para no caer en N+1 - las otras rutas que devuelven `RegistrationResponse` no la necesitan y la dejan `null` vía `@Mapping(target = "rejectionJustification", ignore = true)`). `MyApplicationsPage` ahora muestra el nombre de la mesa, un link "Ver mesa" a `/tables/:id`, y el motivo del rechazo cuando corresponde

## Por qué

`screen-my-applications.html` (`design/build.py`) ya documentaba desde antes de esta sesión que "el rechazo muestra su justificación, que es obligatoria: un rechazo sin motivo no existe en el modelo" - nunca se había construido así. El usuario, revisando la pantalla real, notó que faltaba algo como "ver detalles" de la postulación; la causa de fondo era más simple y más grave: la fila ni siquiera decía **a qué mesa** te habías postulado, solo la fecha y el estado - no había nada que "ver en detalle" porque no había con qué distinguir una fila de otra. Verificado en vivo contra `jugador-1` (una de las cuentas fijas de `pruebas-e1.md`, ya con datos reales): `GET /registrations/mine` devuelve `gameTableName: "Tumbas de Sal"` y `rejectionJustification: "No me cae bien"` para la fila que él mismo había rechazado antes en esta sesión de pruebas.
