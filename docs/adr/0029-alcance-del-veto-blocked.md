---
adr: 29
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0029: Alcance del veto (Blocked)

## Decisión

**Acotado a una sola mesa.** No existe veto a nivel master ni a nivel plataforma por decisión de un master

## Por qué

Cierra **M8**. Un jugador vetado no ve esa mesa ni sabe que existe, pero **si el mismo master abre una mesa nueva, puede postularse a ella**. CentralDungeon no discrimina: lo que regula la actitud de una persona es el karma (#30), no una lista negra que siga al usuario por toda la plataforma. Como el veto siempre reacciona a una solicitud existente, `Blocked` se queda como estado de `table_registrations` y **no necesita tabla propia**. No confundir con `users.status = 'Blocked'`, que es el bloqueo global de la plataforma y lo aplica un admin.
