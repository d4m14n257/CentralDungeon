# Arquitectura

> **Qué queda acá y qué se mudó.** Desde F4.0, las reglas de cómo se escribe el código viven en **skills del repo**, que Claude carga al trabajar y cualquiera lee en `.claude/skills/`: `arquitectura` (§2 el backend, §3 el frontend) y, para lo visual, `diseno`. Conserva la numeración que tenían acá, que es la que citan el código y `decisiones.md`. Las reglas duras están en `CLAUDE.md`.
>
> Este documento conserva lo que no es una regla de escritura: el stack con sus versiones fijadas (§1), la deuda del proyecto viejo que las reglas evitan (§5) y el arranque y despliegue (§6). El modelo de datos es la skill `arquitectura` §4; el razonamiento detrás de cada decisión, `decisiones.md`.

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
- **Versionado de API nativo**: Framework 7 trae versionado de API de primera clase (path, header, query, media type). Acá **no se usa por ahora**: la versión va en el path (`arquitectura` §2.5) y mientras exista una sola versión viva no se agrega maquinaria. Si algún día hay v2, se usa ese soporte nativo, no controllers duplicados.
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
| Estado de UI | Zustand global, Context por subárbol, `useState` local — criterio en `arquitectura` §3.3 | 5.x |
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


## 2 y 3. Backend y frontend — en las skills

| § | Dónde vive ahora |
|---|---|
| 2.1 – 2.6 | Skill `arquitectura` §2: paquetes, capas, DTOs, abstracción, contrato de la API, seguridad |
| 3.1 – 3.3 | Skill `arquitectura` §3: estructura y ruteo, modelo de tipos, reglas de datos, estado, i18n, formularios, fechas y estilos |
| 2.7, 3.4 → 5 | Skill `arquitectura` §5: testing de los dos lados |
| 2.8 → 6 | Skill `arquitectura` §6: Javadoc y JSDoc |

El §4 de este documento repetía las reglas duras de `CLAUDE.md` y se borró: dos copias de la misma lista terminan diciendo cosas distintas.

## 5. Deuda del proyecto viejo que no se repite

Lista corta, tomada del inventario del código legacy. Es el contrapunto concreto de las reglas de la skill `arquitectura`:

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
cd backend && docker compose up -d            # MySQL 8 en localhost:3306
cd backend && ./mvnw spring-boot:run          # perfil dev, Flyway aplica migraciones al arrancar
cd backend && ./mvnw test                     # solo unitarios (*Test) — no necesitan Docker
cd backend && ./mvnw verify                   # unitarios + integración (*IT, Testcontainers) — necesita colima arriba

# Frontend
cd frontend && npm run dev
cd frontend && npm run test
cd frontend && npx playwright test            # contra el backend real, arrancado con -Dspring-boot.run.profiles=dev,test
```

Requisitos locales (versiones exactas en §1): JDK **25** vía **SDKMAN**, Node **24 LTS** vía **nvm**, contenedores vía **colima** (no Docker Desktop — su runtime no expone `/var/run/docker.sock`; ver `DOCKER_HOST` en `backend/README.md`), MySQL 8 siempre como contenedor.

Solo la base de datos vive en Docker. El backend corre nativo (`./mvnw spring-boot:run`), sin contenedor propio ni `Dockerfile` — dockerizarlo alenta el ciclo de guardar-y-ver-el-cambio, que es exactamente lo que el hot reload de abajo optimiza.

### 6.1 Configuración: `.env`, nunca variables de shell

Cada servicio (`backend/`, `frontend/`) tiene su propio `.env.example`, versionado, con **todas** las variables que ese servicio lee — nombre y para qué sirve, nunca el secreto real. Se copia a `.env` (backend) o `.env.local` (frontend) para tener valores propios; ambos patrones ya estaban en el `.gitignore` de la raíz.

Por qué no alcanza con `export` en el perfil de shell (como sí se usa para las variables de los MCP servers, `docs/mcp-y-skills.md`): esas viven en la máquina de quien las exportó y no se documentan en ningún lado del repo. Cada persona que clona el proyecto — o un cliente probando localmente sus propias credenciales de Discord — tendría que llegar a pedir la lista completa en vez de encontrarla en un archivo. `.env.example` es esa lista, versionada y siempre al día porque vive al lado del código que la usa.

Spring Boot, a diferencia de Vite, no lee `.env` de forma nativa: `backend/pom.xml` agrega `springboot4-dotenv` (scope `runtime`, `optional`) para que los mismos placeholders `${VAR:default}` que ya existían en `application*.yml` resuelvan también desde un `.env` local, sin cambiar una línea de código. En un servidor real las variables se inyectan por el mecanismo del hosting (systemd, la plataforma, lo que sea) — ahí no hace falta ningún `.env`, y la librería simplemente no encuentra el archivo y no hace nada.

### 6.2 Despliegue en producción

**Una VM Linux genérica, sin atarse a un proveedor** (`decisiones.md` #142). En esta etapa se prueba en una instancia EC2 propia, pero el cliente muy probablemente corre el sistema final en una instancia de Oracle Cloud — así que nada de esto puede depender de un servicio propietario de ninguno de los dos. Para el backend, EC2 y Oracle Compute son la misma cosa: una máquina Linux con Docker, donde corre el jar y, al lado, MySQL.

**Perfil `prod`** (`application-prod.yml`): la misma variable `${DB_URL}` que ya usa `dev` (con su default a `localhost:3306` para no pedir nada en local), pero en `prod` **sin default** — si falta, el arranque falla en vez de conectarse silenciosamente a algo que no es. Mantiene agnóstico si MySQL termina corriendo en la misma instancia o en otro lado, y ambos perfiles comparten el mismo nombre de variable en vez de inventar uno distinto por entorno.

**MySQL en producción**: **dónde corre no está decidido** — la misma instancia, un servicio administrado, o cualquier otra cosa. Por eso la aplicación no trae un compose de producción: el único `docker-compose.yml` del repo levanta la MySQL **de desarrollo**, con sus credenciales locales, y Docker no se usa para nada más. Lo que sí es fijo, venga de donde venga la base: contraseñas reales por variable de entorno, nunca `centraldungeon`/`centraldungeon`, y el puerto cerrado a todo lo que no la necesite.

**Los secretos reales nunca son un archivo `.env` en el repo.** Un solo `backend/.env.example` documenta **las mismas variables** para los dos casos — `DB_URL` incluida, con default local en `application-dev.yml` y sin default en `application-prod.yml` — así que no hay dos listas que puedan desalinearse. El archivo real de producción vive **en el servidor**, fuera de este directorio de trabajo — por ejemplo `/etc/centraldungeon/prod.env` — y lo entrega el sistema operativo, no `spring-dotenv` (que solo mira `.env` en el directorio del proceso; por eso el archivo real ni se llama igual). Un ejemplo con `systemd`:

```ini
# /etc/systemd/system/centraldungeon.service
[Service]
EnvironmentFile=/etc/centraldungeon/prod.env
ExecStart=/usr/bin/java -jar /opt/centraldungeon/backend.jar --spring.profiles.active=prod
Restart=always
User=centraldungeon
```

`/etc/centraldungeon/prod.env` es un ejemplo de cómo un entorno concreto puede entregar esas variables, no la forma en que se decidió desplegar: `systemd` lo convierte en variables de entorno reales antes de arrancar el jar. Otro entorno las entregará a su manera. Lo único que la aplicación exige es que lleguen como variables del sistema y que haya **una sola fuente de verdad por secreto**, no copias que puedan divergir.

Delante de todo esto falta un reverse proxy (TLS, dominio) — no decidido todavía, y fuera del alcance de este repo: es configuración del servidor, no de la aplicación.

`springboot4-dotenv` queda en el jar empaquetado (el plugin de Spring Boot no lo excluye como sí hace con `devtools`), pero es inofensivo: sin un archivo llamado `.env` en el directorio desde donde arranca el proceso, no hace nada. La regla simple que lo mantiene así: **jamás copiar `backend/.env` a un servidor.**
