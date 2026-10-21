# 6–8 Las herramientas de Claude en este repo

> Parte de la skill `entorno-local`. Hasta #292 era `entorno-local` §6. Lo que son las skills, las rules y los agentes del repo, y cuándo se usa cada uno, está en `CLAUDE.md`.

## 6. MCP servers (`.mcp.json`, raíz del repo)

Ninguno tiene secretos literales: todos usan `${VAR}` (variables de entorno) o autenticación interactiva, para que nada sensible quede commiteado.

| Server | Paquete | Para qué | Requiere |
|---|---|---|---|
| `mysql` | `@benborla29/mcp-server-mysql` | Introspección del schema real y queries de solo lectura contra la BD local — validar el modelo de la skill `arquitectura` (§4) contra datos reales antes de escribir entidades JPA | `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PASS`, `MYSQL_DB`. Insert/Update/Delete están deshabilitados en la config a propósito. |
| `context7` | `@upstash/context7-mcp` | Documentación versionada de Spring Boot, React, shadcn/ui, Tailwind — evita APIs desactualizadas o inventadas | Opcional: `CONTEXT7_API_KEY` (sin ella funciona con rate limit anónimo) |
| `playwright` | `@playwright/mcp` | Generar y ejecutar los e2e navegando el frontend real | Nada |
| `shadcn-ui` | `@jpisnice/shadcn-ui-mcp-server` | Código fuente real de cada componente shadcn/ui (props, estructura, bloques) | Opcional: `GITHUB_PERSONAL_ACCESS_TOKEN` (sube el límite de 60 a 5000 req/hora) |

GitHub MCP no se configuró: no fue solicitado. Si más adelante se quieren gestionar issues/PRs desde Claude Code, se agrega `github/github-mcp-server` con `claude mcp add`.

**Figma se removió** (#130). Estaba configurado apuntando al MCP remoto oficial, pero tanto ese como el servidor local de Figma Desktop exigen asiento Dev o Full en plan pago, que el proyecto no tiene. El diseño pasó a Claude Design.

### 6.1 Aprobación de los servidores del proyecto

Los servidores de `.mcp.json` no se cargan hasta que se aprueban una vez por proyecto. Si `claude mcp list` los muestra como `⏸ Pending approval`, ninguna de sus herramientas existe todavía. Se aprueban con `/mcp` dentro de la sesión; si la decisión quedó guardada como rechazada, se reabre con `claude mcp reset-project-choices` y se vuelve a arrancar `claude` en el repo.

### 6.2 Variables de entorno

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

## 7. Diseño — Claude Design

No es un MCP: es la herramienta `DesignSync`, integrada en Claude Code, así que no aparece en `.mcp.json` ni necesita aprobación de proyecto.

| | |
|---|---|
| **Para qué** | Publicar y revisar el design system del frontend: tokens y componentes como previews HTML + Tailwind, en un proyecto de design system de `claude.ai/design` |
| **Requiere** | Autorización única con `/design-login`, contra la cuenta de claude.ai. Funciona aunque la sesión se autentique con API key o token de proveedor |
| **Decisión** | #130. La fuente de verdad de los tokens sigue siendo el diseño (#118); lo que cambió es dónde vive |

## 8. Skills externas (globales, `~/.claude/skills/`)

Instaladas **fuera del repo**, a nivel de usuario. No se versionan y no las cubre la regla de arriba: no siguen las reglas de `arquitectura` porque no escriben código del proyecto — producen artefactos. Por eso viven en el directorio global y no en `.claude/skills/`, donde la frase «son skills propias» dejaría de ser cierta.

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
