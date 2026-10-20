---
adr: 187
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0187: InvalidRequestException, el quinto miembro de ApiException

## Decisión

`ApiException` pasa a permitir **cinco** subclases: se suma `InvalidRequestException` → **400**, para lo que está mal formado en la relación entre campos y no en ningún campo por separado. El caso que la trajo: dos franjas de la **misma** mesa que se pisan (#178)

## Por qué

Bean Validation ve un campo a la vez, así que no puede opinar sobre un par de franjas; y `ConflictException` es un 409, que significa "el estado del sistema dice que no". Dos franjas que se solapan no chocan con el estado de nadie: describen una semana que no se puede jugar. Son cosas distintas y #178 pide explícitamente un `400`. Se agrega a la lista sellada en vez de reutilizar el 409 porque el `switch` exhaustivo sobre `ApiException` es justamente lo que hace que agregar un error sea una decisión y no un descuido
