---
adr: 111
estado: Aceptada
fecha: 2026-08-29
modificada-por: [133]
---

# ADR-0111: Fechas y horas

## Decisión

**`Intl.DateTimeFormat` nativo**, en `lib/date.ts`, con locale y zona horaria como parámetros. **Sin librería de fechas**

## Por qué

No hay aritmética de calendario en la aplicación: los horarios son día de semana más hora, y lo que llega del backend es ISO-8601 UTC que solo hay que mostrar. El `useDate` del legacy ya lo resolvía con `toLocaleDateString` y funcionaba; su único defecto era tener `'es-ES'` incrustado, que se corrige parametrizando. Agregar `date-fns` + `date-fns-tz` serían dos dependencias del stack fijado (§1.2) para cubrir un caso que no existe. ~~La zona sale del perfil del usuario y solo si falta, del navegador~~ — **corregido en #133**: contradecía a #22, que eliminó `users.timezone`, y describía un comportamiento que el schema no puede sostener. **La zona sale del navegador**, y `lib/date.ts` la recibe como parámetro para que sea testeable, no para que venga de la base.
