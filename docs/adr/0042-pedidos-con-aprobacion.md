---
adr: 42
estado: Aceptada
fecha: 2026-08-29
modificada-por: [255]
---

# ADR-0042: Pedidos con aprobación

## Decisión

**Es un patrón deliberado y se modela una sola vez**: master pide pausa → admin aprueba; co-master pide veto → `Owner` aprueba; usuario pide rol o ser master → admin aprueba

## Por qué

Cierra **M13**. No es burocracia accidental: es el control que evita que los masters abran mesas sin freno o que decisiones fuertes se tomen solas. Como la forma se repite tres veces y va a repetirse más, se abstrae (skill `arquitectura` §2.4): un solo mecanismo de solicitud/aprobación con justificación obligatoria, una sola bandeja para quien aprueba.
