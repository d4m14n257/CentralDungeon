---
adr: 122
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0122: Qué afirma el JWT

## Decisión

**Identidad, no autorización.** El token lleva el `sub` y poco más; roles y `status` se leen de la base en cada request, con caché de TTL corto

## Por qué

Un JWT con roles como claims es una foto del momento en que se emitió, y con filter chain stateless no hay revocación: un admin degradado sigue siendo admin, y alguien marcado `Blocked` (#84, #86) sigue entrando, hasta que el token expire. Leer de la base hace que un baneo o una degradación tengan efecto inmediato, y con la escala de esta comunidad es una lectura por PK cacheada. **Es el patrón que sí se rescata del intento en Java**, que ya lo hacía bien: `JwtGenerator` ponía solo `id` como claim y `JwtAuthorizationFilter` hacía `loadUserById` en cada petición.
