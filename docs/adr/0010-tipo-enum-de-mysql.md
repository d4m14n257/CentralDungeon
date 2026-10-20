---
adr: 10
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0010: Tipo ENUM de MySQL

## Decisión

**Se reemplaza por `VARCHAR(32)` + enum de Java**

## Por qué

Agregar un valor a un `ENUM` de MySQL es un `ALTER TABLE` con bloqueo; agregarlo a un enum de Java es una línea. La validación ya vive en el service.
