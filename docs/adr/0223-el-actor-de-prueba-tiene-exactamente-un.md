---
adr: 223
estado: Aceptada
fecha: 2026-09-09
---

# ADR-0223: El actor de prueba tiene exactamente un rol

## Decisión

`POST /auth/test-login` deja de acumular: el actor sale con el rol pedido y **los otros revocados**. Sin banderas es `Player`, con `asMaster` es `Master`, con `asAdmin` es `Admin`. Revocar marca la fila (#25) y restaurar vuelve a marcarla, porque `(user_id, role_id)` es la clave primaria y no puede haber dos filas del mismo par. Evicta la caché de autorización al terminar (#128) o los roles recién quitados siguen vivos lo que dure el TTL

## Por qué

El usuario entró como `master-1` y encontró que su master también era jugador. No era un bug del modelo —un `UserRole` puede ser Master sin ser Player, y su Javadoc ya lo decía— sino del atajo: `findOrCreateByDiscordId` da `Player` a toda cuenta nueva (#38, que **no cambia**: para una persona real es lo correcto) y `test-login` agregaba el rol pedido encima. Para el producto eso está bien; para un fixture no: un actor que es Master **y** Player no puede mostrar lo que ve un master solo, y todo cruce entre contextos queda invisible — que es justamente lo que #222 vino a arreglar y lo que ninguna de las cuatro suites podía ver. Revocar en vez de solo no otorgar es lo que además **repara** una cuenta que una corrida anterior dejó con dos roles, sin tocar la base a mano. Las 33 pruebas e2e pasan sin cambiar un solo actor: cada spec ya usaba un master y un jugador separados.
