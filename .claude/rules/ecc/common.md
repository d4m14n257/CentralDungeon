# ECC — reglas comunes (curadas para CentralDungeon)

> Selección de las reglas de ECC (`ecc@ecc`) para este repo, versionada desde ADR-01 §6. **Ante cualquier choque mandan `CLAUDE.md`, las rules del proyecto (`.claude/rules/*.md`) y la skill `arquitectura`.** Quedaron fuera a propósito las reglas de ECC que las contradicen: envoltura `{success, data, error}` (choca con ADR-04 §3 y ADR-23 §3), Conventional Commits, ramas y PRs (Git §2–3), cobertura mínima del 80% y TDD obligatorio (§5 define qué se testea), delegación automática a agentes (`plan-desarrollo` §7) e inmutabilidad absoluta (las `@Entity` son mutables).

## Calidad del código

Son guías para revisar, no topes que justifiquen partir algo cohesivo:

- Funciones enfocadas, de **menos de 50 líneas**. Si una crece más, se parte por responsabilidad.
- Archivos de código de **menos de 800 líneas**. Pasarse requiere un motivo; los tests y el código generado (`components/ui/`) quedan fuera.
- **Anidamiento de 4 niveles como máximo**. Se usan early returns en vez de condicionales apilados.
- **Nada de números mágicos**: un umbral, un límite o un plazo con significado va en una constante con nombre o en configuración.
- **Ningún error se traga en silencio**: se maneja, se propaga o se registra con contexto. Nunca un `catch` vacío.
- Se valida en la frontera del sistema y nunca se confía en datos externos (input del usuario, respuestas de terceros, contenido de archivos).
- KISS y YAGNI: la solución más simple que funciona y nada construido por adelantado. Para abstracciones, manda `arquitectura` §2.4.

## Seguridad: comprobar antes de cada commit

- Ningún secreto en el código: van en variables de entorno, como ya hace `.mcp.json` con `${VAR}`.
- Queries parametrizadas (`arquitectura` §2.2, ADR-03 §2) y HTML sanitizado (ADR-05 §6).
- Ninguna respuesta expone stack traces, rutas internas ni errores SQL: el detalle va al log y el cliente recibe el `ProblemDetail` con su código.
- Nunca se registran en el log tokens, contraseñas ni datos personales.
- Si aparece un problema de seguridad: se detiene el trabajo, se corrige lo crítico antes de seguir y se buscan casos similares en el resto del código.

## Delegación a subagentes

Cuando se delega (y solo cuando la tarea lo justifica):

1. **El mensaje final es el entregable.** Un agente nunca termina su turno con "esperando a los subagentes": una tarea lanzada no es una tarea terminada.
2. **Quien delega recoge.** Espera los resultados, los integra y recién después responde.
3. **Se descompone solo lo que no cabe en un contexto.** No se vuelve a delegar una tarea que ya tiene el tamaño de un solo agente.
4. Al delegar se pasa el **objetivo** además de la consulta, y se evalúa lo que vuelve: si falta algo, se repregunta (hasta 3 ciclos) antes de aceptarlo.
