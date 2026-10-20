---
adr: 191
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0191: Weekday es un enum propio, no java.time.DayOfWeek

## Decisión

Un enum de siete valores con los mismos nombres que la columna guarda desde el schema heredado, más la traducción a `DayOfWeek` en un método. En el frontend, la unión de literales equivalente vive en `lib/date.ts`

## Por qué

La columna es `VARCHAR(16)` con `'Monday'`…`'Sunday'` desde el modelo viejo. Persistir `DayOfWeek` obligaba o a cambiar los valores guardados —una migración de datos para nada— o a un converter que hace exactamente la traducción de una línea que ahora vive en el enum. Y tener el tipo propio permite documentar en él lo único que importa de verdad: **el día es UTC** (#22), no el día que los jugadores dicen
