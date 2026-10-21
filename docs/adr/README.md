# Decisiones de arquitectura (ADR)

> Registro de qué se decidió y **por qué**: el lugar al que se vuelve cuando alguien —incluido el autor dentro de seis meses— se pregunta por qué algo está hecho así. **Una decisión, un archivo.** El *qué* concreto vive en las skills (`arquitectura`, `diseno`, `entorno-local`), que citan la decisión al lado de cada regla.

Hasta #289 todo esto era una sola tabla, `docs/decisiones.md`; su historia completa sigue en git.

## Cómo se cita

**`#n` es la ADR número n**, y vive en `docs/adr/` en el archivo que empieza con `n` a cuatro dígitos (`#124` → `0124-…md`). Al migrar se conservaron los números a propósito: el código, las skills y los docs citan `#n` miles de veces, y todas esas citas siguen siendo válidas sin tocarlas.

## Cómo se escribe una nueva

1. Número: el siguiente libre del índice. **Nunca se reusa ni se renumera.**
2. Archivo `NNNN-slug-en-espanol.md`, con esta forma:

   ```markdown
   ---
   adr: 289
   estado: Aceptada          # Propuesta | Aceptada | Reemplazada
   fecha: 2026-10-20
   modifica: [123]           # solo si corrige o precisa otra
   ---

   # ADR-0289: La decisión, dicha como una afirmación

   > Corrige #123 (opcional: la relación con otras, en una línea)

   ## Decisión

   Qué se resolvió, concreto: nombres, archivos, valores.

   ## Por qué

   El problema, las opciones que se pesaron y el motivo de la elegida.
   ```

3. **La que se corrige también se toca**: gana `modificada-por: [n]` en su frontmatter, o `estado: Reemplazada` + `reemplazada-por: [n]` si la nueva la deja entera sin efecto. Su cuerpo no se reescribe —es el registro de lo que se pensó entonces—, a lo sumo lleva una línea que remite a la nueva.
4. Se agrega la fila al índice de abajo y, si la decisión cambia una regla, la regla se actualiza en su skill **en el mismo commit**.

El agente `escriba-adr` (`.claude/agents/`) hace estos cuatro pasos.

## Contexto

CentralDungeon estuvo detenido ~2 años (último commit del código viejo: 2024-08-15). Al retomarse se decidió reescribirlo: **backend Express/TypeScript → Java/Spring Boot**, **frontend Next.js/MUI → React puro (Vite + shadcn/ui + Tailwind)**.

Existió un intento previo de migración a Java en un repo aparte, `CentralDungeonBackend`. No se usa como base: no compilaba, tenía un bean sin registrar y sus repositorios usaban `JdbcTemplate` de forma incorrecta (no podían mapear POJOs multi-columna, pasaban identificadores SQL como bind params). Con 2 commits y ~3 endpoints reales, repararlo costaba lo mismo que reescribir. Se deprecará cuando esta migración esté completa.

Las dos secciones de razonamiento que estaban acá se borraron en F4.0: las dos cosas están **construidas** (F1), y lo que decían vive donde se aplica. Las reglas, en la skill `arquitectura` §4.5; los estados, en [`diagramas/05-ciclo-mesa.mmd`](../diagramas/05-ciclo-mesa.mmd) y [`diagramas/06-ciclo-solicitud.mmd`](../diagramas/06-ciclo-solicitud.mmd); las peticiones y sus archivos, en [`diagramas/14-objetivo-peticiones-archivos.mmd`](../diagramas/14-objetivo-peticiones-archivos.mmd). Las decisiones que las produjeron son #27 y #63. El texto original queda en git. El razonamiento del **karma**, que todavía no está construido (F5), vive en la [ADR-0096](0096-parametros-del-karma.md).

## Documentos que ya no existen

Las ADRs no se reescriben, así que muchas nombran documentos que después se disolvieron en las skills. **La numeración se conservó en cada mudanza**, así que una cita vieja se resuelve con esta tabla:

| Documento | Dónde está ahora | Cómo se traduce la cita |
|---|---|---|
| `docs/decisiones.md` | `docs/adr/` (#289) | `#n` es la ADR n |
| `docs/arquitectura.md` | Skill `arquitectura` (#292) | §1 → §1; §5 → §1.4; §6 → §8. Sus antiguos §2 y §3 ya eran de la skill desde F4.0, con §2.7 → §5.1, §3.4 → §5.2 y §2.8 → §6.1 |
| `docs/modelo-datos.md` | Skill `arquitectura` §4 (F4.0 y #292) | §n → §4.n |
| `docs/frontend-diseno.md` | Skill `diseno` (#273 y #292) | §n → `diseno` §n |
| `docs/mcp-y-skills.md` | Skill `entorno-local` §6–§8 (#292); skills, rules y agentes, en `CLAUDE.md` | — |
| Las skills `arquitectura-backend`, `arquitectura-frontend`, `modelo-datos`, `tests-java`, `nuevo-endpoint-java`, `nuevo-componente-react`, `er-diagram-sync` | Skill `arquitectura` (#274) | La tabla de la cabecera de su `SKILL.md` |
| `docs/fase-1-*` a `docs/fase-3-*` | Borrados en F4.0; lo normativo, en `arquitectura`, `references/modelo-datos/roles-y-alcance.md` | — |

## Índice

| # | Decisión | Estado |
|---|---|---|
| 1 | [Un repo o dos](0001-un-repo-o-dos.md) | Aceptada |
| 2 | [Acceso a datos en Java](0002-acceso-a-datos-en-java.md) | Aceptada |
| 3 | [Lógica que vivía en triggers de MySQL](0003-logica-que-vivia-en-triggers-de-mysql.md) | Aceptada |
| 4 | [Personajes de jugador](0004-personajes-de-jugador.md) | Aceptada |
| 5 | [UI library del frontend](0005-ui-library-del-frontend.md) | Aceptada |
| 6 | [MCP servers](0006-mcp-servers.md) | Aceptada |
| 7 | [Campañas y Temporadas](0007-campanas-y-temporadas.md) | Aceptada |
| 8 | [Tables.tabletype (Public / First class)](0008-tables-tabletype-public-first-class.md) | Aceptada |
| 9 | [Generación de IDs](0009-generacion-de-ids.md) | Aceptada |
| 10 | [Tipo ENUM de MySQL](0010-tipo-enum-de-mysql.md) | Aceptada |
| 11 | [Contador de jugadores por mesa](0011-contador-de-jugadores-por-mesa.md) | Aceptada |
| 12 | [Migraciones de schema](0012-migraciones-de-schema.md) | Aceptada |
| 13 | [Organización del código](0013-organizacion-del-codigo.md) | Aceptada |
| 14 | [Notificaciones](0014-notificaciones.md) | Aceptada |
| 15 | [Almacenamiento de archivos](0015-almacenamiento-de-archivos.md) | Aceptada |
| 16 | [Roles y permisos](0016-roles-y-permisos.md) | Aceptada |
| 17 | [Karma y comentarios](0017-karma-y-comentarios.md) | Aceptada |
| 18 | [Código viejo](0018-codigo-viejo.md) | Aceptada |
| 19 | [Versiones del stack](0019-versiones-del-stack.md) | Aceptada |
| 20 | [TypeScript en el frontend nuevo](0020-typescript-en-el-frontend-nuevo.md) | Aceptada |
| 21 | [Compartir código entre features (backend)](0021-compartir-codigo-entre-features-backend.md) | Aceptada |
| 22 | [Zona horaria](0022-zona-horaria.md) | Aceptada |
| 23 | [Postulaciones repetidas a la misma mesa](0023-postulaciones-repetidas-a-la-misma-mesa.md) | Aceptada |
| 24 | [Cupo máximo de jugadores](0024-cupo-maximo-de-jugadores.md) | Aceptada |
| 25 | [Borrado lógico](0025-borrado-logico.md) | Aceptada |
| 26 | [Duración de la mesa](0026-duracion-de-la-mesa.md) | Aceptada |
| 27 | [Ciclo de vida de la mesa](0027-ciclo-de-vida-de-la-mesa.md) | Aceptada |
| 28 | [Solicitud vigente y orden de atención](0028-solicitud-vigente-y-orden-de-atencion.md) | Aceptada |
| 29 | [Alcance del veto (Blocked)](0029-alcance-del-veto-blocked.md) | Aceptada |
| 30 | [Karma](0030-karma.md) | Aceptada |
| 31 | [Comentarios](0031-comentarios.md) | Aceptada |
| 32 | [Justificaciones de moderación](0032-justificaciones-de-moderacion.md) | Aceptada |
| 33 | [Sesiones](0033-sesiones.md) | Aceptada |
| 34 | [Qué pasa al llenarse el cupo](0034-que-pasa-al-llenarse-el-cupo.md) | Aceptada |
| 35 | [Frecuencia de los comentarios](0035-frecuencia-de-los-comentarios.md) | Aceptada |
| 36 | [Asistencia](0036-asistencia.md) | Aceptada |
| 37 | [Modelo de roles](0037-modelo-de-roles.md) | Aceptada |
| 38 | [Login](0038-login.md) | Aceptada |
| 39 | [Quién puede vetar, y si se deshace](0039-quien-puede-vetar-y-si-se-deshace.md) | Aceptada |
| 40 | [Cuándo se comenta](0040-cuando-se-comenta.md) | Aceptada |
| 41 | [Visibilidad de perfiles y karma](0041-visibilidad-de-perfiles-y-karma.md) | Aceptada |
| 42 | [Pedidos con aprobación](0042-pedidos-con-aprobacion.md) | Aceptada |
| 43 | [Anonimato de los comentarios](0043-anonimato-de-los-comentarios.md) | Aceptada |
| 44 | [Caducidad de la visibilidad de perfiles](0044-caducidad-de-la-visibilidad-de-perfiles.md) | Aceptada |
| 45 | [Alcance del admin](0045-alcance-del-admin.md) | Aceptada |
| 46 | [Comentarios en mesas canceladas](0046-comentarios-en-mesas-canceladas.md) | Aceptada |
| 47 | [Visibilidad entre jugadores](0047-visibilidad-entre-jugadores.md) | Aceptada |
| 48 | [Cuándo se confirma un comentario](0048-cuando-se-confirma-un-comentario.md) | Aceptada |
| 49 | [Dónde vive el autor de un borrador](0049-donde-vive-el-autor-de-un-borrador.md) | Aceptada |
| 50 | [Borrador nunca confirmado](0050-borrador-nunca-confirmado.md) | Aceptada |
| 51 | [Moderación de comentarios](0051-moderacion-de-comentarios.md) | Aceptada |
| 52 | [Borrado de borradores](0052-borrado-de-borradores.md) | Aceptada |
| 53 | [Qué es cada catálogo, y qué significa parentid](0053-que-es-cada-catalogo-y-que-significa.md) | Aceptada |
| 54 | [Semántica de la equivalencia](0054-semantica-de-la-equivalencia.md) | Aceptada |
| 55 | [Alta y clasificación de catálogos](0055-alta-y-clasificacion-de-catalogos.md) | Aceptada |
| 56 | [Qué se guarda al etiquetar](0056-que-se-guarda-al-etiquetar.md) | Aceptada |
| 57 | [Tag propuesto y todavía sin aprobar](0057-tag-propuesto-y-todavia-sin-aprobar.md) | Aceptada |
| 58 | [Qué nombre se muestra](0058-que-nombre-se-muestra.md) | Aceptada |
| 59 | [Modelo de los sinónimos de catálogo](0059-modelo-de-los-sinonimos-de-catalogo.md) | Aceptada |
| 60 | [Los cuatro usos de los archivos](0060-los-cuatro-usos-de-los-archivos.md) | Aceptada |
| 61 | [Cuota de la biblioteca privada DEROGADA por #75](0061-cuota-de-la-biblioteca-privada-derogada-por.md) | Aceptada |
| 62 | [Texto enriquecido](0062-texto-enriquecido.md) | Aceptada |
| 63 | [Peticiones de la mesa](0063-peticiones-de-la-mesa.md) | Aceptada |
| 64 | [Clasificación de los archivos públicos](0064-clasificacion-de-los-archivos-publicos.md) | Aceptada |
| 65 | [Reutilización de archivos](0065-reutilizacion-de-archivos.md) | Aceptada |
| 66 | [Borrado físico de archivos](0066-borrado-fisico-de-archivos.md) | Aceptada |
| 67 | [Roles de la plataforma](0067-roles-de-la-plataforma.md) | Aceptada |
| 68 | [Clasificación de archivos](0068-clasificacion-de-archivos.md) | Aceptada |
| 69 | [Contenido de la postulación](0069-contenido-de-la-postulacion.md) | Aceptada |
| 70 | [Incumplimiento de una petición](0070-incumplimiento-de-una-peticion.md) | Aceptada |
| 71 | [Nombre del rol de mesa](0071-nombre-del-rol-de-mesa.md) | Aceptada |
| 72 | [Mesas creadas por un admin](0072-mesas-creadas-por-un-admin.md) | Aceptada |
| 73 | [Primario único y postulación de un master](0073-primario-unico-y-postulacion-de-un-master.md) | Aceptada |
| 74 | [Extremos del karma](0074-extremos-del-karma.md) | Aceptada |
| 75 | [Estrategia de costo de almacenamiento](0075-estrategia-de-costo-de-almacenamiento.md) | Aceptada |
| 76 | [Alcance del sistema frente a las entregas](0076-alcance-del-sistema-frente-a-las-entregas.md) | Aceptada |
| 77 | [Notificación de peticiones](0077-notificacion-de-peticiones.md) | Aceptada |
| 78 | [Referencia de la tabla de aprobaciones](0078-referencia-de-la-tabla-de-aprobaciones.md) | Aceptada |
| 79 | [Archivos públicos como requisito de una mesa](0079-archivos-publicos-como-requisito-de-una-mesa.md) | Aceptada |
| 80 | [Nombre del archivo en disco](0080-nombre-del-archivo-en-disco.md) | Aceptada |
| 81 | [Borrado de valores de catálogo](0081-borrado-de-valores-de-catalogo.md) | Aceptada |
| 82 | [createdat en los comentarios](0082-createdat-en-los-comentarios.md) | Aceptada |
| 83 | [Pérdida de la cuenta de Discord](0083-perdida-de-la-cuenta-de-discord.md) | Aceptada |
| 84 | [Salir del servidor de Discord](0084-salir-del-servidor-de-discord.md) | Aceptada |
| 85 | [Cambios en el OAuth de Discord](0085-cambios-en-el-oauth-de-discord.md) | Aceptada |
| 86 | [Detección de baneos de Discord](0086-deteccion-de-baneos-de-discord.md) | Aceptada |
| 87 | [Nombre de las tablas de peticiones](0087-nombre-de-las-tablas-de-peticiones.md) | Aceptada |
| 88 | [Qué de Discord necesita un bot](0088-que-de-discord-necesita-un-bot.md) | Aceptada |
| 89 | [Owner incluye a Admin](0089-owner-incluye-a-admin.md) | Aceptada |
| 90 | [Alcance de las solicitudes](0090-alcance-de-las-solicitudes.md) | Aceptada |
| 91 | [Comentarios General](0091-comentarios-general.md) | Aceptada |
| 92 | [Trazabilidad](0092-trazabilidad.md) | Aceptada |
| 93 | [Feedback del sistema](0093-feedback-del-sistema.md) | Aceptada |
| 94 | [Límite de feedback del sistema](0094-limite-de-feedback-del-sistema.md) | Aceptada |
| 95 | [Destino del feedback del sistema](0095-destino-del-feedback-del-sistema.md) | Aceptada |
| 96 | [Parámetros del karma](0096-parametros-del-karma.md) | Aceptada |
| 97 | [Cuándo se recalcula el karma](0097-cuando-se-recalcula-el-karma.md) | Aceptada |
| 98 | [Asistencia y karma](0098-asistencia-y-karma.md) | Aceptada |
| 99 | [Qué se muestra del karma](0099-que-se-muestra-del-karma.md) | Aceptada |
| 100 | [Bandeja compartida de admins](0100-bandeja-compartida-de-admins.md) | Aceptada |
| 101 | [Transporte de tiempo real](0101-transporte-de-tiempo-real.md) | Aceptada |
| 102 | [Idioma del código](0102-idioma-del-codigo.md) | Aceptada |
| 103 | [Navegación del frontend](0103-navegacion-del-frontend.md) | Aceptada |
| 104 | [Cliente HTTP](0104-cliente-http.md) | Aceptada |
| 105 | [Contexto, store o librería](0105-contexto-store-o-libreria.md) | Aceptada |
| 106 | [Formularios](0106-formularios.md) | Aceptada |
| 107 | [Formato de los textos de idioma](0107-formato-de-los-textos-de-idioma.md) | Aceptada |
| 108 | [Capa de normalización](0108-capa-de-normalizacion.md) | Aceptada |
| 109 | [Estilos](0109-estilos.md) | Aceptada |
| 110 | [Envoltorio de los formularios en modal](0110-envoltorio-de-los-formularios-en-modal.md) | Aceptada |
| 111 | [Fechas y horas](0111-fechas-y-horas.md) | Aceptada |
| 112 | [Organización de carpetas del frontend](0112-organizacion-de-carpetas-del-frontend.md) | Aceptada |
| 113 | [Pantallas que necesitan varios dominios](0113-pantallas-que-necesitan-varios-dominios.md) | Aceptada |
| 114 | [Superficie pública de una feature](0114-superficie-publica-de-una-feature.md) | Aceptada |
| 115 | [loader de React Router](0115-loader-de-react-router.md) | Aceptada |
| 116 | [Que el tiempo real no dispare una tormenta de peticiones](0116-que-el-tiempo-real-no-dispare-una.md) | Aceptada |
| 117 | [Internacionalización — cierra M32](0117-internacionalizacion-cierra-m32.md) | Aceptada |
| 118 | [Fuente de verdad de los tokens de diseño — la herramienta cambia en #130](0118-fuente-de-verdad-de-los-tokens-de.md) | Aceptada |
| 119 | [Interfaz por controller para declarar el contrato](0119-interfaz-por-controller-para-declarar-el-contrato.md) | Aceptada |
| 120 | [Forma de la respuesta exitosa](0120-forma-de-la-respuesta-exitosa.md) | Aceptada |
| 121 | [Escalado horizontal — rol ≠ pertenencia](0121-escalado-horizontal-rol-pertenencia.md) | Aceptada |
| 122 | [Qué afirma el JWT](0122-que-afirma-el-jwt.md) | Aceptada |
| 123 | [Dónde se declara la autorización](0123-donde-se-declara-la-autorizacion.md) | Aceptada |
| 124 | [Acceso a datos y consultas](0124-acceso-a-datos-y-consultas.md) | Aceptada |
| 125 | [Los tres tokens](0125-los-tres-tokens.md) | Aceptada |
| 126 | [Cómo referencia approvalrequests a la entidad afectada — cierra M13](0126-como-referencia-approvalrequests-a-la-entidad-afectada.md) | Aceptada |
| 127 | [CSRF en el endpoint de refresh](0127-csrf-en-el-endpoint-de-refresh.md) | Aceptada |
| 128 | [Caché del usuario que resuelve la autorización](0128-cache-del-usuario-que-resuelve-la-autorizacion.md) | Aceptada |
| 129 | [Diseño de Campañas y Temporadas — precisa y corrige #7](0129-diseno-de-campanas-y-temporadas-precisa-y.md) | Aceptada |
| 130 | [Herramienta de diseño — reemplaza la parte de #118 que nombraba a Figma](0130-herramienta-de-diseno-reemplaza-la-parte-de.md) | Aceptada |
| 131 | [Dirección visual](0131-direccion-visual.md) | Aceptada |
| 132 | [Paleta de marca — corrige #131](0132-paleta-de-marca-corrige-131.md) | Aceptada |
| 133 | [Faltantes del contexto Jugador — corrige #111](0133-faltantes-del-contexto-jugador-corrige-111.md) | Aceptada |
| 134 | [Datos propios del usuario — onboarding en vez de pantalla de configuración](0134-datos-propios-del-usuario-onboarding-en-vez.md) | Aceptada |
| 135 | [Master de una sola mesa, sin el rol de plataforma](0135-master-de-una-sola-mesa-sin-el.md) | Aceptada |
| 136 | [Dashboard del master](0136-dashboard-del-master.md) | Aceptada |
| 137 | [Asistencia histórica: qué es y cómo se calcula](0137-asistencia-historica-que-es-y-como-se.md) | Aceptada |
| 138 | [Alcance del responsive](0138-alcance-del-responsive.md) | Aceptada |
| 139 | [Cómo se especifican los cuatro estados obligatorios](0139-como-se-especifican-los-cuatro-estados-obligatorios.md) | Aceptada |
| 140 | ["Ver como" — suplantación asistida](0140-ver-como-suplantacion-asistida.md) | Aceptada |
| 141 | [Configuración del sistema](0141-configuracion-del-sistema.md) | Aceptada |
| 142 | [Gestión de configuración y despliegue](0142-gestion-de-configuracion-y-despliegue.md) | Aceptada |
| 143 | [Cómo se prueba el login de Discord sin Discord](0143-como-se-prueba-el-login-de-discord.md) | Aceptada |
| 144 | [Bajar el shell al design system: fuentes, acento como texto y el toggle de tema](0144-bajar-el-shell-al-design-system-fuentes.md) | Aceptada |
| 145 | [Detalle de mesa contra el design system, y matiz a #103 sobre el chip de contexto](0145-detalle-de-mesa-contra-el-design-system.md) | Aceptada |
| 146 | [Health check público y conectividad en el frontend](0146-health-check-publico-y-conectividad-en-el.md) | Aceptada |
| 147 | [Quinto estado del retorno de OAuth: cuenta bloqueada](0147-quinto-estado-del-retorno-de-oauth-cuenta.md) | Aceptada |
| 148 | [Salida a /login desde los estados de error del callback](0148-salida-a-login-desde-los-estados-de.md) | Aceptada |
| 149 | [Chip de "ya te postulaste" en la card del explorador](0149-chip-de-ya-te-postulaste-en-la.md) | Aceptada |
| 150 | [isLoadingError, no isError, en las ocho pantallas de lectura](0150-isloadingerror-no-iserror-en-las-ocho-pantallas.md) | Aceptada |
| 151 | [Pertenencia explícita en /master/tables/:id, y el logo vuelve al home del contexto activo](0151-pertenencia-explicita-en-master-tables-id-y.md) | Aceptada |
| 152 | [GET /api/v1/game-tables/{id}/managed — la pertenencia se verifica en el backend, no después de que el dato ya viajó](0152-get-api-v1-game-tables-id-managed.md) | Aceptada |
| 153 | [El master se entera de una postulación nueva: tipo NewCandidate](0153-el-master-se-entera-de-una-postulacion.md) | Aceptada |
| 154 | [NotificationBell contra el mock, y siempre las últimas 5](0154-notificationbell-contra-el-mock-y-siempre-las.md) | Aceptada |
| 155 | [Un actor no puede ser jugador y master de la misma mesa](0155-un-actor-no-puede-ser-jugador-y.md) | Aceptada |
| 156 | [Las notificaciones se pueden clickear: marcan como leída, navegan, y cambian de contexto si hace falta](0156-las-notificaciones-se-pueden-clickear-marcan-como.md) | Aceptada |
| 157 | [El hover de una notificación no se notaba](0157-el-hover-de-una-notificacion-no-se.md) | Aceptada |
| 158 | [Panel de desarrollo flotante, solo en npm run dev](0158-panel-de-desarrollo-flotante-solo-en-npm.md) | Aceptada |
| 159 | [hasManagedTables en /users/me: #135 quedaba a medias](0159-hasmanagedtables-en-users-me-135-quedaba-a.md) | Aceptada |
| 160 | [Detalle en "Mis postulaciones": qué mesa, y por qué te rechazaron](0160-detalle-en-mis-postulaciones-que-mesa-y.md) | Aceptada |
| 161 | [DevPanel rompía /login con un bucle de reload - useMe() sin gatear](0161-devpanel-rompia-login-con-un-bucle-de.md) | Aceptada |
| 162 | [El diagrama de navegación se rescata del artifact de revisión, vivo en docs/diagramas/](0162-el-diagrama-de-navegacion-se-rescata-del.md) | Reemplazada por #266 |
| 163 | [E2 sub-rebanada 1: la máquina de estados completa reemplaza el open() self-service de E1](0163-e2-sub-rebanada-1-la-maquina-de.md) | Aceptada |
| 164 | [Cómo se busca en toda la aplicación: un lenguaje de una línea, con chips y conectores](0164-como-se-busca-en-toda-la-aplicacion.md) | Aceptada |
| 165 | [Asignar masters en /admin/tables: el orden de los chips es el rol](0165-asignar-masters-en-admin-tables-el-orden.md) | Aceptada |
| 166 | [Primary y Secondary son vocabulario interno: no se muestran](0166-primary-y-secondary-son-vocabulario-interno-no.md) | Aceptada |
| 167 | [Una pantalla de ayuda, /help, para toda la comunidad — reemplazada por #231: la ayuda pasó a ser un diálogo y /help dejó de ser una pantalla](0167-una-pantalla-de-ayuda-help-para-toda.md) | Reemplazada por #231 |
| 168 | [La ayuda se parte por audiencia y se enlaza por #ref, y cada fase la actualiza — reemplazada por #231: la ayuda pasó a ser un diálogo y /help dejó de ser una pantalla](0168-la-ayuda-se-parte-por-audiencia-y.md) | Reemplazada por #231 |
| 169 | [Admin y Owner no se acumulan: son el mismo rol con distinto alcance](0169-admin-y-owner-no-se-acumulan-son.md) | Aceptada |
| 170 | [La ayuda enseña, se ata al rol de quien lee, y resalta la sección que se vino a leer — (b) y (c) caen con #231](0170-la-ayuda-ensena-se-ata-al-rol.md) | Aceptada |
| 171 | [El explorador ordena por fecha, la mesa nueva primero](0171-el-explorador-ordena-por-fecha-la-mesa.md) | Aceptada |
| 172 | [Los datos que crea un e2e se borran al terminar la corrida](0172-los-datos-que-crea-un-e2e-se.md) | Aceptada |
| 173 | [Cómo se pagina cada cosa](0173-como-se-pagina-cada-cosa.md) | Aceptada |
| 174 | [Prettier, con la configuración del repo](0174-prettier-con-la-configuracion-del-repo.md) | Aceptada |
| 175 | [Borrar y cancelar no son lo mismo: la línea es si alguien más la vio](0175-borrar-y-cancelar-no-son-lo-mismo.md) | Aceptada |
| 176 | ["Por revisar" y "todas las mesas" son dos pantallas distintas](0176-por-revisar-y-todas-las-mesas-son.md) | Aceptada |
| 177 | [El plan se reordena: de etapas por subsistema a fases por actor](0177-el-plan-se-reordena-de-etapas-por.md) | Aceptada |
| 178 | [Choque de horarios entre mesas](0178-choque-de-horarios-entre-mesas.md) | Aceptada |
| 179 | [Administración de catálogos: se adelanta de F3 a F1](0179-administracion-de-catalogos-se-adelanta-de-f3.md) | Aceptada |
| 180 | [closedat se sella al cerrar la mesa](0180-closedat-se-sella-al-cerrar-la-mesa.md) | Aceptada |
| 181 | [Cómo se ejecuta cada rebanada: reparto en subagentes](0181-como-se-ejecuta-cada-rebanada-reparto-en.md) | Aceptada |
| 182 | [La entidad de systems se llama GameSystem](0182-la-entidad-de-systems-se-llama-gamesystem.md) | Aceptada |
| 183 | [Dar de baja el canónico de un grupo exige elegir sucesor](0183-dar-de-baja-el-canonico-de-un.md) | Aceptada |
| 184 | [GET /admin/catalogs/{type}/{id}/group](0184-get-admin-catalogs-type-id-group.md) | Aceptada |
| 185 | [El estado de /admin/catalogs vive en la URL](0185-el-estado-de-admin-catalogs-vive-en.md) | Aceptada |
| 186 | [Sanitizar el texto enriquecido con la librería de OWASP](0186-sanitizar-el-texto-enriquecido-con-la-libreria.md) | Aceptada |
| 187 | [InvalidRequestException, el quinto miembro de ApiException](0187-invalidrequestexception-el-quinto-miembro-de-apiexception.md) | Aceptada |
| 188 | [Un error 409 puede traer su propio código y su mensaje redactado](0188-un-error-409-puede-traer-su-propio.md) | Aceptada |
| 189 | [PUT /api/v1/game-tables/{id} reemplaza la mesa entera, no parchea](0189-put-api-v1-game-tables-id-reemplaza.md) | Aceptada |
| 190 | [La agenda y los catálogos se reemplazan como conjunto, y sus filas se marcan](0190-la-agenda-y-los-catalogos-se-reemplazan.md) | Aceptada |
| 191 | [Weekday es un enum propio, no java.time.DayOfWeek](0191-weekday-es-un-enum-propio-no-java.md) | Aceptada |
| 192 | [La zona horaria y el locale son parámetros de lib/date.ts](0192-la-zona-horaria-y-el-locale-son.md) | Aceptada |
| 193 | [Reanudar una mesa con choque responde 409 y no reanuda](0193-reanudar-una-mesa-con-choque-responde-409.md) | Aceptada |
| 194 | [Cancelar una sesión repone otra al final del calendario](0194-cancelar-una-sesion-repone-otra-al-final.md) | Aceptada |
| 195 | [Marcar una sesión como jugada es una acción explícita, separada de registrar la asistencia](0195-marcar-una-sesion-como-jugada-es-una.md) | Aceptada |
| 196 | [Una mesa sin fecha de inicio, sin agenda o sin sesiones previstas se abre con cero sesiones](0196-una-mesa-sin-fecha-de-inicio-sin.md) | Aceptada |
| 197 | [Ningún texto que lee una persona se escribe ni se guarda en el backend](0197-ningun-texto-que-lee-una-persona-se.md) | Aceptada |
| 198 | [El idioma se elige como el tema, se detecta del navegador y cae en español](0198-el-idioma-se-elige-como-el-tema.md) | Aceptada |
| 199 | [Nombre de la entidad de archivos](0199-nombre-de-la-entidad-de-archivos.md) | Aceptada |
| 200 | [Escritura del archivo y transacción](0200-escritura-del-archivo-y-transaccion.md) | Aceptada |
| 201 | [Alcance de la deduplicación](0201-alcance-de-la-deduplicacion.md) | Aceptada |
| 202 | [Compresión de los archivos](0202-compresion-de-los-archivos.md) | Aceptada |
| 203 | [Subir y vincular son dos llamadas](0203-subir-y-vincular-son-dos-llamadas.md) | Aceptada |
| 204 | [tablefiles.isprivate y files.filetype = 'Public' son ejes distintos](0204-tablefiles-isprivate-y-files-filetype-public-son.md) | Aceptada |
| 205 | [Qué ofrece el selector de archivos publicados](0205-que-ofrece-el-selector-de-archivos-publicados.md) | Aceptada |
| 206 | [Quién puede leer lo que una mesa comparte](0206-quien-puede-leer-lo-que-una-mesa.md) | Aceptada |
| 207 | [Pantalla de administración de archivos](0207-pantalla-de-administracion-de-archivos.md) | Aceptada |
| 208 | [@Transactional en un método @Scheduled que delega](0208-transactional-en-un-metodo-scheduled-que-delega.md) | Aceptada |
| 209 | [Cómo llegan las peticiones al lector](0209-como-llegan-las-peticiones-al-lector.md) | Aceptada |
| 210 | [La entrega del jugador se adelanta de F2 a F1.5](0210-la-entrega-del-jugador-se-adelanta-de.md) | Aceptada |
| 211 | [Quinta vía de lectura de un archivo](0211-quinta-via-de-lectura-de-un-archivo.md) | Aceptada |
| 212 | [Destino de la notificación TaskPublished](0212-destino-de-la-notificacion-taskpublished.md) | Aceptada |
| 213 | [GET /game-tables/{id}/players](0213-get-game-tables-id-players.md) | Aceptada |
| 214 | [Alto máximo de un diálogo de formulario](0214-alto-maximo-de-un-dialogo-de-formulario.md) | Aceptada |
| 215 | [El editor de texto enriquecido se anuncia como campo de texto](0215-el-editor-de-texto-enriquecido-se-anuncia.md) | Aceptada |
| 216 | [Quitar a un co-master, y qué pasa con su fila](0216-quitar-a-un-co-master-y-que.md) | Aceptada |
| 217 | [Qué lista el dashboard del master, y cómo se resuelve «entregas sin revisar»](0217-que-lista-el-dashboard-del-master-y.md) | Aceptada |
| 218 | [La pantalla de edición es una página con secciones, no el wizard](0218-la-pantalla-de-edicion-es-una-pagina.md) | Aceptada |
| 219 | [Dónde vive el dashboard del master en el backend](0219-donde-vive-el-dashboard-del-master-en.md) | Aceptada |
| 220 | [/master pasa a ser el home del contexto Master](0220-master-pasa-a-ser-el-home-del.md) | Aceptada |
| 221 | [El buscador de personas se acota a la mesa en vez de abrirse a más roles](0221-el-buscador-de-personas-se-acota-a.md) | Aceptada |
| 222 | [Cada contexto es dueño de un prefijo, y el chip dice dónde estás](0222-cada-contexto-es-dueno-de-un-prefijo.md) | Aceptada |
| 223 | [El actor de prueba tiene exactamente un rol](0223-el-actor-de-prueba-tiene-exactamente-un.md) | Aceptada |
| 224 | [El token accent deja de ser un alias de raised, y el cursor vuelve al preflight](0224-el-token-accent-deja-de-ser-un.md) | Aceptada |
| 225 | [Un tipo de mesa que trajo la aplicación se traduce; uno que escribió una persona, no](0225-un-tipo-de-mesa-que-trajo-la.md) | Aceptada |
| 226 | [Una mesa no se crea sin decir qué, dónde y cuándo; y los formularios hablan el idioma de quien los llena](0226-una-mesa-no-se-crea-sin-decir.md) | Aceptada |
| 227 | [Mi horario: la semana entera en una grilla, y la grilla es de donde se toma la franja](0227-mi-horario-la-semana-entera-en-una.md) | Aceptada |
| 228 | [La duración es de cada franja, la grilla es la única forma de armar la agenda, y los archivos entran al crear](0228-la-duracion-es-de-cada-franja-la.md) | Aceptada |
| 229 | [Los tags pasan a ser obligatorios — corrige #226 — y la duración de una franja se escribe](0229-los-tags-pasan-a-ser-obligatorios-corrige.md) | Aceptada |
| 230 | [startdate es un día, no un instante, y el paso a paso del wizard muestra lo que ya resolviste](0230-startdate-es-un-dia-no-un-instante.md) | Aceptada |
| 231 | [La ayuda pasa a ser un diálogo, /help queda para soporte, y salir con trabajo sin guardar pregunta — reemplaza #167 y #168, corrige #170](0231-la-ayuda-pasa-a-ser-un-dialogo.md) | Aceptada |
| 232 | [Dónde se usa un archivo se deriva del vínculo; a qué flujo pertenece se guarda.](0232-donde-se-usa-un-archivo-se-deriva.md) | Aceptada |
| 233 | [El cajón de un archivo es el flujo del que viene, no qué documento es](0233-el-cajon-de-un-archivo-es-el.md) | Aceptada |
| 234 | [Una subida deduplicada responde 200, no 201](0234-una-subida-deduplicada-responde-200-no-201.md) | Aceptada |
| 235 | [La compresión se queda en gzip síncrono](0235-la-compresion-se-queda-en-gzip-sincrono.md) | Aceptada |
| 236 | [Un master puede adjuntar archivos a la petición que escribe](0236-un-master-puede-adjuntar-archivos-a-la.md) | Aceptada |
| 237 | [La biblioteca personal y la de la plataforma son dos vistas distintas](0237-la-biblioteca-personal-y-la-de-la.md) | Aceptada |
| 238 | [Nada se sube hasta que se confirma la operación completa.](0238-nada-se-sube-hasta-que-se-confirma.md) | Aceptada |
| 239 | [Dos clases de comando en el buscador, y los nombres llevan su entidad adelante](0239-dos-clases-de-comando-en-el-buscador.md) | Aceptada |
| 240 | [Un solo buscador de verdad: el comando se escribe, y nada se cierra hasta Enter](0240-un-solo-buscador-de-verdad-el-comando.md) | Aceptada |
| 241 | [/my/files es de players y masters: quien no tiene el rol no lo ve](0241-my-files-es-de-players-y-masters.md) | Aceptada |
| 242 | [El buscador es el único filtro de /my/files, y el placeholder nombra el criterio básico](0242-el-buscador-es-el-unico-filtro-de.md) | Aceptada |
| 243 | [useConfirm vive en hooks/, no con su diálogo](0243-useconfirm-vive-en-hooks-no-con-su.md) | Aceptada |
| 244 | [La campanita cubre el ciclo de revisión y la asignación de masters](0244-la-campanita-cubre-el-ciclo-de-revision.md) | Aceptada |
| 245 | [Draft: la mesa del master nace en borrador, y enviarla a revisión se la saca de las manos](0245-draft-la-mesa-del-master-nace-en.md) | Aceptada |
| 246 | [Los comandos de catálogo del explorador son texto libre; el grupo lo resuelve el backend](0246-los-comandos-de-catalogo-del-explorador-son.md) | Aceptada |
| 247 | [Una postulación retirada conserva sus archivos; lo que muere es el uso, no la fila](0247-una-postulacion-retirada-conserva-sus-archivos-lo.md) | Aceptada |
| 248 | [El perfil entra en F2 con la mitad de arriba, y el bloque de comentarios no se dibuja vacío](0248-el-perfil-entra-en-f2-con-la.md) | Aceptada |
| 249 | [Un perfil que caducó responde 404, igual que el veto](0249-un-perfil-que-caduco-responde-404-igual.md) | Aceptada |
| 250 | [La revisión deja de ser el final de cada fase y pasa a ser una fase propia](0250-la-revision-deja-de-ser-el-final.md) | Aceptada |
| 251 | [La auditoría de F3.1 vive en dos tablas propias por entidad, no en approvalrequests ni esperando auditlogs](0251-la-auditoria-de-f3-1-vive-en.md) | Aceptada |
| 252 | [La invariante del último Owner necesita dos bloqueos, no uno](0252-la-invariante-del-ultimo-owner-necesita-dos.md) | Aceptada |
| 253 | [PlatformRole cruza HTTP con @JsonValue, y un cuerpo malformado es 400 y no 500](0253-platformrole-cruza-http-con-jsonvalue-y-un.md) | Aceptada |
| 254 | [Un rol que solo un Owner puede otorgar necesita que las pruebas puedan ser Owner](0254-un-rol-que-solo-un-owner-puede.md) | Aceptada |
| 255 | [approvalrequests entra con tres tipos, no con cinco](0255-approvalrequests-entra-con-tres-tipos-no-con.md) | Aceptada |
| 256 | [La resolución de un pedido se serializa con un bloqueo, y el pedido también](0256-la-resolucion-de-un-pedido-se-serializa.md) | Aceptada |
| 257 | [approvalrequests no tiene migración propia y no puede tenerla](0257-approvalrequests-no-tiene-migracion-propia-y-no.md) | Aceptada |
| 258 | [Resolver un ítem no exige tenerlo reservado: exige que no lo tenga otro](0258-resolver-un-item-no-exige-tenerlo-reservado.md) | Aceptada |
| 259 | [La bandeja son N consultas y un merge en Java, no un UNION ALL nativo](0259-la-bandeja-son-n-consultas-y-un.md) | Aceptada |
| 260 | [La bandeja se mantiene viva con refetchInterval, y el WebSocket la reemplaza en F6](0260-la-bandeja-se-mantiene-viva-con-refetchinterval.md) | Aceptada |
| 261 | [El badge de estado sube a components/; el diálogo con motivo obligatorio no](0261-el-badge-de-estado-sube-a-components.md) | Aceptada |
| 262 | [Se borra docker-compose.prod.yml: la topología de producción no está decidida, y un archivo la decidía](0262-se-borra-docker-compose-prod-yml-la.md) | Aceptada |
| 263 | [systemsettings guarda solo lo que alguien cambió, y la categoría Texts no se construye](0263-systemsettings-guarda-solo-lo-que-alguien-cambio.md) | Aceptada |
| 264 | [El tope por archivo y el timeout de la bandeja los publica un endpoint propio, GET /settings/limits](0264-el-tope-por-archivo-y-el-timeout.md) | Aceptada |
| 265 | [El cupo máximo por mesa nace en F3.5, porque un límite que no existe no se puede ajustar](0265-el-cupo-maximo-por-mesa-nace-en.md) | Aceptada |
| 266 | [El diagrama de navegación pasa a Archify y 18-navegacion.mmd se borra](0266-el-diagrama-de-navegacion-pasa-a-archify.md) | Aceptada |
| 267 | [La navegación se dibuja en cuatro diagramas con un nodo por ruta, y el 20 se borra](0267-la-navegacion-se-dibuja-en-cuatro-diagramas.md) | Aceptada |
| 268 | [Buscar son dos Enter, no se busca mientras se escribe, y todo buscador lleva «Limpiar filtros»](0268-buscar-son-dos-enter-no-se-busca.md) | Aceptada |
| 269 | [Cada contexto se cierra a quien no lo tiene, y la respuesta es volver a casa, no un 403](0269-cada-contexto-se-cierra-a-quien-no.md) | Aceptada |
| 270 | [/admin es la home del contexto Admin: una bienvenida, sin métricas](0270-admin-es-la-home-del-contexto-admin.md) | Aceptada |
| 271 | [Las listas de trabajo se paginan con tira numerada, salto directo a una página y tamaño elegible](0271-las-listas-de-trabajo-se-paginan-con.md) | Aceptada |
| 272 | [Las acciones de una fila en una tabla son íconos con tooltip, no botones con texto](0272-las-acciones-de-una-fila-en-una.md) | Aceptada |
| 273 | [Un patrón de estilo tiene nombre, y lo visual tiene su skill](0273-un-patron-de-estilo-tiene-nombre-y.md) | Aceptada |
| 274 | [Una sola skill de arquitectura](0274-una-sola-skill-de-arquitectura.md) | Aceptada |
| 275 | [Los catálogos se administran como grupos, y cada grupo se abre como un lienzo de nodos](0275-los-catalogos-se-administran-como-grupos-y.md) | Aceptada |
| 276 | [Dos operaciones nuevas sobre un grupo: mover un equivalente (reassign) y hacer principal (promote)](0276-dos-operaciones-nuevas-sobre-un-grupo-mover.md) | Aceptada |
| 277 | [La página no se sale de la pantalla en ningún ancho, de 320 px a un ultrawide](0277-la-pagina-no-se-sale-de-la.md) | Aceptada |
| 278 | [/admin/files es la biblioteca de la plataforma y nada más: solo lo publicado, y subir ahí es publicar, con los cajones elegidos antes](0278-admin-files-es-la-biblioteca-de-la.md) | Aceptada |
| 279 | [Subir a la biblioteca es su propia página, /admin/files/upload, y cada archivo elige qué es en su fila de la lista, con un select](0279-subir-a-la-biblioteca-es-su-propia.md) | Aceptada |
| 280 | [Toda cabecera tiene un solo lugar para cada cosa: «Volver» arriba del título; «Cómo funciona» y la acción principal a la derecha, en ese orden](0280-toda-cabecera-tiene-un-solo-lugar-para.md) | Aceptada |
| 281 | [El estándar de cabecera llega a todas las pantallas: los dos detalles de mesa del jugador pasan a PageHeader, y Mis archivos y Mi horario tienen su «Cómo funciona»](0281-el-estandar-de-cabecera-llega-a-todas.md) | Aceptada |
| 282 | [La biblioteca de la plataforma tiene ciclo de vida: sin publicar, publicado, oculto y dado de baja](0282-la-biblioteca-de-la-plataforma-tiene-ciclo.md) | Aceptada |
| 283 | [Todo lo que crea, cambia o borra se confirma antes de escribirse, con un paso de revisión que dice qué cambia, a quién afecta y qué sigue](0283-todo-lo-que-crea-cambia-o-borra.md) | Aceptada |
| 284 | [El admin ve cada mesa entera y puede editar la de otro, y todo lo que un admin cambia de algo ajeno se le avisa a quien le pertenece](0284-el-admin-ve-cada-mesa-entera-y.md) | Aceptada |
| 285 | [Cada persona de una mesa tiene un indicador «Ver ficha», y la ficha depende de quién la abre: el admin ve la cuenta entera en /admin/users/:id; el master, una ficha corta](0285-cada-persona-de-una-mesa-tiene-un.md) | Aceptada |
| 286 | [(Corregida por #287: la cancelación y las demás acciones ya no están en una pestaña Estado, sino en el aviso de estado arriba de las pestañas.) El admin decide sobre una mesa desde su propia pestaña Estado: aprobarla o pedir cambios con el motivo mientras está en revisión, y cancelarla con el motivo cuando no tiene arreglo — precisa #176, que había dejado la revisión solo en la bandeja](0286-corregida-por-287-la-cancelacion-y-las.md) | Aceptada |
| 287 | [Las decisiones sobre una entidad van en su aviso de estado, arriba de las pestañas, nunca en una pestaña o sección aparte](0287-las-decisiones-sobre-una-entidad-van-en.md) | Aceptada |
| 288 | [En un aviso de estado, las acciones destructivas van aparte, en el extremo derecho, cuando hay otros botones](0288-en-un-aviso-de-estado-las-acciones.md) | Aceptada |
| 289 | [Las decisiones pasan a ADRs, un archivo por decisión, con el número conservado](0289-las-decisiones-pasan-a-adrs-un-archivo-por.md) | Aceptada |
| 290 | [Las reglas duras se cargan por ruta desde .claude/rules/, y las rules de ECC se versionan](0290-las-reglas-duras-se-cargan-por-ruta-desde-claude-rules.md) | Aceptada |
| 291 | [Agentes propios en .claude/agents/, que precargan las skills del repo y devuelven hallazgos](0291-agentes-propios-en-claude-agents-que-precargan-las.md) | Aceptada |
| 292 | [Los documentos de referencia se disuelven en las skills, con su numeración](0292-los-documentos-de-referencia-se-disuelven-en-las-skills.md) | Aceptada |

## Abierto

Lo que sigue sin resolverse: cuando se cierra, se vuelve una ADR y sale de esta tabla. **Dos entradas se retiraron de acá por estar respondidas**: el *diseño de Campañas y Temporadas*, que cerró #129 y solo espera la migración de fase 2, y la *secuenciación de entrega*, que `plan-desarrollo.md` fijó primero en E1–E6 desde #177 en fases por actor y desde #250 en **F1–F6**, con la revisión en F4 y comentarios y karma en F5.

| Tema | Estado |
|---|---|
| Jerarquía de catálogos | Las columnas `parent_id` se conservan, pero la navegación de árbol nunca se terminó en Node y no es requisito de v1. |
| **Integración profunda con Discord** | **Planeada, no aprobada por el cliente. Fuera de v1.** Se anota ahora para que las decisiones de hoy no la bloqueen. Alcance previsto: desde la página, redirigir al canal de Discord de tu mesa y unirte directamente; y manejo de canales desde el sistema — abrir el canal cuando la mesa pasa a `Opened` o `InProgress` y cerrarlo cuando pasa a `Finished` o `Canceled`. Implica un bot con permisos sobre el servidor (no solo OAuth2 de login, que es lo único que hay en v1) y una columna de referencia al canal en `game_tables`. **No se agrega nada al modelo por ahora**; cuando se apruebe, es una migración aditiva. |
| **¿«Conservarlo para reusar» protege un archivo de la purga?** | **Discrepancia encontrada en #281, sin resolver.** El diálogo de editar de `/my/files` dice que un archivo sin marcar «se libera cuando pase mucho tiempo sin uso», lo que promete que uno marcado (`Private`) se queda. Pero la purga (`StoredFileRepository.findPurgeCandidates`, #75) exceptúa **solo** a los `Public`: un `Private` sin vínculo vivo con una mesa y sin uso en 90 días se da de baja igual que un `Single-use`. Hay que decidir cuál de los dos tiene razón: si conservar protege, la consulta tiene que exceptuar también a los `Private`; si no, el texto del diálogo tiene que dejar de prometerlo. **Y una segunda discrepancia en la misma consulta**: #247 afirma que el job de retención «hereda» el filtro de postulación viva de los usos, pero `findPurgeCandidates` solo mira `table_files` — ni una postulación viva, ni una entrega, ni un formulario adjunto a un pedido (`task_files`) protegen hoy a un archivo de la purga. |
| Generar los tipos del frontend desde OpenAPI | Candidato, no adoptado. springdoc ya publica el schema, así que los tipos base de `features/<dominio>/types.ts` podrían generarse en vez de escribirse a mano y quedarían imposibilitados de divergir del backend. Cuesta un paso de build y ata el frontend a que el backend esté levantado o a un artefacto versionado. Se evalúa cuando el contrato de la API esté estable, no antes. Hasta entonces rige skill `arquitectura` §3.2 con tipos escritos a mano. |

## Qué se rescata de `CentralDungeonBackend`

Revisado a fondo en agosto de 2026, con el repo en `/Users/d4m14n257/Personal/CentralDungeonBackend`. Como referencia de forma, nunca copiando código literal:

- **La forma del `JwtAuthorizationFilter`**: token con el `id` como único claim y `loadUserById` resolviendo autoridades en cada request (#122). Es lo mejor que tiene el repo. Se corrigen tres cosas: el `chain.doFilter()` está **dentro del `try`**, así que cualquier excepción de un controller río abajo se reporta como `401`; `validateToken` lanza en vez de devolver `false`, lo que vuelve engañoso el `&&` que lo acompaña; y no hay refresh token (#125).
- **La forma de los `@ConfigurationProperties` como records** (`DiscordConfig`, `JwtConfig`, `HostOriginConfig`).
- **Tomar la identidad de `@AuthenticationPrincipal`** y no de la URL: ese error central del Node ya estaba corregido ahí.
- El patrón `@RestControllerAdvice` para manejo centralizado de errores.

**Se descarta**, con motivo:

| Qué | Por qué |
|---|---|
| `Routes.java` + seguridad por lista de URLs | Cinco de seis rutas sin barra inicial: no protegían nada (#123) |
| Interfaces `*IController` | El contrato lo publica OpenAPI y lo tipan los records (#119) |
| `ResponseData<T>` | Duplica el status y ya se contradecía (#120) |
| Repositorios sobre `JdbcTemplate` | Parámetros corridos, mapeo imposible, SQL que no parsea (#124) |
| `CustomIdGenerator` | Correcto en sí mismo (`SecureRandom` + Base64, 72 bits), pero **#9 ya fijó UUID v7**, que tiene 74 bits aleatorios y además da localidad de índice en InnoDB |
| Paquete por capa (`controllers/`, `services/`, `repositories/`) | El proyecto nuevo es paquete por feature (skill `arquitectura` §2.1) |
| Los controllers stub | `TablesController.getJoinedTables` tiene un `List<>` suelto: el repo no compila |

## Pendientes del modelo de datos (M1–M32)

Los pendientes que se abrieron al revisar el modelo heredado, en agosto de 2026, y la decisión que cerró cada uno. **Están todos cerrados.** Los cuatro que se resolvieron «en parte» dejaron un residual que pasó a otro pendiente y se cerró ahí: M1 → M7, M2 → M11, M4 → M10, M14 → M16; los puntos abiertos de M25 los cerraron M26 y #75.

El razonamiento completo de cada uno —las opciones que se pesaron y por qué se descartaron— se colapsó en F4.0 a este índice: vive en las decisiones que lo cerraron y, palabra por palabra, en el historial de git del antiguo `docs/decisiones.md`.

| | Pendiente | Cómo cerró |
|---|---|---|
| M1 | `table_registrations` hace dos trabajos a la vez | Resuelto en parte (#23) |
| M2 | Comentarios sin mesa y karma sin valor numérico | Resuelto en parte (#30, #31) |
| M3 | Los adjuntos no tienen estado propio | Ampliado por #60 y M21–M26 |
| M4 | Horarios sin fin ni excepciones | Resuelto en parte (#26) |
| M5 | Un master puede postularse a su propia mesa | Resuelto (#73, #155) |
| M6 | La identidad depende por completo de Discord | Resuelto (#83, #84, #85) |
| M7 | Con N solicitudes por mesa, ¿cuál es la vigente? | Resuelto (#28) |
| M8 | Alcance del veto (`Blocked`) | Resuelto (#29) |
| M9 | Dónde se guardan las justificaciones de transición | Resuelto (#32) |
| M10 | Sesiones: ¿materializadas o derivadas? | Resuelto (#33) |
| M11 | Alcance del "solo una vez" de los comentarios, y cuánto vale cada uno | Resuelto (#35, #36, #96–#99) |
| M12 | Residuales del veto | Resuelto (#39) |
| M13 | Los "pedidos con aprobación" ya son un patrón, y conviene modelarlos una sola vez | Resuelto (#42, #78, #126) |
| M14 | Residuales de la visibilidad de perfiles | Resuelto casi todo (#43, #44, #45) |
| M15 | El anonimato duro choca con tres decisiones previas | Resuelto (#82) |
| M16 | Comentarios escritos antes de que la mesa se cancele | Resuelto (#48) |
| M17 | Consecuencias del comentario en borrador | Resuelto (#49, #50, #51) |
| M18 | Un borrador con `deleted_at` sigue siendo un registro de autoría | Resuelto (#52) |
| M19 | Cómo modelar los sinónimos de catálogo | Resuelto (#54, #59) |
| M20 | Otros pendientes de catálogos | Resuelto (#55, #56, #57, #58, #81) |
| M21 | Qué encontré en el código de archivos que no estaba en el modelo | Relevamiento: lo cerraron M22–M26 |
| M22 | Texto enriquecido: no está en el modelo | Resuelto (#62, #63) |
| M23 | Requisitos al jugador ya aceptado | Resuelto (#63) |
| M24 | Archivos públicos: quién, dónde y con qué alcance | Resuelto (#64, #79) |
| M25 | Biblioteca privada: alcance, cuota y costo | Resuelto en parte (#65) |
| M26 | Ciclo de vida del archivo físico | Resuelto (#66, #80, #199, #200) |
| M27 | La cuota de #61 choca con la reutilización de #65 | Resuelto (#68, #75) |
| M28 | Mesas creadas por un admin, sin que él sea master | Resuelto (#72, #73) |
| M29 | Cómo renombrar el `Owner` de la mesa | Resuelto (#71) |
| M30 | El sistema no puede distinguir "se fue" de "lo banearon" | Resuelto (#86) |
| M31 | ¿`General` comparte tabla con los comentarios sobre personas? | Resuelto (#91, #93) |
| M32 | ¿La internacionalización entra en v1? | Resuelto (#117) |
