# Arquitectura

> Documento de convenciones. Define **dónde va cada archivo, cómo se llama y qué le está permitido hacer**. Si algo acá contradice al código, gana este documento hasta que se decida cambiarlo explícitamente (y se actualice acá en el mismo commit).
>
> El modelo de datos está en `modelo-datos.md`; el razonamiento detrás de las decisiones de stack, en `decisiones.md`.

## 0. Estado del repositorio

```
CentralDungeon/
├── backend/            Java 25 + Spring Boot 4.1      ← por scaffoldear
├── frontend/           React 19 + Vite 8 + TypeScript ← por scaffoldear
├── legacy/
│   ├── backend-node/   Express + TS (2024). Solo lectura, referencia funcional.
│   └── frontend-next/  Next.js 14 + MUI (2024). Solo lectura, referencia funcional.
├── docs/
└── .claude/skills/
```

`legacy/` **no se edita nunca**. Se consulta para recordar cómo se comportaba realmente una pantalla o un endpoint, y se elimina cuando el código nuevo alcance paridad funcional.

## 1. Stack y versiones

Las versiones de abajo son las **fijadas para el proyecto**: son las que se escriben en `pom.xml` y `package.json` al scaffoldear, y las que asume el resto de este documento. Se fijaron en agosto de 2026, al retomar el proyecto. La política para cambiarlas está en §1.3.

### 1.1 Backend

| Área | Elección | Versión fijada |
|---|---|---|
| Lenguaje / runtime | Java (LTS) | **25** — records, sealed, pattern matching, virtual threads, structured concurrency |
| Framework | Spring Boot | **4.1.1** — arrastra Spring Framework 7.0.9, Spring Security 7.1, Spring Data 2025.1, Hibernate 7.4 |
| Build | Maven Wrapper (`./mvnw`) | 3.9.x (Boot 4.1 exige 3.6.3+) |
| Persistencia | Spring Data JPA + Hibernate, MySQL 8 | `mysql-connector-j`, versión del BOM |
| Migraciones | Flyway | versión del BOM. `ddl-auto: validate`, nunca `update` ni `create-drop` |
| Seguridad | Spring Security + OAuth2 Client (Discord) + JWT propio | 7.1, del BOM |
| Serialización JSON | Jackson | **3** (`tools.jackson.*`) |
| Validación | Jakarta Bean Validation | `spring-boot-starter-validation` |
| Null-safety | JSpecify | reemplaza a `org.springframework.lang.@Nullable` |
| Mapeo entity↔DTO | MapStruct | 1.6.x, declarado en `annotationProcessorPaths` del `maven-compiler-plugin` |
| Documentación de API | springdoc-openapi | **3.1.0** (`springdoc-openapi-starter-webmvc-ui`) — la línea 3.x es la de Boot 4; la 2.x es de Boot 3 |
| Caché en proceso | Spring Cache + Caffeine (#128) | `spring-boot-starter-cache` del BOM + `com.github.ben-manes.caffeine:caffeine` |
| Tests | JUnit **6** (Jupiter) + Mockito + AssertJ + Testcontainers | Testcontainers 2.x (MySQL) |

**Por qué Java 25 y no 21**: Boot 4.1 acepta de Java 17 a 26, y 25 es el LTS vigente. Además, los updates de JDK 21 posteriores a septiembre de 2026 dejan de estar bajo licencia permisiva de Oracle.

**Por qué Boot 4.1 y no 3.5**: la línea 3.5 llegó a end-of-life open source el 30 de junio de 2026. Arrancar un proyecto nuevo ahí sería nacer sin soporte upstream. Spring Boot no designa releases LTS: cada minor tiene 12 meses de soporte y sale una cada seis meses.

Consecuencias de Boot 4 que este documento da por sentadas, y que conviene tener presentes al leer cualquier tutorial escrito para Boot 3:

- **Jackson 3**: los imports son `tools.jackson.*`, no `com.fasterxml.jackson.*` (excepción: las anotaciones siguen en `com.fasterxml.jackson.annotation`). `JacksonConfig` configura el `JsonMapper`. Cuidado con el drift silencioso en formatos de fecha, nulos y `BigDecimal` si se comparan JSON carácter por carácter en un test.
- **JSpecify**: los paquetes se marcan `@NullMarked` y lo nullable se anota explícitamente. Las anotaciones de `org.springframework.lang` están deprecadas en Framework 7.
- **JUnit 6**: JUnit 4 y el motor Vintage quedan fuera. Ningún test lleva `@RunWith` ni `SpringRunner`.
- **Testcontainers 2.x**: módulos con prefijo `testcontainers-` y clases reubicadas por módulo (`org.testcontainers.mysql.MySQLContainer`).
- **`RestTemplate` ya no se autoconfigura**. Para llamadas salientes (la API de Discord) se usa `RestClient` o una interfaz `@HttpExchange`.
- **Versionado de API nativo**: Framework 7 trae versionado de API de primera clase (path, header, query, media type). Acá **no se usa por ahora**: la versión va en el path (§2.5) y mientras exista una sola versión viva no se agrega maquinaria. Si algún día hay v2, se usa ese soporte nativo, no controllers duplicados.
- **`spring-boot-starter-classic`** (el shim que restituye starters removidos) **no se usa**: el proyecto es nuevo, no tiene nada que restituir.

### 1.2 Frontend

TypeScript **se introduce** en el frontend: `legacy/frontend-next/` era JavaScript (`jsconfig.json`, archivos `.js`/`.jsx`). Lo que se conservaba era el TypeScript del backend Node. Es lo que hace posible la disciplina de tipos de §3.2 y lo que hace que un cambio de contrato en el backend se detecte compilando y no en producción (`decisiones.md` #20).

| Área | Elección | Versión fijada |
|---|---|---|
| Lenguaje | TypeScript, `strict: true` | 5.9.x |
| Runtime de build | Node (Active LTS) | 24.x — mínimo 22, lo exige React Router 8 |
| Build / dev server | Vite | **8.x** — bundling con Rolldown/Oxc en lugar de esbuild+Rollup |
| UI | React | **19.2.x** |
| Ruteo | React Router (data router, `createBrowserRouter`) | **8.x** — paquete `react-router`, **no** `react-router-dom` (quedó como alias de compatibilidad) |
| Componentes | shadcn/ui sobre Radix + Tailwind CSS | Tailwind **4.3.x** vía `@tailwindcss/vite` |
| Estado de servidor | TanStack Query | 5.10x |
| Estado de UI | Zustand global, Context por subárbol, `useState` local — criterio en §3.3 | 5.x |
| Formularios | react-hook-form + zod + `@hookform/resolvers` | RHF 7.8x (la 8 está en beta), zod 4.x, resolvers 5.x |
| HTTP | `fetch` envuelto en un cliente propio tipado | — |
| Fechas y horas | `Intl.DateTimeFormat` nativo, sin librería (#111) | — |
| Internacionalización | `i18next` + `react-i18next`, JSON por espacio de nombres (#107, #117) | 25.x / 16.x |
| Tests | Vitest + React Testing Library, Playwright para e2e | Vitest **4.x**, RTL 16.x, Playwright 1.6x |

Consecuencias de estas versiones:

- **Tailwind 4 se configura en CSS, no en JS**: no hay `tailwind.config.ts`. El tema vive en `src/styles/globals.css` con `@import "tailwindcss"` y un bloque `@theme`; la detección de contenido es automática. Cualquier receta que hable de `content: [...]` o de `@tailwind base` es de la v3.
- **React Router 8 es ESM-only** y asume React 19 y Node 22+. Los `future.v8_*` flags ya no existen: su comportamiento es el default.
- **Vite 8 usa Rolldown**. Antes de agregar un plugin que dependa de internals de Rollup, verificar que esté portado. **Ya mordió una vez**: Vitest 3 declara peer `vite ^5–^7`, así que npm le instala **su propio Vite** anidado y `defineConfig` de `vitest/config` deja de tipar contra el Vite del proyecto — el error habla de `rolldownVersion` faltante en `PluginContextMeta`. Se resuelve con **Vitest 4**, que sí declara `vite ^8`. Si aparece un `node_modules/vitest/node_modules/vite`, es este problema.
- **`npm create vite` instala TypeScript 6**, y el stack está fijado en **5.9.x**. Al scaffoldear se bajó a `~5.9.3` a propósito: subir una major es una decisión (regla dura 15), no algo que decida una plantilla. No lo "arregles" subiéndolo.
- **El template trae `oxlint`**, no ESLint. No estaba fijado en ningún lado, así que se conserva; si se cambia, es una decisión.

### 1.3 Política de versiones

- La fuente de verdad ejecutable es `pom.xml` / `package.json`; esta sección es el objetivo declarado. Si divergen, se corrige el que esté mal y se deja constancia acá.
- Las versiones de las dependencias del backend **no se escriben a mano**: las gestiona el BOM de Spring Boot. Solo se fija explícitamente lo que el BOM no cubre (springdoc, MapStruct).
- En el frontend, las dependencias del stack (las de la tabla) se instalan con versión exacta, sin `^`. El resto puede usar el default de npm. El lockfile se commitea siempre.
- **Subir una major de cualquier cosa de estas tablas es una decisión**: se registra en `decisiones.md` y se actualiza esta sección en el mismo commit. Subir un minor o un patch no requiere nada.

## 2. Backend — estructura y patrón

### 2.1 Patrón: paquete por feature, capas adentro

El árbol se organiza **por dominio de negocio primero, por capa técnica después**. Un `com.centraldungeon.controller` con 9 controllers de dominios distintos obliga a saltar entre 6 carpetas para tocar una sola funcionalidad; con paquete por feature, agregar o borrar una feature es agregar o borrar una carpeta.

Dentro de cada feature se respeta la arquitectura en capas clásica: `controller → service → repository`.

```
backend/src/main/java/com/centraldungeon/
├── CentralDungeonApplication.java
│
├── common/                              transversal, sin lógica de negocio propia
│   ├── config/                          @ConfigurationProperties (records) + @Configuration
│   │   ├── DiscordProperties.java
│   │   ├── JwtProperties.java
│   │   ├── StorageProperties.java
│   │   ├── CorsConfig.java
│   │   └── JacksonConfig.java
│   ├── security/
│   │   ├── SecurityConfig.java          filter chain, stateless, matchers por rol
│   │   ├── JwtService.java              emisión y validación
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── DiscordOAuth2UserService.java  valida membresía al guild + alta de usuario
│   │   └── CurrentUser.java             @AuthenticationPrincipal tipado
│   ├── exception/
│   │   ├── ApiException.java            base (sealed) — lleva HttpStatus + código de error
│   │   ├── NotFoundException.java
│   │   ├── ConflictException.java
│   │   ├── ForbiddenActionException.java
│   │   └── GlobalExceptionHandler.java  @RestControllerAdvice → RFC 7807 ProblemDetail
│   ├── audit/
│   │   ├── AuditLog.java                @Entity
│   │   ├── AuditLogRepository.java
│   │   └── AuditService.java            lo invocan los services, nunca los controllers
│   │                                    ⚠ NUNCA audita `comments`: guardaría autor + contenido
│   │                                      y rompería el anonimato (decisiones.md #43)
│   ├── storage/
│   │   ├── StorageService.java          interfaz
│   │   └── LocalDiskStorageService.java implementación por defecto
│   └── model/
│       ├── BaseEntity.java              @MappedSuperclass: id String, created_at, updated_at
│       ├── IdGenerator.java             UUID v7 como String
│       └── PageResponse.java            envoltorio de paginación de la API
│
├── users/                               users, roles, users_roles
│   ├── UserController.java
│   ├── UserService.java
│   ├── UserRepository.java
│   ├── RoleRepository.java
│   ├── User.java                        @Entity
│   ├── Role.java
│   ├── UserRole.java
│   ├── UserStatus.java                  enum
│   ├── UserMapper.java                  @Mapper(componentModel = "spring")
│   └── dto/
│       ├── UserResponse.java            record
│       ├── UserDetailResponse.java
│       └── UpdateUserRequest.java
│
├── tables/                              game_tables, masters, table_schedules, table_types
│   ├── GameTableController.java
│   ├── TableTypeController.java
│   ├── GameTableService.java
│   ├── TableScheduleService.java
│   ├── MasterService.java
│   ├── GameTableRepository.java
│   ├── ...Entity/enum/mapper...
│   └── dto/
│
├── registrations/                       table_registrations, registration_rejections
├── catalogs/                            systems, tags, platforms + tablas puente
├── files/                               files, table_files, registration_files
├── comments/                            comments + ajuste de karma
├── requests/                            requests (rol / master)
└── notifications/                       notifications
```

`src/main/resources/`:

```
application.yml              config base
application-dev.yml          perfil local
application-test.yml         perfil de tests
db/migration/
├── V1__baseline.sql         schema completo (ver modelo-datos.md §4)
└── V2__seed.sql             roles + table_types iniciales
```

### 2.2 Reglas por capa

**Controller** (`*Controller.java`)
- Solo HTTP: recibe DTO validado, llama a **un** service, devuelve DTO + status code explícito.
- Nunca inyecta un `Repository`. Nunca contiene `if` de negocio. Nunca devuelve una `@Entity`.
- **Clase concreta, sin interfaz de contrato** (#119). El contrato publicado es el OpenAPI que genera springdoc desde este archivo, más los `record` de §2.3.
- Anotaciones de autorización (`@PreAuthorize`) van acá, **en el método concreto** — nunca en una interfaz, una superclase genérica ni una lista de rutas aparte (§2.6).
- La identidad del actor entra por `@AuthenticationPrincipal`, jamás como `@PathVariable` ni en el cuerpo.
- Un `@RestController` por agregado, no por tabla: `GameTableController` cubre mesa + horario + masters porque son el mismo agregado.

**Service** (`*Service.java`)
- Dueño de la transacción: `@Transactional` en escritura, `@Transactional(readOnly = true)` en lectura.
- Dueño de la lógica de negocio, incluida toda la que antes vivía en triggers de MySQL (`modelo-datos.md` §5).
- Lanza excepciones de `common/exception`, nunca devuelve `null` para señalar "no existe".
- Puede llamar a otros services; **no puede llamar a un controller**.
- Es la única capa que se testea obligatoriamente con unitarios.

**Repository** (`*Repository.java`)
- Interfaz `JpaRepository<Entity, String>` (los IDs son `String`, ver `modelo-datos.md` §1).
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

### 2.3 DTOs: tipado explícito de todo lo que cruza HTTP

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

### 2.4 Interfaces y clases abstractas: qué se comparte y qué no

Hay tres formas de tratar código parecido entre features, y elegir mal cuesta caro en las dos direcciones — duplicar una regla de negocio genera bugs divergentes, y abstraer dos cosas que solo se parecían acopla dominios que después hay que separar a la fuerza.

**1. Interfaz — cuando hay, o va a haber, más de una implementación real.**

Es el caso de `StorageService`: hoy escribe en disco local, mañana puede escribir en S3, y el resto del backend no debería enterarse (`decisiones.md` #15). La interfaz vive en `common/<área>/`, la implementación al lado, y se inyecta siempre la interfaz.

Lo que **no** se hace: `UserServiceImpl implements UserService` con una sola implementación y sin intención de tener otra. No aporta nada, duplica la navegación entre archivos y no hace falta para testear — Mockito mockea clases concretas sin problema.

**2. Clase abstracta genérica — cuando la misma forma se repite idéntica en tres o más features.**

El caso real del proyecto son los catálogos. `systems`, `tags` y `platforms` tienen la misma estructura (id, nombre, descripción, `parent_id`) y el mismo CRUD. Escribir tres veces el mismo service es mantener tres veces el mismo bug.

```java
public abstract class AbstractCatalogService<E extends CatalogEntity, R extends CatalogResponse> {

    protected final JpaRepository<E, String> repository;
    protected final CatalogMapper<E, R> mapper;

    @Transactional(readOnly = true)
    public PageResponse<R> findAll(Pageable pageable) { /* igual para los tres */ }

    @Transactional
    public R create(CatalogRequest request) {
        requireNameIsUnique(request.name());
        return mapper.toResponse(repository.save(newEntity(request)));
    }

    /** Cada catálogo construye su propia entidad. */
    protected abstract E newEntity(CatalogRequest request);

    /** Gancho opcional: por defecto no valida nada extra. */
    protected void validateBeforeDelete(E entity) { }
}
```

Condiciones para que esto sea legítimo:

- **Se extrae después de ver la repetición, no antes.** Primero se escribe `SystemService` completo; cuando `TagService` sale idéntico y `PlatformService` también, ahí se abstrae. Una base genérica escrita antes de la segunda implementación siempre termina teniendo la forma equivocada.
- Lo abstracto es el CRUD mecánico. Lo que varía se expone como método abstracto o gancho `protected`, **nunca** como un `if (this instanceof TagService)`.
- **Un solo nivel de herencia.** Una clase abstracta que extiende otra clase abstracta deja de poder leerse.
- Si solo la usa una feature, vive en esa feature. A `common/` sube únicamente lo que usan features distintas.

**3. Nada — cuando el parecido es superficial.**

`GameTableService` y `RegistrationService` comparten un `findById` que lanza `NotFoundException`, y ahí termina el parecido: sus reglas de negocio no tienen relación. Una base común entre ellos acopla dos dominios que van a divergir en el primer requerimiento nuevo. Repetir tres líneas es más barato que desacoplarlos después.

> Criterio: **se abstrae lo que es igual por definición** (la forma de un catálogo), **no lo que hoy es parecido por casualidad** (dos services que ambos leen por id).

**Controllers**: mismo criterio, con una salvedad. Heredar un controller esconde el mapeo HTTP, así que aunque la lógica venga de una base genérica, **la ruta y las anotaciones de autorización se declaran en la subclase concreta**. Leer `SystemController.java` tiene que seguir diciendo qué expone y quién puede llamarlo.

**Lo que no se abstrae nunca es la seguridad.** Un `@PreAuthorize` heredado de una base genérica hace que el permiso de un endpoint sea invisible en el archivo que lo declara. Cada controller concreto declara su propia autorización, aunque sea repetitivo.

**Interfaces selladas** (`sealed interface`, `sealed class`): se usan para modelar variantes cerradas del dominio — `ApiException` ya lo hace — y para poder usar `switch` con pattern matching exhaustivo. No son un mecanismo para compartir implementación.

### 2.5 Contrato de la API

- Base: `/api/v1`. Recursos en plural y kebab-case: `/api/v1/game-tables/{id}/registrations`.
- Status codes: `200` lectura, `201` + header `Location` en creación, `204` en borrado/actualización sin cuerpo, `400` validación, `401` sin token, `403` sin permiso, `404` inexistente, `409` conflicto de estado.
- **Éxito: el DTO desnudo, sin envoltura** (#120). Nada de un `ResponseData<T>` con `message`/`status` adentro del cuerpo: el status vive en HTTP y en un solo lugar. El intento en Java tenía esa envoltura y ya se contradecía sola — devolvía `206 Partial Content` en la respuesta HTTP con un `204` escrito en el cuerpo.
- Errores: siempre `ProblemDetail` (RFC 9457, que obsoleta al 7807) producido por `GlobalExceptionHandler`. Nunca un string suelto, nunca un `418` genérico (el backend Node lo usaba como error comodín).
- Toda colección va paginada (`?page=&size=&sort=`) y devuelve `PageResponse`. El backend viejo no tenía paginación en ningún endpoint y era un TODO explícito suyo.
- Fechas en ISO-8601 UTC. La conversión a la zona del usuario es responsabilidad del frontend.

### 2.6 Seguridad

Flujo: el frontend inicia el login OAuth2 de Discord → Spring Security completa el intercambio → `DiscordOAuth2UserService` verifica membresía al guild, crea el usuario si no existe (con rol `Player`), rechaza si `status = 'Blocked'` → el backend emite un JWT propio → el frontend lo usa como `Authorization: Bearer` en todas las llamadas.

**No hay registro propio: la membresía al servidor de Discord es la puerta de entrada** (`decisiones.md` #38). Si el usuario autentica pero no es miembro del guild, no se responde con un error seco: se le ofrece la invitación para unirse y solo se corta el login si la declina. El id del guild es configuración (`DiscordProperties`), nunca una constante en el código.

- Filter chain **stateless**, sin sesión de servidor.
- Dos capas de autorización, igual que el modelo de datos: rol global (`users_roles`: **Player / Master / Admin / Owner**) vía `@PreAuthorize("hasRole(...)")`, y rol por mesa (`masters.master_type`: Owner/Master) verificado en el service, porque depende del recurso concreto.
- ⚠️ **Los dos `Owner` son cosas distintas** (`decisiones.md` #67): el rol global `Owner` es el dueño de la plataforma y puede todo; `masters.master_type = 'Owner'` es el dueño de **una mesa**. En el código no pueden llamarse igual — `PlatformRole.OWNER` y `MasterType.OWNER`, o se renombra el segundo.
- **Los roles globales son funciones acumulables, no niveles** (`decisiones.md` #37, #67). Un usuario puede ser `Master` y `Player` a la vez, o `Master` sin ser `Player`. **No hay jerarquía ni herencia**: `Admin` no hereda lo de `Master`, ni `Master` lo de `Player`, y `Owner` puede todo por definición, no por heredar de `Admin`. En consecuencia **no se registra un `RoleHierarchy`** y cada endpoint enumera explícitamente los roles que lo alcanzan — `@PreAuthorize("hasAnyRole('MASTER','ADMIN')")`, nunca el "mínimo" de una escala que no existe.
- CORS restringido al origen del frontend por perfil, nunca `*`.
- Ningún endpoint acepta el `user_id` del usuario autenticado como parámetro de ruta — sale del token. Este era el agujero central del backend Node.

**Los dos escalados, que son problemas distintos y se arreglan en lugares distintos:**

**Horizontal — el rol no es la pertenencia** (#121). `hasRole('MASTER')` afirma "es master de algo", no "es master de *esta* mesa". Con el actor sacado del token, el id que el atacante todavía controla es el **del recurso**. Por eso:

> **Toda lectura o mutación de un recurso concreto filtra por el actor.** O el actor entra en el `WHERE` (`findByIdAndOwnerId`), o el service comprueba la pertenencia **antes** de tocar nada y lanza si no corresponde. Nunca un `findById(id)` seguido de `save()` sin verificar.

Es el agujero que tenía el Node: `UPDATE Tables SET … WHERE id = ?`, sin un solo predicado de pertenencia. Quien conociera el id de una mesa podía editarla o borrarla. Y ojo: que los ids sean impredecibles (`modelo-datos.md`, UUID v7) es **defensa en profundidad, nunca autorización** — un id filtrado en un link no puede ser lo único que separa a alguien de un recurso ajeno.

**Temporal — el token es una foto vieja** (#122). Si los roles viajan como claims, el JWT afirma lo que era cierto cuando se emitió: un admin degradado sigue siendo admin, y alguien marcado `Blocked` (#84, #86) sigue entrando, hasta que el token expire. Por eso:

> **El JWT afirma identidad, no autorización.** Lleva el `sub` y poco más. Los roles y el `status` se leen de la base en cada request. Un `JwtAuthenticationFilter` resuelve el id, carga el usuario y arma el `Authentication` con las autoridades reales de ese momento.

Para que eso no sea una consulta por petición, la carga va cacheada con **Caffeine** (#128): `expireAfterWrite = 60 s`, `maximumSize = 10 000`, más `@CacheEvict` explícito al bloquear a alguien o cambiarle los roles. **El TTL es la ventana de revocación**, no un número de rendimiento: la evicción explícita hace el efecto inmediato y los 60 s son la red de seguridad para cualquier camino que se olvide de evictar. La caché es **por JVM** — con más de una instancia dos cachés pueden discrepar y el `@CacheEvict` solo limpia la local, la misma limitación que el broker STOMP en memoria (#101).

Es el patrón que sí vale la pena rescatar del intento en Java, que ya lo hacía bien: el token llevaba solo `id` y el filtro hacía `loadUserById` en cada petición.

**La autorización se declara en el controller, nunca en una lista de rutas aparte** (#123). El intento previo la tenía en un `Routes.java` con listas de endpoints por rol, y falló de la peor manera: cinco de las seis rutas estaban escritas **sin barra inicial**, así que no matcheaban, caían en `anyRequest().authenticated()` y **cualquier usuario autenticado alcanzaba los endpoints de master y de admin**. La causa de fondo no es el typo: es que la regla vivía lejos del endpoint que protege y nada obligaba a que coincidieran. En el `SecurityConfig` solo queda lo transversal —stateless, CORS, qué es público—; el permiso concreto vive pegado al método.

⚠️ Y por lo mismo, **`@PreAuthorize` va siempre en el método concreto**: anotarlo en una interfaz o en una superclase genérica es un riesgo documentado de bypass (CVE-2025-41248, septiembre de 2025).

**Tokens: tres, y no se confunden** (#125).

| Token | Emisor | Para qué | Vida |
|---|---|---|---|
| Access de Discord | Discord | Solo durante el login: `identify` y verificar membresía al guild (#38) | Se **descarta** al terminar el callback |
| Access propio | Spring | Autenticar cada llamada a la API | Corto (~15 min) |
| Refresh propio | Spring | Renovar el access | Largo, rotativo, en cookie `httpOnly` + `SameSite=Strict` |

El de Discord no se guarda: después del login no se usa para nada, y conservarlo obligaría a mantener un segundo ciclo de refresh con su propia caducidad para una capacidad que v1 no tiene. Lo que sí requiere la integración futura (#88) es un **bot token**, que es de la aplicación y no del usuario.

**El refresh es el punto de re-afirmación**: al renovar se releen `status` y roles desde la base. Es el momento en que el sistema vuelve a preguntar "¿esta persona sigue estando bien?" — lo que el legacy nunca hacía.

**CSRF: activo solo en `/auth/refresh`** (#127). El resto de la API va con `csrf.disable()`, y eso es correcto, no un descuido: se autentica con `Authorization: Bearer`, un header que el navegador nunca adjunta por su cuenta, así que no existe la credencial automática de la que vive el ataque. El refresh es el único endpoint que se autentica con **cookie**, y las cookies sí viajan solas hacia su destino sin importar quién originó la petición.

Qué se protege exactamente: un sitio ajeno no podría leer la respuesta —lo impide CORS—, así que no hay robo de sesión; lo que sí podría es **forzar la rotación** del refresh y dejar al cliente legítimo con un token viejo, cerrándole la sesión al usuario y disparando falsas alarmas de reuso. El `SameSite=Strict` de #125 ya bloquea eso, pero lo aplica el **navegador**: si el agente lo ignora, del lado del servidor no se entera nadie. El token CSRF es la comprobación que sí ocurre acá.
- **Filtro de visibilidad por veto**: toda lectura de mesas excluye aquellas donde el usuario tenga una solicitud `Blocked`, y el detalle por id responde `404`, no `403` (`decisiones.md` → *Ciclo de vida de la mesa*).

### 2.7 Testing backend

| Tipo | Herramienta | Alcance |
|---|---|---|
| Unitario | JUnit 6 + Mockito + AssertJ | Cada regla de negocio de un service, con los repositories mockeados. Es el grueso de la cobertura. **Obligatorio** antes de dar una regla por terminada. |
| Integración | Testcontainers 2.x (MySQL real) + `@SpringBootTest` | Queries JPA no triviales, constraints, transacciones, y toda la lógica migrada desde triggers. Flyway aplica las migraciones reales sobre el contenedor. |
| Contrato HTTP | `@WebMvcTest` + MockMvc | Solo donde el contrato importe por sí mismo (status codes, forma del `ProblemDetail`). No se duplica lo que ya cubre Playwright. |

Convención: `<ClaseATestear>Test` para unitarios, `<ClaseATestear>IT` para integración. Cada test nombra el caso, no el método: `rechazaPostulacionDeUsuarioBloqueado()`, no `testRegister2()`.

## 3. Frontend — estructura y patrón

### 3.1 Patrón: features de dominio, con capas transversales en la raíz

El dominio manda, igual que en el backend. Una feature es autocontenida —sus llamadas a la API, sus hooks, sus componentes, sus tipos— y **nunca importa de otra feature**: solo depende de las capas transversales de la raíz de `src/`.

Las **pantallas viven fuera de las features**, en `routes/`. Es lo que hace que esa regla no tenga excepciones (§3.1.5).

```
frontend/
├── index.html
├── vite.config.ts                   incluye el plugin @tailwindcss/vite
├── components.json                  config de shadcn/ui (aliases por defecto)
├── tsconfig.json                    strict + alias "@/*" → "src/*"
├── e2e/                             specs de Playwright
└── src/
    ├── main.tsx                     bootstrap: providers + RouterProvider
    ├── styles/
    │   └── globals.css              @import "tailwindcss" + bloque @theme (Tailwind 4: la config vive acá, no en un .ts)
    ├── assets/                      imágenes, íconos propios, fuentes
    │
    ├── config/                      configuración de la app, sin lógica
    │   ├── paths.ts                 constantes de rutas ('/tables/:id'), únicas en todo el proyecto
    │   ├── env.ts                   variables de entorno, leídas y validadas en un solo lugar
    │   └── query.ts                 defaults del QueryClient y política de staleTime (§3.3)
    │
    ├── routes/                      TODAS las pantallas + el árbol de rutas (§3.1.6)
    │   ├── router.tsx               createBrowserRouter: URLs, layouts anidados y lazy
    │   ├── TableListPage.tsx        /
    │   ├── TableDetailPage.tsx      /tables/:id
    │   ├── my/                      /my/*
    │   ├── master/                  /master/*
    │   ├── admin/                   /admin/*
    │   └── owner/                   /owner/*
    │
    ├── layouts/                     cáscaras de página, montadas por el router
    │   ├── RootLayout.tsx           sesión y guard de autenticación
    │   ├── PublicLayout.tsx
    │   ├── PlayerLayout.tsx  MasterLayout.tsx  AdminLayout.tsx  OwnerLayout.tsx
    │   └── components/              piezas del shell: AppHeader, AppSidebar, ContextSwitcher, UserMenu
    │
    ├── providers/                   QueryProvider, ThemeProvider, AuthProvider, StompProvider, I18nProvider
    ├── stores/                      Zustand global: contexto de rol activo, preferencias
    │
    ├── features/                    los dominios. Sin páginas adentro
    │   ├── auth/
    │   ├── tables/
    │   │   ├── api/
    │   │   │   ├── gameTablesApi.ts       las llamadas, sobre api/client
    │   │   │   ├── useGameTables.ts       queries
    │   │   │   └── useCreateGameTable.ts  mutations (una por archivo)
    │   │   ├── components/          plano, con sufijo (§3.1.3):
    │   │   │                        GameTableCard.tsx, GameTableForm.tsx,
    │   │   │                        CreateGameTableDialog.tsx, EditGameTableDialog.tsx,
    │   │   │                        TableSchedulesSection.tsx, TableStatusBadge.tsx
    │   │   ├── hooks/               hooks de UI propios de la feature (opcional)
    │   │   ├── schemas.ts           esquemas zod de los formularios
    │   │   ├── constants.ts         labels y opciones del dominio (opcional)
    │   │   ├── types.ts             tipo base del dominio + derivados (§3.2)
    │   │   └── index.ts             la superficie pública de la feature (§3.1.3)
    │   ├── registrations/  files/  catalogs/  comments/  notifications/  users/
    │
    ├── components/                  sin dominio, para toda la app
    │   ├── ui/                      primitivas shadcn/ui generadas (button.tsx, dialog.tsx…)
    │   └── …                        compuestos propios: FormDialog, DataTable, EmptyState, ErrorState…
    ├── hooks/                       useDisclosure, useConfirm, useDebounce, useTableSelection
    ├── lib/
    │   ├── utils.ts                 cn() — la ruta que shadcn/ui espera por defecto
    │   └── date.ts                  formateo de fechas y horas con Intl (§3.3)
    ├── api/
    │   ├── client.ts                fetch tipado: base URL, JWT, parseo de ProblemDetail
    │   └── queryKeys.ts             fábrica central de query keys
    ├── types/
    │   ├── api.ts                   PageResponse<T>, ProblemDetail, ApiError
    │   └── utils.ts                 helpers de tipos propios: StrictOmit, Expect, Equals (§3.2)
    └── locales/
        └── es/                      un JSON por espacio de nombres (§3.3)
```

Dos notas sobre `components/`: las primitivas de shadcn/ui caen en `components/ui/` porque es el alias que su CLI usa por defecto, y `lib/utils.ts` es donde espera encontrar `cn()` — respetar ambos evita reconfigurar el generador en cada componente nuevo.

#### 3.1.1 Dónde va cada archivo

Cuatro preguntas, en orden. La primera que aplique decide:

1. **¿Es una pantalla?** → `routes/`, nunca dentro de una feature.
2. **¿Es una llamada a la API o un hook de TanStack Query?** → `features/<dominio>/api/`.
3. **¿Menciona un concepto del dominio?** Si el nombre del archivo o sus props nombran una mesa, una postulación, un comentario o un archivo → **vive en su feature**, aunque parezca reutilizable. `TableStatusBadge` es de `tables`.
4. **Si no menciona ningún dominio** (un diálogo genérico, una tabla paginada, `cn()`) → la capa transversal que corresponda: `components/`, `hooks/`, `lib/`, `types/`.

Por defecto **todo nace dentro de una feature**. Las capas transversales no se llenan por anticipación.

#### 3.1.2 Cuándo algo sube a la raíz

Una feature **nunca importa de otra feature**. Esa es la regla que fuerza el criterio: si dos features necesitan la misma pieza, la única salida legítima es subirla.

- **Sube cuando una segunda feature ya la necesita** — dos usos reales, no dos usos previstos. Es un umbral más bajo que el del backend (§2.4, tres repeticiones) justamente porque acá la alternativa a subir no es un poco de duplicación: es un import prohibido.
- **Al subir se le quita el dominio.** Si `EditGameTableDialog` y `EditCommentDialog` comparten forma, lo que sube a `components/` es `FormDialog`, sin saber de mesas ni de comentarios. Un componente de `components/` que reciba un `GameTable` está mal ubicado.
- **No sube lo que solo se parece.** Dos formularios no comparten componente por ser dos formularios; comparten `FormDialog`, que es el envoltorio.

#### 3.1.3 Nombres, sufijos y la superficie pública

`features/<dominio>/components/` es **plano**. El sufijo dice qué es cada archivo, y el inventario completo de la feature se lee de un vistazo:

| Sufijo | Qué es |
|---|---|
| `Page` | Pantalla montada por el router. Solo en `routes/` |
| `Form` | Formulario puro: recibe valores iniciales y `onSubmit` (§3.3) |
| `Dialog` | Envoltorio que aloja un formulario o una acción en un modal |
| `Section` | Bloque de una pantalla compuesta: trae sus propios datos a partir de un id (§3.1.5) |
| `Card` | Ficha de una entidad en un listado |
| `Badge` | Etiqueta de estado |
| `List` / `Table` | Listado de entidades |
| `Editor` | Control compuesto de edición (`ScheduleEditor`, `RichTextEditor`) |
| `Provider` | Componente que monta un Context |
| `use…` | Hook |

Se admite **una** subcarpeta dentro de `components/` solo cuando la feature pasa de una docena de archivos y hay un subconjunto que se lee como un bloque. Nunca una carpeta con uno o dos archivos: eso era lo que hacía `components/tables/status/` en el legacy con un único componente adentro.

**Cada feature declara su superficie pública en `index.ts`** (#114). Desde afuera se importa `@/features/tables`, nunca una ruta interna; lo que no está exportado ahí es privado de la feature:

```ts
// features/tables/index.ts
export { GameTableCard } from './components/GameTableCard';
export { TableSchedulesSection } from './components/TableSchedulesSection';
export type { GameTable, GameTableSummary } from './types';
```

Es el **único** barrel del proyecto: dentro de una feature, y en las capas transversales, se importa la ruta completa. Un `index.ts` por carpeta reintroduce ciclos de importación y no aporta ninguna frontera.

Archivos: componentes y páginas en `PascalCase.tsx`, hooks en `useCamelCase.ts`, el resto en `camelCase.ts`.

#### 3.1.4 Carpetas que no existen

El frontend viejo agrupaba **por tipo de archivo** en la raíz de `src/`, mezclando dominios dentro de cada una. La raíz de ahora también tiene carpetas por tipo, pero solo para lo que **no tiene dominio**; todo lo que menciona una mesa o una postulación vive en su feature. Cada carpeta del legacy tiene un destino fijo:

| Legacy | Dónde va ahora | Por qué |
|---|---|---|
| `src/forms/` | `features/<dominio>/components/` | Mezclaba cinco dominios en una sola carpeta (#106) |
| `src/contexts/` | `providers/` si es global; junto al componente que envuelve si es de subárbol | Un Context de una tabla no es global (#105) |
| `src/api/` con un archivo por verbo | `api/client.ts` + `features/<dominio>/api/` | Eran siete wrappers sin tipos (#104) |
| `src/constants/` | `config/paths.ts` las rutas; `features/<dominio>/constants.ts` los labels del dominio | Un archivo global de constantes termina siendo el cajón de todo |
| `src/normalize/` | No existe (#108) | El tipo base es el contrato (§3.2) |
| `src/styles/*.js` | Clases de Tailwind en el JSX (#109) | — |
| `src/helper/` | `lib/` si es puro y sin dominio; si no, su feature | "helper" no dice nada sobre qué hay adentro |
| `src/language/*.xml` | `locales/es/*.json` con i18next (#107, #117) | — |
| `pages/` de Next.js | `routes/` (§3.1.6) | La carpeta ya no define la URL; el router la declara |

Los tests van **junto al archivo que prueban** (`GameTableForm.test.tsx`), no en un `__tests__/` aparte. Los de Playwright son la excepción: viven en `e2e/`.

#### 3.1.5 Una feature no es una pantalla

Una feature es un **dominio**, y la relación con las pantallas no es uno a uno en ninguna dirección: las mesas ocupan dos pantallas (listado y detalle), y la pantalla de detalle necesita seis dominios —mesa, horarios, archivos, catálogos, masters y postulaciones—. En el legacy eso era `PreparationStatus`, un componente de ~300 líneas que importaba de todos lados y manejaba seis modales a la vez.

Por eso las páginas viven en `routes/` y no dentro de una feature: **componer dominios es trabajo de la pantalla**, y sacarlas afuera es lo que permite que "una feature nunca importa de otra" no tenga asteriscos.

**Lo que la página le pasa a cada bloque es un identificador, no una entidad.** Cada bloque es un componente `…Section` que vive en **su** feature, lanza **su** propia query y monta **sus** propios diálogos:

```tsx
// routes/TableDetailPage.tsx — compone; su única query es la mesa
<GameTableHeader table={table} />
<TableSchedulesSection tableId={id} />       {/* features/tables        */}
<TableFilesSection tableId={id} />           {/* features/files         */}
<TableCatalogsSection tableId={id} />        {/* features/catalogs      */}
<TableRegistrationsSection tableId={id} />   {/* features/registrations */}
```

`TableFilesSection` recibe un `tableId` y nada más: no conoce el tipo `GameTable`, así que `features/files` no depende de `features/tables`. Toda la dependencia entre dos features cabe en una prop. Si un `…Section` necesitara la entidad entera, es señal de que el bloque está en la feature equivocada.

La página tampoco concentra el estado de los diálogos: cada `…Section` tiene el suyo. Los seis `useModal` en paralelo del legacy eran el síntoma de que un solo componente estaba haciendo el trabajo de seis.

#### 3.1.6 Ruteo

React Router **no tiene convención de archivos**: la URL no sale de dónde está el archivo, sale de un árbol de objetos declarado a mano. Es la diferencia de fondo con Next.js, donde `pages/tables/[id].js` *era* `/tables/:id`. Por eso el router es una lista central y no algo distribuido por feature: una ruta dentro de `features/` no se registraría sola.

```tsx
// routes/router.tsx
export const router = createBrowserRouter([
  {
    Component: RootLayout,                    // sesión y guard; ErrorBoundary global
    ErrorBoundary: RootErrorBoundary,
    children: [
      {
        Component: PublicLayout,              // sin path: solo agrupa
        children: [
          { path: paths.login,        lazy: () => import('./LoginPage') },
          { path: paths.authCallback, lazy: () => import('./OAuthCallbackPage') },
        ],
      },
      {
        Component: PlayerLayout,
        children: [
          { index: true,        lazy: () => import('./TableListPage') },
          { path: 'tables/:id', lazy: () => import('./TableDetailPage') },
          // …el resto del contexto Jugador
        ],
      },
      { path: 'master', Component: MasterLayout, children: [ /* … */ ] },
      { path: 'admin',  Component: AdminLayout,  children: [ /* … */ ] },
      { path: 'owner',  Component: OwnerLayout,  children: [ /* … */ ] },
      { path: '*', Component: NotFoundPage },
    ],
  },
]);
```

Cinco reglas:

1. **El árbol espeja el sitemap** de `frontend-diseno.md` §2. Si dejan de parecerse, uno de los dos está desactualizado.
2. **Los paths salen de `config/paths.ts`**, nunca strings sueltos repartidos entre el router y los `<Link>`.
3. **Anidar es cómo se comparte un layout.** Una ruta sin `path` agrupa; una con `path` además prefija a sus hijas. Las hijas se pintan en el `<Outlet />` del layout.
4. **Cada página se carga con `lazy`**, y para eso su módulo exporta `Component`:

```tsx
export function TableDetailPage() { /* … */ }
export { TableDetailPage as Component };   // lo que consume router.tsx
```

5. **Las pestañas de una pantalla son rutas hijas, no `useState`.** Las siete de `/master/tables/:id` (candidatos, jugadores, agenda, sesiones, peticiones, archivos, estado) se declaran como `children`, así cada una tiene URL propia, se comparte por link y el botón de atrás funciona.

**Guards: en el layout, no por ruta.** `RootLayout` redirige a `/login` si no hay sesión, y con eso cubre todo lo que cuelga de él. ⚠️ Los layouts de contexto **no verifican el rol**: el contexto es organización de UI, no seguridad (#103). Si alguien fuerza `/admin/queue` sin ser admin, el backend responde `403` y la pantalla pinta `ForbiddenState`.

**No se usan los `loader` de React Router** (#115). La recomendación general del ecosistema es combinarlos con TanStack Query para adelantar el fetch, y es buena con SSR; acá no hay SSR, la app está detrás de login y cada pantalla ya define su skeleton (`frontend-diseno.md` §5). Adoptarlos obligaría a inyectar el `queryClient` en el router y a declarar cada query en dos lugares. Si algún día se mide un waterfall real, se agrega el loader en esa pantalla concreta sin tocar el resto.

### 3.2 Modelo de tipos: un tipo base por entidad, el resto derivado

Esta sección es la contraparte frontend de §2.3 y la razón principal por la que se conserva TypeScript al migrar de Next.js a React puro.

La regla es: **por cada entidad del dominio se escribe a mano un único tipo base, y todas las variantes se derivan de él con utility types.** El problema que resuelve es concreto: si `GameTable`, `CreateGameTableInput`, `UpdateGameTableInput` y `GameTableCardProps` se declaran cada una por separado, son cuatro copias del mismo modelo que se desincronizan en silencio — el día que el backend renombra un campo, tres de las cuatro siguen compilando y el bug aparece en runtime.

**El tipo base** vive en `features/<dominio>/types.ts` y es el espejo exacto del `...Response` del backend:

```ts
// features/tables/types.ts

/** Espejo de GameTableResponse. Único tipo de esta feature escrito a mano. */
export interface GameTable {
  id: string;
  name: string;
  description: string;
  tableTypeId: string;
  status: GameTableStatus;
  ownerId: string;
  createdAt: string;   // ISO-8601 UTC — la conversión a zona local es del frontend (§2.5)
  updatedAt: string;
}
```

**Todo lo demás se deriva**, en el mismo archivo, debajo del base:

```ts
// Payload de creación: sin lo que genera el servidor.
export type CreateGameTableInput = StrictOmit<GameTable, 'id' | 'ownerId' | 'status' | 'createdAt' | 'updatedAt'>;

// PATCH: los mismos campos, todos opcionales.
export type UpdateGameTableInput = Partial<CreateGameTableInput>;

// Lo que necesita una card de listado, y nada más.
export type GameTableSummary = Pick<GameTable, 'id' | 'name' | 'status' | 'tableTypeId'>;

// El detalle: la base más las relaciones que solo trae ese endpoint.
export type GameTableDetail = GameTable & {
  schedules: TableSchedule[];
  masters: MasterSummary[];
  tags: Tag[];
};

// Formulario a medio llenar: todo opcional menos la identidad.
export type GameTableDraft = Partial<GameTable> & Pick<GameTable, 'id'>;
```

**Qué utility type usar para qué** ([referencia completa](https://www.typescriptlang.org/docs/handbook/utility-types.html)):

| Utility | Cuándo |
|---|---|
| `Pick<T, K>` | vistas reducidas: la card de un listado, las opciones de un selector |
| `Omit<T, K>` | payloads de escritura: sacar lo que genera el servidor (`id`, `createdAt`, `updatedAt`) |
| `Partial<T>` | payloads de PATCH y estado de formularios a medio llenar |
| `Required<T>` | pasar de un draft a un valor ya validado |
| `Readonly<T>` | datos de servidor que un componente recibe y no debe mutar |
| `Record<K, V>` | diccionarios por clave cerrada: `Record<GameTableStatus, string>` para labels — obliga a cubrir todos los estados |
| `Exclude<T, U>` / `Extract<T, U>` | acotar uniones de estado: `Exclude<GameTableStatus, 'Archived'>` |
| `NonNullable<T>` | estrechar un campo opcional después de comprobarlo |
| `ReturnType<T>` / `Awaited<T>` | derivar el tipo de una respuesta desde la función del cliente HTTP en vez de re-declararlo |
| `Parameters<T>` | reusar la firma de una función en un wrapper |
| `Capitalize` / `Uppercase` y familia | claves derivadas en tipos de plantilla, casos puntuales |

**Reglas**

1. **Un tipo base por entidad**, en `features/<dominio>/types.ts`. Si el backend agrega un campo, se agrega ahí y todos los derivados se actualizan solos.
2. **Prohibido re-declarar a mano un tipo que sea subconjunto o variante de otro.** Si es "lo mismo pero sin X", es `Omit`. Si es "lo mismo pero opcional", es `Partial`. Si es "solo estos tres campos", es `Pick`.
3. **Los derivados se declaran junto al base**, no dispersos por los componentes. Un componente importa el tipo que necesita; no lo inventa en sus props.
4. **`interface` para el base** (extensible, mejores mensajes de error), **`type` para los derivados** (los utility types devuelven types).
5. **Nunca `any`.** Para lo genuinamente desconocido, `unknown`, y se estrecha antes de usarlo.
6. **`Omit` no valida sus claves**: `Omit<GameTable, 'createdAtt'>` compila y no quita nada. Por eso los payloads de escritura usan el helper propio, que sí las verifica:

```ts
// types/utils.ts
export type StrictOmit<T, K extends keyof T> = Omit<T, K>;

// Aserciones de tipos en tiempo de compilación.
export type Expect<T extends true> = T;
export type Equals<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false;
```

7. **El esquema zod y el payload del dominio no pueden divergir.** El tipo del formulario sale del esquema con `z.infer`, y una aserción de tipo lo ata al derivado del dominio: si dejan de coincidir, falla la compilación, no el submit.

```ts
// features/tables/schemas.ts
export const createGameTableSchema = z.object({ /* ... */ });
export type CreateGameTableForm = z.infer<typeof createGameTableSchema>;

type _CheckCreatePayload = Expect<Equals<CreateGameTableForm, CreateGameTableInput>>;
```

8. **Los tipos transversales de la API viven en `types/api.ts`** y son genéricos: se instancian (`PageResponse<GameTableSummary>`), no se re-declaran por feature.
9. **Los enums son uniones de literales, no `enum` de TypeScript.** El backend los serializa como string (§2.3):

```ts
export type GameTableStatus = 'Open' | 'InProgress' | 'Closed' | 'Finished';
```

Un `enum` de TS genera código en runtime, no es tree-shakeable y no coincide estructuralmente con el string que llega por la red. Con la unión, además, `Record<GameTableStatus, string>` obliga a cubrir todos los casos al mapear a labels o a variantes de badge.

10. **`tsconfig.json`**: `strict: true` más `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes` y `verbatimModuleSyntax`. Sin `strict` toda esta sección es decorativa.

> El tipo base se escribe a mano y se mantiene sincronizado con el `...Response` de Java por disciplina. Generarlo desde el OpenAPI que ya publica springdoc (§1.1) eliminaría ese trabajo manual; está anotado como candidato en `decisiones.md`, pero **hoy no está adoptado** y las reglas de arriba asumen tipos escritos a mano.

### 3.3 Reglas

**Datos de servidor**: exclusivamente TanStack Query. Prohibido `useEffect` + `fetch` para cargar datos, y prohibido guardar respuestas de la API en Context o Zustand — es lo que hacía el frontend viejo y por eso no tenía caché ni invalidación.

**Query keys**: centralizadas en `api/queryKeys.ts` como fábrica (`queryKeys.tables.detail(id)`), nunca strings sueltos en los componentes. Sin esto la invalidación se vuelve adivinanza.

**Cliente HTTP**: un único `client.ts` **genérico y tipado**, con las operaciones como métodos (#104). Inyecta el JWT, traduce el `ProblemDetail` a un `ApiError` tipado y centraliza el `401`. Reemplaza los siete wrappers sueltos del proyecto viejo.

```ts
// api/client.ts
export const api = {
  get:     <TRes>(path: string, params?: QueryParams) => request<TRes>('GET', path, { params }),
  getPage: <TItem>(path: string, params?: QueryParams) => request<PageResponse<TItem>>('GET', path, { params }),
  post:    <TRes, TBody>(path: string, body: TBody) => request<TRes>('POST', path, { body }),
  put:     <TRes, TBody>(path: string, body: TBody) => request<TRes>('PUT', path, { body }),
  patch:   <TRes, TBody>(path: string, body: TBody) => request<TRes>('PATCH', path, { body }),
  delete:  <TRes = void>(path: string) => request<TRes>('DELETE', path),
  upload:  <TRes>(path: string, files: File[], body?: unknown) => request<TRes>('POST', path, { files, body }),
};
```

Cada feature arma su módulo encima, y ahí es donde los tipos derivados de §3.2 llegan hasta la llamada:

```ts
// features/tables/api/gameTablesApi.ts
export const gameTablesApi = {
  list:   (params: GameTableFilters) => api.getPage<GameTableSummary>('/game-tables', params),
  byId:   (id: string)               => api.get<GameTableDetail>(`/game-tables/${id}`),
  create: (input: CreateGameTableInput) =>
             api.post<GameTableResponse, CreateGameTableInput>('/game-tables', input),
};
```

Si el backend cambia el contrato, esto **no compila** — que es el objetivo. Por eso **no hay capa de normalización** que traduzca nombres de campos (#108): esconder la ruptura la convierte en un bug de runtime.

**Estado de UI** — tres mecanismos con criterio explícito (#105):

| Mecanismo | Cuándo | Ejemplos |
|---|---|---|
| **Librería** | Ya existe resuelto | Toasts con `sonner`, tema con `next-themes` |
| **Context** | El estado pertenece a un subárbol, o hay que montar UI | `ConfirmDialogProvider` + `useConfirm()`; selección múltiple con Shift, montada **alrededor de la tabla que la usa**, no global |
| **Zustand** | Global y plano, sin necesidad del árbol | Contexto de rol activo, preferencias |

Todo lo demás —el estado de un modal, los filtros de una pantalla— es estado local del componente que lo posee. Ninguno de los tres guarda **datos de servidor**: eso es siempre TanStack Query.

**Contexto activo**: la navegación se organiza por contexto de rol (`frontend-diseno.md` §2). ⚠️ **El contexto es organización de UI, no autorización.** Estar "en contexto Admin" no habilita nada: el backend autoriza endpoint por endpoint (§2.6) y una ruta forzada sin el rol devuelve `403`. Ningún componente decide qué puede hacer el usuario mirando el contexto activo; lo decide el rol que trae el token.

**Tiempo real**: un único `StompProvider` mantiene la conexión WebSocket. Los mensajes que llegan son **señales de invalidación**, no datos: el handler hace `queryClient.invalidateQueries(...)` y TanStack Query refetchea. Nunca se escribe una respuesta de servidor en la caché a partir de un mensaje — eso reintroduciría por la ventana el estado de servidor fuera de TanStack Query que la regla de arriba prohíbe.

Tres reglas para que eso no se convierta en una tormenta de peticiones (#116):

1. **El mensaje dice qué invalidar, no "algo cambió".** Trae un tipo y un id (`{ type: 'GameTablePublished', tableId }`), y el handler invalida exactamente esa rama de `queryKeys`. Invalidar de más multiplica las peticiones por la cantidad de clientes conectados.
2. **`invalidateQueries` solo refetchea las queries activas** — las montadas en ese momento. Las demás quedan marcadas como stale y piden datos recién cuando alguien vuelve a esa pantalla. Es la defensa que viene de fábrica y por eso la invalidación puede ser generosa dentro de su rama.
3. **Al reconectar se invalida todo lo activo.** Mientras el socket estuvo caído se perdieron mensajes, así que el cliente no puede confiar en su caché. Sin esto, una caída de red de treinta segundos deja la pantalla mintiendo hasta el próximo montaje.

Tres destinos (#101 y #116): `/user/queue/notifications` para lo personal, `/topic/admin-queue` para la bandeja compartida, y `/topic/tables` para los cambios del catálogo público — que es lo que hace que a alguien navegando el explorador le aparezca una mesa recién publicada.

**Caché**: `staleTime` **explícito en toda query**, tomado de la política de `config/query.ts`. El default de TanStack Query es `0` —todo se considera viejo al instante— y dejarlo así es lo que produce el goteo de peticiones que se quiere evitar:

| Dato | `staleTime` | Por qué |
|---|---|---|
| Catálogos (sistemas, tags, plataformas) | 1 h | Solo los cambia un admin desde `/admin/catalogs` |
| Listado de mesas | 30 s | El WS cubre lo urgente; el `staleTime` cubre el resto |
| Detalle de mesa | 1 min | |
| Notificaciones | `Infinity` | El WS es su única fuente de cambio; pedirlas por tiempo es ruido puro |
| Perfil y karma | 5 min | El karma se recalcula al aprobar un comentario o en el job semanal (#97) |
| Bandeja de admins | 30 s | La reserva de #100 se libera sola a los 15 min |

**Sesión**: el access token vive **en memoria**, nunca en `localStorage` ni en `sessionStorage`; el refresh viaja en una cookie `httpOnly` + `SameSite=Strict` que el frontend no lee ni escribe. `client.ts` es el único que conoce el token: lo inyecta, y ante un `401` intenta el refresh una vez y reintenta la llamada; si el refresh también falla, limpia y manda a `/login`. La razón es concreta: hay editor de texto enriquecido renderizado en el navegador (#62), que es la superficie de XSS más directa del sistema, y un token en `localStorage` es legible por cualquier script que se cuele por ahí.

**Textos**: ningún string visible se escribe en el JSX. Todo pasa por `t('espacio.clave')` de i18next, con los JSON en `locales/<idioma>/<espacio>.json` — un espacio de nombres por feature (#107, #117). En v1 solo existe `es`, pero la indirección está desde el primer componente: retrofitearla después significa recorrer cada pantalla ya escrita, que es justo lo que hace caro el cambio.

**Componentes**: se construyen sobre las primitivas de `components/ui`. Antes de crear una primitiva nueva se consulta el MCP `shadcn-ui` para usar el componente real en vez de aproximar su API. Nada de MUI, Emotion ni styled-components en código nuevo.

**Formularios**: react-hook-form + zod, con el esquema en `schemas.ts` de la feature. El mismo esquema tipa el formulario y valida el submit, y queda atado al tipo del payload (§3.2, regla 7).

El formulario es un **componente puro, desacoplado de su contenedor** (#106): recibe valores iniciales y `onSubmit`, y no sabe si lo van a mostrar en un modal, en un panel lateral o en una página. El modal es un envoltorio aparte. Así el mismo `GameTableForm` sirve para crear en un diálogo y para editar en pantalla completa sin tocarlo.

El reparto es fijo (#110):

| Pieza | Qué hace | Qué **no** hace |
|---|---|---|
| `GameTableForm` | Monta `useForm` con su esquema zod, pinta los campos, valida y llama `onSubmit(values)` | No conoce la mutación, no invalida queries, no cierra nada, no muestra toasts |
| `EditGameTableDialog` | Compone `FormDialog` + el formulario, **posee la mutación** (`useUpdateGameTable`), cierra al terminar y avisa | No declara campos ni validación |
| `FormDialog` (`components/`) | El envoltorio: título, descripción, y confirmación al cerrar con cambios sin guardar | No sabe de dominio: no recibe entidades, solo `children` |

```tsx
// features/tables/components/EditGameTableDialog.tsx
export function EditGameTableDialog({ table, open, onOpenChange }: EditGameTableDialogProps) {
  const updateTable = useUpdateGameTable(table.id);

  return (
    <FormDialog open={open} onOpenChange={onOpenChange} title="Editar mesa" confirmOnDirtyClose>
      <GameTableForm
        defaultValues={table}
        onSubmit={async (values) => {
          await updateTable.mutateAsync(values);
          onOpenChange(false);
        }}
      />
    </FormDialog>
  );
}
```

`confirmOnDirtyClose` es lo único que se rescata del `ModalBase` viejo, que ya preguntaba antes de cerrar un modal a medio llenar. Lo que no se rescata es el resto de aquel reparto: allá el formulario recibía `handleCloseModal` y `reloadAction`, hacía él mismo el `PUT` y decidía cuándo cerrarse — por eso no se podía usar fuera de un modal, que es justo lo que #106 corrige.

**Fechas y horas** (#111): el backend manda ISO-8601 UTC (§2.5) y la conversión a hora local es del frontend, con **`Intl.DateTimeFormat` nativo** desde `lib/date.ts`. Sin librería de fechas: no hay aritmética de calendario en la aplicación —los horarios son día de semana más hora (`modelo-datos.md`)— y el `useDate` del legacy ya resolvía esto con `Intl`. La zona horaria sale del perfil del usuario, y solo si no está, del navegador; nunca se asume la del navegador cuando el perfil dice otra cosa. El locale y la zona son parámetros de las funciones de `date.ts`, nunca constantes incrustadas: el legacy tenía `'es-ES'` fijo en el hook.

**Tipos**: un tipo base por entidad y derivados con utility types (§3.2). Ningún componente declara a mano una variante de un tipo que ya existe.

**Ubicación y nombres**: dónde va cada archivo, cuándo sube a la raíz y qué sufijo lleva está en §3.1.1–§3.1.3, y el ruteo en §3.1.6.

**Estilos** (#109): Tailwind en el JSX. Los tokens del tema —colores, tipografía, radios, y los estados de mesa y postulación— se definen en el bloque `@theme` de `styles/globals.css`; no hay `tailwind.config.ts` en Tailwind 4.

Las variantes por props se resuelven con `class-variance-authority`, que es lo que shadcn/ui ya usa internamente, y la composición condicional con el helper `cn()`:

```ts
const badge = cva('inline-flex items-center rounded-md px-2 py-1 text-xs font-medium', {
  variants: {
    state: {
      Opened:     'bg-state-open/15 text-state-open',
      InProgress: 'bg-state-active/15 text-state-active',
      Canceled:   'bg-state-canceled/15 text-state-canceled',
    },
  },
});
```

**Nada de CSS-in-JS, archivos CSS por componente ni objetos de estilo en JavaScript** — el frontend viejo tenía los estilos en `styles/*.js` y cuatro motores conviviendo. Estilos inline solo para valores calculados en runtime.

### 3.4 Testing frontend

- **Vitest + React Testing Library**: hooks con lógica y componentes con comportamiento condicional. Se testea lo que el usuario ve, no la implementación.
- **Playwright** (`e2e/`): los cuatro flujos críticos — login con Discord, crear mesa (master), postularse a una mesa (jugador), subir archivo de preparación. Corre contra backend real, no mocks.

## 4. Reglas duras (las que no se negocian)

1. Un controller nunca llama a un repository. Siempre pasa por un service, incluso para una lectura trivial.
2. Una `@Entity` nunca cruza la frontera HTTP. Entrada y salida son DTOs.
3. Ningún endpoint devuelve un tipo inferido, genérico o abierto: nada de `Map<String, Object>`, `Object` ni `ResponseEntity<?>`. Todo lo que cruza HTTP es un `record` con nombre propio (§2.3).
4. No se abstrae por parecido: interfaz solo si hay más de una implementación real; clase abstracta solo si la misma forma se repite idéntica en tres o más features, y siempre después de ver la repetición (§2.4).
5. La autorización se declara en cada controller concreto. Nunca se hereda de una base genérica.
6. Toda regla de negocio nueva llega con su test unitario. No hay tests en ninguno de los dos repos viejos; ese patrón no se repite.
7. Nada de lógica de negocio en la base de datos: ni triggers, ni stored procedures.
8. Todo cambio de schema es una migración Flyway nueva. Nunca se edita una migración ya aplicada, nunca `ddl-auto: update`.
9. Un cambio en una `@Entity` actualiza `modelo-datos.md` en el mismo commit (skill `er-diagram-sync`).
10. `legacy/` es de solo lectura.
11. Datos de servidor en el frontend: solo TanStack Query.
12. En el frontend, un tipo base por entidad. Las variantes se derivan con utility types (`Pick`, `Omit`, `Partial`…), nunca se re-declaran a mano (§3.2). Nunca `any`.
13. Subir una major de cualquier cosa del stack de §1 es una decisión: se registra en `decisiones.md` y se actualiza §1 en el mismo commit.

## 5. Deuda del proyecto viejo que no se repite

Lista corta, tomada del inventario del código legacy. Es el contrapunto concreto de las reglas de arriba:

- Backend sin autenticación: el `user_id` llegaba por URL y se confiaba en él.
- IDs generados por un stored procedure (`generate_base64_id`) que nunca estuvo versionado — la app dependía de un objeto de BD inexistente en el `database.sql`.
- Cero paginación en todos los endpoints (TODO propio del autor).
- Status codes inconsistentes, con `418` usado como error genérico.
- Cascadas de borrado copiadas línea por línea entre el borrado individual y el masivo.
- Dos motores de estilos conviviendo en el frontend (MUI+Emotion y styled-components).
- `next-auth` instalado y jamás usado; i18n a medias con XML vacíos y un helper roto.
- Sin tests ni linter en ninguno de los dos proyectos.

## 6. Migraciones y arranque local

```bash
# Backend
cd backend && ./mvnw spring-boot:run          # perfil dev, Flyway aplica migraciones al arrancar
cd backend && ./mvnw test                     # unitarios + integración (Testcontainers necesita Docker)
cd backend && ./mvnw verify

# Frontend
cd frontend && npm run dev
cd frontend && npm run test
cd frontend && npx playwright test
```

Requisitos locales (versiones exactas en §1): JDK **25**, Node **24 LTS**, Docker (para Testcontainers), MySQL 8 local o en contenedor.
