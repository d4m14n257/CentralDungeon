---
adr: 95
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0095: Destino del feedback del sistema

## Decisión

**Va directo a una bandeja de lectura para admins y owner. No pasa por moderación**

## Por qué

Cierra el otro residuo de **M31**. Moderar feedback sobre el producto no tiene el mismo sentido que moderar una evaluación que mueve el karma de alguien: acá no hay una persona afectada a la que proteger. El `status` de `system_feedback` sirve para que los admins marquen lo que ya revisaron, no para aprobar o rechazar.
