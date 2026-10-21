---
paths:
  - "backend/**"
---

# Backend — reglas duras

> Se cargan al tocar `backend/`. Conservan el número de regla dura que les da `CLAUDE.md`, que es el que cita el código. El detalle y el porqué están en la skill `arquitectura` §2, §5.1 y §6.1, que se lee antes de escribir; las reglas de Java moderno que suma ECC, en `ecc/java.md`.

**1.** Un controller nunca llama a un repository — siempre pasa por un service, incluso para una lectura trivial.

**2.** Una `@Entity` nunca cruza la frontera HTTP: entrada y salida son DTOs (`record`).

**3.** Ningún endpoint devuelve un tipo inferido o abierto: nada de `Map<String, Object>`, `Object` ni `ResponseEntity<?>`. Todo lo que cruza HTTP tiene su `record` con nombre propio (`arquitectura` §2.3).

**4.** No se abstrae por parecido: interfaz solo si hay más de una implementación real, clase abstracta solo si la misma forma se repite idéntica en 3+ features y ya se vio repetida (§2.4). **Los controllers son clases concretas, sin interfaz de contrato** (ADR-04 §2): el contrato lo publica OpenAPI. La autorización nunca se hereda ni vive en una lista de rutas: cada controller concreto declara su `@PreAuthorize` en el método (ADR-05 §4, CVE-2025-41248).

**7.** Toda regla de negocio nueva llega con su test unitario (`arquitectura` §5.1); lo que depende del motor real, con su IT de Testcontainers. No hay tests en ninguno de los dos repos viejos; ese patrón no se repite.

**10.** Un cambio en una `@Entity` actualiza el modelo de la skill `arquitectura` (§4) y el diagrama ER de `arquitectura` §4.3 en el mismo commit (procedimiento en `arquitectura` §7.3).

**14.** Nada de código copiado literal de `CentralDungeonBackend` (`/Users/d4m14n257/Personal/CentralDungeonBackend`). Solo se rescata la forma conceptual de su capa de config y seguridad; qué se rescata y qué se descarta, en `docs/adr/README.md`.

**18 (la mitad del backend).** El backend no escribe frases que lea una persona (ADR-23 §3): manda un código y sus parámetros en el `ProblemDetail`, y el frontend las arma. Lo único verbatim es lo que escribió otra persona. Los mensajes de excepción son para el log, en inglés y con ids.

Y las del dominio que valen aquí más que en ningún lado, completas en `CLAUDE.md`: **5** (cuatro roles, sin `RoleHierarchy`), **8** (nada de lógica en la base), **13** (sin Campañas ni Temporadas) y **17** (el rol no es la pertenencia; el actor sale del token).
