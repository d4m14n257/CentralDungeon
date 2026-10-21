---
adr: 10
titulo: Perfiles
estado: Vigente
actualizada: 2026-10-20
absorbe: [41, 44, 47, 134, 248, 249, 285]
---

# ADR-10: Perfiles

> Quién puede ver el perfil de quién y durante cuánto tiempo, qué datos propios carga cada usuario, y qué muestra la ficha de una persona según quién la abra. Las reglas que esto produce las aplica la skill `arquitectura` (`reglas-negocio.md` §Visibilidad de perfiles). La skill dice *cómo* se escribe; esta ADR, *qué* se decidió y *por qué*.

## §1 La visibilidad de perfiles es asimétrica y nace de la relación en una mesa

**Decisión.** El perfil de un **master** es visible para cualquier usuario que mire su mesa. El perfil de un **jugador** se abre para el master **desde que recibe su postulación**. Los **jugadores de una mesa** ven los perfiles de sus compañeros. Las tres reglas caducan igual (§2) y el admin no tiene restricciones de visibilidad salvo la autoría de los comentarios (ADR-06 §8). Es una regla de privacidad, no de UI: el backend la aplica y el frontend la refleja.

**Por qué.** El karma existe para evaluar comportamiento, y evaluar requiere ver; pero exponer a todos ante todos convierte la plataforma en un ranking público de personas. La asimetría refleja quién decide qué: el jugador decide **si vale la pena entrar** y mira al master antes de postularse; el master decide **a quién acepta**, y mira al candidato solo cuando ese candidato se le presentó por su cuenta. Los compañeros van a jugar juntos y a comentarse entre ellos (`JJ`), así que necesitan poder evaluarse igual.

*Antes: #41, #47.*

## §2 La visibilidad caduca a los N días del cierre de la mesa, y en pausa no corre

**Decisión.** La ventana se cuenta desde `closed_at` (ADR-07 §7), cuando la mesa entra en `Finished` o `Canceled`, y dura N días: `profiles.visibility_window_days`, que arranca en **14** y se ajusta sin desplegar (ADR-17 §3). Mientras la mesa esté en `Pause` el reloj no corre y la visibilidad sigue abierta. Pasado el plazo se deja de ver el perfil de los masters y jugadores con los que se coincidió, aunque el registro de con quién se jugó se conserva.

**Por qué.** Acota la ventana en que el karma sirve para decidir —postularse, aceptar a un candidato— sin convertir la plataforma en un directorio permanente de personas. El ancla es el cierre porque es cuando la relación termina de verdad; una mesa pausada solo está detenida, y sus integrantes siguen siendo compañeros. El historial se guarda porque hace falta para el karma y la moderación, pero deja de ser visible.

*Antes: #44.*

## §3 Un perfil que no se puede ver responde `404`, igual que el veto

**Decisión.** `GET /api/v1/users/{id}/profile` responde `404` cuando el actor no cumple ninguna de las reglas de §1 y §2 — ni `403` ni `200` recortado. La pantalla sí puede explicarle la caducidad a quien tuvo la relación.

**Por qué.** Es la misma pregunta que el veto ya contestó (ADR-09 §4), y las dos respuestas tienen que decir lo mismo o la diferencia se vuelve un canal: un `403` afirma que la persona existe **y** que hubo algo entre ustedes, que es lo que la ventana cierra; bastaría probar ids hasta ver un `403`. El `200` recortado filtra la existencia igual y reparte la regla en dos lugares, con el frontend interpretando ausencias. La explicación en pantalla no filtra nada: el frontend conoce sus propias mesas cerradas y nunca aprende si el perfil existe para alguien más.

*Antes: #249.*

## §4 Nombre y país se piden una vez, en un onboarding bloqueante

**Decisión.** No hay pantalla de edición de perfil en v1. `name` y `country` se piden **una sola vez**, en `/onboarding`, un paso bloqueante después del primer login e inmediatamente después de la invitación al servidor (ADR-05 §1). `name` arranca vacío, con un placeholder que pide cómo te conocen en la mesa; `country` es `CHAR(2)` ISO 3166-1 alpha-2; `discord_username` se conserva siempre. Editar después queda fuera de v1, no descartado: sería una pantalla que reusa el mismo formulario, sin tocar el schema.

**Por qué.** Ninguna de las dos columnas tenía quién la llenara: Discord no expone país y el legacy nunca tuvo un endpoint de escritura de usuarios. Una ruta entera para dos campos no se paga, y eliminarlos perdía un objetivo legítimo: que cada quien se llame como quiera **sin perder el rastro de cuál es su Discord**, que queda como identidad verificable. El paso bloquea a propósito: si se pudiera saltar, las columnas volverían a quedar nulas. `CHAR(2)` evita *México* / *Mexico* / *MX* y deriva la bandera del ISO sin llamar a ningún servicio externo.

*Antes: #134.*

## §5 El perfil no promete karma ni comentarios hasta que existan

**Decisión.** `/player/profile` y `/player/users/:id` muestran nombre, usuario de Discord, país, roles, mesas terminadas (§6) y la asistencia agregada (ADR-08 §5). **La respuesta no lleva `karma` ni `comments`** —ni siquiera en `null`— y la pantalla **no dibuja** la sección «Comentarios recibidos». Las dos llegan en F5 con `KarmaService` y `comments` (ADR-01 §3), y entonces van en todas las fichas.

**Por qué.** El perfil se construyó antes que el karma para estrenar las reglas de visibilidad (§1–§3), que hacen falta funcionando para decidir quién puede comentar a quién, en vez de construir el karma sobre una puerta que nadie abrió. El bloque vacío no se dibuja porque una sección titulada «Comentarios recibidos» sin nada abajo no dice «todavía nadie te comentó» sino «esto debería tener algo» (`diseno` §5). Y el campo no viaja en `null` porque un DTO que promete `karma` le enseña al frontend a preguntarlo.

*Antes: #248.*

## §6 La ficha de una persona depende de quién la abre: expediente para el admin, consulta corta para el master

**Decisión.** Cada persona de una mesa —masters, jugadores y candidatos— tiene un indicador «Ver ficha». **El admin** abre `/admin/users/:id`: usuario de Discord, país, miembro desde, mesas terminadas, roles y estado **con las acciones que los cambian** (las mismas reglas que la fila de la lista), asistencia, **todas las mesas** que dirige, juega o a las que se postuló —abriendo `/admin/tables/:id`— y el historial de lo que hicieron los admins con la cuenta. La lista de usuarios abre la ficha por el usuario de Discord y por un ícono en toda fila. **El master** abre un modal (`PersonSummaryDialog`): usuario de Discord, nombre y mesas terminadas, nada más. Las pestañas no saben cuál abren: reciben `onOpenPerson` y lo decide la pantalla que las monta. El perfil (`ProfileResponse`) suma `discordUsername` y `finishedTables` —mesas `Finished` donde fue `Player`— bajo las reglas de §1–§3. `GET /users/{id}/tables`, solo admin, lista las mesas de una persona: masters y postulaciones mezclados y paginados en memoria como la bandeja (ADR-16 §5), la más nueva primero, sin mesas dadas de baja ni postulaciones retiradas (ADR-03 §7).

**Por qué.** El admin necesita toda la información de la persona y poder actuar desde ahí; el historial suelto «realmente no sirve». La línea entre roles la fijó el usuario: **el master solo ve información muy general**, y es muy importante distinguirlo. Página para el admin porque es un expediente con acciones y una lista que crece; modal para el master porque es una consulta al pasar sin salir de la mesa. El usuario de Discord lo ve cualquiera que ya podía ver ese perfil: es una comunidad de un servidor de Discord y así se encuentran, así que no abre nada que el servidor no muestre.

*Antes: #285.*
