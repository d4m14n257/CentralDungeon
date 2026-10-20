---
adr: 31
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0031: Comentarios

## Decisión

**Atados a la sesión en que ocurrieron, tipificados como positivo / negativo / neutro, y uno solo por autor sobre la misma persona**

## Por qué

Cierra la parte de contexto de **M2**. Un comentario es una opinión sobre alguien con quien se jugó ("me gustó jugar con él porque…", "me desagradó la sesión por él"), así que solo tiene sentido dentro de una sesión concreta — eso da además la verificación natural de que ambos estuvieron ahí. Las tres direcciones del enum heredado (`JJ` jugador→jugador, `JM` jugador→master, `MJ` master→jugador) se conservan. **El límite de uno por autor es antispam**: sin él, se puede hundir o inflar el karma de alguien repitiendo comentarios.
