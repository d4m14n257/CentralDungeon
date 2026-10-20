---
adr: 254
estado: Aceptada
fecha: 2026-09-14
---

# ADR-0254: Un rol que solo un Owner puede otorgar necesita que las pruebas puedan ser Owner

## Decisión

`TestLoginController` gana `asOwner`, con `Owner` excluyendo `Admin` (#169); el `DevPanel` gana su cuarta fila «Entrar como Owner», detrás de `import.meta.env.DEV`. Y `TestDataService` borra `user_role_changes` y `user_status_changes` —por `user_id` **y** por `changed_by`— antes de borrar los usuarios.

## Por qué

El seed no le da `Owner` a nadie y solo un `Owner` puede otorgarlo (§3), así que **el flujo que la rebanada promete no tenía actor**: sin esto, «un owner asciende a alguien a admin» no era ejecutable ni en Playwright ni a mano. El comentario del propio `DevPanel` ya lo anticipaba. **Lo de `TestDataService` era un bug real, no una comodidad**: las dos tablas nuevas apuntan a `users` dos veces cada una, así que en cuanto un spec ascendía o bloqueaba a una cuenta `e2e-`, el borrado chocaba con la FK — y como la limpieza es una sola transacción, hacía **rollback entero** y la corrida siguiente arrancaba con la base llena. Es el modo de falla que el propio archivo documenta seis veces (#171, #172), y la lección general es que **toda tabla nueva que referencie `users` entra con su línea de limpieza en el mismo commit**, porque el síntoma no aparece donde está la causa.
