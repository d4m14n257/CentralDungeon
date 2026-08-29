# MCP servers y skills

## MCP servers (`.mcp.json`, raíz del repo)

Ninguno tiene secretos literales: todos usan `${VAR}` (variables de entorno) o autenticación interactiva, para que nada sensible quede commiteado.

| Server | Paquete | Para qué | Requiere |
|---|---|---|---|
| `mysql` | `@benborla29/mcp-server-mysql` | Introspección del schema real y queries de solo lectura contra la BD local — validar `modelo-datos.md` contra datos reales antes de escribir entidades JPA | `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PASS`, `MYSQL_DB`. Insert/Update/Delete están deshabilitados en la config a propósito. |
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

## Skills (`.claude/skills/`)

Son skills propias, no de marketplaces de terceros, para que sigan exactamente las convenciones de `arquitectura.md`.

| Skill | Cuándo se usa |
|---|---|
| `nuevo-endpoint-java` | Agregar un endpoint al backend: controller + service + repository + DTO + mapper dentro del paquete de la feature, respetando las reglas duras de capas. |
| `nuevo-componente-react` | Agregar un componente o página al frontend: shadcn/ui + Tailwind + hook de TanStack Query, dentro de `features/<dominio>/`. |
| `tests-java` | Escribir o revisar tests del backend: JUnit 6 + Mockito para unitarios, Testcontainers para integración. |
| `er-diagram-sync` | Después de tocar cualquier `@Entity`: actualizar `modelo-datos.md` (diagrama + DDL) y crear la migración Flyway correspondiente. |
