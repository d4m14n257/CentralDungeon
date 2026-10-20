---
adr: 27
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0027: Ciclo de vida de la mesa

## Decisión

**Se formaliza la máquina de estados, se agrega un estado entre `Preparation` y `Opened`, y toda transición sensible lleva justificación**

## Por qué

El schema heredado tenía los siete estados pero ninguna regla escrita sobre quién puede pasar de cuál a cuál ni dónde se guarda el motivo. Detalle y pendientes en la sección *Ciclo de vida de la mesa*.
