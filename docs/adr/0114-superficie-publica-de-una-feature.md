---
adr: 114
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0114: Superficie pública de una feature

## Decisión

**Un `index.ts` en la raíz de cada feature** declara qué exporta; desde afuera se importa `@/features/tables` y nunca una ruta interna. Es el **único** barrel del proyecto

## Por qué

Con las páginas fuera de las features (#113), `routes/TableDetailPage.tsx` importa de cuatro dominios a la vez, y sin una puerta declarada nada distingue lo público de lo interno: cualquiera puede alcanzar un archivo que la feature considera privado, y refactorizarlo rompe consumidores que no se sabía que existían. El barrel convierte "una feature no importa de otra" en algo verificable, y más adelante lo puede vigilar una regla de lint. Se limita a la raíz de la feature a propósito: un `index.ts` por carpeta reintroduce ciclos de importación y no aporta ninguna frontera.
