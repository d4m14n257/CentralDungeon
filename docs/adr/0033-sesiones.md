---
adr: 33
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0033: Sesiones

## Decisión

**Se materializan como filas (`table_sessions`), no se derivan por cálculo**

## Por qué

Cierra **M10**. Derivar el calendario a partir de `start_date` + agenda semanal + número de sesiones solo funcionaría si nada pudiera alterarlo. Con `Pause` sí puede: al retomar hay que reagendar lo que quedó pendiente, y eso exige saber qué se jugó realmente y qué no. Materializar habilita además corregir una fecha puntual, cancelar una sesión suelta y anclar los comentarios a la sesión (#31).
