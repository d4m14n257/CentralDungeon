---
adr: 255
estado: Aceptada
fecha: 2026-09-17
modifica: [42, 78, 90, 126]
---

# ADR-0255: approvalrequests entra con tres tipos, no con cinco

> Precisa #42, #78, #90 y #126

## Decisión

`MasterGrant`, `TableOpen` y `General`. `TablePause` y `PlayerBan` quedan **declarados en el DDL como comentario y sin valor en el enum**, y los agrega **F3.4 junto con su productor**. Los tres que entran apuntan al **solicitante** (`entity_type = 'user'`), porque `TableOpen` pide una mesa que todavía no existe y `General` no es sobre ninguna entidad, y las dos columnas son `NOT NULL`.

## Por qué

El DDL baseline comenta los cinco y `roles-y-alcance.md` §4 los enumera, así que lo natural era declararlos todos. **No se declaran, y el motivo es el corazón de #78**: «agregar un flujo nuevo es agregar un valor al enum, **sin `ALTER TABLE`**» — la columna es `VARCHAR(32)`, así que adelantarlos no compra nada y sí crea **dos valores de enum que nada produce**, que es exactamente el huérfano que F1.7 relevó y que esta misma fase le reprocha a `PauseRequested` (el punto de partida del documento de F3, §2, conservado en git). No se estrena una fase creando el defecto que la fase vino a cerrar. **Y apuntar los tres al solicitante no es un relleno**: el pedido *es* sobre esa persona, y es lo que evita las columnas nullable por flujo que #126 descartó por hacer que la bandeja tuviera que saber cuál mirar.
