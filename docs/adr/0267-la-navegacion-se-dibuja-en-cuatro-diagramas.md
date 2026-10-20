---
adr: 267
estado: Aceptada
fecha: 2026-09-27
modifica: [266]
---

# ADR-0267: La navegación se dibuja en cuatro diagramas con un nodo por ruta, y el 20 se borra

> Precisa #266

## Decisión

`21-navegacion-general` (login → callback → onboarding → `/` → shell → los tres hogares y las transversales), `22-navegacion-jugador`, `23-navegacion-master` (con las siete pestañas) y `24-navegacion-admin`. Cada ruta del router es un nodo; las entradas que vienen de fuera del contexto —un tipo de notificación, un pendiente de la bandeja, `/my/schedule`, el `UserMenu`— son **fichas pegadas a su destino** en vez de un nodo central con flechas cruzando el diagrama. La clasificación de F4.1 (sin puerta, entrada única, callejón, cruce de contexto) va en el sublabel y en los capítulos de `meta.views`

## Por qué

El 20 agrupaba pantallas en cajas («Pantallas del jugador») y por eso **no podía responder la pregunta que lo motivó**: un huérfano es un nodo sin arista de entrada, y dentro de una caja no hay aristas que mirar. Cuatro y no uno porque 38 rutas en un solo lienzo no pasan la legibilidad de escritorio de Archify (texto proyectado ≥ 6 px a 1440×900) ni caben sin scroll. Las fichas repetidas son la forma de dibujar todas las aristas sin cruces: el precio es que un mismo origen aparece varias veces, y se paga porque cada ficha nombra exactamente el caso (`SessionToRecord`, `ScheduleConflict`) que lleva a ese destino. Mismo mecanismo anti-mentira que #266: `meta.repository.revision` y `sources` verificados con `--repo-root`
