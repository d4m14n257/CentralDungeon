---
adr: 5
titulo: Seguridad, sesión y login
estado: Vigente
actualizada: 2026-10-20
absorbe: [38, 83, 84, 85, 86, 88, 121, 122, 123, 125, 127, 128, 147, 148, 186]
---

# ADR-05: Seguridad, sesión y login

> Cómo se entra, qué afirma la sesión, dónde se autoriza, cómo se protege cada recurso y el texto enriquecido, y qué depende de Discord. Lo aplica `arquitectura` §2.6 con la regla dura 17, y lo revisa el agente `revisor-seguridad`; esta ADR dice *qué* se decidió y *por qué*.

## §1 Se entra con Discord y siendo miembro del servidor

**Decisión.** El login requiere cuenta de Discord y membresía al servidor de CentralDungeon; no hay registro propio. Si la persona no es miembro, primero se le ofrece unirse, y solo si declina no hay login. Todo usuario nuevo se crea con rol `Player`, desde el service (`arquitectura` §4.5).

**Por qué.** Es la puerta de la comunidad: no debería haber usuarios que no estén en el servidor. No se rechaza con un error seco porque quien llega sin ser miembro casi siempre quiere serlo; la invitación va antes del corte.

*Antes: #38.*

## §2 Tres tokens: el de Discord se descarta, el access es corto y el refresh rota en una cookie

**Decisión.** El token OAuth de Discord se descarta al terminar el callback. La sesión es propia: un **access** corto (~15 min) en memoria del cliente y un **refresh** largo y rotativo en cookie `httpOnly` + `SameSite=Strict`. El refresh es el punto donde se re-afirman `status` y roles. **CSRF está activo únicamente en `/auth/refresh`**: `csrf.spa().requireCsrfProtectionMatcher(...)` con un matcher que solo cubre `POST /auth/refresh`; el resto de la API no pide token CSRF.

**Por qué.** El token de Discord contesta dos preguntas durante el login —quién sos y si sos miembro— y después no se usa; guardarlo exigiría otro ciclo de refresh para una capacidad que v1 no tiene, y la integración futura necesita un *bot token*, que es de la aplicación (§7). La API se autentica con `Authorization: Bearer`, que el navegador nunca adjunta por su cuenta, así que no exigir CSRF ahí es correcto. El refresh es el único endpoint con cookie, que sí viaja sola: un sitio ajeno no puede leer la respuesta (CORS), pero sí forzar la rotación y cerrarle la sesión al usuario legítimo. `SameSite=Strict` lo bloquea, pero lo aplica el navegador; el token CSRF es la comprobación que ocurre en el servidor.

*Antes: #125, #127.*

## §3 El JWT afirma identidad; roles y `status` se leen de la base con una caché de 60 s

**Decisión.** El token lleva el `sub` y poco más. Roles y `status` se leen de la base en cada request, a través de Caffeine en proceso vía Spring Cache: `expireAfterWrite = 60 s`, `maximumSize = 10 000`, y `@CacheEvict` explícito al bloquear a alguien o cambiarle los roles.

**Por qué.** Un JWT con roles es una foto del momento en que se emitió, y sin revocación un admin degradado o alguien `Blocked` seguiría entrando hasta que expire. Leyendo de la base, un baneo o una degradación tienen efecto inmediato, y es una lectura por PK cacheada. **El TTL es la ventana de revocación**: 60 s y no cinco minutos porque `Owner` da acceso a la auditoría (ADR-17 §1) y al borrado físico (ADR-13 §8); la evicción explícita hace el efecto inmediato y el TTL es la red para el camino que se olvide de evictar. **Limitación aceptada**: la caché es por JVM; con más de una instancia `@CacheEvict` solo limpia la local, la misma restricción que el broker STOMP en memoria (ADR-18 §2), y hará falta caché compartida o el TTL será la única garantía.

*Antes: #122, #128.*

## §4 La autorización se declara en el método del controller

**Decisión.** Cada método de controller declara su `@PreAuthorize`, enumerando sus roles. `SecurityConfig` tiene solo lo transversal: sin sesión de servidor salvo la corta que necesita el intercambio OAuth2 (`IF_REQUIRED`), CORS y qué es público. **Nunca una lista de rutas por rol**, y nunca una anotación heredada (ADR-04 §2).

**Por qué.** El intento previo tenía un `Routes.java` con endpoints por rol, y cinco de sus seis rutas estaban escritas sin barra inicial: no matcheaban nada, caían en `anyRequest().authenticated()` y cualquier usuario autenticado alcanzaba los endpoints de master y de admin. La causa de fondo es la distancia: la regla vivía en otro archivo y nada obligaba a que coincidiera con el mapping. Pegada al método, el permiso se lee junto al endpoint y no queda huérfano; heredada, es el bypass de CVE-2025-41248.

*Antes: #123.*

## §5 Toda lectura o mutación de un recurso concreto filtra por el actor

**Decisión.** O el actor entra en el `WHERE`, o el service verifica la pertenencia antes de tocar nada; nunca `findById(id)` + `save()` sin comprobar. El actor sale del token, nunca de la URL (regla dura 17).

**Por qué.** Es el agujero del Node: `UPDATE Tables SET … WHERE id = ?` sin un predicado de pertenencia, así que cualquiera con el id de una mesa podía editarla o borrarla. `hasRole('MASTER')` afirma «es master de algo», no «de *esta* mesa», y la segunda comprobación no se puede delegar en Spring Security (ADR-06 §4). Que los ids sean impredecibles (ADR-03 §4) es defensa en profundidad: un id filtrado en un link no puede ser lo único que proteja un recurso.

*Antes: #121.*

## §6 El texto enriquecido se sanitiza con OWASP, por lista blanca, al guardar y al servir

**Decisión.** `owasp-java-html-sanitizer` detrás de `RichTextSanitizer` en `common/text/`, con lista blanca: `p`, `br`, `strong`, `em`, `u`, `s`, `code`, `pre`, `blockquote`, `h1`–`h3`, `ul`/`ol`/`li` y `a` con `href` `http(s)` y `rel=nofollow`. Todo lo demás cae, incluidos `img`, `style` y cualquier `on*`. Se aplica al guardar y al servir sobre `description`, `permitted` y `requirements` (ADR-03 §8). Nulo entra, nulo sale, y lo que queda vacío tras sanitizar vuelve nulo.

**Por qué.** Es la superficie de XSS más directa del sistema, y escapar a mano es la forma habitual de publicar un XSS: entidades, protocolos de `href`, atributos de evento y comentarios condicionales, cualquiera alcanza. Lista blanca porque una etiqueta que nadie previó tiene que ser inofensiva por defecto. Al servir además de al guardar porque una fila escrita antes de la puerta, o por un camino que la saltee, llega igual al navegador.

*Antes: #186.*

## §7 Irse del servidor no borra nada; ser baneado bloquea, y en v1 lo marca un admin

**Decisión.** Salir del servidor de Discord conserva los datos —karma, historial, mesas— por si la persona vuelve. Ser baneado del servidor implica quedar baneado del sistema, sin retorno: un admin le pone `status = 'Blocked'` a mano desde el panel de moderación (ADR-11 §7). Automatizarlo queda para el bot. Con solo OAuth2 (v1) se puede identificar al usuario y verificar si es miembro; **necesitan bot** detectar baneos, crear y borrar canales por mesa y leer la lista de miembros, y se harán juntas. Enlazar a un canal existente no necesita bot: es un link.

**Por qué.** Sin la distinción, salirse un mes equivaldría a perder dos años de historial, y quien fue expulsado de la comunidad volvería a entrar. OAuth2 solo responde si alguien es miembro: el que se fue y el baneado devuelven lo mismo, y consultar baneos exige un bot con permisos, fuera de v1. Sin la marca manual, la distinción no la aplicaría nadie. Baneos, canales y su apertura según el estado de la mesa son la misma pieza de trabajo: un bot con permisos sobre el servidor.

*Antes: #84, #86, #88.*

## §8 Depender de Discord es un riesgo asumido, y una cuenta perdida se migra a mano

**Decisión.** No hay vía alternativa de login: si Discord cambia su OAuth o corta el acceso, se responde como emergencia. Tampoco hay recuperación de cuenta ni self-service: si alguien pierde su cuenta de Discord, lo único posible es que el owner migre sus datos a la cuenta nueva, como operación de administración auditada (ADR-13 §8, ADR-06 §1).

**Por qué.** No hay contraseña, email ni ningún otro dato propio con que reconocer a alguien, y construir un sistema de identidad paralelo por si acaso no se justifica. La migración manual cubre el caso real: una persona conocida de la comunidad que vuelve con otra cuenta. Queda escrito para que sea una decisión y no una sorpresa.

*Antes: #83, #85.*

## §9 El retorno de OAuth tiene cinco estados en una misma tarjeta, y todo error tiene salida a `/login`

**Decisión.** `OAuthCallbackPage` muestra sus cinco estados —verificando, no es miembro, listo, declinó la invitación y **cuenta bloqueada**— con un mismo `CallbackCard` (card + badge) y el wordmark arriba, igual que en `/login`; el diseño vive en `sc_callback()` de `design/build.py`. Bloqueado usa el tono `canceled` y no ofrece reintentar. Los tres estados de error (no es miembro, bloqueado, error genérico) tienen **«Volver al login»** (`Link` de React Router): `ghost` como tercera acción en no-es-miembro, detrás de unirse y «Ya me uní, reintentar»; `outline` y única en bloqueado y en el error genérico.

**Por qué.** `status = 'Blocked'` no tiene que ver con Discord ni con el guild, y no tenía pantalla. Los estados de la misma pantalla son la misma forma repetida, así que van en un solo componente para no quedar inconsistentes uno al lado del otro. La pantalla no tiene header, y el wordmark es la única marca de que se sigue en la aplicación. Sin la salida, quien llegaba a un error se quedaba sin camino: en bloqueado reintentar no cambia el `status`, así que volver al login es la única acción con sentido.

*Antes: #147, #148.*
