---
adr: 244
estado: Aceptada
fecha: 2026-09-13
---

# ADR-0244: La campanita cubre el ciclo de revisión y la asignación de masters

## Decisión

Cuatro tipos nuevos: `MasterAssigned` (un admin te hizo master de una mesa, y también al sumarte como co-master), `TableApproved`, `TableApprovedWithChanges` y `TableChangesRequested`. La razón del rechazo **no** viaja en la notificación: ya es obligatoria en la transición y vive en el historial de estados

## Por qué

Dos silencios del mismo flujo. **Asignar un master no avisaba a nadie**: un admin creaba la mesa y la entregaba, y la persona se enteraba de que dirigía una mesa solo si entraba a su contexto Master y veía algo que nunca había visto. Es la única notificación cuyo destinatario no hizo nada para provocarla, y por eso es la que no se puede saltear. **Y la revisión era una espera sin otro lado visible**: la mesa quedaba en la cola y nada en la pantalla del master cambiaba hasta que un admin actuara. Se distinguen aprobada y aprobada-con-cambios porque que te editen lo que escribiste es otra noticia que que te lo aprueben. La razón no se copia a la notificación por lo mismo que #197: una segunda copia es una que puede discrepar de la primera, y el historial ya la muestra entera. Promover a alguien que ya dirigía la mesa **no** notifica: ya la dirigía y está mirando la pantalla que lo hizo.
