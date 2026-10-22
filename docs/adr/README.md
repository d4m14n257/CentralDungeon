# Decisiones de arquitectura (ADR)

> Qué se decidió y **por qué**. Una ADR por tema; cada sección `§k` dice la decisión que rige hoy y su motivo. El *cómo* se escribe el código está en las skills (`arquitectura`, `diseno`, `entorno-local`), que citan la ADR al lado de cada regla.

## Cómo se cita

**`ADR-NN §k`**: la sección `k` de la ADR `NN` (`docs/adr/NN-*.md`). Así citan el código, las skills, las rules y los docs.

Hasta el 2026-10-20 las decisiones eran 292 entradas sueltas, `#1`–`#292` (ADR-01 §8). Se fundieron en estos temas, y **las citas `#n` que quedan en el repo —las de las migraciones Flyway aplicadas, que no se pueden editar (regla dura 9)— y las de los mensajes de commit se resuelven con la [tabla de equivalencias](#equivalencias-de-la-numeración-anterior)** del final. Cada sección cierra además con su línea *Antes* con los números viejos que absorbió.

## Cómo se escribe una decisión nueva

1. **Se ubica su tema.** Si el tema existe, la decisión es una sección más —la siguiente `§k`— o **reescribe la sección que cambia**: una sección dice lo que rige hoy, nunca «antes decía» ni texto tachado. La historia queda en git, y el mensaje del commit dice qué cambió y por qué.
2. Si no hay tema que la contenga, se crea `NN-slug.md` con el siguiente número libre y la fila en el índice de abajo.
3. La sección lleva **Decisión** (concreta: nombres, valores, archivos cuando son la decisión) y **Por qué** (el problema y el motivo de la elección), compactos.
4. **Si la decisión cambia una regla, la regla se actualiza en su skill o su rule en el mismo commit**, citando `ADR-NN §k`.
5. Una sección no se renumera nunca: si deja de regir entera, se borra y su número queda libre, con una línea en la ADR que diga a dónde fue lo que la reemplazó.

```markdown
---
adr: 5
titulo: Seguridad, sesión y login
estado: Vigente
actualizada: 2026-10-20
---

# ADR-05: Seguridad, sesión y login

> Qué cubre, y qué sección de skill aplica estas decisiones como reglas.

## §1 El JWT afirma identidad, no autorización

**Decisión.** …

**Por qué.** …
```

El agente `escriba-adr` (`.claude/agents/`) hace estos pasos.

## Índice

| ADR | Tema | Secciones |
|---|---|---|
| ADR-01 | [Proyecto, proceso y conocimiento](01-proyecto-proceso-y-conocimiento.md) | 10 |
| ADR-02 | [Stack, herramientas y despliegue](02-stack-herramientas-y-despliegue.md) | 6 |
| ADR-03 | [Modelo de datos y persistencia](03-modelo-de-datos-y-persistencia.md) | 10 |
| ADR-04 | [Backend y contrato de la API](04-backend-y-contrato-de-la-api.md) | 7 |
| ADR-05 | [Seguridad, sesión y login](05-seguridad-sesion-y-login.md) | 9 |
| ADR-06 | [Roles y alcance](06-roles-y-alcance.md) | 9 |
| ADR-07 | ["Mesas: ciclo de vida"](07-mesas-ciclo-de-vida.md) | 10 |
| ADR-08 | [Agenda, sesiones y asistencia](08-agenda-sesiones-y-asistencia.md) | 6 |
| ADR-09 | [Postulaciones y veto](09-postulaciones-y-veto.md) | 7 |
| ADR-10 | [Perfiles](10-perfiles.md) | 6 |
| ADR-11 | [Comentarios, karma y feedback](11-comentarios-karma-y-feedback.md) | 12 |
| ADR-12 | [Catálogos](12-catalogos.md) | 9 |
| ADR-13 | ["Archivos: modelo y almacenamiento"](13-archivos-modelo-y-almacenamiento.md) | 8 |
| ADR-14 | ["Archivos: lectura y bibliotecas"](14-archivos-lectura-y-bibliotecas.md) | 8 |
| ADR-15 | [Peticiones de la mesa](15-peticiones-de-la-mesa.md) | 5 |
| ADR-16 | [Pedidos con aprobación y bandeja de admins](16-pedidos-con-aprobacion-y-bandeja.md) | 8 |
| ADR-17 | [Configuración del sistema y auditoría](17-configuracion-y-auditoria.md) | 4 |
| ADR-18 | [Notificaciones y tiempo real](18-notificaciones-y-tiempo-real.md) | 5 |
| ADR-19 | [Arquitectura del frontend](19-arquitectura-del-frontend.md) | 10 |
| ADR-20 | [Navegación, contextos y ayuda](20-navegacion-contextos-y-ayuda.md) | 6 |
| ADR-21 | [Diseño visual e interacción](21-diseno-visual-e-interaccion.md) | 13 |
| ADR-22 | [Buscadores](22-buscadores.md) | 5 |
| ADR-23 | [Textos e idiomas](23-textos-e-idiomas.md) | 4 |
| ADR-24 | [Testing](24-testing.md) | 3 |

## Contexto

CentralDungeon estuvo detenido ~2 años (último commit del código viejo: 2024-08-15). Al retomarse se decidió reescribirlo: **backend Express/TypeScript → Java/Spring Boot**, **frontend Next.js/MUI → React puro (Vite + shadcn/ui + Tailwind)**.

Existió un intento previo de migración a Java en un repo aparte, `CentralDungeonBackend`. No se usa como base: no compilaba, tenía un bean sin registrar y sus repositorios usaban `JdbcTemplate` de forma incorrecta (no podían mapear POJOs multi-columna, pasaban identificadores SQL como bind params). Con 2 commits y ~3 endpoints reales, repararlo costaba lo mismo que reescribir. Se deprecará cuando esta migración esté completa.

Las dos secciones de razonamiento que estaban acá se borraron en F4.0: las dos cosas están **construidas** (F1), y lo que decían vive donde se aplica. Las reglas, en la skill `arquitectura` §4.5; los estados, en [`diagramas/05-ciclo-mesa.mmd`](../diagramas/05-ciclo-mesa.mmd) y [`diagramas/06-ciclo-solicitud.mmd`](../diagramas/06-ciclo-solicitud.mmd); las peticiones y sus archivos, en [`diagramas/14-objetivo-peticiones-archivos.mmd`](../diagramas/14-objetivo-peticiones-archivos.mmd). Las decisiones que las produjeron son ADR-07 §1 y ADR-15 §1. El texto original queda en git. El razonamiento del **karma**, que todavía no está construido (F5), vive en [ADR-11 §8](11-comentarios-karma-y-feedback.md).

## Abierto

Lo que sigue sin resolverse: cuando se cierra, se vuelve una sección de su ADR y sale de esta tabla. **Dos entradas se retiraron de acá por estar respondidas**: el *diseño de Campañas y Temporadas*, que cerró ADR-07 §10 y solo espera la migración de fase 2, y la *secuenciación de entrega*, que `plan-desarrollo.md` fijó primero en E1–E6 desde ADR-01 §3 en fases por actor y desde ADR-01 §3 en **F1–F6**, con la revisión en F4 y comentarios y karma en F5.

| Tema | Estado |
|---|---|
| Jerarquía de catálogos | Las columnas `parent_id` se conservan, pero la navegación de árbol nunca se terminó en Node y no es requisito de v1. |
| **Integración profunda con Discord** | **Planeada, no aprobada por el cliente. Fuera de v1.** Se anota ahora para que las decisiones de hoy no la bloqueen. Alcance previsto: desde la página, redirigir al canal de Discord de tu mesa y unirte directamente; y manejo de canales desde el sistema — abrir el canal cuando la mesa pasa a `Opened` o `InProgress` y cerrarlo cuando pasa a `Finished` o `Canceled`. Implica un bot con permisos sobre el servidor (no solo OAuth2 de login, que es lo único que hay en v1) y una columna de referencia al canal en `game_tables`. **No se agrega nada al modelo por ahora**; cuando se apruebe, es una migración aditiva. |
| **¿«Conservarlo para reusar» protege un archivo de la purga?** | **Discrepancia encontrada en ADR-21 §5, sin resolver.** El diálogo de editar de `/my/files` dice que un archivo sin marcar «se libera cuando pase mucho tiempo sin uso», lo que promete que uno marcado (`Private`) se queda. Pero la purga (`StoredFileRepository.findPurgeCandidates`, ADR-13 §3) exceptúa **solo** a los `Public`: un `Private` sin vínculo vivo con una mesa y sin uso en 90 días se da de baja igual que un `Single-use`. Hay que decidir cuál de los dos tiene razón: si conservar protege, la consulta tiene que exceptuar también a los `Private`; si no, el texto del diálogo tiene que dejar de prometerlo. **Y una segunda discrepancia en la misma consulta**: ADR-09 §2 afirma que el job de retención «hereda» el filtro de postulación viva de los usos, pero `findPurgeCandidates` solo mira `table_files` — ni una postulación viva, ni una entrega, ni un formulario adjunto a un pedido (`task_files`) protegen hoy a un archivo de la purga. |
| **El estado `Confirmed` de `comment_drafts`** | **Postergado a F5.** El DDL declara `Draft | Confirmed | Expired`, pero ADR-11 §6 dice que al confirmar el borrador **desaparece**: si desaparece, `Confirmed` no lo tiene ninguna fila viva; si se queda marcado, la autoría sobrevive a la confirmación, que es justo lo que §6 prohíbe. Se decide al construir `CommentService`. |
| **Desde qué estado publica el job de Temporadas** | **Postergado a fase 2.** El job de `publish_at` pasaría `Preparation → Opened`, pero `Preparation` es «espera a un admin»: una mesa aprobada con salida agendada necesita su propio estado o el job publicaría mesas sin revisar (`arquitectura` §4.7.1, punto 2). Se decide con la migración de Campañas y Temporadas (ADR-07 §10). |
| Generar los tipos del frontend desde OpenAPI | Candidato, no adoptado. springdoc ya publica el schema, así que los espejos de `features/<dominio>/types.ts` podrían generarse en vez de escribirse a mano y quedarían imposibilitados de divergir del backend. Cuesta un paso de build y ata el frontend a que el backend esté levantado o a un artefacto versionado. Se evalúa cuando el contrato de la API esté estable, no antes. Hasta entonces rige skill `arquitectura` §3.2 con tipos escritos a mano. |

## Qué se rescata de `CentralDungeonBackend`

Revisado a fondo en agosto de 2026, con el repo en `/Users/d4m14n257/Personal/CentralDungeonBackend`. Como referencia de forma, nunca copiando código literal:

- **La forma del `JwtAuthorizationFilter`**: token con el `id` como único claim y `loadUserById` resolviendo autoridades en cada request (ADR-05 §3). Es lo mejor que tiene el repo. Se corrigen tres cosas: el `chain.doFilter()` está **dentro del `try`**, así que cualquier excepción de un controller río abajo se reporta como `401`; `validateToken` lanza en vez de devolver `false`, lo que vuelve engañoso el `&&` que lo acompaña; y no hay refresh token (ADR-05 §2).
- **La forma de los `@ConfigurationProperties` como records** (`DiscordConfig`, `JwtConfig`, `HostOriginConfig`).
- **Tomar la identidad de `@AuthenticationPrincipal`** y no de la URL: ese error central del Node ya estaba corregido ahí.
- El patrón `@RestControllerAdvice` para manejo centralizado de errores.

**Se descarta**, con motivo:

| Qué | Por qué |
|---|---|
| `Routes.java` + seguridad por lista de URLs | Cinco de seis rutas sin barra inicial: no protegían nada (ADR-05 §4) |
| Interfaces `*IController` | El contrato lo publica OpenAPI y lo tipan los records (ADR-04 §2) |
| `ResponseData<T>` | Duplica el status y ya se contradecía (ADR-04 §3) |
| Repositorios sobre `JdbcTemplate` | Parámetros corridos, mapeo imposible, SQL que no parsea (ADR-03 §2) |
| `CustomIdGenerator` | Correcto en sí mismo (`SecureRandom` + Base64, 72 bits), pero **ADR-03 §4 ya fijó UUID v7**, que tiene 74 bits aleatorios y además da localidad de índice en InnoDB |
| Paquete por capa (`controllers/`, `services/`, `repositories/`) | El proyecto nuevo es paquete por feature (skill `arquitectura` §2.1) |
| Los controllers stub | `TablesController.getJoinedTables` tiene un `List<>` suelto: el repo no compila |

## Pendientes del modelo de datos (M1–M32)

Los pendientes que se abrieron al revisar el modelo heredado, en agosto de 2026, y la decisión que cerró cada uno. **Están todos cerrados.** Los cuatro que se resolvieron «en parte» dejaron un residual que pasó a otro pendiente y se cerró ahí: M1 → M7, M2 → M11, M4 → M10, M14 → M16; los puntos abiertos de M25 los cerraron M26 y ADR-13 §3.

El razonamiento completo de cada uno —las opciones que se pesaron y por qué se descartaron— se colapsó en F4.0 a este índice: vive en las ADRs que lo cerraron y, palabra por palabra, en el historial de git.

| | Pendiente | Cómo cerró |
|---|---|---|
| M1 | `table_registrations` hace dos trabajos a la vez | Resuelto en parte (ADR-09 §1) |
| M2 | Comentarios sin mesa y karma sin valor numérico | Resuelto en parte (ADR-11 §1, §2) |
| M3 | Los adjuntos no tienen estado propio | Ampliado por ADR-13 §1 y M21–M26 |
| M4 | Horarios sin fin ni excepciones | Resuelto en parte (ADR-08 §3) |
| M5 | Un master puede postularse a su propia mesa | Resuelto (ADR-06 §3, §5) |
| M6 | La identidad depende por completo de Discord | Resuelto (ADR-05 §8, §7) |
| M7 | Con N solicitudes por mesa, ¿cuál es la vigente? | Resuelto (ADR-09 §1) |
| M8 | Alcance del veto (`Blocked`) | Resuelto (ADR-09 §4) |
| M9 | Dónde se guardan las justificaciones de transición | Resuelto (ADR-07 §2) |
| M10 | Sesiones: ¿materializadas o derivadas? | Resuelto (ADR-08 §3) |
| M11 | Alcance del "solo una vez" de los comentarios, y cuánto vale cada uno | Resuelto (ADR-11 §2, ADR-08 §4, ADR-11 §8, §9, §10) |
| M12 | Residuales del veto | Resuelto (ADR-09 §5) |
| M13 | Los "pedidos con aprobación" ya son un patrón, y conviene modelarlos una sola vez | Resuelto (ADR-16 §1, §2) |
| M14 | Residuales de la visibilidad de perfiles | Resuelto casi todo (ADR-11 §5, ADR-10 §2, ADR-06 §8) |
| M15 | El anonimato duro choca con tres decisiones previas | Resuelto (ADR-11 §5) |
| M16 | Comentarios escritos antes de que la mesa se cancele | Resuelto (ADR-11 §3) |
| M17 | Consecuencias del comentario en borrador | Resuelto (ADR-11 §6, §7) |
| M18 | Un borrador con `deleted_at` sigue siendo un registro de autoría | Resuelto (ADR-11 §6) |
| M19 | Cómo modelar los sinónimos de catálogo | Resuelto (ADR-12 §2) |
| M20 | Otros pendientes de catálogos | Resuelto (ADR-12 §3, §4, §5) |
| M21 | Qué encontré en el código de archivos que no estaba en el modelo | Relevamiento: lo cerraron M22–M26 |
| M22 | Texto enriquecido: no está en el modelo | Resuelto (ADR-03 §8, ADR-15 §1) |
| M23 | Requisitos al jugador ya aceptado | Resuelto (ADR-15 §1) |
| M24 | Archivos públicos: quién, dónde y con qué alcance | Resuelto (ADR-13 §2, ADR-14 §1) |
| M25 | Biblioteca privada: alcance, cuota y costo | Resuelto en parte (ADR-13 §2) |
| M26 | Ciclo de vida del archivo físico | Resuelto (ADR-13 §8, §6, §7) |
| M27 | La cuota de ADR-13 §3 choca con la reutilización de ADR-13 §2 | Resuelto (ADR-13 §2, §3) |
| M28 | Mesas creadas por un admin, sin que él sea master | Resuelto (ADR-06 §7, §3) |
| M29 | Cómo renombrar el `Owner` de la mesa | Resuelto (ADR-06 §3) |
| M30 | El sistema no puede distinguir "se fue" de "lo banearon" | Resuelto (ADR-05 §7) |
| M31 | ¿`General` comparte tabla con los comentarios sobre personas? | Resuelto (ADR-11 §11) |
| M32 | ¿La internacionalización entra en v1? | Resuelto (ADR-23 §1) |

## Documentos que ya no existen

La numeración de cada skill se conservó en cada mudanza, así que una cita vieja se resuelve con esta tabla:

| Documento | Dónde está ahora | Cómo se traduce la cita |
|---|---|---|
| `docs/decisiones.md` y `docs/adr/NNNN-*.md` (las 292 decisiones sueltas) | Estas ADRs temáticas | `#n` → la tabla de equivalencias de abajo |
| `docs/arquitectura.md` | Skill `arquitectura` (ADR-01 §5) | §1 → §1; §5 → §1.4; §6 → §8. Sus antiguos §2 y §3 ya eran de la skill desde F4.0, con §2.7 → §5.1, §3.4 → §5.2 y §2.8 → §6.1 |
| `docs/modelo-datos.md` | Skill `arquitectura` §4 | §n → §4.n |
| `docs/frontend-diseno.md` | Skill `diseno` | §n → `diseno` §n |
| `docs/mcp-y-skills.md` | Skill `entorno-local` §6–§8; skills, rules y agentes, en `CLAUDE.md` | — |
| Las skills `arquitectura-backend`, `arquitectura-frontend`, `modelo-datos`, `tests-java`, `nuevo-endpoint-java`, `nuevo-componente-react`, `er-diagram-sync` | Skill `arquitectura` (ADR-01 §5) | La tabla de la cabecera de su `SKILL.md` |
| `docs/fase-1-*` a `docs/fase-3-*` | Borrados en F4.0; lo normativo, en `arquitectura`, `references/modelo-datos/roles-y-alcance.md` | — |

## Equivalencias de la numeración anterior

| # | Ahora | # | Ahora | # | Ahora | # | Ahora |
|---|---|---|---|---|---|---|---|
| #1 | ADR-01 §1 | #2 | ADR-03 §2 | #3 | ADR-03 §1 | #4 | ADR-13 §1 |
| #5 | ADR-02 §2 | #6 | ADR-01 §9 | #7 | ADR-07 §10 | #8 | ADR-03 §5 |
| #9 | ADR-03 §4 | #10 | ADR-03 §5 | #11 | ADR-03 §1 | #12 | ADR-03 §3 |
| #13 | ADR-04 §1 | #14 | ADR-18 §1 | #15 | ADR-13 §6 | #16 | ADR-06 §1 |
| #17 | ADR-11 §1 | #18 | ADR-01 §1 | #19 | ADR-02 §1 | #20 | ADR-02 §3 |
| #21 | ADR-04 §2 | #22 | ADR-03 §6 | #23 | ADR-09 §1 | #24 | ADR-07 §5 |
| #25 | ADR-03 §7 | #26 | ADR-08 §3 | #27 | ADR-07 §1 | #28 | ADR-09 §1 |
| #29 | ADR-09 §4 | #30 | ADR-11 §1 | #31 | ADR-11 §2 | #32 | ADR-07 §2 |
| #33 | ADR-08 §3 | #34 | ADR-09 §3 | #35 | ADR-11 §2 | #36 | ADR-08 §4 |
| #37 | ADR-06 §1 | #38 | ADR-05 §1 | #39 | ADR-09 §5 | #40 | ADR-11 §3 |
| #41 | ADR-10 §1 | #42 | ADR-16 §1 | #43 | ADR-11 §5 | #44 | ADR-10 §2 |
| #45 | ADR-06 §8 | #46 | ADR-11 §4 | #47 | ADR-10 §1 | #48 | ADR-11 §3 |
| #49 | ADR-11 §6 | #50 | ADR-11 §6 | #51 | ADR-11 §7 | #52 | ADR-11 §6 |
| #53 | ADR-12 §1 | #54 | ADR-12 §2 | #55 | ADR-12 §3 | #56 | ADR-12 §4 |
| #57 | ADR-12 §3 | #58 | ADR-12 §4 | #59 | ADR-12 §2 | #60 | ADR-13 §1 |
| #61 | ADR-13 §3 | #62 | ADR-03 §8 | #63 | ADR-15 §1 | #64 | ADR-13 §2 |
| #65 | ADR-13 §2 | #66 | ADR-13 §8 | #67 | ADR-06 §1 | #68 | ADR-13 §2 |
| #69 | ADR-09 §2 | #70 | ADR-15 §2 | #71 | ADR-06 §3 | #72 | ADR-06 §7 |
| #73 | ADR-06 §3 | #74 | ADR-11 §1 | #75 | ADR-13 §3 | #76 | ADR-15 §2 |
| #77 | ADR-15 §5 | #78 | ADR-16 §2 | #79 | ADR-14 §1 | #80 | ADR-13 §6 |
| #81 | ADR-12 §5 | #82 | ADR-11 §5 | #83 | ADR-05 §8 | #84 | ADR-05 §7 |
| #85 | ADR-05 §8 | #86 | ADR-05 §7 | #87 | ADR-15 §1 | #88 | ADR-05 §7 |
| #89 | ADR-06 §1 | #90 | ADR-16 §1 | #91 | ADR-11 §11 | #92 | ADR-17 §1 |
| #93 | ADR-11 §11 | #94 | ADR-11 §12 | #95 | ADR-11 §11 | #96 | ADR-11 §8 |
| #97 | ADR-11 §9 | #98 | ADR-11 §10 | #99 | ADR-11 §10 | #100 | ADR-16 §5 |
| #101 | ADR-18 §2 | #102 | ADR-01 §2 | #103 | ADR-20 §1 | #104 | ADR-19 §3 |
| #105 | ADR-19 §5 | #106 | ADR-19 §6 | #107 | ADR-23 §1 | #108 | ADR-19 §3 |
| #109 | ADR-21 §1 | #110 | ADR-19 §6 | #111 | ADR-19 §7 | #112 | ADR-19 §1 |
| #113 | ADR-19 §2 | #114 | ADR-19 §1 | #115 | ADR-19 §4 | #116 | ADR-18 §2 |
| #117 | ADR-23 §1 | #118 | ADR-21 §1 | #119 | ADR-04 §2 | #120 | ADR-04 §3 |
| #121 | ADR-05 §5 | #122 | ADR-05 §3 | #123 | ADR-05 §4 | #124 | ADR-03 §2 |
| #125 | ADR-05 §2 | #126 | ADR-16 §2 | #127 | ADR-05 §2 | #128 | ADR-05 §3 |
| #129 | ADR-07 §10 | #130 | ADR-21 §1 | #131 | ADR-21 §2 | #132 | ADR-21 §2 |
| #133 | ADR-19 §8 | #134 | ADR-10 §4 | #135 | ADR-06 §4 | #136 | ADR-20 §4 |
| #137 | ADR-08 §5 | #138 | ADR-21 §7 | #139 | ADR-21 §8 | #140 | ADR-06 §9 |
| #141 | ADR-17 §3 | #142 | ADR-02 §5 | #143 | ADR-24 §1 | #144 | ADR-21 §3 |
| #145 | ADR-21 §3 | #146 | ADR-02 §6 | #147 | ADR-05 §9 | #148 | ADR-05 §9 |
| #149 | ADR-09 §7 | #150 | ADR-19 §4 | #151 | ADR-06 §6 | #152 | ADR-06 §6 |
| #153 | ADR-18 §3 | #154 | ADR-18 §4 | #155 | ADR-06 §5 | #156 | ADR-18 §5 |
| #157 | ADR-18 §4 | #158 | ADR-19 §9 | #159 | ADR-06 §4 | #160 | ADR-09 §7 |
| #161 | ADR-19 §9 | #162 | ADR-01 §10 | #163 | ADR-07 §1 | #164 | ADR-22 §1 |
| #165 | ADR-07 §8 | #166 | ADR-06 §3 | #167 | ADR-20 §5 | #168 | ADR-20 §6 |
| #169 | ADR-06 §1 | #170 | ADR-20 §6 | #171 | ADR-07 §9 | #172 | ADR-24 §3 |
| #173 | ADR-04 §6 | #174 | ADR-02 §4 | #175 | ADR-07 §6 | #176 | ADR-16 §7 |
| #177 | ADR-01 §3 | #178 | ADR-08 §2 | #179 | ADR-12 §3 | #180 | ADR-07 §7 |
| #181 | ADR-01 §4 | #182 | ADR-03 §9 | #183 | ADR-12 §5 | #184 | ADR-12 §6 |
| #185 | ADR-12 §7 | #186 | ADR-05 §6 | #187 | ADR-04 §4 | #188 | ADR-04 §4 |
| #189 | ADR-04 §7 | #190 | ADR-04 §7 | #191 | ADR-08 §1 | #192 | ADR-19 §7 |
| #193 | ADR-08 §2 | #194 | ADR-08 §3 | #195 | ADR-08 §4 | #196 | ADR-07 §3 |
| #197 | ADR-23 §3 | #198 | ADR-23 §2 | #199 | ADR-13 §6 | #200 | ADR-13 §7 |
| #201 | ADR-13 §4 | #202 | ADR-13 §7 | #203 | ADR-13 §5 | #204 | ADR-13 §2 |
| #205 | ADR-14 §4 | #206 | ADR-14 §2 | #207 | ADR-14 §7 | #208 | ADR-03 §10 |
| #209 | ADR-15 §4 | #210 | ADR-15 §2 | #211 | ADR-14 §2 | #212 | ADR-15 §5 |
| #213 | ADR-09 §6 | #214 | ADR-21 §13 | #215 | ADR-21 §13 | #216 | ADR-07 §8 |
| #217 | ADR-20 §4 | #218 | ADR-21 §12 | #219 | ADR-20 §4 | #220 | ADR-20 §3 |
| #221 | ADR-22 §5 | #222 | ADR-20 §2 | #223 | ADR-24 §2 | #224 | ADR-21 §4 |
| #225 | ADR-23 §4 | #226 | ADR-07 §3 | #227 | ADR-08 §6 | #228 | ADR-08 §1 |
| #229 | ADR-07 §3 | #230 | ADR-07 §4 | #231 | ADR-20 §5 | #232 | ADR-14 §3 |
| #233 | ADR-14 §4 | #234 | ADR-13 §4 | #235 | ADR-13 §7 | #236 | ADR-15 §3 |
| #237 | ADR-14 §5 | #238 | ADR-13 §5 | #239 | ADR-22 §2 | #240 | ADR-22 §3 |
| #241 | ADR-14 §5 | #242 | ADR-14 §6 | #243 | ADR-19 §1 | #244 | ADR-18 §3 |
| #245 | ADR-07 §1 | #246 | ADR-12 §8 | #247 | ADR-09 §2 | #248 | ADR-10 §5 |
| #249 | ADR-10 §3 | #250 | ADR-01 §3 | #251 | ADR-17 §2 | #252 | ADR-06 §2 |
| #253 | ADR-04 §5 | #254 | ADR-24 §3 | #255 | ADR-16 §1 | #256 | ADR-16 §4 |
| #257 | ADR-16 §3 | #258 | ADR-16 §6 | #259 | ADR-16 §5 | #260 | ADR-16 §8 |
| #261 | ADR-21 §6 | #262 | ADR-02 §5 | #263 | ADR-17 §3 | #264 | ADR-17 §4 |
| #265 | ADR-07 §5 | #266 | ADR-01 §10 | #267 | ADR-01 §10 | #268 | ADR-22 §4 |
| #269 | ADR-20 §2 | #270 | ADR-20 §3 | #271 | ADR-21 §11 | #272 | ADR-21 §11 |
| #273 | ADR-21 §5 | #274 | ADR-01 §5 | #275 | ADR-12 §7 | #276 | ADR-12 §6 |
| #277 | ADR-21 §7 | #278 | ADR-14 §7 | #279 | ADR-14 §8 | #280 | ADR-21 §5 |
| #281 | ADR-21 §5 | #282 | ADR-14 §7 | #283 | ADR-21 §9 | #284 | ADR-06 §8 |
| #285 | ADR-10 §6 | #286 | ADR-21 §10 | #287 | ADR-21 §10 | #288 | ADR-21 §10 |
| #289 | ADR-01 §8 | #290 | ADR-01 §6 | #291 | ADR-01 §7 | #292 | ADR-01 §5 |
