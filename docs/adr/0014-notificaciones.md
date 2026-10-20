---
adr: 14
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0014: Notificaciones

## Decisión

**Solo in-app en v1**

## Por qué

La fila en `notifications` *es* la notificación. Email y Discord DM quedan fuera; si se agregan, es una tabla de canal aparte sin tocar la existente. Tampoco hay preferencias ni suscripciones: todo destinatario de un evento recibe su fila.
