---
adr: 24
titulo: Testing
estado: Vigente
actualizada: 2026-10-20
absorbe: [143, 172, 223, 254]
---

# ADR-24: Testing

> Los dobles de prueba del backend que hacen posible probar el login real, armar actores con un rol preciso y dejar la base como estaba. La skill `arquitectura` §5 lo aplica (backend en §5.1, e2e en §5.3) y `entorno-local` cubre cómo se corre. La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 El login de Discord se prueba en dos niveles, y ninguno habla con discord.com

**Decisión.** **Unitario** (`DiscordOAuth2UserServiceTest`): el delegate que trae el user-info se sustituye por un seam de constructor *package-private*, y la llamada de guilds, que es nuestra, corre por un `RestClient` real contra `MockRestServiceServer`; cubre miembro, no miembro (`not_guild_member`) y bloqueado (`user_blocked`). **e2e** (`frontend/e2e/discord-login.spec.ts`): el perfil `test` levanta un **Discord falso dentro del backend** (`auth/TestDiscordController`) y apunta ahí las cuatro URIs de Discord —`discord.guilds-uri` es propiedad de `DiscordProperties` y no una constante, justamente para poder apuntarla—, así que el handshake entero corre desde un navegador. Todo doble de prueba (`TestLoginController`, `TestDataController`, `TestDiscordController`) existe solo bajo `@Profile("test")` y fuera de él da 404.

**Por qué.** `test-login` saltea el handshake para conseguir sesión barata, y el camino real no lo ejercitaba nadie: un `String.valueOf(getAttribute(...))` resolvía la sobrecarga `valueOf(char[])` y el login explotaba en runtime sin que nada fallara antes. Playwright no puede interceptar las llamadas backend→Discord, que no pasan por el navegador, así que el falso tiene que vivir del lado del servidor. Controller bajo perfil y no WireMock, que sumaría una dependencia y un proceso más que arrancar. Se quedan los dos niveles: el unitario corre en `./mvnw test` sin Docker y ata el contrato de códigos que lee `OAuth2LoginFailureHandler`; el e2e es el único que prueba que las piezas están cableadas.

*Antes: #143.*

## §2 El actor de prueba tiene exactamente un rol, también cuando es Owner

**Decisión.** `POST /auth/test-login` no acumula: el actor sale con el rol pedido y **los demás revocados**. Sin banderas es `Player`; con `asMaster`, `Master`; con `asAdmin`, `Admin`; con `asOwner`, `Owner`, que excluye `Admin` (ADR-06 §1). Revocar marca la fila (ADR-03 §7) y restaurar vuelve a marcarla, porque `(user_id, role_id)` es la clave primaria. Al terminar evicta la caché de autorización (ADR-05 §3). El `DevPanel` tiene una fila por cada uno de los cuatro roles (ADR-19 §9).

**Por qué.** `findOrCreateByDiscordId` da `Player` a toda cuenta nueva (ADR-05 §1), que para una persona real es lo correcto, y el atajo agregaba el rol pedido encima. Para un fixture no sirve: un actor que es Master **y** Player no puede mostrar lo que ve un master solo, y todo cruce entre contextos queda invisible. Revocar, y no solo no otorgar, repara además una cuenta que una corrida anterior dejó con dos roles. Sin evictar, los roles quitados siguen vivos lo que dure el TTL. Y `Owner` necesita su bandera porque el seed no se lo da a nadie y solo un `Owner` puede otorgarlo: sin ella, «un owner asciende a alguien a admin» no tenía actor, ni en Playwright ni a mano.

*Antes: #223.*

## §3 Lo que crea un e2e se borra al terminar la corrida, y toda tabla que apunte a `users` entra con su limpieza

**Decisión.** `DELETE /api/v1/test-data/e2e`, solo bajo el perfil `test`, borra en orden de claves foráneas las mesas cuyo nombre contiene «E2E» —o que creó un usuario de prueba— y los usuarios cuyo `discordId` empieza con `e2e-`. **No recibe parámetros**: los patrones están fijos en `TestDataService`. Playwright lo llama en `globalTeardown` y avisa cuántas filas se fueron; si no puede, no rompe la suite. Los specs nombran sus mesas con «E2E» y sus usuarios con `e2e-`, que es lo que los hace borrables. **Toda tabla nueva que referencie `users` entra con su línea de limpieza en el mismo commit**, por cada columna que lo referencie (en `user_role_changes` y `user_status_changes`, por `user_id` **y** por `changed_by`).

**Por qué.** Cada corrida dejaba sus filas, la base de desarrollo se llenó y eso llegó a hacer fallar specs (ADR-07 §9): un test que no limpia lo que ensucia deja de ser repetible. Sin parámetros porque el endpoint también existe en la base de desarrollo del perfil `dev,test`, así que la garantía tiene que estar en el código y no en quien lo llama. La limpieza es una sola transacción: una FK olvidada hace rollback entero y la corrida siguiente arranca con la base llena, con un síntoma que no aparece donde está la causa.

*Antes: #172, #254.*
