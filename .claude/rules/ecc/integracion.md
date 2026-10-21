# ECC — cómo convive con las skills del repo

> Versionada desde ADR-01 §6: quien herede el repo con ECC hereda también qué parte de ECC choca con el proyecto. ECC (`ecc@ecc`) está instalado con scope local solo en este repo, y aporta 293 skills, 94 comandos y 68 agentes pensados para cualquier proyecto. Este archivo dice cuál manda en cada tema.

## Precedencia

1. `CLAUDE.md` y las rules del proyecto (`.claude/rules/*.md`).
2. Las skills del repo (`arquitectura`, `diseno`, `entorno-local`) y las ADRs de `docs/adr/`.
3. Las reglas de `.claude/rules/ecc/`.
4. Los agentes del repo (`.claude/agents/`), que precargan las skills del punto 2.
5. Skills, agentes y comandos de ECC.

**Una skill de ECC nunca reemplaza a la del repo.** Primero se lee la del repo (su tabla en `CLAUDE.md` dice cuándo), y ECC solo complementa lo que la del repo no cubre. Si se contradicen, gana la del repo, y la contradicción no se resuelve editando una regla del repo: un cambio de regla es una decisión, con su sección en `docs/adr/`.

## Por tema

| Tema | Fuente de verdad | ECC que suma | ECC que no se sigue |
|---|---|---|---|
| Backend | `arquitectura` §2 | `ecc:java-reviewer` para revisar; `ecc:jpa-patterns` y `ecc:springboot-security` como referencia | `ecc:springboot-patterns`, `-tdd` y `-verification` (Boot 3, `@MockBean`, JUnit 5); `ecc:api-design` y `ecc:backend-patterns` (envoltura `{success, data, error}`, contra ADR-04 §3 y ADR-23 §3) |
| Testing | `arquitectura` §5 y `entorno-local` | `ecc:e2e-testing` para patrones generales de Playwright, sin cambiar la estructura de `frontend/e2e/` ni sus helpers (`acceptReview`, `test-login`); `@ParameterizedTest` | `ecc:tdd-workflow`, `ecc:verification-loop` y `/ecc:test-coverage`: el 80% de cobertura y el TDD obligatorio no son reglas de este repo (§5 dice qué se testea) |
| Datos y migraciones | `arquitectura` §4 y §7.3 | `ecc:mysql-patterns`; `ecc:database-migrations` solo por principios (forward-only, una migración aplicada no se toca) | Ejemplos y consejos de PostgreSQL (`JSONB`, `SERIAL`, `ecc:database-reviewer`) sin verificarlos contra MySQL 8 |
| Frontend | `arquitectura` §3 | `ecc:react-reviewer` y `ecc:typescript-reviewer` para revisar; `ecc:i18n-sync` para claves `es` y `en` en el mismo commit (ADR-23 §2) | Cualquier patrón que salte TanStack Query, `api/client.ts`, la regla feature ≠ pantalla o el barrel único (§3.1, §3.3) |
| Diseño | `diseno` y `design/build.py` (ADR-21 §1) | Ninguno | `ecc:frontend-design-direction`, `ecc:design-system`, `ecc:make-interfaces-feel-better`, `ecc:liquid-glass-design` y `ecc:taste*`: inventan dirección visual o tokens, contra la regla dura 18 y ADR-21 §5 |
| Decisiones | `docs/adr/`, una ADR por tema; se cita `ADR-NN §k` (ADR-01 §8) | El agente del repo `escriba-adr` | `ecc:architecture-decision-records`: su plantilla es otra, en inglés. El formato es el de `docs/adr/README.md` |
| Documentación | `docs/` sin redundancia; Javadoc y JSDoc según `arquitectura` §6 | `ecc:comment-analyzer` para encontrar comentarios que mienten | `/ecc:update-docs`, `/ecc:update-codemaps`, `/ecc:plan-prd` y `ecc:living-docs-governance`: crean `CONTRIBUTING`, `RUNBOOK`, `CODEMAPS` o PRDs que duplican lo que ya existe |
| `CLAUDE.md` | Se edita solo a pedido del usuario | Ninguno | `ecc:codebase-onboarding` (genera o reescribe `CLAUDE.md`) |
| Git | `CLAUDE.md` §Git | Ninguno | `ecc:git-workflow`, `/ecc:pr`, `/ecc:prp-commit` y `/ecc:review-pr`: ramas, PRs y Conventional Commits |
| Planificación | Plan mode, `docs/plan-desarrollo.md` y `docs/fase-4-revision.md` | `/ecc:plan` como formato de plan | `ecc:orch-*`, `ecc:plan-orchestrate`, `/ecc:multi-*`, `/ecc:prp-*` y `/ecc:epic-*`: pipelines propios que pisan el reparto de §7 y el corte de F4 |
| Seguridad | `arquitectura` §2.6 y sus decisiones (ADR-05 §5, §3, §4, §2, §6, ADR-03 §2, ADR-16 §2) | `ecc:security-reviewer` y `ecc:security-review` | Sus ejemplos con respuesta `{success: false}`, y «CSRF en todo» (ADR-05 §2 lo activa solo en `/auth/refresh`, con motivo) |

## Agentes de ECC

- **Primero el agente del repo** (ADR-01 §7): `revisor-backend` en vez de `ecc:java-reviewer`, `revisor-frontend` en vez de `ecc:react-reviewer` y `ecc:typescript-reviewer`, `revisor-seguridad` en vez de `ecc:security-reviewer`, y `auditor-conocimiento` en vez de `ecc:comment-analyzer` y `ecc:doc-updater`. Ya cargan la skill y devuelven el triaje de F4. El de ECC se usa solo para lo que esos no cubren (`ecc:silent-failure-hunter`, `ecc:pr-test-analyzer`, `ecc:type-design-analyzer`, `ecc:performance-optimizer`…).
- **Se lanzan cuando la tarea o el usuario lo piden, no por iniciativa propia.** El «use PROACTIVELY» de sus descripciones no aplica en este repo.
- El prompt de un agente de ECC incluye el objetivo, que lea `CLAUDE.md` y la sección de `arquitectura` que toca, y que el stack es Boot 4, JUnit 6, MySQL 8 y React 19.
- En F4, lo que devuelve es un **hallazgo**, no una corrección: se tría en las tres categorías de `fase-4-revision.md` §3 antes de tocar código.

## Lo que se aprende va al repo

- Un patrón aprendido sobre CentralDungeon (un tropiezo, una convención, un cómo) se guarda en **la sección que corresponde de `arquitectura`, `diseno` o `entorno-local`**, o en su ADR de `docs/adr/` si cambia una regla. **Nunca en una skill nueva** (son tres a propósito, ADR-01 §5) **ni en `~/.claude/skills/`**.
- `/ecc:learn` escribe en `~/.claude/skills/` (global), así que no se usa para lo de este repo. En `/ecc:learn-eval`, el destino «Project» significa editar la skill existente, no crear una carpeta nueva.
- Los instintos de continuous-learning (incluidos los «globales», como «Conventional commits») nunca prevalecen sobre `CLAUDE.md`.

## Verificación

- El typecheck que el hook de ECC corre al terminar **no revisa nada aquí**: `frontend/tsconfig.json` es un tsconfig de solución con `"files": []`. El typecheck real es `cd frontend && npx tsc -b`.
- Los comandos de verificación son los de `CLAUDE.md` §Comandos (`./mvnw test` o `verify`, `npm run test`, `npx tsc -b`, `npm run format`), con las precauciones de `entorno-local`.
