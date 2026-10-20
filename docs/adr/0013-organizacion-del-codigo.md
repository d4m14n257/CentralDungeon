---
adr: 13
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0013: Organización del código

## Decisión

**Paquete/carpeta por feature, capas adentro**, en backend y frontend

## Por qué

Un `controller/` con nueve controllers de dominios distintos obliga a saltar entre carpetas para tocar una sola funcionalidad. Por feature, agregar o quitar una funcionalidad es agregar o quitar una carpeta. Ver skill `arquitectura` §2.1 y §3.1.
