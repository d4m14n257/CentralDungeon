---
adr: 40
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0040: Cuándo se comenta

## Decisión

**Un solo comentario por mesa (#35), sin segundo comentario al cierre. En su lugar, el frontend advierte antes de enviar** que es el único que podrá dejar sobre esa persona en esa mesa y que el mejor momento para evaluar es al final

## Por qué

Se evaluó habilitar un segundo comentario al terminar la mesa y se descartó: duplicaba el peso que una sola persona puede mover en el karma de otra. El problema real que resolvía —que a mitad de camino la opinión todavía no está formada— se resuelve mejor avisando, no dando otro comentario. Es una confirmación explícita en el frontend, no una restricción de fecha: si alguien quiere comentar antes del final, puede, pero sabiendo que gasta su única oportunidad.
