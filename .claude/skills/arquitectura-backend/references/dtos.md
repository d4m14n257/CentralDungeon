# 2.3 DTOs: tipado explícito de todo lo que cruza HTTP

> Parte de la skill `arquitectura-backend`. Se movió desde `docs/` en F4.0 y conserva su numeración original, que es la que citan el código y `decisiones.md`.


Regla base: **ningún endpoint devuelve un tipo inferido, genérico o abierto.** Nada de `Map<String, Object>`, nada de `Object`, nada de `ResponseEntity<?>`, nada de `@Entity` serializada. Todo lo que entra o sale por HTTP tiene un `record` con nombre propio en el `dto/` de su feature.

El objetivo no es ceremonia: es que el contrato de la API esté escrito en algún lado y el compilador lo verifique. Es la contraparte exacta de la disciplina de tipos del frontend (§3.2) — del lado Java el contrato se declara, del lado TypeScript se refleja y se deriva.

**Nomenclatura** (dentro de `<feature>/dto/`):

| Sufijo | Rol | Ejemplo |
|---|---|---|
| `...Request` | entrada de creación o actualización | `CreateGameTableRequest` |
| `...Response` | salida estándar de un recurso | `GameTableResponse` |
| `...SummaryResponse` | versión reducida, para listados y referencias anidadas | `GameTableSummaryResponse` |
| `...DetailResponse` | versión ampliada, para la vista de detalle | `GameTableDetailResponse` |
| `...Command` | entrada de un service cuando no coincide con el `Request` HTTP | `RegisterPlayerCommand` |

**Reglas**

- **Entrada y salida son records distintos**, aunque hoy tengan los mismos campos. Se separan porque evolucionan por motivos distintos: al `Request` se le agregan validaciones, al `Response` se le agregan campos derivados.
- **Un `Response` no expone nada que su consumidor no deba ver.** `UserResponse` no lleva `discordId` ni `status`, porque el listado público de jugadores de una mesa no los necesita; `UserDetailResponse` sí.
- **Listado y detalle son DTOs distintos.** Devolver el detalle completo en una página de 50 mesas es cargar 50 veces relaciones que nadie va a mirar.
- **Nada de entidades anidadas dentro de un DTO.** Si `GameTableResponse` necesita a su master, lleva un `MasterSummaryResponse`, no un `User`.
- **Las colecciones nunca son `null`**: `List.of()` vacía. El frontend no debería tener que distinguir "sin tags" de "tags no cargados".
- **Los campos opcionales se anotan** con `@Nullable` (JSpecify). No se usa `Optional<T>` como campo de un record de DTO: `Optional` está pensado para retornos, no para serialización.
- **La validación Jakarta vive en el `Request`** (`@NotBlank`, `@Size`, `@Positive`) y el controller lo recibe con `@Valid`. El service no revalida formato; sí valida reglas de negocio (existencia, permisos, transiciones de estado).
- **Un DTO no tiene lógica**: ni métodos de cálculo, ni acceso a repositorios, ni fábricas estáticas que consulten algo. Si un campo hay que derivarlo, lo deriva el mapper o el service.
- **Un DTO que usan dos features no vive en el `dto/` de ninguna**: sube a `common/model/`. Hoy el único caso es `PageResponse<T>`.
- Los enums viajan como `String` (`@Enumerated(EnumType.STRING)`, §2.2) y del lado TypeScript se modelan como unión de literales, no como `enum` (§3.2).
