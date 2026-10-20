---
adr: 11
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0011: Contador de jugadores por mesa

## Decisión

**Se deriva con `COUNT`**, se elimina la tabla `Players_Table`

## Por qué

Era una denormalización mantenida por trigger. Derivarla elimina de raíz la clase de inconsistencia que el trigger existía para prevenir.
