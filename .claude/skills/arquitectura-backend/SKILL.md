---
name: arquitectura-backend
description: CentralDungeon's backend architecture rules — feature packages, what each layer may do, DTOs, the API contract, security and ownership, testing and Javadoc. Use before writing, changing or reviewing any Java code in backend/, and whenever code or decisiones.md cites "arquitectura-backend §2.x".
---

# Arquitectura del backend

Las reglas de cómo se escribe el backend de CentralDungeon. **Son la fuente**: el código y `docs/decisiones.md` citan estas secciones por su número (`arquitectura-backend §2.3`), que es el mismo que tenían cuando vivían en `docs/arquitectura.md`. El stack y sus versiones siguen en `docs/arquitectura.md` §1; el porqué de cada regla, en la decisión `#n` que se cita al lado.

## Reglas fijas

1. **El código se organiza por feature, no por capa.** Todo vive en `com.centraldungeon.<feature>/` (hoy: `adminqueue`, `approvals`, `auth`, `catalogs`, `dashboard`, `files`, `health`, `notifications`, `profiles`, `registrations`, `settings`, `tables`, `tasks`, `users`), con sus capas adentro. Lo transversal va en `common/`. → §2.1
2. **El controller nunca llama a un repository.** Siempre pasa por un service, incluso para una lectura trivial. → §2.2
3. **Una `@Entity` nunca cruza la frontera HTTP.** Entrada y salida son `record` en `dto/`, con sufijo `Request` o `Response`. **Nada de tipos abiertos**: ni `Map<String, Object>`, ni `Object`, ni `ResponseEntity<?>`. Listado y detalle son DTOs distintos (`…SummaryResponse` / `…DetailResponse`). → §2.3
4. **El service es dueño de la transacción** (`@Transactional`, o `readOnly = true` en lectura) y de la lógica de negocio, incluida la que antes vivía en triggers (skill `modelo-datos` §5). → §2.2
5. **El repository es una interfaz `JpaRepository<Entity, String>`** (los ids son `String`). Sin lógica, sin `@Transactional`. Todo `@Query` con **parámetros nombrados** (`:tableId` + `@Param`), nunca posicionales ni concatenación (#124). → §2.2
6. **Errores**: excepciones de `common/exception`, nunca `null` para decir «no existe». El `GlobalExceptionHandler` las traduce a `ProblemDetail`, con un **código y sus parámetros**, nunca una frase: la arma el frontend (#197). → §2.5
7. **Colecciones siempre paginadas** (`?page=&size=&sort=`), devolviendo `PageResponse`. → §2.5
8. **El actor sale del JWT** vía `@AuthenticationPrincipal`, **nunca** de un parámetro de ruta (#121). → §2.6
9. **El rol no es la pertenencia** (#121, #135). `hasRole('MASTER')` no dice «de *esta* mesa». Todo acceso a un recurso concreto filtra por el actor: el actor entra en el `WHERE` (`findByIdAndOwnerId`) o el service verifica pertenencia **antes** de tocar nada. Nunca `findById(id)` seguido de `save()`. El JWT afirma identidad, no autorización: roles y `status` se releen de la base (#122). → §2.6
10. **No se abstrae por parecido.** Interfaz solo si hay más de una implementación real; clase abstracta solo si la misma forma se repite idéntica en 3+ features y ya se vio repetida. **El controller es una clase concreta, sin interfaz de contrato** (#119). El `@PreAuthorize` va en el **método concreto**, nunca en una interfaz, una superclase ni una lista de rutas (#123), y enumera sus roles: no hay `RoleHierarchy` (`hasAnyRole('ADMIN','OWNER')`). → §2.4, §2.6
11. **La respuesta exitosa es el DTO desnudo**, sin envoltura `ResponseData<T>` (#120). El status vive en HTTP. → §2.5
12. **Toda regla de negocio llega con su test unitario**, y lo que depende del motor real, con su IT de Testcontainers. → §2.7 y la skill `tests-java`
13. **Todo lo `public` y `protected` lleva Javadoc en inglés**, incluidos los componentes de cada `record` y los getters. Dice qué hace y por qué existe, y cita la decisión con su `#n`. → §2.8
14. **Stack**: Java 25 / Spring Boot 4.1. Jackson es **3** (`tools.jackson.*`), lo nullable se anota con **JSpecify**, y `RestTemplate` ya no se autoconfigura (`docs/arquitectura.md` §1.1).

## El detalle, por sección

Se lee la referencia de la sección que se está tocando. No hace falta leerlas todas.

| § | Qué cubre | Archivo |
|---|---|---|
| 2.1 | El árbol de paquetes, qué va en cada capa de la feature y qué en `common/` | [`references/paquetes.md`](references/paquetes.md) |
| 2.2 | Reglas por capa: controller, service, repository, entity, mapper | [`references/capas.md`](references/capas.md) |
| 2.3 | DTOs: tipado explícito de todo lo que cruza HTTP, validación, nombres | [`references/dtos.md`](references/dtos.md) |
| 2.4 | Interfaces y clases abstractas: qué se comparte y qué no | [`references/abstraccion.md`](references/abstraccion.md) |
| 2.5 | El contrato de la API: rutas, status, paginación, `ProblemDetail` | [`references/contrato-api.md`](references/contrato-api.md) |
| 2.6 | Seguridad: sesión, JWT, pertenencia, autorización por método (#121–#127) | [`references/seguridad.md`](references/seguridad.md) |
| 2.7 | Testing: unitario, integración, contrato | [`references/testing.md`](references/testing.md) |
| 2.8 | Javadoc: la API pública documentada en el código | [`references/javadoc.md`](references/javadoc.md) |

Para agregar un endpoint entero, el procedimiento está en la skill `nuevo-endpoint-java`; para un cambio de schema, en `er-diagram-sync`.
