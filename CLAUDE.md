# CentralDungeon

Sistema de gestión de mesas de Dungeons & Dragons de una comunidad: mesas con masters y jugadores, postulaciones de ingreso, catálogos (sistemas/tags/plataformas), horarios semanales, archivos de preparación y de personaje, comentarios anónimos con karma, notificaciones en tiempo real y cuatro roles: Player, Master, Admin y Owner.

Reescritura en curso: **Express/TypeScript → Java/Spring Boot** y **Next.js/MUI → React + Vite + shadcn/ui**. El proyecto estuvo detenido ~2 años (último commit del código viejo: 2024-08-15).

## Estado del repositorio

```
backend/            Java 25 + Spring Boot 4.1      ← por scaffoldear
frontend/           React 19 + Vite 8 + TypeScript ← scaffoldeado, sin páginas todavía
design/             Sistema de diseño — build.py genera tokens y previews
legacy/backend-node/    Express + TS (2024)  — SOLO LECTURA, referencia funcional
legacy/frontend-next/   Next.js 14 + MUI     — SOLO LECTURA, referencia funcional
docs/
```

`design/build.py` es la **fuente de verdad de todo valor de diseño** (#130): paleta, tipografía, espaciados y radios. Se corre con `python3 design/build.py` y escribe en `design/out/` —ignorado por git, como los PNG de `docs/diagramas/`— el `theme.css` que se transcribe al `@theme` del frontend, más las previews de los tokens, los componentes base y las pantallas del sitemap. **Cambiar un color es editar ese archivo y volver a correrlo**, nunca tocar el CSS generado. Cada corrida mide los 30 pares de contraste y **sale con código 1 si alguno cae por debajo de WCAG AA**, así que una paleta rota no se publica.

`legacy/` nunca se edita. Se consulta para recordar cómo se comportaba realmente el sistema, y se elimina cuando el código nuevo alcance paridad.

Existe otro repo, `CentralDungeonBackend` (intento previo de migración a Java), en **`/Users/d4m14n257/Personal/CentralDungeonBackend`** — fuera de este repo, consultable como referencia. **No se usa como base ni se copia código de ahí**. Solo se rescata la forma conceptual de su capa de config/seguridad. Se deprecará al terminar esta migración.

## Documentación

Sin redundancia entre ellos. Leer antes de trabajar en algo nuevo:

| Documento | Qué contiene |
|---|---|
| `docs/arquitectura.md` | Estructura de carpetas, patrón por feature, reglas de cada capa, contrato de API, seguridad, testing, convenciones de nombres. **La referencia para escribir código.** |
| `docs/modelo-datos.md` | Fuente de verdad del schema: convenciones, diagrama ER (Mermaid), DDL baseline, reglas de negocio que reemplazaron a los triggers, qué queda fuera de v1. |
| `docs/decisiones.md` | Qué se decidió y por qué. Se consulta cuando algo parece arbitrario, y se actualiza cuando una decisión cambia. 176 decisiones cerradas y el rastro de los 31 pendientes que se abrieron y resolvieron (M1–M31). |
| `docs/diagramas/` | Fuentes Mermaid (`.mmd`), sin PNG versionados — se regeneran con el comando del README. ER del **modelo objetivo** por subsistema (11–16) y los ciclos de vida de mesa, postulación y comentario (05–07). |
| `docs/frontend-diseno.md` | Sitemap por contexto, navegación, sistema de diseño, wireframes e inventario de componentes. **Se lee antes de crear cualquier pantalla.** |
| `docs/plan-desarrollo.md` | Etapas de construcción, qué se rescata del legacy y definición de terminado. |
| `docs/mcp-y-skills.md` | MCP servers y skills configurados, y las variables de entorno que hay que exportar localmente. |

## Stack

Versiones **fijadas** en `docs/arquitectura.md` §1 (agosto 2026). Subir una major es una decisión, no un mantenimiento.

**Backend** — Java 25 (LTS), Spring Boot 4.1.1, Maven, Spring Data JPA + Hibernate 7.4, MySQL 8, Flyway, Spring Security 7.1 + OAuth2 (Discord) + JWT, Bean Validation, MapStruct, springdoc-openapi 3.1, Caffeine (caché en proceso). Tests: JUnit 6 + Mockito + AssertJ + Testcontainers 2.x.

Ojo con Boot 4 al leer material escrito para Boot 3: **Jackson 3** (`tools.jackson.*`), **JSpecify** en vez de `org.springframework.lang.@Nullable`, **JUnit 6** (nada de JUnit 4 ni Vintage), **Testcontainers 2.x** (clases reubicadas por módulo), `RestTemplate` ya no se autoconfigura.

**Frontend** — TypeScript 5.9 (`strict`), Vite 8, React 19.2, React Router 8 (paquete `react-router`), shadcn/ui + Tailwind 4 (config en CSS, **no** hay `tailwind.config.ts`), TanStack Query 5 (estado de servidor), Zustand 5 (estado de UI global), react-hook-form 7 + zod 4, i18next (todo texto visible pasa por `t()` desde el día uno). Tests: Vitest + React Testing Library, Playwright para e2e. Node 24 LTS.

Organización en ambos lados: **por feature de dominio** (`com.centraldungeon.<feature>/` y `src/features/<dominio>/`). En el backend, las capas van adentro de la feature más un `common/` transversal; en el frontend, las **pantallas viven fuera** de las features en `src/routes/`, y lo sin dominio en capas de la raíz (`components/`, `hooks/`, `lib/`, `api/`, `types/`, `config/`). Detalle en `docs/arquitectura.md` §2.1 y §3.1.

## Reglas duras

1. Un controller nunca llama a un repository — siempre pasa por un service, incluso para una lectura trivial.
2. Una `@Entity` nunca cruza la frontera HTTP: entrada y salida son DTOs (`record`).
3. Ningún endpoint devuelve un tipo inferido o abierto: nada de `Map<String, Object>`, `Object` ni `ResponseEntity<?>`. Todo lo que cruza HTTP tiene su `record` con nombre propio (`docs/arquitectura.md` §2.3).
4. No se abstrae por parecido: interfaz solo si hay más de una implementación real, clase abstracta solo si la misma forma se repite idéntica en 3+ features y ya se vio repetida (§2.4). **Los controllers son clases concretas, sin interfaz de contrato** (#119): el contrato lo publica OpenAPI. La autorización nunca se hereda ni vive en una lista de rutas: cada controller concreto declara su `@PreAuthorize` en el método (#123, CVE-2025-41248).
5. **Cuatro roles, acumulables salvo una pareja**: `Player`, `Master`, `Admin`, `Owner`. `Player` y `Master` se suman con cualquiera; **`Admin` y `Owner` son el mismo rol con distinto alcance y no se acumulan** — nadie tiene los dos, y `Owner` puede todo lo de `Admin` (#169). **No se registra un `RoleHierarchy`**: cada endpoint enumera sus roles (`hasAnyRole('ADMIN','OWNER')`). Y ojo con los dos `Owner`: el rol de plataforma y `masters.master_type = 'Primary'` no son lo mismo y no se llaman igual (`docs/decisiones.md` #67, #71, #89).
6. En el frontend, **un tipo base por entidad**; las variantes se derivan con utility types (`Pick`, `Omit`, `Partial`, `Record`…), nunca se re-declaran a mano (§3.2). Nunca `any`.
7. Toda regla de negocio nueva llega con su test unitario. No hay tests en ninguno de los dos repos viejos; ese patrón no se repite.
8. Nada de lógica de negocio en la base de datos: ni triggers ni stored procedures. Lo que era trigger vive ahora en el service layer.
9. Todo cambio de schema es una migración Flyway nueva. Nunca se edita una migración aplicada, nunca `ddl-auto: update`.
10. Un cambio en una `@Entity` actualiza `docs/modelo-datos.md` en el mismo commit (skill `er-diagram-sync`).
11. En el frontend, datos de servidor solo con TanStack Query. Nada de `useEffect` + `fetch`, nada de respuestas de API en Context o Zustand.
12. `legacy/` es de solo lectura.
13. **Campañas y Temporadas están fuera de la v1 a propósito** — no las agregues al modelo ni al código sin decisión explícita (#7). Su **diseño** ya está cerrado (#129) y los tres puntos a resolver antes de construirlas, en `docs/modelo-datos.md` §7.1.
14. Nada de código copiado literal de `CentralDungeonBackend`.
15. Subir una major del stack es una decisión: se registra en `docs/decisiones.md` y se actualiza `docs/arquitectura.md` §1 en el mismo commit.
16. En el frontend, **una feature nunca importa de otra**. Las pantallas van en `src/routes/` y son el único lugar que compone dominios; cada bloque de una pantalla compuesta recibe un **id**, no una entidad (§3.1.5). Cada feature expone su superficie en `features/<dominio>/index.ts`.
17. **El rol no es la pertenencia**, y en las mesas la pertenencia manda: dirigir una mesa concreta se autoriza **solo por la fila en `masters`**, sin exigir el rol `Master` — que significa "puedo crear mesas propias" y nada más (#135). Toda lectura o mutación de un recurso concreto filtra por el actor —el actor en el `WHERE`, o verificación explícita en el service antes de tocar nada— y el actor sale siempre del token, nunca de la URL (#121). El JWT afirma **identidad, no autorización**: roles y `status` se leen de la base en cada request (#122).
18. Ningún string visible se escribe en el JSX: todo pasa por `t('espacio.clave')` con el JSON en `src/locales/es/` (#117). Y ningún valor de estilo suelto: los tokens salen del `@theme`, que se transcribe desde el design system en Claude Design (#118, #130).

## Comandos

```bash
# Backend
cd backend && docker compose up -d       # MySQL 8 en localhost:3306
cd backend && ./mvnw spring-boot:run     # perfil dev; Flyway aplica migraciones al arrancar
cd backend && ./mvnw test                # solo unitarios (*Test) — no necesitan Docker
cd backend && ./mvnw verify              # unitarios + integración (*IT, Testcontainers) — necesita colima arriba, ver backend/README.md

# Frontend
cd frontend && npm run dev
cd frontend && npm run test          # Vitest
cd frontend && npm run test:e2e      # Playwright, contra el backend real — arrancarlo con -Dspring-boot.run.profiles=dev,test (backend/README.md)
cd frontend && npx tsc -b            # typecheck (strict)
cd frontend && npm run format        # prettier con la config del repo (#174)

# Transcribir el tema al frontend tras cambiar design/build.py (#118, #130)
python3 design/build.py
{ printf '@import "tailwindcss";\n\n'; cat design/out/theme.css; } > frontend/src/styles/globals.css

# Diseño
python3 design/build.py                  # regenera design/out/ y mide los contrastes
open design/out/accent-decision.html     # cualquier preview, en el navegador

# Legacy (solo consulta)
cd legacy/backend-node && npm run dev
cd legacy/frontend-next && npm run dev
```

Requisitos locales: JDK 25 vía **SDKMAN**, Node 24 LTS vía **nvm**, contenedores vía **colima** (no Docker Desktop), MySQL 8 como contenedor. Detalle de instalación y las variables que Testcontainers necesita bajo colima, en `backend/README.md`.

## MCP servers

Configurados en `.mcp.json`, sin secretos literales. Detalle y variables de entorno en `docs/mcp-y-skills.md`.

| Server | Uso |
|---|---|
| `mysql` | Introspección de la BD local, solo lectura — validar el modelo contra datos reales |
| `context7` | Documentación actualizada de Spring Boot, React, shadcn/ui, Tailwind |
| `playwright` | Tests e2e sobre el frontend real |
| `shadcn-ui` | Código fuente real de los componentes shadcn, para no inventar props |

El diseño **no** va por MCP: se usa `DesignSync` contra Claude Design, autorizado una vez con `/design-login` (#130). Figma se removió — exige plan pago.

## Skills

En `.claude/skills/`. Son propias, para que sigan exactamente las convenciones de `docs/arquitectura.md`.

| Skill | Cuándo |
|---|---|
| `nuevo-endpoint-java` | Agregar un endpoint: controller + service + repository + DTO + mapper en el paquete de su feature |
| `nuevo-componente-react` | Agregar un componente o página: shadcn/ui + Tailwind + hook de TanStack Query en `features/<dominio>/` |
| `tests-java` | Escribir o revisar tests del backend (JUnit 6, Mockito, Testcontainers 2.x) |
| `er-diagram-sync` | Después de tocar cualquier `@Entity`: migración Flyway + actualizar `docs/modelo-datos.md` |

## Git

Mientras el proyecto no sea productivo, estas cinco valen sin excepción:

1. **Ningún commit lleva co-autor.** Nada de `Co-Authored-By`, ni de cualquier otra forma de atribución o firma al pie. Bajo ningún esquema.
2. **Sin convención de commits.** Nada de Conventional Commits, prefijos, scopes ni emojis.
3. **Todo va a `master` directo.** Un solo desarrollador: no se crean ramas ni pull requests.
4. **El push va junto con el commit.** Al terminar de commitear se hace `git push` a `origin/master` sin volver a preguntar — la autorización está dada. Un commit sin publicar se considera trabajo a medias.
5. **El mensaje describe todo lo que se hizo**, no solo el titular: qué cambió, por qué, y lo que se decidió o descartó en el camino. Es el registro que queda cuando la conversación ya no está.

Se revisan cuando el proyecto pase a productivo, no antes.

## Idioma

Documentación y conversación en **español**.

**Todo el código en inglés, sin excepciones — incluidos los comentarios.** Eso cubre identificadores, nombres de tabla y columna, endpoints, comentarios de código, Javadoc, mensajes de log, comentarios SQL de las migraciones y mensajes de commit. Un archivo de código no debería tener una sola palabra en español.

Lo único en español dentro del repo son los `docs/` y este archivo.
