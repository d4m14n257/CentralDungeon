---
adr: 119
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0119: Interfaz por controller para declarar el contrato

## Decisión

**No se usa. El controller es una clase concreta**, y el contrato publicado es el OpenAPI que springdoc genera desde ella, más los `record` con nombre propio de la regla dura 3

## Por qué

El patrón venía del intento en Java (`TablesIController`, `FilesIController`, …) y el objetivo era bueno: tener en un archivo legible qué recibe y qué devuelve cada endpoint, para que el frontend sepa el contrato. Pero ese objetivo ya tiene dos guardianes que **no pueden desincronizarse** —el esquema OpenAPI, generado del código real, y los DTOs tipados—, mientras que la interfaz agrega un tercer archivo que sí puede divergir y duplica el trabajo en cada cambio de firma. Además choca con la regla dura 4 (interfaz solo con más de una implementación real) y arrastra un riesgo concreto: poner `@PreAuthorize` en una interfaz o superclase genérica es un bypass de autorización documentado (CVE-2025-41248). Se conserva la intención, no el mecanismo.
