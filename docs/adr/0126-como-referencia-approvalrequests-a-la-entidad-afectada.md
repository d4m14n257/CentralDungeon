---
adr: 126
estado: Aceptada
fecha: 2026-08-29
modificada-por: [255]
---

# ADR-0126: Cómo referencia approvalrequests a la entidad afectada — cierra M13

## Decisión

**Polimórfica: `entity_type` + `entity_id`, sin FK real.** Un solo service, una sola bandeja, y agregar un cuarto flujo es una fila más

## Por qué

Cierra el residual de M13 por la opción (a). Los tres flujos existentes —pausar mesa (#32), vetar jugador (#39), pedir rol o mesa (#90)— tienen forma idéntica: alguien pide, alguien con más autoridad aprueba o rechaza, siempre con justificación. Con FK nullable por flujo, cada uno nuevo agrega una columna a una tabla que ya nadie entiende, y la bandeja de #100 tendría que saber cuál de las cinco columnas mirar. El precio de la polimórfica es real y se asume explícitamente: **la base no puede garantizar la integridad referencial**, así que la validación de que `entity_id` existe es del service y llega con test (regla dura 7), y un borrado de la entidad apuntada no arrastra sus solicitudes. Se acepta porque `approval_requests` es un registro histórico: una solicitud sobre una mesa borrada sigue siendo un hecho que pasó y conviene conservar.
