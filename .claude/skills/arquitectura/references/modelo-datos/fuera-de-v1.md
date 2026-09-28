# 4.7 Fuera de alcance de v1 (decidido, no olvidado)

> Parte de la skill `arquitectura` (#274), sección 4 — el modelo de datos. Se movió desde `docs/` en F4.0; su número original lleva ahora el prefijo `4.` (el viejo §5 es §4.5).


| Tema | Estado |
|---|---|
| **Campañas y Temporadas** | Fase 2 — **diseño ya cerrado en #129**, solo espera la migración. *Campaña* = **una** mesa larga dividida en bloques, porque el reclutamiento es único: entidad nueva `table_arcs` entre `game_tables` y `table_sessions`, más `arc_id` en `table_sessions`. **No** es un agrupador de varias mesas ni una FK, como decía la versión anterior de esta fila. *Temporada* = **no es entidad**: `publish_at DATETIME NULL` en `game_tables` y un job que pasa `Preparation → Opened` al llegar la fecha. Tres puntos a resolver al construirlo, en §7.1. |
| **Integración profunda con Discord** | Planeada, no aprobada. Un solo lote de trabajo, todo dependiente de un **bot con permisos** sobre el servidor (#88): canal de voz por mesa, abrir y cerrar canales según el estado de la mesa, y detección automática de baneos (#86). Lo único que no necesita bot es enlazar a un canal que ya existe. Migración aditiva cuando se apruebe. |
| **Personajes estructurados** | Siguen siendo archivo adjunto genérico, no entidad con nombre/clase/nivel/stats (#4). |
| **Detección automática de baneos de Discord** | En v1 un admin marca el baneo a mano (#86). Automatizarlo requiere el bot, y va en el mismo lote que el resto de la integración (#88). |
| **i18n** | **La aplicación habla español e inglés desde #198**, pero el modelo sigue sin columna de idioma y es deliberado: la elección vive en `localStorage`, igual que el tema. Lo que sí cambió el modelo es #197 — `notifications.params` guarda los nombres que la frase necesita en vez de la frase, y `title` pasa a ser nulable. |

### 4.7.1 Campañas y Temporadas: qué queda por resolver al construirlas

El diseño está cerrado (#129), pero tres puntos chocan con decisiones ya tomadas y hay que resolverlos **antes** de escribir la migración, no durante:

**1. `total_sessions` pasa a tener dos fuentes de verdad.** Hoy `game_tables.total_sessions` es autoritativo (#26). Con arcos, lo natural es que mande la suma de `planned_sessions` de los arcos, y la columna quede derivada o desaparezca. Hay que elegir una de las dos y que la otra no exista, o se desincronizan.

**2. `Preparation` queda sobrecargado.** Hoy significa exactamente una cosa: "creada por el master, esperando que un admin la evalúe, no pública". Con `publish_at`, una mesa **ya aprobada pero con salida agendada** también estaría en `Preparation`, y son dos situaciones distintas — una espera juicio humano, la otra espera un reloj. Lo previsible es que haga falta un estado `Scheduled` entre `Preparation` y `Opened`. Si no, la bandeja de admins (#100) mostraría como pendientes mesas que ya nadie tiene que revisar.

**3. Las campañas rompen la escala de la cuota de comentarios y de la caducidad de visibilidad.** La cuota antispam es **una evaluación por mesa** (#35) y la visibilidad de perfiles caduca a las **dos semanas del cierre** (#44). Una campaña de 40 sesiones es *una* mesa: produce un solo comentario por par de personas después de un año de juego, mientras que cuatro mesas cortas producirían cuatro. El karma recibiría mucha menos señal justo de las relaciones más largas, que son las que más información tienen. Al construir campañas hay que decidir si la cuota y la ventana de caducidad pasan a contarse **por arco** en vez de por mesa.
