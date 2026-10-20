---
adr: 120
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0120: Forma de la respuesta exitosa

## Decisión

**El DTO desnudo, sin envoltura.** Los errores, `ProblemDetail` (RFC 9457). La paginación, `PageResponse<T>`

## Por qué

El intento en Java envolvía todo en `ResponseData<T>(message, response, status)`. Un formato uniforme suena bien hasta que el status existe en dos lugares y se contradicen: `TablesController` devolvía `206 Partial Content` en HTTP con un `204` escrito en el cuerpo. Además obliga al frontend a desenvolver en cada llamada y a que el tipo derivado de §3.2 cargue una capa que no aporta información. HTTP ya tiene un canal para el estado y Spring ya genera `ProblemDetail` para los errores.
