---
adr: 143
estado: Aceptada
fecha: 2026-08-31
---

# ADR-0143: Cómo se prueba el login de Discord sin Discord

## Decisión

**Dos niveles, y ninguno de los dos habla con discord.com.** (a) **Unitario** (`DiscordOAuth2UserServiceTest`): el delegate que trae el user-info se sustituye por un seam de constructor *package-private* y la llamada de guilds —que sí es nuestra— corre por un `RestClient` real contra `MockRestServiceServer`. Cubre los tres desenlaces: miembro, no miembro (`not_guild_member`) y bloqueado (`user_blocked`). (b) **e2e** (`frontend/e2e/discord-login.spec.ts`): el perfil `test` levanta un **Discord falso dentro del propio backend** (`auth/TestDiscordController`) y apunta ahí las cuatro URIs de Discord, así que el handshake real —authorize, token, user-info, guilds, `DiscordOAuth2UserService`, los handlers de éxito y de fallo, la cookie de refresh— corre entero desde un navegador. Para que la cuarta URI se pueda apuntar, `discord.guilds-uri` pasa a ser propiedad de `DiscordProperties` en vez de una constante

## Por qué

Faltaba: `TestLoginController` (#38, E1 Bloque 8) **saltea** el handshake para conseguir sesión barata, con lo cual el camino de login real no lo ejercitaba nadie — y se pagó: un `String.valueOf(getAttribute(...))` resolvía la sobrecarga `valueOf(char[])`, el compilador infería `A = char[]` y el login explotaba con `ClassCastException` en runtime, sin que compilara mal ni fallara ningún test. Playwright no puede interceptar las llamadas backend→Discord porque no pasan por el navegador, así que stubbear desde el frontend no era opción: el falso Discord tiene que vivir del lado del servidor. Se eligió montarlo como controller bajo `@Profile("test")` —mismo patrón ya aceptado para `TestLoginController`, que no se registra fuera de ese perfil y por lo tanto 404ea en dev y prod— antes que un proceso aparte (WireMock), que agregaría una dependencia y un puerto más que arrancar a mano antes de cada corrida. Los dos niveles se quedan: el unitario corre en `./mvnw test` sin Docker ni servidor y es el que ata el contrato de códigos de error que lee `OAuth2LoginFailureHandler`; el e2e es el único que prueba que las piezas están **cableadas** entre sí.
