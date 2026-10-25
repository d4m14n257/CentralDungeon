---
adr: 1
titulo: Proyecto, proceso y conocimiento
estado: Vigente
actualizada: 2026-10-24
absorbe: [1, 6, 18, 102, 162, 177, 181, 250, 266, 267, 274, 289, 290, 291, 292]
---

# ADR-01: Proyecto, proceso y conocimiento

> Cómo está armado el repo, en qué orden se construye, quién ejecuta cada rebanada y dónde se guarda lo que se sabe del proyecto: skills, rules, agentes, ADRs, MCP y diagramas. Las reglas de escritura que salen de acá viven en `CLAUDE.md`, en `.claude/rules/` y en `entorno-local` §6–§8; esta ADR dice *qué* se decidió y *por qué*.

## §1 Un solo repo, y el código viejo queda en `legacy/` de solo lectura hasta la paridad

**Decisión.** El backend Java y el frontend React viven en un solo repo, `CentralDungeon`, con un solo `CLAUDE.md` y un solo juego de MCP, skills, rules y agentes. El código viejo está en `legacy/backend-node/` y `legacy/frontend-next/`: no se edita (regla dura 12), se consulta para recordar cómo se comportaba el sistema real y se elimina cuando el código nuevo alcance paridad.

**Por qué.** El código nuevo reemplaza al viejo en el mismo lugar, y repartirlo en dos repos duplicaría la configuración de Claude y el contexto de cada sesión. El legacy no se borra de entrada porque es la única referencia fiel de comportamiento —no hay tests ni documentación de cómo funcionaba—, y no se edita para que siga siendo eso: un registro, no una segunda base de código.

*Antes: #1, #18.*

## §2 Todo el código en inglés; los documentos y la conversación, en español

**Decisión.** Identificadores, tablas, columnas, endpoints, comentarios, Javadoc, JSDoc, logs, comentarios SQL de las migraciones y mensajes de commit van en inglés, sin excepción. En español quedan solo `docs/`, `CLAUDE.md`, las skills y la conversación.

**Por qué.** Con los comentarios en español los archivos eran bilingües: la firma de un método en un idioma y su explicación en otro. Un solo idioma dentro del código lo hace legible para cualquiera que llegue, y deja el español donde aporta, en la documentación y las decisiones. Lo que lee una persona en la interfaz no es código: sale de los archivos de idioma (ADR-23 §3, §2).

*Antes: #102.*

## §3 Se construye por actor, en seis fases, y la revisión es una fase propia

**Decisión.** **F1 Master, F2 Jugador, F3 Admin y Owner, F4 Revisión, F5 Comunidad, F6 Operación.** Cada fase entrega la experiencia completa de un actor y arrastra los subsistemas que ese actor necesita —catálogos y archivos entran con el master porque su mesa los pide—, con **el mínimo del otro lado para poder probarla**: si el master publica una agenda, el jugador la ve en la misma fase. F1, F2 y F3 cierran hechas y con sus tests en verde, pero **sin revisar**, y entregan su deuda de revisión escrita. F4 no construye producto: verifica el mapa de la interfaz, la matriz de roles, la integridad y la seguridad de las tres juntas, y termina con una revisión mano a mano del cliente (`docs/fase-4-revision.md`). Las referencias ya escritas a fases o etapas viejas no se reescriben —«F1.4 construyó X» es un hecho fechado—; solo se actualiza lo que **promete** algo para una fase que cambió.

**Por qué.** El corte por subsistema repartía a cada actor en cuatro etapas y nunca había una mesa completa que probar. El master va primero porque fabrica lo que los demás consumen. La revisión sale de cada fase porque lo que se rompe son las costuras entre actores, y una costura no se mira hasta que existen sus dos lados: la matriz de roles no tiene respuesta sin los cuatro roles, ni una prueba de IDOR sin dos actores con derechos distintos. Y la atención del cliente es escasa: se gasta una vez, sobre el producto entero. El costo —bugs que viven más— se acota con los tests obligatorios y la deuda escrita en el momento.

*Antes: #177, #250.*

## §4 Cada rebanada se ejecuta con el contrato primero y constructores en paralelo

**Decisión.** El hilo principal fija el **contrato** —endpoints, DTOs, rutas, `queryKeys`—; después **A1 Backend** y **A2 Frontend** trabajan en paralelo con directorios disjuntos; después **A3 Verificación**; y el cierre —docs, ayuda, commit— vuelve al hilo principal. **A4 Revisor** es opcional entre constructores y A3 en las rebanadas pesadas. Los agentes no commitean. Vale para las fases que construyen (F1–F3, F5, F6), no para F4. Detalle en `plan-desarrollo.md` §7.

**Por qué.** Sin contrato el frontend adivina la forma del DTO y la adivina mal, así que el paralelismo no ahorra tiempo. A3 no es «el que escribe los tests»: la regla dura 7 pide que cada regla llegue con su test escrito por quien la escribió, porque un test puesto después describe lo que el código hace y no lo que la regla exige. A3 aporta lo que ningún constructor cubre solo: concurrencia con Testcontainers, Playwright cruzando los dos lados, los cuatro estados de cada pantalla y el camino manual. Un commit por agente rompería el mensaje que describe todo lo hecho (`CLAUDE.md` §Git).

*Antes: #181.*

## §5 El conocimiento vive en tres skills que conservan su numeración

**Decisión.** Tres skills en `.claude/skills/`: **`arquitectura`** (stack §1, backend §2, frontend §3, modelo de datos §4, testing §5, Javadoc y JSDoc §6, procedimientos §7, configuración y despliegue §8), **`diseno`** (lo visual, ADR-21 §5) y **`entorno-local`** (la máquina y las herramientas, §6–§8). Los documentos de referencia que había en `docs/` se disolvieron en la skill dueña del tema conservando el número de sección; `docs/` queda con las ADRs, el plan, la fase en curso y los diagramas. Cada mudanza conserva la numeración, y las citas dentro de migraciones aplicadas no se reescriben (regla dura 9): siguen resolviendo por el número conservado. Lo que se aprende del proyecto va a la sección que corresponde de una de las tres, nunca a una skill nueva ni a `~/.claude/`.

**Por qué.** Repartida en siete skills, cada tarea obligaba a cargar tres o cuatro que se citaban entre sí, y las reglas del Javadoc vivían en tres lugares. Los documentos de `docs/` eran normativos pero Claude no los carga al trabajar, así que una regla se aplicaba solo si alguien se acordaba de abrirlos. Conservar los números es la condición: el código ya los cita, y renumerar dejaría cientos de citas apuntando a otra sección sin que nada fallara.

*Antes: #274, #292.*

## §6 Las reglas duras se cargan por ruta desde `.claude/rules/`

**Decisión.** Cada regla dura vive en una rule de `.claude/rules/` con `paths:` en el frontmatter, que Claude Code carga al tocar un archivo que coincide: `backend.md` (`backend/**`: 1, 2, 3, 4, 7, 10, 14 y la mitad backend de 18), `frontend.md` (`frontend/**`: 6, 7, 11, 16, 18), `migraciones.md` (`db/migration/**`: 8, 9, 10), `documentacion-codigo.md` (código de `backend/src`, `frontend/src`, `frontend/e2e`: 19), `tokens.md` (`design/**`, `frontend/src/styles/**`) y `stack.md` (`pom.xml`, `package.json` y `arquitectura/references/stack.md`: 15). Los números de las reglas se conservan. `CLAUDE.md` tiene el índice de las 19 y desarrolla completas solo 5, 8, 12, 13 y 17, las que valen al planificar cualquier cosa. Las rules de `.claude/rules/ecc/` —qué parte de ECC choca con el proyecto— se versionan. Una rule es el resumen que se impone; la skill, el detalle y el porqué.

**Por qué.** `CLAUDE.md` se carga entero en cada sesión, y las diecinueve reglas desarrolladas pesaban igual en una sesión de diseño que en una de migraciones. Por ruta, cada regla está en contexto justo cuando se puede romper. Las rules de ECC se versionan porque el cliente recibe el repo para seguir mejorándolo, y si usa ECC necesita saber que su envoltura `{success, data, error}`, sus skills de Boot 3 y su TDD obligatorio no aplican.

*Antes: #290.*

## §7 Agentes propios que precargan las skills y devuelven hallazgos

**Decisión.** Cinco subagentes en `.claude/agents/`: `revisor-backend` (precarga `arquitectura` y `entorno-local`), `revisor-frontend` (`arquitectura`, `diseno`), `revisor-seguridad` (`arquitectura`; vara: ADR-05 §2–§6, ADR-03 §2, §8), `auditor-conocimiento` (las tres; reglas vencidas, citas rotas, ADRs sin reflejo en su skill, repetición) y `escriba-adr` (`arquitectura`). Los cuatro revisores **no editan**: devuelven hallazgos triados en las tres categorías de `fase-4-revision.md` §3. Solo `escriba-adr` escribe, y ninguno commitea. Pueden pedir una skill de ECC como lista de control, con los límites de `.claude/rules/ecc/integracion.md`, pero no la precargan. Se lanzan cuando el usuario o la tarea lo piden.

**Por qué.** Los agentes de ECC arrancan sin las skills del repo: proponen `@MockBean` y JUnit 5, piden CSRF en todo contra ADR-05 §2 y no saben que el rol no es la pertenencia (ADR-06 §4); cada uso exigía reescribirles el contexto. Las skills de ECC no se precargan porque pondrían lo genérico a la par de la vara del repo. El auditor existe para que una skill desactualizada se note antes de que alguien la siga.

*Antes: #291.*

## §8 Las decisiones viven en ADRs temáticas, numeradas ADR-01 a ADR-NN

**Decisión.** `docs/adr/` tiene una ADR por tema, `NN-slug.md`, con frontmatter (`adr`, `titulo`, `estado`, `actualizada`, `absorbe`) y secciones `§k`, cada una con la decisión vigente y su motivo. **Se cita `ADR-NN §k`** desde el código, las skills, las rules y los docs. Una sección dice lo que rige hoy: cuando una decisión cambia, se reescribe la sección —o se agrega una `§k` nueva al final del tema, o una ADR nueva si el tema no existe— y la historia queda en git. Las 292 decisiones sueltas que existían (`#1`–`#292`) se fundieron en estos temas; cada sección cierra con su línea *Antes* con los números viejos que absorbió, y `docs/adr/README.md` trae la **tabla de equivalencias** `#n → ADR-NN §k`, que es lo que resuelve las citas viejas que no se pueden reescribir: las de las migraciones Flyway aplicadas (regla dura 9) y las de los mensajes de commit. Lo que no es decisión —contexto, lo abierto, el índice M1–M32— va en el README. El formato lo aplica el agente `escriba-adr`; `ecc:architecture-decision-records` no se usa porque trae su propia plantilla en inglés.

**Por qué.** La tabla única de `docs/decisiones.md` dejó de ser consultable, y un archivo por decisión —292 archivos, muchos que corregían o anulaban a otros— obligaba a recorrer cadenas de correcciones para saber qué regía. Por tema, un asunto se lee entero y en su estado vigente. Se renumeró en vez de conservar `#n` porque una numeración de 292 entradas sin orden temático no le dice nada a quien la lee; el costo fueron unas 5.300 citas reescritas, y la tabla de equivalencias cubre las que no se pueden tocar.

*Antes: #289.*

## §9 MCP servers: mysql, context7, playwright y shadcn-ui

**Decisión.** `.mcp.json` configura cuatro servers, sin secretos literales: `mysql` (introspección de la base local, solo lectura), `context7` (documentación actual del stack), `playwright` (e2e sobre el frontend real) y `shadcn-ui` (el código real de cada componente). No hay GitHub MCP, y el diseño no va por MCP sino por Claude Design con `DesignSync` (ADR-21 §1). Detalle y variables de entorno en `entorno-local` §6.

**Por qué.** Cada server responde a una necesidad concreta del trabajo diario —validar el modelo contra datos reales, no inventar APIs ni props—; los que nadie pidió no se agregan.

*Antes: #6.*

## §10 La navegación se dibuja en cuatro diagramas de Archify, un nodo por ruta

**Decisión.** `docs/diagramas/21-navegacion-general`, `22-navegacion-jugador`, `23-navegacion-master` (con sus siete pestañas) y `24-navegacion-admin`, como `.architecture.json` que la skill externa `archify` rinde a HTML. Se versiona el JSON, no el HTML ni las capturas. Cada ruta del router es un nodo; las entradas que vienen de fuera del contexto —un tipo de notificación, un pendiente de la bandeja, `/my/schedule`, el `UserMenu`— son **fichas pegadas a su destino**. La clasificación de F4.1 (sin puerta, entrada única, callejón, cruce de contexto) va en el sublabel y en `meta.views`. `meta.repository.revision` fija el commit del que se leyó la evidencia y cada nodo cita sus archivos en `sources`, verificados con `--repo-root`.

**Por qué.** Un diagrama «vivo» mantenido a mano se quedó atrás sin que nada lo detectara, y un diagrama que miente es peor que ninguno porque se lee como cierto. Con `sources` verificados, una ruta que deja de existir hace **fallar** la entrega. Un nodo por ruta, y no cajas que agrupen pantallas, porque la pregunta que el diagrama responde es cuál pantalla es huérfana, y un huérfano es un nodo sin arista de entrada. Cuatro y no uno porque 38 rutas no pasan la legibilidad de Archify (texto ≥ 6 px a 1440×900); las fichas repetidas permiten dibujar todas las aristas sin cruces.

*Antes: #162, #266, #267.*

## §11 Todo número va con su nombre: `ADR-19 «Arquitectura del frontend»`, no `ADR-19` a secas

**Decisión.** Al hablarle al dueño del proyecto, y en todo texto nuevo de `docs/`, de las skills, de las rules y de los mensajes de commit, cada identificador numerado lleva al lado su nombre descriptivo: una ADR con su título (`ADR-19 «Arquitectura del frontend»`), y con el de la sección cuando se cita una (`ADR-19 «Arquitectura del frontend» §6 «El diálogo es dueño de su formulario y de su mutación»`); una fase o rebanada con el suyo (`F4.1 «El mapa de la interfaz»`); una regla dura con lo que dice (`regla dura 6 «Un espejo por cada respuesta»`); un hallazgo o una decisión de una lista de trabajo con lo que trata (`D3 «Las acciones del master van en un aviso de estado»`). El número no se abandona —sigue siendo lo que se busca con `grep` y lo que identifica sin ambigüedad (§6, §8)—: se le suma el nombre. Lo ya escrito no se reescribe en bloque; se corrige al tocar el archivo. El código no cita ninguno de estos números, ni corto ni con nombre (§12).

**Por qué.** El dueño del proyecto no retiene qué es cada número: `ADR-19 §6`, `F4.1` o `D3` solos no le dicen nada, y obligan a abrir otro archivo para entender una frase. El número es una dirección y el nombre es el contenido; quien lee necesita las dos. El código queda fuera porque su documentación también se lee sin el repo (§12).

## §12 La documentación del código se explica sola: no cita ADRs, skills, rules ni fases

**Decisión.** Ningún comentario de código cita la documentación con la que se trabaja junto al modelo agéntico. Eso incluye el Javadoc, el JSDoc, los comentarios de línea, `@DisplayName` y los nombres de test, los mensajes de log y los comentarios SQL de una migración nueva. Lo que no se cita:

- una ADR (`ADR-07 §1`);
- una sección de skill (`arquitectura §2.3`, `diseno §5.c`);
- una regla dura por número, ni una rule;
- una fase o rebanada (`F4.5`);
- un hallazgo de una lista de trabajo (`B6`, `D3`);
- la numeración vieja (`#n`).

Si el comentario ya explicaba el porqué, la cita se borra. Si la cita era la única explicación, el porqué se escribe en el comentario, en inglés y con palabras propias. Las migraciones Flyway ya aplicadas quedan como están, porque su checksum cubre los comentarios (regla dura 9 «Todo cambio de schema es una migración nueva»).

La trazabilidad va al revés: la ADR, la skill y la rule nombran las clases y archivos que las aplican, y el mensaje del commit cita la decisión.

A 2026-10-24 el código tiene unas 5.400 citas: 3.118 en Java y 2.248 en TypeScript, en unos 830 archivos. Se quitan en un barrido de F4 «Revisión» (`docs/fase-4-revision.md` §2.1). Hasta entonces, el código nuevo o tocado ya no agrega ninguna.

**Por qué.** El Javadoc y el JSDoc se publican y se leen fuera del repo: en un sitio de documentación, o en el IDE de quien hereda el proyecto. Ahí `ADR-07 §1` no lleva a ningún lado, porque es la dirección de un documento que no está. Las ADRs, las skills, las rules y las fases son la forma en que se trabaja con un modelo agéntico, no parte del producto, y el código tiene que poder leerse sin ellas. Además, una cita envejece: se renumera una fase o se funde una skill, y el comentario pasa a apuntar a otra cosa sin que nada falle.

Esto corrige lo que §11 decidía para el código, que conservaba la cita corta porque quien lo mantiene tiene la ADR a un salto. Eso vale para quien trabaja dentro del repo, no para quien lee la documentación publicada.

## §13 Cada tarea de un plan dice con qué modelo se planea y con cuál se ejecuta

**Decisión.** Todo plan, el de plan mode y las listas de trabajo de `docs/`, dice en cada tarea dos modelos: con cuál **se planea** y con cuál **se ejecuta**. Primero se planea cada punto, y con el plan aprobado se ejecuta. El criterio:

- **Fable 5.1** planea lo de mayor complejidad: decisiones que cruzan varias ADRs, el modelo de datos y la seguridad.
- **Opus 5.5** planea lo demás, diagnostica los bugs, escribe las ADRs y tría lo que vuelve de los agentes.
- **Sonnet 5.5** ejecuta lo de volumen que ya no tiene decisiones abiertas: barridos, renombres, mover paquetes, traducciones y ajustes de UI ya decididos.

**La sesión principal es siempre Opus 5.5 y no cambia de modelo**: `/model` no se usa para ejecutar un plan. Lo que el plan asigna a otro modelo se delega a un subagente con ese modelo en el parámetro `model` de la herramienta Agent: lo que «ejecuta Sonnet 5.5», a subagentes con `model: sonnet`; lo que «planea Fable 5.1», a un agente de planificación con `model: fable`. Lo que es de Opus 5.5 lo hace la sesión principal o un subagente que hereda su modelo. **Las tareas independientes se lanzan siempre en paralelo**: varios agentes en un mismo mensaje, uno por feature, por contexto o por bloque sin dependencias entre sí; la que depende de otra espera a que esa vuelva. La columna «Ejecuta» del plan es el pedido que autoriza lanzar ese agente, y no autoriza ningún otro. La sesión principal sigue siendo dueña del resultado, con las reglas de `.claude/rules/ecc/common.md` §«Delegación a subagentes».

Una tarea chica y ya decidida puede planearse y ejecutarse con el mismo modelo, y el plan lo dice; si ese modelo es Opus 5.5, la hace la sesión principal sin delegar.

**Por qué.** No todo es Opus. El costo y la latencia de un modelo grande se justifican al decidir, no al repetir la misma edición en cientos de archivos con la decisión ya tomada. Separar el plan de la ejecución deja corta la parte cara y hace explícito, antes de empezar, qué pide cada tarea. Ejecutar cambiando la sesión de modelo con `/model` la fragmentaba y le hacía perder contexto; con subagentes la sesión no cambia, el costo de Opus queda en decidir e integrar, y el volumen corre en paralelo y más barato.
