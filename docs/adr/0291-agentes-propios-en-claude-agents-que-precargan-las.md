---
adr: 291
estado: Aceptada
fecha: 2026-10-20
---

# ADR-0291: Agentes propios en `.claude/agents/`, que precargan las skills del repo y devuelven hallazgos

## Decisión

Cinco subagentes versionados en `.claude/agents/`:

| Agente | Precarga | Para qué |
|---|---|---|
| `revisor-backend` | `arquitectura`, `entorno-local` | Revisar Java contra §2, §4, §5.1 y §6.1 |
| `revisor-frontend` | `arquitectura`, `diseno` | Revisar React/TS contra §3, §5.2, §6.2 y `diseno` |
| `revisor-seguridad` | `arquitectura` | Pertenencia, JWT, `@PreAuthorize`, sesión, sanitizador, `404` que no confirman (#121–#127, #186) |
| `auditor-conocimiento` | las tres | Mejora continua del propio conocimiento: reglas vencidas, citas rotas, ADRs sin reflejo en su skill, repetición, comentarios que mienten |
| `escriba-adr` | `arquitectura` | Escribir una ADR con el formato de `docs/adr/README.md` y actualizar la regla que cambia |

Los cuatro revisores **no editan**: devuelven hallazgos triados en las tres categorías de `fase-4-revision.md` §3. Solo `escriba-adr` escribe. Ninguno hace commit.

Cada uno puede pedir una skill de ECC con la herramienta Skill (`ecc:jpa-patterns`, `ecc:security-review`, `ecc:comment-analyzer`…) como lista de control, con los mismos límites de `.claude/rules/ecc/integracion.md`. Las skills de ECC no se precargan: Claude Code no documenta la precarga de skills de un plugin, y precargarlas pondría lo genérico a la par de la vara del repo.

Se lanzan **cuando el usuario o la tarea lo piden**, no por iniciativa propia (`integracion.md`).

## Por qué

Los agentes de ECC son buenos revisores genéricos, pero arrancan sin las skills del repo: un `ecc:java-reviewer` propone `@MockBean` y JUnit 5 (Boot 3), un `ecc:security-reviewer` pide CSRF en todo contra #127, y ninguno sabe que el rol no es la pertenencia (#135). Cada uso exigía reescribirles el contexto en el prompt, y lo que no se escribía se perdía. Un agente propio carga la skill entera al arrancar y devuelve el formato de triaje que F4 ya usa.

El auditor existe porque las skills se escribieron rápido y no tenían quién las revisara contra el código: es el mecanismo para que una skill sin actualizar se note antes de que alguien la siga.
