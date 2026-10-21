---
adr: 3
titulo: Modelo de datos y persistencia
estado: Vigente
actualizada: 2026-10-20
absorbe: [2, 3, 8, 9, 10, 11, 12, 22, 25, 62, 124, 182, 208]
---

# ADR-03: Modelo de datos y persistencia

> Las convenciones transversales del schema y del acceso a datos: dónde vive la lógica, cómo se consulta, cómo se migra, cómo se generan los ids, cómo se guardan enums, fechas, borrados y texto enriquecido. Las aplica `arquitectura` §2.2 y §4, con las reglas duras 8, 9 y 10; esta ADR dice *qué* se decidió y *por qué*.

## §1 Nada de lógica de negocio en la base de datos

**Decisión.** Ni triggers ni stored procedures (regla dura 8). Lo que hacían los tres triggers heredados (`create_new_user`, `create_player_in_table`, `players_in_table`) vive en el service layer (`arquitectura` §4.5). La tabla `Players_Table` desaparece: el número de jugadores de una mesa se deriva con `COUNT`. Tampoco hay `ON DELETE CASCADE`: la cascada es del service (§7).

**Por qué.** Los triggers eran invisibles desde Java, no los cubría ningún test y no se versionaban junto al dominio. El contador era una denormalización mantenida por trigger: derivarlo elimina de raíz la inconsistencia que el trigger existía para prevenir. Y un objeto de base que no está en el repo deja la aplicación sin poder arrancar (§4).

*Antes: #3, #11.*

## §2 Spring Data JPA, y todo `@Query` con parámetros nombrados

**Decisión.** Entidades reales con `JpaRepository`; no `JdbcTemplate`. Todo `@Query` usa **parámetros nombrados** (`:tableId` con `@Param`), nunca posicionales, y **nunca se concatena input** en una consulta.

**Por qué.** El `JdbcTemplate` a mano fue la causa directa de los bugs del intento previo, y no por inyección —`?` sí parametriza— sino por fallas silenciosas: una consulta con seis placeholders y cinco argumentos corría todos una posición sin que nada lo detectara, `queryForList` con un POJO reventaba en runtime porque mapea una sola columna, y otra consulta ni parseaba. Los parámetros nombrados eliminan la clase entera del desalineamiento, y JPA elimina el `RowMapper` escrito a mano. La inyección solo vuelve si alguien concatena, y por eso está prohibido.

*Antes: #2, #124.*

## §3 El schema lo lleva Flyway, con `ddl-auto: validate`

**Decisión.** Todo cambio de schema es una migración Flyway nueva; una migración aplicada no se edita y nunca hay `ddl-auto: update` (regla dura 9). Hibernate corre con `ddl-auto: validate`.

**Por qué.** El proyecto viejo tenía dos `database.sql` en dos repos, ambos desactualizados y uno con errores de sintaxis que impedían ejecutarlo. Con Flyway el schema tiene una única historia versionada, y con `validate` el arranque falla si una entidad no coincide con su tabla. Una migración aplicada no se toca porque su checksum cubre el archivo entero, comentarios incluidos.

*Antes: #12.*

## §4 Los ids son UUID v7 generados en la aplicación

**Decisión.** Cada id es un UUID v7 que genera la aplicación, de tipo `String`, en columnas `VARCHAR(64)`.

**Por qué.** El schema viejo dependía del stored procedure `generate_base64_id`, que no estaba versionado en ningún repo: la app no arrancaba sin un objeto de base que no existía en el `database.sql`. `VARCHAR(64)` se conserva para no romper los ids existentes. Que un id sea impredecible es defensa en profundidad, no autorización (ADR-05 §5).

*Antes: #9.*

## §5 Los valores fijos son `VARCHAR(32)` con un enum de Java; los que crecen, un catálogo

**Decisión.** Ningún `ENUM` de MySQL: la columna es `VARCHAR(32)` y el conjunto de valores, un enum de Java validado en el service. Lo que los admins necesitan ampliar desde la aplicación es una tabla catálogo: el tipo de mesa es `table_types`, una FK simple —una mesa tiene un tipo—, no una tabla puente como tags, sistemas y plataformas, que son multivaluados.

**Por qué.** Agregar un valor a un `ENUM` de MySQL es un `ALTER TABLE` con bloqueo; agregarlo a un enum de Java es una línea. El viejo `tabletype` (Public / First class) era la forma en que los admins separaban mesas por tema, y dos valores fijos se quedaban cortos: como catálogo se agregan tipos sin tocar el schema.

*Antes: #8, #10.*

## §6 Todo timestamp se guarda en UTC y lo convierte el navegador

**Decisión.** No hay columna `timezone` en `users` ni en `game_tables`. Todo timestamp —creación, borrado, agenda, horarios de sesión— se persiste en UTC, la API lo publica en ISO-8601 UTC (`arquitectura` §2.5) y el frontend lo convierte a la zona del navegador.

**Por qué.** La columna existía porque los timestamps se manejaban mal y se le pedía al usuario declarar su zona, en una comunidad repartida por Latinoamérica y algo de Europa. Guardar en UTC y convertir en el cliente resuelve el problema de raíz, no obliga a configurar nada y sigue siendo correcto si la persona viaja o se muda.

*Antes: #22.*

## §7 El borrado es lógico, con fecha, y la cascada la hace el service

**Decisión.** Además de `status = 'Deleted'`, cada tabla borrable lleva `deleted_at DATETIME NULL`. Borrar una mesa marca como borradas sus filas de `masters` y sus `table_registrations` en una sola transacción, desde el service (ADR-07 §6).

**Por qué.** El `status` dice *que* algo está borrado pero no *cuándo*, y sin fecha no se puede purgar, auditar ni deshacer. La cascada es lógica de negocio, así que vive en el service con sus tests y no en un `ON DELETE CASCADE`; la marca compartida permite reconocer qué cayó junto.

*Antes: #25.*

## §8 El texto enriquecido es `LONGTEXT` en su propia fila, y el adjunto es opcional

**Decisión.** El texto con que el jugador se postula va en `table_registrations` y los requisitos de la mesa, en la mesa, como `LONGTEXT`; nunca es una fila de `files`. El archivo adjunto pasa a ser opcional: según el master, una postulación pide archivos, solo texto o ambas cosas, y las dos vías conviven en la misma solicitud. El texto se sanitiza al guardar y al servir (ADR-05 §6).

**Por qué.** El texto enriquecido no es un archivo: no ocupa disco, no se descarga y no participa de la cuota (ADR-13 §3). Como se renderiza en el navegador, es la superficie de XSS más directa del sistema.

*Antes: #62.*

## §9 Ninguna entidad se llama como una clase de la plataforma

**Decisión.** La entidad de `systems` es `GameSystem` y la de las mesas, `GameTable`: nunca `System` ni `Table`.

**Por qué.** Una clase `System` en `com.centraldungeon.catalogs` tapa `java.lang.System` para todo el paquete, y `Table` choca con `jakarta.persistence.Table`. El nombre de la tabla puede ser el corto; el de la clase, no.

*Antes: #182.*

## §10 `@Transactional` va en el método que llama el scheduler

**Decisión.** En un job `@Scheduled` que delega en otro método de la misma clase, la anotación va en el método que invoca el scheduler, no solo en el que hace el trabajo. La cobertura de un job que muta filas incluye un test de integración que llama a ese método y después consulta la base.

**Por qué.** Una llamada dentro de la misma clase es autoinvocación y no pasa por el proxy, así que la transacción nunca se abre: la consulta corre en una de lectura, las entidades vuelven desprendidas y marcarlas no escribe nada. El job de purga de archivos informaba un conteo sin cambiar una fila, sin lanzar ni loguear. Un unitario no lo ve porque mockea el repositorio.

*Antes: #208.*
