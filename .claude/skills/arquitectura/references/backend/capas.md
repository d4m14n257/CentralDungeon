# 2.2 Reglas por capa

> Parte de la skill `arquitectura` (#274). Se movió desde `docs/` en F4.0 y conserva su numeración original, que es la que citan el código y `decisiones.md`.


**Todas las capas**
- Las dependencias entran **por constructor**, en campos `private final`. Nada de `@Autowired` sobre un campo y nada de Lombok: el constructor deja a la vista lo que una clase necesita, y permite armarla en un unitario sin levantar Spring (§5.1). Es como está escrito todo el backend hoy.

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

**Lecturas por página: sin N+1**
- Una página se arma con **un número fijo de consultas, tenga las filas que tenga**. Lo que hace falta por fila —el `Primary` de cada mesa, cuántos jugadores tiene, cuántos candidatos esperan, cuántas veces se usa un valor de catálogo— se resuelve en **una consulta agrupada para la página entera** (`IN (:ids)`, con `GROUP BY` si es un conteo), y el service arma la respuesta desde un `Map` por id. Es la forma de `MasterRepository.findByGameTablesAndType`, `TableRegistrationRepository.countPlayersByTables` y `countPendingByTables`, y `AbstractCatalogService.countUses`.
- Si la fila necesita una asociación, viene en esa misma consulta, con `join fetch` o con `@EntityGraph` en el finder: resolver después un `LAZY` vuelve a meter el N+1 una capa más abajo. La excepción son los conjuntos chicos y cerrados (los tipos de mesa, los admins), donde la caché de primer nivel responde los repetidos dentro de la página; quedarse con el `LAZY` ahí se dice en el Javadoc, para que se lea como decisión y no como descuido.
- Las proyecciones de esas consultas (`TablePlayerCount`, `PendingCandidateCount`, `TaskSubmissionCount`, `CatalogUsageCount`) son `record` internos que **nunca cruzan HTTP** (§2.3).
- **Se mide, no se razona**: el IT cuenta las consultas con `Statistics` de Hibernate (`getPrepareStatementCount` en `AdminQueueServiceIT` y `UserRoleServiceIT`, `getQueryExecutionCount` en `GameTableHistoryIT`). Un `join fetch` justificado solo por leer el mapeo no prueba nada.
- Nació en F3.3, cuando `/admin/tables` pasó a listar todas las mesas (#176): el armado fila por fila hacía cuarenta y una consultas para una página de veinte.

**DTO** (`dto/*.java`)
- `record`, inmutable. Sufijo `Request` (entrada) o `Response` (salida) — separados aunque los campos coincidan hoy.
- Validación con anotaciones Jakarta en el record de entrada, `@Valid` en el controller.
- Detalle completo de nomenclatura y reglas en §2.3.

**Mapper** (`*Mapper.java`)
- MapStruct, `componentModel = "spring"`. Solo entity↔DTO, sin lógica ni acceso a repositorios.
