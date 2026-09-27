# MCP servers y skills

## MCP servers (`.mcp.json`, raíz del repo)

Ninguno tiene secretos literales: todos usan `${VAR}` (variables de entorno) o autenticación interactiva, para que nada sensible quede commiteado.

| Server | Paquete | Para qué | Requiere |
|---|---|---|---|
| `mysql` | `@benborla29/mcp-server-mysql` | Introspección del schema real y queries de solo lectura contra la BD local — validar la skill `modelo-datos` contra datos reales antes de escribir entidades JPA | `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PASS`, `MYSQL_DB`. Insert/Update/Delete están deshabilitados en la config a propósito. |
| `context7` | `@upstash/context7-mcp` | Documentación versionada de Spring Boot, React, shadcn/ui, Tailwind — evita APIs desactualizadas o inventadas | Opcional: `CONTEXT7_API_KEY` (sin ella funciona con rate limit anónimo) |
| `playwright` | `@playwright/mcp` | Generar y ejecutar los e2e navegando el frontend real | Nada |
| `shadcn-ui` | `@jpisnice/shadcn-ui-mcp-server` | Código fuente real de cada componente shadcn/ui (props, estructura, bloques) | Opcional: `GITHUB_PERSONAL_ACCESS_TOKEN` (sube el límite de 60 a 5000 req/hora) |

GitHub MCP no se configuró: no fue solicitado. Si más adelante se quieren gestionar issues/PRs desde Claude Code, se agrega `github/github-mcp-server` con `claude mcp add`.

**Figma se removió** (#130). Estaba configurado apuntando al MCP remoto oficial, pero tanto ese como el servidor local de Figma Desktop exigen asiento Dev o Full en plan pago, que el proyecto no tiene. El diseño pasó a Claude Design.

### Aprobación de los servidores del proyecto

Los servidores de `.mcp.json` no se cargan hasta que se aprueban una vez por proyecto. Si `claude mcp list` los muestra como `⏸ Pending approval`, ninguna de sus herramientas existe todavía. Se aprueban con `/mcp` dentro de la sesión; si la decisión quedó guardada como rechazada, se reabre con `claude mcp reset-project-choices` y se vuelve a arrancar `claude` en el repo.

### Variables de entorno

Van en el perfil de shell del usuario, no en el repo:

```bash
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3306
export MYSQL_USER=root
export MYSQL_PASS=...
export MYSQL_DB=centraldungeon
# opcionales
export CONTEXT7_API_KEY=...
export GITHUB_PERSONAL_ACCESS_TOKEN=...
```

Después de exportarlas, reabrir Claude Code en el repo o correr `/mcp` para reconectar.

## Diseño — Claude Design

No es un MCP: es la herramienta `DesignSync`, integrada en Claude Code, así que no aparece en `.mcp.json` ni necesita aprobación de proyecto.

| | |
|---|---|
| **Para qué** | Publicar y revisar el design system del frontend: tokens y componentes como previews HTML + Tailwind, en un proyecto de design system de `claude.ai/design` |
| **Requiere** | Autorización única con `/design-login`, contra la cuenta de claude.ai. Funciona aunque la sesión se autentique con API key o token de proveedor |
| **Decisión** | #130. La fuente de verdad de los tokens sigue siendo el diseño (#118); lo que cambió es dónde vive |

## Skills propias (`.claude/skills/`)

Son skills propias, escritas acá, y **se versionan con el repo**. Hay dos clases:

- **De conocimiento**: guardan cómo es el proyecto y son la **fuente** de sus reglas. Desde F4.0, las reglas de arquitectura y el modelo de datos viven acá y no en `docs/`, y lo que Claude sabía solo por su memoria local de la máquina —el entorno y sus trampas— también. El código las cita por sección (`arquitectura-backend §2.3`, `modelo-datos` skill §5), con la misma numeración que tenían como documentos. Cada una tiene un `SKILL.md` corto con las reglas que se aplican siempre y una carpeta `references/` con el detalle, que se lee solo cuando hace falta.
- **De procedimiento**: los pasos, en orden, para una tarea que se repite. No repiten las reglas: invocan las de conocimiento.

| Skill | Clase | Cuándo se usa |
|---|---|---|
| `arquitectura-backend` | Conocimiento | Antes de escribir o revisar código Java: paquetes por feature, reglas por capa, DTOs, contrato de la API, seguridad y pertenencia, testing y Javadoc (§2.1–§2.8). |
| `arquitectura-frontend` | Conocimiento | Antes de escribir o revisar código del frontend: dónde va cada archivo, feature ≠ pantalla, ruteo, modelo de tipos, estado, i18n, formularios, fechas y estilos (§3.1–§3.4). |
| `modelo-datos` | Conocimiento | Antes de tocar una `@Entity`, una migración, una query o una regla de negocio: convenciones (§1), DDL y migraciones (§4, §6), reglas de negocio (§5), fuera de v1 (§7) y `roles-y-alcance.md`, lo normativo de F3. |
| `entorno-local` | Conocimiento | Antes de correr o verificar algo contra el backend, el frontend, Playwright o Testcontainers: el backend del usuario y no uno paralelo, nunca `mvn clean` con la JVM viva, el autobuild de JDT, las variables de colima. |
| `nuevo-endpoint-java` | Procedimiento | Agregar un endpoint al backend: controller + service + repository + DTO + mapper dentro del paquete de la feature. |
| `nuevo-componente-react` | Procedimiento | Agregar un componente o página al frontend, con su puerta de entrada si es pantalla. |
| `tests-java` | Procedimiento | Escribir o revisar tests del backend: JUnit 6 + Mockito para unitarios, Testcontainers para integración. |
| `er-diagram-sync` | Procedimiento | Después de tocar cualquier `@Entity`: migración Flyway, fila en la tabla de migraciones de `modelo-datos` y diagramas ER. |

## Skills externas (globales, `~/.claude/skills/`)

Instaladas **fuera del repo**, a nivel de usuario. No se versionan y no las cubre la regla de arriba: no siguen las reglas de `arquitectura-backend` ni `arquitectura-frontend` porque no escriben código del proyecto — producen artefactos. Por eso viven en el directorio global y no en `.claude/skills/`, donde la frase «son skills propias» dejaría de ser cierta.

| Skill | Origen | Para qué |
|---|---|---|
| `archify` | [`tt-a1i/archify`](https://github.com/tt-a1i/archify) (MIT) | Diagramas de arquitectura, flujo, secuencia, datos y ciclo de vida como HTML autocontenido e interactivo. Lo usa `docs/diagramas/` para los `.architecture.json` — ver su README. |

Se instaló con el CLI `skills` de [vercel-labs](https://github.com/vercel-labs/skills):

```bash
npx -y skills@latest add tt-a1i/archify --skill archify --agent claude-code --global --copy --yes
```

Tres cosas que conviene tener escritas, porque no se ven después:

- **`--skill archify` y no el repo entero.** El repositorio publica dos skills; la segunda, `archify-review`, es para mantener *ese* proyecto y no tiene nada que hacer acá.
- **`--copy` y no el enlace simbólico por defecto**, que apuntaría a un caché que se puede limpiar. La skill ocupa 8,4 MB y no tiene dependencias de runtime: solo Node ≥ 18.
- **El instalador manda telemetría de instalación** (su `--metadata` la documenta). Es el CLI, no la skill.

Verificación: `node ~/.claude/skills/archify/bin/archify.mjs doctor`.
