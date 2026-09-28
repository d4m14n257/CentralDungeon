# 2.5 Contrato de la API

> Parte de la skill `arquitectura` (#274). Se movió desde `docs/` en F4.0 y conserva su numeración original, que es la que citan el código y `decisiones.md`.


- Base: `/api/v1`. Recursos en plural y kebab-case: `/api/v1/game-tables/{id}/registrations`.
- Status codes: `200` lectura, `201` + header `Location` en creación, `204` en borrado/actualización sin cuerpo, `400` validación, `401` sin token, `403` sin permiso, `404` inexistente, `409` conflicto de estado.
- **Éxito: el DTO desnudo, sin envoltura** (#120). Nada de un `ResponseData<T>` con `message`/`status` adentro del cuerpo: el status vive en HTTP y en un solo lugar. El intento en Java tenía esa envoltura y ya se contradecía sola — devolvía `206 Partial Content` en la respuesta HTTP con un `204` escrito en el cuerpo.
- Errores: siempre `ProblemDetail` (RFC 9457, que obsoleta al 7807) producido por `GlobalExceptionHandler`. Nunca un string suelto, nunca un `418` genérico (el backend Node lo usaba como error comodín).
- Toda colección va paginada (`?page=&size=&sort=`) y devuelve `PageResponse`. El backend viejo no tenía paginación en ningún endpoint y era un TODO explícito suyo. Dos reglas que la acompañan (#173): **el tamaño de página se topa en 100** (`spring.data.web.pageable.max-page-size`), y **todo endpoint paginado declara su orden por defecto** con `@PageableDefault`, **con desempate por `id`** — sin orden explícito las páginas pueden repetir o saltear filas (#171).
- **Toda búsqueda entra por un solo parámetro, `?q=`**, escrito en el lenguaje de `common/search/` (#164): texto suelto es el criterio básico del endpoint, `/campo valor` acota a un campo, y `and`/`or` combinan. Cada endpoint declara los campos que acepta en un enum propio (`UserSearchField`) — un `/campo` que no esté ahí se busca como texto literal, nunca es un `400`.
- Fechas en ISO-8601 UTC. La conversión a la zona del usuario es responsabilidad del frontend.
