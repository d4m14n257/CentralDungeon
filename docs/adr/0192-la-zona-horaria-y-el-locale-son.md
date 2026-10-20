---
adr: 192
estado: Aceptada
fecha: 2026-09-03
---

# ADR-0192: La zona horaria y el locale son parámetros de lib/date.ts

## Decisión

Ninguna función de `lib/date.ts` lee una constante: locale y zona entran por parámetro. Hoy la zona sale de `browserTimeZone()` porque #22 quitó `users.timezone` del modelo; el día que exista una preferencia de perfil se cambia el llamador y nada más. `vite.config.ts` fija `TZ=America/Argentina/Buenos_Aires` para la suite de Vitest

## Por qué

El `useDate` del legacy tenía `'es-ES'` incrustado, que es lo que #111 anotó. Y la suite corre en una zona real y **no en UTC** a propósito: la mitad de lo que hace este módulo es correr el día al cruzar la medianoche, y en UTC ese caso no se ejercita nunca — que es exactamente el bug más probable de F1
