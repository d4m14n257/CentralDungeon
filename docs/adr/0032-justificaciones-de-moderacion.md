---
adr: 32
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0032: Justificaciones de moderación

## Decisión

**Van en su propia tabla, separadas de `comments`. La pausa pedida por un master no es efectiva hasta que un admin la aprueba**

## Por qué

Cierra **M9**. `comments` es opinión entre personas y alimenta el karma (#30, #31); las justificaciones de moderación son otra cosa: mesa rechazada por un admin y por qué, pedido de correcciones, motivo de pausa, motivo de cancelación. Mezclarlas contaminaría el karma con actos administrativos. Como la pausa requiere aprobación, la máquina de estados gana `PauseRequested`: la mesa sigue corriendo hasta que el admin resuelve. La pausa iniciada por un admin es inmediata, pero también se justifica, para que el resto de los admins vea el motivo.
