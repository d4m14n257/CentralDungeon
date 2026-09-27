# 2.6 Seguridad

> Parte de la skill `arquitectura-backend`. Se movió desde `docs/` en F4.0 y conserva su numeración original, que es la que citan el código y `decisiones.md`.


Flujo: el frontend inicia el login OAuth2 de Discord → Spring Security completa el intercambio → `DiscordOAuth2UserService` verifica membresía al guild, crea el usuario si no existe (con rol `Player`), rechaza si `status = 'Blocked'` → el backend emite un JWT propio → el frontend lo usa como `Authorization: Bearer` en todas las llamadas.

**No hay registro propio: la membresía al servidor de Discord es la puerta de entrada** (`decisiones.md` #38). Si el usuario autentica pero no es miembro del guild, no se responde con un error seco: se le ofrece la invitación para unirse y solo se corta el login si la declina. El id del guild es configuración (`DiscordProperties`), nunca una constante en el código.

- Filter chain **stateless**, sin sesión de servidor.
- Dos capas de autorización, igual que el modelo de datos: rol global (`users_roles`: **Player / Master / Admin / Owner**) vía `@PreAuthorize("hasRole(...)")`, y rol por mesa (`masters.master_type`: Owner/Master) verificado en el service, porque depende del recurso concreto.
- ⚠️ **Los dos `Owner` son cosas distintas** (`decisiones.md` #67): el rol global `Owner` es el dueño de la plataforma y puede todo; `masters.master_type = 'Owner'` es el dueño de **una mesa**. En el código no pueden llamarse igual — `PlatformRole.OWNER` y `MasterType.OWNER`, o se renombra el segundo.
- **Los roles globales son funciones acumulables, no niveles** (`decisiones.md` #37, #67). Un usuario puede ser `Master` y `Player` a la vez, o `Master` sin ser `Player`. **No hay jerarquía ni herencia**: `Admin` no hereda lo de `Master`, ni `Master` lo de `Player`, y `Owner` puede todo por definición, no por heredar de `Admin`. En consecuencia **no se registra un `RoleHierarchy`** y cada endpoint enumera explícitamente los roles que lo alcanzan — `@PreAuthorize("hasAnyRole('MASTER','ADMIN')")`, nunca el "mínimo" de una escala que no existe.
- CORS restringido al origen del frontend por perfil, nunca `*`.
- Ningún endpoint acepta el `user_id` del usuario autenticado como parámetro de ruta — sale del token. Este era el agujero central del backend Node.

**Los dos escalados, que son problemas distintos y se arreglan en lugares distintos:**

**Horizontal — el rol no es la pertenencia** (#121). `hasRole('MASTER')` afirma "es master de algo", no "es master de *esta* mesa". Con el actor sacado del token, el id que el atacante todavía controla es el **del recurso**. Por eso:

> **Toda lectura o mutación de un recurso concreto filtra por el actor.** O el actor entra en el `WHERE` (`findByIdAndOwnerId`), o el service comprueba la pertenencia **antes** de tocar nada y lanza si no corresponde. Nunca un `findById(id)` seguido de `save()` sin verificar.

Es el agujero que tenía el Node: `UPDATE Tables SET … WHERE id = ?`, sin un solo predicado de pertenencia. Quien conociera el id de una mesa podía editarla o borrarla. Y ojo: que los ids sean impredecibles (`modelo-datos.md`, UUID v7) es **defensa en profundidad, nunca autorización** — un id filtrado en un link no puede ser lo único que separa a alguien de un recurso ajeno.

**Temporal — el token es una foto vieja** (#122). Si los roles viajan como claims, el JWT afirma lo que era cierto cuando se emitió: un admin degradado sigue siendo admin, y alguien marcado `Blocked` (#84, #86) sigue entrando, hasta que el token expire. Por eso:

> **El JWT afirma identidad, no autorización.** Lleva el `sub` y poco más. Los roles y el `status` se leen de la base en cada request. Un `JwtAuthenticationFilter` resuelve el id, carga el usuario y arma el `Authentication` con las autoridades reales de ese momento.

Para que eso no sea una consulta por petición, la carga va cacheada con **Caffeine** (#128): `expireAfterWrite = 60 s`, `maximumSize = 10 000`, más `@CacheEvict` explícito al bloquear a alguien o cambiarle los roles. **El TTL es la ventana de revocación**, no un número de rendimiento: la evicción explícita hace el efecto inmediato y los 60 s son la red de seguridad para cualquier camino que se olvide de evictar. La caché es **por JVM** — con más de una instancia dos cachés pueden discrepar y el `@CacheEvict` solo limpia la local, la misma limitación que el broker STOMP en memoria (#101).

Es el patrón que sí vale la pena rescatar del intento en Java, que ya lo hacía bien: el token llevaba solo `id` y el filtro hacía `loadUserById` en cada petición.

**La autorización se declara en el controller, nunca en una lista de rutas aparte** (#123). El intento previo la tenía en un `Routes.java` con listas de endpoints por rol, y falló de la peor manera: cinco de las seis rutas estaban escritas **sin barra inicial**, así que no matcheaban, caían en `anyRequest().authenticated()` y **cualquier usuario autenticado alcanzaba los endpoints de master y de admin**. La causa de fondo no es el typo: es que la regla vivía lejos del endpoint que protege y nada obligaba a que coincidieran. En el `SecurityConfig` solo queda lo transversal —stateless, CORS, qué es público—; el permiso concreto vive pegado al método.

⚠️ Y por lo mismo, **`@PreAuthorize` va siempre en el método concreto**: anotarlo en una interfaz o en una superclase genérica es un riesgo documentado de bypass (CVE-2025-41248, septiembre de 2025).

**Tokens: tres, y no se confunden** (#125).

| Token | Emisor | Para qué | Vida |
|---|---|---|---|
| Access de Discord | Discord | Solo durante el login: `identify` y verificar membresía al guild (#38) | Se **descarta** al terminar el callback |
| Access propio | Spring | Autenticar cada llamada a la API | Corto (~15 min) |
| Refresh propio | Spring | Renovar el access | Largo, rotativo, en cookie `httpOnly` + `SameSite=Strict` |

El de Discord no se guarda: después del login no se usa para nada, y conservarlo obligaría a mantener un segundo ciclo de refresh con su propia caducidad para una capacidad que v1 no tiene. Lo que sí requiere la integración futura (#88) es un **bot token**, que es de la aplicación y no del usuario.

**El refresh es el punto de re-afirmación**: al renovar se releen `status` y roles desde la base. Es el momento en que el sistema vuelve a preguntar "¿esta persona sigue estando bien?" — lo que el legacy nunca hacía.

**CSRF: activo solo en `/auth/refresh`** (#127). El resto de la API va con `csrf.disable()`, y eso es correcto, no un descuido: se autentica con `Authorization: Bearer`, un header que el navegador nunca adjunta por su cuenta, así que no existe la credencial automática de la que vive el ataque. El refresh es el único endpoint que se autentica con **cookie**, y las cookies sí viajan solas hacia su destino sin importar quién originó la petición.

Qué se protege exactamente: un sitio ajeno no podría leer la respuesta —lo impide CORS—, así que no hay robo de sesión; lo que sí podría es **forzar la rotación** del refresh y dejar al cliente legítimo con un token viejo, cerrándole la sesión al usuario y disparando falsas alarmas de reuso. El `SameSite=Strict` de #125 ya bloquea eso, pero lo aplica el **navegador**: si el agente lo ignora, del lado del servidor no se entera nadie. El token CSRF es la comprobación que sí ocurre acá.
- **Filtro de visibilidad por veto**: toda lectura de mesas excluye aquellas donde el usuario tenga una solicitud `Blocked`, y el detalle por id responde `404`, no `403` (`decisiones.md` → *Ciclo de vida de la mesa*).
