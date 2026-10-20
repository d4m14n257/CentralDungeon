---
adr: 256
estado: Aceptada
fecha: 2026-09-17
---

# ADR-0256: La resolución de un pedido se serializa con un bloqueo, y el pedido también

> Misma forma que #252

## Decisión

`ApprovalService.resolve` lee el pedido con `ApprovalRequestRepository.lockById` (`PESSIMISTIC_WRITE`), y **esa lectura con bloqueo es la lectura del estado**. `ApprovalService.submit` toma la fila del solicitante con `UserRepository.lockById` **como primera operación de la transacción**, antes del chequeo de pendientes. Y `GlobalExceptionHandler` gana `handleDataIntegrityViolation` → `409 CONFLICT`.

## Por qué

Las dos guardas —`REQUEST_ALREADY_PENDING` y `REQUEST_ALREADY_RESOLVED`— eran check-then-act sin nada en el medio, y **las dos se rompían de verdad**: probado con concurrencia real contra MySQL, dos submits simultáneos abrían dos pedidos sin que ninguno fuera rechazado, y dos admins respondiendo a la vez resolvían dos veces. **El peor caso no es una nota de resolución pisada**: aprobar y rechazar a la vez pasan los dos, la fila puede quedar `Rejected` sobre alguien que ya es `Master`, y al solicitante se le notifican las dos cosas — el registro de la decisión contradiciendo la decisión. Y había un espejismo peligroso: el caso `MasterGrant` *parecía* protegido, pero lo único que lo salvaba era una colisión accidental con la PK de `users_roles`, que cubría **uno de los tres tipos** y le respondía `500` al perdedor. La guarda no estaba haciendo el trabajo; lo estaba haciendo la clave primaria. **El orden importa y es la parte que no se ve**: bajo `REPEATABLE READ`, tomar el lock y después leer con una query plana devuelve el snapshot abierto **antes** de empezar a esperar, así que la transacción que esperó lee alegremente `Pending` sobre una fila ya resuelta. La lectura con bloqueo tiene que ser la primera vez que la transacción toca esa fila, porque un `findById` previo dejaría una instancia vieja en el contexto de persistencia y el lock no refrescaría nada. Es exactamente lo aprendido en #252, aplicado antes de pagarlo dos veces. **Los stubs de los tests unitarios se movieron a los métodos con bloqueo**: sobre `findById` seguirían en verde con el lock sacado, que es cómo el bug llegó hasta acá.
