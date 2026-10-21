# 1 Stack y versiones

> Parte de la skill `arquitectura`. Hasta #292 era `arquitectura` §1, y conserva su numeración: una cita a `arquitectura` §1.1 es `arquitectura` §1.1. La deuda del proyecto viejo, que era el §5 de ese documento, es §1.4.

Las versiones de abajo son las **fijadas para el proyecto**: son las que se escriben en `pom.xml` y `package.json` al scaffoldear, y las que asume el resto de esta skill. Se fijaron en agosto de 2026, al retomar el proyecto. La política para cambiarlas está en §1.3.

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

TypeScript **se introduce** en el frontend: `legacy/frontend-next/` era JavaScript (`jsconfig.json`, archivos `.js`/`.jsx`). Lo que se conservaba era el TypeScript del backend Node. Es lo que hace posible la disciplina de tipos de §3.2 y lo que hace que un cambio de contrato en el backend se detecte compilando y no en producción (#20).

| Área | Elección | Versión fijada |
|---|---|---|
| Lenguaje | TypeScript, `strict: true` | 5.9.x |
| Runtime de build | Node (Active LTS) | 24.x — mínimo 22, lo exige React Router 8 |
| Build / dev server | Vite | **8.x** — bundling con Rolldown/Oxc en lugar de esbuild+Rollup |
| UI | React | **19.2.x** |
| Ruteo | React Router (data router, `createBrowserRouter`) | **8.x** — paquete `react-router`, **no** `react-router-dom` (quedó como alias de compatibilidad) |
| Componentes | shadcn/ui sobre Radix + Tailwind CSS | Tailwind **4.3.x** vía `@tailwindcss/vite` |
| Estado de servidor | TanStack Query | 5.10x |
| Estado de UI | Zustand global, Context por subárbol, `useState` local — criterio en `arquitectura` §3.3 | 5.x |
| Formularios | react-hook-form + zod + `@hookform/resolvers` | RHF 7.8x (la 8 está en beta), zod 4.x, resolvers 5.x |
| HTTP | `fetch` envuelto en un cliente propio tipado | — |
| Fechas y horas | `Intl.DateTimeFormat` nativo, sin librería (#111) | — |
| Internacionalización | `i18next` + `react-i18next`, JSON por espacio de nombres (#107, #117) | 25.x / 16.x |
| Lienzo de nodos | React Flow (`@xyflow/react`), MIT — solo para el patrón de lienzo de la skill `diseno` §5 (#275), cargado con `lazy` en su ruta | **12.12.0** |
| Tests | Vitest + React Testing Library, Playwright para e2e | Vitest **4.x**, RTL 16.x, Playwright 1.6x |

Consecuencias de estas versiones:

- **Tailwind 4 se configura en CSS, no en JS**: no hay `tailwind.config.ts`. El tema vive en `src/styles/globals.css` con `@import "tailwindcss"` y un bloque `@theme`; la detección de contenido es automática. Cualquier receta que hable de `content: [...]` o de `@tailwind base` es de la v3.
- **React Router 8 es ESM-only** y asume React 19 y Node 22+. Los `future.v8_*` flags ya no existen: su comportamiento es el default.
- **Vite 8 usa Rolldown**. Antes de agregar un plugin que dependa de internals de Rollup, verificar que esté portado. **Ya mordió una vez**: Vitest 3 declara peer `vite ^5–^7`, así que npm le instala **su propio Vite** anidado y `defineConfig` de `vitest/config` deja de tipar contra el Vite del proyecto — el error habla de `rolldownVersion` faltante en `PluginContextMeta`. Se resuelve con **Vitest 4**, que sí declara `vite ^8`. Si aparece un `node_modules/vitest/node_modules/vite`, es este problema.
- **`npm create vite` instala TypeScript 6**, y el stack está fijado en **5.9.x**. Al scaffoldear se bajó a `~5.9.3` a propósito: subir una major es una decisión (regla dura 15), no algo que decida una plantilla. No lo "arregles" subiéndolo.
- **React Flow trae su propia hoja de estilos**, y `globals.css` se reescribe entero al transcribir el tema (#118, #130): por eso se importa desde `components/graph/GraphCanvas.tsx` y sus colores son variables `--xy-*` apuntadas a tokens en `.graph-canvas` (`styles/base.css`). Sin motor de layout (dagre, ELK): un grupo de profundidad 1 es una estrella y se ubica con `radialLayout` (#275).
- **El template trae `oxlint`**, no ESLint. No estaba fijado en ningún lado, así que se conserva; si se cambia, es una decisión.

### 1.3 Política de versiones

- La fuente de verdad ejecutable es `pom.xml` / `package.json`; esta sección es el objetivo declarado. Si divergen, se corrige el que esté mal y se deja constancia acá.
- Las versiones de las dependencias del backend **no se escriben a mano**: las gestiona el BOM de Spring Boot. Solo se fija explícitamente lo que el BOM no cubre (springdoc, MapStruct).
- En el frontend, las dependencias del stack (las de la tabla) se instalan con versión exacta, sin `^`. El resto puede usar el default de npm. El lockfile se commitea siempre.
- **Subir una major de cualquier cosa de estas tablas es una decisión**: se registra como ADR nueva en `docs/adr/` y se actualiza esta sección en el mismo commit (regla dura 15, `.claude/rules/stack.md`). Subir un minor o un patch no requiere nada.

### 1.4 Deuda del proyecto viejo que no se repite

Lista corta, tomada del inventario del código legacy. Es el contrapunto concreto de las reglas de esta skill:

- Backend sin autenticación: el `user_id` llegaba por URL y se confiaba en él.
- IDs generados por un stored procedure (`generate_base64_id`) que nunca estuvo versionado — la app dependía de un objeto de BD inexistente en el `database.sql`.
- Cero paginación en todos los endpoints (TODO propio del autor).
- Status codes inconsistentes, con `418` usado como error genérico.
- Cascadas de borrado copiadas línea por línea entre el borrado individual y el masivo.
- Dos motores de estilos conviviendo en el frontend (MUI+Emotion y styled-components).
- `next-auth` instalado y jamás usado; i18n a medias con XML vacíos y un helper roto.
- Sin tests ni linter en ninguno de los dos proyectos.
