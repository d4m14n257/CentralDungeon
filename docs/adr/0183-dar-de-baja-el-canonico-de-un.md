---
adr: 183
estado: Aceptada
fecha: 2026-09-03
modificada-por: [276]
---

# ADR-0183: Dar de baja el canónico de un grupo exige elegir sucesor

## Decisión

**`disable` acepta un `newCanonicalId`**, obligatorio solo cuando el valor es canónico y todavía tiene alias vivos. El sucesor pasa a canónico, el resto lo re-apunta, y el valor dado de baja **queda en el grupo como alias del sucesor**

## Por qué

#59 dice que con `canonical_id` plano "dar de baja el canónico" y "cambiar el canónico" son la misma operación, y #55 que el nuevo canónico lo elige el admin. Implementarlo pedía decidir *cómo*: la alternativa —promover el primer alias— es una decisión que el sistema no tiene derecho a tomar, y prohibir la baja dejaría el callejón sin salida que #59 quería cerrar. Que el valor dado de baja siga en el grupo es lo que mantiene encontrables las mesas etiquetadas con él (#81)
