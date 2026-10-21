---
adr: 4
titulo: Backend y contrato de la API
estado: Vigente
actualizada: 2026-10-20
absorbe: [13, 21, 119, 120, 173, 187, 188, 189, 190, 253]
---

# ADR-04: Backend y contrato de la API

> Cómo se organiza el código, cuándo se abstrae y qué forma tiene lo que cruza HTTP: respuestas, errores, paginación y edición. Lo aplica `arquitectura` §2.1–§2.5 con las reglas duras 1 a 4; esta ADR dice *qué* se decidió y *por qué*.

## §1 El código se organiza por feature, con las capas adentro

**Decisión.** En los dos lados, una carpeta por feature de dominio: `com.centraldungeon.<feature>/` con sus capas adentro más un `common/` transversal, y `src/features/<dominio>/` en el frontend (`arquitectura` §2.1 y §3.1).

**Por qué.** Un `controller/` con nueve controllers de dominios distintos obliga a saltar entre carpetas para tocar una sola funcionalidad. Por feature, agregar o quitar una funcionalidad es agregar o quitar una carpeta.

*Antes: #13.*

## §2 Se abstrae lo idéntico por definición, y los controllers son clases concretas

**Decisión.** Interfaz solo con más de una implementación real; clase abstracta genérica solo cuando la misma forma se repite idéntica en 3+ features y ya se vio repetida (regla dura 4, `arquitectura` §2.4). Los controllers son clases concretas, sin interfaz de contrato: el contrato publicado es el OpenAPI que springdoc genera desde ellos más los `record` con nombre propio de la regla dura 3. `@PreAuthorize` nunca se hereda ni vive en una interfaz o superclase (ADR-05 §4).

**Por qué.** Los dos extremos duelen: duplicar CRUD idéntico entre `systems`, `tags` y `platforms` multiplica el mismo bug, y abstraer services que solo se parecen acopla dominios que divergen en el primer requerimiento. La interfaz por controller del intento previo buscaba un contrato legible, pero ese objetivo ya tiene dos guardianes que no pueden desincronizarse —el OpenAPI generado del código real y los DTOs tipados—, y la interfaz agrega un tercer archivo que sí diverge. Además, `@PreAuthorize` en una interfaz o superclase genérica es un bypass de autorización documentado (CVE-2025-41248), y el permiso tiene que verse en el archivo que declara el endpoint.

*Antes: #21, #119.*

## §3 La respuesta exitosa es el DTO desnudo; el error, `ProblemDetail`

**Decisión.** Sin envoltura: el cuerpo es el `record` de la respuesta. Los errores son `ProblemDetail` (RFC 9457) y las listas paginadas, `PageResponse<T>`.

**Por qué.** El intento previo envolvía todo en `ResponseData<T>(message, response, status)`, y con el status en dos lugares se contradecían: un `206` en HTTP con un `204` en el cuerpo. La envoltura obliga además a desenvolver en cada llamada y a que el tipo derivado del frontend cargue una capa sin información. HTTP ya tiene un canal para el estado y Spring ya genera `ProblemDetail`.

*Antes: #120.*

## §4 Los errores de negocio son una jerarquía sellada que viaja con código y parámetros

**Decisión.** `ApiException` es sellada y permite cinco subclases: `NotFoundException`, `ConflictException`, `ForbiddenActionException`, `UnauthorizedException` e `InvalidRequestException`. **`InvalidRequestException` → 400** es para lo mal formado en la relación entre campos, no en un campo suelto: dos franjas de la misma mesa que se pisan (ADR-08 §2). **`ConflictException` → 409** acepta un `errorCode` propio, el primero `SCHEDULE_CONFLICT`. Cada error viaja como `errorCode` más `errorParams`, y el `message` es inglés, para el log (ADR-23 §3). En el frontend, `EXPLAINED_ERROR_CODES` de `config/query.ts` es la lista corta de códigos cuya oración se arma desde el código y sus parámetros; el resto muestra el aviso genérico.

**Por qué.** Bean Validation ve un campo a la vez, y un 409 significa «el estado del sistema dice que no»: dos franjas que se solapan no chocan con nadie, describen una semana que no se puede jugar. Se suma a la lista sellada en vez de reusar el 409 porque el `switch` exhaustivo sobre `ApiException` hace que agregar un error sea una decisión y no un descuido. El 409 lleva código porque el motivo tiene que verse en pantalla —el choque nombra la mesa con la que se pisa, y un «no se pudo guardar» esconde lo único que la persona puede resolver (`diseno` §1, principio 2)—; la lista es corta porque solo se explica lo que el backend se comprometió a codificar.

*Antes: #187, #188.*

## §5 Un cuerpo malformado es 400, y `PlatformRole` cruza HTTP con `@JsonValue`

**Decisión.** `GlobalExceptionHandler.handleUnreadableBody` mapea `HttpMessageNotReadableException` a `400 VALIDATION_ERROR` con `detail` genérico en todo endpoint, gemelo de `handleTypeMismatch` para path variables y query params. `PlatformRole.roleName()` lleva `@JsonValue`, así que el mismo método fija la ortografía de ida y de vuelta (`"Player"`). Un test recorre las cuatro constantes. Asimetría deliberada: `"ADMIN"` es 400 en un cuerpo JSON pero sí resuelve en el `?q=` del buscador (`/role Admin`), porque una caja de búsqueda tiene que sobrevivir a que la escriban.

**Por qué.** `PlatformRole` es el único enum cuyas constantes (`PLAYER`…`OWNER`) difieren de lo que publica la API; sin `@JsonValue`, Jackson deserializaba por `Enum.name()` y todo grant y revoke respondía 400 mientras la respuesta sí decía `"Player"`. Ningún test podía verlo: el backend llama al service directo y el frontend mockea el `fetch`. Y un cuerpo ilegible no es un fallo del servidor: era el mismo agujero que `handleTypeMismatch` ya cerraba para la URL.

*Antes: #253.*

## §6 Se pagina por offset, con un tope y un orden declarado

**Decisión.** `PageResponse` con `page`, `size` y `totalElements`. Los listados de lectura —explorador, mis mesas, notificaciones, resultados de un buscador— traen más con **«Ver más»** (`useInfiniteQuery`); las listas de trabajo de admin usan **página numerada con el total a la vista** (ADR-21 §11). Nunca scroll infinito. Tamaños en `config/pagination.ts`: explorador 12, listas 20, admin 25, selector 8. En el servidor, dos reglas duras: **el tamaño se topa en 100** (`spring.data.web.pageable.max-page-size`) y **todo endpoint paginado declara su orden por defecto con `@PageableDefault`, con desempate por `id`**.

**Por qué.** Lo que keyset resuelve —el `OFFSET` grande— aparece con volúmenes que esta comunidad no tiene, y a cambio se perderían el total y el salto de página, que sí se usan. Quien explora mira hasta que algo le interesa y «Ver más» no le corta el hilo; quien modera trabaja una cola y necesita saber cuánto falta y volver a donde estaba. El scroll infinito se dispara solo, deja el pie inalcanzable y nunca dice cuánto falta. El tope evita que un `?size=100000` vuelque una tabla, y sin `ORDER BY` estable las páginas repiten o saltean filas (ADR-07 §9).

*Antes: #173.*

## §7 Editar una mesa reemplaza el conjunto entero, y las filas que sobran se marcan

**Decisión.** `PUT /api/v1/game-tables/{id}` es un **reemplazo completo**: un campo ausente vacía. Solo se permite en `Preparation` y `ChangesRequested`. `UpdateGameTableRequest` es un `record` propio, no un alias de `CreateGameTableRequest`. La agenda y los vínculos de catálogo se reemplazan como conjunto (`TableScheduleService.replace`, `TableCatalogService.replaceLinks`): lo que está y se quiere revive, lo que falta se inserta y lo que sobra se **marca** (`Deleted` / `Removed`), nunca se borra.

**Por qué.** El wizard muestra la mesa entera y la manda entera, así que «ausente» solo puede significar vacío: como parche no habría forma de quitar la última franja ni el último tag. Los dos `record` son mensajes distintos, y un alias propagaría en silencio el día que crear acepte algo que editar no. Las reglas de la agenda son del conjunto (ADR-08 §2), así que validar franja por franja dejaría pasar un par roto. Y la clave de `table_schedules` es `(mesa, día, hora)` y la de los puentes `(mesa, valor)`: sacar el martes 20:00 y volver a ponerlo chocaría con la fila recién borrada; marcando, es un `UPDATE`, y queda el registro del que salieron las sesiones ya materializadas (ADR-08 §3, ADR-08 §3).

*Antes: #189, #190.*
