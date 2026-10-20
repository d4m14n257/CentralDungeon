---
adr: 153
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0153: El master se entera de una postulación nueva: tipo NewCandidate

## Decisión

`NotificationType` suma un tercer valor, `NewCandidate`, junto a los dos que ya emitía `RegistrationRejected`/`RegistrationAccepted` (ambos hacia el jugador). `NotificationService.notifyNewCandidate(masterUserId, table, applicantName)` sigue el mismo patrón que los otros dos — título en español directo ("Nueva postulación a &lt;mesa&gt;"), `applicantName` como `message` —, y `RegistrationService.apply()` lo llama una vez por cada fila en `masterService.findByGameTable(gameTableId)` después de guardar la postulación: **todos** los masters de la mesa se enteran, Primary y Secondary por igual, porque la regla 17 ya establece que cualquiera de los dos puede aceptar o rechazar candidatos — no tendría sentido notificar solo al Primary de algo que el Secondary también puede resolver

## Por qué

El usuario se postuló como jugador a dos mesas y, entrando como `master-1` (Secondary de ambas), no vio ninguna notificación — encontrado probando el flujo real de postulación de la fase 1, no reportado por nadie antes. Confirmado por código, no asumido: `RegistrationService.apply()` no llamaba a `notificationService` en absoluto, y el propio comentario de `NotificationType` ("E1 minimal set - more types arrive with the features that emit them") documentaba que esto faltaba a propósito, sin que ninguna decisión previa ni `plan-desarrollo.md` lo hubieran exigido para E1 — es una funcionalidad nueva, no un bug contra un comportamiento ya definido. El nombre del postulante sale de la misma regla que ya usan `GameTableMapper`/`RegistrationMapper` (`name` si existe, si no `discordUsername`), no una nueva. Verificado en vivo contra el backend corriendo del usuario (mismo proceso, sin instancias paralelas): se creó una mesa temporal como master de prueba, se postuló un jugador de prueba, y `GET /api/v1/notifications` del master devolvió la fila `NewCandidate` con el nombre del postulante como mensaje — datos de prueba borrados de MySQL después de confirmar.
