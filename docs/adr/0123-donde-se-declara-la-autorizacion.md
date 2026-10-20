---
adr: 123
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0123: Dónde se declara la autorización

## Decisión

**En el método concreto del controller**, con `@PreAuthorize`. En `SecurityConfig` solo queda lo transversal: stateless, CORS y qué es público. **Nunca una lista de rutas por rol**

## Por qué

El intento en Java tenía un `Routes.java` con listas de endpoints por rol, y falló del peor modo posible: cinco de sus seis rutas estaban escritas **sin barra inicial** (`"tables/master/list"`), así que no matcheaban ninguna petición, caían en `anyRequest().authenticated()` y cualquier usuario autenticado alcanzaba los endpoints de master y de admin. La causa de fondo no es el typo, es la distancia: la regla vivía en otro archivo y nada obligaba a que coincidiera con el mapping. Pegada al método, el permiso se lee junto al endpoint y no puede quedar huérfana. Refuerza la regla dura 4 y el riesgo del CVE-2025-41248.
