---
adr: 71
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0071: Nombre del rol de mesa

## Decisión

**`masters.master_type` pasa a `Primary` / `Secondary`.** Se abandona `Owner` para la mesa

## Por qué

Cierra **M29**. Elimina la colisión con el rol de plataforma `Owner` (#67). Se descartó `Creator` porque sería falso justo en el caso de #72: si un admin crea la mesa y le asigna un master, ese master no la creó. `Primary` describe la jerarquía real —uno manda, los otros acompañan— y vale igual haya creado la mesa o se la hayan asignado.
