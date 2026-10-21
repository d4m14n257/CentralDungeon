---
adr: 19
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0019: Versiones del stack

## Decisión

**Se fijan explícitamente en `arquitectura` §1**: Java 25 LTS + Spring Boot 4.1.1; React 19.2 + Vite 8 + TypeScript 5.9 + Tailwind 4 + React Router 8

## Por qué

El proyecto estuvo detenido dos años; arrancar sobre lo que estaba vigente en 2024 sería empezar con deuda. Spring Boot 3.5 llegó a EOL open source el 30/06/2026 y Java 21 pierde licencia permisiva de Oracle en septiembre de 2026, así que 3.x/21 no eran opción para código nuevo. Como no hay código escrito todavía, el costo de arrancar en la versión actual es cero, mientras que migrar después Jackson 2→3, JUnit 4/5→6 y Testcontainers 1→2 sí tiene costo.
