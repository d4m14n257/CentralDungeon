# 2.6 Seguridad

> Parte de la skill `arquitectura` (ADR-01 §5). Se movió desde `docs/` en F4.0 y conserva su numeración original, que es la que citan el código y las ADRs.


Flujo: el frontend inicia el login OAuth2 de Discord → Spring Security completa el intercambio → `DiscordOAuth2UserService` verifica membresía al guild, crea el usuario si no existe (con rol `Player`), rechaza si `status = 'Blocked'` → el backend emite un JWT propio → el frontend lo usa como `Authorization: Bearer` en todas las llamadas.

**No hay registro propio: la membresía al servidor de Discord es la puerta de entrada** (ADR-05 §1). Si el usuario autentica pero no es miembro del guild, no se responde con un error seco: se le ofrece la invitación para unirse y solo se corta el login si la declina. El id del guild es configuración (`DiscordProperties`), nunca una constante en el código.

- Filter chain **sin sesión de servidor, salvo la del intercambio OAuth2**: `SessionCreationPolicy.IF_REQUIRED`, porque `oauth2Login` necesita una sesión corta para el `state` del intercambio. Pasado el callback, todo va por JWT. **El contexto de seguridad nunca se guarda en esa sesión** (`RequestAttributeSecurityContextRepository`): guardado ahí, la cookie `JSESSIONID` sola autenticaba cada request sin token y sin volver a leer estado y roles, así que un bloqueo o un rol quitado no tenían efecto hasta que vencía la sesión, y la protección CSRF —solo en `/auth/refresh` y `/auth/logout`— quedaba corta. Lo fija `SessionDoesNotAuthenticateIT`.
- Dos capas de autorización, igual que el modelo de datos: rol global (`users_roles`: **Player / Master / Admin / Owner**) vía `@PreAuthorize("hasRole(...)")`, y rol por mesa (`masters.master_type`: `Primary`/`Secondary`, ADR-06 §3) verificado en el service, porque depende del recurso concreto.
- ⚠️ **Dos «dueños» que no son lo mismo** (ADR-06 §1): el rol global `Owner` es el dueño de la plataforma y puede todo lo que puede `Admin`; el master principal de **una mesa** es `masters.master_type = 'Primary'`. Por eso en el código no se llaman igual: `PlatformRole.OWNER` y `MasterType.Primary` / `Secondary` (ADR-06 §3).
- **Los roles globales son funciones acumulables, no niveles** (ADR-06 §1). Un usuario puede ser `Master` y `Player` a la vez, o `Master` sin ser `Player`. **No hay jerarquía ni herencia**: `Admin` no hereda lo de `Master`, ni `Master` lo de `Player`. La única relación es que **`Owner` puede todo lo que puede `Admin`**, y por eso no se acumulan; `Owner` **no** implica `Player` ni `Master`, así que un `Owner` sin esos roles no juega, no crea mesas ni dirige (regla dura 5 «Cuatro roles, `Admin` y `Owner` excluyentes, sin `RoleHierarchy`»). En consecuencia **no se registra un `RoleHierarchy`** y cada endpoint enumera explícitamente los roles que lo alcanzan —`@PreAuthorize("hasAnyRole('ADMIN','OWNER')")` en la administración, incluida la mesa que un admin crea sin dirigirla (ADR-06 §7), y `hasRole('MASTER')` en crear una mesa propia—, nunca el "mínimo" de una escala que no existe. Lo que se autoriza por pertenencia a una mesa no lleva rol en la anotación: lo decide el service (regla dura 17 «El rol no es la pertenencia; el actor sale del token», ADR-06 §4).
- **Una cuenta `Blocked` con un access token o una cookie de refresh válidos recibe `401` con el código `ACCOUNT_BLOCKED`** en su `ProblemDetail`, en todo endpoint protegido y en `POST /auth/refresh`; nunca un `403`, porque el frontend lo trata como un fin de sesión con motivo (ADR-05 «Seguridad, sesión y login» §3 «El JWT afirma identidad; roles y `status` se leen de la base con una caché de 60 s»). En los endpoints lo ponen `JwtAuthenticationFilter`, que ya relee el `status` en cada request, y el entry point de autenticación. Los demás `401` —sin token, token vencido, cookie inválida— no llevan ese código.
- **Fuera de un contexto, el `403` es solo de lo que pide un rol**: la superficie de administración y crear una mesa. El otro `403`, el de un recurso concreto que no es del actor, sigue siendo de la pertenencia, y solo cuando el actor ve el recurso: si no lo ve, es `404` (abajo, «Negar no confirma»). **Las listas propias** —`/game-tables/mine`, `/game-tables/mine/history`, `/game-tables/managed`, `/registrations/mine`, `/master/dashboard`— filtran por el actor y responden `200` a toda sesión, vacías si no tiene nada: el contexto donde la interfaz las muestra no es una puerta del backend (ADR-20 «Navegación, contextos y ayuda» §1 «Se cambia de contexto explícitamente, y el contexto es organización de UI, no seguridad»). Una lista propia nueva hace lo mismo.
- **Ningún endpoint de mesas es anónimo**: el explorador y el detalle de una mesa, que ADR-06 §5 y §6 llaman «públicos», son de cualquier sesión, y sin token responden `401` (§2.5).
- CORS restringido al origen del frontend por perfil, nunca `*`.
- Ningún endpoint acepta el `user_id` del usuario autenticado como parámetro de ruta — sale del token. Este era el agujero central del backend Node.

**Los dos escalados, que son problemas distintos y se arreglan en lugares distintos:**

**Horizontal — el rol no es la pertenencia** (ADR-05 §5). `hasRole('MASTER')` afirma "es master de algo", no "es master de *esta* mesa". Con el actor sacado del token, el id que el atacante todavía controla es el **del recurso**. Por eso:

> **Toda lectura o mutación de un recurso concreto filtra por el actor.** O el actor entra en el `WHERE` (`findByGameTable_IdAndUser_IdAndStatus`, `existsByGameTable_IdAndUser_IdAndStatusIn`), o el service comprueba la pertenencia **antes** de tocar nada y lanza si no corresponde. Nunca un `findById(id)` seguido de `save()` sin verificar.

Es el agujero que tenía el Node: `UPDATE Tables SET … WHERE id = ?`, sin un solo predicado de pertenencia. Quien conociera el id de una mesa podía editarla o borrarla. Y ojo: que los ids sean impredecibles (§4.1, UUID v7) es **defensa en profundidad, nunca autorización** — un id filtrado en un link no puede ser lo único que separa a alguien de un recurso ajeno.

**Negar no confirma** (ADR-05 «Seguridad, sesión y login» §10 «Negar no confirma: lo que el actor no puede ver es `404`, y `403` es solo de lo que ve y no puede hacer»). Sobre todo recurso con id, el service comprueba en este orden y responde con la primera que falla:

> 1. **Existe y el actor lo ve** → si no, `404` (una fila `Deleted` no existe).
> 2. **Puede hacer esa acción** → si no, `403`.
> 3. **El estado la admite** → si no, `409`.

| Recurso | Lo ve quien… |
|---|---|
| Mesa | pasa `TableVisibilityService.canSee` (`requireVisible` lanza el `404`) |
| Sesión, petición, postulación | ve su mesa y, además, la dirige o es el destinatario o el dueño |
| Archivo | pasa `FileService.requireReadable` |
| Notificación | es su dueño |
| Perfil | pasa `ProfileVisibilityService.requireVisible` |

La forma es la de `GameTableService.getStatusHistory`: a quien no dirige la mesa se le exige primero `requireVisible`, y recién después se lanza el `ForbiddenActionException`. Un `ForbiddenActionException` lanzado sin haber comprobado antes la visibilidad es un bug: le confirma la existencia a quien no la ve. El veto, la mesa borrada y el perfil caducado son casos de esta regla (ADR-09 §4, ADR-07 §6, ADR-10 §3).

**Temporal — el token es una foto vieja** (ADR-05 §3). Si los roles viajan como claims, el JWT afirma lo que era cierto cuando se emitió: un admin degradado sigue siendo admin, y alguien marcado `Blocked` (ADR-05 §7) sigue entrando, hasta que el token expire. Por eso:

> **El JWT afirma identidad, no autorización.** Lleva el `sub` y poco más. Los roles y el `status` se leen de la base en cada request. Un `JwtAuthenticationFilter` resuelve el id, carga el usuario y arma el `Authentication` con las autoridades reales de ese momento.

Para que eso no sea una consulta por petición, la carga va cacheada con **Caffeine** (ADR-05 §3): `expireAfterWrite = 60 s`, `maximumSize = 10 000`, más `@CacheEvict` explícito al bloquear a alguien o cambiarle los roles. **El TTL es la ventana de revocación**, no un número de rendimiento: la evicción explícita hace el efecto inmediato y los 60 s son la red de seguridad para cualquier camino que se olvide de evictar. La caché es **por JVM** — con más de una instancia dos cachés pueden discrepar y el `@CacheEvict` solo limpia la local, la misma limitación que el broker STOMP en memoria (ADR-18 §2).

Es el patrón que sí vale la pena rescatar del intento en Java, que ya lo hacía bien: el token llevaba solo `id` y el filtro hacía `loadUserById` en cada petición.

**La autorización se declara en el controller, nunca en una lista de rutas aparte** (ADR-05 §4). El intento previo la tenía en un `Routes.java` con listas de endpoints por rol, y falló de la peor manera: cinco de las seis rutas estaban escritas **sin barra inicial**, así que no matcheaban, caían en `anyRequest().authenticated()` y **cualquier usuario autenticado alcanzaba los endpoints de master y de admin**. La causa de fondo no es el typo: es que la regla vivía lejos del endpoint que protege y nada obligaba a que coincidieran. En el `SecurityConfig` solo queda lo transversal —stateless, CORS, qué es público—; el permiso concreto vive pegado al método.

⚠️ Y por lo mismo, **`@PreAuthorize` va siempre en el método concreto**: anotarlo en una interfaz o en una superclase genérica es un riesgo documentado de bypass (CVE-2025-41248, septiembre de 2025).

**Tokens: tres, y no se confunden** (ADR-05 §2).

| Token | Emisor | Para qué | Vida |
|---|---|---|---|
| Access de Discord | Discord | Solo durante el login: `identify` y verificar membresía al guild (ADR-05 §1) | Se **descarta** al terminar el callback |
| Access propio | Spring | Autenticar cada llamada a la API | Corto (~15 min) |
| Refresh propio | Spring | Renovar el access | Largo, en cookie `httpOnly` + `SameSite=Strict`; rota dentro de una familia de `refresh_tokens` y se revoca |

El de Discord no se guarda: después del login no se usa para nada, y conservarlo obligaría a mantener un segundo ciclo de refresh con su propia caducidad para una capacidad que v1 no tiene. Lo que sí requiere la integración futura (ADR-05 §7) es un **bot token**, que es de la aplicación y no del usuario.

**El refresh es el punto de re-afirmación**: al renovar se releen `status` y roles desde la base. Es el momento en que el sistema vuelve a preguntar "¿esta persona sigue estando bien?" — lo que el legacy nunca hacía.

**El refresh es revocable** (ADR-05 «Seguridad, sesión y login» §2 «Tres tokens: el de Discord se descarta, el access es corto y el refresh rota en una familia revocable»). Cada uno tiene su fila en `refresh_tokens` —el **hash** de su `jti`, nunca el token, con `family_id`, `user_id`, `expires_at` y `revoked_at`—, y el login abre una familia:

- **renovar** revoca el presentado y emite el siguiente en la misma familia;
- **ventana de gracia de 30 s**: presentar uno rotado hace menos de 30 s, con la familia todavía con un token vivo, **no es reuso**: se revoca ese sucesor —que nunca llegó al navegador— y se emite otro de la misma familia;
- **fuera de la ventana, o con la familia sin tokens vivos** (la cerró un logout o un reuso), presentar uno ya rotado o revocado es un reuso: se revoca la familia entera y se responde `401`;
- **el logout** revoca la familia, además de expirar la cookie.

Lo hace `AuthService` (`openSession`, `refresh`, `logout`); `JwtService` solo firma y verifica, y un refresh firmado que no tiene fila es `401`. **La ventana existe por la recarga**: recargar la página mientras corre el refresh aborta en el navegador una respuesta que el servidor ya rotó, la cookie nueva no llega y la página siguiente presenta el token rotado; sin ventana se leía como robo y cerraba la sesión. El ladrón solo gana esos 30 s, y solo detrás de una rotación legítima (como Auth0 y Okta); 10 s se descartaron porque una red lenta vuelve a cerrar la sesión (ADR-05 «Seguridad, sesión y login» §2 «Tres tokens: el de Discord se descarta, el access es corto y el refresh rota en una familia revocable»). **Dos renovaciones simultáneas con el mismo token** se serializan con el lock de la fila presentada (`findByJtiHashForUpdate`): la primera rota y la segunda, al despertar, encuentra el token rotado dentro de la ventana y revoca el sucesor que la primera acaba de recibir. Evitar la carrera sigue siendo trabajo del cliente: `refreshSession` comparte la renovación en curso dentro de la pestaña y toma un `navigator.locks` entre pestañas; la que espera el lock igualmente envía su refresh, porque la cookie `httpOnly` es una sola para todo el navegador y la respuesta de la primera ya la reemplazó. La transacción no hace rollback ante `UnauthorizedException`, para que la revocación del reuso quede escrita. Una cuenta `Blocked` recibe `ACCOUNT_BLOCKED` sin gastar el token. **Las filas inservibles las purga `RefreshTokenPurgeService`**, un job diario (`0 0 6 * * *`, después de los otros dos diarios) que borra, en una sola sentencia, las filas vencidas hace más de 7 días y las revocadas cuyo `expires_at` ya pasó. Una fila vencida ya no autentica —el JWT se rechaza por su `exp` antes de mirar la tabla—, así que el margen de 7 días es solo un rastro corto de auditoría; una familia revocada sigue detectando reuso mientras su token podría seguir vivo, porque esas filas no se tocan hasta que vencen. Lo fijan `RefreshTokenPurgeServiceTest`, `RefreshTokenPurgeIT` y `ProductionWiringIT`. Lo fijan `RefreshTokenRotationIT` y `AuthServiceTest`.

**La sesión HTTP del intercambio OAuth2 muere en el callback**: `OAuth2LoginSuccessHandler` la invalida, y `SecurityConfig` le da a `oauth2Login` un `OAuth2AuthorizedClientRepository` que no guarda nada — el de Boot por defecto dejaba el `OAuth2AuthorizedClient`, con el access token de Discord, en un `InMemoryOAuth2AuthorizedClientService` indexado por persona y sin borrarlo nunca. Lo fija `DiscordTokenIsDiscardedIT`.

Del lado del cliente, `api/client.ts` hace **un solo refresh a la vez** (`arquitectura` §3.3, «Sesión»): dos renovaciones simultáneas presentarían el mismo token y la segunda gastaría la ventana de gracia revocando el sucesor de la primera. **Un logout que falla en el servidor se reintenta una vez**; si vuelve a fallar, el frontend limpia igual la sesión local y avisa que la sesión no se cerró en el servidor —cerrar el navegador o reintentar—, porque en una computadora compartida la cookie seguía viva y el siguiente entraba con la cuenta anterior (ADR-05 «Seguridad, sesión y login» §2 «Tres tokens: el de Discord se descarta, el access es corto y el refresh rota en una familia revocable»).

**CSRF: activo solo en `/auth/refresh` y `/auth/logout`** (ADR-05 «Seguridad, sesión y login» §2 «Tres tokens: el de Discord se descarta, el access es corto y el refresh rota en una familia revocable»). `SecurityConfig` lo arma con `csrf.spa().requireCsrfProtectionMatcher(...)` y un matcher que cubre `POST /auth/refresh` y `POST /auth/logout`, y el frontend manda el header en los dos: el resto de la API no exige token CSRF, y eso es correcto, no un descuido: se autentica con `Authorization: Bearer`, un header que el navegador nunca adjunta por su cuenta, así que no existe la credencial automática de la que vive el ataque. Refresh y logout son los únicos endpoints que se autentican con **cookie**, y las cookies sí viajan solas hacia su destino sin importar quién originó la petición.

Qué se protege exactamente: un sitio ajeno no podría leer la respuesta —lo impide CORS—, así que no hay robo de sesión; lo que sí podría es **forzar la rotación** del refresh o **el logout**, y cerrarle la sesión al usuario: con la familia revocable, una rotación forzada deja al cliente legítimo con un token ya rotado, que pasada la ventana de gracia el servidor lee como reuso. El `SameSite=Strict` de ADR-05 §2 ya bloquea eso, pero lo aplica el **navegador**: si el agente lo ignora, del lado del servidor no se entera nadie. El token CSRF es la comprobación que sí ocurre acá.
- **Filtro de visibilidad por veto**: toda lectura de mesas excluye aquellas donde el usuario tenga una solicitud `Blocked`, y el detalle por id responde `404`, no `403` (ADR-09 §4).

**Límites de frecuencia** (ADR-05 «Seguridad, sesión y login» §11 «Las puertas que cualquiera puede repetir tienen un límite por ventana, contado en proceso»). Un filtro propio sobre Caffeine, sin dependencia nueva, con contador por instancia:

| Límite (`app.rate-limit.*`) | Endpoint | Límite | Clave |
|---|---|---|---|
| `login` | Login con Discord y su callback (`/oauth2/authorization/**` y `/login/oauth2/code/**`, **un solo contador**: un login completo gasta dos) | 10 por minuto | IP |
| `refresh` | `POST /auth/refresh` | 30 por minuto | IP |
| `upload` | Toda subida de archivos (`POST /files` y `POST /admin/files`, un solo contador) | 20 por hora | usuario del token |
| `registration` | Postularse (`POST /game-tables/{id}/registrations`) | 10 por hora | usuario del token |
| `catalog-proposal` | Proponer un valor de catálogo (`POST /tags`, `/systems` y `/platforms`, **un solo contador**) | 20 por hora | usuario del token |
| `master-candidates` | El buscador de co-masters (`GET /game-tables/{id}/master-candidates`) | 30 por minuto | usuario del token |

**La clave IP**: una IPv4 cuenta por la dirección completa; una IPv6, por su prefijo `/64`, porque una conexión doméstica tiene un `/64` entero y rotar la dirección en cada pedido evadía el límite y llenaba el caché de contadores; una IPv4-mapped cuenta como la IPv4.

- Cómo está construido: `RateLimitFilters` arma **dos instancias** de `RateLimitFilter` sobre `FixedWindowLimiter` (ventana fija alineada al epoch, un `Cache` de Caffeine por límite, acotado en claves). La de IP va **antes de `CsrfFilter`**, así que también cuenta el refresh o el login que después se rechazan; la de usuario va **después de `JwtAuthenticationFilter`** y toma el actor del `SecurityContext`: un pedido anónimo no cuenta y recibe su `401`. No son beans `Filter`, para que Boot no los monte también fuera de la cadena.
- Pasarse responde `429` con el código `RATE_LIMITED` en el `ProblemDetail` —escrito a mano, como el `401` del entry point— y el header `Retry-After` en segundos, repetido en `errorParams.retryAfterSeconds` para que el frontend diga «en N min» sin leer headers. El log es un `WARN` con **solo el nombre del límite y los segundos de espera**: ni la IP ni un hash de la clave, que era reversible.
- Los límites viven en `application.yml` (`app.rate-limit.*`, `RateLimitProperties`), y `application-test.yml` los sube a 100000; nunca en `system_settings` (ADR-17 §3). `RateLimitProperties` rechaza al arrancar un `requests` menor que 1 o una `window` menor que un segundo.
- En `prod`, `server.forward-headers-strategy: native`: Tomcat toma la IP de `X-Forwarded-For` **solo si el pedido viene de un proxy interno** (loopback y rangos privados, su default). El proxy tiene que vivir en el host o en la red privada. **Pendiente para F6 «Operación»**: ese default confía en toda la red privada, no solo en el proxy propio; cuando se decida la topología, se fija `server.tomcat.remoteip.internal-proxies` a la IP del proxy (`arquitectura` §8.2).
- La IP es `request.getRemoteAddr()` con `server.forward-headers-strategy` activo: solo se confía en lo que dice el proxy propio, y nunca se lee `X-Forwarded-For` a mano.
- **Un endpoint nuevo que cualquier `Player` pueda repetir llega con su límite**, como los comentarios de F5 «Comunidad».

**El buscador de co-masters no vuelca el directorio** (ADR-07 «Mesas: ciclo de vida» §8 «Los masters de una mesa: el orden de los chips es el cargo, y quitar marca la fila»). `GET /game-tables/{id}/master-candidates`, el picker del `Primary` para sumar co-masters, exige un **mínimo de caracteres** en `q` y devuelve **como mucho un tope de resultados**, pida el `size` que pida el cliente: acotarlo a la mesa (ADR-22 «Buscadores» §5 «El buscador de personas para sumar co-masters se acota a la mesa») decide quién busca, no cuánto se lleva, y cada fila trae el usuario de Discord. Viven en `UserService.search`, que también usa `GET /users/search` de admin: `MIN_SEARCH_LENGTH = 2` (cada valor de la consulta; con menos, página vacía y sin tocar la base, nunca `400`, porque el parser no falla mientras se tipea) y `MAX_SEARCH_RESULTS = 20`, siempre la página 0 para que no se pueda volcar de a 20. **El orden es del servidor**, siempre `discordUsername, id`, y el `sort` del cliente se ignora, y la búsqueda lleva el límite `master-candidates` de 30 por minuto: con los 1296 prefijos de dos letras y el orden elegido por el cliente, el directorio se volcaba entero (ADR-05 «Seguridad, sesión y login» §11 «Las puertas que cualquiera puede repetir tienen un límite por ventana, contado en proceso»). El directorio completo de admin es `/admin/users`, por `AdminUserService`, y no se acota.

**Swagger y `/v3/api-docs` no existen en `prod`** (ADR-05 «Seguridad, sesión y login» §12 «El schema de la API y Swagger UI no se publican en `prod`»): `application-prod.yml` los apaga, porque `SecurityConfig` los deja públicos.
