# CentralDungeon

Sistema de gestión de mesas de Dungeons & Dragons de una comunidad: mesas con masters y jugadores, postulaciones de ingreso, catálogos (sistemas/tags/plataformas), horarios semanales, archivos de preparación y de personaje, comentarios anónimos con karma, notificaciones en tiempo real y cuatro roles: Player, Master, Admin y Owner.

Reescritura en curso: **Express/TypeScript → Java/Spring Boot** y **Next.js/MUI → React + Vite + shadcn/ui**. El proyecto estuvo detenido ~2 años (último commit del código viejo: 2024-08-15).

## Estado del repositorio

```
backend/            Java 25 + Spring Boot 4.1      ← F1, F2 y F3 construidas
frontend/           React 19 + Vite 8 + TypeScript ← idem; pantallas en src/routes/
design/             Sistema de diseño — build.py genera tokens y previews
legacy/backend-node/    Express + TS (2024)  — SOLO LECTURA, referencia funcional
legacy/frontend-next/   Next.js 14 + MUI     — SOLO LECTURA, referencia funcional
docs/               Documentación; docs/adr/ son las decisiones, una ADR por tema
.claude/            skills/ (cómo se hace), rules/ (reglas por ruta), agents/ (revisores)
```

`design/build.py` es la **fuente de verdad de todo valor de diseño** (ADR-21 §1); cómo se corre y se transcribe al frontend está en `.claude/rules/tokens.md`, que se carga al tocar `design/` o los estilos.

`legacy/` nunca se edita. Se consulta para recordar cómo se comportaba realmente el sistema, y se elimina cuando el código nuevo alcance paridad.

Existe otro repo, `CentralDungeonBackend` (intento previo de migración a Java), en **`/Users/d4m14n257/Personal/CentralDungeonBackend`** — fuera de este repo, consultable como referencia. **No se usa como base ni se copia código de ahí**. Solo se rescata la forma conceptual de su capa de config/seguridad. Se deprecará al terminar esta migración.

## Dónde vive cada cosa

Sin redundancia: cada cosa se dice en un solo lugar, y los demás remiten. Desde ADR-01 §5, `docs/` solo guarda lo que no es una regla: las ADRs, el plan de fases, la fase en curso y los diagramas.

| Qué | Dónde |
|---|---|
| **El stack fijado** (§1), **cómo se escribe el código** —backend (§2), frontend (§3), modelo de datos con su DDL y su ER (§4), testing (§5), Javadoc/JSDoc (§6), procedimientos (§7)— y **configuración y despliegue** (§8) | Skill `arquitectura` |
| **Las pantallas y lo visual**: principios (§1), navegación y sitemap (§2), tokens y temas (§3), pantallas clave (§4), componentes, responsive y clases de patrón (§5) | Skill `diseno` |
| **El entorno local** y sus trampas (§1–§5), los MCP servers con sus variables (§6), Claude Design (§7) y las skills externas (§8) | Skill `entorno-local` |
| **Las reglas duras**, cargadas por ruta | `.claude/rules/*.md` (índice abajo) |
| **Qué se decidió y por qué** | `docs/adr/`, una ADR por tema; se cita `ADR-NN §k` (ADR-01 §8). El README es el índice, lo abierto, cómo se escribe una nueva y la tabla de equivalencias de la numeración anterior (`#n`) |
| Fases de construcción, qué se rescata del legacy, definición de terminado | `docs/plan-desarrollo.md` |
| **La fase en curso (F4): cómo se revisa lo construido** y la deuda que dejaron F1–F3 | `docs/fase-4-revision.md` |
| Diagramas: ER por subsistema (11–16), ciclos de vida (05–07), navegación real de la UI (21–24) | `docs/diagramas/` (fuentes `.mmd` y `.architecture.json`; se regeneran con los comandos de su README) |

## Stack

Versiones **fijadas** en `arquitectura` §1 (agosto 2026). Subir una major es una decisión, no un mantenimiento (regla dura 15).

**Backend** — Java 25 (LTS), Spring Boot 4.1.1, Maven, Spring Data JPA + Hibernate 7.4, MySQL 8, Flyway, Spring Security 7.1 + OAuth2 (Discord) + JWT, Bean Validation, MapStruct, springdoc-openapi 3.1, Caffeine (caché en proceso). Tests: JUnit 6 + Mockito + AssertJ + Testcontainers 2.x. **Es Boot 4**: Jackson 3, JSpecify, JUnit 6 — el detalle de lo que cambia respecto de Boot 3, en `.claude/rules/stack.md`.

**Frontend** — TypeScript 5.9 (`strict`), Vite 8, React 19.2, React Router 8 (paquete `react-router`), shadcn/ui + Tailwind 4 (config en CSS, **no** hay `tailwind.config.ts`), TanStack Query 5 (estado de servidor), Zustand 5 (estado de UI global), react-hook-form 7 + zod 4, i18next (**español e inglés**, ADR-23 §2). Tests: Vitest + React Testing Library, Playwright para e2e. Node 24 LTS.

Organización en ambos lados: **por feature de dominio** (`com.centraldungeon.<feature>/` y `src/features/<dominio>/`). En el backend, las capas van adentro de la feature más un `common/` transversal; en el frontend, las **pantallas viven fuera** de las features en `src/routes/`, y lo sin dominio en capas de la raíz (`components/`, `hooks/`, `lib/`, `api/`, `types/`, `config/`). Detalle en `arquitectura` §2.1 y §3.1.

## Reglas duras

Se citan por número desde los docs —el código no las cita (ADR-01 «Proyecto, proceso y conocimiento» §12 «La documentación del código se explica sola»)— y **el número no cambia** (ADR-01 §6). Las del dominio están completas acá porque valen al planificar cualquier cosa; el resto se carga desde su rule al tocar los archivos de su ruta, con el detalle en la skill.

| # | Regla | Dónde |
|---|---|---|
| 1 | Un controller nunca llama a un repository | `rules/backend.md` |
| 2 | Una `@Entity` nunca cruza la frontera HTTP | `rules/backend.md` |
| 3 | Ningún endpoint devuelve un tipo abierto: todo es un `record` con nombre | `rules/backend.md` |
| 4 | No se abstrae por parecido; controllers concretos, `@PreAuthorize` en cada método | `rules/backend.md` |
| 5 | Cuatro roles, `Admin` y `Owner` excluyentes, sin `RoleHierarchy` | abajo |
| 6 | Un espejo a mano por cada `…Response` en el frontend; lo que arma el frontend, derivado; nunca `any` | `rules/frontend.md` |
| 7 | Toda regla de negocio nueva llega con su test | `rules/backend.md`, `rules/frontend.md` |
| 8 | Nada de lógica de negocio en la base | abajo |
| 9 | Todo cambio de schema es una migración nueva; una aplicada no se toca | `rules/migraciones.md` |
| 10 | Un cambio de `@Entity` actualiza `arquitectura` §4 y el ER en el mismo commit | `rules/backend.md` |
| 11 | Datos de servidor solo con TanStack Query | `rules/frontend.md` |
| 12 | `legacy/` es de solo lectura | abajo |
| 13 | Campañas y Temporadas fuera de v1 | abajo |
| 14 | Nada copiado literal de `CentralDungeonBackend` | `rules/backend.md` |
| 15 | Subir una major del stack es una ADR | `rules/stack.md` |
| 16 | Una feature nunca importa de otra | `rules/frontend.md` |
| 17 | El rol no es la pertenencia; el actor sale del token | abajo |
| 18 | Ningún texto ni valor de estilo suelto; el backend manda códigos, no frases | `rules/frontend.md`, `rules/backend.md` |
| 19 | Todo lo público lleva su Javadoc o JSDoc, en inglés | `rules/documentacion-codigo.md` |

**5.** **Cuatro roles, acumulables salvo una pareja**: `Player`, `Master`, `Admin`, `Owner`. `Player` y `Master` se suman con cualquiera; **`Admin` y `Owner` son el mismo rol con distinto alcance y no se acumulan** — nadie tiene los dos, y `Owner` puede todo lo de `Admin` (ADR-06 §1). **No se registra un `RoleHierarchy`**: cada endpoint enumera sus roles (`hasAnyRole('ADMIN','OWNER')`). Y ojo con los dos `Owner`: el rol de plataforma y `masters.master_type = 'Primary'` no son lo mismo y no se llaman igual (ADR-06 §1, §3).

**8.** Nada de lógica de negocio en la base de datos: ni triggers ni stored procedures. Lo que era trigger vive ahora en el service layer.

**12.** `legacy/` es de solo lectura.

**13.** **Campañas y Temporadas están fuera de la v1 a propósito** — no las agregues al modelo ni al código sin decisión explícita (ADR-07 §10). Su **diseño** ya está cerrado (ADR-07 §10) y los tres puntos a resolver antes de construirlas, en `arquitectura` §4.7.1.

**17.** **El rol no es la pertenencia**, y en las mesas la pertenencia manda: dirigir una mesa concreta se autoriza **solo por la fila en `masters`**, sin exigir el rol `Master` — que significa "puedo crear mesas propias" y nada más (ADR-06 §4). Toda lectura o mutación de un recurso concreto filtra por el actor —el actor en el `WHERE`, o verificación explícita en el service antes de tocar nada— y el actor sale siempre del token, nunca de la URL (ADR-05 §5). El JWT afirma **identidad, no autorización**: roles y `status` se leen de la base en cada request (ADR-05 §3).

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
cd frontend && npm run format        # prettier con la config del repo (ADR-02 §4)

# Diseño
python3 design/build.py                  # regenera design/out/ y mide los contrastes
open design/out/accent-decision.html     # cualquier preview, en el navegador

# Legacy (solo consulta)
cd legacy/backend-node && npm run dev
cd legacy/frontend-next && npm run dev
```

Requisitos locales: JDK 25 vía **SDKMAN**, Node 24 LTS vía **nvm**, contenedores vía **colima** (no Docker Desktop), MySQL 8 como contenedor. Detalle de instalación y las variables que Testcontainers necesita bajo colima, en `backend/README.md`.

## MCP servers

Configurados en `.mcp.json`, sin secretos literales. Detalle y variables de entorno en `entorno-local` §6.

| Server | Uso |
|---|---|
| `mysql` | Introspección de la BD local, solo lectura — validar el modelo contra datos reales |
| `context7` | Documentación actualizada de Spring Boot, React, shadcn/ui, Tailwind |
| `playwright` | Tests e2e sobre el frontend real |
| `shadcn-ui` | Código fuente real de los componentes shadcn, para no inventar props |

El diseño **no** va por MCP: se usa `DesignSync` contra Claude Design, autorizado una vez con `/design-login` (ADR-21 §1). Figma se removió — exige plan pago.

## Skills

En `.claude/skills/`, versionadas con el repo. **Son la forma en que se guarda cómo se hace cada cosa**, y los docs las citan por sección (`arquitectura §2.3`, `diseno §5.c`); el código no. Son tres, a propósito (ADR-01 §5): lo aprendido va a la sección que corresponde de una de ellas, nunca a una skill nueva. Hay además una skill **externa y global**, `archify`, para los diagramas interactivos de `docs/diagramas/` (detalle en `entorno-local` §8).

| Skill | Cuándo |
|---|---|
| `arquitectura` | Antes de escribir o revisar cualquier código, de tocar una `@Entity`, una migración o una regla de negocio, o de escribir tests. El stack fijado (§1), backend (§2), frontend (§3), modelo de datos con su DDL y su ER (§4), testing (§5), Javadoc y JSDoc (§6), los procedimientos —nuevo endpoint, nuevo componente o pantalla, cambio de schema— (§7) y configuración y despliegue (§8) |
| `diseno` | Antes de diseñar o escribir una pantalla o un componente, o de agregar un estilo: los principios (§1), la navegación y el sitemap (§2), de dónde sale cada valor (§3), las pantallas clave (§4), el inventario de componentes (§5), el responsive (§5.b) y **las clases y componentes de patrón** que una pantalla no vuelve a escribir (§5.c, ADR-21 §5) |
| `entorno-local` | Antes de correr, reiniciar o verificar algo contra el backend, el frontend, Playwright o Testcontainers — y antes de culpar al código por una suite entera en rojo. También los MCP servers y sus variables (§6), Claude Design (§7) y `archify` (§8) |

## Rules

En `.claude/rules/`, versionadas (ADR-01 §6). Cada una se carga sola al tocar un archivo de su `paths:`, y remite a la skill para el detalle. Las de `rules/ecc/` dicen qué parte de ECC aplica aquí y cuál choca: `common.md` e `integracion.md` se cargan siempre; `java.md` y `typescript.md`, por extensión.

## Agentes

En `.claude/agents/`, versionados (ADR-01 §7). Cada uno precarga la skill del repo que le toca. **Se lanzan cuando el usuario o la tarea lo piden**, no por iniciativa propia; la columna «Ejecuta» de un plan es un pedido (ADR-01 «Proyecto, proceso y conocimiento» §13 «Cada tarea de un plan dice con qué modelo se planea y con cuál se ejecuta»). Los revisores no editan: devuelven hallazgos triados en las tres categorías de `fase-4-revision.md` §3 (bug, alcance de F5 o F6, decisión nueva); el auditor, en las suyas (corrección directa, decisión nueva, limpieza).

| Agente | Cuándo |
|---|---|
| `revisor-backend` | Después de escribir o cambiar Java, o para revisar una feature del backend |
| `revisor-frontend` | Después de escribir o cambiar una pantalla, un componente o un hook |
| `revisor-seguridad` | Endpoints nuevos, cambios de autorización o de archivos, la rebanada F4.4 |
| `auditor-conocimiento` | Periódicamente, al cerrar una rebanada o cuando una skill parezca vieja: reglas que el código ya no cumple, citas rotas, repetición, comentarios que mienten |
| `escriba-adr` | Al cerrar una decisión: escribe la ADR, marca la que corrige y actualiza la regla en su skill |

## Planes

Todo plan dice, **en cada tarea, con qué modelo se planea y con cuál se ejecuta** (ADR-01 «Proyecto, proceso y conocimiento» §13 «Cada tarea de un plan dice con qué modelo se planea y con cuál se ejecuta»): **Fable 5.1** planea lo más complejo —decisiones que cruzan varias ADRs, modelo de datos, seguridad—; **Opus 5.5** planea lo demás, diagnostica bugs y escribe ADRs; **Sonnet 5.5** ejecuta lo de volumen ya decidido —barridos, renombres, traducciones, ajustes de UI—, en paralelo por feature si hace falta. Primero se planea cada punto. Con el plan aprobado, **la sesión principal —siempre Opus 5.5, sin cambiar de modelo con `/model`— delega** lo que es de otro modelo a subagentes con ese `model` (`sonnet`, `fable`), y lanza **en paralelo**, en un mismo mensaje, todas las tareas sin dependencias entre sí.

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

Lo único en español dentro del repo son los `docs/`, `.claude/` y este archivo.

## Cómo se nombran las cosas

**Ningún número va solo** (ADR-01 «Proyecto, proceso y conocimiento» §11). Al hablarle al usuario, y en todo texto nuevo de `docs/`, `.claude/` y los mensajes de commit, cada identificador numerado lleva su nombre descriptivo al lado:

- una ADR, con su título, y la sección con el suyo: `ADR-19 «Arquitectura del frontend» §6 «El diálogo es dueño de su formulario y de su mutación»`;
- una fase o rebanada: `F4 «Revisión»`, `F4.1 «El mapa de la interfaz»`;
- una regla dura: `regla dura 6 «Un espejo por cada respuesta»`;
- un hallazgo o decisión de una lista: `B6 «Una mesa nunca pública se leía por id»`, `D3 «Las acciones del master van en un aviso de estado»`.

El número se conserva, porque es lo que se busca y lo que no es ambiguo: se le suma el nombre. Lo ya escrito se corrige al tocar cada archivo, no en bloque. **El código no cita ninguno de estos números**: ni ADRs, ni skills, ni reglas, ni fases, ni hallazgos (ADR-01 «Proyecto, proceso y conocimiento» §12 «La documentación del código se explica sola»). Su documentación se publica y tiene que entenderse sin el repo.

**Dónde está cada nombre**: el de una ADR, en el índice de `docs/adr/README.md`, y el de una sección, en su título `## §k`; los de las fases y todas sus rebanadas, en la tabla de `docs/plan-desarrollo.md` §4 «Fases»; los de las reglas duras, en la tabla de arriba.
