---
adr: 124
estado: Aceptada
fecha: 2026-08-29
---

# ADR-0124: Acceso a datos y consultas

## Decisión

**Spring Data JPA** (ya fijado en #2). Todo `@Query` con **parámetros nombrados** (`:tableId` + `@Param`), nunca posicionales ni concatenación de strings

## Por qué

Precisa el motivo, que estaba mal atribuido: `JdbcTemplate` con `?` **sí** parametriza, así que la inyección SQL no fue el modo de falla real ni en Node ni en Java. Lo que falló fue otra cosa, y peor porque era silenciosa: en `TableRepository.tableList` la consulta tenía seis placeholders y se pasaban cinco argumentos —faltaba `utc`—, así que todos quedaban corridos una posición sin que nada lo detectara; `queryForList(sql, TableAvailable.class, …)` mapea una sola columna, no una fila a un POJO, así que reventaba en runtime; y `joinedTables` ni siquiera parseaba, le faltaba una coma. Los parámetros nombrados eliminan la clase entera de bug del desalineamiento, y JPA elimina el mapeo a mano. La inyección solo vuelve a ser un riesgo si alguien concatena input en un `@Query`, y por eso queda prohibido explícitamente.
