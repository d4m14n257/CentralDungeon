---
adr: 125
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0125: Los tres tokens

## Decisión

**El de Discord se descarta al terminar el callback.** Access propio corto (~15 min) en memoria del cliente, refresh propio largo y rotativo en cookie `httpOnly` + `SameSite=Strict`. **El refresh es el punto donde se re-afirman `status` y roles**

## Por qué

El token OAuth de Discord sirve para dos preguntas durante el login —quién sos y si sos miembro del guild (#38)— y después no se usa más, porque la sesión pasa a ser propia. Guardarlo obligaría a mantener un segundo ciclo de refresh, con su caducidad y sus fallos, para una capacidad que v1 no tiene; y lo que necesita la integración futura (#88) es un **bot token**, que es de la aplicación, no del usuario, así que conservarlo tampoco acerca ese trabajo. El par access/refresh resuelve la tensión de #122: el access corto acota la ventana y el refresh es el momento natural de volver a preguntar si la persona sigue habilitada. Como el refresh viaja en cookie, **ese endpoint concreto necesita protección CSRF** aunque el resto de la API sea stateless con Bearer.
