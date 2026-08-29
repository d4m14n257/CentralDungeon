---
name: nuevo-endpoint-java
description: Scaffolds a new Spring Boot REST endpoint (controller, service, repository, DTO, mapper) inside its feature package, following CentralDungeon's layered architecture. Use when adding an endpoint to backend/ (Java/Spring Boot).
---

# Nuevo endpoint Java (Spring Boot)

Sigue `docs/arquitectura.md` §2. Léelo si no lo tienes fresco en contexto.

## Reglas fijas

1. **El código se organiza por feature, no por capa.** Todo el endpoint nuevo vive en `com.centraldungeon.<feature>/` (`users`, `tables`, `registrations`, `catalogs`, `files`, `comments`, `requests`, `notifications`). Lo transversal va en `common/`.
2. **El controller nunca llama a un repository.** Siempre pasa por un service, incluso para una lectura trivial.
3. **Una `@Entity` nunca cruza la frontera HTTP.** Entrada y salida son `record` en `dto/`, con sufijo `Request` o `Response`. **Nada de tipos abiertos**: ni `Map<String, Object>`, ni `Object`, ni `ResponseEntity<?>`. Listado y detalle son DTOs distintos (`...SummaryResponse` / `...DetailResponse`). Reglas completas en `docs/arquitectura.md` §2.3.
4. El service es dueño de la transacción (`@Transactional`, o `readOnly = true` en lectura) y de la lógica de negocio — incluida la que antes vivía en triggers (`docs/modelo-datos.md` §5).
5. El repository es una interfaz `JpaRepository<Entity, String>` (los IDs son `String`). Sin lógica, sin `@Transactional`. Todo `@Query` con **parámetros nombrados** (`:tableId` + `@Param`), nunca posicionales ni concatenación de strings (#124).
6. Errores: excepciones de `common/exception`, nunca `null` para decir "no existe". El `GlobalExceptionHandler` las traduce a `ProblemDetail`.
7. Colecciones siempre paginadas (`?page=&size=&sort=`), devolviendo `PageResponse`.
8. El `user_id` del usuario autenticado sale del JWT vía `@AuthenticationPrincipal`, **nunca** de un parámetro de ruta.
9. **El rol no es la pertenencia** (#121). `hasRole('MASTER')` no dice "de *esta* mesa". Todo acceso a un recurso concreto filtra por el actor: el actor entra en el `WHERE` (`findByIdAndOwnerId`) o el service verifica pertenencia **antes** de tocar nada y lanza si no corresponde. Nunca `findById(id)` seguido de `save()`.
10. **No abstraigas por parecido** (§2.4): interfaz solo si hay más de una implementación real, clase abstracta genérica solo si la misma forma se repite idéntica en 3+ features y ya la viste repetida. **El controller es una clase concreta, sin interfaz de contrato** (#119). El `@PreAuthorize` va en el **método concreto**, nunca en una interfaz, una superclase genérica ni una lista de rutas en `SecurityConfig` (#123).
11. **La respuesta exitosa es el DTO desnudo**, sin envoltura tipo `ResponseData<T>` (#120). El status vive en HTTP; los errores son `ProblemDetail`.
12. Stack: Java 25 / Spring Boot 4.1 (§1.1). Jackson es **3** (`tools.jackson.*`), lo nullable se anota con **JSpecify**, y `RestTemplate` ya no se autoconfigura.

## Pasos

1. Confirmar contra `docs/modelo-datos.md` qué tablas toca el endpoint.
2. Si el schema cambia: migración Flyway nueva en `db/migration/` + actualizar `docs/modelo-datos.md` (ver skill `er-diagram-sync`). Nunca editar una migración ya aplicada.
3. Crear o ajustar la `@Entity` en el paquete de la feature (`LAZY` por defecto, enums con `@Enumerated(EnumType.STRING)`).
4. Crear el `JpaRepository`.
5. Crear los DTO en `dto/` (request y response separados, validación Jakarta en el de entrada).
6. Crear el mapper MapStruct si hace falta traducción no trivial.
7. Implementar el método de negocio en el service.
8. Crear el método del controller: ruta bajo `/api/v1`, recurso plural en kebab-case, status code explícito (`201` + `Location` al crear, `204` sin cuerpo), `@PreAuthorize` para el rol global.
9. Escribir el test del service (skill `tests-java`) antes de dar el endpoint por terminado.
