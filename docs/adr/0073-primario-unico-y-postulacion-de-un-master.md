---
adr: 73
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0073: Primario único y postulación de un master

## Decisión

**Exactamente un `Primary` por mesa.** Un master puede postularse como jugador **solo si además tiene el rol `Player`**

## Por qué

Cierra **M5**. El primario único deja de ser teórico con #72: si el admin asignara dos primarios o ninguno, el circuito de aprobación de vetos (#39) queda sin quién lo cierre. Y la postulación aplica la regla de #37/#67 sin excepciones: los roles son permisos acumulables, no una escala — ser master **no** implica poder jugar; para eso hace falta tener también el rol `Player`.
