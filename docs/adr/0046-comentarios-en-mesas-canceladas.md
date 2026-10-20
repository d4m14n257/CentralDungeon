---
adr: 46
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0046: Comentarios en mesas canceladas

## Decisión

**Una mesa que termina en `Canceled` no genera comentarios ni movimiento de karma.** Solo las que llegan a `Finished` habilitan comentar

## Por qué

Una cancelación suele ser por causa de fuerza mayor —el master se enfermó, se cayó el grupo, algo pasó fuera del juego— y no dice nada del comportamiento de nadie. Permitir comentarios ahí convertiría la mala suerte en karma negativo para gente que no hizo nada malo. La visibilidad de perfiles sí corre sus dos semanas (#44): que no se comente no significa que la mesa no haya existido.
