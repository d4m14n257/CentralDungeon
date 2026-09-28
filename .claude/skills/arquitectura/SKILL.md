---
name: arquitectura
description: CentralDungeon's whole architecture in one place — backend layers, DTOs, API contract and security (§2), frontend structure, types, state, i18n and forms (§3), the data model, DDL, migrations and business rules (§4), how both sides are tested (§5), how the code is documented with Javadoc and JSDoc (§6), and the step-by-step procedures for a new endpoint, a new component or screen, and an @Entity change (§7). Use before writing, changing or reviewing any code in backend/ or frontend/, before touching an @Entity, a migration, a query or a business rule, before writing tests, and whenever code or decisiones.md cites "arquitectura §n" or roles-y-alcance.md.
---

# Arquitectura

Cómo se escribe CentralDungeon, de punta a punta. **Es la fuente de sus reglas**: el código y `docs/decisiones.md` citan estas secciones por número (`arquitectura §2.3`). Hasta #274 eran siete skills —`arquitectura-backend`, `arquitectura-frontend`, `modelo-datos`, `tests-java`, `nuevo-endpoint-java`, `nuevo-componente-react` y `er-diagram-sync`—; se juntaron porque ninguna se entendía sin las otras, y **los números viejos se conservaron**, de modo que una cita antigua sigue encontrando su sección:

- el backend sigue siendo §2 y el frontend §3;
- el modelo de datos lleva el prefijo `4.`: el viejo `modelo-datos` §5 es §4.5;
- el testing pasó a §5 (el viejo §2.7 es §5.1 y el §3.4 es §5.2);
- la documentación del código pasó a §6 (el viejo §2.8 es §6.1);
- los procedimientos son §7.

El stack y sus versiones están en `docs/arquitectura.md` §1; el porqué de cada regla, en la decisión `#n` que se cita al lado. **Lo visual —tokens, componentes, clases de patrón— es de la skill `diseno`**, y **el entorno local y sus trampas, de `entorno-local`**.

## Reglas fijas — backend (§2)

1. **Paquete por feature, no por capa.** Todo vive en `com.centraldungeon.<feature>/` (hoy: `adminqueue`, `approvals`, `auth`, `catalogs`, `dashboard`, `files`, `health`, `notifications`, `profiles`, `registrations`, `settings`, `tables`, `tasks`, `users`), con sus capas adentro. Lo transversal va en `common/`. → §2.1
2. **El controller nunca llama a un repository.** Siempre pasa por un service, aunque sea una lectura trivial. → §2.2
3. **Una `@Entity` nunca cruza la frontera HTTP.** Entrada y salida son `record` en `dto/`, con sufijo `Request` o `Response`. **Nada de tipos abiertos**: ni `Map<String, Object>`, ni `Object`, ni `ResponseEntity<?>`. Listado y detalle son DTOs distintos (`…SummaryResponse` / `…DetailResponse`). → §2.3
4. **El service es dueño de la transacción** (`@Transactional`, o `readOnly = true` en lectura) y de la lógica de negocio, incluida la que antes vivía en triggers (§4.5). → §2.2
5. **El repository es una interfaz `JpaRepository<Entity, String>`**, sin lógica y sin `@Transactional`. Todo `@Query` lleva **parámetros nombrados** (`:tableId` + `@Param`), nunca posicionales ni concatenación (#124). → §2.2
6. **Errores**: excepciones de `common/exception`, nunca `null` para decir «no existe». El `GlobalExceptionHandler` las traduce a `ProblemDetail` con **un código y sus parámetros**, nunca con una frase: la frase la arma el frontend (#197). → §2.5
7. **Las colecciones van siempre paginadas** (`?page=&size=&sort=`) y devuelven `PageResponse`, con orden por defecto y desempate por `id` (#173). → §2.5
8. **El actor sale del JWT** vía `@AuthenticationPrincipal`, **nunca** de un parámetro de ruta (#121). → §2.6
9. **El rol no es la pertenencia** (#121, #135). Todo acceso a un recurso concreto filtra por el actor: o el actor va en el `WHERE`, o el service verifica la pertenencia **antes** de tocar nada. Roles y `status` se releen de la base en cada request (#122). → §2.6
10. **No se abstrae por parecido.** Una interfaz, solo si hay más de una implementación real; una clase abstracta, solo si la misma forma ya se vio repetida idéntica en 3+ features. **El controller es concreto** (#119), y su `@PreAuthorize` va en el método y enumera sus roles, sin `RoleHierarchy` (#123). → §2.4, §2.6
11. **La respuesta exitosa es el DTO desnudo**, sin envoltura (#120). → §2.5
12. **Stack**: Java 25 / Spring Boot 4.1, Jackson **3** (`tools.jackson.*`) y **JSpecify** para lo nullable (`docs/arquitectura.md` §1.1).

## Reglas fijas — frontend (§3)

13. **Feature-first, con las pantallas aparte.** Lo de dominio va en `src/features/<dominio>/`; **las páginas van en `src/routes/`**. Algo sube a la capa transversal de la raíz recién **cuando una segunda feature lo necesita**. → §3.1.1, §3.1.2
14. **Una feature nunca importa de otra.** Una pantalla compone dominios con bloques `…Section` que reciben un **id**, no la entidad. → §3.1.5
15. **Superficie pública** en `features/<dominio>/index.ts`, que es el único barrel (#114). El `components/` de una feature es plano y va con sufijo. → §3.1.3, §3.1.4
16. **Ruteo**: la página exporta `Component`, se registra con `lazy` bajo su layout, y **todo enlace sale de un builder de `config/paths.ts`**. Cada layout de contexto se cierra con `RequireContext`, que redirige a `/` a quien no tiene el contexto (#269). Eso decide qué se ve, no qué se permite: la autorización es del backend (#103). → §3.1.6
17. **Tipos**: un tipo base por entidad, espejo del `…Response`; las variantes se derivan, nunca se re-declaran. Los enums son uniones de literales. Nunca `any`. → §3.2
18. **Datos de servidor, solo con TanStack Query**, con query keys de la fábrica y `staleTime` de `config/query.ts`. Nada de `useEffect` + `fetch`, y ninguna respuesta de la API en Context o Zustand. **HTTP**, solo por `api/client.ts`. → §3.3
19. **Estado de UI**: local por defecto; Zustand solo si es global y plano (#105). **Formularios**: react-hook-form + zod, con el formulario puro y el diálogo como dueño de la mutación (#110). **Fechas**: `lib/date.ts` (#111). → §3.3
20. **Textos**: ningún string visible en el JSX. Todo pasa por `t()`, con la clave en `es` **y** en `en` en el mismo commit (#117, #198). → §3.3
21. **Estilos**: tokens del `@theme` y **patrones con nombre** (#273). Ningún valor suelto, y ninguna combinación de utilidades que cumpla un papel de diseño escrita a mano en una pantalla. El catálogo es de la skill `diseno`. → §3.3

## Reglas fijas — modelo de datos (§4)

22. **Ninguna regla de negocio vive en la base** (regla dura 8, #3): ni triggers ni stored procedures. §4.5 dice en qué service vive cada una.
23. **Las invariantes que MySQL no puede expresar las sostiene el service con un lock**: un solo `Primary` vivo por mesa (#73), una postulación activa por par (#28), el cupo (#34), la plataforma nunca sin `Owner`. Antes de escribir una, se mira cómo lo hace la que ya existe.
24. **Una fila `Deleted` es invisible en todos los caminos de lectura** (#25), y **lo derivado no se guarda** (#11, #232).
25. **Todo cambio de schema es una migración Flyway nueva** (regla dura 9), con el procedimiento de §7.3. **Una migración aplicada no se toca ni en sus comentarios**: el checksum cubre el archivo entero. Toda búsqueda y reemplazo masivo sobre `backend/src` **excluye `db/migration/`**.
26. **Dos «owner» que no son lo mismo**: el rol de plataforma `Owner` y `masters.master_type = 'Primary'` (#67, #71, #89). La línea entre `Admin` y `Owner` está en `references/modelo-datos/roles-y-alcance.md` §3.
27. **Campañas y Temporadas están fuera de v1** (regla dura 13, #7); lo que falta resolver está en §4.7.1.

## Reglas fijas — testing y documentación (§5, §6)

28. **Toda regla de negocio llega con su test unitario**, y lo que depende del motor real, con su IT de Testcontainers. Las pantallas llevan Vitest si tienen lógica, y los flujos críticos, Playwright contra el backend real. → §5
29. **Todo lo público lleva su bloque, en inglés**: Javadoc en el backend (getters y cada componente de un `record` incluidos) y JSDoc en todo `export` del frontend. Dice qué hace y por qué, cita el `#n`, y se actualiza en el mismo cambio que la firma. → §6

## El detalle, por sección

Se lee la referencia de la sección que se está tocando; no hace falta leerlas todas.

| § | Qué cubre | Archivo |
|---|---|---|
| 2.1 | El árbol de paquetes, qué va en cada capa y qué en `common/` | [`references/backend/paquetes.md`](references/backend/paquetes.md) |
| 2.2 | Reglas por capa: controller, service, repository, entity, mapper | [`references/backend/capas.md`](references/backend/capas.md) |
| 2.3 | DTOs: tipado explícito, validación, nombres | [`references/backend/dtos.md`](references/backend/dtos.md) |
| 2.4 | Interfaces y clases abstractas | [`references/backend/abstraccion.md`](references/backend/abstraccion.md) |
| 2.5 | Contrato de la API: rutas, status, paginación, `ProblemDetail` | [`references/backend/contrato-api.md`](references/backend/contrato-api.md) |
| 2.6 | Seguridad: sesión, JWT, pertenencia, autorización por método | [`references/backend/seguridad.md`](references/backend/seguridad.md) |
| 3.1 – 3.1.6 | Estructura del frontend, nombres, feature ≠ pantalla, ruteo y guardias | [`references/frontend/estructura.md`](references/frontend/estructura.md) |
| 3.2 | Modelo de tipos | [`references/frontend/tipos.md`](references/frontend/tipos.md) |
| 3.3 | Datos, paginación, query keys, cliente, estado, caché, sesión, i18n, formularios, fechas, estilos | [`references/frontend/reglas.md`](references/frontend/reglas.md) |
| 4.1 | Convenciones del schema | abajo |
| 4.4 y 4.6 | El DDL de `V1__baseline.sql`, literal, la tabla de migraciones posteriores y el seed | [`references/modelo-datos/ddl.md`](references/modelo-datos/ddl.md) |
| 4.5 | Reglas de negocio por subsistema y el service donde vive cada una | [`references/modelo-datos/reglas-negocio.md`](references/modelo-datos/reglas-negocio.md) |
| 4.7 | Fuera de v1; §4.7.1, Campañas y Temporadas | [`references/modelo-datos/fuera-de-v1.md`](references/modelo-datos/fuera-de-v1.md) |
| — | **Roles y alcance**: la matriz `Admin`/`Owner` (§3), las rebanadas de F3 (§4–§7) | [`references/modelo-datos/roles-y-alcance.md`](references/modelo-datos/roles-y-alcance.md) |
| 5 | Testing: backend (5.1), frontend (5.2), e2e (5.3) | [`references/testing.md`](references/testing.md) |
| 6 | Documentación del código: Javadoc (6.1), JSDoc (6.2) | [`references/documentacion.md`](references/documentacion.md) |
| 7 | Procedimientos: nuevo endpoint (7.1), nuevo componente o pantalla (7.2), cambio de `@Entity` (7.3) | [`references/procedimientos.md`](references/procedimientos.md) |

El diagrama entidad-relación está en `docs/modelo-datos.md` §3, y los de cada subsistema en `docs/diagramas/11`–`16`.

## 4.1 Convenciones del schema

| Tema | Convención | Ref. |
|---|---|---|
| Nombre de tabla | `snake_case`, plural, minúsculas | — |
| Nombre de columna | `snake_case` singular. FK = `<entidad_singular>_id` | — |
| Clave primaria | Siempre `id`, `VARCHAR(64)`, UUID v7 generado en la aplicación | #9 |
| Enums | Columna `VARCHAR(32)` + `enum` de Java con `@Enumerated(EnumType.STRING)`. Nunca el tipo `ENUM` de MySQL | #10 |
| Borrado | **Soft delete**: columna `status` con el valor `Deleted` **y** `deleted_at DATETIME NULL` con la fecha. Toda lectura filtra por estado | #25 |
| Cascadas | Se resuelven en el **service layer**, nunca con `ON DELETE CASCADE`. Un borrado arrastra a sus dependientes con la **misma** marca de tiempo, en una transacción | #25 |
| Fechas y horas | **Todo en UTC.** No existe ninguna columna `timezone`: la conversión a hora local la hace el frontend | #22 |
| Timestamps | `created_at DATETIME NOT NULL`, `updated_at DATETIME NULL`, `deleted_at DATETIME NULL` donde aplique | #25 |
| Texto enriquecido | `LONGTEXT` en la tabla que lo necesita. **Nunca** una fila de `files`. Se sanitiza al guardar y al servir | #62 |
| Charset | `utf8mb4` / `utf8mb4_unicode_ci` | — |

**Las referencias polimórficas** (`approval_requests.entity_id`) no tienen FK: su integridad es del service y de la verificación periódica que exige #78.
