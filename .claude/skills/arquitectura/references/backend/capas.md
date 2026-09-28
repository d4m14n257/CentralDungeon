# 2.2 Reglas por capa

> Parte de la skill `arquitectura` (#274). Se movió desde `docs/` en F4.0 y conserva su numeración original, que es la que citan el código y `decisiones.md`.


**Controller** (`*Controller.java`)
- Solo HTTP: recibe DTO validado, llama a **un** service, devuelve DTO + status code explícito.
- Nunca inyecta un `Repository`. Nunca contiene `if` de negocio. Nunca devuelve una `@Entity`.
- **Clase concreta, sin interfaz de contrato** (#119). El contrato publicado es el OpenAPI que genera springdoc desde este archivo, más los `record` de §2.3.
- Anotaciones de autorización (`@PreAuthorize`) van acá, **en el método concreto** — nunca en una interfaz, una superclase genérica ni una lista de rutas aparte (§2.6).
- La identidad del actor entra por `@AuthenticationPrincipal`, jamás como `@PathVariable` ni en el cuerpo.
- Un `@RestController` por agregado, no por tabla: `GameTableController` cubre mesa + horario + masters porque son el mismo agregado.

**Service** (`*Service.java`)
- Dueño de la transacción: `@Transactional` en escritura, `@Transactional(readOnly = true)` en lectura.
- Dueño de la lógica de negocio, incluida toda la que antes vivía en triggers de MySQL (skill `arquitectura` §4.5).
- Lanza excepciones de `common/exception`, nunca devuelve `null` para señalar "no existe".
- Puede llamar a otros services; **no puede llamar a un controller**.
- Es la única capa que se testea obligatoriamente con unitarios.

**Repository** (`*Repository.java`)
- Interfaz `JpaRepository<Entity, String>` (los IDs son `String`, ver skill `arquitectura` §4.1).
- Query methods derivados por defecto; `@Query` (JPQL) solo cuando el derivado no alcanza. SQL nativo solo si JPQL no puede expresarlo, y con un comentario que diga por qué.
- **Todo `@Query` usa parámetros nombrados** (`:tableId` + `@Param`), nunca posicionales y **nunca concatenación de strings** (#124). Los posicionales fueron una fuente real de bugs en el intento previo: una consulta pasaba cinco argumentos para seis placeholders y todos quedaban corridos una posición, sin que nada lo detectara.
- Sin lógica. Sin `@Transactional`.

**Entity** (`User.java`, `GameTable.java`, …)
- Nombre en singular, sin sufijo. Extiende `BaseEntity` salvo las tablas puente con clave compuesta.
- `FetchType.LAZY` por defecto en toda relación — `EAGER` solo con justificación escrita.
- Enums con `@Enumerated(EnumType.STRING)`, siempre.
- No se exponen fuera del paquete de su feature: el resto del sistema consume DTOs.

**DTO** (`dto/*.java`)
- `record`, inmutable. Sufijo `Request` (entrada) o `Response` (salida) — separados aunque los campos coincidan hoy.
- Validación con anotaciones Jakarta en el record de entrada, `@Valid` en el controller.
- Detalle completo de nomenclatura y reglas en §2.3.

**Mapper** (`*Mapper.java`)
- MapStruct, `componentModel = "spring"`. Solo entity↔DTO, sin lógica ni acceso a repositorios.
