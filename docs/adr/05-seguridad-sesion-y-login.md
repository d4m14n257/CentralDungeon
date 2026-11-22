---
adr: 5
titulo: Seguridad, sesión y login
estado: Vigente
actualizada: 2026-11-22
absorbe: [38, 83, 84, 85, 86, 88, 121, 122, 123, 125, 127, 128, 147, 148, 186]
---

# ADR-05: Seguridad, sesión y login

> Cómo se entra, qué afirma la sesión y cómo se revoca, dónde se autoriza, cómo se protege cada recurso y qué responde una negativa, el texto enriquecido, los límites de frecuencia, qué no se publica en producción y qué depende de Discord. Lo aplica `arquitectura` §2.6 con la regla dura 17, y lo revisa el agente `revisor-seguridad`; esta ADR dice *qué* se decidió y *por qué*.

## §1 Se entra con Discord y siendo miembro del servidor

**Decisión.** El login requiere cuenta de Discord y membresía al servidor de CentralDungeon; no hay registro propio. Si la persona no es miembro, primero se le ofrece unirse, y solo si declina no hay login. Todo usuario nuevo se crea con rol `Player`, desde el service (`arquitectura` §4.5).

**Por qué.** Es la puerta de la comunidad: no debería haber usuarios que no estén en el servidor. No se rechaza con un error seco porque quien llega sin ser miembro casi siempre quiere serlo; la invitación va antes del corte.

*Antes: #38.*

## §2 Tres tokens: el de Discord se descarta, el access es corto y el refresh rota en una familia revocable

**Decisión.** El token OAuth de Discord se descarta al terminar el callback. La sesión es propia: un **access** corto (~15 min) en memoria del cliente y un **refresh** largo en cookie `httpOnly` + `SameSite=Strict`, que el servidor recuerda en la tabla **`refresh_tokens`**: el hash de su `jti`, `family_id`, `user_id`, `expires_at` y `revoked_at`. El login abre una familia, y cada renovación:

- **revoca el refresh presentado** y emite uno nuevo en la misma familia;
- **ventana de gracia de 30 s**: si el presentado fue rotado hace menos de 30 s y su familia todavía tiene un token vivo, no es reuso: se revoca ese sucesor, que nunca llegó al navegador, y se emite otro de la misma familia;
- fuera de la ventana, o con la familia sin tokens vivos —la cerró un logout o un reuso—, presentar uno **ya rotado o revocado** es un reuso: se revoca **la familia entera** y la respuesta es `401`.

**El logout revoca la familia**, además de expirar la cookie. **Si el logout falla en el servidor, el frontend lo reintenta una vez**; si vuelve a fallar, limpia igual la sesión local y avisa que la sesión no se cerró en el servidor, con la salida de cerrar el navegador o reintentar. El refresh sigue siendo el punto donde se re-afirman `status` y roles (§3). **El cliente hace un solo refresh a la vez**: `api/client.ts` comparte la renovación en curso entre todas las llamadas que reciben un `401` al mismo tiempo. **CSRF está activo únicamente en los dos endpoints que autentica la cookie**, `POST /auth/refresh` y `POST /auth/logout`: `csrf.spa().requireCsrfProtectionMatcher(...)` con un matcher que cubre esos dos, y el frontend manda el header en ambos; el resto de la API no pide token CSRF.

**Por qué.** El token de Discord contesta dos preguntas durante el login —quién sos y si sos miembro— y después no se usa; guardarlo exigiría otro ciclo de refresh para una capacidad que v1 no tiene, y la integración futura necesita un *bot token*, que es de la aplicación (§7). **Un refresh JWT sin estado no se puede revocar**: el logout solo expiraba la cookie, y un refresh robado seguía valiendo hasta vencer; rotar sin recordar qué se rotó tampoco detecta nada. Con la familia, el ladrón y la persona terminan presentando el mismo token, el segundo que llega pasada la ventana es un reuso y se corta la sesión de los dos, el ladrón incluido. Se guarda el hash y no el token para que la tabla no contenga sesiones utilizables. Se descartaron `users.refresh_not_before`, que corta todas las sesiones de una persona pero no detecta el reuso, y aceptar el riesgo. **La ventana de gracia** sale de un bug reproducido: recargar la página mientras corre el refresh hace que el navegador aborte una respuesta que el servidor ya rotó; la cookie nueva no llega, la página siguiente presenta el token rotado y, sin ventana, se leía como robo y cerraba la sesión. El ladrón solo gana esos 30 s, y únicamente detrás de una rotación legítima —es lo que hacen Auth0 y Okta—, mientras que lo estricto desloguea a cualquiera que recargue rápido. Se descartaron **10 s**, con los que una red lenta vuelve a cerrar la sesión, y **mantenerlo estricto**. El refresh de a uno sigue siendo del cliente: dos `401` simultáneos presentarían el mismo refresh, y el segundo gastaría la ventana revocando el sucesor que el primero acaba de recibir. **El logout que falla no deja la pantalla abierta**: en una computadora compartida la cookie seguía viva y el siguiente entraba con la cuenta anterior; se descartó no limpiar nada en pantalla, y el aviso es lo que le dice a la persona que el servidor no se enteró. La ventana y el logout que falla los decidió el usuario el 2026-11-22, al cerrar F4.4 «Seguridad». La API se autentica con `Authorization: Bearer`, que el navegador nunca adjunta por su cuenta, así que no exigir CSRF ahí es correcto. Refresh y logout son los únicos endpoints con cookie, que sí viaja sola: un sitio ajeno no puede leer la respuesta (CORS), pero sí forzar la rotación o el logout y cerrarle la sesión al usuario legítimo. `SameSite=Strict` lo bloquea, pero lo aplica el navegador; el token CSRF es la comprobación que ocurre en el servidor. Lo decidió el usuario el 2026-11-18, en F4.4 «Seguridad».

*Antes: #125, #127.*

## §3 El JWT afirma identidad; roles y `status` se leen de la base con una caché de 60 s

**Decisión.** El token lleva el `sub` y poco más. Roles y `status` se leen de la base en cada request, a través de Caffeine en proceso vía Spring Cache: `expireAfterWrite = 60 s`, `maximumSize = 10 000`, y `@CacheEvict` explícito al bloquear a alguien o cambiarle los roles. **Una cuenta `Blocked` con un access token o una cookie de refresh válidos recibe `401` con el código `ACCOUNT_BLOCKED` en su `ProblemDetail`**: en todo endpoint protegido, donde lo ponen `JwtAuthenticationFilter` y el entry point de autenticación, y en `POST /auth/refresh`. El frontend, al recibirlo, no intenta renovar la sesión y muestra la pantalla «Tu cuenta está bloqueada», la misma que muestra el retorno del login por Discord con `error=user_blocked` (§9). Los demás `401` —sin token, token vencido, cookie inválida— no cambian. Es `401` y no `403` para que el frontend trate el bloqueo como un fin de sesión, con su motivo.

**Por qué.** Un JWT con roles es una foto del momento en que se emitió, y sin revocación un admin degradado o alguien `Blocked` seguiría entrando hasta que expire. Leyendo de la base, un baneo o una degradación tienen efecto inmediato, y es una lectura por PK cacheada. **La cuenta bloqueada tiene que saber que lo está**: con un `401` genérico, quien tenía la sesión abierta cuando lo bloquearon volvía a la aplicación, el refresh fallaba y terminaba en `/login` sin saber qué pasaba; la única forma de enterarse era volver a entrar por Discord. El usuario lo vio en su propia cuenta. Se pesó no distinguir el bloqueo, para no confirmárselo a quien solo tiene el token, y pesa poco: el refresh lo autentica una cookie `httpOnly` que vive en el navegador de la persona y el access token sale de esa misma sesión, así que quien los tiene es, en la práctica, la persona bloqueada, que igual se entera al entrar por Discord. Lo decidió el usuario el 2026-11-11, durante F4.2 «La matriz de roles». **El TTL es la ventana de revocación**: 60 s y no cinco minutos porque `Owner` da acceso a la auditoría (ADR-17 §1) y al borrado físico (ADR-13 §8); la evicción explícita hace el efecto inmediato y el TTL es la red para el camino que se olvide de evictar. **Limitación aceptada**: la caché es por JVM; con más de una instancia `@CacheEvict` solo limpia la local, la misma restricción que el broker STOMP en memoria (ADR-18 §2), y hará falta caché compartida o el TTL será la única garantía.

*Antes: #122, #128.*

## §4 La autorización se declara en el método del controller

**Decisión.** Cada método de controller declara su `@PreAuthorize`, enumerando sus roles. `SecurityConfig` tiene solo lo transversal: sin sesión de servidor salvo la corta que necesita el intercambio OAuth2 (`IF_REQUIRED`), CORS y qué es público. **Nunca una lista de rutas por rol**, y nunca una anotación heredada (ADR-04 §2).

**Por qué.** El intento previo tenía un `Routes.java` con endpoints por rol, y cinco de sus seis rutas estaban escritas sin barra inicial: no matcheaban nada, caían en `anyRequest().authenticated()` y cualquier usuario autenticado alcanzaba los endpoints de master y de admin. La causa de fondo es la distancia: la regla vivía en otro archivo y nada obligaba a que coincidiera con el mapping. Pegada al método, el permiso se lee junto al endpoint y no queda huérfano; heredada, es el bypass de CVE-2025-41248.

*Antes: #123.*

## §5 Toda lectura o mutación de un recurso concreto filtra por el actor

**Decisión.** O el actor entra en el `WHERE`, o el service verifica la pertenencia antes de tocar nada; nunca `findById(id)` + `save()` sin comprobar. El actor sale del token, nunca de la URL (regla dura 17). Qué se responde cuando la comprobación niega, en §10.

**Por qué.** Es el agujero del Node: `UPDATE Tables SET … WHERE id = ?` sin un predicado de pertenencia, así que cualquiera con el id de una mesa podía editarla o borrarla. `hasRole('MASTER')` afirma «es master de algo», no «de *esta* mesa», y la segunda comprobación no se puede delegar en Spring Security (ADR-06 §4). Que los ids sean impredecibles (ADR-03 §4) es defensa en profundidad: un id filtrado en un link no puede ser lo único que proteja un recurso.

*Antes: #121.*

## §6 El texto enriquecido se sanitiza con OWASP, por lista blanca, al guardar y al servir

**Decisión.** `owasp-java-html-sanitizer` detrás de `RichTextSanitizer` en `common/text/`, con lista blanca: `p`, `br`, `strong`, `em`, `u`, `s`, `code`, `pre`, `blockquote`, `h1`–`h3`, `ul`/`ol`/`li` y `a` con `href` **solo `http` o `https`** —ni `mailto` ni ningún otro protocolo (`allowUrlProtocols("http", "https")`)— y `rel=nofollow`. Todo lo demás cae, incluidos `img`, `style` y cualquier `on*`. Se aplica al guardar y al servir sobre `description`, `permitted` y `requirements` de la mesa (ADR-03 §8), y sobre la descripción de una petición y el contenido de una entrega (ADR-15). Nulo entra, nulo sale, y lo que queda vacío tras sanitizar vuelve nulo.

**Por qué.** Es la superficie de XSS más directa del sistema, y escapar a mano es la forma habitual de publicar un XSS: entidades, protocolos de `href`, atributos de evento y comentarios condicionales, cualquiera alcanza. Lista blanca porque una etiqueta que nadie previó tiene que ser inofensiva por defecto. Al servir además de al guardar porque una fila escrita antes de la puerta, o por un camino que la saltee, llega igual al navegador. Los protocolos se enumeran a mano y no se toman los «estándar» de la librería (`allowStandardUrlProtocols`), que suman `mailto`: la lista blanca vale también para los protocolos, y lo que nadie nombró no pasa.

*Antes: #186.*

## §7 Irse del servidor no borra nada; ser baneado bloquea, y en v1 lo marca un admin

**Decisión.** Salir del servidor de Discord conserva los datos —karma, historial, mesas— por si la persona vuelve. Ser baneado del servidor implica quedar baneado del sistema, sin retorno: un admin le pone `status = 'Blocked'` a mano desde el panel de moderación (ADR-11 §7), y desde ese momento su sesión recibe `401` con el código `ACCOUNT_BLOCKED` en todo endpoint y en el refresh, y la persona ve que está bloqueada (§3). Automatizarlo queda para el bot. Con solo OAuth2 (v1) se puede identificar al usuario y verificar si es miembro; **necesitan bot** detectar baneos, crear y borrar canales por mesa y leer la lista de miembros, y se harán juntas. Enlazar a un canal existente no necesita bot: es un link.

**Por qué.** Sin la distinción, salirse un mes equivaldría a perder dos años de historial, y quien fue expulsado de la comunidad volvería a entrar. OAuth2 solo responde si alguien es miembro: el que se fue y el baneado devuelven lo mismo, y consultar baneos exige un bot con permisos, fuera de v1. Sin la marca manual, la distinción no la aplicaría nadie. Baneos, canales y su apertura según el estado de la mesa son la misma pieza de trabajo: un bot con permisos sobre el servidor.

*Antes: #84, #86, #88.*

## §8 Depender de Discord es un riesgo asumido, y una cuenta perdida se migra a mano

**Decisión.** No hay vía alternativa de login: si Discord cambia su OAuth o corta el acceso, se responde como emergencia. Tampoco hay recuperación de cuenta ni self-service: si alguien pierde su cuenta de Discord, lo único posible es que el owner migre sus datos a la cuenta nueva, como operación de administración auditada (ADR-13 §8, ADR-06 §1).

**Por qué.** No hay contraseña, email ni ningún otro dato propio con que reconocer a alguien, y construir un sistema de identidad paralelo por si acaso no se justifica. La migración manual cubre el caso real: una persona conocida de la comunidad que vuelve con otra cuenta. Queda escrito para que sea una decisión y no una sorpresa.

*Antes: #83, #85.*

## §9 El retorno de OAuth pinta sus estados en una misma tarjeta, y todo error tiene salida a `/login`

**Decisión.** `OAuthCallbackPage` muestra cuatro estados —verificando, no es miembro, **cuenta bloqueada** y error— y, al terminar bien, redirige; con un mismo `CallbackCard` (card + badge) y el wordmark arriba, igual que en `/login`; el diseño vive en `sc_callback()` de `design/build.py`. Bloqueado usa el tono `canceled` y no ofrece reintentar. Los tres estados de error (no es miembro, bloqueado, error genérico) tienen **«Volver al login»** (`Link` de React Router): `ghost` como tercera acción en no-es-miembro, detrás de unirse y «Ya me uní, reintentar»; `outline` y única en bloqueado y en el error genérico.

**Por qué.** `status = 'Blocked'` no tiene que ver con Discord ni con el guild, y no tenía pantalla. Los estados de la misma pantalla son la misma forma repetida, así que van en un solo componente para no quedar inconsistentes uno al lado del otro. La pantalla no tiene header, y el wordmark es la única marca de que se sigue en la aplicación. Sin la salida, quien llegaba a un error se quedaba sin camino: en bloqueado reintentar no cambia el `status`, así que volver al login es la única acción con sentido.

*Antes: #147, #148.*

## §10 Negar no confirma: lo que el actor no puede ver es `404`, y `403` es solo de lo que ve y no puede hacer

**Decisión.** Sobre todo recurso con id, la negativa sale de comprobar en este orden, y la primera que falla es la respuesta:

1. **Existe y el actor lo puede ver** → si no, `404`. Una fila `Deleted` no existe (ADR-03 «Modelo de datos y persistencia» §7).
2. **El actor puede hacer esa acción** → si no, `403`.
3. **El estado del recurso la admite** → si no, `409`.

Qué es «ver», por recurso:

| Recurso | Lo ve quien… |
|---|---|
| Mesa | pasa `TableVisibilityService.canSee`; el service lo exige con `requireVisible` |
| Sesión, petición, postulación | ve su mesa y, además, la dirige o es el destinatario o el dueño del recurso |
| Archivo | pasa `FileService.requireReadable` (ADR-14 «Archivos: lectura y bibliotecas» §2) |
| Notificación | es su dueño |
| Perfil | pasa `ProfileVisibilityService.requireVisible` (ADR-10 «Perfiles» §3) |

Generaliza tres casos que ya regían: el veto (ADR-09 «Postulaciones y veto» §4), la mesa borrada (ADR-07 «Mesas: ciclo de vida» §6) y el perfil que no se puede ver (ADR-10 «Perfiles» §3). El precedente en el código es `GameTableService.getStatusHistory`: a quien no dirige la mesa le exige primero `requireVisible`, y recién después responde `403`.

**Por qué.** Un `403` sobre algo que el actor no puede ver le confirma que existe, y bastaría probar ids hasta ver uno; los ids impredecibles (ADR-03 §4) son defensa en profundidad, no lo que lo oculta. Un `409` antes de la visibilidad filtra además el estado. El orden lo da cada respuesta: la siguiente solo puede decir algo que la anterior ya admitió. Se descartó **`404` siempre**, que borraría el «No tenés permiso» de quien sí ve la mesa y no puede hacer lo que intenta, y que merece saber por qué; y **dejar el `403`**, que confirma la existencia. Lo decidió el usuario el 2026-11-18, en F4.4 «Seguridad», para todos los recursos a la vez.

## §11 Las puertas que cualquiera puede repetir tienen un límite por ventana, contado en proceso

**Decisión.** Un filtro propio, sobre Caffeine, sin dependencia nueva, cuenta pedidos por clave y ventana. Límites iniciales:

| Endpoint | Límite | Clave |
|---|---|---|
| El login con Discord y su callback | 10 por minuto | IP |
| `POST /auth/refresh` | 30 por minuto | IP |
| Toda subida de archivos | 20 por hora | usuario del token |
| `POST /game-tables/{id}/registrations` (postularse) | 10 por hora | usuario del token |
| Proponer un valor de catálogo (`POST /tags`, `/systems`, `/platforms`), **un solo contador** para los tres | 20 por hora | usuario del token |
| El buscador de co-masters (`GET /game-tables/{id}/master-candidates`) | 30 por minuto | usuario del token |

Pasarse responde **`429`** con un `ProblemDetail` de código **`RATE_LIMITED`** y el header **`Retry-After`**. Los límites van en `application.yml`, y el perfil `test` los sube; **no** son ajustes de `system_settings`. La IP es la que resuelve Spring con **`server.forward-headers-strategy`**, y solo se confía en lo que dice el proxy propio: nunca se lee `X-Forwarded-For` a mano. **Una IPv4 cuenta por su dirección y una IPv6 por su prefijo `/64`**; una IPv4 escrita como IPv6 (*IPv4-mapped*) cuenta como la IPv4. El log del `429` lleva solo el nombre del límite y los segundos de espera: **ni la IP ni nada derivado de ella**. El orden del buscador de co-masters **lo fija el servidor**, nunca el `sort` del cliente. El contador es **por instancia**. Un endpoint nuevo que cualquier `Player` pueda repetir llega con su límite: los comentarios de F5 «Comunidad» heredan la regla.

**Pendiente para F6 «Operación»**: con `forward-headers-strategy: native`, Tomcat confía hoy en el `X-Forwarded-For` de **toda la red privada** —su default de `internal-proxies`—, no solo del proxy propio. Cuando se decida la topología (ADR-02 «Stack, herramientas y despliegue» §5 «Un solo `.env.example`, y la topología de producción queda sin decidir»), `server.tomcat.remoteip.internal-proxies` se fija a la IP de ese proxy.

**Por qué.** No existía, y hay superficies que lo piden: el login, que cualquiera puede martillar sin sesión, y las escrituras que cualquier `Player` repite —postularse, y en F5 comentar—. La subida no tiene cuota por usuario (ADR-13 «Archivos: modelo y almacenamiento» §3), y el límite es lo que contiene una ráfaga. Detrás de un proxy, todos los pedidos llegan desde su IP y compartirían un contador; confiar en el header de cualquiera dejaría evadirlo cambiándolo. Va en `application.yml` porque es operación y no una regla de la comunidad que un admin mueva (ADR-17 «Configuración del sistema y auditoría» §3). Se descartaron **Bucket4j**, una dependencia nueva para un contador que Caffeine ya da, y **limitar en el proxy**, porque el despliegue no está decidido (C15 «Despliegue», ADR-02 «Stack, herramientas y despliegue» §5). **Limitación aceptada**: con más de una instancia, cada una cuenta lo suyo, la misma que la caché de roles y `status` (§3). Lo decidió el usuario el 2026-11-18, en F4.4 «Seguridad».

El resto lo decidió el usuario el 2026-11-22, al cerrar F4.4 «Seguridad». **El `/64`**, porque una conexión doméstica tiene un `/64` entero: contando por dirección, bastaba rotarla en cada pedido para no llegar nunca al límite, y además se llenaba el caché de contadores. **Las propuestas de catálogo**, porque llenaban la bandeja de los admins (ADR-12 «Catálogos» §3 «Propone quien escribe una mesa; solo un admin acepta y clasifica, y eso existe desde F1»). **El buscador de co-masters**, porque el mínimo y el tope de resultados (ADR-07 «Mesas: ciclo de vida» §8 «Los masters de una mesa: el orden de los chips es el cargo, y quitar marca la fila») no bastaban: con los 1296 prefijos de dos letras y el orden elegido por el cliente, se volcaba el directorio. **El log sin IP**, porque el hash que se registraba era reversible.

## §12 El schema de la API y Swagger UI no se publican en `prod`

**Decisión.** `application-prod.yml` apaga `springdoc.api-docs.enabled` y `springdoc.swagger-ui.enabled`, y un test lo fija. En `dev` y `test` siguen como están.

**Por qué.** `SecurityConfig` deja públicos `/v3/api-docs/**` y `/swagger-ui/**`, así que en producción cualquiera, sin sesión, leería el mapa entero de la API, administración incluida. En producción nadie lo consume: los tipos del frontend son espejos escritos a mano (ADR-02 «Stack, herramientas y despliegue» §3). Lo decidió el usuario el 2026-11-18, en F4.4 «Seguridad».
