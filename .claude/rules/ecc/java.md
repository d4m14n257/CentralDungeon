---
paths:
  - "**/*.java"
---
# ECC — Java (curado para CentralDungeon)

> Complementa la skill `arquitectura` §2, §5 y §6; **ante un choque, manda ella**. Stack: Java 25, Spring Boot 4.1, **JUnit 6**, Jackson 3, JSpecify. No se adoptan de ECC: la envoltura `ApiResponse`, el nombre de test `method_scenario_expected` (se usa el de §5.1), las carpetas de test por capa ni los mensajes de excepción con frases para el cliente (#197).

## Skills y agentes de ECC escritos para otro stack

- **`ecc:springboot-tdd`, `ecc:springboot-verification` y `ecc:springboot-patterns` están escritas para Spring Boot 3.** Su código no se copia tal cual: en Boot 4 **`@MockBean` no existe** (se usa `@MockitoBean`), los tests van con **JUnit 6**, Jackson es `tools.jackson.*` y lo nullable es JSpecify. Ante la duda, manda `arquitectura` §5.1 y context7.
- **`ecc:database-reviewer` es especialista en PostgreSQL.** Aquí el motor es MySQL 8: sus consejos de índices, tipos o sintaxis se verifican contra MySQL antes de aplicarlos, y la skill de referencia es `ecc:mysql-patterns`. Toda corrección de schema sigue siendo una migración Flyway nueva (regla dura 9).

## Java moderno, donde aclare

- `record` para DTOs y value types (ya es regla en §2.3).
- `sealed` para jerarquías cerradas, con `switch` exhaustivo por pattern matching.
- Pattern matching en `instanceof`, sin cast explícito.
- `switch` como expresión, con flecha.
- Text blocks para strings multilínea (JPQL largo, JSON de tests).
- `var` solo cuando el tipo es evidente a la derecha.

## Inmutabilidad, con la excepción de JPA

- Services, DTOs, value objects y componentes: **campos `final` por defecto** y estado mutable solo si hace falta.
- Lo que sale de una API pública como colección se devuelve como copia (`List.copyOf()`) cuando el original es interno y mutable.
- **Excepción: las `@Entity`.** Hibernate necesita constructor sin argumentos y estado mutable, y sus getters y setters se documentan (§6.1). La regla de inmutabilidad no aplica a ellas.

## Inyección y dependencias

- **Siempre por constructor**, nunca `@Autowired` en un campo. Los campos inyectados son `private final`.

## `Optional`

- Lo devuelven los métodos de búsqueda que pueden no encontrar nada, y se consume con `map`, `flatMap` u `orElseThrow`.
- Nunca `get()` sin comprobar, y nunca `Optional` como campo ni como parámetro.
- «No existe» se expresa con una excepción de `common/exception` (§2.5), no devolviendo `null`.

## Excepciones

- Las de dominio son unchecked y extienden las de `common/exception`.
- Nada de `catch (Exception e)` amplio fuera del `GlobalExceptionHandler`.
- El mensaje de una excepción es para el log, en inglés y con contexto (ids). **El cliente recibe un código y sus parámetros** (#197), nunca ese mensaje.

## Streams

- Pipelines cortos, de 3 o 4 operaciones. Si la lógica se complica, un bucle es más legible.
- Sin efectos colaterales dentro del stream. Referencias a método cuando se leen bien.

## Tests (lo que suma a §5.1)

- `@ParameterizedTest` (`@CsvSource` / `@MethodSource`) cuando el mismo caso cambia solo en los datos.
- `assertThatThrownBy(...)` para los caminos de error, verificando tipo y código.
