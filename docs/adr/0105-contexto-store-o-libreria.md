---
adr: 105
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0105: Contexto, store o librería

## Decisión

**Tres criterios en vez de uno**: librería cuando ya existe (toast → `sonner`, tema → `next-themes`), **Context** cuando el estado pertenece a un subárbol o necesita montar UI (confirmación imperativa, selección múltiple de una tabla), y **Zustand** cuando es global y plano (contexto de rol activo, preferencias)

## Por qué

El frontend viejo tenía cinco contexts y la idea era buena: mensajes, tema, confirmación y selección con Shift son estado de UI, no datos de servidor, así que la regla dura 11 no los prohíbe. Lo que fallaba era usar el mismo mecanismo para todo, incluidos los datos de servidor. La selección con Shift es el caso más claro: es de **una tabla concreta**, no de la aplicación, así que va como Context alrededor de esa tabla y no como estado global.
