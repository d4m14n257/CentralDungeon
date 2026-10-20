---
adr: 127
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0127: CSRF en el endpoint de refresh

## Decisión

**CSRF activo únicamente en `/auth/refresh`**, con el repositorio de tokens de Spring Security; el resto de la API sigue con `csrf.disable()`. Es la **segunda** capa: la primera es el `SameSite=Strict` de #125

## Por qué

Toda la API se autentica con `Authorization: Bearer`, y el navegador **nunca** adjunta ese header por su cuenta, así que no hay credencial automática que un sitio ajeno pueda aprovechar: desactivar CSRF ahí es correcto, no una omisión. El refresh es el único endpoint que se autentica con **cookie**, y las cookies sí viajan solas. Qué se gana protegiéndolo: un sitio ajeno no puede leer la respuesta (lo impide CORS), así que no hay robo de sesión, pero **sí puede forzar la rotación** del refresh y dejar al cliente legítimo con un token viejo — es decir, cerrarle la sesión al usuario y disparar falsas alarmas de reuso. `SameSite=Strict` ya bloquea eso, pero es una defensa que **aplica el navegador**: si falla o el agente la ignora, nada del lado del servidor se entera. El token CSRF es la comprobación que sí ocurre en el servidor.
