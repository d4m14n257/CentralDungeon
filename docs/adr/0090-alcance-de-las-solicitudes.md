---
adr: 90
estado: Aceptada
fecha: 2026-08-29
modificada-por: [255]
---

# ADR-0090: Alcance de las solicitudes

## Decisión

**`approval_requests` cubre todo pedido dirigido a los admins**: pedir que se abra una mesa sin ser master, pedir el rol de master de forma permanente, y peticiones generales al equipo. Se revisan para actuar o rechazar

## Por qué

Precisa el alcance de #42 y #78. Aparece un tipo que no estaba: **pedir que se abra una mesa**, que engancha directamente con #72 — un jugador lo solicita, un admin crea la mesa en `Unassigned` y le asigna un master. Los dos flujos son el mismo circuito visto desde sus dos puntas.
