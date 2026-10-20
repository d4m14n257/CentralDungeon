---
adr: 212
estado: Aceptada
fecha: 2026-09-04
---

# ADR-0212: Destino de la notificación TaskPublished

## Decisión

**Al detalle público de la mesa, `/tables/:id`**, y no a `/my/tables/:id`

## Por qué

El mismo aviso le llega a un candidato y a un jugador: una petición de audiencia `Candidates` se publica para gente que todavía no está en la mesa, y un candidato no tiene entrada en `/my/tables`. El detalle público muestra a los dos lo que la mesa les pide, así que es el único destino que sirve para ambos sin partir el tipo de notificación en dos (#63, #206)
